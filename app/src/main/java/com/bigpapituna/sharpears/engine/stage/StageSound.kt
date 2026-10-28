package com.bigpapituna.sharpears.engine.stage

import com.bigpapituna.sharpears.engine.Ducking
import com.bigpapituna.sharpears.engine.lessons.ChimeMode
import com.bigpapituna.sharpears.engine.sampleFor

/**
 * The port under the Stage (2.18): a bank of short sounds already loaded by its adapter (SoundPool on Android, audio/Stage.kt;
 * a recording fake in StageSoundTest). Ids come from the adapter's own loading; 0 is "no stream".
 */
interface SoundBank {
    /** Plays [sound] at [volume] (0..1, both sides) and [rate]; [priority] wins a stream when all are busy. Returns the stream. */
    fun play(sound: Int, volume: Float, rate: Float = 1f, priority: Int = 1): Int
    fun volume(stream: Int, volume: Float)
    fun stop(stream: Int)
}

/**
 * 2.18, Stage sound: the app's short sounds and the speaker-and-mic rule in one place, without Android (audio/Stage.kt is the
 * Android host: SoundPool, the headphones check, the main thread). Screens ask it:
 * - the rule ([quiet], [echo], [cue], [deafFor]: StageRule with this phone's headphones and the mic's listening);
 * - lines on the speaker ([lineStarts]/[lineEnds]/[hushLines]): the voice's and the coach's. A line makes the mic deaf from its
 *   start until exactly its end plus the room's tail (DeafWindow), ducks the app's notes while it plays when asked (2.17's
 *   coach ducking) and shows "Listening again" after it when asked (2.17's pill, [onCue]);
 * - the piano's notes ([noteOn], [noteOff], [noteVolume]), ducked as above, notes sounding included;
 * - the other sounds by id ([effect]).
 * Synchronized: notes come from the main thread and the MIDI and mic collectors, lines from the main thread. Pure (StageSoundTest).
 */
class StageSound(
    private val bank: SoundBank,
    private val headphones: () -> Boolean,
    private val ear: () -> Ear?,
    /** Runs a block [ms] later (the main thread's Handler on Android, a manual clock in tests). */
    private val later: (ms: Long, block: () -> Unit) -> Unit,
    private val log: (String) -> Unit = {},
) {
    /** What the Stage needs of the mic: whether it listens, and its deaf window. */
    interface Ear {
        val listening: Boolean
        val window: DeafWindow
    }

    // ---- the rule ------------------------------------------------------------------------------------------------------

    private fun listeningNow() = ear()?.listening == true

    /** The app must stay quiet on the speaker now ([listening]: the screen's own idea of it, the mic's by default). */
    fun quiet(listening: Boolean = listeningNow()): Boolean = StageRule.quiet(listening, headphones())

    /** An echo may sound now (StageRule.echo). */
    fun echo(soundOn: Boolean, listening: Boolean = listeningNow()): Boolean = StageRule.echo(soundOn, listening, headphones())

    /** How a cue plays now (StageRule.cue). */
    fun cue(soundOn: Boolean, listening: Boolean = listeningNow()): ChimeMode = StageRule.cue(soundOn, listening, headphones())

    /** The app makes a sound of [ms] (its ring included) on the speaker: the mic is deaf for it, unless headphones are on. */
    fun deafFor(ms: Long): Boolean {
        if (!StageRule.deafens(headphones())) return false
        val w = ear()?.window ?: return false
        w.deafFor(ms)
        log("mic: deaf for $ms ms")
        return true
    }

    // ---- lines on the speaker ------------------------------------------------------------------------------------------

    private class Line(val deaf: Boolean, val duck: Boolean, val cue: Boolean)
    private val lines = LinkedHashMap<Any, Line>()
    private var ducked = false

    /** "Listening again" was earned: the host shows the pill for StageRule.CUE_MS. */
    var onCue: () -> Unit = {}

    /** The app's notes are ducked now (a line that asked for it plays). */
    val isDucked: Boolean @Synchronized get() = ducked

    /**
     * A line of [owner]'s starts on the speaker, [lengthMs] long when known. Unless headphones are on, the mic is deaf from now
     * until [lineEnds] (StageRule.limitMs bounds a lost end; [lineLength] tightens it once the length is known). [duck]: the
     * app's notes about 4 dB down while it plays (2.17, the coach). [cue]: "Listening again" after it (2.17, the coach).
     */
    @Synchronized fun lineStarts(owner: Any, lengthMs: Long?, duck: Boolean = false, cue: Boolean = false) {
        val deaf = StageRule.deafens(headphones())
        lines[owner] = Line(deaf, duck, cue)
        if (deaf) ear()?.window?.open(owner, StageRule.limitMs(lengthMs))
        if (deaf) log("mic: deaf while a line plays" + (lengthMs?.let { " ($it ms)" } ?: ""))
        reduck()
    }

    /** [owner]'s line turned out [lengthMs] long (a recording prepared): a lost end now frees the mic just after it. */
    @Synchronized fun lineLength(owner: Any, lengthMs: Long) {
        val l = lines[owner] ?: return
        if (l.deaf) ear()?.window?.open(owner, StageRule.limitMs(lengthMs))
    }

    /**
     * [owner]'s line ended (finished, failed or stopped): the mic hears again after [tailMs] (the room's ring), and once that
     * has passed, with no other line playing and the mic listening, "Listening again" for a line that asked for it and [finished]
     * (a line cut short, by the step pop's chime or a page moving on, gets none).
     */
    @Synchronized fun lineEnds(owner: Any, tailMs: Long, finished: Boolean = true) {
        val l = lines.remove(owner) ?: return
        if (l.deaf) { ear()?.window?.close(owner, tailMs); log("mic: hears again in $tailMs ms") }
        reduck()
        if (finished && l.cue && l.deaf) later(tailMs) { cueIfHeard() }
    }

    /** Every line stops holding the mic at once, no tail (a screen left, the voice stopped, a profile closed). */
    @Synchronized fun hushLines() {
        if (lines.isEmpty()) return
        val w = ear()?.window
        for (o in lines.keys) w?.close(o, 0L)
        lines.clear()
        log("mic: lines hushed, hears again now")
        reduck()
    }

    /** A line of [owner]'s is playing (started and not ended). */
    @Synchronized fun playing(owner: Any): Boolean = owner in lines

    private fun cueIfHeard() {
        val show = synchronized(this) { lines.values.none { it.deaf } && StageRule.listeningCue(deafened = true, listening = listeningNow()) }
        if (show) { log("stage: listening again"); onCue() }
    }

    private fun reduck() {
        val now = lines.values.any { it.duck }
        if (now == ducked) return
        ducked = now
        log("duck: notes ${if (now) "down ${Ducking.DB.toInt()} dB" else "back to full"}")
        val g = Ducking.gain(now)
        for ((midi, id) in streams) bank.volume(id, ((struck[midi] ?: 1f) * g).coerceIn(0f, 1f))
    }

    // ---- the piano -----------------------------------------------------------------------------------------------------

    private var piano: Map<Int, Int> = emptyMap()       // sample's midi -> sound id
    private val streams = HashMap<Int, Int>()           // midi -> playing stream
    private val struck = HashMap<Int, Float>()          // midi -> the volume it was struck at

    /** The piano's samples by the note each was recorded at (the host loaded them). */
    @Synchronized fun piano(samples: Map<Int, Int>) { piano = samples }

    /** A piano note: the nearest sample at the right rate, louder with [velocity], ducked while a line asks for it. */
    @Synchronized fun noteOn(midi: Int, velocity: Int) {
        if (piano.isEmpty()) return
        val (sample, rate) = sampleFor(midi, piano.keys)
        val volume = 0.3f + 0.7f * velocity / 127f
        streams.remove(midi)?.let(bank::stop)
        streams[midi] = bank.play(piano.getValue(sample), Ducking.volume(volume, ducked), rate, 1)
        struck[midi] = volume
    }

    /** A sounding note turned down to [share] of the volume it was struck at (the step pop's chime fades before its noteOff). */
    @Synchronized fun noteVolume(midi: Int, share: Float) {
        val id = streams[midi] ?: return
        bank.volume(id, Ducking.volume((struck[midi] ?: 1f) * share.coerceIn(0f, 1f), ducked))
    }

    // ponytail: hard stop on release (no damper fade); add a short volume ramp if the cut-off clicks
    @Synchronized fun noteOff(midi: Int) { streams.remove(midi)?.let(bank::stop) }

    /** Every sounding note stops. */
    @Synchronized fun allNotesOff() { streams.values.forEach(bank::stop); streams.clear() }

    // ---- the other sounds ----------------------------------------------------------------------------------------------

    /** One of the bank's other sounds (a click, the drum, a band kit, a world jingle). */
    fun effect(sound: Int, volume: Float, rate: Float = 1f, priority: Int = 1): Int = bank.play(sound, volume.coerceIn(0f, 1f), rate, priority)
}
