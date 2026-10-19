package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong

/**
 * The time model for the treehouse and the night sky: the other pages' model (PlaygroundChecks.estimatedSeconds) for
 * every step it knows, and for the 2.3 stages the same kind of sum: the instruction read aloud, each round shown and
 * answered, the pup's lines between rounds, the cheer and Continue after the step.
 */
object TreeSkyTime {
    fun seconds(lesson: CourseLesson): Double = lesson.steps.sumOf(::seconds)

    fun seconds(s: Step): Double {
        val game = StageGames.of(s)
        if (game == null || game == StageGame.NightClap) return PlaygroundChecks.estimatedSeconds(listOf(s))
        val ex = s.exercise!!
        val song = ex.toSong("e", "e")
        val run = song.durationMs / 1000.0
        val keys = song.groups.size
        val beat = 60 / ex.bpm
        val said = s.text.split(' ').size / 2.0 + 1.5 + if (s.kind == StepKind.LISTEN) 0.0 else 4.0
        return said + when (game) {
            // Each phrase: "Let me show you" or the gap, the hand playing it with the fingers said, then two seconds a note to copy.
            is StageGame.Copy -> TreeSkyLines.phrases(song).let { p -> p.size * (1.3 + 0.65 + 1.4 + 1.2) + p.sumOf { it.size } * (0.45 + 2.0) }
            // The pup's example, then each round: its sounds, a few seconds to decide and tap, the pup's answer, the key to play.
            is StageGame.Tap -> (0.7 + game.example.soundMs / 1000.0 + 0.5 + 2.4) + 0.9 +
                game.rounds.sumOf { r -> 0.7 + r.soundMs / 1000.0 + 2.5 + 1.5 + 1.4 + if (r.play != null) 4.0 else 0.0 }
            // The pup's bar first, then the count-in and the beats, and the stars' verdict.
            is StageGame.Beat -> BeatStars(com.bigpapituna.sharpears.engine.parseTokens(ex.right).filter { !it.rest }.map { it.startBeat }, ex.bpm).let { b ->
                4 * beat + 2.5 + b.lengthMs / 1000.0 + 1.5 + 1.4
            }
            is StageGame.HigherLower -> 1.3 + game.rounds.size * (1.2 + 1.4 + 2.5 + 1.0)
            is StageGame.LoudQuiet -> game.rounds.size * (1.3 + 1.0 + 2.0 + 1.5 + 1.4)
            is StageGame.Tempo -> TreeSkyLines.phrases(song).let { p -> p.size * (1.3 + 0.65 + 1.5 + 1.4) + p.sumOf { it.size } * (0.42 + 0.8) }
            StageGame.SkyChord -> run + keys * 4.5
            is StageGame.Jam -> 2.0 + 2.5 + game.bars * 4 * beat + 3.0
            is StageGame.Concert -> 2 * ex.beats * beat + 2.0 + run + 2 * ex.beats * beat + 3.4
            StageGame.NightClap -> error("measured above")
        }
    }
}
