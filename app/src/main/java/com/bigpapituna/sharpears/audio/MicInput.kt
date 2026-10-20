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
    companion object {
        const val RATE = 48_000
        /** Space for stuck-moment clips (WAV, ~5.6 MB a minute); the oldest are deleted past it. */
        const val STALL_BUDGET = 250L * 1024 * 1024
        /** The mic stays deaf this long after a line said over it ends. */
        const val VOICE_TAIL_MS = 300L
    }

    private val prefs = context.getSharedPreferences("mic", Context.MODE_PRIVATE)
    /** Sends saved stuck moments, flags and crashes to the private GitHub repo (when set up; see [Reporter]). */
    val reporter = com.bigpapituna.sharpears.data.Reporter(context)
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
     * Show wrong keys heard by the mic (2.1: on by default for everyone, the switch stays in Microphone setup). The
     * mic names a wrong key only on a confident piano strike (MicEvent.Wrong); talking and TV go to [noise] instead.
     * Roy's scale session with talking in the room: 57 false flashes when every loudness jump was one, 0 now.
     * A noisy room can still stop the painting for the session ([wrongPaintOff], the room-noise watchdog).
     */
    var wrongFlash: Boolean
        get() = prefs.getBoolean("wrongFlash", true)
        set(v) { prefs.edit().putBoolean("wrongFlash", v).apply() }

    /**
     * "Stop for this session" from the room-noise watchdog: no wrong keys painted until the app closes or someone else
     * starts playing ([forProfile] clears it: the next player may be in a quieter room). Never stored.
     */
    @Volatile var wrongPaintOff = false
    /** Whether a wrong key the mic hears is painted now: the setting, unless this session turned it off. */
    val paintsWrongKeys get() = wrongFlash && !wrongPaintOff
    /** The room-noise watchdog for this app session (the player feeds it and asks it; see engine/hints/NoiseWatch). */
    val noiseWatch = com.bigpapituna.sharpears.engine.hints.NoiseWatch()

    /**
     * The person playing: [kid] goes into every report this mic saves as "profile=kid|adult" (never the name), and
     * [micOffsetDb] (Profile.micOffsetDb, +3 for kids) makes the matcher that much easier. Call it whenever the
     * profile opens or changes; the stored sensitivity setting is untouched.
     */
    fun forProfile(kid: Boolean, micOffsetDb: Double, newSession: Boolean = false) {
        if (newSession && wrongPaintOff) { wrongPaintOff = false; Log.d(TAG, "mic: wrong keys painted again (new player)") }
        profileKind = com.bigpapituna.sharpears.engine.StallWatch.profileKind(kid)
        matcher.offsetDb = micOffsetDb
        Log.d(TAG, "mic: profile $profileKind, offset $micOffsetDb dB")
    }
    /** "kid" or "adult" ([forProfile]); reports carry it. */
    @Volatile var profileKind: String = "adult"
        private set

    /** Loudness jumps with no expected and no piano note in them (talking, TV, knocks), as matcher samples. */
    private val _noise = MutableSharedFlow<Long>(extraBufferCapacity = 32)
    val noise: SharedFlow<Long> = _noise

    // The octave hint listens on the audio thread (every turned-down expected note); the player asks it.
    private val octave = com.bigpapituna.sharpears.engine.hints.OctaveHint()
    private fun sampleMs(sample: Long) = sample * 1000 / RATE
    init {
        matcher.noise = { s -> if (!deaf) _noise.tryEmit(s) }
        matcher.candidates = { c -> synchronized(octave) { octave.candidate(sampleMs(c.sample), c) } }
    }

    /** A new wait on [expected] (or none): the octave hint starts over. Main thread. */
    fun octaveReset(expected: Set<Int>) = synchronized(octave) { octave.reset(expected) }

    /** The octave hint, once per wait, when the mic has heard an expected note an octave off for about two seconds. */
    fun octaveCheck(): com.bigpapituna.sharpears.engine.hints.OctaveHint.Hint? =
        synchronized(octave) { octave.check(sampleMs(matcher.samplesProcessed)) }

    /**
     * Ignore what the mic hears for [ms] (the app is playing a note through the speaker as a hint: the mic must not
     * take it for the player's). The matcher keeps listening so nothing is stale afterwards; its events are dropped.
     */
    fun deafFor(ms: Long) { deafUntil = maxOf(deafUntil, System.nanoTime() + ms * 1_000_000); Log.d(TAG, "mic: deaf for $ms ms") }
    @Volatile private var deafUntil = 0L

    /**
     * Deaf while the voice says a line over the listening mic ([Voice.speakOverMic], 2.1.1: the Playground's call-outs
     * and demos), and [VOICE_TAIL_MS] after it ends (the room rings on). Set by the voice; a line that never reports its
     * end is cut off by [voiceDeafLimit].
     */
    fun deafWhileSpeaking(on: Boolean) {
        val now = System.nanoTime()
        if (on) { voiceDeaf = true; voiceDeafLimit = now + 8_000_000_000L }
        else if (voiceDeaf) { voiceDeaf = false; deafUntil = maxOf(deafUntil, now + VOICE_TAIL_MS * 1_000_000) }
    }
    @Volatile private var voiceDeaf = false
    @Volatile private var voiceDeafLimit = 0L

    /** Whether what the mic hears is being dropped now (a hint note, a demo, a line over the mic). */
    val deaf: Boolean get() {
        val now = System.nanoTime()
        if (voiceDeaf && now > voiceDeafLimit) voiceDeaf = false
        return now < deafUntil || voiceDeaf
    }

    /**
     * Deaf now or within the last [ms] (for readers of [rawFrames] that decide late, like the pitch tracker, whose note
     * started while the phone was playing).
     */
    fun deafWithin(ms: Long): Boolean = deaf || System.nanoTime() - lastDeafNanos < ms * 1_000_000
    @Volatile private var lastDeafNanos = Long.MIN_VALUE / 2

    /**
     * Raw input for whoever reads the audio itself (the Playground's clap counter and pitch tracker, Clap along, the
     * Jam's chord ear): each sink is called on the audio thread with each chunk as it is read (512 frames at 48 kHz,
     * mono floats -1..1), before note matching. The array is reused for the next chunk, so copy what you keep, and
     * return fast: the capture loop waits for you. No sinks (the default) costs nothing.
     *
     * Several can listen at once (a screen leaving and the next one arriving overlap for the length of the transition),
     * so it is a list: [RawFrames.add] returns the call that removes that sink again.
     */
    val rawFrames = RawFrames()

    /** The sinks of [rawFrames]; safe to add and remove from any thread while the audio thread calls them. */
    class RawFrames {
        private val sinks = java.util.concurrent.CopyOnWriteArrayList<(FloatArray) -> Unit>()
        fun add(sink: (FloatArray) -> Unit): () -> Unit { sinks += sink; return { sinks -= sink } }
        val isEmpty get() = sinks.isEmpty()
        internal fun send(chunk: FloatArray) { for (s in sinks) s(chunk) }
    }

    var micAddress: String?
        get() = prefs.getString("micAddress", null)
        set(v) { prefs.edit().putString("micAddress", v).apply() }

    /**
     * Debug builds only: keep the last few mic sessions on this phone, for tuning note detection offline.
     * Release builds never record. Shown and switchable in Microphone setup.
     */
    val canRecord get() = debuggable
    var keepRecordings: Boolean
        get() = debuggable && prefs.getBoolean("keepRecordings", true)
        set(v) { prefs.edit().putBoolean("keepRecordings", v).apply() }

    /** Saved practice recordings (each session is a .wav with a .notes file). */
    fun recordings(): List<File> =
        File(context.getExternalFilesDir(null), "fixtures").listFiles { f -> f.extension == "wav" }?.sortedBy { it.name }.orEmpty()

    /** Deletes every saved recording; returns how many sessions were removed. */
    fun deleteRecordings(): Int {
        val dir = File(context.getExternalFilesDir(null), "fixtures")
        val n = recordings().size
        dir.listFiles()?.forEach { if (it != recordFile) it.delete() }
        Log.d(TAG, "mic: deleted $n recording(s)")
        return n
    }

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

    @Volatile private var thread: Thread? = null
    @Volatile private var stop = false

    /** True while the mic (or the debug injector) feeds the matcher. The voice holds its lines meanwhile. */
    val listening: Boolean get() = thread != null

    /** Called on the main thread after the mic stops (the voice speaks what it held back). */
    var onStopped: (() -> Unit)? = null

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
        if (keepRecordings && autoRecord) autoRecord()
        return true
    }

    /** Debug: feeds a WAV file, or "synth:76,75,60+64+67,r[@600[~500]]" (notes, chords, rests [ms apart [first at]]), through the matcher. */
    fun startInjection(spec: String) {
        if (thread != null) stop()
        launch("injected $spec") { loopInjected(spec) }
    }

    fun stop() {
        if (recording.value?.startsWith("session-") == true) stopRecording()
        stop = true
        val was = thread
        was?.join(1000)
        synchronized(threadLock) { if (thread === was) thread = null }
        main.post { source.value = null; level.floatValue = -120f; if (was != null) onStopped?.invoke() }
    }

    private fun launch(label: String, body: () -> Unit) {
        stop = false
        ringOn = keepRecordings
        matcher.reset()
        matcher.trace = if (traceOn()) { msg -> Log.d(TAG, "matcher $msg") } else null
        source.value = label
        val t = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try { body() } catch (e: Exception) { Log.d(TAG, "mic: thread failed: $e") }
            finally { endedByItself() }
        }, "sharpears-mic")
        thread = t           // before start(): a loop that fails at once must find itself here to clear it
        t.start()
    }

    /**
     * The capture loop ended without [stop] (the mic would not start, a read error, the input taken away): the mic is
     * free again, so [listening] stops holding the voice's lines and the next [start] opens it afresh. Before this a
     * dead loop stayed in [thread] and every later start() returned true without listening.
     */
    private fun endedByItself() {
        val me = Thread.currentThread()
        synchronized(threadLock) { if (thread !== me || stop) return; thread = null }
        Log.d(TAG, "mic: the capture loop ended by itself")
        main.post { source.value = null; level.floatValue = -120f; onStopped?.invoke() }
    }
    private val threadLock = Any()

    // ---- capture loops ----

    private var framesRead = 0L
    private var lastLevelPost = 0L
    // Latest mapping between a captured frame and System.nanoTime(), for placing MIDI truth in recordings.
    @Volatile private var clockFrame = 0L
    @Volatile private var clockNanos = 0L

    private fun loopMic(rec: AudioRecord) {
        val buf = FloatArray(512)
        framesRead = 0
        try {
            rec.startRecording()          // inside the try: a refused start still releases the recorder below
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
            runCatching { rec.stop() }; rec.release()
            Log.d(TAG, "mic: stopped after ${framesRead / RATE} s")
        }
    }

    private fun loopInjected(spec: String) {
        val samples = if (spec.startsWith("synth:")) {
            // "60+64+67" is a chord: notes struck together. "r" is a rest. "@600~2000": 600 ms apart, first at 2000 ms.
            val (list, timing) = spec.removePrefix("synth:").split("@").let { it[0] to it.getOrNull(1).orEmpty() }
            val step = timing.substringBefore("~").toDoubleOrNull() ?: 600.0
            val first = timing.substringAfter("~", "").toDoubleOrNull() ?: 500.0
            val groups = list.split(",")
            val notes = groups.flatMapIndexed { i, g ->
                g.split("+").filter { it.trim() != "r" }.map { SynthNote(it.trim().toInt(), first + i * step, durationMs = step * 0.9) }
            }
            PianoSynth.render(notes, first + groups.size * step + 1000, RATE)
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
        thread = null
        main.post { source.value = null; onStopped?.invoke() }
    }

    /** Raw input, on the audio thread, for tools that read the mic besides note matching (the tuner). */
    @Volatile var tap: ((samples: FloatArray, count: Int) -> Unit)? = null

    private fun handle(buf: FloatArray, n: Int) {
        tap?.invoke(buf, n)
        if (!rawFrames.isEmpty) rawFrames.send(if (n == buf.size) buf else buf.copyOf(n))
        // The writer is read inside the lock: stopRecording() nulls and closes it under the same lock, and a stale
        // reference taken outside would append to a closed file (EBADF at the end of every recording).
        synchronized(this) {
            // A full disk ends the debug recording, never the listening (an exception here used to kill the mic thread).
            try { writer?.append(buf, n) } catch (e: java.io.IOException) {
                Log.d(TAG, "mic: recording stopped: $e"); runCatching { writer?.close() }; writer = null
            }
        }
        if (ringOn) for (i in 0 until n) { ring[(ringFrames % ring.size).toInt()] = buf[i]; ringFrames++ }
        val deaf = this.deaf
        if (deaf) lastDeafNanos = System.nanoTime()
        for (e in matcher.process(buf, n)) {
            if (deaf) continue
            when (e) {
                is MicEvent.Strike -> synchronized(octave) { octave.right(e.midi) }
                is MicEvent.Wrong -> synchronized(octave) { octave.wrong(sampleMs(e.sample), e.midi, e.riseDb) }
            }
            _events.tryEmit(e)
        }
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

    // ---- stuck moments ----

    /** The last 45 s of input (with recordings kept), so a stuck moment can be saved from just before it began. */
    private val ring by lazy { FloatArray(RATE * 45) }
    @Volatile private var ringOn = false
    /** Frames written to [ring] since the app started; keeps counting across mic restarts, unlike [framesRead]. */
    @Volatile private var ringFrames = 0L

    /** Where the input is now, for [saveStall] later. */
    fun clipMark(): Long = ringFrames

    /** Stuck moments saved for review (each a .wav clip and a .txt report). */
    fun stalls(): List<File> = stallDir().listFiles { f -> f.extension == "wav" }?.sortedBy { it.name }.orEmpty()
    /** Everything in the stalls folder (clips, reports, flags, crashes, summaries, sent markers), for sharing. */
    fun stallFiles(): List<File> = stallDir().listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
    private fun stallDir() = File(context.getExternalFilesDir(null), "stalls")

    /**
     * Saves a stuck moment: the input from 2 s before [fromMark] up to now (the last 45 s at most) as
     * stalls/<name>.wav, and [report] as <name>.txt. The oldest go first past [STALL_BUDGET] bytes.
     */
    fun saveStall(fromMark: Long, report: String, name: String) {
        if (!ringOn) return
        val end = ringFrames
        val from = maxOf(fromMark - 2L * RATE, end - ring.size + RATE / 10, 0L)   // 0.1 s clear of the writer
        val n = (end - from).toInt()
        if (n <= 0) return
        val clip = FloatArray(n) { ring[((from + it) % ring.size).toInt()] }
        val tagged = com.bigpapituna.sharpears.engine.StallWatch.tagProfile(report, profileKind)
        Thread {
            // A full or missing disk loses this clip, never the app (an exception on this thread was a crash mid-lesson).
            try {
                val dir = stallDir().apply { mkdirs() }
                val wav = File(dir, "$name.wav")
                Wav.Writer(wav, RATE).apply { append(clip, clip.size); close() }
                File(dir, "$name.txt").writeText(tagged + "clip_starts_s=%.2f before the marked moment\n".format((fromMark - from) / RATE.toDouble()))
                // ponytail: oldest first by name (time-stamped); about 80 clips of 30 s fit
                val all = dir.listFiles().orEmpty().sortedBy { it.name }.toMutableList()
                while (all.sumOf { it.length() } > STALL_BUDGET && all.size > 2) all.removeAt(0).delete()
                Log.d(TAG, "mic: stuck moment saved ${wav.name} (${wav.length() / 1024} KB)")
                reporter.flush()
            } catch (e: Exception) { Log.d(TAG, "mic: stuck moment $name not saved: $e") }
        }.start()
    }

    /** Where the input was [seconds] ago (for a clip of the recent past, like a "that wasn't right" flag). */
    fun clipMarkAgo(seconds: Int): Long = maxOf(0L, ringFrames - seconds.toLong() * RATE)
    /** Whether clips can be saved now (recordings kept and the mic has been running). */
    val canClip get() = ringOn && ringFrames > 0

    /** Deletes every saved stuck moment; returns how many. */
    fun deleteStalls(): Int = stalls().size.also { stallDir().listFiles()?.forEach { it.delete() } }

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
            // No room (or no storage): no recording, but the mic goes on listening.
            writer = try { Wav.Writer(f, RATE) } catch (e: java.io.IOException) { Log.d(TAG, "mic: cannot record ${f.name}: $e"); return null }
            recordFile = f
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
        runCatching { File(f.path.removeSuffix(".wav") + ".notes").writeText(truth.toString()) }
            .onFailure { Log.d(TAG, "mic: ${f.name} notes not saved: $it") }
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
