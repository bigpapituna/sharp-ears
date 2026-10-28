package com.bigpapituna.sharpears.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.audio.Stage
import com.bigpapituna.sharpears.engine.stage.StageRule
import kotlinx.coroutines.delay

/** The pill's words. */
const val LISTENING_AGAIN = "Listening again"

/**
 * 2.17 (audit M3), the Stage's since 2.18: "Listening again", small and quiet, as the mic hears again after a line that asked
 * for it (Stage.lineStarts cue: the coach's) made it deaf, for StageRule.CUE_MS. [hidden]: something else holds the spot (the
 * coach's own words). Drawn by whoever owns that spot on screen (the coach's overlay).
 */
@Composable
fun ListeningAgain(hidden: Boolean) {
    val cue = Stage.listeningAgain.intValue
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(cue) { if (cue == 0) return@LaunchedEffect; shown = true; delay(StageRule.CUE_MS); shown = false }
    AnimatedVisibility(shown && !hidden, enter = fadeIn(), exit = fadeOut()) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)) {
            Text(LISTENING_AGAIN, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        }
    }
}
