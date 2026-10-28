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
import com.bigpapituna.sharpears.engine.voice.Speaker
import com.bigpapituna.sharpears.engine.voice.Utterance
import java.util.Locale

/**
 * 2.18: the phone's own speech engine (TextToSpeech) as a speaker of the speaking policy (engine/voice/SpeakingPolicy.kt), was
 * the second half of audio/Voice.kt. Started on first use (Google's engine when the phone has it); a line said before it is up
 * waits for it; an engine that fails ends its lines at once (false) instead of holding the mic for good. Settings' voice list
 * reads [voices] once [engineReady]. Callbacks come to the main thread.
 */
class SystemSpeaker(private val context: Context, private val prefs: Prefs) : Speaker {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    /** The engine has started. */
    var ready = false
        private set
    /** The platform engine did not start (none installed, or disabled): its lines end at once instead of waiting forever. */
    private var failed = false
    private class Line(val u: Utterance, val started: (Long?) -> Unit, val ended: (Boolean) -> Unit)
    /** The line said before the engine was up (the policy hands one line at a time). */
    private var waiting: Line? = null
    private val inFlight = HashMap<String, Line>()

    /** Becomes true once the engine has started (the Settings voice list waits for it). */
    val engineReady = mutableStateOf(false)

    override fun say(u: Utterance, started: (Long?) -> Unit, ended: (Boolean) -> Unit) {
        if (failed) { Log.d(TAG, "voice: no speech engine, dropped: ${u.text}"); ended(false); return }
        val t = engine()
        val line = Line(u, started, ended)
        if (!ready) { waiting = line; return }
        speakNow(t, line)
    }

    private fun speakNow(t: TextToSpeech, line: Line) {
        val id = "u${line.u.id}"
        inFlight[id] = line
        Log.d(TAG, "voice: ${line.u.text}")
        t.setSpeechRate(0.95f * line.u.speed)
        if (t.speak(line.u.text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) finished(id, "error (speak)")
    }

    override fun stop() {
        val w = waiting
        waiting = null
        tts?.stop()
        val all = inFlight.values.toList()
        inFlight.clear()
        w?.ended?.invoke(false)
        all.forEach { it.ended(false) }
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    /** Starts the engine early (Settings opens the voice list, a line over the mic found it not ready), without speaking. */
    fun warmUp() { engine() }

    /** One selectable voice: the engine's id and a readable label ("US English 3"). */
    data class Option(val name: String, val label: String)

    /** Offline English voices of the running engine, best first. Empty until the engine has started. */
    fun voices(): List<Option> {
        val t = tts?.takeIf { ready } ?: return emptyList()
        val counts = mutableMapOf<String, Int>()
        return goodVoices(t).map { v ->
            val region = when (v.locale.country) { "US" -> "US"; "GB" -> "British"; "CA" -> "Canadian"; "AU" -> "Australian"; "IN" -> "Indian"
                else -> v.locale.getDisplayCountry(Locale.ENGLISH).ifEmpty { "Other" } }
            val n = counts.merge(region, 1, Int::plus)!!
            Option(v.name, "$region voice $n")
        }
    }

    /** The voice in use, by its engine id. */
    fun currentVoice(): String? = tts?.takeIf { ready }?.voice?.name

    /** Switches to [name] and saves it; false when the engine is not up. */
    fun choose(name: String): Boolean {
        val t = tts?.takeIf { ready } ?: return false
        t.voices?.firstOrNull { it.name == name }?.let { t.voice = it }
        prefs.voiceName = name
        Log.d(TAG, "voice: chose $name")
        return true
    }

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
                // The line waiting for it never comes: it ends now, so the voice never holds the mic for good.
                Log.d(TAG, "voice: engine failed to start ($status), ${if (waiting != null) 1 else 0} waiting line(s) dropped")
                com.bigpapituna.sharpears.analytics.Errors.report("voice_engine", IllegalStateException("the speech engine did not start, status $status"), mapOf("code" to status))
                failed = true
                val w = waiting
                waiting = null
                w?.ended?.invoke(false)
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
                override fun onStart(utteranceId: String) { main.post { inFlight[utteranceId]?.started?.invoke(null) } }
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
            waiting?.let { w -> waiting = null; speakNow(t, w) }
            engineReady.value = true
        }
    }, preferredEngine()).also { tts = it; Log.d(TAG, "voice: starting the engine (${preferredEngine() ?: "phone default"})") }

    private fun finished(id: String, how: String) = main.post {
        val line = inFlight.remove(id) ?: return@post
        if (how != "done") Log.d(TAG, "voice: $id $how")
        if (inFlight.isEmpty()) Log.d(TAG, "voice: finished")
        line.ended(how == "done")
    }

    private companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
        /** The starting voice: Google's offline US voice "tpc" (US voice 6 in the picker), chosen by Roy by ear. */
        const val DEFAULT_VOICE = "en-us-x-tpc-local"
    }
}
