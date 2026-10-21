package com.bigpapituna.sharpears.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.data.SongMeta
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.progress.Achievement
import com.bigpapituna.sharpears.engine.progress.AchievementCategory
import com.bigpapituna.sharpears.engine.progress.AchievementIcon
import com.bigpapituna.sharpears.engine.progress.Achievements
import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.engine.progress.Snapshot
import com.bigpapituna.sharpears.engine.progress.WeeklyGoal
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.PupGold
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.Pup
import com.bigpapituna.sharpears.ui.PupSays
import java.text.NumberFormat
import java.time.LocalDate

/** Everything the screen shows, worked out once per change of the store or Prefs. */
private class ProfileData(val store: ProgressStore, val prefs: Prefs, val today: LocalDate) {
    val snap: Snapshot = store.snapshot(today)
    val log = store.practiceLog()
    val streak = store.streak(today)
    val secondsToday = log[today] ?: 0
    val xp = store.xp
    val level = Progression.levelFor(xp)
    val days30 = (29 downTo 0).map { today.minusDays(it.toLong()).let { d -> d to (log[d] ?: 0) } }
    val practicedDays = log.filterValues { it > 0 }.keys
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)

/**
 * The You tab: the pup and your level, today's goal and streak, your totals, a 30 day chart, a 12 week calendar,
 * achievements and a report card per song. The gear opens Settings.
 *
 * 2.1: [world] is the pup's world card (album, jar, dress-up, records behind it) and [paws] this week's paw calendar.
 * A grown-up finds them under today's panel; for a kid ([kid]) they come first: the album is a kid's You tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(store: ProgressStore, prefs: Prefs, songs: List<SongMeta>, onSettings: () -> Unit,
                  kid: Boolean = false, world: (@Composable () -> Unit)? = null, paws: (@Composable () -> Unit)? = null,
                  /** A grown-up's "My week" card (the parent brief, 2.4), under today's panel; null hides it. */
                  brief: (@Composable () -> Unit)? = null) {
    val today = remember { LocalDate.now() }
    val v = store.version.intValue + prefs.version.intValue
    val data = remember(v) { ProfileData(store, prefs, today) }
    var editGoal by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<Achievement?>(null) }
    val rows = remember { achievementRows() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (kid) {
            item(key = "bar") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) { TopBar(onSettings) } }
            world?.let { w -> item(key = "world") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 8.dp)) { w() } } }
            paws?.let { p -> item(key = "paws") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 8.dp)) { p() } } }
            item(key = "top") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp).padding(top = 12.dp)) { Hero(data) } }
        } else item(key = "top") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) { TopBar(onSettings); Hero(data) } }
        item(key = "today") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 20.dp)) { TodayPanel(data, onEditGoal = { editGoal = true }) } }
        if (!kid) brief?.let { b -> item(key = "brief") { Column(Modifier.widthIn(max = 720.dp).padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) { b() } } }
        if (!kid) world?.let { w -> item(key = "world") { Column(Modifier.widthIn(max = 720.dp).padding(start = 16.dp, end = 16.dp, bottom = 20.dp)) { w() } } }
        item(key = "stats") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) { StatsRow(data.snap) } }
        item(key = "bars") {
            Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                SectionTitle("Last 30 days", trailing = data.days30.maxOf { it.second }.takeIf { it > 0 }?.let { "Best day ${DailyGoal.minutes(it)} min" })
                PracticeBars(data.days30, store.goalMinutes)
                Text("The dashed line is your ${store.goalMinutes} minute goal.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item(key = "heat") {
            Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                SectionTitle("Last 12 weeks", trailing = com.bigpapituna.sharpears.engine.plural(data.practicedDays.count { !it.isBefore(WeeklyGoal.weekStart(today).minusWeeks(11)) }, "day"))
                Heatmap(data.log, today, store.goalMinutes, data.streak.frozenDays)
                HeatLegend(Modifier.padding(top = 8.dp))
            }
        }
        item(key = "ach-head") {
            Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) {
                SectionTitle("Achievements", trailing = "${store.unlocked.size} of ${Achievements.ALL.size}")
            }
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is AchRow.Header -> Text(row.category.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp).semantics { heading() })
                is AchRow.Tiles -> Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.items.forEach { a -> Box(Modifier.weight(1f)) { AchievementTile(a, store.unlocked[a.id], data.snap) { open = a } } }
                    repeat(3 - row.items.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        item(key = "cards") { Column(Modifier.widthIn(max = 720.dp).padding(horizontal = 20.dp)) { ReportCards(prefs, songs) } }
    }

    if (editGoal) GoalDialog(store) { editGoal = false }
    open?.let { a ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AchievementSheet(a, store.unlocked[a.id], data.snap) { open = null }
        }
    }
}

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("You", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(top = 8.dp).semantics { heading() })
        IconButton(onClick = onSettings) {
            Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The pup inside the level ring, the level and its title, and how far to the next. */
@Composable
private fun Hero(d: ProfileData) {
    val pose = when {
        DailyGoal.met(d.secondsToday, d.store.goalMinutes) -> Pose.GREAT_JOB
        d.streak.streak >= 3 -> Pose.STREAK
        d.streak.atRisk -> Pose.GET_READY
        d.level >= 10 -> Pose.PRACTICING
        // Dressed up (2.1): the base pose wears the outfit, so the pup shows it off when nothing else applies.
        com.bigpapituna.sharpears.ui.world.LocalPupOutfit.current != com.bigpapituna.sharpears.engine.world.Outfit.NONE -> Pose.WEAR_BASE
        else -> Pose.WELCOME
    }
    val level = rememberCountUp(d.level, 700)
    val xp = rememberCountUp(d.xp, 1100)
    val c = MaterialTheme.colorScheme
    val toNext = Progression.xpToNext(d.xp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(212.dp), contentAlignment = Alignment.Center) {
            ProgressRing(Progression.fractionToNext(d.xp), Modifier.size(200.dp), stroke = 10.dp) { Pup(pose, size = 132.dp) }
            // The level sits on the ring's lower edge.
            Text("Level $level", style = MaterialTheme.typography.titleMedium.mono(), color = c.onPrimary,
                modifier = Modifier.align(Alignment.BottomCenter).background(c.primary, CircleShape).padding(horizontal = 16.dp, vertical = 5.dp)
                    .semantics { contentDescription = "Level ${d.level}" })
        }
        Text(Progression.title(d.level), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            if (toNext > 0) "${count(xp)} XP, ${count(toNext)} to level ${d.level + 1}" else "${count(xp)} XP, the top level",
            style = MaterialTheme.typography.bodyMedium.mono(), color = c.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "${d.xp} experience points, $toNext to the next level" },
        )
    }
}

/** Today's goal ring, the streak with its freezes, and this week's days: the one raised surface of this section. */
@Composable
private fun TodayPanel(d: ProfileData, onEditGoal: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val goal = d.store.goalMinutes
    val minutes = d.secondsToday / 60
    val met = DailyGoal.met(d.secondsToday, goal)
    val shown = rememberCountUp(minutes, 800)
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(c.surfaceContainerHigh).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProgressRing(DailyGoal.fraction(d.secondsToday, goal), Modifier.size(104.dp), stroke = 9.dp, track = c.outlineVariant) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$shown", style = MaterialTheme.typography.headlineMedium.mono(), color = c.onSurface)
                    Text("of $goal min", style = MaterialTheme.typography.labelSmall.mono(), color = c.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Today", style = MaterialTheme.typography.labelLarge, color = c.primary)
                Text(if (met) "Goal met" else "${(goal * 60 - d.secondsToday + 59) / 60} min to go", style = MaterialTheme.typography.titleLarge)
                Text(if (met) "Nice work. Every extra minute still counts." else if (d.secondsToday > 0) "Open a song or a lesson to keep going." else "Open a song or a lesson to start.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                TextButton(onClick = onEditGoal, contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.semantics { contentDescription = "Change your daily goal, now $goal minutes" }) { Text("Change goal") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Flame(color = if (d.streak.streak > 0) c.primary else c.outline, size = 40.dp)
            Column(Modifier.weight(1f)) {
                val n = rememberCountUp(d.streak.streak, 800)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$n", style = MaterialTheme.typography.headlineMedium.mono())
                    Text("day streak", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    when {
                        d.streak.streak == 0 -> "Practise today to start one."
                        d.streak.atRisk -> "Practise today to keep it."
                        else -> "Kept for today."
                    },
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            }
            Freezes(d.streak.freezes)
        }
        WeekDots(d)
    }
}

/** Two snowflake slots: filled for each streak freeze held. */
@Composable
private fun Freezes(held: Int) {
    val c = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics { contentDescription = "$held of 2 streak freezes held. A freeze covers a missed day." }) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { Snowflake(color = if (it < held) c.primary else c.outline.copy(alpha = 0.5f), size = 22.dp) }
        }
        Text("Freezes", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}

/** Monday to Sunday of this week: a dot for each day practised, with the weekly goal beside it. */
@Composable
private fun WeekDots(d: ProfileData) {
    val c = MaterialTheme.colorScheme
    val start = WeeklyGoal.weekStart(d.today)
    val done = WeeklyGoal.daysThisWeek(d.practicedDays, d.today)
    val goal = d.store.weeklyDays
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("This week", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$done of $goal days", style = MaterialTheme.typography.labelMedium.mono(), color = if (done >= goal) c.primary else c.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0 until 7) {
                val day = start.plusDays(i.toLong())
                val did = day in d.practicedDays
                val future = day.isAfter(d.today)
                // One node per day ("Tuesday, practised"): TalkBack read only the letters, never which days were done.
                Column(Modifier.clearAndSetSemantics {
                    contentDescription = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()) +
                        when { did -> ", practised"; future -> ""; else -> ", not practised" }
                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(26.dp).clip(CircleShape)
                        .background(if (did) c.primary else c.surfaceContainerHighest)
                        .then(if (day == d.today && !did) Modifier.border(2.dp, c.primary, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center) {
                        if (did) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onPrimary, modifier = Modifier.size(16.dp))
                    }
                    Text("MTWTFSS"[i].toString(), style = MaterialTheme.typography.labelSmall,
                        color = if (future) c.onSurfaceVariant.copy(alpha = 0.5f) else c.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(s: Snapshot) {
    val minutes = rememberCountUp(s.totalMinutes)
    val songs = rememberCountUp(s.songsCompleted)
    val lessons = rememberCountUp(s.lessonsDone)
    val best = rememberCountUp(s.bestPerform.coerceAtLeast(0))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stat("$minutes", "Minutes", Modifier.weight(1f))
        Stat("$songs", "Songs played", Modifier.weight(1f))
        Stat("$lessons", "Lessons", Modifier.weight(1f))
        Stat(if (s.bestPerform < 0) "None" else "$best%", "Best score", Modifier.weight(1f))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.mono(), maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium.mono(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp)) }
    }
}

// ---- achievements ----

private sealed interface AchRow {
    val key: String
    data class Header(val category: AchievementCategory) : AchRow { override val key = "h-${category.name}" }
    data class Tiles(val items: List<Achievement>) : AchRow { override val key = "t-${items.first().id}" }
}

private fun achievementRows(): List<AchRow> = Achievements.byCategory().flatMap { (cat, list) ->
    listOf<AchRow>(AchRow.Header(cat)) + list.chunked(3).map { AchRow.Tiles(it) }
}

/** One achievement: coloured with its date when earned; greyed with a lock and what it needs when not. */
@Composable
private fun AchievementTile(a: Achievement, on: LocalDate?, snap: Snapshot, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val got = on != null
    val label = if (got) "${a.title}, unlocked ${shortDate(on!!)}" else "${a.title}, locked. ${a.hint}"
    Column(
        Modifier.fillMaxWidth().heightIn(min = 160.dp).clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp, vertical = 10.dp)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AchievementBadge(a.icon, got, 64.dp)
        Text(a.title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (got) c.onSurface else c.onSurfaceVariant)
        if (got) Text(shortDate(on!!), style = MaterialTheme.typography.labelSmall.mono(), color = c.onSurfaceVariant)
        else Text(a.hint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            color = c.onSurfaceVariant.copy(alpha = 0.85f))
    }
}

private val GREY = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private fun drawableFor(name: String) = when (name) {
    "ic_piano" -> R.drawable.ic_piano
    "ic_ear" -> R.drawable.ic_ear
    "ic_school" -> R.drawable.ic_school
    "ic_flag" -> R.drawable.ic_flag
    "ic_library_music" -> R.drawable.ic_library_music
    "ic_mic" -> R.drawable.ic_mic
    "ic_repeat" -> R.drawable.ic_repeat
    "ic_speed" -> R.drawable.ic_speed
    else -> R.drawable.ic_check
}

/** The round picture of an achievement: the pup on a soft gold glow, or an icon on the accent tint. Greyed with a lock when [got] is false. */
@Composable
fun AchievementBadge(icon: AchievementIcon, got: Boolean, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Box(modifier.size(size)) {
        val bg = when { !got -> c.surfaceContainerHighest; icon.isPose -> PupGold.copy(alpha = 0.28f); else -> c.primaryContainer }
        Box(Modifier.fillMaxSize().clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
            if (icon.isPose) {
                val pose = runCatching { Pose.valueOf(icon.name) }.getOrDefault(Pose.WELCOME)
                Image(painterResource(pose.res), contentDescription = null, colorFilter = if (got) null else GREY,
                    modifier = Modifier.size(size * 0.78f).graphicsLayer { alpha = if (got) 1f else 0.35f })
            } else {
                Icon(painterResource(drawableFor(icon.name)), contentDescription = null, modifier = Modifier.size(size * 0.5f),
                    tint = if (got) c.onPrimaryContainer else c.onSurfaceVariant.copy(alpha = 0.45f))
            }
        }
        if (!got) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.36f).clip(CircleShape).background(c.surfaceContainerHigh).border(1.dp, c.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_profile_lock), contentDescription = null, tint = c.onSurfaceVariant, modifier = Modifier.size(size * 0.22f))
        }
    }
}

@Composable
private fun AchievementSheet(a: Achievement, on: LocalDate?, snap: Snapshot, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val got = on != null
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 28.dp).padding(bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AchievementBadge(a.icon, got, 112.dp)
        Text(a.category.label, style = MaterialTheme.typography.labelLarge, color = c.primary, modifier = Modifier.padding(top = 8.dp))
        Text(a.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(if (got) a.description else a.hint, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        if (got) Text("Unlocked ${shortDate(on!!)}", style = MaterialTheme.typography.labelLarge.mono(), color = c.onSurface)
        else if (a.target > 1) {
            val have = a.measure(snap).coerceAtMost(a.target)
            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(c.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(a.fraction(snap)).height(8.dp).clip(CircleShape).background(c.primary))
            }
            Text("${count(have)} of ${count(a.target)}", style = MaterialTheme.typography.labelLarge.mono(), color = c.onSurfaceVariant)
        }
        TextButton(onClick = onClose) { Text("Done") }
    }
}

// ---- song report cards ----

@Composable
private fun ReportCards(prefs: Prefs, songs: List<SongMeta>) {
    val c = MaterialTheme.colorScheme
    val cards = remember(prefs.version.intValue, songs) {
        songs.map { Triple(it, prefs.progress(it.id), prefs.handsDone(it.id)) }
            .filter { (_, p, h) -> p.completed || p.bestAccuracy >= 0 || h.isNotEmpty() }
            .sortedWith(compareByDescending<Triple<SongMeta, com.bigpapituna.sharpears.data.SongProgress, Set<Practice>>> { it.second.bestAccuracy }.thenBy { it.first.title })
    }
    SectionTitle("Song report cards", trailing = if (cards.isEmpty()) null else "${cards.size}")
    if (cards.isEmpty()) {
        PupSays(Pose.LEARNING, "Play a song to the end and its report card shows up here.", Modifier.fillMaxWidth(), size = 80.dp)
        return
    }
    cards.forEach { (meta, p, hands) ->
        val status = when { p.bestAccuracy >= 0 -> "Best run ${p.bestAccuracy}%"; p.completed -> "Played through"; else -> "In progress" }
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {
            contentDescription = "${meta.title}. $status. Hands done: " +
                (listOf(Practice.RIGHT to "right", Practice.LEFT to "left", Practice.BOTH to "both").filter { it.first in hands }.joinToString { it.second }.ifEmpty { "none" })
        }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meta.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta.composer, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.bestAccuracy >= 0) Text("${p.bestAccuracy}%", style = MaterialTheme.typography.titleLarge.mono(), color = c.primary)
                else if (p.completed) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.primary, modifier = Modifier.size(22.dp))
            }
            if (p.bestAccuracy >= 0) Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth(p.bestAccuracy / 100f).height(4.dp).clip(CircleShape).background(c.primary))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                HandChip("Right", Practice.RIGHT in hands)
                HandChip("Left", Practice.LEFT in hands)
                HandChip("Both", Practice.BOTH in hands)
                Spacer(Modifier.weight(1f))
                Text(status, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HandChip(label: String, done: Boolean) {
    val c = MaterialTheme.colorScheme
    Text(label, style = MaterialTheme.typography.labelSmall, color = if (done) c.onPrimaryContainer else c.onSurfaceVariant.copy(alpha = 0.8f),
        modifier = Modifier.clip(CircleShape)
            .then(if (done) Modifier.background(c.primaryContainer) else Modifier.border(1.dp, c.outlineVariant, CircleShape))
            .padding(horizontal = 10.dp, vertical = 4.dp))
}

// ---- editing the goal ----

@Composable
private fun GoalDialog(store: ProgressStore, onClose: () -> Unit) {
    var daily by remember { mutableStateOf(store.goalMinutes) }
    var weekly by remember { mutableStateOf(store.weeklyDays) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Your goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Minutes a day", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    DailyGoal.OPTIONS.forEachIndexed { i, m ->
                        SegmentedButton(daily == m, { daily = m }, SegmentedButtonDefaults.itemShape(i, DailyGoal.OPTIONS.size), icon = {}) { Text("$m") }
                    }
                }
                Text(DailyGoal.note(daily), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Days a week", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WeeklyGoal.OPTIONS.forEachIndexed { i, d ->
                        SegmentedButton(weekly == d, { weekly = d }, SegmentedButtonDefaults.itemShape(i, WeeklyGoal.OPTIONS.size), icon = {}) { Text("$d") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { store.goalMinutes = daily; store.weeklyDays = weekly; onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
