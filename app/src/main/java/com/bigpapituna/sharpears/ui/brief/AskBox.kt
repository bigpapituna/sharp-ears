package com.bigpapituna.sharpears.ui.brief

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.brief.AskRules
import com.bigpapituna.sharpears.engine.brief.Exchange
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.SpeechBubble

/**
 * "Ask the brief": a text field with a microphone button (when this phone can recognise speech on itself), starter
 * questions, and the exchanges so far, the pup answering from the facts above. The answer comes from the writer
 * ([onAsk] -> BriefController.ask); the first name never goes with the question (AskRules.forWriter). Asking (typed, a
 * starter, or the microphone) goes through [guard] first: the brief's parent gate (QA 2.8.3: Show Mum's note asked the AI
 * after the hold alone).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskBox(
    exchanges: List<Exchange>, busy: Boolean, starters: List<String>, onAsk: (String) -> Unit,
    modifier: Modifier = Modifier, dictation: Dictation = rememberDictation(),
    /** A draft handed in by a test or the demo route. */
    initial: String = "",
    /** Runs an action once a grown-up has passed the gate (BriefPage passes guardBrief). */
    guard: (() -> Unit) -> Unit = { it() },
) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var text by remember { mutableStateOf(initial) }
    val focus = LocalFocusManager.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) dictation.start(text) { text = it } else dictation.message = "Allow the microphone to talk instead of typing."
    }
    fun send() {
        if (busy || AskRules.clean(text) == null) return
        guard { if (AskRules.clean(text) != null) { onAsk(text); text = ""; focus.clearFocus() } }
    }
    fun toggleMic() {
        if (dictation.listening) { dictation.stop(); return }
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) dictation.start(text) { text = it }
        else askMic.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Ask about this brief", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("The answer comes from the facts above. Names stay on this phone.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        exchanges.forEach { e -> ExchangeView(e) }
        if (text.isBlank() && !dictation.listening) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            starters.forEach { s ->
                Box(Modifier.heightIn(min = 40.dp).clip(CircleShape).background(c.surfaceContainerHigh).clickable(enabled = !busy) { guard { onAsk(s) } }
                    .padding(horizontal = 16.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(s, style = MaterialTheme.typography.labelLarge, color = c.onSurface)
                }
            }
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(text, { text = it.take(AskRules.MAX_QUESTION) }, modifier = Modifier.weight(1f),
                placeholder = { Text(if (dictation.listening) "Listening" else "Type or tap the microphone") },
                shape = RoundedCornerShape(28.dp), minLines = 1, maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                trailingIcon = if (!dictation.available) null else { {
                    val tint by animateColorAsState(if (dictation.listening) c.primary else c.onSurfaceVariant, label = "mic")
                    IconButton(onClick = { guard(::toggleMic) }) {
                        Icon(painterResource(R.drawable.ic_mic), contentDescription = if (dictation.listening) "Stop listening" else "Speak your question", tint = tint)
                    }
                } },
                supportingText = {
                    Text(dictation.message ?: when {
                        dictation.listening -> "Listening. Tap the microphone when you are done."
                        !dictation.available -> "Dictation is not available on this phone, so type your question."
                        else -> "Speech is turned into text on this phone."
                    }, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                })
            ChunkyButton(onClick = ::send, enabled = !busy && AskRules.clean(text) != null, modifier = Modifier.padding(top = 4.dp)) { Text("Ask") }
        }
    }
}

/** One question (right) and the pup's answer (left, in his bubble). */
@Composable
private fun ExchangeView(e: Exchange) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(e.question, style = MaterialTheme.typography.bodyLarge, color = c.onSecondaryContainer,
                modifier = Modifier.widthIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(c.secondaryContainer).padding(horizontal = 16.dp, vertical = 10.dp))
        }
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Image(painterResource(R.drawable.pup_thinking), contentDescription = null, modifier = Modifier.size(52.dp))
            SpeechBubble(when {
                e.pending -> "Let me look."
                e.failed -> AskRules.COULD_NOT
                else -> e.answer.orEmpty()
            }, Modifier.weight(1f, fill = false), MaterialTheme.typography.bodyLarge)
        }
    }
}
