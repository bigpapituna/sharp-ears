package com.bigpapituna.sharpears.ui.family

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.analytics.testerMask
import com.bigpapituna.sharpears.data.AppClock
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.engine.family.ChildToday
import com.bigpapituna.sharpears.engine.family.FamilyToday
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.TodayPaw
import com.bigpapituna.sharpears.ui.drawRestPaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 2.14, Family today on a grown-up's Today (Priya's P8: "nothing about the kids on Today"): one row per child, the pup
 * avatar, the name, today's minutes playing and on screen, lessons and stars, and today's paw (played, a rest day, or
 * nothing yet). The numbers come from each child's event log through the one minutes ledger (engine/family/FamilyToday.kt),
 * so the row says what that child's brief says. A tap on a row opens the child's brief on Today. Read off the main thread
 * each time the card is shown ([refresh] reads it again). Hidden in tester replays (it names the children).
 */
@Composable
fun FamilyTodayCard(kids: List<Profile>, onOpen: (Profile) -> Unit, modifier: Modifier = Modifier, refresh: Int = 0) {
    if (kids.isEmpty()) return
    var rows by remember { mutableStateOf<Map<String, ChildToday>>(emptyMap()) }
    LaunchedEffect(kids.map { it.id }, refresh) {
        rows = withContext(Dispatchers.IO) {
            val now = AppClock.millis(); val today = AppClock.today()
            kids.associate { k -> k.id to (runCatching { FamilyToday.of(k.id, Briefs.read(k.id), today, now, Briefs.zone) }.getOrNull() ?: ChildToday(k.id)) }
        }
    }
    Column(modifier.fillMaxWidth().testerMask(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Family today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() })
        kids.forEach { k -> ChildRow(k, rows[k.id], onOpen) }
    }
}

@Composable
private fun ChildRow(kid: Profile, row: ChildToday?, onOpen: (Profile) -> Unit) {
    val c = MaterialTheme.colorScheme
    val line = row?.let(FamilyToday::line) ?: " "
    val paw = row?.let(FamilyToday::pawWords).orEmpty()
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp))
        .clickable(role = Role.Button, onClickLabel = "Open ${kid.name}'s brief") { onOpen(kid) }.padding(horizontal = 8.dp, vertical = 8.dp)
        .semantics(mergeDescendants = true) { contentDescription = "${kid.name}. $line $paw." },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        PupAvatar(kid.avatar, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kid.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // 2.15, the grown-up's Plain look: today played or not as a dot, no paw, rest-day moon or streak number.
        val plain = com.bigpapituna.sharpears.ui.LocalPlain.current.on
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { }) {
            if (plain) Canvas(Modifier.size(16.dp)) {
                if (row?.paw == TodayPaw.PLAYED) drawCircle(c.primary) else drawCircle(c.outline, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            } else Canvas(Modifier.size(28.dp)) {
                when (row?.paw) {
                    TodayPaw.PLAYED -> drawPaw(c.primary, filled = true)
                    TodayPaw.REST -> drawRestPaw(c.primary)
                    else -> drawPaw(c.outline, filled = false)
                }
            }
            if (!plain && row != null && row.streak >= 2) Text("${row.streak}", style = MaterialTheme.typography.labelSmall, color = c.primary)
        }
    }
}
