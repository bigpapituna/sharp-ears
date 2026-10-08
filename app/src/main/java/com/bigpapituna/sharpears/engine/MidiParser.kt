package com.bigpapituna.sharpears.engine

/**
 * Turns a live MIDI byte stream into note events (and control changes through [onControl]). Handles running status, note-on with velocity 0 as
 * note-off, system-exclusive blocks, and real-time bytes (clock, active sensing) interleaved anywhere.
 * Stateful across calls, since a message can be split between two packets. One instance per input port.
 */
class MidiParser(
    /** Control changes (controller, value), e.g. the sustain pedal: 64, 0..127 (64 and up is down). */
    private val onControl: (controller: Int, value: Int) -> Unit = { _, _ -> },
) {
    private var status = 0            // running status, 0 = none
    private val data = IntArray(2)
    private var count = 0
    private var inSysex = false

    fun feed(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size): List<NoteEvent> {
        val out = ArrayList<NoteEvent>(2)
        for (i in offset until offset + length) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b >= 0xF8 -> {}                                        // real-time: ignore, keep state
                b == 0xF0 -> { inSysex = true; status = 0 }
                b == 0xF7 -> inSysex = false
                b >= 0xF0 -> { status = 0; inSysex = false }           // system common clears running status
                b >= 0x80 -> { status = b; count = 0; inSysex = false }
                inSysex || status == 0 -> {}                           // stray data byte
                else -> {
                    data[count++] = b
                    if (count == dataLength(status)) {
                        count = 0
                        message(status, out)
                    }
                }
            }
        }
        return out
    }

    private fun message(s: Int, out: MutableList<NoteEvent>) {
        when (s and 0xF0) {
            0x90 -> out += if (data[1] == 0) NoteEvent.Off(data[0]) else NoteEvent.On(data[0], data[1])
            0x80 -> out += NoteEvent.Off(data[0])
            0xB0 -> onControl(data[0], data[1])
        }
    }

    private fun dataLength(s: Int) = if (s and 0xF0 == 0xC0 || s and 0xF0 == 0xD0) 1 else 2
}
