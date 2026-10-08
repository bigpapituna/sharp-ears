package com.bigpapituna.sharpears.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R

/** The Golden Coach Pup's poses (art/mascot/sheet.jpg, cut by scripts/cut_mascot.py), each with its own small motion. */
enum class Pose(val res: Int) {
    WELCOME(R.drawable.pup_welcome), PRACTICING(R.drawable.pup_practicing), GREAT_JOB(R.drawable.pup_great_job),
    MISTAKE(R.drawable.pup_mistake), LEARNING(R.drawable.pup_learning), LEVEL_UP(R.drawable.pup_level_up),
    STREAK(R.drawable.pup_streak), LISTENING(R.drawable.pup_listening), GET_READY(R.drawable.pup_get_ready),
    RESTING(R.drawable.pup_resting),
}

/**
 * The pup in [pose]. A new pose hops in; then it keeps moving a little the way the pose suggests (a wave, a jump,
 * a head tilt, slow breathing). Still when the phone's animations are off. Decorative: the text beside it says
 * what it means, so screen readers skip it.
 */
@Composable
fun Pup(pose: Pose, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val hop = remember { Animatable(1f) }
    LaunchedEffect(pose) {
        if (still) return@LaunchedEffect
        hop.snapTo(0.6f)
        hop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }
    val t = rememberInfiniteTransition(label = "pup")
    // One 0..1 cycle; each pose turns it into its own motion below.
    val period = when (pose) { Pose.RESTING -> 3200; Pose.GREAT_JOB, Pose.LEVEL_UP -> 900; Pose.STREAK -> 420; Pose.PRACTICING -> 500; else -> 1600 }
    val c by t.animateFloat(0f, 1f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "cycle")
    val wave = kotlin.math.sin(c * 2 * Math.PI).toFloat()          // -1..1, smooth
    val bounce = kotlin.math.abs(kotlin.math.sin(c * Math.PI)).toFloat()   // 0..1..0, a hop per cycle
    val m = if (still) 0f else 1f
    Image(painterResource(pose.res), contentDescription = null, modifier = modifier.size(size).graphicsLayer {
        transformOrigin = TransformOrigin(0.5f, 1f)                 // moves from the feet
        val s = hop.value
        scaleX = s; scaleY = s
        when (pose) {
            Pose.WELCOME -> rotationZ = 4f * wave * m
            Pose.GREAT_JOB, Pose.LEVEL_UP -> { translationY = -size.toPx() * 0.12f * bounce * m; scaleY = s * (1f - 0.04f * (1 - bounce) * m) }   // squash on landing
            Pose.STREAK -> { translationY = -size.toPx() * 0.04f * bounce * m; rotationZ = -3f * m }
            Pose.PRACTICING -> translationY = -size.toPx() * 0.02f * bounce * m
            Pose.MISTAKE -> rotationZ = (if (wave > 0) 5f * wave else 0f) * m   // a thoughtful tilt, then back
            Pose.LEARNING -> rotationZ = 1.5f * wave * m
            Pose.LISTENING -> rotationZ = 3f * wave * m                         // swaying to the music
            Pose.GET_READY -> { val p = 1f + 0.05f * bounce * m; scaleX = s * p; scaleY = s * p }
            Pose.RESTING -> scaleY = s * (1f + 0.025f * wave * m)               // slow breathing
        }
    })
}

/** The pup with one line beside it, for the moments it speaks up (results, encouragement, empty states). */
@Composable
fun PupSays(pose: Pose, line: String, modifier: Modifier = Modifier, size: Dp = 72.dp) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pup(pose, size = size)
        Text(line, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
    }
