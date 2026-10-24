package com.bigpapituna.sharpears.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.Pup
import com.bigpapituna.sharpears.ui.family.isKid
import com.bigpapituna.sharpears.ui.motion.pressScale

/** The three toys of the play corner (2.1): nothing to pass, no score, no timer. */
enum class Toy(val title: String, val line: String, val pose: Pose) {
    JAM("Jam", "Play anything and watch it light up.", Pose.DANCING),
    PUP_SAYS("Pup says", "Copy the pup's tunes, alone or taking turns.", Pose.POINTING),
    CLAP("Clap along", "Tap or clap with the pup's drum.", Pose.DRUMMING),
}

/**
 * The Play corner: the Jam, Pup says and Clap along as three tiles, each the pup doing it. Since 2.7 a grown-up finds it at
 * the top of the Songs tab and in Today's Explore (it was on the home). A kid gets bigger pups and no words under them (the
 * pictures say it); a grown-up a line each. [titled] false leaves the title to a sheet that has its own.
 */
@Composable
fun PlayRow(onToy: (Toy) -> Unit, modifier: Modifier = Modifier, titled: Boolean = true) {
    val kid = isKid()
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titled) Row(verticalAlignment = Alignment.Bottom) {
            Text(com.bigpapituna.sharpears.engine.nav.Names.PLAY_CORNER, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("  just for fun", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        // One height for the three, whatever wraps.
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Toy.entries.forEach { t ->
                Column(Modifier.weight(1f).fillMaxHeight().pressScale().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh)
                    .clickable(role = Role.Button, onClickLabel = "Open ${t.title}") { onToy(t) }.padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pup(t.pose, size = if (kid) 76.dp else 60.dp, idle = false)
                    Text(t.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1)
                    if (!kid) Text(t.line, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 3)
                }
            }
        }
    }
}
