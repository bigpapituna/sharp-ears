package com.bigpapituna.sharpears.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.voice.Source
import com.bigpapituna.sharpears.engine.voice.Speaker
import com.bigpapituna.sharpears.engine.voice.SpeakingPolicy
import com.bigpapituna.sharpears.engine.voice.Utterance

/**
 * Spoken guidance on Android (2.18: the host of engine/voice/SpeakingPolicy.kt, which decides what is said, when and from
 * where; this class supplies its speakers and its view of the phone). The speakers: [ClipPlayer] for the recorded lines
 * (assets/voice) and the coach's narration of them (Extras.narration), [KokoroSpeaker] for lines made on the phone (optional
 * model), [SystemSpeaker] for the phone's own voice (the fallback, and the voice with Natural voice off).
 *
 * The mic rule (the policy's): while [mic] listens through the speaker a line is held and said when the mic stops; screens that
 * start the mic wait for [holdsMic] first, so a step's text is spoken before its listening starts. A line said over the mic
 * ([speakOverMic]) keeps it deaf for exactly its length (the Stage's line window, audio/Stage.kt).
 * To add a speaker: implement engine/voice/Speaker and give the policy's [Source] for it here.
 */
class Voice(private val context: Context, private val prefs: Prefs, private val mic: MicInput) {
    /** True while a line is being spoken (or queued to be said now). */
    val speaking = mutableStateOf(false)
    /**
     * 2.16: the build's extra (extras/Extras.kt) has a line of its own waiting or playing through the speaker: a lesson step
     * waits for it before it starts by itself, as for this voice (PlayerScreen's autoStart). Never true on a build without it.
     */
    val extraTalking = mutableStateOf(false)
    /**
     * 2.15: the Plain look is on for the profile (set by MainActivity: PlainLook.on, never for a little kid). Spoken guidance
     * is then off unless turned on there (PlainLook.voiceOn), and a line said goes through the plain bank (PlainLook.calm).
     */
    @Volatile var plain: () -> Boolean = { false }
    /** 2.18: the profile's own isKid or bigKid (MainActivity; never the skin's): a kid never hears the coach's narration. */
    @Volatile var kid: () -> Boolean = { false }
    val enabled get() = com.bigpapituna.sharpears.engine.family.PlainLook.voiceOn(plain(), prefs.voice, prefs.plainVoice)

    /**
     * How fast lines made on the phone are spoken: 1 for grown-ups, 0.85 for kids (KIDS-PLAN 3.8, "big and slow";
     * MainActivity.openApp sets it per profile). Recorded lines carry their own speed (render_voice.py renders the
     * kid lines at 0.85). Lines with a kid's name in them are always made on the phone, so they follow this.
     */
    @Volatile var speed: Float = 1f

    private val main = Handler(Looper.getMainLooper())
    private val system = SystemSpeaker(context, prefs)
    private val model = KokoroSpeaker(context)
    private val clips = ClipPlayer(context, report = "natural_voice")
    /** The recorded lines' file names: listing 3000 assets took about 100 ms of the first frame, so a thread reads it first. */
    private val recorded: Set<String> by lazy { runCatching { context.assets.list("voice").orEmpty().toSet() }.getOrDefault(emptySet()) }

    /** The recorded clip speaker: the line's recording or the coach's narration of it. */
    private val clipSpeaker = object : Speaker {
        override fun say(u: Utterance, started: (Long?) -> Unit, ended: (Boolean) -> Unit) {
            Log.d(TAG, "voice: (${if (u.source == Source.RECORDING) "recorded" else "narrated ${u.asset}"}) ${u.text}")
            clips.play(u.asset!!, started, ended)
        }
        override fun stop() = clips.stop()
    }

    private val world = object : SpeakingPolicy.World {
        override val enabled get() = this@Voice.enabled
        override val plain get() = this@Voice.plain()
        override val natural get() = prefs.naturalVoice
        override val kid get() = this@Voice.kid()
        override val speed get() = this@Voice.speed
        override val listening get() = mic.listening
        override val headphones get() = Stage.headphones()
        override val systemReady get() = system.ready
        override fun warmSystem() = system.warmUp()
        /** 2.16: the build's extra's own narration of the line (a grown-up's coach voice; null on a build without it). */
        override fun narration(key: String) = com.bigpapituna.sharpears.extras.Extras.narration(key)
        override fun recording(key: String) = "voice/$key.ogg".takeIf { "$key.ogg" in recorded }
        override val modelReady get() = model.usable
        override fun lineStarts(owner: Any, lengthMs: Long?) = Stage.lineStarts(owner, lengthMs)
        override fun lineLength(owner: Any, lengthMs: Long) = Stage.lineLength(owner, lengthMs)
        override fun lineEnds(owner: Any, tailMs: Long) = Stage.lineEnds(owner, tailMs)
        override fun changed(speaking: Boolean) { this@Voice.speaking.value = speaking }
    }

    private val policy = SpeakingPolicy(world, { s -> when (s) { Source.SYSTEM -> system; Source.MODEL -> model; else -> clipSpeaker } },
        { System.currentTimeMillis() }, { Log.d(TAG, it) })

    /** The Kokoro model is on the phone (else only the recorded lines use the natural voice). */
    val naturalModelInstalled get() = model.installed

    init {
        mic.onStopped = { onMain { policy.micStopped() } }
        // The recorded list and the coach's course list load in the background while the first screen opens.
        Thread({ recorded.size; com.bigpapituna.sharpears.extras.Extras.narration("") }, "voice-lists").start()
        if (enabled && prefs.naturalVoice) model.warmUp()
    }

    /** The voice is talking through the speaker: the mic should not start yet. Lines said over the mic do not hold it. */
    val holdsMic get() = policy.holdsMic

    /**
     * Says [text]. [interrupt] cuts off whatever is being said or waiting. [deferrable] false drops the line
     * (instead of holding it) when the mic is listening, for lines that are stale a moment later.
     */
    fun speak(text: String, interrupt: Boolean = false, deferrable: Boolean = true) = onMain { policy.speak(text, interrupt, deferrable) }

    /**
     * Says [text] now although the mic is listening (2.1.1: the Playground's call-outs between rounds and the "Let me
     * show you" of a demo): the mic is deaf while the line plays (the phone's speaker is next to it) and hears again
     * shortly after it ends. With the mic off, or headphones on, it is an ordinary line.
     */
    fun speakOverMic(text: String) = onMain { policy.speakOverMic(text) }

    /**
     * 2.15: says [text] over the mic only in the pup's own voice (its recording, or the Kokoro model on the phone); otherwise
     * nothing, and the screen's words stand alone (the "could not hear" hint: a kid hears the pup or reads it, never the
     * platform voice). Returns whether it is said.
     */
    fun sayInPupVoice(text: String): Boolean = policy.sayInPupVoice(text)

    fun stop() = onMain { policy.stop() }

    fun release() {
        policy.stop()                                    // 2.18: a profile closed mid-line never leaves the mic deaf
        model.release()
        system.release()
        mic.onStopped = null
    }

    /** Offline English voices of the running engine, best first. Empty until the engine has started. */
    fun voices(): List<SystemSpeaker.Option> = system.voices()

    /** The voice in use, by its engine id. */
    fun currentVoice(): String? = system.currentVoice()

    /** Switches to [name], saves it, and says a short sample line in it. */
    fun choose(name: String) {
        if (system.choose(name)) speak("Ready when you are. Play the first note.", interrupt = true)
    }

    /** Starts the engine early (Settings opens the voice list), without speaking. */
    fun warmUp() = system.warmUp()

    /** Becomes true once the engine has started (the Settings voice list waits for it). */
    val engineReady get() = system.engineReady

    /** The policy is the main thread's: a call from elsewhere goes there. */
    private fun onMain(block: () -> Unit) { if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block) }
}
