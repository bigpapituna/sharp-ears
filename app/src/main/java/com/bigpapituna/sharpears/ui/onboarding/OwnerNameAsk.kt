package com.bigpapituna.sharpears.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.engine.family.OwnerName
import com.bigpapituna.sharpears.engine.family.Profile

/**
 * The one-time question on a phone that already has an owner called "Roy" from before 2.14 (OwnerName.askDue): "What should
 * we call you?" Save keeps what was typed; Not now (or an empty field) leaves them unnamed and greeted "Hi there". Either way
 * it is never asked again ([onAnswer] gets the text, blank for none). Nothing here is required.
 */
@Composable
fun OwnerNameAsk(onAnswer: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val focus = LocalFocusManager.current
    AlertDialog(
        onDismissRequest = { onAnswer("") },
        title = { Text(OwnerName.QUESTION) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(OwnerName.HINT)
                OutlinedTextField(name, { name = it.take(Profile.MAX_NAME) }, label = { Text("First name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(name) }, enabled = name.isNotBlank()) { Text(OwnerName.SAVE) } },
        dismissButton = { TextButton(onClick = { onAnswer("") }) { Text(OwnerName.LATER) } },
    )
}
