package com.bigpapituna.sharpears.ui.play

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.PlayStore
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.play.Box as CardBox
import com.bigpapituna.sharpears.engine.play.ConcertCardLayout
import com.bigpapituna.sharpears.engine.play.Quest
import com.bigpapituna.sharpears.engine.play.QuestEvent
import com.bigpapituna.sharpears.engine.play.QuestKind
import com.bigpapituna.sharpears.engine.play.QuestTask
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.SharpEarsTheme
import com.bigpapituna.sharpears.ui.family.LocalProfile
import com.bigpapituna.sharpears.ui.reducedMotion
import java.time.LocalDate

/**
 * Pup's quest on the home screen: today's three tasks, each with its own star. [plan] is today's "Practice today"
 * plan (grown-ups' tasks come from it; kids' do not need it). Tapping a task calls [onTask] so the integrator can open
 * the place it is done (see ui/play/INTEGRATION.md). The quest is made the first time the card shows on a day.
 */
@Composable
fun QuestCard(plan: SessionPlan?, onTask: (QuestTask) -> Unit, modifier: Modifier = Modifier) {
    val profile = LocalProfile.current
    val context = LocalContext.current
    val quest = remember(profile.id, profile.isKid, plan, LocalDate.now()) { PlayStore(context, profile.id).quest(profile.isKid, plan) }
    QuestCardContent(quest, profile.isKid, onTask, modifier)
}

@Composable
internal fun QuestCardContent(quest: Quest, kid: Boolean, onTask: (QuestTask) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PupPicture(if (quest.allDone) R.drawable.pup_treat else R.drawable.pup_pointing, 44.dp)
            Column {
                Text("Pup's quest", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text(questLine(quest, kid), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        quest.tasks.forEach { t -> TaskRow(t, kid) { onTask(t) } }
    }
}

/** The line under the heading: how the day's quest is going, never what is missing. */
internal fun questLine(q: Quest, kid: Boolean): String = when {
    q.allDone -> if (kid) "All three stars! The pup is so proud." else "All three done today. Nicely played."
    q.stars == 0 -> if (kid) "Three little jobs, three stars." else "Three small things for today, a star each."
    else -> if (kid) "Keep going, more stars to find!" else "${q.stars} of ${Quest.SIZE} done."
}

@Composable
private fun TaskRow(t: QuestTask, kid: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    val progress = when {
        t.complete -> "done"
        t.done == 0 || t.target == 1 -> ""
        kid -> ""                                              // kids see dots, not "1 of 3"
        else -> "${t.done} of ${t.target}"
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
        .border(1.dp, if (t.complete) PupGold else c.outlineVariant, shape)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = t.title + if (t.complete) ", star earned" else if (t.done > 0) ", ${t.done} of ${t.target} done" else "" }
        .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        QuestStar(t.complete)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t.title, style = MaterialTheme.typography.bodyLarge)
            if (!t.complete && t.target > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(t.target.coerceAtMost(8)) { i ->
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < t.done) c.primary else c.surfaceContainerHighest))
                }
            }
        }
        if (progress.isNotEmpty()) Text(progress, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
    }
}

/** A task's star: gold when earned (it pops in once), a pale outline until then. */
@Composable
private fun QuestStar(earned: Boolean) {
    val still = reducedMotion()
    val pop = remember { Animatable(1f) }
    LaunchedEffect(earned) { if (earned && !still) { pop.snapTo(0.6f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f)) } }
    val outline = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(30.dp).scale(pop.value)) {
        val pts = ConcertCardLayout.starPoints(CardBox(0f, 0f, size.width, size.height))
        val path = Path().apply { pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }; close() }
        if (earned) drawPath(path, PupGold) else drawPath(path, outline, style = Stroke(2.dp.toPx()))
    }
}

// ---- previews ----

private fun previewQuest(kid: Boolean) = Quest.forDay(LocalDate.of(2026, 10, 18), kid, null).apply {
    record(QuestEvent.SongFinished("ode")); record(QuestEvent.SongFinished("mary"))
    record(QuestEvent.PupCopied(3))
    record(QuestEvent.NotePlayed(60)); record(QuestEvent.NotePlayed(72))
}

@Preview(name = "Quest card, kid", widthDp = 380)
@Composable
private fun QuestCardKidPreview() = SharpEarsTheme(dark = false) {
    Surface { QuestCardContent(previewQuest(kid = true), kid = true, onTask = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(name = "Quest card, grown-up, dark", widthDp = 380)
@Composable
private fun QuestCardAdultPreview() = SharpEarsTheme(dark = true) {
    Surface { QuestCardContent(previewQuest(kid = false), kid = false, onTask = {}, modifier = Modifier.padding(16.dp)) }
}

/** Which kinds of task a tap can open (for the integrator's routing; see INTEGRATION.md). */
val QuestTask.opens: String get() = when (kind) {
    QuestKind.PLAY_SONGS, QuestKind.SONG, QuestKind.TROUBLE_BAR -> "songs"
    QuestKind.COPY_PUP -> "pupsays"
    QuestKind.FIND_CS -> "jam"
    QuestKind.DUE_CHORDS -> "chords"
    QuestKind.LESSON -> "learn"
}
