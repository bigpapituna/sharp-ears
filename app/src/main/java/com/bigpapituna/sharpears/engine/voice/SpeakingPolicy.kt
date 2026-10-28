package com.bigpapituna.sharpears.engine.voice

import com.bigpapituna.sharpears.engine.SpeechQueue
import com.bigpapituna.sharpears.engine.SpokenCatalog
import com.bigpapituna.sharpears.engine.family.PlainLook

/** Where a line's sound comes from (2.18). */
enum class Source {
    /** The coach's narration of the line (2.16.2, a grown-up with a coach voice; the build's extra). */
    NARRATION,
    /** The line's recording in the pup's natural voice (assets/voice, scripts/render_voice.py). */
    RECORDING,
    /** The on-phone Kokoro model (files/kokoro), for lines nobody recorded (a kid's name in them). */
    MODEL,
    /** The phone's own speech engine: the fallback, and the voice when the natural voice is off. */
    SYSTEM,
}

/** A line handed to a speaker: its words, where its sound comes from ([asset] for a recording), its speed (the model's). */
data class Utterance(val id: Int, val text: String, val source: Source, val asset: String?, val speed: Float, val overMic: Boolean)

/**
 * The speaker port (2.18): one way of saying a line out loud. System speech (TextToSpeech on Android), a recorded clip
 * (MediaPlayer, shared with the coach), the on-phone model (sherpa-onnx). An iOS port supplies its own.
 */
interface Speaker {
    /**
     * Says [u]. [started] when its sound begins (its length when known); [ended] exactly once when it is over: finished, failed
     * or stopped ([ok] false for a failure or a stop). Both on the policy's thread (the host's adapters post them there).
     */
    fun say(u: Utterance, started: (lengthMs: Long?) -> Unit, ended: (ok: Boolean) -> Unit)

    /** Cuts off what it is saying; its [say]'s ended still comes (or came). */
    fun stop()
}

/** Which recording a line plays (2.18, was NaturalVoice.recording and Voice.say). Pure. */
object RecordingChoice {
    /**
     * [natural]: the Natural voice setting. [kid]: the profile's own isKid or bigKid (never the skin's): a kid never hears the
     * coach's narration, whatever the door returns. [narration]: the extra's narration asset for the line, or null.
     * [recording]: the line's recording asset, or null. [model]: the Kokoro model is installed and loaded fine.
     */
    fun pick(natural: Boolean, kid: Boolean, narration: String?, recording: String?, model: Boolean): Pair<Source, String?> = when {
        !natural -> Source.SYSTEM to null
        narration != null && !kid -> Source.NARRATION to narration
        recording != null -> Source.RECORDING to recording
        model -> Source.MODEL to null
        else -> Source.SYSTEM to null
    }
}

/**
 * 2.18, the speaking policy: what the app's voice says, when, and through which speaker; no Android (audio/Voice.kt is the host
 * and supplies the speakers, VoicePolicyTest drives it with fakes). It owns:
 * - guidance on or off and the Plain look's calm words ([World.enabled], [World.plain]);
 * - the mic rule: while the mic listens on the speaker a line waits in [SpeechQueue] (or is dropped when it is only good now)
 *   and is said when the mic stops; [speakOverMic] says it anyway with the mic deaf for exactly its length (the Stage's line
 *   window, [World.lineStarts]/[World.lineEnds]);
 * - one line at a time, in order, whichever speaker says it (the natural voice and the phone's voice no longer overlap);
 * - the recording to use ([RecordingChoice]: the coach's narration for a grown-up, the recording, the model, the phone);
 * - the pup-only rule ([sayInPupVoice]: a kid hears the pup or reads, never the phone's voice);
 * - [speaking] and [holdsMic] for screens that wait for the voice.
 * Everything on one thread (the main one on Android).
 */
class SpeakingPolicy(
    private val world: World,
    private val speakers: (Source) -> Speaker,
    private val clock: () -> Long,
    private val log: (String) -> Unit = {},
) {
    /** What the policy reads of the app and the phone. */
    interface World {
        /** Spoken guidance is on (PlainLook.voiceOn: the voice setting, or the Plain look's own). */
        val enabled: Boolean
        /** The Plain look is on: lines go through its calm bank. */
        val plain: Boolean
        /** The Natural voice setting. */
        val natural: Boolean
        /** The profile's own isKid or bigKid. */
        val kid: Boolean
        /** The model's speed for this profile (0.85 for little kids). */
        val speed: Float
        /** The mic listens now. */
        val listening: Boolean
        /** Headphones are on (Stage.headphones). */
        val headphones: Boolean
        /** The phone's speech engine has started (a line over the mic in its voice must start now or never). */
        val systemReady: Boolean
        /** Starts the phone's speech engine (it takes a moment). */
        fun warmSystem()
        /** The extra's narration asset for a line's catalog key, or null. */
        fun narration(key: String): String?
        /** The line's recording asset ("voice/<key>.ogg"), or null. */
        fun recording(key: String): String?
        /** The Kokoro model is installed and did not fail to load. */
        val modelReady: Boolean
        /** The Stage's line window: deaf from now until [lineEnds] (audio/Stage.lineStarts). */
        fun lineStarts(owner: Any, lengthMs: Long?)
        fun lineLength(owner: Any, lengthMs: Long)
        fun lineEnds(owner: Any, tailMs: Long)
        /** [speaking] changed (the host's Compose state). */
        fun changed(speaking: Boolean)
    }

    private val queue = SpeechQueue()
    private val toSay = ArrayDeque<Utterance>()
    private var current: Utterance? = null
    private var speaker: Speaker? = null
    /** A line is being said over the listening mic: it never holds the mic (screens that wait for [holdsMic] go on). */
    private var overMic = false
    /** Lines whose sound holds the mic deaf now (started on the speaker while it listened). */
    private val deafLines = HashSet<Int>()
    private var nextId = 0

    /** The voice is talking (or about to: a line is queued to say now). */
    val speaking: Boolean get() = current != null || toSay.isNotEmpty()

    /** The voice is talking through the speaker: a screen's mic should not start yet. Lines said over the mic do not hold it. */
    val holdsMic: Boolean get() = speaking && !world.headphones && !overMic

    /** Lines held while the mic listens. */
    val held: Int get() = queue.size

    private fun quiet() = world.listening && !world.headphones
    private fun plainText(text: String) = if (world.plain) PlainLook.calm(text, spoken = true) else text

    /** Where [text] would be said from now. */
    fun choose(text: String): Pair<Source, String?> {
        val key = SpokenCatalog.key(text)
        return RecordingChoice.pick(world.natural, world.kid, if (world.natural && !world.kid) world.narration(key) else null,
            world.recording(key), world.modelReady)
    }

    /**
     * Says [text]. [interrupt] cuts off whatever is being said or waiting. [deferrable] false drops the line (instead of
     * holding it) while the mic listens, for lines that are stale a moment later.
     */
    fun speak(text: String, interrupt: Boolean = false, deferrable: Boolean = true) {
        if (!world.enabled) { log("voice: (off) $text"); return }
        val said = plainText(text)
        val lines = queue.offer(said, clock(), !quiet(), interrupt, deferrable)
        if (lines.isEmpty()) {
            log("voice: ${if (deferrable) "held while the mic listens" else "dropped, the mic listens"}: $said")
            return
        }
        if (interrupt) cutOff()
        lines.forEach { enqueue(it, overMic = false) }
        pump()
    }

    /**
     * Says [text] now although the mic is listening (the Playground's call-outs, a hint, a demo's "Let me show you"): the mic
     * is deaf while the line plays (the speaker is next to it) and hears again just after it ends. With the mic off, or
     * headphones on, it is an ordinary line. A line in the phone's voice that cannot start now (its engine still starting)
     * is dropped: a call-out said seconds later is stale.
     */
    fun speakOverMic(text: String) {
        if (!world.enabled) { log("voice: (off) $text"); return }
        val said = plainText(text)
        if (!quiet()) { speak(said); return }
        if (choose(said).first == Source.SYSTEM && !world.systemReady) {
            world.warmSystem(); log("voice: over the mic, not ready yet, dropped: $said"); return
        }
        overMic = true
        log("voice: over the mic, deaf meanwhile: $said")
        enqueue(said, overMic = true)
        pump()
    }

    /**
     * Says [text] over the mic only in the pup's own voice (its recording, or the model); otherwise nothing, and the screen's
     * words stand alone (a kid hears the pup or reads it, never the phone's voice). Returns whether it is said.
     */
    fun sayInPupVoice(text: String): Boolean {
        if (!world.enabled || !world.natural || choose(plainText(text)).first == Source.SYSTEM) { log("voice: shown, not said (no recording): $text"); return false }
        speakOverMic(text)
        return true
    }

    /** The mic stopped: the lines it held, still fresh, are said now. */
    fun micStopped() {
        val lines = queue.release(clock())
        if (lines.isNotEmpty()) log("voice: mic stopped, speaking ${lines.size} held line(s)")
        if (!world.enabled) return
        lines.forEach { enqueue(it, overMic = false) }
        pump()
    }

    /** Everything stops: what is said, what waits, what the mic held. The mic hears again at once. */
    fun stop() {
        queue.clear()
        cutOff()
        settle()
        log("voice: stop")
    }

    private fun enqueue(text: String, overMic: Boolean) {
        val (source, asset) = choose(text)
        toSay.addLast(Utterance(nextId++, text, source, asset, world.speed, overMic))
        world.changed(true)
    }

    /** What is said or waits to be said goes; the speaker saying it stops; its deaf window ends now. */
    private fun cutOff() {
        toSay.clear()
        val c = current ?: return
        current = null
        val s = speaker
        speaker = null
        if (deafLines.remove(c.id)) world.lineEnds(c, 0L)
        s?.stop()
    }

    private fun pump() {
        if (current != null) return
        val u = toSay.removeFirstOrNull() ?: return settle()
        current = u
        val s = speakers(u.source)
        speaker = s
        // The one rule: a line that starts on the speaker while the mic listens makes it deaf until exactly its end.
        if (u.overMic || quiet()) { deafLines += u.id; world.lineStarts(u, null) }
        s.say(u, started = { len -> if (current === u && len != null && u.id in deafLines) world.lineLength(u, len) },
            ended = { ok -> ended(u, ok) })
    }

    private fun ended(u: Utterance, ok: Boolean) {
        if (deafLines.remove(u.id)) world.lineEnds(u, VOICE_TAIL_MS)
        if (current !== u) return                      // cut off already: what replaced it goes on
        current = null
        speaker = null
        if (!ok) log("voice: u${u.id} ${if (u.source == Source.SYSTEM) "error or stopped" else "did not play"}")
        pump()
    }

    private fun settle() {
        if (speaking) return
        overMic = false
        world.changed(false)
    }

    companion object {
        /** The mic stays deaf this long after a line said over it ends (the room rings on). */
        const val VOICE_TAIL_MS = 300L
    }
}

/**
 * 2.18 (bug 1): when a line of the build's extra (the coach) may start. It waits for the voice to be quiet, the step pop's chime
 * to end and nothing else of its own to play. A line with no bubble of its own ([outlastsTheVoice]: the overlay's, the finish's
 * stars) waits the voice out however long it talks (Liam's "Lesson complete... good girl" narration runs past the old 8 s and
 * dropped the stars line after it): only the time it waits for something else counts toward [waitMs], and [maxMs] bounds it
 * all. A line whose words a bubble shows now must start within [waitMs] whatever holds it. A page moving on drops it (the
 * extra's hush), never this. Pure (VoicePolicyTest).
 */
class ExtraTurn(private val startAt: Long, private val waitMs: Long, private val outlastsTheVoice: Boolean, private val maxMs: Long = MAX_MS) {
    enum class Next { WAIT, GO, DROP }

    private var waited = 0L
    private var last = startAt

    /** At [now]: [voiceTalking] (the app's voice speaks or has lines to say), [blocked] (the chime, a line of its own). */
    fun next(now: Long, voiceTalking: Boolean, blocked: Boolean): Next {
        if (now < startAt) return Next.WAIT
        val step = now - maxOf(last, startAt)
        last = now
        if (!(outlastsTheVoice && voiceTalking)) waited += step
        if (now - startAt > maxMs || waited > waitMs) return Next.DROP
        return if (voiceTalking || blocked) Next.WAIT else Next.GO
    }

    companion object {
        /** However long the voice talks, a waiting line older than this is stale. */
        const val MAX_MS = 30_000L
    }
}
