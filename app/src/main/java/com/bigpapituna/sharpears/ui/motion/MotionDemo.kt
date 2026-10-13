package com.bigpapituna.sharpears.ui.motion

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.Cheer
import com.bigpapituna.sharpears.ui.CheerBar
import com.bigpapituna.sharpears.ui.Good
import com.bigpapituna.sharpears.ui.Keyboard
import com.bigpapituna.sharpears.ui.LaneBackground
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.RightHandColor
import com.bigpapituna.sharpears.ui.Stat
import com.bigpapituna.sharpears.ui.WrongGlow
import com.bigpapituna.sharpears.ui.XpBar
import com.bigpapituna.sharpears.ui.cheerTitle
import kotlinx.coroutines.delay

/**
 * Debug only (`--es screen motion [--es demo celebrate|cheer|pup|keys]`): every Team E effect on one screen,
 * so it can be looked at and screenshotted on an emulator without a song or lesson. Not reachable in release.
 */
@Composable
fun MotionDemo(demo: String?, sampler: Sampler?, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    when (demo) {
        "celebrate" -> Celebration("New best!", "Twinkle at full speed, cleaner than ever.",
            listOf(Stat("95%", "Accuracy"), Stat("3:12", "Time"), Stat("22", "Longest streak")),
            primary = "Continue", onPrimary = onClose, pose = Pose.LEVEL_UP, sampler = sampler,
            newBest = true, xpGained = 120, xpBar = XpBar(0.7f, 1.25f, "Level 4"))
        "cheer" -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            var n by remember { mutableStateOf(0) }
            CheerBar(if (n % 2 == 0) Cheer.GOOD else Cheer.MISS, if (n % 2 == 0) cheerTitle(n) else "Not quite", "Tap the button to see the other one.",
                "Continue", Modifier.align(Alignment.BottomCenter)) { n++ }
        }
        "keys" -> KeysDemo()
        else -> PupDemo(onClose)
    }
}

@Composable
private fun PupDemo(onClose: () -> Unit) {
    val haptics = rememberHaptics()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Pup idle life (tap him; sleepy after 8 s here)", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pup(Pose.WELCOME, size = 120.dp, onTap = { haptics.success() }, sleepyAfterMs = 8_000)
            Pup(Pose.LISTENING, size = 120.dp, onTap = { haptics.tick() })
            Pup(Pose.RESTING, size = 120.dp, onTap = { haptics.miss() })
        }
        Text("Loading", style = MaterialTheme.typography.titleMedium)
        Shimmer(Modifier.fillMaxWidth().height(56.dp))
        Shimmer(Modifier.fillMaxWidth(0.6f).height(20.dp))
        listOf("A tappable row (press scale)", "Another row").forEach {
            Text(it, Modifier.fillMaxWidth().pressScale().clickable { haptics.tick() }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(20.dp)).padding(18.dp))
        }
        TextButton(onClick = onClose) { Text("Close") }
    }
}

/** A keyboard with ripples on a timer and the ghost hand playing a little C major line with both hands. */
@Composable
private fun KeysDemo() {
    val notes = remember {
        val right = listOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5, 69 to 5, 66 to 4, 64 to 3)
        val left = listOf(48 to 5, 52 to 3, 55 to 1, 52 to 3)
        right.mapIndexed { i, (m, f) -> GhostNote(m, Hand.RIGHT, f, 1000L + i * 600, 500) } +
            left.mapIndexed { i, (m, f) -> GhostNote(m, Hand.LEFT, f, 1000L + i * 1200, 1000) } +
            GhostNote(60, Hand.RIGHT, 1, 6000, 800) + GhostNote(64, Hand.RIGHT, 3, 6000, 800) + GhostNote(67, Hand.RIGHT, 5, 6000, 800)
    }.sortedBy { it.startMs }
    var pos by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) withFrameNanos { now -> if (last != 0L) pos = (pos + (now - last) / 1e6) % 8000.0; last = now }
    }
    val flash = remember { mutableStateMapOf<Int, Color>() }
    val lit = remember { mutableStateMapOf<Int, Color>() }
    LaunchedEffect(Unit) {
        var i = 0
        while (true) {
            delay(700)
            val m = listOf(60, 62, 64, 65, 67, 61, 66)[i % 7]
            flash.clear(); flash[m] = if (i % 4 == 3) WrongGlow else Good
            lit[m] = RightHandColor; delay(250); lit.remove(m)
            i++
        }
    }
    Column(Modifier.fillMaxSize().background(LaneBackground).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Text("Ghost hand, key press, ripples, sheen", color = Color.White, modifier = Modifier.padding(16.dp))
        Box(Modifier.fillMaxWidth().weight(1f))
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            Keyboard(48, 72, lit, { _, _ -> }, { }, Modifier.fillMaxSize(), flash = flash)
            GhostHand(notes, { pos }, 48, 72, Modifier.fillMaxSize())
        }
    }
}

