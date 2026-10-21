package com.bigpapituna.sharpears.ui.brief

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.engine.brief.AskRules
import com.bigpapituna.sharpears.engine.brief.BarRow
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Spark
import com.bigpapituna.sharpears.engine.brief.TrendRow
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.reducedMotion

/**
 * One brief: the one-minute read (a headline, three facts, two or three things to try with their buttons, the praise), the
 * question box, and "See the full report" with the numbers behind it. [onSend] opens the export; [footer] is a slot under
 * everything (the weekly time on the Week tab).
 */
@Composable
fun BriefPage(
    page: BriefPageState, controller: BriefController, onSend: (Brief, Findings) -> Unit,
    modifier: Modifier = Modifier, footer: (@Composable () -> Unit)? = null,
) {
    val env = controller.env
    val name = page.profile.name
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (page.phase) {
            Phase.LOADING -> PupSays(Pose.THINKING, if (page.profile.kind == ProfileKind.KID) "Reading ${name}'s ${periodWord(page.period)}" else "Reading your ${periodWord(page.period)}",
                Modifier.padding(top = 24.dp).semantics { liveRegion = LiveRegionMode.Polite }, size = 88.dp)
            Phase.EMPTY -> Empty(page)
            Phase.READY -> {
                val brief = page.brief ?: return@Column
                val f = page.findings ?: return@Column
                Meta(page, brief, f, env, onRefresh = { controller.refresh(page) }, onSend = { onSend(brief, f) })
                Text(brief.headline, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    brief.facts.take(3).forEach { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Dot(Modifier.padding(top = 9.dp))
                        Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    } }
                }
                Recommendations(page, controller)
                Praise(brief.praise)
                AskBox(page.asks.exchanges, page.asks.busy, AskRules.starters(f), onAsk = { controller.ask(page, it) }, modifier = Modifier.padding(top = 10.dp))
                ExpandRow(page.expanded, onToggle = { page.expanded = !page.expanded })
                AnimatedVisibility(page.expanded, enter = if (reducedMotion()) fadeIn() else fadeIn() + expandVertically(), exit = if (reducedMotion()) fadeOut() else fadeOut() + shrinkVertically()) {
                    FullReport(brief, f, env)
                }
                footer?.invoke()
                Text(footerLine(page.profile.kind == ProfileKind.KID, name, brief.writtenBy),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))
            }
        }
    }
}

private fun periodWord(p: Period) = when (p) { Period.SESSION -> "session"; Period.DAY -> "day"; Period.WEEK -> "week" }

/** The line under a brief: where it was made and who sees it. An AI brief says what the AI was given (and what it never was). */
internal fun footerLine(kid: Boolean, name: String, by: Brief.Writer): String = when {
    by == Brief.Writer.AI && kid -> "Written from the numbers of ${name}'s playing. The AI never gets a name or a sound. Only you can see it here."
    by == Brief.Writer.AI -> "Written from the numbers of your playing. The AI never gets a name or a sound."
    kid -> "Made on this phone from ${name}'s playing. Only you can see it here."
    else -> "Made on this phone from your playing."
}

@Composable
private fun Empty(page: BriefPageState) {
    val kid = page.profile.kind == ProfileKind.KID
    val name = page.profile.name
    PupSays(Pose.RESTING, page.notice ?: when (page.period) {
        Period.SESSION -> if (kid) "No session to read yet. Once $name has played, a short note shows up here." else "No session to read yet. Once you have played, a short note shows up here."
        Period.DAY -> if (kid) "Nothing today yet. When $name plays, today's read is here." else "Nothing today yet. When you play, today's read is here."
        Period.WEEK -> if (kid) "A quiet week so far. When $name plays, the week fills in here." else "A quiet week so far. When you play, the week fills in here."
    }, Modifier.padding(top = 24.dp), size = 96.dp, idle = false)
}

/** The range, who wrote it and when, and the Refresh and Send pills. */
@Composable
private fun Meta(page: BriefPageState, brief: Brief, f: Findings, env: BriefEnv, onRefresh: () -> Unit, onSend: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(BriefFormat.rangeLabel(f, env.zone), style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
        Text(BriefFormat.writtenNote(brief, env.now(), env.zone), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        // The phone's brief is up while the AI writes its own; it takes its place when it comes (or this line just goes).
        if (page.improving) Text("The AI is writing its version. It takes this one's place when it is ready.", style = MaterialTheme.typography.bodySmall,
            color = c.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(if (page.refreshing) "Rewriting" else "Refresh", enabled = !page.refreshing, busy = page.refreshing, onClick = onRefresh)
            PillButton("Send", onClick = onSend)
        }
        page.notice?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.primary, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }
}

/** A secondary action: an outlined pill, at least 44 dp tall. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false, selected: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(modifier.heightIn(min = 44.dp).clip(CircleShape)
        .background(if (selected) c.primaryContainer else Color0)
        .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outline, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.primary)
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) (if (selected) c.onPrimaryContainer else c.onSurface) else c.onSurfaceVariant)
    }
}

private val Color0 = androidx.compose.ui.graphics.Color.Transparent

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp).semantics { heading() })

@Composable
private fun Recommendations(page: BriefPageState, controller: BriefController) {
    val recs = page.recommendations
    val gate = LocalParentGate.current
    if (recs.isEmpty()) return
    SectionLabel("Things to try")
    val actions = remember(page.profile.id, controller) { controller.actionsFor(page) }
    recs.forEachIndexed { i, r ->
        val view = remember(r.candidate) { actions.view(r.candidate) }
        RecommendationCard(heading = cardHeading(r.candidate), text = r.text, view = view, state = page.cards[i] ?: CardState.Idle,
            onPress = { gate.guard { controller.openSheet(page, i) } },
            onUndo = { controller.undo(page, i) }, onRetry = { controller.closeSheet(page, i) })
    }
    // The open sheet, if any.
    page.cards.entries.firstOrNull { it.value is CardState.Confirm }?.let { (i, st) ->
        val rec = recs.getOrNull(i) ?: return@let
        val view = actions.view(rec.candidate)
        val songs = remember(i) { if (view.needsSong) actions.songChoices(rec.candidate) else emptyList() }
        ActionSheet(view, st as CardState.Confirm, songs,
            onChange = { controller.update(page, i, it) },
            onConfirm = { input -> controller.perform(page, i, rec.candidate, input) },
            onDismiss = { controller.closeSheet(page, i) })
    }
}

/** The one thing to praise at home: the pup's gold behind it, the pup beside it. */
@Composable
private fun Praise(text: String) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PupGold.copy(alpha = 0.18f))
        .border(1.dp, PupGold.copy(alpha = 0.55f), RoundedCornerShape(20.dp)).padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("One thing to praise", style = MaterialTheme.typography.labelLarge, color = c.onSurface.copy(alpha = 0.75f), modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
        }
        Image(painterResource(R.drawable.pup_great_job), contentDescription = null, modifier = Modifier.size(72.dp))
    }
}

@Composable
private fun ExpandRow(expanded: Boolean, onToggle: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).border(1.dp, c.outline, RoundedCornerShape(26.dp))
        .clickable(role = Role.Button, onClick = onToggle).padding(horizontal = 20.dp)
        .semantics { stateDescription = if (expanded) "Open" else "Closed" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(if (expanded) "Hide the full report" else "See the full report", style = MaterialTheme.typography.titleSmall)
        Icon(painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more), contentDescription = null, tint = c.primary)
    }
}

/** The numbers behind the brief: everything the plan lists, one section each, only where there is something to show. */
@Composable
fun FullReport(brief: Brief, f: Findings, env: BriefEnv, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val s = f.stats
    Column(modifier.fillMaxWidth()) {
        if (brief.notes.isNotEmpty()) {
            ReportTitle("How it went")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { brief.notes.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        }
        if (brief.facts.size > 3) {
            ReportTitle("A little more")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { brief.facts.drop(3).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        }
        if (s.days.isNotEmpty()) { ReportTitle("The seven days"); WeekStrip(s.days, f.goalMinutes) }
        if (s.lessons.isNotEmpty()) {
            ReportTitle("Lessons")
            s.lessons.forEach { LessonRow(it, env.titles.lesson(it.lessonId)) }
        }
        if (s.songs.isNotEmpty()) {
            ReportTitle("Songs")
            s.songs.forEach { PlainRow(env.titles.song(it.songId), BriefFormat.songLine(it, env.titles).substringAfter(": ")) }
        }
        val rows = s.skills.filter { it.points.count { p -> p != null } >= 2 }
            .map { TrendRow(BriefFormat.skillLabel(it.kind), BriefFormat.skillSummary(it), it.points, BriefFormat.upIsGood(it.kind)) }
        if (rows.isNotEmpty()) {
            ReportTitle("Skills over four weeks")
            rows.forEach { TrendLine(it) }
        }
        val toys = s.toys.filter { it.minutes > 0 || it.opens > 0 }
        if (toys.isNotEmpty()) {
            ReportTitle("The toys")
            val shares = Spark.shares(toys.map { it.minutes })
            BarList(toys.mapIndexed { i, t -> BarRow(BriefFormat.toyLabel(t.toy), BriefFormat.minutes(t.minutes), shares[i]) })
        }
        val parts = s.timeOfDay.filter { it.minutes > 0 }
        if (parts.isNotEmpty()) {
            ReportTitle("Time of day")
            val shares = Spark.shares(parts.map { it.minutes })
            BarList(parts.mapIndexed { i, p -> BarRow(BriefFormat.partLabel(p.part), BriefFormat.minutes(p.minutes), shares[i]) }, parts.map { BriefFormat.partHours(it.part) })
        }
        ReportTitle("The room and the hints")
        Text(BriefFormat.noisyLine(s.noisyRoom), style = MaterialTheme.typography.bodyLarge)
        val hints = BriefFormat.hintLines(s.hints)
        if (hints.isEmpty()) Text("No hints were needed.", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
        else Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { hints.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) } }
        Text("Hints are how the app teaches: a glow, then a pointing hand, then the app plays the key.", style = MaterialTheme.typography.bodySmall,
            color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        brief.sections.forEach { sec ->
            ReportTitle(sec.title)
            Text(sec.body, style = MaterialTheme.typography.bodyLarge)
        }
        if (f.actions.isNotEmpty() || f.settings.isNotEmpty()) {
            ReportTitle("Since last time")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                f.actions.forEach { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Dot(Modifier.padding(top = 9.dp)); Text(BriefFormat.actionTaken(it, env.titles), style = MaterialTheme.typography.bodyLarge) } }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
