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
 * Spoken guidance through the platform TextToSpeech. Starts on first use, released by the activity.
 *
 * The mic rule: while [mic] listens and no headphones are on, a line is held (the mic would hear the phone)
 * and spoken when the mic stops. Screens that start the mic wait for [holdsMic] to clear first, so a step's
 * text is spoken before its listening starts.
 */
class Voice(private val context: Context, private val prefs: Prefs, private val mic: MicInput) {
    /** True while a line is being spoken. */
    val speaking = mutableStateOf(false)
    val enabled get() = prefs.voice

    private val main = Handler(Looper.getMainLooper())
    private val queue = SpeechQueue()
    private var tts: TextToSpeech? = null
    private var ready = false
    private val beforeReady = mutableListOf<Pair<String, Boolean>>()
    private val inFlight = mutableSetOf<String>()
    private var nextId = 0

    init { mic.onStopped = ::micStopped }

    /** The voice is talking through the speaker: the mic should not start yet. */
    val holdsMic get() = speaking.value && !mic.headphonesOn()

    private fun canSpeak() = !mic.listening || mic.headphonesOn()

    /**
     * Says [text]. [interrupt] cuts off whatever is being said or waiting. [deferrable] false drops the line
     * (instead of holding it) when the mic is listening, for lines that are stale a moment later.
     */
    fun speak(text: String, interrupt: Boolean = false, deferrable: Boolean = true) {
        if (!enabled) { Log.d(TAG, "voice: (off) $text"); return }
        val now = System.currentTimeMillis()
        val lines = queue.offer(text, now, canSpeak(), interrupt, deferrable)
        if (lines.isEmpty()) {
            Log.d(TAG, "voice: ${if (deferrable) "held while the mic listens" else "dropped, the mic listens"}: $text")
            return
        }
        lines.forEachIndexed { i, l -> say(l, flush = interrupt && i == 0) }
    }

    fun stop() {
        queue.clear()
        beforeReady.clear()
        tts?.stop()
        inFlight.clear()
        speaking.value = false
        Log.d(TAG, "voice: stop")
    }

    fun release() {
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
        val t = engine()
        // Until the engine is up, the line waits; [speaking] is already true so no screen starts the mic meanwhile.
        if (!ready) { if (flush) beforeReady.clear(); beforeReady += text to flush; speaking.value = true; return }
        val id = "u${nextId++}"
        if (flush) inFlight.clear()
        inFlight += id
        speaking.value = true
        Log.d(TAG, "voice: $text")
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
            if (status != TextToSpeech.SUCCESS) { Log.d(TAG, "voice: engine failed to start ($status)"); return@post }
            val lang = t.setLanguage(Locale.US).let { if (it < 0) t.setLanguage(Locale.getDefault()) else it }
            // The saved voice if it still exists, otherwise the first of the offline English voices (always a real,
            // named voice, so the picker can show which one is in use).
            val voices = goodVoices(t)
            (voices.firstOrNull { it.name == prefs.voiceName } ?: voices.firstOrNull())?.let { t.voice = it }
            Log.d(TAG, "voice: using ${t.voice?.name} (quality ${t.voice?.quality}) of ${voices.size} offline English voices: " +
                voices.joinToString { "${it.name} q${it.quality} l${it.latency}" })
            t.setSpeechRate(0.95f)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String) {}
                override fun onDone(utteranceId: String) { finished(utteranceId, "done") }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String) { finished(utteranceId, "error") }
                override fun onError(utteranceId: String, errorCode: Int) { finished(utteranceId, "error $errorCode") }
                override fun onStop(utteranceId: String, interrupted: Boolean) { finished(utteranceId, "stopped") }
            })
            ready = true
            Log.d(TAG, "voice: engine ready (${t.defaultEngine}, language result $lang)")
            val waiting = beforeReady.toList(); beforeReady.clear()
            speaking.value = false
            waiting.forEach { (text, flush) -> speak(text, interrupt = flush) }
            engineReady.value = true
        }
    }, preferredEngine()).also { tts = it; Log.d(TAG, "voice: starting the engine (${preferredEngine() ?: "phone default"})") }

    /** Becomes true once the engine has started (the Settings voice list waits for it). */
    val engineReady = mutableStateOf(false)

    private companion object { const val GOOGLE_TTS = "com.google.android.tts" }

    private fun finished(id: String, how: String) = main.post {
        val removed = inFlight.remove(id)
        if (removed && how != "done") Log.d(TAG, "voice: $id $how")
        speaking.value = inFlight.isNotEmpty()
        if (removed && inFlight.isEmpty()) Log.d(TAG, "voice: finished")
    }
}
