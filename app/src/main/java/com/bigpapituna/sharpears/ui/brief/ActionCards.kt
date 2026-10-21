package com.bigpapituna.sharpears.ui.brief

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.brief.ActionInput
import com.bigpapituna.sharpears.engine.brief.ActionView
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.SongChoice
import com.bigpapituna.sharpears.ui.ChunkyButton

/**
 * A recommendation: its heading, the writer's words, and the action as a ChunkyButton. Pressing the button asks the parent
 * gate first (the caller's [onPress] does), then opens the confirmation sheet; once done the card shows "Done" and, where the
 * action can be taken back, Undo.
 */
@Composable
fun RecommendationCard(
    heading: String, text: String, view: ActionView, state: CardState,
    onPress: () -> Unit, onUndo: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(heading, style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.semantics { heading() })
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
        when (state) {
            is CardState.Done -> Row(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.tertiary), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onTertiary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Done", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = c.tertiary)
                    Text(state.line, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                if (state.canUndo) TextButton(onClick = onUndo) { Text("Undo") }
            }
            is CardState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(state.reason, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                TextButton(onClick = onRetry) { Text("Back to the button") }
            }
            CardState.Working -> ChunkyButton(onClick = {}, enabled = false) { Text("Working on it") }
            else -> ChunkyButton(onClick = onPress) { Text(view.button) }
        }
    }
}

/**
 * The confirmation sheet for a [Candidate]: what will happen, in words, then "Yes" and "Not now". A fresh start asks
 * twice. Teacher mode also asks for the song (from [songs]) and a note. [onConfirm] is called with the input when the last
 * yes is pressed; [onNext] moves a two step sheet on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionSheet(
    view: ActionView, state: CardState.Confirm, songs: List<SongChoice>,
    onChange: (CardState.Confirm) -> Unit, onConfirm: (ActionInput) -> Unit, onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val second = state.step == 2
    val title = if (second) view.secondTitle else view.title
    val body = if (second) view.secondBody else view.body
    val label = if (second) view.secondConfirm else view.confirm
    val ready = !view.needsSong || state.song != null
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surfaceContainerLow) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().align(Alignment.CenterHorizontally).navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            if (view.needsSong) {
                if (songs.isEmpty()) Text("There are no songs to offer yet.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    songs.take(6).forEach { s ->
                        val chosen = state.song == s.id
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                            .background(if (chosen) c.primaryContainer else c.surfaceContainerHigh)
                            .then(if (chosen) Modifier.border(2.dp, c.primary, RoundedCornerShape(16.dp)) else Modifier)
                            .selectable(chosen, role = Role.RadioButton) { onChange(state.copy(song = s.id)) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(s.title, style = MaterialTheme.typography.titleSmall, color = if (chosen) c.onPrimaryContainer else c.onSurface, modifier = Modifier.weight(1f))
                            if (chosen) Icon(painterResource(R.drawable.ic_check), contentDescription = "Chosen", tint = c.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                OutlinedTextField(state.note, { onChange(state.copy(note = it.take(com.bigpapituna.sharpears.engine.brief.BriefActions.MAX_NOTE))) },
                    label = { Text("A note to go with it (optional)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), minLines = 2, maxLines = 3,
                    supportingText = { Text("Stays on this phone. ${state.note.length} of ${com.bigpapituna.sharpears.engine.brief.BriefActions.MAX_NOTE}") })
            }
            if (view.confirmations == 2) Text(if (second) "Step 2 of 2" else "Step 1 of 2", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            ChunkyButton(enabled = ready, modifier = Modifier.fillMaxWidth(), onClick = {
                if (view.confirmations == 2 && !second) onChange(state.copy(step = 2)) else onConfirm(ActionInput(state.song, state.note))
            }) { Text(label) }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Not now") }
        }
    }
}

/** The heading for a recommendation card. */
fun cardHeading(c: Candidate) = BriefFormat.kindLabel(c.recommendation)
