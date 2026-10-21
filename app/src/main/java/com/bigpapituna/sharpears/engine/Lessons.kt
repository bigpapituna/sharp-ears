package com.bigpapituna.sharpears.engine

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

/**
 * The chord of one bar: duration-weighted pitch-class histogram matched against chord templates. Notes in the
 * chord score their weight, notes outside cost half theirs, and a seventh chord pays for its fourth note.
 * ponytail: per bar, no key context; smoothing across bars is the upgrade if songs mislabel
 */
fun labelBar(notes: List<Note>): Chord? {
    if (notes.isEmpty()) return null
    val w = DoubleArray(12)
    notes.forEach { w[it.midi % 12] += it.durationMs.toDouble() }
    // The lowest sounding note hints at the root: give it a little more weight.
    w[notes.minBy { it.midi }.midi % 12] += notes.sumOf { it.durationMs } * 0.1
    var best: Chord? = null
    var bestScore = Double.NEGATIVE_INFINITY
    // A four-note chord must earn its extra note: a stray passing tone or a sliver of the next bar is not a seventh.
    val extraNoteCost = w.sum() * 0.08
    for (type in LABEL_TYPES) for (root in 0..11) {
        val pcs = type.intervals.map { (root + it) % 12 }.toSet()
        val score = (0..11).sumOf { if (it in pcs) w[it] else -w[it] / 2 } - (type.intervals.size - 3) * extraNoteCost
        if (score > bestScore + 1e-9) { bestScore = score; best = Chord(root, type) }
    }
    return best
}

/** Length of one bar in song ms, from the song's (first) tempo and time signature. */
fun barMs(song: Song) = song.timeSignature.first * (4.0 / song.timeSignature.second) * 60_000 / song.bpm

/** Each bar's chord, in order (bars without notes are left out). */
fun labelBars(song: Song): List<Chord> = labelBarsAligned(song).filterNotNull()

/** Each bar's chord by bar index: null for a bar without notes. Bars come from [barMs] (first tempo only). */
fun labelBarsAligned(song: Song): List<Chord?> {
    val barMs = barMs(song)
    if (barMs <= 0 || song.notes.isEmpty()) return emptyList()
    val bars = (song.durationMs / barMs).toInt() + 1
    return (0 until bars).map { b ->
        val from = b * barMs; val to = from + barMs
        // Clip each note to the bar so a held note counts only for the time it sounds there.
        // Slivers (under 5% of the bar, e.g. rounding at the bar line) are dropped: one could pose as the bass note.
        labelBar(song.notes.filter { it.startMs < to && it.startMs + it.durationMs > from }.map { n ->
            val s = maxOf(n.startMs.toDouble(), from); val e = minOf((n.startMs + n.durationMs).toDouble(), to)
            n.copy(durationMs = (e - s).toLong())
        }.filter { it.durationMs >= barMs * 0.05 })
    }
}

/** "Chords of <song>": the distinct chords of its bars in order of first appearance (level 6). [bars]: labelBarsAligned output. */
fun songPack(song: Song, max: Int = 12, bars: List<Chord?> = labelBarsAligned(song)): LessonPack {
    val chords = bars.filterNotNull().distinct().take(max)
    return LessonPack("song-${song.id}", "Chords of ${song.title}", 6, emptyList(), chords.map { LessonItem.ChordItem(it) })
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
