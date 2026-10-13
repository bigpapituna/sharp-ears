package com.bigpapituna.sharpears.engine.ear

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.rootName
import com.bigpapituna.sharpears.engine.voiceLead
import java.util.Random

/** One chord of a progression: [semitones] above the key's tonic, its quality, and how many beats it lasts. */
data class ProgStep(val numeral: String, val semitones: Int, val type: ChordType, val beats: Int = 1)

/**
 * A progression to recognise. [id] is its place in [PROGRESSION_CATALOG] (and the number of its choice button).
 * [label] is what the button says; [nickname] the small line under it; [blurb] is the "what it was" teaching line.
 */
data class ProgPattern(val id: Int, val nickname: String, val minor: Boolean, val steps: List<ProgStep>, val blurb: String) {
    val label get() = steps.joinToString(" ") { it.numeral }
}

private fun maj(n: String, s: Int, b: Int = 1) = ProgStep(n, s, ChordType.MAJOR, b)
private fun min(n: String, s: Int, b: Int = 1) = ProgStep(n, s, ChordType.MINOR, b)

/** Ten progressions: six in a major key, four in a minor key. Three-chord ones hold the last chord for two beats. */
val PROGRESSION_CATALOG: List<ProgPattern> = listOf(
    ProgPattern(0, "Rock and folk", false, listOf(maj("I", 0), maj("IV", 5), maj("V", 7), maj("I", 0)),
        "IV lifts away from home, V pulls hard back to I. Rock, folk and country lean on it."),
    ProgPattern(1, "Pop loop", false, listOf(maj("I", 0), maj("V", 7), min("vi", 9), maj("IV", 5)),
        "It never quite settles: the minor vi gives it a wistful turn before IV."),
    ProgPattern(2, "Jazz turnaround", false, listOf(min("ii", 2), maj("V", 7), maj("I", 0, 2)),
        "Three chords sliding down by fifths into home. It is the building block of jazz."),
    ProgPattern(3, "Fifties", false, listOf(maj("I", 0), min("vi", 9), maj("IV", 5), maj("V", 7)),
        "The doo-wop progression: up to the minor vi, round through IV, and V leaves it open."),
    ProgPattern(4, "Falling fifths", false, listOf(maj("I", 0), min("vi", 9), min("ii", 2), maj("V", 7)),
        "Each chord falls a fifth to the next, vi to ii to V, a smooth slide that ends open."),
    ProgPattern(5, "Away and back", false, listOf(maj("I", 0), maj("V", 7), maj("IV", 5), maj("I", 0)),
        "Home, up to V, then the softer IV, and back. It sounds like a hymn or a folk tune."),
    ProgPattern(6, "Minor cadence", true, listOf(min("i", 0), min("iv", 5), maj("V", 7), min("i", 0)),
        "The minor iv darkens the middle, and a major V gives the strong pull home."),
    ProgPattern(7, "Andalusian", true, listOf(min("i", 0), maj("VII", 10), maj("VI", 8), maj("V", 7)),
        "Four chords stepping down one by one, the sound of flamenco and many dramatic songs."),
    ProgPattern(8, "Epic loop", true, listOf(min("i", 0), maj("VI", 8), maj("III", 3), maj("VII", 10)),
        "A rising loop through the major chords of the minor key. Film music loves it."),
    ProgPattern(9, "Minor rock", true, listOf(min("i", 0), min("iv", 5), maj("VII", 10), maj("III", 3)),
        "Heavy and dark: i and iv stay minor, then VII and III lift away."),
)

/** The chords of [p] in the key whose tonic is pitch class [key]. */
fun chordsOf(p: ProgPattern, key: Int): List<Chord> = p.steps.map { Chord((key + it.semitones) % 12, it.type) }

/** Block chords, smoothly voiced (see [voiceLead]) with the root an octave below, one [Step] per chord. */
fun progressionSteps(p: ProgPattern, key: Int, beatMs: Int = 620): List<Step> {
    val chords = chordsOf(p, key)
    val voicings = voiceLead(chords)
    return chords.mapIndexed { i, c ->
        val bass = c.root % 12 + if (c.root % 12 <= 4) 48 else 36        // C3 to E3, then F2 to B2: below the right hand
        val hold = p.steps[i].beats * beatMs
        Step(listOf(bass) + voicings[i], hold + if (i == chords.lastIndex) 600 else 0, 50, 80)
    }
}

/**
 * Progressions. A progression plays in a key; the player picks which one it is from buttons labelled with the
 * Roman numerals (I = the home chord) and a nickname. Level 0: three common ones in C major. Level 1: six in major,
 * four buttons, in C, G, F or D. Level 2: major or minor progressions in any key (the wrong answers are from the
 * same mode, so the mode itself does not give it away).
 */
class ProgressionGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : TrainerBase(EarKind.PROGRESSION, level, rounds) {
    private val rnd = Random(seed)
    /** The progressions this level draws from. */
    val pool: List<ProgPattern> = when (level) {
        0 -> PROGRESSION_CATALOG.filter { it.id in listOf(0, 1, 2) }
        1 -> PROGRESSION_CATALOG.filter { !it.minor }
        else -> PROGRESSION_CATALOG
    }
    private val keys = when (level) { 0 -> listOf(0); 1 -> listOf(0, 7, 5, 2); else -> (0..11).toList() }
    private val optionCount = if (level == 0) 3 else 4
    var target: ProgPattern = pool[0]; private set
    var key = 0; private set
    private var options: List<ProgPattern> = emptyList()

    override val answering get() = Answering.CHOICES

    init { newRound() }

    override fun newRound() {
        val prev = if (round > 0) target else null
        target = rnd.pickDifferent(pool, prev)
        key = rnd.pickDifferent(keys, key.takeIf { round > 0 })
        // The target plus wrong answers of the same mode, in a shuffled order.
        val others = pool.filter { it.minor == target.minor && it.id != target.id }.shuffled(rnd).take(optionCount - 1)
        options = (others + target).shuffled(rnd)
    }

    override fun prompt() = GameLine("Which progression is this?", "Which progression is this?")
    override fun toPlay() = progressionSteps(target, key)
    override fun choices() = options.map { Choice(it.id, it.label, it.nickname, it.nickname) }
    override fun answerId() = target.id
    override fun lit(): Map<Int, Lit> = emptyMap()
    override fun micNotes() = emptySet<Int>()
    override fun micGroups() = emptyList<Set<Int>>()
    override fun describe() = "${target.label} (${target.nickname}) in ${keyName(key, target.minor)}"

    override fun choose(id: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        val chords = chordsOf(target, key).distinct().joinToString(", ") { it.name }
        val where = "In ${keyName(key, target.minor)} that is $chords."
        if (id == target.id) return endRound(true, GameLine("Yes: ${target.label} (${target.nickname}). ${target.blurb}",
            "Yes, ${target.nickname}. ${target.blurb}"))
        wrongs++
        val said = PROGRESSION_CATALOG.first { it.id == id }
        return endRound(false, GameLine("That was ${target.label} (${target.nickname}). ${target.blurb} $where You picked ${said.label}.",
            "That was ${target.nickname}. ${target.blurb} You picked ${said.nickname}."))
    }

    /** The key's chord names for a log line (tests). */
    fun chordNames(): List<String> = chordsOf(target, key).map { rootName(it.root) + it.type.suffix }
}
