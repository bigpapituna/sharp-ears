package com.bigpapituna.sharpears.engine

import java.util.Random

/** The three chord games on the Chords > Play tab. Each is a round of ten, answered on the piano. */
enum class GameKind(val id: String, val title: String, val howTo: String, val levels: List<String>) {
    BUILD("build", "Build it",
        "A chord name appears and its root lights up. Add the third and the fifth, in any order and any octave.",
        listOf("Triads in C", "Triads, all keys", "Sevenths")),
    HEAR("hear", "Hear it",
        "The app plays a chord. Find it and play it back in any octave. After two wrong notes the answer is shown.",
        listOf("Major or minor", "All triads", "Sevenths")),
    MAJOR_MINOR("mm", "Major or minor",
        "The app plays a chord. If it sounds major, play any major chord; if minor, any minor chord.",
        listOf("Major or minor", "7 or maj7")),
    INTERVALS("int", "Intervals",
        "The app plays two notes and lights the first. Find the second and play it. After two wrong notes it is shown.",
        listOf("Within a fifth", "Within an octave", "Up or down")),
    PLAYBACK("back", "Play it back",
        "The app plays a short tune under one hand position. Play it back in order. A wrong note starts it over; after two tries the notes are shown.",
        listOf("C position", "G position", "One black key")),
    ;

    /** The how-to line for one level (sevenths have a fourth note to find, and a different question). */
    fun howToFor(level: Int): String = when {
        this == BUILD && level == 2 -> "A chord name appears and its root lights up. Add the third, the fifth and the seventh, in any order and any octave."
        this == MAJOR_MINOR && level == 1 -> "The app plays a seventh chord. If it is a 7, play any 7 chord; if a major 7, any major 7 chord."
        else -> howTo
    }
}

/** "D minor", "Eb major", "C7", "C major 7". */
fun chordTitle(c: Chord): String = rootName(c.root) + when (c.type) {
    ChordType.DOM7 -> "7"; ChordType.MAJ7 -> " major 7"; ChordType.MIN7 -> " minor 7"
    else -> " " + c.type.label
}

/** The same, as the voice says it: "E flat major", "C seven". */
fun chordSpoken(c: Chord): String = spokenPitch(c.root) + when (c.type) {
    ChordType.DOM7 -> " seven"; ChordType.MAJ7 -> " major seven"; ChordType.MIN7 -> " minor seven"
    else -> " " + c.type.label
}

private fun degreeName(interval: Int) = when (interval) { 3, 4 -> "third"; 6, 7, 8 -> "fifth"; 10, 11, 9 -> "seventh"; else -> "root" }

/** One line for the screen and the same for the voice. */
data class GameLine(val text: String, val spoken: String = text)

/**
 * One game: [rounds] chords drawn with a seeded [Random]. Input is any key (MIDI number); the game works in pitch
 * classes, so any octave counts. A round is scored when it is clean (Build: no wrong note; Hear: answered before
 * the notes were shown; Major or minor: the right answer).
 */
class ChordGame(override val kind: GameKind, override val level: Int, seed: Long = System.nanoTime(), override val rounds: Int = 10) : EarGame {
    enum class Outcome { IGNORED, FOUND, WRONG, DONE }
    data class Answer(val outcome: Outcome, val line: GameLine?, val clean: Boolean = false)

    private val rnd = Random(seed)
    private val pool: List<Chord> = when (kind) {
        GameKind.BUILD -> when (level) {
            0 -> listOf(0 to ChordType.MAJOR, 2 to ChordType.MINOR, 4 to ChordType.MINOR, 5 to ChordType.MAJOR, 7 to ChordType.MAJOR, 9 to ChordType.MINOR)
                .map { (r, t) -> Chord(r, t) }
            1 -> (0..11).flatMap { r -> listOf(Chord(r, ChordType.MAJOR), Chord(r, ChordType.MINOR)) }
            else -> sevenths()
        }
        GameKind.HEAR -> when (level) {
            0 -> (0..11).flatMap { r -> listOf(Chord(r, ChordType.MAJOR), Chord(r, ChordType.MINOR)) }
            1 -> (0..11).flatMap { r -> listOf(ChordType.MAJOR, ChordType.MINOR, ChordType.DIM, ChordType.AUG).map { Chord(r, it) } }
            else -> sevenths()
        }
        GameKind.MAJOR_MINOR -> if (level == 0) (0..11).flatMap { r -> listOf(Chord(r, ChordType.MAJOR), Chord(r, ChordType.MINOR)) }
            else (0..11).flatMap { r -> listOf(Chord(r, ChordType.DOM7), Chord(r, ChordType.MAJ7)) }
        else -> error("not a chord game: $kind")
    }
    private fun sevenths() = (0..11).flatMap { r -> listOf(ChordType.DOM7, ChordType.MAJ7, ChordType.MIN7).map { Chord(r, it) } }

    /** The two answers in Major or minor. */
    val answerTypes = if (level == 0) listOf(ChordType.MAJOR, ChordType.MINOR) else listOf(ChordType.DOM7, ChordType.MAJ7)

    override var round = 0; private set
    override var score = 0; private set
    override var streak = 0; private set
    override var bestStreak = 0; private set
    var target: Chord = draw(null); private set
    /** Pitch classes found this round (Build starts with the root). */
    val found = mutableSetOf<Int>()
    var wrongs = 0; private set
    var roundOver = false; private set
    override val finished get() = round >= rounds
    /** Hear it: after two wrong notes the answer is lit on the keyboard. */
    val notesShown get() = kind == GameKind.HEAR && wrongs >= 2
    /** The chord where the app plays and shows it: root position from middle C. */
    val voicing get() = target.voicing(0, 60)
    // Major or minor: the keys struck recently, newest last, with their times.
    private val recent = ArrayDeque<Pair<Int, Long>>()

    init { startRound() }

    // Never the same keys twice in a row: augmented triads on C, E and G sharp are one chord by pitch class.
    private fun draw(prev: Chord?): Chord { var c: Chord; do { c = pool[rnd.nextInt(pool.size)] } while (c.pitchClasses == prev?.pitchClasses && pool.size > 1); return c }

    private fun startRound() {
        found.clear(); wrongs = 0; roundOver = false; recent.clear()
        if (kind == GameKind.BUILD) found += target.root % 12
    }

    override fun toPlay(): List<List<Int>> = if (kind == GameKind.BUILD) emptyList() else listOf(voicing)
    override fun lit(): Map<Int, Lit> = buildMap {
        val v = voicing
        if (notesShown) v.forEach { put(it, Lit.SHOWN) }
        if (kind == GameKind.BUILD) put(v.first(), Lit.GIVEN)
        v.filter { it % 12 in found && !(kind == GameKind.BUILD && it == v.first()) }.forEach { put(it, Lit.FOUND) }
    }
    override fun micGroups(): List<Set<Int>> = voicing.let { v -> listOf(v.toSet(), v.map { it - 12 }.toSet()) }
    override fun describe() = chordTitle(target)

    override fun prompt(): GameLine = when (kind) {
        GameKind.BUILD -> GameLine("Build ${chordTitle(target)}", "Build ${chordSpoken(target)}")
        GameKind.HEAR -> GameLine("Play back the chord you hear")
        GameKind.MAJOR_MINOR -> if (level == 0) GameLine("Major or minor?") else GameLine("7 or maj7?", "Seven, or major seven?")
        else -> error("not a chord game")
    }

    /** Keys the mic should listen for: the chord (or both answers on its root) where shown and an octave lower. */
    override fun micNotes(): Set<Int> {
        val base = when (kind) {
            GameKind.MAJOR_MINOR -> answerTypes.flatMap { Chord(target.root, it).voicing(0, 60) }.toSet()
            else -> voicing.toSet()
        }.filter { it % 12 !in found }
        return (base + base.map { it - 12 }).toSet()
    }

    override fun input(midi: Int, nowMs: Long): Answer {
        if (finished || roundOver) return Answer(Outcome.IGNORED, null)
        val pc = midi % 12
        if (kind == GameKind.MAJOR_MINOR) return answerByChord(pc, nowMs)
        if (pc !in target.pitchClasses) {
            wrongs++
            val missing = target.type.intervals.filter { (target.root + it) % 12 !in found }
            val hint = if (wrongs >= 2 && kind == GameKind.BUILD) missing.firstOrNull()?.let { i ->
                " The ${degreeName(i)} is ${target.toneName(target.root + i)}." to " The ${degreeName(i)} is ${target.spell(target.root + i).spoken}."
            } else null
            val shown = if (kind == GameKind.HEAR && wrongs == 2) " Here are the notes." else ""
            val name = if (kind == GameKind.BUILD) chordTitle(target) else "the chord"
            val spokenName = if (kind == GameKind.BUILD) chordSpoken(target) else "the chord"
            return Answer(Outcome.WRONG, GameLine("${rootName(pc)} is not in $name.${hint?.first ?: ""}$shown",
                "${spokenPitch(pc)} is not in $spokenName.${hint?.second ?: ""}$shown"))
        }
        if (pc in found) return Answer(Outcome.IGNORED, null)
        found += pc
        if (found != target.pitchClasses) {
            val i = (pc - target.root + 12) % 12
            return Answer(Outcome.FOUND, if (kind == GameKind.BUILD) GameLine("${target.toneName(pc)}, the ${degreeName(i)}.", "${target.spell(pc).spoken}, the ${degreeName(i)}.") else null)
        }
        val clean = if (kind == GameKind.BUILD) wrongs == 0 else !notesShown
        val notes = voicing.joinToString(", ") { target.toneName(it) }
        val line = when {
            kind == GameKind.BUILD && clean -> GameLine("${chordTitle(target)}: $notes. Clean.", "${chordSpoken(target)}. Clean.")
            kind == GameKind.BUILD -> GameLine("${chordTitle(target)}: $notes.", "That is ${chordSpoken(target)}.")
            else -> GameLine("Yes: ${chordTitle(target)}, $notes.", "Yes, ${chordSpoken(target)}.")
        }
        return endRound(clean, line)
    }

    /** Major or minor: the answer is whichever answer type the last few distinct keys form, on any root. */
    private fun answerByChord(pc: Int, nowMs: Long): Answer {
        recent.removeAll { it.first == pc || nowMs - it.second > 2500 }
        recent.addLast(pc to nowMs)
        val size = Chord(0, answerTypes[0]).pitchClasses.size
        if (recent.size < size) return Answer(Outcome.FOUND, null)
        while (recent.size > size) recent.removeFirst()
        val pcs = recent.map { it.first }.toSet()
        val played = answerTypes.firstNotNullOfOrNull { t -> (0..11).map { Chord(it, t) }.firstOrNull { it.pitchClasses == pcs } }
        if (played == null) {
            recent.clear()
            wrongs++
            val what = if (level == 0) "a major or minor chord" else "a 7 or maj7 chord"
            val said = if (level == 0) what else "a seven or major seven chord"
            return Answer(Outcome.WRONG, GameLine("Those notes are not $what. Try again.", "Those notes are not $said. Try again."))
        }
        val right = played.type == target.type
        val other = Chord(target.root, answerTypes.first { it != target.type })
        val why = when (target.type) {
            ChordType.MINOR -> "the middle note is a half step lower than in ${chordTitle(other)}" to "the middle note is a half step lower than in ${chordSpoken(other)}"
            ChordType.MAJOR -> "the middle note is a half step higher than in ${chordTitle(other)}" to "the middle note is a half step higher than in ${chordSpoken(other)}"
            ChordType.DOM7 -> "the top note is a half step lower than in ${chordTitle(other)}" to "the top note is a half step lower than in ${chordSpoken(other)}"
            else -> "the top note is a half step higher than in ${chordTitle(other)}" to "the top note is a half step higher than in ${chordSpoken(other)}"
        }
        val lead = if (right) "Yes. That was" else "That was"
        return endRound(right, GameLine("$lead ${chordTitle(target)}: ${why.first}.", "$lead ${chordSpoken(target)}: ${why.second}."))
    }

    private fun endRound(clean: Boolean, line: GameLine): Answer {
        roundOver = true
        if (clean) { score++; streak++; bestStreak = maxOf(bestStreak, streak) } else streak = 0
        return Answer(Outcome.DONE, line, clean)
    }

    /** Gives up on this round (no point) and moves on. */
    override fun skip() { if (!roundOver) { roundOver = true; streak = 0 }; next() }

    /** Moves to the next round after one ended. */
    override fun next() {
        if (!roundOver || finished) return
        round++
        if (!finished) { target = draw(target); startRound() }
    }
}
