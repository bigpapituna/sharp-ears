package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.Pitch
import com.bigpapituna.sharpears.engine.theory.StaffNote
import java.util.Random

/**
 * Staff sight-reading (2.14, Sam: "two levels, falling notes with names hidden, no staff"). One ladder of eight levels:
 * 1 and 2 are the four-bar pieces of [sightPiece] (falling notes, names hidden, timed to the end), 3 to 8 a two-minute
 * drill on a real staff: a note at a time (treble C to G, the whole treble staff, the bass staff, both staves, sharps and
 * flats), then a bar at a time in rhythm with the clicks. Pure and seeded; ui/sight/StaffDrill.kt draws and runs it.
 */
enum class SightLevel(val n: Int, val title: String, val line: String) {
    PIECE_1(1, "Level 1", "C major, right hand, quarter and half notes."),
    PIECE_2(2, "Level 2", "Eighth notes too, and a bass note in the left hand."),
    TREBLE_C(3, "Treble C to G", "Middle C to G on the treble staff. Play each note you see."),
    TREBLE(4, "Treble staff", "Every line and space of the treble staff, from middle C to the A above it."),
    BASS(5, "Bass staff", "The bass clef, from the low E to middle C."),
    GRAND(6, "Both staves", "Treble and bass together, the way piano music is written."),
    ACCIDENTALS(7, "Sharps and flats", "Sharps and flats on both staves. A sharp is the next key up, a flat the next key down."),
    RHYTHM(8, "Rhythm", "A bar at a time with the clicks. Play each note on its beat."),
    ;

    /** Levels 3 to 8 are the staff drill; 1 and 2 the falling-note pieces. */
    val staff get() = n >= FIRST_STAFF
    /** The chip: "Level 3". */
    val chip get() = "Level $n"

    companion object {
        const val FIRST_STAFF = 3
        val MAX = entries.size
        fun of(n: Int): SightLevel = entries.first { it.n == n.coerceIn(1, MAX) }
    }
}

/** One card of the drill: a note (levels 3 to 7) or a bar of 4/4 (level 8), on the treble, the bass or both staves. */
data class StaffCard(val level: Int, val notes: List<StaffNote>, val grand: Boolean) {
    /** The keys to play, in order (rests left out). */
    val keys: List<Int> get() = notes.filter { !it.rest }.map { it.pitches.first().midi }
    /** The clef a single-staff card is drawn on. */
    val clef: Clef get() = notes.firstOrNull { !it.rest }?.staff ?: Clef.TREBLE
    /** "C4", "F#4 Bb3", "C4 D4 r E4": logged so a walk can script a keyboard against a seeded drill. */
    val code: String get() = notes.joinToString(" ") { if (it.rest) "r" else it.pitches.first().code }
    /** [code] with each note's beats, as the token notation writes them: "C4:1 D4:0.5 r:1". */
    val timedCode: String get() = notes.joinToString(" ") { (if (it.rest) "r" else it.pitches.first().code) + ":" + it.beats.toString().removeSuffix(".0") }
}

/** A drill's tally: notes right, and wrong keys (in rhythm, missed notes count as wrong too). */
data class DrillScore(val right: Int, val wrong: Int) {
    /** A better drill: more right, then fewer wrong. */
    fun beats(other: DrillScore?): Boolean = other == null || right > other.right || (right == other.right && wrong < other.wrong)
    fun encode() = "$right,$wrong"
    /** "24 right, 2 wrong", "24 right, none wrong". */
    val label get() = "$right right, " + if (wrong == 0) "none wrong" else "$wrong wrong"

    companion object {
        fun decode(s: String?): DrillScore? = s?.split(',')?.takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { r -> b.toIntOrNull()?.let { DrillScore(r, it) } } }
    }
}

object SightStaff {
    /** The drill's length. */
    const val DRILL_MS = 120_000L
    /** Level 8's tempo and its count-in (one bar of clicks). */
    const val RHYTHM_BPM = 72
    const val BEATS = 4
    val rhythmBeatMs get() = 60_000.0 / RHYTHM_BPM
    val rhythmBarMs get() = rhythmBeatMs * BEATS

    private fun whites(from: String, to: String): List<Pitch> {
        val lo = Pitch.parse(from).midi; val hi = Pitch.parse(to).midi
        return (lo..hi).filter { !isBlack(it) }.map { Pitch.of(it) }
    }

    /** Treble C to G, the treble staff with a ledger each side, the bass staff from low E up to middle C. */
    val TREBLE_C = whites("C4", "G4")
    val TREBLE = whites("C4", "A5")
    val BASS = whites("E2", "C4")
    /** Sharps and flats: the white keys of a middle range on each staff, a third of them sharpened or flattened. */
    val ACC_TREBLE = whites("C4", "G5")
    val ACC_BASS = whites("A2", "B3")

    /** The notes a level draws its single cards from, each with its staff. */
    fun pool(level: Int): List<Pair<Pitch, Clef>> = when (SightLevel.of(level)) {
        SightLevel.TREBLE_C -> TREBLE_C.map { it to Clef.TREBLE }
        SightLevel.TREBLE -> TREBLE.map { it to Clef.TREBLE }
        SightLevel.BASS -> BASS.map { it to Clef.BASS }
        SightLevel.GRAND, SightLevel.ACCIDENTALS -> (if (level == SightLevel.GRAND.n) TREBLE else ACC_TREBLE).map { it to Clef.TREBLE } +
            (if (level == SightLevel.GRAND.n) BASS else ACC_BASS).map { it to Clef.BASS }
        else -> TREBLE_C.map { it to Clef.TREBLE }
    }

    /**
     * The next card of a [level] drill from [rnd], never the key of [previous] again. Levels 3 to 7 are one note; level 7
     * sharpens or flattens two notes in five (never E sharp, B sharp, F flat or C flat); level 8 is a bar of [RHYTHM].
     */
    fun card(level: Int, rnd: Random, previous: StaffCard? = null): StaffCard {
        val lv = SightLevel.of(level)
        if (lv == SightLevel.RHYTHM) return rhythmCard(rnd, previous)
        val grand = lv == SightLevel.GRAND || lv == SightLevel.ACCIDENTALS
        val last = previous?.keys?.firstOrNull()
        var pick: Pair<Pitch, Clef>
        do {
            pick = pool(level)[rnd.nextInt(pool(level).size)]
            if (lv == SightLevel.ACCIDENTALS && rnd.nextInt(5) < 2) {
                val p = pick.first
                val up = rnd.nextBoolean()
                val acc = if (up && p.letterName !in setOf("E", "B")) 1 else if (!up && p.letterName !in setOf("F", "C")) -1 else 0
                pick = p.copy(accidental = acc) to pick.second
            }
        } while (pick.first.midi == last)
        return StaffCard(level, listOf(StaffNote(listOf(pick.first), NoteValue.WHOLE, staff = pick.second)), grand)
    }

    /** Level 8's bars: beats per note (negative: a rest), each adding up to a 4/4 bar. */
    val RHYTHM: List<List<Double>> = listOf(
        listOf(1.0, 1.0, 1.0, 1.0), listOf(2.0, 1.0, 1.0), listOf(1.0, 1.0, 2.0), listOf(2.0, 2.0), listOf(4.0), listOf(1.0, 2.0, 1.0),
        listOf(0.5, 0.5, 1.0, 1.0, 1.0), listOf(1.0, 0.5, 0.5, 1.0, 1.0), listOf(1.0, 1.0, 0.5, 0.5, 1.0), listOf(1.0, 1.0, 1.0, 0.5, 0.5),
        listOf(1.0, -1.0, 1.0, 1.0), listOf(2.0, -1.0, 1.0), listOf(1.0, 1.0, -1.0, 1.0),
    )

    private fun valueOf(beats: Double) = when (beats) { 4.0 -> NoteValue.WHOLE; 2.0 -> NoteValue.HALF; 0.5 -> NoteValue.EIGHTH; else -> NoteValue.QUARTER }

    /** A bar on the treble staff between middle C and G: stepwise, now and then a third, never the previous bar's pattern. */
    private fun rhythmCard(rnd: Random, previous: StaffCard?): StaffCard {
        var pattern: List<Double>
        do { pattern = RHYTHM[rnd.nextInt(RHYTHM.size)] } while (previous != null && pattern == previous.notes.map { (if (it.rest) -1.0 else 1.0) * it.beats })
        var d = rnd.nextInt(TREBLE_C.size)
        val notes = pattern.map { b ->
            if (b < 0) StaffNote(emptyList(), valueOf(-b), rest = true, staff = Clef.TREBLE)
            else {
                val step = listOf(-2, -1, -1, 0, 1, 1, 2)[rnd.nextInt(7)]
                d = (d + step).coerceIn(0, TREBLE_C.lastIndex)
                StaffNote(listOf(TREBLE_C[d]), valueOf(b), staff = Clef.TREBLE)
            }
        }
        return StaffCard(SightLevel.RHYTHM.n, notes, grand = false)
    }

    /** Where each note of a rhythm card starts in its bar, in beats (rests included). */
    fun beatsOf(card: StaffCard): List<Double> = card.notes.runningFold(0.0) { at, n -> at + n.beats }.dropLast(1)

    /**
     * Level 8 as one timeline: the bars of [cards] one after another from song time 0 (after the count-in), each note a group
     * at its beat, for [PerformScore] to grade as Perform does (good ±150 ms, ok ±300 ms, a miss after).
     */
    fun rhythmGroups(cards: List<StaffCard>): List<OnsetGroup> = rhythmStarts(cards).zip(cards).flatMap { (starts, c) ->
        starts.zip(c.notes.filter { !it.rest }).map { (at, n) ->
            OnsetGroup(at, listOf(Note(n.pitches.first().midi, at, (n.beats * rhythmBeatMs).toLong(), Hand.RIGHT, 80)))
        }
    }

    /** Each bar's note times on the drill's timeline (rests left out): the times [rhythmGroups] grades, bar by bar. */
    fun rhythmStarts(cards: List<StaffCard>): List<List<Long>> = cards.mapIndexed { bar, c ->
        beatsOf(c).zip(c.notes).filter { !it.second.rest }.map { (beat, _) -> (bar * rhythmBarMs + beat * rhythmBeatMs).toLong() }
    }

    /** Bars a drill can reach: two minutes of level 8, and one spare. */
    val RHYTHM_BARS get() = (DRILL_MS / rhythmBarMs).toInt() + 1

    /** The first [n] cards of a [level] drill seeded with [seed] (the drill draws them in this order). */
    fun cards(level: Int, seed: Long, n: Int): List<StaffCard> {
        val rnd = Random(seed)
        val out = ArrayList<StaffCard>(n)
        repeat(n) { out += card(level, rnd, out.lastOrNull()) }
        return out
    }
}

/**
 * Who opens the staff levels (2.14): a grown-up in Advanced or Full (Learn's Sight-read row shows from there) or one placed
 * at Some or A lot, and a kid who reads and has had lessons (a parent's "Has had lessons", the kids' Some). Everyone who
 * sees the row has levels 1 and 2. 2.14 merge: a big kid ([bigKid], 8 to 13, engine/family/Ages.kt) walks the grown-ups'
 * course and layouts, so the grown-ups' rule opens them: a layout past Simple, or a parent's "Lots" (the kids' Some).
 */
object SightLevels {
    fun staffOpen(kid: Boolean, reader: Boolean, placement: Level, layout: Layout, bigKid: Boolean = false): Boolean =
        if (kid && !bigKid) reader && placement.forKid == Level.SOME
        else layout != Layout.SIMPLE || placement.ordinal >= Level.SOME.ordinal

    fun open(kid: Boolean, reader: Boolean, placement: Level, layout: Layout, bigKid: Boolean = false): List<SightLevel> =
        SightLevel.entries.filter { !it.staff || staffOpen(kid, reader, placement, layout, bigKid) }
}
