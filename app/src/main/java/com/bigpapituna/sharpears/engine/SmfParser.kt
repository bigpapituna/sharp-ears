package com.bigpapituna.sharpears.engine

class MidiFormatException(message: String) : Exception(message)

/** One note as the file has it: its track, channel (0..15; 9 is General MIDI's drums) and ticks. */
data class SmfNote(val track: Int, val channel: Int, val key: Int, val velocity: Int, val startTick: Long, val endTick: Long)

/**
 * A file as read, before any hand or timing rule ([SmfParser.read]): ticks per quarter, the tempo map (tick to microseconds
 * per quarter, sorted, starting at tick 0), the time signature, the key signature's flats (null when the file has none),
 * every note, each track's name (meta 0x03; absent when the track has none) and the markers.
 */
class SmfData(
    val ppq: Int,
    val tempos: List<Pair<Long, Int>>,
    val timeSignature: Pair<Int, Int>,
    val flats: Boolean?,
    val notes: List<SmfNote>,
    val trackNames: Map<Int, String>,
    val markers: List<Pair<Long, String>>,
) {
    private val clock = SmfParser.TempoClock(tempos, ppq)
    /** [tick] in milliseconds through the tempo map. */
    fun ms(tick: Long): Long = clock.ms(tick)
}

/**
 * Minimal Standard MIDI File reader: formats 0 and 1, PPQ timing, running status,
 * note on/off (velocity 0 = off), tempo map, time signature, key signature (flats or not), track names, markers.
 * Everything else is skipped.
 *
 * Any bytes a learner picks come through here (2.8.3, security review finding 4), so what it returns is bounded whatever
 * the file says: at most [MAX_BYTES] in, [MAX_TRACKS] tracks read, a time signature music has (else 4/4), tempos held
 * to [MIN_BPM]..[MAX_BPM], a song of at most [MAX_SONG_MS] and [MAX_NOTES] notes. Anything it cannot read is a
 * [MidiFormatException], never another exception.
 */
object SmfParser {
    /** The biggest file read: far above any song (the bundled ones are under 13 KB). My songs and the older import refuse more. */
    const val MAX_BYTES = 512 * 1024
    /** A song stops at 20 minutes: notes starting later go, and a note still sounding ends there. */
    const val MAX_SONG_MS = 20 * 60_000L
    /** At most this many notes, the earliest (Clair de Lune, the longest bundled song, has 1,468). */
    const val MAX_NOTES = 20_000
    /** At most this many tracks are read; a file that says it has more keeps its first ones. */
    const val MAX_TRACKS = 128
    /** At most this many markers (the player's sections). */
    const val MAX_MARKERS = 256
    /** Track names and markers keep at most this many characters. */
    const val MAX_TEXT = 128
    /** Tempos are held to this range, in quarter notes a minute. */
    const val MIN_BPM = 20
    const val MAX_BPM = 300
    private const val SLOWEST_US = 60_000_000 / MIN_BPM
    private const val FASTEST_US = 60_000_000 / MAX_BPM

    fun parse(bytes: ByteArray, id: String, title: String, rule: HandRule = HandRule.Auto): Song {
        val data = read(bytes)
        val noteTracks = data.notes.map { it.track }.distinct().sorted()
        val hands = { n: SmfNote ->
            when (rule) {
                is HandRule.Tracks -> if (n.track == rule.left) Hand.LEFT else Hand.RIGHT
                HandRule.MiddleCSplit -> split(n.key)
                HandRule.Auto ->
                    if (noteTracks.size >= 2) (if (n.track == noteTracks[1]) Hand.LEFT else Hand.RIGHT)
                    else split(n.key)
            }
        }
        val songNotes = data.notes.map {
            val start = data.ms(it.startTick)
            Note(it.key, start, (data.ms(it.endTick) - start).coerceAtLeast(1), hands(it), it.velocity)
        }.sortedWith(compareBy({ it.startMs }, { it.midi }))

        val durationMs = songNotes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
        // ponytail: sections come from marker events only; deriving sections from bars waits until a song needs it
        val marks = data.markers
        val sections = marks.mapIndexed { i, m ->
            Section(m.second, data.ms(m.first), marks.getOrNull(i + 1)?.let { data.ms(it.first) } ?: durationMs)
        }
        return Song(id, title, 60_000_000.0 / data.tempos.first().second, data.timeSignature,
            songNotes, groupOnsets(songNotes), sections, durationMs, preferFlats = data.flats == true)
    }

    /** The file's notes, tempo map, signatures, track names and markers, with nothing decided yet ([parse] decides the hands). */
    fun read(bytes: ByteArray): SmfData {
        if (bytes.size > MAX_BYTES) throw MidiFormatException("bigger than any song (${bytes.size} bytes)")
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
        if (division == 0) throw MidiFormatException("no ticks per quarter note")

        val notes = mutableListOf<SmfNote>()
        val tempos = mutableListOf(0L to 500_000)   // (tick, microseconds per quarter)
        val markers = mutableListOf<Pair<Long, String>>()
        val names = HashMap<Int, String>()
        var timeSig = 4 to 4
        var flats: Boolean? = null
        var track = 0
        val reading = minOf(trackCount, MAX_TRACKS)
        while (track < reading && r.remaining() >= 8) {
            val type = r.ascii(4)
            val len = r.u32()
            if (len < 0 || len > r.remaining()) throw MidiFormatException("track $track truncated")   // < 0: a length past 2 GB
            val end = r.pos + len
            if (type != "MTrk") { r.skip(len); continue }   // unknown chunk
            val t = track
            readTrack(r, end, track, notes, tempos, markers, { flats = it }, { timeSig = it }) { if (t !in names) names[t] = it }
            r.pos = end
            track++
        }
        if (track < reading) throw MidiFormatException("expected $trackCount tracks, found $track")

        // Later events at the same tick win, so a tempo at tick 0 replaces the 120 bpm default.
        val tempoMap = tempos.groupBy { it.first }.map { it.value.last() }.sortedBy { it.first }
        // The caps: the song ends at MAX_SONG_MS (a huge delta time made a song of years) and keeps its earliest
        // MAX_NOTES notes, in the order the file gave them.
        val endTick = TempoClock(tempoMap, division).tick(MAX_SONG_MS.toDouble())
        var kept: List<SmfNote> = notes.filter { it.startTick < endTick }
        if (kept.size > MAX_NOTES) kept = kept.withIndex().sortedWith(compareBy({ it.value.startTick }, { it.index }))
            .take(MAX_NOTES).sortedBy { it.index }.map { it.value }
        kept = kept.map { if (it.endTick > endTick) it.copy(endTick = endTick) else it }
        val marks = markers.filter { it.first < endTick }.sortedBy { it.first }.take(MAX_MARKERS)
        return SmfData(division, tempoMap, timeSig, flats, kept, names, marks)
    }

    private fun split(key: Int) = if (key >= MIDDLE_C) Hand.RIGHT else Hand.LEFT

    private fun text(data: ByteArray) = String(data, 0, minOf(data.size, MAX_TEXT), Charsets.ISO_8859_1)

    private fun readTrack(
        r: Reader, end: Int, track: Int, notes: MutableList<SmfNote>,
        tempos: MutableList<Pair<Long, Int>>, markers: MutableList<Pair<Long, String>>, onFlats: (Boolean) -> Unit, onTimeSig: (Pair<Int, Int>) -> Unit,
        onName: (String) -> Unit,
    ) {
        var tick = 0L
        var running = 0
        val open = HashMap<Int, ArrayDeque<Pair<Long, Int>>>()   // (channel shl 8 or key) -> FIFO of (startTick, velocity)
        fun close(channel: Int, key: Int) {
            val start = open[(channel shl 8) or key]?.removeFirstOrNull() ?: return
            notes += SmfNote(track, channel, key, start.second, start.first, tick)
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
                        // A zero tempo would make every later note time 0; any other is held to MIN_BPM..MAX_BPM.
                        0x51 -> if (len == 3) {
                            val us = data.fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
                            if (us > 0) tempos += tick to us.coerceIn(FASTEST_US, SLOWEST_US)
                        }
                        // A time signature music has: 1 to 32 beats of a whole note down to a 32nd (an exponent byte of
                        // 31 made a denominator of Int.MIN_VALUE). Anything else is left out, so 4/4 stays.
                        0x58 -> if (len >= 2) {
                            val beats = data[0].toInt() and 0xFF; val exponent = data[1].toInt() and 0xFF
                            if (beats in 1..32 && exponent in 0..5) onTimeSig(beats to (1 shl exponent))
                        }
                        // Key signature: sf is a signed count of sharps (positive) or flats (negative).
                        0x59 -> if (len >= 1) onFlats(data[0] < 0)
                        0x06 -> markers += tick to text(data)
                        0x03 -> onName(text(data))
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
                            if (key > 127 || vel > 127) throw MidiFormatException("bad note data in track $track")   // a key 255 broke the player
                            if (vel == 0) close(channel, key)
                            else open.getOrPut((channel shl 8) or key) { ArrayDeque() }.addLast(tick to vel)
                        }
                        0x80 -> { val key = r.u8(); r.u8(); if (key > 127) throw MidiFormatException("bad note data in track $track"); close(channel, key) }
                        0xC0, 0xD0 -> r.skip(1)
                        else -> r.skip(2)
                    }
                }
            }
        }
        // ponytail: notes never switched off are dropped; a file that relies on end-of-track to close notes loses them
    }

    /**
     * Converts ticks to milliseconds through the tempo map (sorted by tick, starting at tick 0) and back. In Double: a
     * tick count times microseconds per quarter passed Long's range after about 5e11 ticks and wrapped to negative times.
     * The tempo in force is found by binary search, so a file of thousands of tempo changes costs no more per note.
     */
    internal class TempoClock(private val tempos: List<Pair<Long, Int>>, private val ppq: Int) {
        private val startMs = DoubleArray(tempos.size).also {
            for (i in 1 until tempos.size) it[i] = it[i - 1] + span(tempos[i].first - tempos[i - 1].first, tempos[i - 1].second)
        }
        private fun span(ticks: Long, us: Int) = ticks.toDouble() * us / 1000.0 / ppq

        /** The last index whose [key] is at or before [x] (0 when [x] is before them all). */
        private inline fun last(x: Double, key: (Int) -> Double): Int {
            var lo = 0; var hi = tempos.size
            while (lo < hi) { val mid = (lo + hi) ushr 1; if (key(mid) <= x) lo = mid + 1 else hi = mid }
            return (lo - 1).coerceAtLeast(0)
        }

        fun ms(tick: Long): Long {
            val i = last(tick.toDouble()) { tempos[it].first.toDouble() }
            return Math.round(startMs[i] + span(tick - tempos[i].first, tempos[i].second))
        }

        /** The tick [ms] milliseconds in: where a song of that length ends. */
        fun tick(ms: Double): Long {
            val i = last(ms) { startMs[it] }
            return tempos[i].first + ((ms - startMs[i]) * 1000.0 * ppq / tempos[i].second).toLong()
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
