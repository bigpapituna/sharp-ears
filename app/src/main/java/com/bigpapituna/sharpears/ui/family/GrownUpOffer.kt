package com.bigpapituna.sharpears.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupSays

/** The pup's line when a kid finishes Beginner (shown on their Learn path, once). */
fun grownUpLine(name: String): String = "You finished Beginner, $name! Ask a grown-up about grown-up mode."

/** What a grown-up reads under it. */
const val GROWN_UP_NOTE = "For grown-ups: grown-up mode shows scores and timings and opens the whole course without the kid " +
    "words. Progress, stickers and songs stay, and Settings, under Family, can switch it back."

/**
 * The offer of grown-up mode once a kid has finished Beginner (KIDS-PLAN section 1: the app suggests it once). "Grown-up
 * mode" goes through the parent gate; either answer puts the card away for good.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrownUpOffer(name: String, onSwitch: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PupSays(Pose.GREAT_JOB, grownUpLine(name), size = 64.dp, idle = false)
        Text(GROWN_UP_NOTE, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            ChunkyButton(onClick = onSwitch) { Text("Grown-up mode") }
            TextButton(onClick = onLater) { Text("Not yet") }
        }
    }
}
