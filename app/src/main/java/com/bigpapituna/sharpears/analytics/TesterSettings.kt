package com.bigpapituna.sharpears.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.ui.brief.SwitchRow

/** "Help Roy test"'s one line (Roy, 2026-10-21). */
const val HELP_ROY_TEST_LINE = "Sends screen recordings and app events to Roy's test dashboard. No sound."

/** 2.13: the one-time notice when the default turned Help Roy test on (Roy's words, 2026-10-25). */
const val DEFAULT_ON_NOTICE = "This test build sends screen recordings and app events to Roy to help improve the app. No sound is recorded. " +
    "You can turn this off in Settings, Testing."

/**
 * 2.13: the notice that the default turned Help Roy test on, once, to a grown-up (MainActivity shows it while
 * [Tester.noticePending] and the profile is a grown-up's, never on the stage): a small card at the bottom of the screen with OK.
 * It holds nothing up: the screen under it works as before, and only the card itself takes touches.
 */
@Composable
fun TesterDefaultNotice(onOk: () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
        androidx.compose.material3.Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().testerMask(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp) {
            Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Help Roy test is on", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(DEFAULT_ON_NOTICE, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onOk, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text("OK") }
            }
        }
    }
}

/**
 * The tester switches (analytics/Tester.kt): "Help Roy test", "Email Roy a daily diary" (only on a build with DIARY_TO) and,
 * while either is on, "Something felt off". In the kids' grown-ups corner (already behind the gate: [guard] runs at once) and
 * in Settings > Family (each change through the gate). Nothing shows on a build that has neither (a release without a key).
 * Masked in the replays as a whole.
 */
@Composable
fun TesterSettings(modifier: Modifier = Modifier, guard: (() -> Unit) -> Unit = { it() },
                   /** False under Settings' own Testing header (2.7). */
                   titled: Boolean = true) {
    val context = LocalContext.current.applicationContext
    val row = Tester.rowShown(context)
    val diaryRow = Tester.diaryBuilt
    if (!row && !diaryRow) return
    val on by Tester.switchOn
    val diary by Tester.diaryOn
    var noting by rememberSaveable { mutableStateOf(false) }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth().testerMask(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (titled) Text("Testing", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (row) {
            SwitchRow("Help Roy test", HELP_ROY_TEST_LINE, checked = on, enabled = true) { v -> guard { Tester.setOn(context, v) } }
            if (!Tester.hasKey) Text("This build has no test dashboard key, so nothing is sent.", style = MaterialTheme.typography.bodySmall, color = dim)
        }
        if (diaryRow) {
            SwitchRow("Email Roy a daily diary", "Once a day, a plain email to Roy: which screens, lessons and tries, and your notes. No names.",
                checked = diary, enabled = true) { v -> guard { Tester.setDiary(context, v) } }
            if (!BriefEmail.hasKey) Text("Email is not set up on this build.", style = MaterialTheme.typography.bodySmall, color = dim)
        }
        if (on || diary) TextButton(onClick = { noting = true }, modifier = Modifier.padding(top = 4.dp)) { Text("Something felt off") }
    }
    if (noting) FeltOffDialog(onDone = { text -> text?.let(Tester::note); noting = false })
}

/** One line about what felt off; it is kept with the screen the app was on (and the one before). */
@Composable
private fun FeltOffDialog(onDone: (String?) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = { onDone(null) }, title = { Text("Something felt off") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("One line for Roy. It is kept with the screen you were on.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.replace('\n', ' ').take(300) }, singleLine = true, label = { Text("What happened") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onDone(text) }),
                    modifier = Modifier.fillMaxWidth().testerMask())
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onDone(text) }) { Text("Keep it") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } })
}
