package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class MidiParserTest {
    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun noteOnAndOff() {
        val p = MidiParser()
        assertEquals(listOf(NoteEvent.On(60, 100)), p.feed(bytes(0x90, 60, 100)))
        assertEquals(listOf(NoteEvent.Off(60)), p.feed(bytes(0x80, 60, 64)))
        assertEquals(listOf(NoteEvent.Off(60)), p.feed(bytes(0x93, 60, 0)))     // velocity 0, any channel
    }

    @Test fun runningStatus() {
        assertEquals(
            listOf(NoteEvent.On(60, 90), NoteEvent.On(64, 80), NoteEvent.Off(60)),
            MidiParser().feed(bytes(0x90, 60, 90, 64, 80, 60, 0)),
        )
    }

    @Test fun messageSplitAcrossPackets() {
        val p = MidiParser()
        assertEquals(emptyList<NoteEvent>(), p.feed(bytes(0x90, 62)))
        assertEquals(listOf(NoteEvent.On(62, 70)), p.feed(bytes(70)))
    }

    @Test fun otherMessagesAreSkipped() {
        val events = MidiParser().feed(bytes(
            0xB0, 64, 127,             // sustain pedal (control change)
            0xC0, 5,                   // program change: one data byte
            0xE0, 0, 64,               // pitch bend
            0xF0, 0x7E, 0x7F, 9, 1, 0xF7, // sysex
            0xFE,                      // active sensing
            0x90, 67, 50,
        ))
        assertEquals(listOf(NoteEvent.On(67, 50)), events)
    }

    @Test fun realTimeBytesInsideAMessage() {
        assertEquals(listOf(NoteEvent.On(65, 40)), MidiParser().feed(bytes(0x90, 0xF8, 65, 0xF8, 40)))
    }

    @Test fun strayDataIgnored() {
        assertEquals(emptyList<NoteEvent>(), MidiParser().feed(bytes(60, 100)))
    }
}
