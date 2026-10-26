package com.bigpapituna.sharpears.audio

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.MidiOffset
import com.bigpapituna.sharpears.engine.MidiParser
import com.bigpapituna.sharpears.engine.NoteEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Connects to the first MIDI keyboard that appears (USB or Bluetooth already paired at system level)
 * and publishes its notes. Reconnects on hot-plug.
 */
// ponytail: auto-connects to the first device with an output port; a device picker waits until someone owns two keyboards
class MidiDevices(context: Context) {
    private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val main = Handler(Looper.getMainLooper())
    private val _events = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<NoteEvent> = _events
    private val _pedal = MutableSharedFlow<Boolean>(extraBufferCapacity = 64)
    /** The sustain pedal (CC 64): true when it goes down, false when it comes up. */
    val pedal: SharedFlow<Boolean> = _pedal

    /** Every parsed note with the keyboard's System.nanoTime() timestamp, on the binder thread (fixture recording). */
    @Volatile var onTruth: ((NoteEvent, Long) -> Unit)? = null

    /** Name of the connected keyboard, or null. Compose reads this. */
    val connected = mutableStateOf<String?>(null)

    /** The phone's input setup ("mic", never per profile, carried by a backup): where the MIDI timing offset lives. */
    private val setup = context.getSharedPreferences("mic", Context.MODE_PRIVATE)
    /** Bumps when [offsetMs] changes, so Settings shows the new value. */
    val offsetVersion = mutableStateOf(0)

    /**
     * 2.14: the MIDI timing offset (engine/MidiOffset.kt), −100..+100 ms, 0 until set: how late this phone hears its keyboard.
     * Perform's scoring alone moves each MIDI note back by it.
     */
    var offsetMs: Int
        get() = MidiOffset.clamp(setup.getInt(OFFSET_KEY, MidiOffset.DEFAULT))
        set(v) {
            val ms = MidiOffset.clamp(v)
            setup.edit().putInt(OFFSET_KEY, ms).apply()
            offsetVersion.value++
            Log.d(TAG, "midi: timing offset $ms ms")
        }

    companion object { const val OFFSET_KEY = "midiOffsetMs" }

    private var device: MidiDevice? = null
    private var port: MidiOutputPort? = null
    private var rawLogged = 0

    private val callback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(info: MidiDeviceInfo) { if (device == null) open(info) }
        override fun onDeviceRemoved(info: MidiDeviceInfo) {
            if (device?.info?.id == info.id) { Log.d(TAG, "midi: ${name(info)} removed"); close(); scan() }
        }
    }

    fun start() {
        val m = manager ?: run { Log.d(TAG, "midi: not supported on this device"); return }
        if (Build.VERSION.SDK_INT >= 33) {
            m.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, { main.post(it) }, callback)
        } else {
            @Suppress("DEPRECATION") m.registerDeviceCallback(callback, main)
        }
        scan()
    }

    fun stop() { manager?.unregisterDeviceCallback(callback); close() }

    /** Debug: pretend a keyboard is connected (for steps that need MIDI on the emulator). */
    fun debugConnect() { if (connected.value == null) connected.value = "Debug MIDI" }

    /**
     * Debug: plays a script of MIDI input from now: "800:p1 4790:p0 4900:p1 1000:60/40" (ms: pedal down/up, or
     * key/velocity, released 250 ms later), through the same flows as a real keyboard.
     */
    fun debugPlay(script: String, fromUptimeMs: Long? = null) {
        Log.d(TAG, "midi: debug script $script" + (fromUptimeMs?.let { " from ${it - android.os.SystemClock.uptimeMillis()} ms" } ?: ""))
        // 2.14: [fromUptimeMs] times the script from a moment already known (the player's first frame, on the same monotonic
        // clock as the frames), so a busy main thread does not shift the whole script against the song.
        val base = fromUptimeMs ?: android.os.SystemClock.uptimeMillis()
        script.split(' ', ',').filter { it.isNotBlank() }.forEach { item ->
            val (t, what) = item.split(':').let { it[0].toLong() to it[1] }
            if (what.startsWith("p")) main.postAtTime({ _pedal.tryEmit(what == "p1"); Log.d(TAG, "midi: pedal ${if (what == "p1") "down" else "up"} (debug)") }, base + t)
            else what.split('/').let { (k, v) ->
                main.postAtTime({ _events.tryEmit(NoteEvent.On(k.toInt(), v.toInt())) }, base + t)
                main.postAtTime({ _events.tryEmit(NoteEvent.Off(k.toInt())) }, base + t + 250)
            }
        }
    }

    private fun scan() {
        val m = manager ?: return
        val all = if (Build.VERSION.SDK_INT >= 33) m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
                  else @Suppress("DEPRECATION") m.devices.toList()
        Log.d(TAG, "midi: ${all.size} device(s): ${all.joinToString { "${name(it)} out=${it.outputPortCount}" }}")
        all.firstOrNull { it.outputPortCount > 0 }?.let(::open)
    }

    private fun open(info: MidiDeviceInfo) {
        if (info.outputPortCount == 0) return
        manager?.openDevice(info, { d ->
            if (d == null) { Log.d(TAG, "midi: could not open ${name(info)}"); return@openDevice }
            val p = d.openOutputPort(0) ?: run { d.close(); return@openDevice }
            var pedalDown = false
            val parser = MidiParser { controller, value ->
                if (controller == 64 && (value >= 64) != pedalDown) { pedalDown = value >= 64; _pedal.tryEmit(pedalDown); Log.d(TAG, "midi: pedal ${if (pedalDown) "down" else "up"}") }
            }
            p.connect(object : MidiReceiver() {
                // Runs on a binder thread; the flow buffers and the player collects on the main thread.
                override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
                    val keepAlive = count == 1 && (msg[offset].toInt() and 0xFF) >= 0xF8   // clock / active sensing
                    if (!keepAlive && rawLogged++ < 40) {                                // first packets, for diagnosis
                        Log.d(TAG, "midi raw: " + (offset until offset + count).joinToString(" ") { "%02X".format(msg[it]) })
                    }
                    parser.feed(msg, offset, count).forEach { _events.tryEmit(it); onTruth?.invoke(it, timestamp) }
                }
            })
            device = d; port = p
            connected.value = name(info)
            Log.d(TAG, "midi: connected ${name(info)}")
        }, main)
    }

    private fun close() {
        port?.close(); device?.close()
        port = null; device = null
        connected.value = null
    }

    private fun name(info: MidiDeviceInfo) =
        info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT) ?: "MIDI device"
}
