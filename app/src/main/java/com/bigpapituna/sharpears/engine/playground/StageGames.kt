package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.Step

/*
 * 2.3 part B: the treehouse and the night sky draw their own stages for some steps (ui/playground's registry). Their
 * places and the Game enum belong to the world side of 2.3 (Playground.kt), so these steps are marked here instead: a
 * step's stage is looked up by its text, which is unique across the two pages (TreeSkyTest checks it). A step marked
 * here keeps an ordinary kind and exercise (and, for the clapping steps, Game.CLAP), so a screen that does not know the
 * stage plays it in the player, and PlaygroundFlowTest's plain walk still reaches its end.
 */

/** Which of the two new places a stage is drawn in (its own backdrop: the stage draws it, not the world). */
enum class Backdrop { TREEHOUSE, NIGHT }

/** A 2.3 stage: what draws and runs a marked step. */
sealed interface StageGame {
    val backdrop: Backdrop

    /**
     * Copy the pup, a phrase a round (StageRounds with phrases): in the treehouse the painted hand plays each note with
     * its finger and the pup says the fingers ("One, two, three!"); under the stars the stars sing the tune. [byEar]:
     * after the first round the hand stays away and only the pup's words and the sound are left to copy.
     */
    data class Copy(override val backdrop: Backdrop = Backdrop.TREEHOUSE, val byEar: Boolean = false) : StageGame

    /** A tap game ([TapQuiz]): the app plays, the child taps an answer on screen; which-finger rounds then play the key. */
    data class Tap(val kind: TapKind, val example: TapRound, val rounds: List<TapRound>) : StageGame {
        override val backdrop get() = if (kind == TapKind.FINGER) Backdrop.TREEHOUSE else Backdrop.NIGHT
    }

    /** A key on every beat ([BeatStars]): a star lights for each one heard in time, the pup's drum or the metronome keeping the beat. */
    data class Beat(val sound: BeatSound) : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** Play a note higher or lower than the pup's ([HigherLowerJudge]). [rounds] match the exercise's groups. */
    data class HigherLower(val rounds: List<PitchRound>) : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** Single notes graded for loudness ([TuneLoudness] over one-note phrases): your usual way, then loud and quiet. */
    data class LoudQuiet(val rounds: List<Loudness?>) : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** A tune played your usual way, then fast and slow ([TempoTune]): the rabbit and the turtle. */
    data class Tempo(val rounds: List<Pace?>) : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** The happy chord, C E G together ([ChordJudge]): the rain cloud turns into the sun. */
    data object SkyChord : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** Make up a tune on the black keys with the band ([StarJam]): every note a star, the tune drawn across the sky. */
    data class Jam(val bars: Int) : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** The drum game (Game.CLAP's ClapStage) under the stars. */
    data object NightClap : StageGame { override val backdrop get() = Backdrop.NIGHT }

    /** A concert of the step's song: the pup plays its start, then you play it with the band (Perform, [CONCERT_PASS]). */
    data class Concert(override val backdrop: Backdrop, val title: String, val picture: String) : StageGame
}

/** The 2.3 stages by step text (the treehouse's and the night sky's game steps). */
object StageGames {
    private val byText = HashMap<String, StageGame>()

    /** Marks [step] as drawn by [game]; the levels call this as they are built. */
    internal fun mark(step: Step, game: StageGame): Step {
        check(byText.put(step.text, game).let { it == null || it == game }) { "two stages for \"${step.text}\"" }
        return step
    }

    /** The stage [step] is drawn on, or null for a step the player (or the Game registry) runs. */
    fun of(step: Step): StageGame? {
        // The levels mark their steps as they are built: build them first.
        TREEHOUSE.lessons.size + NIGHT_SKY.lessons.size
        return if (step.exercise == null) null else byText[step.text]
    }

    /** Every marked step's text, for the tests. */
    internal val marked: Map<String, StageGame> get() { of(Step(com.bigpapituna.sharpears.engine.StepKind.READ, "")); return byText.toMap() }
}

/** Marks this step as drawn by [game] (see [StageGames]). */
internal fun Step.on(game: StageGame): Step = StageGames.mark(this, game)
