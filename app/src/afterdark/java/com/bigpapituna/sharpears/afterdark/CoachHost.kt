package com.bigpapituna.sharpears.afterdark

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.analytics.Tester
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.family.PlainLook
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import com.bigpapituna.sharpears.extras.ExtraLine
import com.bigpapituna.sharpears.extras.Moment

/**
 * The coach voice on the phone (2.16): the profile open ([open]), the wall ([on]: CoachGate for the profile read live at every
 * moment), the bank (assets/coach/lines.json, read once), the picks ([CoachBrain]) and one MediaPlayer at a time.
 *
 * A line never plays over another, over the voice (it waits until Voice.speaking clears) or under the step pop's chime ([chime]:
 * a line playing stops, one waiting waits the chime out). While a line waits or plays, Voice.extraTalking is true, so a lesson
 * step does not start by itself under it (PlayerScreen's autoStart). The mic: deaf for the line and the room's ring after it
 * (CoachSound), renewed in short slices while it plays so a line cut short ([hush]) leaves the mic hearing again at once.
 * All on the main thread.
 */
@android.annotation.SuppressLint("StaticFieldLeak")    // the application context; the mic and voice are replaced at each profile open
internal object CoachHost {
    /**
     * The tester's super properties (analytics/EVENTS.md): whether lines play for the profile open, which voice (its folder, or
     * "off") and how it talks to the player ("w", "m", or "unset"), to compare voices and players. Never a line or its id.
     */
    const val PROPERTY = "coach_voice"
    const val VOICE_PROPERTY = "coach_voice_id"
    const val FOR_PROPERTY = "coach_for"

    private val main = Handler(Looper.getMainLooper())
    private var context: Context? = null
    private var who: (() -> Profile)? = null
    internal var prefs: Prefs? = null
        private set
    private var mic: MicInput? = null
    private var voice: Voice? = null
    private var brain = CoachBrain()
    private val banks = HashMap<String, CoachBank>()
    private var voiceList: List<CoachVoice>? = null

    private class Pending(val line: CoachLine, val voice: String, val moment: Moment, val overlay: Boolean, val startAt: Long, val deadline: Long)
    private var pending: Pending? = null
    private var player: MediaPlayer? = null
    private var deafens = false
    private var chimeUntil = 0L
    private val waiters = ArrayList<() -> Unit>()

    /** Words the overlay shows (lines with no bubble of their own), [seq] counting up so the same words twice both show. */
    data class Caption(val seq: Int, val text: String, val ms: Long)
    val caption = mutableStateOf<Caption?>(null)
    private var seq = 0

    /** 2.17 (M3): "Listening again" after a line that made the mic deaf, [listening] counting up per cue (0: none yet). */
    val listening = mutableStateOf(0)

    /** 2.17 (M3): lines held (a profile's first lesson until a step is passed): none plays or waits. */
    private var held = false

    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private fun now() = SystemClock.uptimeMillis()

    fun open(context: Context, who: () -> Profile, prefs: Prefs, mic: MicInput, voice: Voice) {
        hush()
        this.context = context.applicationContext
        this.who = who
        this.prefs = prefs
        this.mic = mic
        this.voice = voice
        brain = CoachBrain()
        on()
    }

    fun close() = hush()

    /** The Encouragement in force for the profile open (the Plain look reads as Off). */
    private fun frequency(): CheerFrequency {
        val p = who?.invoke() ?: return CheerFrequency.OFF
        val pr = prefs ?: return CheerFrequency.OFF
        return PlainLook.cheerFrequency(PlainLook.on(p.audience, pr.plainLook), pr.cheerFrequency)
    }

    /** The voices this build has (a folder with a voice.json under assets/coach), in Settings' order; read once. */
    fun voices(): List<CoachVoice> = voiceList ?: context?.let { c ->
        val found = c.assets.list("coach").orEmpty().mapNotNull { dir ->
            runCatching { CoachVoice.parse(c.assets.open("coach/$dir/voice.json").reader(Charsets.UTF_8).use { it.readText() }) }.getOrNull()
                ?.takeIf { it.id == dir }
        }
        CoachVoice.sorted(found).also { voiceList = it; Log.d(TAG, "coach: voices ${it.joinToString { v -> v.id }}") }
    }.orEmpty()

    /** The voice that plays for the profile open (CoachGate.voiceOf), or null for Off. */
    private fun voice(): String? = CoachGate.voiceOf(prefs?.extraString(CoachGate.KEY), voices().map { it.id })

    /** How the coach talks to the player open: "w", "m", or null (unset: only the lines for anyone). */
    private fun forWho(): String? = CoachGate.forOf(prefs?.extraString(CoachGate.FOR_KEY))

    /**
     * Lines play now for the profile open: CoachGate.on, asked of the profile as it is now (a kid, a big kid: never). Keeps the
     * tester's three super properties up to date (Tester.extra sends only a change).
     */
    fun on(): Boolean {
        val p = who?.invoke()
        val pr = prefs
        val v = voice()
        val on = p != null && pr != null && PlainLook.on(p.audience, pr.plainLook).let { plain ->
            CoachGate.on(p, v, PlainLook.cheerFrequency(plain, pr.cheerFrequency), plain)
        }
        Tester.extra(PROPERTY, on)
        Tester.extra(VOICE_PROPERTY, if (on && v != null) v else CoachGate.OFF)
        Tester.extra(FOR_PROPERTY, CoachGate.forProperty(prefs?.extraString(CoachGate.FOR_KEY)))
        return on
    }

    private val busy get() = pending != null || player != null

    private fun bank(): CoachBank? {
        val v = voice() ?: return null
        banks[v]?.let { return it }
        val c = context ?: return null
        return runCatching { CoachBank.parse(c.assets.open("coach/$v/lines.json").reader(Charsets.UTF_8).use { it.readText() }) }
            .onFailure { Log.w(TAG, "coach: the bank of $v did not load", it) }.getOrNull()?.also { banks[v] = it }
    }

    /**
     * A moment whose words the screen's bubble shows now (a cheer pop, the step pop's line): the words and how long to show
     * them, or null (the screen's own). It must be able to play at once: nothing else of the coach's waiting or playing, the
     * voice quiet (unless it starts [afterMs] later, the step pop's line after its chime).
     */
    fun line(m: Moment, count: Int?, afterMs: Long): ExtraLine? {
        if (!on() || busy || held) return null
        if (afterMs == 0L && voice?.speaking?.value == true) return null
        val b = bank() ?: return null
        val l = brain.pick(m, count, now(), frequency(), b, forWho()) ?: return null
        queue(Pending(l, voice()!!, m, overlay = false, startAt = now() + afterMs, deadline = now() + afterMs + CoachSound.SHOWN_WAIT_MS))
        return ExtraLine(l.caption, CoachSound.showMs(l.ms))
    }

    /** A moment with no bubble of its own: the line plays when the voice and the chime allow (CoachSound.WAIT_MS at most), its words in the overlay. */
    fun say(m: Moment) {
        if (!on() || busy || held) return
        val b = bank() ?: return
        val l = brain.pick(m, null, now(), frequency(), b, forWho()) ?: return
        queue(Pending(l, voice()!!, m, overlay = true, startAt = now(), deadline = now() + CoachSound.WAIT_MS))
    }

    /** Each voice's course narration files (assets/coach/<voice>/course), listed once; read on the voice's worker thread too. */
    private val courseFiles = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()

    /**
     * A spoken line's narration in the coach's voice for the profile open, as an asset path, or null (the normal voice):
     * CoachGate.narrates (a grown-up with a voice chosen, the Plain look off), and the file there (CoachGate.narrationOf).
     */
    fun narration(key: String): String? {
        val p = who?.invoke() ?: return null
        val pr = prefs ?: return null
        val v = voice() ?: return null
        if (!CoachGate.narrates(p, v, PlainLook.on(p.audience, pr.plainLook))) return null
        val c = context ?: return null
        val files = courseFiles.getOrPut(v) { runCatching { c.assets.list("coach/$v/course").orEmpty().toSet() }.getOrDefault(emptySet()) }
        return CoachGate.narrationOf(key, forWho(), files)?.let { "coach/$v/course/$it" }
    }

    fun stepBegan() = brain.stepBegan()

    /** Runs [then] once no line waits or plays (at once when none does): the step's own voice line after the coach's. */
    fun afterLine(then: () -> Unit) { if (!busy) then() else waiters += then }

    /** The step pop's chime sounds for [ms]: a line playing stops, a line waiting waits it out. */
    fun chime(ms: Long) {
        chimeUntil = now() + ms
        player?.let { mp -> Log.d(TAG, "coach: stopped for the step pop's chime"); stopPlayer(mp); settle() }
    }

    /** 2.17 (M3): hold every line (none plays or waits) or let them go again; holding stops what plays. */
    fun hold(on: Boolean) {
        if (held == on) return
        held = on
        Log.d(TAG, if (on) "coach: held until a step is passed" else "coach: free to talk")
        if (on) hush()
    }

    /** Everything stops: the line playing, the one waiting, its words. What waited on the line goes on now. */
    fun hush() {
        main.removeCallbacks(tick)
        pending = null
        player?.let { stopPlayer(it) }
        caption.value = null
        settle()
    }

    private fun queue(p: Pending) {
        pending = p
        voice?.extraTalking?.value = true
        main.removeCallbacks(tick)
        main.post(tick)
    }

    private val tick = Runnable { step() }

    private fun step() {
        val p = pending ?: return
        val t = now()
        if (t > p.deadline) { Log.d(TAG, "coach: ${p.line.id} dropped, no quiet moment"); pending = null; settle(); return }
        if (t < p.startAt || t < chimeUntil || voice?.speaking?.value == true || player != null) { main.postDelayed(tick, POLL_MS); return }
        pending = null
        play(p)
    }

    private fun play(p: Pending) {
        val c = context ?: return settle()
        val mp = MediaPlayer()
        val ok = runCatching {
            c.assets.openFd("coach/${p.voice}/${p.line.id}.ogg").use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            mp.setAudioAttributes(attrs)
            mp.setOnCompletionListener { done(it) }
            mp.setOnErrorListener { m, _, _ -> done(m); true }
            mp.prepare()
        }.onFailure { Log.w(TAG, "coach: ${p.line.id} did not play", it) }.isSuccess
        if (!ok) { mp.release(); settle(); return }
        val lengthMs = mp.duration.toLong().takeIf { it > 0 } ?: p.line.ms
        deafens = CoachSound.deafens(mic?.headphonesOn() ?: false)
        player = mp
        if (deafens) renew.run()
        mp.start()
        com.bigpapituna.sharpears.audio.Duck.set(true)          // 2.17: the notes a little quieter under the line
        if (p.overlay) caption.value = Caption(++seq, p.line.caption, CoachSound.showMs(lengthMs))
        Log.d(TAG, "coach: ${p.moment.name.lowercase()} ${p.voice}/${p.line.id} \"${p.line.caption}\" $lengthMs ms, mic deaf " +
            if (deafens) "${CoachSound.deafMs(lengthMs, headphones = false)} ms" else "no (headphones)")
        // Should its end never be reported, the line still ends.
        main.postDelayed({ if (player === mp) done(mp) }, lengthMs + 2_000)
    }

    /** The step pop chime's mic rule for the line's length: deaf a slice ahead, renewed while it plays. */
    private val renew: Runnable = Runnable {
        if (player == null || !deafens) return@Runnable
        mic?.deafFor(CoachSound.SLICE_MS)
        main.postDelayed(renew, CoachSound.RENEW_MS)
    }

    private fun done(mp: MediaPlayer) {
        if (player === mp) {
            player = null
            main.removeCallbacks(renew)
            com.bigpapituna.sharpears.audio.Duck.set(false)
            if (deafens) mic?.deafFor(StepPopRules.ROOM_TAIL_MS.toLong())   // the room's ring after the speaker stops
            // 2.17 (M3): the mic hears again once the ring has passed; say so, quietly, when it is listening.
            val wasDeaf = deafens
            main.postDelayed({
                if (player == null && CoachSound.listeningCue(wasDeaf, mic?.listening == true)) {
                    listening.value = listening.value + 1; Log.d(TAG, "coach: listening again")
                }
            }, StepPopRules.ROOM_TAIL_MS.toLong())
        }
        runCatching { mp.release() }
        settle()
    }

    private fun stopPlayer(mp: MediaPlayer) {
        player = null
        main.removeCallbacks(renew)
        com.bigpapituna.sharpears.audio.Duck.set(false)
        runCatching { mp.stop() }
        runCatching { mp.release() }
        caption.value = null
    }

    /** Nothing waits or plays: what waited on the line goes on (first, so a step never starts in the gap), then the voice's hold ends. */
    private fun settle() {
        if (busy) return
        val w = waiters.toList()
        waiters.clear()
        w.forEach { it() }
        if (!busy) voice?.extraTalking?.value = false
    }

    private const val POLL_MS = 50L
}
