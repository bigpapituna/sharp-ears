package com.bigpapituna.sharpears.ui.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.playground.Game
import com.bigpapituna.sharpears.ui.LessonDeps
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.Stage

/*
 * The Playground's registry (2.1): the steps of Pup's Playground that the embedded player cannot show draw their own
 * stage here. LessonScreen asks [hasPlaygroundStage] for the step it is on and, when true, shows [PlaygroundStage]
 * where it would show the embedded PlayerScreen (the pup's instruction above it stays as it is). See INTEGRATION.md.
 */

/** True when the Playground draws [step] itself; otherwise the embedded player runs it. */
fun hasPlaygroundStage(step: Step): Boolean = step.game != null && step.exercise != null

/**
 * The stage for a Playground game step, in the lesson's one raised dark panel. It runs the step (its own microphone,
 * input and sound) and calls [onEnded] once with the run's report, as the embedded player's onFinished does, so
 * LessonScreen's exerciseEnded(step, report) grades and moves on unchanged. [inject] is the debug microphone
 * injection for the step opened first (MicInput.startInjection, plus "claps:" and "dyn:", see [debugInjection]).
 * Key the call on the step and its retry count, as LessonScreen keys the player, so a retry starts fresh.
 */
@Composable
fun PlaygroundStage(step: Step, lessonId: String, deps: LessonDeps, inject: String?, onEnded: (Report) -> Unit, modifier: Modifier = Modifier) {
    SharpEarsTheme(dark = true) {
        Box(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(Stage)) {
            when (step.game) {
                Game.HIGH_LOW -> HighLowStage(step, lessonId, deps, inject, onEnded)
                Game.KEY_HUNT -> KeyHuntStage(step, lessonId, deps, inject, onEnded)
                Game.CLAP -> ClapStage(step, lessonId, deps, inject, onEnded)
                Game.SOFT_LOUD -> SoftLoudStage(step, lessonId, deps, inject, onEnded)
                Game.CONCERT -> ConcertStage(step, lessonId, deps, inject, onEnded)
                Game.ECHO -> EchoStage(step, lessonId, deps, inject, onEnded)
                Game.CHORD -> ChordStage(step, lessonId, deps, inject, onEnded)
                Game.SOFT_LOUD_TUNE -> TuneStage(step, lessonId, deps, inject, onEnded)
                Game.SET_LIST -> SetListStage(step, lessonId, deps, inject, onEnded)
                null -> {}
            }
        }
    }
}
