package com.bigpapituna.sharpears.engine

class MidiFormatException(message: String) : Exception(message)

/**
 * Minimal Standard MIDI File reader: formats 0 and 1, PPQ timing, running status,
 * note on/off (velocity 0 = off), tempo map, time signature, track names, markers.
 * Everything else is skipped.
 */
object SmfParser {

    private class RawNote(val track: Int, val key: Int, val velocity: Int, val startTick: Long, val endTick: Long)
    private class Marker(val tick: Long, val name: String)

    fun parse(bytes: ByteArray, id: String, title: String, rule: HandRule = HandRule.Auto): Song {
        val r = Reader(bytes)
        if (r.ascii(4) != "MThd") throw MidiFormatException("not a MIDI file")
        val headerLen = r.u32()
        if (headerLen < 6) throw MidiFormatException("bad header")
        val format = r.u16()
        val trackCount = r.u16()
        val division = r.u16()
        r.skip(headerLen - 6)
        if (format > 1) throw MidiFormatException("format $format not supported")
        if (division and 0x8000 != 0) throw MidiFormatException("SMPTE timing not supported")

        val notes = mutableListOf<RawNote>()
        val tempos = mutableListOf(0L to 500_000)   // (tick, microseconds per quarter)
        val markers = mutableListOf<Marker>()
        var timeSig = 4 to 4
        var track = 0
        while (track < trackCount && r.remaining() >= 8) {
            val type = r.ascii(4)
            val len = r.u32()
            if (len > r.remaining()) throw MidiFormatException("track $track truncated")
            val end = r.pos + len
            if (type != "MTrk") { r.skip(len); continue }   // unknown chunk
            readTrack(r, end, track, notes, tempos, markers) { timeSig = it }
            r.pos = end
            track++
        }
        if (track < trackCount) throw MidiFormatException("expected $trackCount tracks, found $track")

        // Later events at the same tick win, so a tempo at tick 0 replaces the 120 bpm default.
        val tempoMap = tempos.groupBy { it.first }.map { it.value.last() }.sortedBy { it.first }
        val clock = TempoClock(tempoMap, division)
        val noteTracks = notes.map { it.track }.distinct().sorted()
        val hands = { n: RawNote ->
            when (rule) {
                is HandRule.Tracks -> if (n.track == rule.left) Hand.LEFT else Hand.RIGHT
                HandRule.MiddleCSplit -> split(n.key)
                HandRule.Auto ->
                    if (noteTracks.size >= 2) (if (n.track == noteTracks[1]) Hand.LEFT else Hand.RIGHT)
                    else split(n.key)
            }
        }
        val songNotes = notes.map {
            val start = clock.ms(it.startTick)
            Note(it.key, start, (clock.ms(it.endTick) - start).coerceAtLeast(1), hands(it), it.velocity)
        }.sortedWith(compareBy({ it.startMs }, { it.midi }))

        val durationMs = songNotes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        // ponytail: sections come from marker events only; deriving sections from bars waits until a song needs it
        val marks = markers.sortedBy { it.tick }
        val sections = marks.mapIndexed { i, m ->
            Section(m.name, clock.ms(m.tick), marks.getOrNull(i + 1)?.let { clock.ms(it.tick) } ?: durationMs)
        }
        return Song(id, title, 60_000_000.0 / tempoMap.first().second, timeSig,
            songNotes, groupOnsets(songNotes), sections, durationMs)
    }

    private fun split(key: Int) = if (key >= MIDDLE_C) Hand.RIGHT else Hand.LEFT

    private fun readTrack(
        r: Reader, end: Int, track: Int, notes: MutableList<RawNote>,
        tempos: MutableList<Pair<Long, Int>>, markers: MutableList<Marker>, onTimeSig: (Pair<Int, Int>) -> Unit,
    ) {
        var tick = 0L
        var running = 0
        val open = HashMap<Int, ArrayDeque<Pair<Long, Int>>>()   // (channel shl 8 or key) -> FIFO of (startTick, velocity)
        fun close(channel: Int, key: Int) {
            val start = open[(channel shl 8) or key]?.removeFirstOrNull() ?: return
            notes += RawNote(track, key, start.second, start.first, tick)
        }
        while (r.pos < end) {
            tick += r.vlq()
            var status = r.u8()
            if (status < 0x80) {   // running status: this byte is the first data byte
                if (running == 0) throw MidiFormatException("data byte without status in track $track")
                status = running; r.pos--
            }
            when {
                status == 0xFF -> {
                    val type = r.u8(); val len = r.vlq().toInt(); val data = r.bytes(len)
                    when (type) {
                        0x51 -> if (len == 3) {
                            val us = data.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
                            tempos += tick to us
                        }
                        0x58 -> if (len >= 2) onTimeSig(data[0].toInt() to (1 shl data[1].toInt()))
                        0x06 -> markers += Marker(tick, String(data, Charsets.ISO_8859_1))
                    }
                    running = 0
                }
                status == 0xF0 || status == 0xF7 -> { r.skip(r.vlq().toInt()); running = 0 }
                status >= 0xF0 -> throw MidiFormatException("unexpected status ${status.toString(16)} in track $track")
                else -> {
                    running = status
                    val channel = status and 0x0F
                    when (status and 0xF0) {
                        0x90 -> {
                            val key = r.u8(); val vel = r.u8()
                            if (vel == 0) close(channel, key)
                            else open.getOrPut((channel shl 8) or key) { ArrayDeque() }.addLast(tick to vel)
                        }
                        0x80 -> { val key = r.u8(); r.u8(); close(channel, key) }
                        0xC0, 0xD0 -> r.skip(1)
                        else -> r.skip(2)
                    }
                }
            }
        }
        // ponytail: notes never switched off are dropped; a file that relies on end-of-track to close notes loses them
    }

    /** Converts ticks to milliseconds through the tempo map. */
    private class TempoClock(private val tempos: List<Pair<Long, Int>>, private val ppq: Int) {
        private val startMs = DoubleArray(tempos.size).also {
            for (i in 1 until tempos.size)
                it[i] = it[i - 1] + (tempos[i].first - tempos[i - 1].first) * tempos[i - 1].second / 1000.0 / ppq
        }
        fun ms(tick: Long): Long {
            val i = tempos.indexOfLast { it.first <= tick }.coerceAtLeast(0)
            return Math.round(startMs[i] + (tick - tempos[i].first) * tempos[i].second / 1000.0 / ppq)
        }
    }

    private class Reader(private val b: ByteArray) {
        var pos = 0
        fun remaining() = b.size - pos
        fun u8(): Int { if (pos >= b.size) throw MidiFormatException("unexpected end of file"); return b[pos++].toInt() and 0xFF }
        fun u16() = (u8() shl 8) or u8()
        fun u32() = (u16() shl 16) or u16()
        fun vlq(): Long {
            var v = 0L
            repeat(4) { val c = u8(); v = (v shl 7) or (c and 0x7F).toLong(); if (c and 0x80 == 0) return v }
            throw MidiFormatException("variable-length value too long")
        }
        fun ascii(n: Int) = String(bytes(n), Charsets.US_ASCII)
        fun bytes(n: Int): ByteArray {
            if (n < 0 || n > remaining()) throw MidiFormatException("unexpected end of file")
            return b.copyOfRange(pos, pos + n).also { pos += n }
        }
        fun skip(n: Int) { bytes(n) }
    }
}
