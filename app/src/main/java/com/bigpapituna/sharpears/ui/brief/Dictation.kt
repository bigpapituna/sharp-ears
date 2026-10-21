package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.brief.DictationText

/**
 * Speech to text for the question box, through Android's SpeechRecognizer, kept on the phone: where the system has an
 * on-device recogniser (Android 13 and later; 12 may create one) it is the only kind used, and no recogniser is ever asked to
 * use the network on purpose (EXTRA_PREFER_OFFLINE). A phone with none simply has no microphone button ([available] false):
 * the field alone works. Nothing is recorded or kept by the app; the text lands in the field for the grown-up to read, edit
 * and send.
 */
@Stable
class Dictation(private val context: Context) {
    var listening by mutableStateOf(false)
        private set
    /** What to tell the grown-up (null: nothing). */
    var message by mutableStateOf<String?>(null)

    private var recognizer: SpeechRecognizer? = null

    /** True when this phone can turn speech into text without sending it anywhere. */
    val available: Boolean = Companion.available(context)

    /**
     * Starts listening. [base] is what the field already holds; [onText] gets base plus what is heard so far, as it comes
     * (partial results) and when the phrase ends.
     */
    fun start(base: String, onText: (String) -> Unit) {
        if (!available || listening) return
        message = null
        val r = runCatching {
            if (Build.VERSION.SDK_INT >= 33 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            else SpeechRecognizer.createSpeechRecognizer(context)
        }.getOrNull()
        if (r == null) { message = "Speech is not available on this phone. You can type instead."; return }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstHeard()?.let { onText(DictationText.append(base, it)) }
            }
            override fun onResults(results: Bundle?) {
                results.firstHeard()?.let { onText(DictationText.append(base, it)) }
                finish()
            }
            override fun onError(error: Int) {
                Log.d(TAG, "brief: dictation error $error")
                message = DictationText.errorMessage(error)
                finish()
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        listening = true
        runCatching { r.startListening(intent) }.onFailure { message = "Speech is not working just now. You can type instead."; finish() }
    }

    /** Stops listening; what was heard so far still arrives. */
    fun stop() { runCatching { recognizer?.stopListening() } }

    private fun finish() {
        listening = false
        recognizer?.let { r -> runCatching { r.destroy() } }
        recognizer = null
    }

    fun destroy() { finish() }

    private fun Bundle?.firstHeard(): String? = this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** Whether this phone can recognise speech on the phone itself (see the class note). */
        fun available(context: Context): Boolean = runCatching {
            if (Build.VERSION.SDK_INT >= 33) SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            else SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)
    }
}


/** A [Dictation] for the screen that is open, destroyed when it closes. */
@Composable
fun rememberDictation(): Dictation {
    val context = LocalContext.current
    val d = remember(context) { Dictation(context) }
    DisposableEffect(d) { onDispose { d.destroy() } }
    return d
}
