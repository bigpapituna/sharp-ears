package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.MySongs
import com.bigpapituna.sharpears.engine.MySongs.Words

/** A picked file waiting for its title and hands (My songs, 2.8.1): nothing is saved until Add. */
class MySongDraft(val bytes: ByteArray, val scan: MySongs.Scan)

/**
 * The title dialog after "Add a song from a file": the title the file suggested (editable), how much of the tune was kept,
 * and, when the file has a left hand, Melody only (the default) or Both hands. Add saves it; Cancel keeps nothing.
 */
/** A text field's value holding [text] with the caret after its last character: a rename starts where typing adds to it. */
fun caretAtEnd(text: String) = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length))

@Composable
fun MySongDialog(draft: MySongDraft, onAdd: (title: String, bothHands: Boolean) -> Unit, onCancel: () -> Unit) {
    // The file's title, the caret after it (QA D8): typing adds to the title rather than in front of it.
    var field by remember(draft) { mutableStateOf(caretAtEnd(draft.scan.title)) }
    val title = field.text
    var both by remember(draft) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(Words.DIALOG_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(field, { field = it.copy(text = it.text.take(MySongs.TITLE_MAX)) }, label = { Text(Words.TITLE_LABEL) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("${Words.EASY_LINE} ${Words.notes(draft.scan.melodyNotes)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (draft.scan.hasLeft) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false to Words.MELODY_ONLY, true to Words.BOTH_HANDS).forEachIndexed { i, (value, label) ->
                            SegmentedButton(both == value, { both = value }, SegmentedButtonDefaults.itemShape(i, 2), icon = {}) { Text(label, maxLines = 1) }
                        }
                    }
                    Text(Words.BOTH_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { onAdd(title.trim(), both && draft.scan.hasLeft) }) { Text(Words.ADD_BUTTON) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(Words.CANCEL) } },
    )
}
