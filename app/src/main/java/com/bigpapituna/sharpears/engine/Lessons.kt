package com.bigpapituna.sharpears.engine

import kotlin.math.floor

/**
 * Chord lessons (DESIGN 4): packs of items, Leitner mastery per item, a daily due queue, unlocks by prerequisite,
 * and packs derived from songs by labeling each bar's chord.
 */

const val DAY_MS = 86_400_000L
private val BOX_DAYS = listOf(0, 1, 2, 4, 7, 14)

/** Leitner box 0..5 and when the item is due again. A never-practiced item has [seen] false. */
data class Mastery(val box: Int = 0, val dueAt: Long = 0, val seen: Boolean = false)

/**
 * Due again at the start of the day [BOX_DAYS] days on, in [zone]: a chord practised in the evening is due the next
 * morning (24 hours on it was not, nor at the evening reminder), and a night with a clock change does not skip a day.
 */
fun Mastery.after(correct: Boolean, now: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Mastery {
    val b = (if (correct) box + 1 else box - 1).coerceIn(0, 5)
    val day = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(BOX_DAYS[b].toLong())
    return Mastery(b, day.atStartOfDay(zone).toInstant().toEpochMilli(), seen = true)
}

/** A stored "box,dueAt" (data/LessonStore); null for anything else (a broken or hand-edited backup). Boxes stay 0..5. */
fun decodeMastery(s: String): Mastery? = s.split(",").takeIf { it.size == 2 }?.let { (b, d) ->
    val box = b.trim().toIntOrNull() ?: return null
    val due = d.trim().toLongOrNull() ?: return null
    Mastery(box.coerceIn(0, 5), due, seen = true)
}

/** One thing to practice. Chord items share ids across packs ("chord:Am7"), so mastery carries over. */
sealed interface LessonItem {
    val id: String
    data class ChordItem(val chord: Chord) : LessonItem { override val id get() = "chord:${chord.name}" }
    data class ProgressionItem(val progression: Int, val key: Int) : LessonItem {
        override val id get() = "prog:$progression:$key"
        val title get() = "${PROGRESSIONS[progression].name} in ${rootName(key)}"
    }
}

data class LessonPack(val id: String, val title: String, val level: Int, val requires: List<String>, val items: List<LessonItem>)

/** "C", "F#m", "Bbmaj7", "Ebm7b5"... Longest suffix wins, so "m7b5" is not read as "m". */
fun parseChord(name: String): Chord? {
    val rootLen = if (name.length > 1 && (name[1] == '#' || name[1] == 'b')) 2 else 1
    val root = listOf("C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B")
        .zip(listOf(0, 1, 1, 2, 3, 3, 4, 5, 6, 6, 7, 8, 8, 9, 10, 10, 11)).toMap()[name.take(rootLen)] ?: return null
    val type = ChordType.entries.firstOrNull { it.suffix == name.drop(rootLen) } ?: return null
    return Chord(root, type)
}

/** Counts the Chords home shows for a pack or the whole library. */
data class LessonCounts(val due: Int, val new: Int, val mastered: Int)

class Curriculum(val packs: List<LessonPack>) {
    /** A pack unlocks when every pack it requires averages box 3 or more. */
    fun unlocked(pack: LessonPack, mastery: Map<String, Mastery>) = pack.requires.all { req ->
        val p = packs.firstOrNull { it.id == req } ?: return@all true
        p.items.map { mastery[it.id]?.box ?: 0 }.average() >= 3.0
    }

    fun counts(pack: LessonPack, mastery: Map<String, Mastery>, now: Long): LessonCounts {
        val ms = pack.items.map { mastery[it.id] ?: Mastery() }
        return LessonCounts(ms.count { it.seen && it.dueAt <= now }, ms.count { !it.seen }, ms.count { it.box >= 4 })
    }

    /**
     * Today's session: items already learned and due (oldest first), then up to [newLimit] new items from
     * unlocked packs in level order. Each item once, even when several packs share it.
     */
    fun queue(mastery: Map<String, Mastery>, now: Long, newLimit: Int = 5, only: LessonPack? = null): List<LessonItem> {
        val source = (only?.let { listOf(it) } ?: packs.filter { unlocked(it, mastery) }).sortedBy { it.level }
        val items = source.flatMap { it.items }.distinctBy { it.id }
        val due = items.filter { mastery[it.id]?.let { m -> m.seen && m.dueAt <= now } == true }.sortedBy { mastery[it.id]!!.dueAt }
        val new = items.filter { mastery[it.id]?.seen != true }.take(newLimit)
        return due + new
    }
}

/** The chord shapes the labeler knows (DESIGN 4.3). */
private val LABEL_TYPES = listOf(ChordType.MAJOR, ChordType.MINOR, ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7, ChordType.DIM)

/** Every chord the labeler tries, in its order (type, then root), with its pitch classes. */
private val TEMPLATES = LABEL_TYPES.flatMap { type -> (0..11).map { root -> Chord(root, type) to BooleanArray(12).also { pcs -> type.intervals.forEach { pcs[(root + it) % 12] = true } } } }

/** Bars labelled at most: over 20 minutes of 2/4 at 200 bpm. A longer song's last bars keep the chord before them. */
const val MAX_LABEL_BARS = 4_000

/** A bar shorter than this (a damaged file's time signature) is no bar: such a song gets no labels. */
const val MIN_LABEL_BAR_MS = 10.0

/**
 * The chord of one bar: duration-weighted pitch-class histogram matched against chord templates. Notes in the
 * chord score their weight, notes outside cost half theirs, and a seventh chord pays for its fourth note.
 * ponytail: per bar, no key context; smoothing across bars is the upgrade if songs mislabel
 */
fun labelBar(notes: List<Note>): Chord? {
    if (notes.isEmpty()) return null
    val w = DoubleArray(12)
    notes.forEach { w[it.midi % 12] += it.durationMs.toDouble() }
    return labelWeights(w, notes.minBy { it.midi }.midi, notes.sumOf { it.durationMs })
}

/** [labelBar] from a bar's sounding time per pitch class [w] (changed here), its lowest key and its notes' total time. */
private fun labelWeights(w: DoubleArray, lowest: Int, total: Long): Chord? {
    // The lowest sounding note hints at the root: give it a little more weight.
    w[lowest % 12] += total * 0.1
    var best: Chord? = null
    var bestScore = Double.NEGATIVE_INFINITY
    // A four-note chord must earn its extra note: a stray passing tone or a sliver of the next bar is not a seventh.
    val extraNoteCost = w.sum() * 0.08
    for ((chord, pcs) in TEMPLATES) {
        val score = (0..11).sumOf { if (pcs[it]) w[it] else -w[it] / 2 } - (chord.type.intervals.size - 3) * extraNoteCost
        if (score > bestScore + 1e-9) { bestScore = score; best = chord }
    }
    return best
}

/** Length of one bar in song ms, from the song's (first) tempo and time signature. */
fun barMs(song: Song) = song.timeSignature.first * (4.0 / song.timeSignature.second) * 60_000 / song.bpm

/** Each bar's chord, in order (bars without notes are left out). */
fun labelBars(song: Song): List<Chord> = labelBarsAligned(song).filterNotNull()

/**
 * Each bar's chord by bar index: null for a bar without notes. Bars come from [barMs] (first tempo only), at most
 * [MAX_LABEL_BARS] of them.
 *
 * Each note is clipped to the bars it sounds in, so a held note counts only for the time it sounds there; slivers
 * (under 5% of the bar, e.g. rounding at the bar line) are dropped, since one could pose as the bass note. The work is
 * bounded by the notes and the bars, never their product (2.8.3): a note adds its first and last bar once and the bars
 * it holds through as one run, which a sweep over the bars carries. The result is the same as clipping every note to
 * every bar (LessonsTest checks the bundled songs against that).
 */
fun labelBarsAligned(song: Song): List<Chord?> {
    val barMs = barMs(song)
    if (song.notes.isEmpty() || !(barMs >= MIN_LABEL_BAR_MS) || barMs.isInfinite()) return emptyList()
    val bars = minOf(song.durationMs / barMs, MAX_LABEL_BARS - 1.0).toInt() + 1
    if (bars <= 0) return emptyList()
    fun from(b: Int) = b * barMs
    // The bars a note starts or ends in: its clipped time there, per bar and pitch class, the lowest key, the total.
    val edgeW = DoubleArray(bars * 12)
    val edgeLow = IntArray(bars) { Int.MAX_VALUE }
    val edgeTotal = LongArray(bars)
    val runs = ArrayList<IntArray>()                      // [first bar, last bar, key]: the bars a note sounds through
    for (n in song.notes) {
        val s = n.startMs.toDouble(); val e = (n.startMs + n.durationMs).toDouble()
        var b = (floor(s / barMs) - 1).coerceIn(0.0, bars.toDouble()).toInt()
        val last = (floor(e / barMs) + 1).coerceIn(-1.0, bars - 1.0).toInt()
        while (b <= last) {
            val f = from(b); val t = f + barMs
            if (s <= f && e >= t) {
                var z = last
                while (z > b && from(z) + barMs > e) z--
                runs += intArrayOf(b, z, n.midi)
                b = z + 1
                continue
            }
            if (s < t && e > f) {
                val d = (minOf(e, t) - maxOf(s, f)).toLong()
                if (d >= barMs * 0.05) { edgeW[b * 12 + n.midi % 12] += d.toDouble(); edgeLow[b] = minOf(edgeLow[b], n.midi); edgeTotal[b] += d }
            }
            b++
        }
    }
    val byStart = runs.sortedBy { it[0] }; val byEnd = runs.sortedBy { it[1] }
    val held = IntArray(12); val heldKeys = java.util.TreeMap<Int, Int>(); var heldCount = 0
    var i = 0; var j = 0
    val out = ArrayList<Chord?>(bars)
    for (b in 0 until bars) {
        while (i < byStart.size && byStart[i][0] <= b) { val k = byStart[i++][2]; held[k % 12]++; heldKeys.merge(k, 1) { a, c -> a + c }; heldCount++ }
        while (j < byEnd.size && byEnd[j][1] < b) {
            val k = byEnd[j++][2]; held[k % 12]--; heldCount--
            val left = heldKeys.getValue(k) - 1
            if (left == 0) heldKeys.remove(k) else heldKeys[k] = left
        }
        val f = from(b); val whole = (f + barMs - f).toLong()        // what clipping leaves of a note held through the bar
        val through = heldCount > 0 && whole >= barMs * 0.05
        val lowest = minOf(edgeLow[b], if (through) heldKeys.firstKey() else Int.MAX_VALUE)
        out += if (lowest == Int.MAX_VALUE) null else labelWeights(
            DoubleArray(12) { edgeW[b * 12 + it] + if (through) held[it].toDouble() * whole else 0.0 },
            lowest, edgeTotal[b] + if (through) heldCount * whole else 0L)
    }
    return out
}

/** A song pack's id is this plus the song's id (the layouts keep song packs whole in Advanced, engine/nav/Simple.kt). */
const val SONG_PACK_PREFIX = "song-"

/** "Chords of <song>": the distinct chords of its bars in order of first appearance (level 6). [bars]: labelBarsAligned output. */
fun songPack(song: Song, max: Int = 12, bars: List<Chord?> = labelBarsAligned(song)): LessonPack {
    val chords = bars.filterNotNull().distinct().take(max)
    return LessonPack(SONG_PACK_PREFIX + song.id, "Chords of ${song.title}", 6, emptyList(), chords.map { LessonItem.ChordItem(it) })
}

/**
 * A song's chords over time, for the label on the falling notes: the bar chords merged into changes, looked up
 * by song time with a binary search (no relabeling per frame). A bar without notes keeps the chord before it.
 */
class ChordTrack(bars: List<Chord?>, private val barMs: Double) {
    private val starts: DoubleArray
    private val chords: List<Chord>
    init {
        val s = mutableListOf<Double>(); val c = mutableListOf<Chord>()
        bars.forEachIndexed { i, ch -> if (ch != null && ch != c.lastOrNull()) { s += i * barMs; c += ch } }
        starts = s.toDoubleArray(); chords = c
    }
    val isEmpty get() = chords.isEmpty()

    /** The chord sounding at [ms] and the next different one (either may be null). */
    fun at(ms: Double): Pair<Chord?, Chord?> {
        var lo = 0; var hi = starts.size
        while (lo < hi) { val mid = (lo + hi) ushr 1; if (starts[mid] <= ms) lo = mid + 1 else hi = mid }
        return chords.getOrNull(lo - 1) to chords.getOrNull(lo)
    }
}
