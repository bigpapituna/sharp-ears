package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Step

/*
 * 2.3, the treehouse and the night sky: some of their games need more than the step's exercise to run (which copy game,
 * the tap game's rounds, the beat's sound, the rounds of higher or lower, loud and quiet, fast and slow, the jam's
 * bars). The step carries its [Game] (the registry, ui/playground PlaygroundStage, picks the stage by it); the rest is
 * kept here by step text, as the workshop's pick rounds are ([pickRounds]). A step's text is unique across the course
 * (TreeSkyTest checks it), and a kid's bite keeps it.
 */

/** What one of the 2.3 stages needs besides its step: the [game] the step carries and the [place] it is drawn in. */
sealed interface StageGame {
    val game: Game
    val place: Place

    /**
     * Copy the pup, a phrase a round (StageRounds with phrases): in the treehouse the painted hand plays each note with
     * its finger and the pup says the fingers ("One, two, three!"); under the stars the stars sing the tune. [byEar]:
     * after the first round the hand stays away and only the pup's words and the sound are left to copy.
     */
    data class Copy(override val place: Place = Place.TREEHOUSE, val byEar: Boolean = false) : StageGame {
        override val game get() = Game.COPY
    }

    /** A tap game ([TapQuiz]): the app plays, the child taps an answer on screen; which-finger rounds then play the key. */
    data class Tap(val kind: TapKind, val example: TapRound, val rounds: List<TapRound>) : StageGame {
        override val game get() = Game.TAP
        override val place get() = if (kind == TapKind.FINGER) Place.TREEHOUSE else Place.NIGHT_SKY
    }

    /** A key on every beat ([BeatStars]): a star lights for each one heard in time, the pup's drum or the metronome keeping the beat. */
    data class Beat(val sound: BeatSound) : StageGame {
        override val game get() = Game.BEAT
        override val place get() = Place.NIGHT_SKY
    }

    /** Play a note higher or lower than the pup's ([HigherLowerJudge]). [rounds] match the exercise's groups. */
    data class HigherLower(val rounds: List<PitchRound>) : StageGame {
        override val game get() = Game.HIGHER_LOWER
        override val place get() = Place.NIGHT_SKY
    }

    /** Single notes graded for loudness ([TuneLoudness] over one-note phrases): your usual way, then loud and quiet. */
    data class LoudQuiet(val rounds: List<Loudness?>) : StageGame {
        override val game get() = Game.LOUD_QUIET
        override val place get() = Place.NIGHT_SKY
    }

    /** A tune played your usual way, then fast and slow ([TempoTune]): the rabbit and the turtle. */
    data class Tempo(val rounds: List<Pace?>) : StageGame {
        override val game get() = Game.TEMPO
        override val place get() = Place.NIGHT_SKY
    }

    /** Make up a tune on the black keys with the band ([StarJam]): every note a star, the tune drawn across the sky. */
    data class Jam(val bars: Int) : StageGame {
        override val game get() = Game.JAM
        override val place get() = Place.NIGHT_SKY
    }
}

/** The 2.3 stages' data by step text (the treehouse's and the night sky's game steps that need more than their exercise). */
object StageGames {
    private val byText = HashMap<String, StageGame>()

    /** Keeps [game]'s data for [step]; the levels call this (through [on]) as they are built. */
    internal fun mark(step: Step, game: StageGame): Step {
        check(step.game == game.game) { "\"${step.text}\" carries ${step.game}, its data is for ${game.game}" }
        check(byText.put(step.text, game).let { it == null || it == game }) { "two stages for \"${step.text}\"" }
        return step
    }

    /** The data of [step]'s stage, or null for a step whose stage needs none (or that the player runs). */
    fun of(step: Step): StageGame? {
        // The levels keep their steps' data as they are built: build them first.
        TREEHOUSE.lessons.size + NIGHT_SKY.lessons.size
        return if (step.exercise == null) null else byText[step.text]?.takeIf { it.game == step.game }
    }

    /** [step]'s data as the stage its [Game] draws needs it. */
    inline fun <reified T : StageGame> forStage(step: Step): T =
        of(step) as? T ?: error("no ${T::class.simpleName} for \"${step.text}\" (${step.game})")

    /** Every step's data by its text, for the tests and the voice catalog. */
    internal val marked: Map<String, StageGame> get() { of(Step(com.bigpapituna.sharpears.engine.StepKind.READ, "")); return byText.toMap() }
}

/** This step plays [game] (its [Game] set, its data kept by [StageGames]). */
internal fun Step.on(game: StageGame): Step = StageGames.mark(copy(game = game.game), game)
