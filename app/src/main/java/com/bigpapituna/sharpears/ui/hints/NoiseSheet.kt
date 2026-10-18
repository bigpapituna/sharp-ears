package com.bigpapituna.sharpears.ui.hints

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.StageRaised


/**
 * The room-noise watchdog's offer, once per session, over the stage: the pup asks, "Keep showing" leaves everything
 * as it is, "Stop for this session" stops painting wrong keys until the app closes (the setting is never changed),
 * and [onPause] (shown when nothing has been played for a while in wait mode) switches the mic off for this song.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoiseSheet(onKeep: () -> Unit, onStop: () -> Unit, onPause: (() -> Unit)?, modifier: Modifier = Modifier, kid: Boolean = false) {
    Column(modifier.widthIn(max = 560.dp).background(StageRaised, RoundedCornerShape(20.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
        PupSays(Pose.LISTENING, com.bigpapituna.sharpears.engine.hints.NoiseWatch.line(kid), size = 64.dp, idle = false)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChunkyButton(onClick = onKeep) { Text("Keep showing") }
            ChunkyButton(onClick = onStop) { Text("Stop for this session") }
            if (onPause != null) OutlinedButton(onClick = onPause, modifier = Modifier.align(Alignment.CenterVertically)) { Text("Pause listening") }
        }
    }
}
