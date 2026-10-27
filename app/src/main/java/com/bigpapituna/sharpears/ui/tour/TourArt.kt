package com.bigpapituna.sharpears.ui.tour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.BarVerdict
import com.bigpapituna.sharpears.engine.nav.Welcome.TourArt
import com.bigpapituna.sharpears.ui.Ink
import com.bigpapituna.sharpears.ui.InkDim
import com.bigpapituna.sharpears.ui.Numeric
import com.bigpapituna.sharpears.ui.Stage
import com.bigpapituna.sharpears.ui.StageRaised
import com.bigpapituna.sharpears.ui.drawRestPaw
import com.bigpapituna.sharpears.ui.family.drawPaw
import com.bigpapituna.sharpears.ui.player.verdictColour

/**
 * The little picture on a tour card (2.15): a small stand-in for the real screen, built from the app's own colours, icons and
 * the strip's verdict colours, so it looks like what the person will meet. No pup and no photo; a description for TalkBack.
 * [TourArt.PLAN] to [TourArt.STREAK] are the grown-up tour's, [TourArt.FAMILY] to [TourArt.GATE] the "For grown-ups" tour's.
 */
@Composable
fun TourPicture(art: TourArt, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(PICTURE_HEIGHT).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .padding(12.dp).clearAndSetSemantics { contentDescription = describe(art) }, contentAlignment = Alignment.Center) {
        when (art) {
            TourArt.PLAN -> PlanPicture()
            TourArt.BAND -> BandPicture()
            TourArt.TIMING -> TimingPicture()
            TourArt.LOOP -> LoopPicture()
            TourArt.STREAK -> StreakPicture()
            TourArt.FAMILY -> FamilyPicture()
            TourArt.LIMIT -> LimitPicture()
            TourArt.GATE -> GatePicture()
        }
    }
}

val PICTURE_HEIGHT = 192.dp

/** What each picture shows, for TalkBack (the card's words say the rest). */
fun describe(art: TourArt): String = when (art) {
    TourArt.PLAN -> "Picture: the Today card with chords, a lesson and a song, and a Start button."
    TourArt.BAND -> "Picture: a song's chords in blocks, with a drum and a bass part."
    TourArt.TIMING -> "Picture: a strip of bars coloured clean, rushed or dragged."
    TourArt.LOOP -> "Picture: two hard bars with Loop buttons."
    TourArt.STREAK -> "Picture: a week of paw prints, one of them a rest day."
    TourArt.FAMILY -> "Picture: Family today with a row for each child."
    TourArt.LIMIT -> "Picture: a daily time used up, and a goodbye until tomorrow."
    TourArt.GATE -> "Picture: a maths question to open the grown-ups' corner, and a Lock now button."
}

@Composable
private fun PlanPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your plan", style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.weight(1f))
            Text("About 10 min", style = Numeric.merge(MaterialTheme.typography.labelSmall), color = c.onSurfaceVariant)
        }
        PlanRow(R.drawable.ic_piano, "Chords", "Four to warm up")
        PlanRow(R.drawable.ic_school, "Lesson", "Your next one")
        PlanRow(R.drawable.ic_music_note, "Song", "A song by chords")
        Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp)).background(c.primary), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(painterResource(R.drawable.ic_play_arrow), null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
                Text("Start", style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
            }
        }
    }
}

@Composable
private fun PlanRow(icon: Int, title: String, detail: String) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = c.primary, modifier = Modifier.size(15.dp))
        }
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BandPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Stage).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Verse", style = MaterialTheme.typography.labelSmall, color = InkDim)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("C", "G", "Am", "F").forEachIndexed { i, name ->
                Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(10.dp)).background(if (i == 0) c.primary else StageRaised), contentAlignment = Alignment.Center) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = if (i == 0) c.onPrimary else Ink)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_music_note), null, tint = InkDim, modifier = Modifier.size(16.dp))
            Text("Band", style = MaterialTheme.typography.labelMedium, color = InkDim)
            listOf("Drums", "Bass").forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = Ink, modifier = Modifier.clip(RoundedCornerShape(50)).background(StageRaised).padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
    }
}

/** The recap's strip in a few sample bars: clean, one rushed, one dragged, one messy. */
private val SAMPLE_BARS = listOf(BarVerdict.CLEAN, BarVerdict.CLEAN, BarVerdict.RUSHED, BarVerdict.CLEAN, BarVerdict.DRAGGED, BarVerdict.CLEAN, BarVerdict.MESSY, BarVerdict.CLEAN)

@Composable
private fun BarStrip(marked: Set<Int> = emptySet()) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SAMPLE_BARS.forEachIndexed { i, v ->
            val bar = i + 1
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(verdictColour(v).copy(alpha = 0.85f))
                .then(if (bar in marked) Modifier.border(2.dp, Ink, RoundedCornerShape(6.dp)) else Modifier), contentAlignment = Alignment.Center) {
                Text("$bar", style = Numeric.merge(MaterialTheme.typography.labelSmall).copy(fontSize = 10.sp), color = Stage)
            }
        }
    }
}

@Composable
private fun TimingPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Stage).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BarStrip(marked = setOf(3))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BarVerdict.entries.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(verdictColour(v), CircleShape))
                        Text(v.word, style = MaterialTheme.typography.labelSmall, color = InkDim)
                    }
                }
            }
        }
        Text("You rush the first beat of bar 3.", style = MaterialTheme.typography.bodyMedium, color = c.primary)
        Text("Beat 1: 120 ms early", style = Numeric.merge(MaterialTheme.typography.bodySmall), color = c.onSurfaceVariant)
    }
}

@Composable
private fun LoopPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.clip(RoundedCornerShape(12.dp)).background(Stage).padding(10.dp)) { BarStrip(marked = setOf(3, 7)) }
        Text("Hardest bars: 3, 7", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Loop bar 3", "Loop bar 7").forEach { Pill(it) }
        }
    }
}

@Composable
private fun Pill(label: String) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(50)).border(1.dp, c.outline, RoundedCornerShape(50)).padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(painterResource(R.drawable.ic_repeat), null, tint = c.primary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StreakPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            // Monday to Sunday: five days played, the second a rest day, today still open.
            val days = listOf("M" to Paw.PLAYED, "T" to Paw.PLAYED, "W" to Paw.REST, "T" to Paw.PLAYED, "F" to Paw.PLAYED, "S" to Paw.PLAYED, "S" to Paw.TODAY)
            days.forEach { (d, p) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Canvas(Modifier.size(30.dp)) {
                        when (p) {
                            Paw.PLAYED -> drawPaw(c.primary, filled = true)
                            Paw.REST -> drawRestPaw(c.primary)
                            Paw.TODAY -> drawPaw(c.outline, filled = false)
                        }
                    }
                    Text(d, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                }
            }
        }
        Text("5 days in a row", style = MaterialTheme.typography.titleMedium, color = c.primary)
        Text("Wednesday was a rest day.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

private enum class Paw { PLAYED, REST, TODAY }

@Composable
private fun FamilyPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Family today", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        listOf(Triple("Mia", "14 min playing of 22 min on screen. 2 lessons, 5 stars.", true), Triple("Theo", "Nothing yet today.", false)).forEach { (name, line, played) ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHigh).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(name.take(1), style = MaterialTheme.typography.titleSmall, color = c.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(line, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Canvas(Modifier.size(24.dp)) { if (played) drawPaw(c.primary, filled = true) else drawPaw(c.outline, filled = false) }
            }
        }
    }
}

@Composable
private fun LimitPicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Daily time", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("15 min", style = Numeric.merge(MaterialTheme.typography.titleSmall), color = c.primary)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth().height(8.dp).background(c.primary))
        }
        Spacer(Modifier.height(2.dp))
        Text("That's all for today, Mia. See you tomorrow!", style = MaterialTheme.typography.titleSmall)
        Text("Lessons, songs and games wait for tomorrow.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
    }
}

@Composable
private fun GatePicture() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHigh).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("For grown-ups", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            Text("23 plus 48", style = Numeric.merge(MaterialTheme.typography.headlineSmall))
            Box(Modifier.width(96.dp).height(34.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.outline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("71", style = Numeric.merge(MaterialTheme.typography.titleMedium))
            }
        }
        Row(Modifier.clip(RoundedCornerShape(50)).background(c.primary).padding(start = 12.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(painterResource(R.drawable.ic_profile_lock), null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
            Text("Lock now", style = MaterialTheme.typography.labelLarge, color = c.onPrimary)
        }
    }
}
