package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.voice.Speaker
import com.bigpapituna.sharpears.engine.voice.Utterance
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import java.io.File
import java.util.concurrent.Executors

/**
 * The natural (Kokoro) voice made on the phone, for lines nobody recorded (a kid's name in them): a speaker of the speaking
 * policy (2.18, engine/voice/SpeakingPolicy.kt; was the model half of NaturalVoice, whose recorded half is audio/ClipPlayer.kt).
 * The model lives in files/kokoro (scripts/install-voice.ps1 puts it there) and is optional: without it the policy picks the
 * recording or the phone's voice. Generated on a worker thread, sentence by sentence; its callbacks come to the main thread.
 */
class KokoroSpeaker(private val context: Context) : Speaker {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "natural-voice") }
    private val main = Handler(Looper.getMainLooper())
    /** The Kokoro speaker the recordings were made with (scripts/render_voice.py writes it), so both halves match. */
    private val speaker = runCatching { context.assets.open("voice/speaker.txt").reader().use { it.readText() }.trim().toInt() }.getOrDefault(3)
    private val modelDir = File(context.filesDir, "kokoro")
    // 2.18.1: the piano's usage (Stage.kt), so the voice gets the same stereo speakers and processing as the notes; as
    // ACCESSIBILITY it went out the primary path in mono and sounded like the earpiece on Roy's Fold (dumpsys, 2026-10-28)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    @Volatile private var generation = 0
    /** Loaded once, on first use; [warmUp] starts it on its own thread so recorded lines never wait for it. */
    private val modelLoad = lazy { loadModel() }
    @Volatile private var modelFailed = false

    val installed get() = File(modelDir, "model.int8.onnx").exists()
    /** The model is on the phone and did not fail to load. */
    val usable get() = installed && !modelFailed

    override fun say(u: Utterance, started: (Long?) -> Unit, ended: (Boolean) -> Unit) {
        if (worker.isShutdown) { Log.d(TAG, "voice: released, dropped: ${u.text}"); ended(false); return }   // a stale screen after a profile switch
        val gen = generation
        worker.execute {
            var ok = false
            try { if (gen == generation) ok = speakNow(u.text, gen, u.speed) { main.post { started(null) } } }
            catch (e: Exception) { Log.w(TAG, "voice: natural voice failed on \"${u.text}\"", e); com.bigpapituna.sharpears.analytics.Errors.report("natural_voice", e) }
            finally { main.post { ended(ok) } }
        }
    }

    /** Cuts off the line being made or played. */
    override fun stop() { generation++ }

    /** Loads the model ahead of the first generated line (a second or two), without speaking. */
    fun warmUp() { if (installed) { warming = true; Thread({ modelLoad.value }, "natural-voice-load").start() } }
    /** [warmUp] started loading: [release] must wait for that model and free it (a profile switch mid-load leaked it). */
    @Volatile private var warming = false

    fun release() {
        stop()
        worker.execute { if (modelLoad.isInitialized() || warming) modelLoad.value?.release() }
        worker.shutdown()
    }

    /** Makes and plays [text]; true when it played to its end. */
    private fun speakNow(text: String, gen: Int, speed: Float, started: () -> Unit): Boolean {
        val tts = modelLoad.value ?: return false
        // Sentence by sentence: the first one plays while the next is made.
        val rate = tts.sampleRate()
        val track = AudioTrack.Builder().setAudioAttributes(attrs)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(rate * 4 * 30)      // 30 s, so writing never waits on playback
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        try {
            val t0 = System.currentTimeMillis()
            var written = 0
            var playStart = 0L
            // An object, not a lambda: the native side looks up invoke(float[]): Integer by name, which a lambda lacks.
            tts.generateWithCallback(text, sid = speaker, speed = speed, callback = object : (FloatArray) -> Int {
                override fun invoke(chunk: FloatArray): Int {
                    if (gen != generation) return 0
                    if (written == 0) { Log.d(TAG, "voice: (first sound in ${System.currentTimeMillis() - t0} ms) $text"); track.play(); playStart = System.currentTimeMillis(); started() }
                    track.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                    written += chunk.size
                    return 1
                }
            })
            // The head position can stall short of [written] once the track runs dry (the Fold7 did, 2026-10-22: Pup says
            // waited for this line for good), so the wait also ends when the audio's own length has passed.
            val deadline = playStart + written * 1000L / rate + 1500
            while (gen == generation && track.playbackHeadPosition < written && System.currentTimeMillis() < deadline) Thread.sleep(20)
            if (gen == generation && track.playbackHeadPosition < written) Log.d(TAG, "voice: ended by the clock, head ${track.playbackHeadPosition} of $written: $text")
            return gen == generation && written > 0
        } finally { track.pause(); track.flush(); track.release() }
    }

    private fun loadModel(): OfflineTts? {
        if (!installed) return null
        val d = modelDir.absolutePath
        val en = if (speaker in 20..27) "gb" else "us"     // Kokoro's British voices (bf_, bm_) read with the British lexicon
        val t0 = System.currentTimeMillis()
        val model = runCatching {
            OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(model = "$d/model.int8.onnx", voices = "$d/voices.bin", tokens = "$d/tokens.txt",
                    dataDir = "$d/espeak-ng-data", lexicon = "$d/lexicon-$en-en.txt", lang = "en-$en"),
                numThreads = 4)))
        }.onFailure { modelFailed = true; Log.w(TAG, "voice: the Kokoro model did not load", it); com.bigpapituna.sharpears.analytics.Errors.report("natural_voice_load", it) }.getOrNull()
        model?.let { Log.d(TAG, "voice: Kokoro loaded in ${System.currentTimeMillis() - t0} ms, speaker $speaker, ${it.sampleRate()} Hz") }
        return model
    }
}
