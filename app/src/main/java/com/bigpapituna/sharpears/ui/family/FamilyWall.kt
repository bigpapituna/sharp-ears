package com.bigpapituna.sharpears.ui.family

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.family.PawDay
import com.bigpapituna.sharpears.engine.family.PawWeek
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.Roster
import java.time.LocalDate

/**
 * One person on the family wall: who, this week's paws, and the latest sticker ([stickerRes], a drawable; null shows an
 * empty slot). The World team fills [stickerRes] through the `latestSticker` argument of [familyWallEntries].
 */
data class FamilyWallEntry(val profile: Profile, val week: List<PawDay>, val stickerRes: Int? = null, val current: Boolean = false)

/**
 * The wall's rows for [roster] on [today]: each profile's practice (ProgressStore.practiceLog of that profile's files)
 * turned into paws. [latestSticker] maps a profile id to its newest sticker drawable. Reads preferences: call it off the
 * draw path (remember it keyed on a version).
 */
fun familyWallEntries(context: Context, roster: Roster, today: LocalDate = LocalDate.now(),
                      latestSticker: (profileId: String) -> Int? = { null }): List<FamilyWallEntry> =
    roster.profiles.map { p ->
        val log = ProgressStore(context, Prefs(context, p.id)).practiceLog()
        FamilyWallEntry(p, PawWeek.of(log, today), latestSticker(p.id), current = p.id == roster.currentId)
    }

/**
 * The family wall (docs/KIDS-PLAN.md section 2): a home card with everyone's pup, name, seven paw prints for this week
 * (filled on days practised) and their latest sticker. Siblings see each other's week; that is most of the motivation.
 * Tapping someone switches to them ([onSwitch]); switching is free, the gate guards settings, not play. Hidden with
 * fewer than two people.
 */
@Composable
fun FamilyWall(entries: List<FamilyWallEntry>, onSwitch: (String) -> Unit, modifier: Modifier = Modifier) {
    if (entries.size < 2) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Family", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("  this week", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            entries.forEach { e -> Member(e) { onSwitch(e.profile.id) } }
        }
    }
}

@Composable
private fun Member(e: FamilyWallEntry, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val days = e.week.count { it.practised }
    val said = "${e.profile.name}${if (e.current) ", playing now" else ""}, $days ${if (days == 1) "day" else "days"} this week" +
        if (e.stickerRes != null) ", latest sticker shown" else ""
    Column(Modifier.width(128.dp).clip(shape)
        .background(if (e.current) c.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
        .border(if (e.current) 2.dp else 1.dp, if (e.current) c.primary else c.outlineVariant, shape)
        .clickable(enabled = !e.current, role = Role.Button, onClickLabel = "Play as ${e.profile.name}", onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = said }
        .padding(horizontal = 10.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box {
            PupAvatar(e.profile.avatar, size = 60.dp)
            StickerSlot(e.stickerRes, Modifier.align(Alignment.BottomEnd).offset(x = 10.dp, y = 4.dp))
        }
        Text(e.profile.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            e.week.forEach { d -> Paw(d, 13.dp) }
        }
    }
}

/** The latest sticker, or a dashed empty slot until there is one. */
@Composable
private fun StickerSlot(res: Int?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    if (res != null) Image(painterResource(res), contentDescription = null, modifier = modifier.size(30.dp))
    else Box(modifier.size(26.dp).clip(CircleShape).background(c.surface)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 1.5.dp.toPx()
            drawCircle(c.outline, radius = size.minDimension / 2 - w, style = Stroke(w,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        }
    }
}

/** One paw print: filled in the accent on a day practised, an outline on a blank day, faint for days still to come. */
@Composable
fun Paw(day: PawDay, size: Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val colour = when { day.practised -> c.primary; day.future -> c.outlineVariant; else -> c.outline }
    Canvas(modifier.size(size)) { drawPaw(colour, filled = day.practised) }
}

/** A paw: a round pad and four toes, drawn to fill the canvas. */
fun DrawScope.drawPaw(colour: Color, filled: Boolean) {
    val w = size.width
    val style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(w * 0.08f)
    drawOval(colour, Offset(w * 0.24f, w * 0.44f), Size(w * 0.52f, w * 0.44f), style = style)
    val toe = w * 0.11f
    listOf(0.16f to 0.40f, 0.36f to 0.20f, 0.64f to 0.20f, 0.84f to 0.40f).forEach { (x, y) ->
        drawCircle(colour, toe, Offset(w * x, w * y), style = style)
    }
}
