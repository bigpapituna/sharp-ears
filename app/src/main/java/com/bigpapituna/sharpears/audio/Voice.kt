package com.bigpapituna.sharpears.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.SpeechQueue
import java.util.Locale

/**
 * Spoken guidance. With the natural voice on (the default), lines go to [NaturalVoice] (Kokoro, recorded or made on
 * the phone); otherwise, or when it cannot say a line, to the platform TextToSpeech, started on first use.
 *
 * The mic rule: while [mic] listens and no headphones are on, a line is held (the mic would hear the phone)
 * and spoken when the mic stops. Screens that start the mic wait for [holdsMic] to clear first, so a step's
 * text is spoken before its listening starts.
 */
class Voice(private val context: Context, private val prefs: Prefs, private val mic: MicInput) {
    /** True while a line is being spoken. */
    val speaking = mutableStateOf(false)
    /**
     * 2.15: the Plain look is on for the profile (set by MainActivity: PlainLook.on, never for a little kid). Spoken guidance
     * is then off unless turned on there (PlainLook.voiceOn), and a line said goes through the plain bank (PlainLook.calm).
     */
    @Volatile var plain: () -> Boolean = { false }
    val enabled get() = com.bigpapituna.sharpears.engine.family.PlainLook.voiceOn(plain(), prefs.voice, prefs.plainVoice)
    private fun plainText(text: String) = if (plain()) com.bigpapituna.sharpears.engine.family.PlainLook.calm(text, spoken = true) else text

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue()
    private var tts: TextToSpeech? = null
    private var ready = false
    /** The platform engine did not start (none installed, or disabled): its lines are dropped instead of waiting forever. */
    private var failed = false
    private val beforeReady = mutableListOf<Pair<String, Boolean>>()
    private val inFlight = mutableSetOf<String>()
    private var nextId = 0

    private val natural: NaturalVoice = NaturalVoice(context) { main.post { settle(inFlight.isNotEmpty() || natural.busy) } }

    /** A line is being said over the listening mic ([speakOverMic]): it never holds the mic, which is deaf meanwhile. */
    private var overMic = false

    /** The voice is (or is not) talking now; a line said over the mic lets the mic hear again when it ends. */
    private fun settle(now: Boolean) {
        speaking.value = now
        if (!now && overMic) { overMic = false; mic.deafWhileSpeaking(false) }
    }

    /** The Kokoro model is on the phone (else only the recorded lines use the natural voice). */
    val naturalModelInstalled get() = natural.modelInstalled

    /**
     * How fast lines made on the phone are spoken: 1 for grown-ups, 0.85 for kids (KIDS-PLAN 3.8, "big and slow";
     * MainActivity.openApp sets it per profile). Recorded lines carry their own speed (render_voice.py renders the
     * kid lines at 0.85). Lines with a kid's name in them are always made on the phone, so they follow this.
     */
    @Volatile var speed: Float = 1f

    init {
        mic.onStopped = ::micStopped
        if (enabled && prefs.naturalVoice) natural.warmUp()   // loads in the background while the first screen opens
    }

    /** The voice is talking through the speaker: the mic should not start yet. Lines said over the mic do not hold it. */
    val holdsMic get() = speaking.value && !mic.headphonesOn() && !overMic

    private fun canSpeak() = !mic.listening || mic.headphonesOn()

    /**
     * Says [text]. [interrupt] cuts off whatever is being said or waiting. [deferrable] false drops the line
     * (instead of holding it) when the mic is listening, for lines that are stale a moment later.
     */
    fun speak(text: String, interrupt: Boolean = false, deferrable: Boolean = true) {
        if (!enabled) { Log.d(TAG, "voice: (off) $text"); return }
        @Suppress("NAME_SHADOWING") val text = plainText(text)
        val now = System.currentTimeMillis()
        val lines = queue.offer(text, now, canSpeak(), interrupt, deferrable)
        if (lines.isEmpty()) {
            Log.d(TAG, "voice: ${if (deferrable) "held while the mic listens" else "dropped, the mic listens"}: $text")
            return
        }
        lines.forEachIndexed { i, l -> say(l, flush = interrupt && i == 0) }
    }

    /**
     * Says [text] now although the mic is listening (2.1.1: the Playground's call-outs between rounds and the "Let me
     * show you" of a demo): the mic is deaf while the line plays (the phone's speaker is next to it) and hears again
     * shortly after it ends. With the mic off, or headphones on, it is an ordinary line.
     */
    fun speakOverMic(text: String) {
        if (!enabled) { Log.d(TAG, "voice: (off) $text"); return }
        @Suppress("NAME_SHADOWING") val text = plainText(text)
        if (!mic.listening || mic.headphonesOn()) { speak(text); return }
        // Only a line that can start now: a call-out said seconds later, once the platform voice has started, is stale.
        if (!(prefs.naturalVoice && natural.canSay(text)) && !ready) { engine(); Log.d(TAG, "voice: over the mic, not ready yet, dropped: $text"); return }
        overMic = true
        mic.deafWhileSpeaking(true)
        Log.d(TAG, "voice: over the mic, deaf meanwhile: $text")
        say(text, flush = false)
    }

    /**
     * 2.15: says [text] over the mic only in the pup's own voice (its recording, or the Kokoro model on the phone); otherwise
     * nothing, and the screen's words stand alone (the "could not hear" hint: a kid hears the pup or reads it, never the
     * platform voice). Returns whether it is said.
     */
    fun sayInPupVoice(text: String): Boolean {
        if (!enabled || !(prefs.naturalVoice && natural.canSay(text))) { Log.d(TAG, "voice: shown, not said (no recording): $text"); return false }
        speakOverMic(text)
        return true
    }

    fun stop() {
        queue.clear()
        beforeReady.clear()
        tts?.stop()
        natural.stop()
        inFlight.clear()
        settle(false)
        Log.d(TAG, "voice: stop")
    }

    fun release() {
        natural.release()
        tts?.shutdown()
        tts = null
        ready = false
        mic.onStopped = null
    }

    private fun micStopped() {
        val lines = queue.release(System.currentTimeMillis())
        if (lines.isNotEmpty()) Log.d(TAG, "voice: mic stopped, speaking ${lines.size} held line(s)")
        if (enabled) lines.forEach { say(it, flush = false) }
    }

    private fun say(text: String, flush: Boolean) {
        // ponytail: the two voices queue separately, so a natural line and a platform line can overlap. Only when the
        // model is missing and a line is not recorded; install the model rather than syncing the queues.
        if (prefs.naturalVoice && natural.canSay(text)) {
            if (flush) { tts?.stop(); inFlight.clear(); beforeReady.clear() }
            speaking.value = true
            natural.say(text, flush, speed)
            return
        }
        if (flush) natural.stop()
        if (failed) { Log.d(TAG, "voice: no speech engine, dropped: $text"); return }
        val t = engine()
        // Until the engine is up, the line waits; [speaking] is already true so no screen starts the mic meanwhile.
        if (!ready) { if (flush) beforeReady.clear(); beforeReady += text to flush; speaking.value = true; return }
        val id = "u${nextId++}"
        if (flush) inFlight.clear()
        inFlight += id
        speaking.value = true
        Log.d(TAG, "voice: $text")
        t.setSpeechRate(0.95f * speed)
        t.speak(text, if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, id)
    }

    /** One selectable voice: the engine's id and a readable label ("US English 3"). */
    data class Option(val name: String, val label: String)

    /** Offline English voices of the running engine, best first. Empty until the engine has started. */
    fun voices(): List<Option> {
        val t = tts?.takeIf { ready } ?: return emptyList()
        val all = goodVoices(t)
        val counts = mutableMapOf<String, Int>()
        return all.map { v ->
            val region = when (v.locale.country) { "US" -> "US"; "GB" -> "British"; "CA" -> "Canadian"; "AU" -> "Australian"; "IN" -> "Indian"
                else -> v.locale.getDisplayCountry(Locale.ENGLISH).ifEmpty { "Other" } }
            val n = counts.merge(region, 1, Int::plus)!!
            Option(v.name, "$region voice $n")
        }
    }

    /** The voice in use, by its engine id. */
    fun currentVoice(): String? = tts?.takeIf { ready }?.voice?.name

    /** Switches to [name], saves it, and says a short sample line in it. */
    fun choose(name: String) {
        val t = tts?.takeIf { ready } ?: return
        t.voices?.firstOrNull { it.name == name }?.let { t.voice = it }
        prefs.voiceName = name
        Log.d(TAG, "voice: chose $name")
        speak("Ready when you are. Play the first note.", interrupt = true)
    }

    /** Starts the engine early (Settings opens the voice list), without speaking. */
    fun warmUp() { engine() }

    /**
     * Voices worth offering: English, installed on the phone (no network: the privacy page promises that speech
     * stays on the phone), best quality first, US and Canadian English ahead of the rest.
     */
    private fun goodVoices(t: TextToSpeech): List<android.speech.tts.Voice> =
        t.voices.orEmpty()
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty() &&
                !it.name.endsWith("-language") }          // Google's alias for its default voice; the real one is listed too
            .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }
                .thenBy { if (it.locale.country in setOf("US", "CA")) 0 else 1 }
                .thenBy { it.latency }
                .thenBy { it.name })

    /** Google's speech engine when the phone has it (its voices sound far less robotic than Samsung's), else the default. */
    private fun preferredEngine(): String? = GOOGLE_TTS.takeIf {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }

    private fun engine(): TextToSpeech = tts ?: TextToSpeech(context.applicationContext, { status ->
        main.post {
            val t = tts ?: return@post
            if (status != TextToSpeech.SUCCESS) {
                // The lines waiting for it never come: [speaking] would stay true and hold the mic for good.
                Log.d(TAG, "voice: engine failed to start ($status), ${beforeReady.size} waiting line(s) dropped")
                com.bigpapituna.sharpears.analytics.Errors.report("voice_engine", IllegalStateException("the speech engine did not start, status $status"), mapOf("code" to status))
                failed = true
                beforeReady.clear()
                settle(inFlight.isNotEmpty() || natural.busy)
                return@post
            }
            val lang = t.setLanguage(Locale.US).let { if (it < 0) t.setLanguage(Locale.getDefault()) else it }
            // The saved voice if it still exists; otherwise Roy's pick (US voice 6 in the list), otherwise the first
            // offline English voice (always a real, named voice, so the picker can show which one is in use).
            val voices = goodVoices(t)
            (voices.firstOrNull { it.name == prefs.voiceName } ?: voices.firstOrNull { it.name == DEFAULT_VOICE } ?: voices.firstOrNull())
                ?.let { t.voice = it }
            Log.d(TAG, "voice: using ${t.voice?.name} (quality ${t.voice?.quality}) of ${voices.size} offline English voices: " +
                voices.joinToString { "${it.name} q${it.quality} l${it.latency}" })
            t.setSpeechRate(0.95f)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) { finished(utteranceId, "done") }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) { finished(utteranceId, "error") }
                override fun onError(utteranceId: String, errorCode: Int) {
                    finished(utteranceId, "error $errorCode")
                    com.bigpapituna.sharpears.analytics.Errors.report("voice_utterance", IllegalStateException("the speech engine failed a line, error $errorCode"), mapOf("code" to errorCode))
                }
                override fun onStop(utteranceId: String, interrupted: Boolean) { finished(utteranceId, "stopped") }
            })
            ready = true
            Log.d(TAG, "voice: engine ready (${t.defaultEngine}, language result $lang)")
            val waiting = beforeReady.toList(); beforeReady.clear()
            settle(false)
            waiting.forEach { (text, flush) -> speak(text, interrupt = flush) }
            engineReady.value = true
        }
    }, preferredEngine()).also { tts = it; Log.d(TAG, "voice: starting the engine (${preferredEngine() ?: "phone default"})") }

    /** Becomes true once the engine has started (the Settings voice list waits for it). */
    val engineReady = mutableStateOf(false)

    private companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
        /** The starting voice: Google's offline US voice "tpc" (US voice 6 in the picker), chosen by Roy by ear. */
        const val DEFAULT_VOICE = "en-us-x-tpc-local"
    }

    private fun finished(id: String, how: String) = main.post {
        val removed = inFlight.remove(id)
        if (removed && how != "done") Log.d(TAG, "voice: $id $how")
        settle(inFlight.isNotEmpty() || natural.busy)
        if (removed && inFlight.isEmpty()) Log.d(TAG, "voice: finished")
    }
}
