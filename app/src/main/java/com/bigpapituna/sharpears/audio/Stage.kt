package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.chords.BandSounds
import com.bigpapituna.sharpears.engine.chords.PopPlay
import com.bigpapituna.sharpears.engine.clickSamples
import com.bigpapituna.sharpears.engine.lessons.ChimeMode
import com.bigpapituna.sharpears.engine.playground.drumSamples
import com.bigpapituna.sharpears.engine.stage.SoundBank
import com.bigpapituna.sharpears.engine.stage.StageRule
import com.bigpapituna.sharpears.engine.stage.StageSound
import java.io.File

/**
 * 2.18, Stage sound on Android: the one place the app makes short sounds, and the one place that knows the speaker-and-mic rule
 * (engine/stage/StageRule.kt; the logic is engine/stage/StageSound.kt, tested on the JVM). It owns:
 * - the sound bank: two SoundPools (the piano's, CONTENT_TYPE_MUSIC; everything else's, SONIFICATION; both USAGE_GAME, the
 *   low-latency path) loaded once for the process: the piano samples (assets/samples), the synthesized clicks, pup drum and band
 *   kit (made once into the cache, named by their hash), the world's jingles and stage sounds (res/raw). Sampler, Clicks,
 *   BandPlayer, MetronomeVoice, DrumSound and WorldSounds are thin handles over it (before 2.18 each built its own pool);
 * - the rule for screens: [quiet], [echo], [cue], [tap], [deafFor], with this phone's [headphones] and the mic's listening;
 * - lines on the speaker ([lineStarts], [lineEnds], [hushLines]): the voice's and the coach's deaf windows, the coach's duck of
 *   the notes and "Listening again" ([listeningAgain], drawn by ui/ListeningAgain.kt).
 * To add a sound: load it in [load] (a raw file, an asset or PCM through [synth]) and give it a call here; a screen never builds a
 * SoundPool or reads the headphones itself (StageWiringTest).
 */
object Stage {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pools: Pools? = null
    @Volatile private var mic: MicInput? = null
    @Volatile private var audio: AudioManager? = null

    /** "Listening again" earned: counts up per cue (0: none yet). The pill shows for StageRule.CUE_MS. */
    val listeningAgain = mutableIntStateOf(0)

    private val bank = object : SoundBank {
        override fun play(sound: Int, volume: Float, rate: Float, priority: Int): Int {
            val p = pools ?: return 0
            val pool = p.of(sound)
            val stream = pool.play(sound and ID_MASK, volume, volume, priority, 0, rate.coerceIn(0.5f, 2f))
            return if (stream == 0) 0 else stream or (sound and POOL_BIT)
        }
        override fun volume(stream: Int, volume: Float) { pools?.of(stream)?.setVolume(stream and ID_MASK, volume, volume) }
        override fun stop(stream: Int) { pools?.of(stream)?.stop(stream and ID_MASK) }
    }

    private val core = StageSound(bank, ::headphones, { mic }, { ms, block -> main.postDelayed(block, ms) }, { Log.d(TAG, it) }).also {
        it.onCue = { main.post { listeningAgain.intValue = listeningAgain.intValue + 1 } }
    }

    /** The pools and what was loaded into them. Sound and stream ids carry the pool in [POOL_BIT]. */
    private class Pools(val music: SoundPool, val effects: SoundPool) {
        fun of(id: Int): SoundPool = if (id and POOL_BIT != 0) effects else music
        val raw = HashMap<Int, Int>()                     // R.raw id -> sound
        var accent = 0; var beat = 0; var drum = 0
        val kit = HashMap<PopPlay.Kit, Int>()
        var pianoCount = 0
        var pianoLoaded = 0
        /** 2.19.0: the 8 s samples (assets/samples-long), loaded after the 2 s set is ready and swapped in when all are in. */
        val long = HashMap<Int, Int>()
        var longCount = 0
        val pianoWaiters = ArrayList<() -> Unit>()
        val pianoReady get() = pianoCount > 0 && pianoLoaded >= pianoCount
    }

    /** Loads the bank (once per process; again after [release]). The mic it rules over comes with [attach]. */
    fun init(context: Context) {
        if (pools != null) return
        synchronized(this) { if (pools == null) load(context.applicationContext) }
    }

    private fun load(app: Context) {
        audio = app.getSystemService(AudioManager::class.java)
        fun attrs(type: Int) = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(type).build()
        val p = Pools(
            SoundPool.Builder().setMaxStreams(16).setAudioAttributes(attrs(AudioAttributes.CONTENT_TYPE_MUSIC)).build(),
            SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs(AudioAttributes.CONTENT_TYPE_SONIFICATION)).build(),
        )
        val files = app.assets.list("samples").orEmpty()
        p.pianoCount = files.size
        p.music.setOnLoadCompleteListener { _, _, status ->
            if (status != 0) return@setOnLoadCompleteListener
            val n = ++p.pianoLoaded
            if (n == p.pianoCount) {
                Log.d(TAG, "sampler: ${p.pianoCount} samples loaded")
                val w = synchronized(p) { p.pianoWaiters.toList().also { p.pianoWaiters.clear() } }
                main.post { w.forEach { it() } }
                // 2.19.0: the 8 s set decodes about 5 times slower than the 2 s one, so it loads behind it, never before a step.
                val long = runCatching { app.assets.list("samples-long").orEmpty() }.getOrDefault(emptyArray())
                p.longCount = long.size
                for (name in long) p.long[name.removePrefix("p").removeSuffix(".ogg").toInt()] =
                    app.assets.openFd("samples-long/$name").use { p.music.load(it, 1) }
            } else if (p.longCount > 0 && n == p.pianoCount + p.longCount) {
                core.piano(HashMap(p.long))
                Log.d(TAG, "sampler: ${p.longCount} long samples in (notes hold their written length)")
            }
        }
        core.piano(files.associate { name ->
            name.removePrefix("p").removeSuffix(".ogg").toInt() to app.assets.openFd("samples/$name").use { p.music.load(it, 1) }
        })
        // The synthesized sounds, written once into the cache (the name carries the sound's hash: whole or rewritten).
        fun synth(name: String, s: FloatArray, rate: Int): Int =
            File(app.cacheDir, "$name-${Integer.toHexString(s.contentHashCode())}.wav").also { if (it.length() != 44L + 2L * s.size) Wav.write(it, s, rate) }
                .let { p.effects.load(it.path, 1) or POOL_BIT }
        p.accent = synth("click-accent", clickSamples(true), 48_000)
        p.beat = synth("click-beat", clickSamples(false), 48_000)
        p.drum = synth("pup-drum", drumSamples(), 48_000)
        for (kit in PopPlay.Kit.entries) p.kit[kit] = synth("band-${kit.name.lowercase()}", BandSounds.samples(kit), BandSounds.RATE)
        for (res in WORLD) p.raw[res] = p.effects.load(app, res, 1) or POOL_BIT
        pools = p
    }

    /** The mic the rule is about (MainActivity's, for the activity's life). */
    fun attach(mic: MicInput) { this.mic = mic }

    /** Frees the pools (the activity's end); the next [init] loads again. Sounding notes stop, lines let go of the mic. */
    fun release() {
        core.hushLines(); core.allNotesOff()
        val p = synchronized(this) { pools.also { pools = null } } ?: return
        p.music.release(); p.effects.release()
    }

    // ---- the rule ------------------------------------------------------------------------------------------------------

    /** Headphones of any kind: then the app may make sound while the mic listens, and the mic need not be deaf for it. */
    fun headphones(): Boolean = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any {
        it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    /** The app must stay quiet on the speaker: [listening] (the mic's by default) and no headphones. */
    fun quiet(listening: Boolean = mic?.listening == true): Boolean = core.quiet(listening)

    /** An echo (a touched key's sound, the app's notes under the player) may sound: [soundOn] and not [quiet]. */
    fun echo(soundOn: Boolean, listening: Boolean = mic?.listening == true): Boolean = core.echo(soundOn, listening)

    /** How a cue (a hint, a chime, a demo) plays: skipped with sound off, deaf for it while [quiet], else as it is. */
    fun cue(soundOn: Boolean, listening: Boolean = mic?.listening == true): ChimeMode = core.cue(soundOn, listening)

    /** The app makes a sound [ms] long (its ring included) on the speaker: the mic is deaf for it unless headphones are on. */
    fun deafFor(ms: Long) { core.deafFor(ms) }

    /**
     * The echo of a key the player touched: the piano note for [holdMs] when [echo] allows ([soundOn], [listening]); returns
     * whether it sounded. The screens' "if sound and (not listening or headphones) noteOn, later noteOff", once.
     */
    fun tap(midi: Int, velocity: Int, holdMs: Long, soundOn: Boolean, listening: Boolean = mic?.listening == true): Boolean {
        if (!echo(soundOn, listening)) return false
        core.noteOn(midi, velocity)
        main.postDelayed({ core.noteOff(midi) }, holdMs)
        return true
    }

    // ---- lines on the speaker (the voice, the coach) -------------------------------------------------------------------

    /** A line starts on the speaker (StageSound.lineStarts): deaf until its end unless headphones, [duck], [cue]. */
    fun lineStarts(owner: Any, lengthMs: Long?, duck: Boolean = false, cue: Boolean = false) = core.lineStarts(owner, lengthMs, duck, cue)
    fun lineLength(owner: Any, lengthMs: Long) = core.lineLength(owner, lengthMs)
    /** The line ended, failed or stopped: the mic hears again after [tailMs]. */
    fun lineEnds(owner: Any, tailMs: Long, finished: Boolean = true) = core.lineEnds(owner, tailMs, finished)
    /** Every line lets go of the mic now (a screen left, the voice stopped). */
    fun hushLines() = core.hushLines()
    fun linePlaying(owner: Any): Boolean = core.playing(owner)
    val ducked: Boolean get() = core.isDucked

    // ---- sounds --------------------------------------------------------------------------------------------------------

    fun noteOn(midi: Int, velocity: Int) = core.noteOn(midi, velocity)
    fun noteOff(midi: Int) = core.noteOff(midi)
    fun noteVolume(midi: Int, share: Float) = core.noteVolume(midi, share)

    /** Runs [then] (on the main thread) once the piano's samples are loaded; at once when they are. */
    fun whenPianoReady(then: () -> Unit) {
        val p = pools ?: return
        val now = synchronized(p) { if (p.pianoReady) true else { p.pianoWaiters += then; false } }
        if (now) main.post(then)
    }

    /** The metronome's click: [accent] or a beat, at [volume] and [rate]. */
    fun click(accent: Boolean, volume: Float = 1f, rate: Float = 1f) {
        val p = pools ?: return
        core.effect(if (accent) p.accent else p.beat, volume, rate, priority = 2)
    }

    /** The pup's drum (a low tom). */
    fun drum(accent: Boolean) { val p = pools ?: return; core.effect(p.drum, if (accent) 1f else 0.8f) }

    /** One of the band's kit sounds at [volume]. */
    fun band(kit: PopPlay.Kit, volume: Float) { val p = pools ?: return; core.effect(p.kit[kit] ?: return, volume) }

    /** One of the world's sounds (res/raw) at [volume] and [rate]. */
    fun world(res: Int, volume: Float, rate: Float = 1f): Boolean { val p = pools ?: return false; core.effect(p.raw[res] ?: return false, volume, rate); return true }

    private const val POOL_BIT = 1 shl 30
    private const val ID_MASK = POOL_BIT - 1
    private val WORLD = listOf(R.raw.tick, R.raw.jingle_common, R.raw.jingle_rare, R.raw.jingle_shiny, R.raw.chime_star, R.raw.munch,
        R.raw.egg_crack, R.raw.pop, R.raw.stage_pop, R.raw.stage_ding, R.raw.jam_whoosh)
}
