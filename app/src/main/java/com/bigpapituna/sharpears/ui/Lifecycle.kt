package com.bigpapituna.sharpears.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * True for a screen or home tab on its way out: AnimatedContent keeps it composed until its exit ends (the player's
 * curtain takes 720 ms). Its mic used to stop only then, after the next screen's start had found the mic running and
 * done nothing, so the next screen showed the mic on and heard nothing (Microphone setup to its mic check and back, a
 * chord game to the tuner). [onScreen] is false for it, so it lets go of the mic at once.
 */
val LocalLeaving = androidx.compose.runtime.compositionLocalOf { false }

/** True while the activity is on screen (started) and this screen is not leaving. The microphone must not run otherwise. */
@Composable
fun onScreen(): Boolean {
    val owner = LocalLifecycleOwner.current
    var started by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, _ -> started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return started && !LocalLeaving.current
}
