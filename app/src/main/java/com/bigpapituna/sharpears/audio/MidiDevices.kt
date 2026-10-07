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

    /** Every parsed note with the keyboard's System.nanoTime() timestamp, on the binder thread (fixture recording). */
    @Volatile var onTruth: ((NoteEvent, Long) -> Unit)? = null

    /** Name of the connected keyboard, or null. Compose reads this. */
    val connected = mutableStateOf<String?>(null)

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
            val parser = MidiParser()
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
