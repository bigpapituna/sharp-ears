package com.bigpapituna.sharpears.engine

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * My songs (2.8.1): a learner's own MIDI file made into an easy song. The app ships nobody's melody; the learner brings the
 * file (Roy: pop songs, Michael Jackson first) and this turns it into the same [Song] a built-in song is, so the player, the
 * mic, stars and progress need nothing new. Easy mode, always:
 *
 * 1. The tune: of the file's note lines (a track and channel each; channel 10, the drums, never counts), the one that moves
 *    between pitches most and sits highest ([lines]). A track named for the tune ("Melody", "Vocal", "Lead") wins outright.
 *    A line that plays chords keeps the top note of each.
 * 2. Notes shorter than a 16th go (their length rounds to no 16th at all: grace notes, ornaments, ghost notes).
 * 3. Every start moves to the nearest 16th; one note a 16th, the tune's top (the left hand's bottom); a note starting
 *    more than a 16th under a held one is accompaniment and goes; each note ends where the next begins.
 * 4. Octaves: the whole line moves by octaves into the range the built-in songs use ([RIGHT_RANGE], the left hand
 *    [LEFT_RANGE]), and a note still outside folds in by octaves.
 * 5. The left hand: the lowest other line, clearly under the tune, kept only when the learner picks Both hands.
 * 6. Time: an intro with no tune is cut to the bar the tune starts in; one steady tempo, the file's main one, slowed to
 *    [MAX_BPM] when faster (the built-in first songs' tempo, and the player still opens it in Wait mode). The song stops
 *    at [SmfParser.MAX_SONG_MS] at that tempo (2.8.3).
 *
 * Pure (MySongsTest builds its own two-track file; no real song's notes are in the repo).
 */
object MySongs {
    /** Far above any song a learner brings; the bytes also travel in the progress export (data/SongRepository.kt). */
    const val MAX_BYTES = SmfParser.MAX_BYTES
    /** The built-in first songs' tempo (Twinkle, Ode to Joy, Mary had a little lamb at 100): a faster file is slowed to it. */
    const val MAX_BPM = 100.0
    /** G3 to C6: where the built-in songs' right hands play (their means 60 to 71). */
    val RIGHT_RANGE = 55..84
    const val RIGHT_CENTER = 65
    /** C2 to B3: where the built-in songs' left hands play (their means 43 to 50). */
    val LEFT_RANGE = 36..59
    const val LEFT_CENTER = 48
    /** General MIDI's drums (channel 10). */
    const val DRUMS = 9
    /** A line needs this many notes to count as a tune or a left hand. */
    const val MIN_NOTES = 4
    /** A left hand sits at least this far (semitones, on average) under the tune. */
    const val LEFT_BELOW = 5
    const val DEFAULT_TITLE = "My song"
    const val TITLE_MAX = 80

    /** Why a file is refused, each with the kind line the learner sees. */
    enum class Problem(val line: String) {
        NOT_MIDI("This file is not a MIDI song. Pick a file that ends in .mid."),
        NO_NOTES("This MIDI file has no notes to play. Try another one."),
        DRUMS_ONLY("This MIDI file only has drums. Pick one with a tune."),
        TOO_BIG("This file is too big to be a song. Pick a MIDI file under 512 KB."),
        UNREADABLE("The file could not be opened. Try picking it again."),
    }

    class Refused(val problem: Problem) : Exception(problem.line)

    /** What a file offers, read once at import: the title it suggests and how many notes each line kept. */
    data class Scan(val title: String, val melodyNotes: Int, val leftNotes: Int) {
        /** A left hand was found, so the learner may pick Both hands. */
        val hasLeft get() = leftNotes > 0
    }

    /** One easy note: its start and end in 16ths from the file's start, its key and velocity. */
    internal data class Step(val start: Long, val end: Long, val key: Int, val velocity: Int)

    /** The tune and the left hand (empty when the file has none), placed, in 16ths; [cut]: the 16th the song starts at. */
    internal class Lines(val melody: List<Step>, val left: List<Step>, val cut: Long = 0)

    /** Reads [bytes] for the title dialog; throws [Refused] with the kind line when it is no song. */
    fun scan(bytes: ByteArray, fileName: String?): Scan {
        val data = read(bytes)
        val lines = easyLines(data).first
        if (lines.melody.isEmpty()) throw Refused(Problem.NO_NOTES)
        // The first track's name is the song's name when that track is the conductor (no notes) or the only one.
        val sequence = data.trackNames[0]?.takeIf { data.notes.none { it.track == 0 } || data.notes.all { it.track == 0 } }
        return Scan(titleFrom(sequence, fileName), lines.melody.size, lines.left.size)
    }

    /** The easy song of [bytes]: the tune as the right hand, plus the left hand when [bothHands] and the file has one. */
    fun song(bytes: ByteArray, id: String, title: String, bothHands: Boolean): Song = song(read(bytes), id, title, bothHands)

    internal fun song(data: SmfData, id: String, title: String, bothHands: Boolean): Song {
        val (lines, bpm) = easyLines(data)
        if (lines.melody.isEmpty()) throw Refused(Problem.NO_NOTES)
        val left = if (bothHands) lines.left else emptyList()
        val msPerStep = 60_000.0 / bpm / 4
        fun ms(step: Long) = ((step - lines.cut) * msPerStep).roundToLong()
        val notes = (lines.melody.map { it to Hand.RIGHT } + left.map { it to Hand.LEFT }).map { (n, hand) ->
            Note(n.key, ms(n.start), (ms(n.end) - ms(n.start)).coerceAtLeast(1), hand, n.velocity.coerceIn(50, 110))
        }.sortedWith(compareBy({ it.startMs }, { it.midi }))
        return Song(id, title, bpm, data.timeSignature, notes, groupOnsets(notes), emptyList(),
            notes.maxOf { it.startMs + it.durationMs }, preferFlats = data.flats ?: flatKey(notes))
    }

    private fun read(bytes: ByteArray): SmfData {
        if (bytes.size > MAX_BYTES) throw Refused(Problem.TOO_BIG)
        val data = try { SmfParser.read(bytes) } catch (e: Exception) { throw Refused(Problem.NOT_MIDI) }
        if (data.notes.isEmpty()) throw Refused(Problem.NO_NOTES)
        if (data.notes.all { it.channel == DRUMS }) throw Refused(Problem.DRUMS_ONLY)
        return data
    }

    // ---- the lines ----

    /**
     * The lines and the one tempo they play at, cut at [SmfParser.MAX_SONG_MS]: the parser's cap is in the file's own
     * tempos, and slowing a fast file to [MAX_BPM] makes it longer.
     */
    private fun easyLines(data: SmfData): Pair<Lines, Double> {
        val lines = lines(data)
        if (lines.melody.isEmpty()) return lines to MAX_BPM
        val bpm = minOf(mainBpm(data, data.notes.minOf { it.startTick }, data.notes.maxOf { it.endTick }), MAX_BPM)
        val end = lines.cut + (SmfParser.MAX_SONG_MS / (60_000.0 / bpm / 4)).toLong()      // the 16th the song stops at
        fun cap(line: List<Step>) = line.filter { it.start < end }.map { if (it.end > end) it.copy(end = end) else it }
        return Lines(cap(lines.melody), cap(lines.left), lines.cut) to bpm
    }

    /** A track's notes on one channel: what the file plays as one line, with its outline (top note of each onset). */
    private class Candidate(val track: Int, val channel: Int, val notes: List<SmfNote>, val outline: List<SmfNote>) {
        val moves = outline.zipWithNext().count { (a, b) -> a.key != b.key }
        val mean = outline.map { it.key }.average()
    }

    private val TUNE_NAME = Regex("(?i)\\b(melody|melodie|melodia|vocals?|vox|voice|singer|lead)\\b")
    private val NOT_TUNE_NAME = Regex("(?i)ooh|aah|choir|backing|harmony|bgv|back|pad|bass|drum")

    internal fun lines(data: SmfData): Lines {
        val onset = (data.ppq / 8).coerceAtLeast(1)                  // notes starting within a 32nd are one onset
        val candidates = data.notes.filter { it.channel != DRUMS }.groupBy { it.track to it.channel }.map { (k, ns) ->
            Candidate(k.first, k.second, ns, outline(ns, onset))
        }.filter { it.outline.size >= MIN_NOTES }
        if (candidates.isEmpty()) return Lines(emptyList(), emptyList())
        val named = candidates.filter { c -> data.trackNames[c.track].orEmpty().let { TUNE_NAME.containsMatchIn(it) && !NOT_TUNE_NAME.containsMatchIn(it) } }
        val pool = named.ifEmpty { candidates }
        val maxMoves = pool.maxOf { it.moves }.coerceAtLeast(1)
        val low = pool.minOf { it.mean }
        val spread = maxOf(12.0, pool.maxOf { it.mean } - low)
        val tune = pool.maxWith(compareBy<Candidate>({ it.moves.toDouble() / maxMoves + (it.mean - low) / spread }, { it.notes.size }, { -it.track }))
        val bass = candidates.filter { it !== tune && it.mean <= tune.mean - LEFT_BELOW }
            .minWithOrNull(compareBy<Candidate>({ it.mean }, { -it.notes.size }))
        val melody = place(easyLine(tune.notes, data.ppq, top = true), RIGHT_RANGE, RIGHT_CENTER)
        if (melody.isEmpty()) return Lines(emptyList(), emptyList())
        // Rule 6: the song starts at the bar the tune starts in; a left hand before it goes with the intro.
        val (num, den) = data.timeSignature
        val barSteps = (num * 16L / den.coerceAtLeast(1)).coerceAtLeast(1)
        val cut = melody.first().start / barSteps * barSteps
        val left = bass?.let { b -> place(easyLine(b.notes, data.ppq, top = false).filter { it.start >= cut }, LEFT_RANGE, LEFT_CENTER) }
        return Lines(melody, left?.takeIf { it.size >= MIN_NOTES }.orEmpty(), cut)
    }

    /** The highest note of each onset (notes starting within [window] ticks of the first of them). */
    private fun outline(notes: List<SmfNote>, window: Int): List<SmfNote> {
        val out = mutableListOf<SmfNote>()
        var start = 0L
        for (n in notes.sortedWith(compareBy({ it.startTick }, { -it.key }))) {
            if (out.isEmpty() || n.startTick - start > window) { out += n; start = n.startTick }
            else if (n.key > out.last().key) out[out.size - 1] = n
        }
        return out
    }

    /** Rules 2 and 3 of the class comment: one note at a time on the 16th grid, the top ([top]) or the bottom of each. */
    private fun easyLine(notes: List<SmfNote>, ppq: Int, top: Boolean): List<Step> {
        val sixteenth = ppq / 4.0
        fun step(tick: Long) = Math.round(tick / sixteenth)
        val slots = notes.filter { (it.endTick - it.startTick) * 2 >= sixteenth }.groupBy { step(it.startTick) }.toSortedMap()
        val one = slots.map { (s, ns) ->
            val n = if (top) ns.maxBy { it.key } else ns.minBy { it.key }
            Step(s, maxOf(s + 1, step(n.endTick)), n.key, n.velocity)
        }
        val line = mutableListOf<Step>()
        for (n in one) {
            val held = line.lastOrNull()
            val under = held != null && n.start < held.end - 1 && (if (top) n.key < held.key else n.key > held.key)
            if (!under) line += n
        }
        return line.mapIndexed { i, n -> line.getOrNull(i + 1)?.takeIf { n.end > it.start }?.let { n.copy(end = it.start) } ?: n }
    }

    /** Rule 4: the octave shift that leaves the fewest notes outside [range] (then sits nearest [center]), then each note folded in. */
    internal fun place(line: List<Step>, range: IntRange, center: Int): List<Step> {
        if (line.isEmpty()) return line
        val keys = line.map { it.key }
        val mean = keys.average()
        val shift = (-6..6).map { it * 12 }.minWith(compareBy({ k -> keys.count { it + k !in range } }, { k -> abs(mean + k - center) }))
        return line.map { it.copy(key = fold(it.key + shift, range)) }
    }

    private fun fold(key: Int, range: IntRange): Int {
        var k = key
        while (k < range.first) k += 12
        while (k > range.last) k -= 12
        return k
    }

    /** The tempo that holds for most of the notes' span, in quarter notes a minute. */
    private fun mainBpm(data: SmfData, from: Long, to: Long): Double {
        val t = data.tempos
        val us = t.indices.maxBy { i ->
            val end = t.getOrNull(i + 1)?.first ?: Long.MAX_VALUE
            (minOf(end, to) - maxOf(t[i].first, from)).coerceAtLeast(0)
        }.let { t[it].second }
        return 60_000_000.0 / us
    }

    // ---- title and id ----

    /** Names that say what a track is, not what the song is (a part, an instrument, a placeholder). */
    private val GENERIC = Regex("(?i)(untitled|track|seq|sequence|song|new song|my song|midi|tempo|conductor|control|control track|" +
        "standard midi file|general midi|piano|grand piano|acoustic grand piano|melody|title|unknown|instrument|right|left|" +
        "right hand|left hand|rh|lh|treble|bass|upper|lower|up|down|one|two)( ?\\d+)?:?")
    private val NOT_A_TITLE = Regex("(?i)copyright|\\(c\\)|\u00a9|https?:|www\\.|@|\\.com\\b")

    /**
     * The title a file suggests: its sequence name when that reads as a title, else the file's name, else [DEFAULT_TITLE].
     * Underscores become spaces, a dash between words a comma (the copy rules), a name written as one slug ("billie-jean")
     * its words, and an all-lowercase name gets capitals ("Billie Jean"); at most 60 letters. The learner edits it in the
     * title dialog.
     */
    fun titleFrom(sequenceName: String?, fileName: String?): String {
        fun clean(raw: String): String {
            var s = raw.replace(Regex("(?i)\\.(mid|midi|kar|rmi|smf)$"), "").replace('_', ' ').trim()
            if (' ' !in s) s = s.replace('-', ' ')
            s = s.replace(Regex("\\s*[\u2014\u2013]\\s*|\\s+-\\s+"), ", ").replace(Regex("\\s+"), " ").trim(' ', '-', '.', ',')
            if (s.none { it.isUpperCase() }) s = s.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
            return s.take(60).trim(' ', '-', ',')
        }
        fun reads(s: String) = s.count { it.isLetter() } >= 2 && !GENERIC.matches(s) && !NOT_A_TITLE.containsMatchIn(s) && s.none { it.isISOControl() }
        return listOfNotNull(sequenceName, fileName).map(::clean).firstOrNull(::reads) ?: DEFAULT_TITLE
    }

    /**
     * The id of a song added from [bytes] whose file suggested [title]: the title's letters and digits and a short hash of the
     * bytes ("billie-jean-3fa2c1"). The same file always gets the same id, so adding it again replaces it and keeps its stars.
     */
    fun idFor(title: String, bytes: ByteArray): String {
        val slug = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).trim('-').ifEmpty { "song" }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).take(3).joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        return "$slug-$hash"
    }

    // ---- the words (the copy rules: sentence case, no dashes or symbol glyphs, lines end with a stop) ----

    object Words {
        const val SECTION = "My songs"
        const val ADD = "Add a song from a file"
        const val EMPTY = "Bring a MIDI file of a song you love. The app keeps its tune and makes it easy to play."
        const val NOTE = "Files stay on this phone."
        const val DIALOG_TITLE = "Add this song"
        const val TITLE_LABEL = "Title"
        const val EASY_LINE = "The app keeps the tune, on the beat and in easy reach."
        const val MELODY_ONLY = "Melody only"
        const val BOTH_HANDS = "Both hands"
        const val BOTH_LINE = "Both hands adds the bass line for your left hand."
        const val ADD_BUTTON = "Add"
        const val CANCEL = "Cancel"
        const val REFUSED_TITLE = "That file did not work"
        const val SAVE_FAILED = "The song could not be saved. Try again."
        const val ROW_LINE = "Your song, made easy."
        const val DELETE_LINE = "It leaves My songs and this phone. Add the file again any time."
        fun notes(n: Int) = if (n == 1) "The tune has 1 note." else "The tune has $n notes."
    }
}
