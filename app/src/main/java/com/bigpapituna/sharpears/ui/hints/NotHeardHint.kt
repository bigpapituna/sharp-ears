package com.bigpapituna.sharpears.ui.hints

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.hints.MicHearing
import com.bigpapituna.sharpears.engine.hints.NotHeard
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.StageRaised
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup

/**
 * 2.15, "could not hear" (engine/hints/MicHearing.kt): a calm line near the listening indicator, never a sheet over the
 * notes. "I can't hear the piano clearly." with the cause under it, then [MicHearing.CLOSER] ([onCloser]: dismiss) and
 * [MicHearing.TOUCH] ([onTouch]: this step on the screen's keys). A kid's has the listening pup beside it (and the pup says
 * the cause line, when it is recorded); a grown-up's is words only. TalkBack reads it as it appears.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotHeardHint(cause: NotHeard, kid: Boolean, onCloser: () -> Unit, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.widthIn(max = 520.dp).background(StageRaised.copy(alpha = 0.96f), shape)
        .border(1.dp, InkDim.copy(alpha = 0.35f), shape).padding(horizontal = 16.dp, vertical = 12.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (kid) Pup(Pose.LISTENING, size = 44.dp, idle = false)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(MicHearing.TITLE, style = MaterialTheme.typography.titleSmall, color = Ink)
                Text(cause.line, style = MaterialTheme.typography.bodyMedium, color = InkDim)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCloser, modifier = Modifier.heightIn(min = 44.dp)) { Text(MicHearing.CLOSER, color = Ink) }
            OutlinedButton(onClick = onTouch, modifier = Modifier.heightIn(min = 44.dp)) { Text(MicHearing.TOUCH, color = Ink) }
        }
    }
}
