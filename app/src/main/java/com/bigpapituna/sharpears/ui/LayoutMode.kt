package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.engine.nav.Simple

/**
 * The open person's layout (2.8, engine/nav/Layout.kt and Simple.kt; 2.7's Simple mode): [layout] as the screens filter by it,
 * [set] to change it (kept per profile, told to the brief). A kid's is always Full with no way to change it (they never see
 * it). Provided by MainActivity.
 */
class LayoutMode(val layout: Layout, val set: (Layout) -> Unit)

val LocalLayout = compositionLocalOf { LayoutMode(Layout.FULL) {} }

/** The three layouts as one segmented row (Settings, a place's "Show more"): the current one picked, a tap changes it. */
@Composable
fun LayoutPicker(modifier: Modifier = Modifier) {
    val m = LocalLayout.current
    SingleChoiceSegmentedButtonRow(modifier) {
        Layout.entries.forEachIndexed { i, l ->
            SegmentedButton(m.layout == l, { if (m.layout != l) m.set(l) }, SegmentedButtonDefaults.itemShape(i, Layout.entries.size), icon = {},
                modifier = Modifier.semantics { contentDescription = "${l.title} layout. ${l.line}" }) {
                Text(l.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The small layout pill (Today's header, You): the layout's name; a tap opens the three to pick from. */
@Composable
fun LayoutPill(modifier: Modifier = Modifier) {
    val m = LocalLayout.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FilterChip(false, { open = true }, label = { Text(m.layout.title) },
            modifier = Modifier.semantics { contentDescription = "Layout: ${m.layout.title}. Tap to change." },
            trailingIcon = { Icon(painterResource(R.drawable.ic_expand_more), contentDescription = null, modifier = Modifier.size(18.dp)) })
        DropdownMenu(open, { open = false }, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Layout.entries.forEach { l ->
                val picked = l == m.layout
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(max = 280.dp).padding(vertical = 4.dp)) {
                            Text(l.title, style = MaterialTheme.typography.titleSmall,
                                color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(l.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    leadingIcon = {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            if (picked) Icon(painterResource(R.drawable.ic_check), contentDescription = "Picked", tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp))
                        }
                    },
                    onClick = { open = false; if (!picked) m.set(l) })
            }
        }
    }
}

/**
 * The quiet foot of a [place] its layout cuts: "Show more" ([label]) shows the place in full for this visit ([onShow]); once
 * it shows ([shown]), the three layouts to pick from for good. Draws nothing where the layout cuts nothing (Full, or Advanced
 * in Learn, Practice and Songs by chords).
 */
@Composable
fun LayoutMore(place: Simple.Place, shown: Boolean, onShow: () -> Unit, modifier: Modifier = Modifier, label: String = Simple.SHOW_MORE) {
    val m = LocalLayout.current
    if (!Simple.cuts(place, m.layout)) return
    Column(modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!shown) TextButton(onClick = onShow) { Text(label) }
        else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(Simple.SWITCH_QUESTION, style = MaterialTheme.typography.titleSmall)
            Text(Simple.SWITCH_LINE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LayoutPicker(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}
