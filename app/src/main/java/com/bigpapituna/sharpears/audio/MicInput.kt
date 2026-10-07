package com.bigpapituna.sharpears.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.MatcherConfig
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.File

/**
 * The microphone path: capture thread -> [NoteMatcher] -> [events]. A debug injector feeds a WAV file or
 * synthesized notes through the same matcher at real-time pace, and a recorder saves mic audio plus MIDI
 * truth as test fixtures.
 */
class MicInput(private val context: Context) {
    companion object { const val RATE = 48_000 }

    private val prefs = context.getSharedPreferences("mic", Context.MODE_PRIVATE)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())

    val matcher = NoteMatcher(RATE, MatcherConfig(
        sensitivityDb = prefs.getFloat("sensitivityDb", MatcherConfig().sensitivityDb.toFloat()).toDouble(),
        wrongOnsetDb = prefs.getFloat("wrongOnsetDb", MatcherConfig().wrongOnsetDb.toFloat()).toDouble(),
    ))
    private val debuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    // Detailed matcher reasons cost real CPU (string formatting every hop); opt in with:
    //   adb shell setprop log.tag.SharpEarsTrace VERBOSE     (then restart the mic)
    private fun traceOn() = debuggable && Log.isLoggable("SharpEarsTrace", Log.VERBOSE)
    private val _events = MutableSharedFlow<MicEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MicEvent> = _events

    /** For Compose: loudness in dBFS (about 15 updates a second), the active source, and the recording label. */
    val level = mutableFloatStateOf(-120f)
    val source = mutableStateOf<String?>(null)
    val recording = mutableStateOf<String?>(null)

    /**
     * Red flash for wrong notes heard by the mic. Off by default: in a room with talking or TV, loudness jumps
     * cannot be told apart from wrong piano notes (57 false flashes in 54 s of Roy's session).
     */
    // ponytail: on/off only; naming the wrong note (a semitone-grid search) is the upgrade
    var wrongFlash: Boolean
        get() = prefs.getBoolean("wrongFlash", false)
        set(v) { prefs.edit().putBoolean("wrongFlash", v).apply() }

    var micAddress: String?
        get() = prefs.getString("micAddress", null)
        set(v) { prefs.edit().putString("micAddress", v).apply() }

    fun saveConfig(cfg: MatcherConfig) {
        matcher.config = cfg
        prefs.edit().putFloat("sensitivityDb", cfg.sensitivityDb.toFloat()).putFloat("wrongOnsetDb", cfg.wrongOnsetDb.toFloat()).apply()
    }

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Built-in microphones as (address, label), e.g. ("bottom", "Bottom mic"). */
    fun builtInMics(): List<Pair<String, String>> =
        audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
            .map { it.address to (it.address.replaceFirstChar(Char::uppercase).ifBlank { "Built-in" } + " mic") }
            .distinctBy { it.first }

    /** Headphones of any kind: then the app may make sound while the mic listens. */
    fun headphonesOn() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
        it.type in setOf(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private var thread: Thread? = null
    @Volatile private var stop = false

    /**
     * Starts the real microphone. Returns false without permission or if the device refuses.
     * [autoRecord]: debug builds save the session for offline replay (the setup screen records explicitly instead).
     */
    fun start(autoRecord: Boolean = true): Boolean {
        if (thread != null) return true
        if (!hasPermission()) return false
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val src = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val format = AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build()
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val rec = try {
            @Suppress("MissingPermission")   // checked above
            AudioRecord.Builder().setAudioSource(src).setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, 8 * 512 * 4)).build()
        } catch (e: Exception) { Log.d(TAG, "mic: open failed: $e"); return false }
        val wanted = micAddress
        val dev = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC && (wanted == null || it.address == wanted) }
        if (dev != null) rec.preferredDevice = dev
        val label = "${dev?.address?.ifBlank { null } ?: "default"} mic, ${if (unprocessed) "unprocessed" else "voice recognition"}"
        Log.d(TAG, "mic: start $label, minBuf=$minBuf")
        launch(label) { loopMic(rec) }
        if (debuggable && autoRecord) autoRecord()
        return true
    }

    /** Debug: feeds a WAV file, or "synth:76,75,60+64+67[@600]" (notes or chords, [ms apart]), through the matcher. */
    fun startInjection(spec: String) {
        if (thread != null) stop()
        launch("injected $spec") { loopInjected(spec) }
    }

    fun stop() {
        if (recording.value?.startsWith("session-") == true) stopRecording()
        stop = true
        thread?.join(1000)
        thread = null
        main.post { source.value = null; level.floatValue = -120f }
    }

    private fun launch(label: String, body: () -> Unit) {
        stop = false
        matcher.reset()
        matcher.trace = if (traceOn()) { msg -> Log.d(TAG, "matcher $msg") } else null
        source.value = label
        thread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try { body() } catch (e: Exception) { Log.d(TAG, "mic: thread failed: $e") }
        }, "sharpears-mic").apply { start() }
    }

    // ---- capture loops ----

    private var framesRead = 0L
    private var lastLevelPost = 0L
    // Latest mapping between a captured frame and System.nanoTime(), for placing MIDI truth in recordings.
    @Volatile private var clockFrame = 0L
    @Volatile private var clockNanos = 0L

    private fun loopMic(rec: AudioRecord) {
        rec.startRecording()
        val buf = FloatArray(512)
        framesRead = 0
        try {
            while (!stop) {
                val n = rec.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n <= 0) { Log.d(TAG, "mic: read returned $n"); break }
                framesRead += n
                // Our own read clock: the newest frame read arrived "now". AudioRecord.getTimestamp's framePosition
                // on the Fold7 counts from an earlier start than our reads (it put MIDI truth 1.5 s early), so it
                // is not used; the read clock is off only by the capture buffer (tens of ms).
                clockFrame = framesRead; clockNanos = System.nanoTime()
                handle(buf, n)
            }
        } finally {
            rec.stop(); rec.release()
            Log.d(TAG, "mic: stopped after ${framesRead / RATE} s")
        }
    }

    private fun loopInjected(spec: String) {
        val samples = if (spec.startsWith("synth:")) {
            val (list, step) = spec.removePrefix("synth:").split("@").let { it[0] to (it.getOrNull(1)?.toDouble() ?: 600.0) }
            // "60+64+67" is a chord: notes struck together.
            val groups = list.split(",")
            val notes = groups.flatMapIndexed { i, g -> g.split("+").map { SynthNote(it.trim().toInt(), 500 + i * step, durationMs = step * 0.9) } }
            PianoSynth.render(notes, 500 + groups.size * step + 1000, RATE)
        } else {
            val w = Wav.read(File(spec))
            if (w.sampleRate == RATE) w.samples else resample(w.samples, w.sampleRate)
        }
        Log.d(TAG, "mic: injecting ${samples.size / RATE.toDouble()} s from $spec")
        val buf = FloatArray(512)
        val t0 = System.nanoTime()
        framesRead = 0
        var i = 0
        while (!stop && i < samples.size) {
            val n = minOf(buf.size, samples.size - i)
            System.arraycopy(samples, i, buf, 0, n)
            i += n; framesRead += n
            clockFrame = framesRead; clockNanos = System.nanoTime()
            handle(buf, n)
            val due = t0 + framesRead * 1_000_000_000L / RATE        // real-time pace
            val wait = (due - System.nanoTime()) / 1_000_000
            if (wait > 0) Thread.sleep(wait)
        }
        Log.d(TAG, "mic: injection finished")
        main.post { source.value = null }
        thread = null
    }

    private fun handle(buf: FloatArray, n: Int) {
        writer?.let { w -> synchronized(this) { w.append(buf, n) } }
        for (e in matcher.process(buf, n)) _events.tryEmit(e)
        if (framesRead - lastLevelPost >= RATE / 15) {
            lastLevelPost = framesRead
            val db = matcher.levelDb.toFloat()
            main.post { level.floatValue = db }
        }
    }

    private fun resample(x: FloatArray, from: Int): FloatArray {
        val out = FloatArray((x.size.toLong() * RATE / from).toInt())
        for (i in out.indices) {
            val p = i.toDouble() * from / RATE
            val k = p.toInt().coerceAtMost(x.size - 2)
            out[i] = (x[k] + (x[k + 1] - x[k]) * (p - k)).toFloat()
        }
        return out
    }

    // ---- fixture recording ----

    /** Debug builds save every live mic session (session-HHmmss.wav + .notes), keeping the last five, for offline replay. */
    private fun autoRecord() {
        val dir = File(context.getExternalFilesDir(null), "fixtures")
        dir.listFiles { f -> f.name.startsWith("session-") }?.sortedBy { it.name }?.dropLast(8)?.forEach { it.delete() }
        // ponytail: keeps the 4 newest sessions (8 files); a size cap if sessions get long
        startRecording("session-" + java.time.LocalTime.now().toString().take(8).replace(":", ""))
    }

    private var writer: Wav.Writer? = null
    private var recordFile: File? = null
    private var recordStartFrame = 0L
    private val truth = StringBuilder()

    /** Starts saving mic audio to fixtures/<label>.wav, with MIDI truth in <label>.notes. Needs the mic running. */
    fun startRecording(label: String): File? {
        if (thread == null) return null
        if (writer != null) stopRecording()
        val dir = File(context.getExternalFilesDir(null), "fixtures").apply { mkdirs() }
        val f = File(dir, "$label.wav")
        synchronized(this) {
            truth.clear()
            truth.append("# sharp ears fixture: sample,midi,velocity (velocity 0 = released)\n")
            truth.append("# mic=${source.value} rate=$RATE recorded=${java.time.LocalDateTime.now()}\n")
            recordStartFrame = framesRead
            recordFile = f
            writer = Wav.Writer(f, RATE)
        }
        recording.value = label
        Log.d(TAG, "mic: recording ${f.absolutePath}")
        return f
    }

    fun stopRecording() {
        val f = synchronized(this) {
            writer?.close(); writer = null
            recordFile.also { recordFile = null }
        } ?: return
        File(f.path.removeSuffix(".wav") + ".notes").writeText(truth.toString())
        recording.value = null
        Log.d(TAG, "mic: saved ${f.name} (${f.length() / 1024} KB) and ${truth.lines().count { it.isNotBlank() && !it.startsWith("#") }} MIDI events")
    }

    /** MIDI truth from [MidiDevices], with the nanoTime the keyboard sent it. */
    fun onMidiTruth(e: NoteEvent, nanos: Long) {
        if (writer == null) return
        val frame = clockFrame + (nanos - clockNanos) * RATE / 1_000_000_000L - recordStartFrame
        val vel = if (e is NoteEvent.On) e.velocity else 0
        synchronized(this) { truth.append("$frame,${e.midi},$vel\n") }
    }
}
