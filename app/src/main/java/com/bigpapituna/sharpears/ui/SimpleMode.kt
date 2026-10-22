package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.nav.Simple

/**
 * The open person's Simple mode (2.7, engine/nav/Simple.kt): [on] while it filters, [set] to change it (kept per profile). A
 * kid's is always off and has no way to change (they never see it). Provided by MainActivity.
 */
class SimpleMode(val on: Boolean, val set: (Boolean) -> Unit)

val LocalSimple = compositionLocalOf { SimpleMode(false) {} }

/** The small "Simple" pill (Today's header, You): selected while Simple is on, a tap switches. */
@Composable
fun SimplePill(modifier: Modifier = Modifier) {
    val s = LocalSimple.current
    FilterChip(s.on, { s.set(!s.on) }, label = { Text(Simple.NAME) }, modifier = modifier.semantics { stateDescription = if (s.on) "Simple mode on" else "Simple mode off" },
        leadingIcon = if (s.on) { { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
}

/**
 * The quiet foot of a place in Simple mode: "Show more" ([label]) shows the place in full for this visit ([onShow]); once it
 * shows ([shown]), the offer to switch to Advanced for good. Draws nothing in Advanced.
 */
@Composable
fun SimpleMore(shown: Boolean, onShow: () -> Unit, modifier: Modifier = Modifier, label: String = Simple.SHOW_MORE) {
    val s = LocalSimple.current
    if (!s.on) return
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!shown) TextButton(onClick = onShow) { Text(label) }
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(Simple.SWITCH_QUESTION, style = MaterialTheme.typography.titleSmall)
                Text(Simple.SWITCH_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { s.set(false) }) { Text(Simple.SWITCH) }
        }
    }
}
