package com.bigpapituna.sharpears.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.nav.Tips

/**
 * A one-time tip (2.15, engine/nav/Tips.kt): a quiet card with a light bulb, one sentence, the tip's own button when it has one
 * ([onAction], "Open") and an X. Drawn with icons only, no pup, so it reads the same whether or not the pup is shown. The caller
 * has already marked the tip seen, so closing it (or never looking) is the end of it.
 */
@Composable
fun TipCard(tip: Tips.Tip, onClose: () -> Unit, modifier: Modifier = Modifier, onAction: (() -> Unit)? = null) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.surfaceContainerHigh).padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 2.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_lightbulb), contentDescription = "Tip", tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Text(tip.line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        if (tip.action != null && onAction != null) TextButton(onClick = onAction) { Text(tip.action) }
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = "Close tip", modifier = Modifier.size(20.dp))
        }
    }
}
