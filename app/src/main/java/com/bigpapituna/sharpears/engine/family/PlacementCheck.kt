package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.playground.ANY_C
import com.bigpapituna.sharpears.engine.playground.rounds

/*
 * "Let's hear you play" (2.6, Roy's decision): a short listen, never a test. Someone who says they have played a little, some
 * or a lot plays up to three tiny things for the pup, who shows each first; the microphone listens with strict keys, every
 * first key gets the full glow and hop, and no score, pass or fail is ever shown. The pup ends by saying where they will start
 * and that the earlier levels stay open. Someone who has never played does not see it at all. It is ninety seconds at most
 * (two things for "a little", three for "some" and "a lot"), always skippable, and quitting halfway leaves what they said.
 * It counts as a first lesson (stars, the first sticker, sparkles). Behind the friendly face the rules are strict enough to
 * make the start gentler, never harder: the screen is ui/family/PlacementCheckScreen.kt, the rules are here.
 */
object PlacementCheck {
    /** The things to play, in order. */
    enum class Task(val title: String, val ask: String) {
        /** Any C counts. */
        FIND_C("Find a C", "Find a C. Any C will do."),
        /** C D E F G, the right hand's five fingers. */
        FIVE_NOTES("Five notes", "Play C, D, E, F, G with your right hand, one finger on each key."),
        /** The first phrase of Twinkle in time with the band. */
        TWINKLE("Twinkle with the band", "Play the start of Twinkle, Twinkle with your right hand. The band plays along."),
    }

    /** The things [level] is asked to play: nothing for a first time, two for a little, three for some and a lot. */
    fun tasksFor(level: Level): List<Task> = when (level) {
        Level.NEVER -> emptyList()
        Level.A_LITTLE -> listOf(Task.FIND_C, Task.FIVE_NOTES)
        Level.SOME, Level.A_LOT -> Task.entries
    }

    /** Someone who has never played goes straight to Basics: no check is offered. */
    fun offered(level: Level) = tasksFor(level).isNotEmpty()

    /** Tries a task gets before the pup lets it go (the Playground's three). */
    const val TRIES = com.bigpapituna.sharpears.engine.playground.PLAYGROUND_TRIES
    /** The band task counts as played at this accuracy (the concerts' pass mark). */
    const val BAND_PASS = com.bigpapituna.sharpears.engine.playground.CONCERT_PASS
    const val BAND_BPM = 70.0
    /** The whole check, at most, in seconds (the screens keep to it: three tasks of thirty). */
    const val MAX_SECONDS = 90

    /** Find a C: one round, any C from C2 to C6. */
    val FIND_C_EXERCISE: Exercise = Exercise(right = rounds(ANY_C), bpm = 60.0, practice = Practice.RIGHT)

    /** Five notes: C D E F G, right hand, the notes waiting. */
    val FIVE_NOTES_EXERCISE: Exercise = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1", bpm = 60.0, practice = Practice.RIGHT,
        fingers = "1 2 3 4 5")

    /** Twinkle's first phrase (C C G G A A G) with the band's bass under it, at [BAND_BPM], the right hand yours. */
    val TWINKLE_EXERCISE: Exercise = TWINKLE.copy(bpm = BAND_BPM, practice = Practice.RIGHT, pass = BAND_PASS).bite()

    /** How a task ended (kept inside; never shown). */
    enum class Result { PLAYED, LET_GO, SKIPPED }

    /** How many things were played, of those asked. */
    fun score(results: List<Result>): Int = results.count { it == Result.PLAYED }

    /**
     * The highest level playing [score] of [asked] things speaks for. All of them does not argue with any level (so the check
     * never lowers a true answer); two of three is a little; less is a first time.
     */
    fun supports(score: Int, asked: Int): Level = when {
        asked > 0 && score >= asked -> Level.A_LOT
        score >= 2 -> Level.A_LITTLE
        else -> Level.NEVER
    }

    /**
     * Where to begin, given what someone said ([selfReport]) and the check's [score] out of [asked] (null: skipped or quit, so
     * it is the self-report). When they disagree the gentler wins, so nobody starts above where they play; when they agree it
     * is the self-report. The check never raises a level. [kind] chooses the words (a kid's pages or a grown-up's units).
     * [Suggestion.line] is what the pup says: where they will start and what stays open, never a mark.
     */
    fun suggest(selfReport: Level, score: Int?, kind: ProfileKind = ProfileKind.ADULT, asked: Int = tasksFor(selfReport).size): Suggestion {
        val said = if (kind == ProfileKind.KID) selfReport.forKid else selfReport
        val played = score?.let { supports(it.coerceIn(0, asked.coerceAtLeast(0)), asked) }
        val level = if (played == null || played.ordinal >= said.ordinal) said else played
        val plan = Placement.startFor(level, kind)
        val agrees = level == said
        val line = when {
            score == null -> plan.line
            agrees -> "Lovely playing. ${plan.line}"
            else -> "Let's begin at ${plan.startTitle}" + plan.openLine().let { if (it.isEmpty()) "." else "; " + (if (kind == ProfileKind.KID) it.replaceFirstChar { c -> c.lowercase() } else it) }
        }
        return Suggestion(level, said, score, agrees, line)
    }

    /** The answer: the [level] to begin at, what was [said], the [score] (null: skipped), whether they [agrees], and the pup's [line]. */
    data class Suggestion(val level: Level, val said: Level, val score: Int?, val agrees: Boolean, val line: String)
}
