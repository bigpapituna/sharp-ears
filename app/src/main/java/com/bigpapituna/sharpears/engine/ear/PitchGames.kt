package com.bigpapituna.sharpears.engine.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.noteName
import java.util.Random

/** The keys of the keyboard the pitch games use, for the mic to listen to. */
private val KEYBOARD = 48..84

/**
 * Perfect pitch: one note sounds, the player names it with the buttons or finds it on the keys (any octave counts).
 * Level 0: the seven white keys of one octave. Level 1: all twelve notes of one octave. Level 2: all twelve notes
 * over two octaves. A point only when the first answer is right; after two wrong keys the note is shown.
 */
class PerfectPitchGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : TrainerBase(EarKind.PITCH, level, rounds) {
    private val rnd = Random(seed)
    val pool: List<Int> = when (level) { 0 -> listOf(60, 62, 64, 65, 67, 69, 71); 1 -> (60..71).toList(); else -> (60..83).toList() }
    /** The names offered on the buttons. */
    val namePcs: List<Int> = if (level == 0) listOf(0, 2, 4, 5, 7, 9, 11) else (0..11).toList()
    var target = -1; private set

    override val answering get() = Answering.KEYS_AND_CHOICES

    init { newRound() }

    override fun newRound() {
        var t: Int
        do { t = pool[rnd.nextInt(pool.size)] } while (t % 12 == target % 12)
        target = t
    }

    override fun prompt() = GameLine("Which note is it?", "Which note is it?")
    override fun toPlay() = listOf(Step(listOf(target), 1500, 0, 90))
    override fun choices() = namePcs.map { Choice(it, pitchLabel(it), null, pitchSpoken(it)) }
    override fun answerId() = target % 12
    override fun lit(): Map<Int, Lit> = when {
        roundOver -> mapOf(target to Lit.FOUND)
        wrongs >= 2 -> mapOf(target to Lit.SHOWN)
        else -> emptyMap()
    }
    override fun micNotes(): Set<Int> = if (roundOver) emptySet() else KEYBOARD.filter { it % 12 == target % 12 }.toSet()
    override fun micGroups() = micNotes().map { setOf(it) }
    override fun describe() = noteName(target)

    private fun right(): ChordGame.Answer {
        val first = wrongs == 0
        val name = pitchLabel(target)
        return endRound(first, GameLine(if (first) "Yes: $name." else "Yes: $name. ${keyHint(target % 12)}", "Yes, ${pitchSpoken(target)}."))
    }

    override fun input(midi: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        if (midi % 12 == target % 12) return right()
        wrongs++
        val shown = if (wrongs == 2) " Here is the note." else ""
        return wrong(GameLine("That was ${pitchLabel(midi)}, not the note you heard.$shown", "That was ${pitchSpoken(midi)}, not the note you heard.$shown"))
    }

    override fun choose(id: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        if (id == target % 12) return right()
        wrongs++
        return endRound(false, GameLine("That was ${pitchLabel(target)}, not ${pitchLabel(id)}. ${keyHint(target % 12)}",
            "That was ${pitchSpoken(target)}, not ${pitchSpoken(id)}."))
    }
}

/**
 * Scale degrees: the app sets a key (its chord, then the scale up), plays one note, and the player names its step
 * in the scale, as solfege (do re mi) with the number under it, or finds it on the keys. The key changes every round
 * from level 1. Level 0: C major, do to sol. Level 1: major keys, all seven steps. Level 2: major and minor keys
 * (do-based minor: me, le, te). The tonic is lit on the keys in levels 0 and 1.
 */
class ScaleDegreeGame(level: Int, seed: Long = System.nanoTime(), rounds: Int = 10) : TrainerBase(EarKind.DEGREE, level, rounds) {
    private val rnd = Random(seed)
    private val keys: List<Pair<Int, Boolean>> = when (level) {
        0 -> listOf(0 to false)
        1 -> listOf(0, 7, 5, 2, 10, 9, 3).map { it to false }
        else -> listOf(0, 7, 5, 2, 10).map { it to false } + listOf(9, 4, 2, 0, 7).map { it to true }
    }
    /** The steps the game asks about (0 = do). */
    val degrees: List<Int> = if (level == 0) (0..4).toList() else (0..6).toList()
    var tonicPc = -1; private set
    var minor = false; private set
    var degree = 0; private set
    val scale get() = if (minor) MINOR_SCALE else MAJOR_SCALE
    val target get() = tonicMidi(tonicPc) + scale[degree]
    val keyLabel get() = keyName(tonicPc, minor)

    override val answering get() = Answering.KEYS_AND_CHOICES

    init { newRound() }

    override fun newRound() {
        val prevKey = tonicPc to minor
        val key = if (keys.size > 1) { var k: Pair<Int, Boolean>; do { k = keys[rnd.nextInt(keys.size)] } while (k == prevKey); k } else keys[0]
        tonicPc = key.first; minor = key.second
        degree = rnd.pickDifferent(degrees, degree.takeIf { round > 0 })
    }

    override fun prompt() = GameLine("Which step of the scale?", "Which step of the scale is this?")

    override fun intro(): List<Step> {
        val t = tonicMidi(tonicPc)
        return listOf(Step(tonicTriad(tonicPc, minor), 900, 200, 80)) +
            (scale + 12).map { Step(listOf(t + it), 260, 25, 80) }
    }

    override fun toPlay() = listOf(Step(listOf(target), 1500, 0, 90))

    override fun choices() = degrees.map { d -> solfege(d, minor).let { s -> Choice(d, s, "${d + 1}", solfegeSpoken(s)) } }
    override fun answerId() = degree

    override fun lit(): Map<Int, Lit> = buildMap {
        if (level < 2) put(tonicMidi(tonicPc), Lit.GIVEN)
        if (roundOver) put(target, Lit.FOUND) else if (wrongs >= 2) put(target, Lit.SHOWN)
    }
    override fun micNotes(): Set<Int> = if (roundOver) emptySet() else KEYBOARD.filter { it % 12 == target % 12 }.toSet()
    override fun micGroups() = micNotes().map { setOf(it) }
    override fun describe() = "${solfege(degree, minor)} (${degree + 1}) in $keyLabel: ${noteName(target)}"

    private fun feel(d: Int) = degreeFeel(d, minor)

    private fun right(): ChordGame.Answer {
        val s = solfege(degree, minor)
        val first = wrongs == 0
        return endRound(first, GameLine("Yes: $s, step ${degree + 1} of $keyLabel.", "Yes, ${solfegeSpoken(s)}, step ${degree + 1}."))
    }

    override fun input(midi: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        if (midi % 12 == target % 12) return right()
        wrongs++
        val d = scale.indexOf(((midi - tonicPc) % 12 + 12) % 12)
        val shown = if (wrongs == 2) " Here is the note." else ""
        val what = if (d < 0) "${spell(midi % 12, flatKey(tonicPc, minor))} is not in $keyLabel." else "That was ${solfege(d, minor)}, step ${d + 1}."
        val spoken = if (d < 0) "${spellSpoken(midi % 12, flatKey(tonicPc, minor))} is not in ${keySpoken(tonicPc, minor)}."
            else "That was ${solfegeSpoken(solfege(d, minor))}, step ${d + 1}."
        return wrong(GameLine("$what$shown", "$spoken$shown"))
    }

    override fun choose(id: Int): ChordGame.Answer {
        if (finished || roundOver) return ignored()
        if (id == degree) return right()
        wrongs++
        val s = solfege(degree, minor)
        val said = solfege(id, minor)
        return endRound(false, GameLine(
            "That was $s, step ${degree + 1} of $keyLabel (${spell(target % 12, flatKey(tonicPc, minor))}), not $said. ${feel(degree)}",
            "That was ${solfegeSpoken(s)}, step ${degree + 1}, not ${solfegeSpoken(said)}. ${feel(degree)}"))
    }
}

/** What scale step [d] feels like, said after a wrong answer in the scale degree game (and pre-recorded by SpokenCatalog). */
fun degreeFeel(d: Int, minor: Boolean): String = if (minor) when (d) {
    2 -> "me is the dark third of the minor home chord."
    5 -> "le is the low sixth, a mournful step."
    6 -> "te is the low seventh, a softer pull than ti."
    3 -> "fa leans down towards me."
    else -> degreeFeelCommon(d)
} else degreeFeelCommon(d)

private fun degreeFeelCommon(d: Int) = when (d) {
    0 -> "do is home: the note everything rests on."
    1 -> "re sits just above home and leans back down to do."
    2 -> "mi is bright and steady, the third of the home chord."
    3 -> "fa leans down towards mi."
    4 -> "sol is strong and open, the fifth of the home chord."
    5 -> "la is the gentle step above sol."
    else -> "ti is the leading tone: it pulls up to do."
}
