package com.bigpapituna.sharpears.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.engine.family.HomeFirst
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.PlacementCheck
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.reminder.Reminder
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Stat
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.profile.mono
import com.bigpapituna.sharpears.ui.reducedMotion

private const val STEPS = 4

/**
 * First run, four short steps with the pup talking: welcome, have you played before, a daily goal, then a reminder and
 * how you will play. It ends with a celebration. Answers go to [store] (experience, goal, input choice, onboardingDone);
 * the reminder and microphone choices are set on [prefs] through its setters, and [onReminderChanged] lets the app set
 * the alarm. [onDone] gets the lesson to open first (null to just look around). Skip leaves everything at its default.
 * [askReminder] is false for profiles other than the phone owner's: the reminder is one alarm per phone, the owner's.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(store: ProgressStore, prefs: Prefs, onReminderChanged: () -> Unit = {}, askReminder: Boolean = true,
                     /** The level they said (2.6, engine/family/Placement.kt), once the questions are answered: the profile keeps it and the course is placed. */
                     onLevel: (Level) -> Unit = {},
                     /** Offered after the questions to anyone above "never": the short listen ("Let's hear you play", ui/family/PlacementCheckScreen.kt). Null: not offered. */
                     onCheck: ((Level) -> Unit)? = null,
                     onDone: (startLessonId: String?) -> Unit) {
    val context = LocalContext.current
    val still = reducedMotion()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var experience by rememberSaveable { mutableIntStateOf(-1) }
    var goal by rememberSaveable { mutableIntStateOf(store.goalMinutes.takeIf { it in DailyGoal.OPTIONS } ?: DailyGoal.DEFAULT) }
    var reminderOn by rememberSaveable { mutableStateOf(askReminder) }
    var reminderMinutes by rememberSaveable { mutableIntStateOf(prefs.reminderMinutes) }
    var useMic by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    fun finish(notifyOk: Boolean) {
        Level.entries.getOrNull(experience)?.let(onLevel)
        store.goalMinutes = goal
        store.useMic = useMic
        prefs.micDefault = useMic
        if (askReminder) {
            prefs.reminderMinutes = reminderMinutes
            prefs.reminderOn = reminderOn && notifyOk
            onReminderChanged()
        }
        store.onboardingDone = true
        finished = true
    }
    // "Microphone" picked: the microphone is asked for here, so the first lesson already hears the piano. It was not
    // asked at all: the lesson ran on the screen's keys and said nothing while the player played the real piano.
    var notifyOk by rememberSaveable { mutableStateOf(true) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        android.util.Log.d(com.bigpapituna.sharpears.TAG, "onboarding: microphone granted=$granted")
        finish(notifyOk)
    }
    fun afterNotify(ok: Boolean) {
        notifyOk = ok
        if (useMic && context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            askMic.launch(android.Manifest.permission.RECORD_AUDIO)
        else finish(ok)
    }
    // Android 13 and up ask before an app may post notifications; asked here, when the reminder is chosen.
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> afterNotify(granted) }

    if (finished) {
        val level = Level.entries.getOrNull(experience) ?: Level.NEVER
        val plan = Placement.planFor(level, ProfileKind.ADULT)
        val unit = COURSE.firstOrNull { it.id == plan.startUnitId }
        val lesson = unit?.lessons?.firstOrNull { it.id == plan.startLessonId }
        // Someone above "never" is asked to play a little (never someone who has not played): two or three tiny things, skippable.
        val check = onCheck?.takeIf { PlacementCheck.offered(level) }
        val songsFirst = plan.homeFirst == HomeFirst.SONGS
        val start = { onDone(if (songsFirst) null else lesson?.id) }
        val firstLine = if (songsFirst) "The songs are ready for you." else lesson?.let { "Your first lesson is ready: ${it.title}." } ?: "Your first lesson is ready."
        Celebration(
            title = "You're all set",
            line = if (level != Level.NEVER) "${plan.line} $firstLine" else firstLine,
            stats = listOf(Stat("$goal min", "Daily goal"), Stat(unit?.title ?: "Basics", "Start at")),
            primary = if (check != null) "Let's hear you play" else if (songsFirst) "Show me the songs" else "Start learning",
            onPrimary = { if (check != null) check(level) else start() },
            secondary = if (check != null) (if (songsFirst) "Skip, show me the songs" else "Skip, start learning") else "Look around first",
            onSecondary = { if (check != null) start() else onDone(null) },
            pose = Pose.LEVEL_UP,
        )
        return
    }

    BackHandler(enabled = step > 0) { step-- }
    val canContinue = step != 1 || experience >= 0
    Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).padding(horizontal = 24.dp)) {
            // Where we are, and a way out.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).semantics { contentDescription = "Step ${step + 1} of $STEPS" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(STEPS) { i ->
                        val k by animateFloatAsState(if (i <= step) 1f else 0f, tween(if (still) 0 else 300), label = "dot")
                        Box(Modifier.height(6.dp).weight(1f).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant)) {
                            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = k)))
                        }
                    }
                }
                TextButton(onClick = { store.onboardingDone = true; onDone(null) }) { Text("Skip") }
            }
            AnimatedContent(
                step, Modifier.weight(1f),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    if (still) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    else (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { dir * it / 4 } + fadeIn(tween(340))) togetherWith
                        (slideOutHorizontally(tween(260, easing = FastOutSlowInEasing)) { -dir * it / 4 } + fadeOut(tween(180)))
                },
                label = "step",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    when (s) {
                        0 -> WelcomeStep()
                        1 -> ExperienceStep(experience) { experience = it }
                        2 -> GoalStep(goal) { goal = it }
                        else -> HabitStep(
                            askReminder, reminderOn, reminderMinutes, useMic,
                            onReminder = { reminderOn = it }, onPickTime = { pickTime = true }, onMic = { useMic = it },
                        )
                    }
                }
            }
            ChunkyButton(
                onClick = {
                    if (step < STEPS - 1) step++
                    else if (reminderOn && !Reminder.canNotify(context)) askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else afterNotify(true)
                },
                enabled = canContinue, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
            ) { Text(if (step == STEPS - 1) "Finish" else "Continue") }
        }
    }

    if (pickTime) {
        val state = androidx.compose.material3.rememberTimePickerState(reminderMinutes / 60, reminderMinutes % 60,
            is24Hour = android.text.format.DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Reminder time") },
            text = { androidx.compose.material3.TimePicker(state, colors = androidx.compose.material3.TimePickerDefaults.colors(
                periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer)) },
            confirmButton = { TextButton(onClick = { reminderMinutes = state.hour * 60 + state.minute; pickTime = false }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StepTitle(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

@Composable
private fun WelcomeStep() {
    PupSays(Pose.WELCOME, "Hi, I'm your coach. I'll help you learn piano, a few minutes a day.", Modifier.fillMaxWidth(), size = 132.dp)
    StepTitle("Welcome to Sharp Ears")
    Text("Lessons that start from the very first key, songs that fall onto the keyboard, chords, ear training and theory. " +
        "It listens as you play and tells you how it went.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // True on every build: one with an AI key sends the parent brief's numbers (never a name or a sound), and says so.
    val ai = com.bigpapituna.sharpears.data.BriefAi.hasKey
    val email = com.bigpapituna.sharpears.data.BriefEmail.hasKey
    // Roy's testing (analytics/Tester.kt): only while "Help Roy test" (with the dashboard's key) or the diary (with an email key) is on.
    val testing = com.bigpapituna.sharpears.analytics.Tester.let { t ->
        t.switchOn.value && t.hasKey || t.diaryOn.value && t.diaryBuilt && email }
    Text(when {
        ai && email -> "Everything stays on this phone, except the parent brief's numbers, sent to an AI to write it, and the brief by email if a grown-up turns it on. " +
            "Settings, Family turns those off."
        ai -> "Everything stays on this phone, except the parent brief's numbers, sent to an AI to write it. Settings, Family turns that off."
        email -> "Everything stays on this phone, except the brief by email, if a grown-up turns it on. Settings, Family turns that off."
        testing -> "Everything stays on this phone, except what Roy's testing sends him while he has it on. Never any sound."
        else -> "Everything stays on this phone. Nothing is sent anywhere."
    } + if (testing && (ai || email)) " Roy's testing is on too: what happens on screen goes to Roy. Never any sound." else "",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ExperienceStep(picked: Int, onPick: (Int) -> Unit) {
    PupSays(Pose.LEARNING, "Have you played before? Any answer is fine. I'll pick a good place to start, and everything before it stays open.", Modifier.fillMaxWidth(), size = 96.dp)
    StepTitle("Have you played before?")
    Level.adult.forEach { l -> Choice(picked == l.ordinal, l.label, l.adultLine) { onPick(l.ordinal) } }
    val level = Level.entries.getOrNull(picked)
    if (level != null) {
        val plan = Placement.planFor(level, ProfileKind.ADULT)
        val lesson = COURSE.firstOrNull { it.id == plan.startUnitId }?.lessons?.firstOrNull { it.id == plan.startLessonId }
        Text("We'll start you in ${plan.startTitle}" + (lesson?.let { ", with \"${it.title}\"." } ?: ".") + plan.openLine().let { if (it.isEmpty()) "" else " $it" },
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun GoalStep(goal: Int, onPick: (Int) -> Unit) {
    PupSays(Pose.GET_READY, "How much time can you give me each day? Small and steady beats big and rare.", Modifier.fillMaxWidth(), size = 96.dp)
    StepTitle("Your daily goal")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DailyGoal.OPTIONS.forEach { m ->
            val sel = goal == m
            val c = MaterialTheme.colorScheme
            Column(
                Modifier.weight(1f).heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (sel) c.primaryContainer else c.surfaceContainerHigh)
                    .then(if (sel) Modifier.border(2.dp, c.primary, RoundedCornerShape(16.dp)) else Modifier)
                    .selectable(sel, role = Role.RadioButton, onClick = { onPick(m) })
                    .semantics { contentDescription = "$m minutes a day, ${DailyGoal.name(m)}" },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("$m", style = MaterialTheme.typography.titleLarge.mono(), color = if (sel) c.onPrimaryContainer else c.onSurface)
                Text("min", style = MaterialTheme.typography.labelSmall, color = if (sel) c.onPrimaryContainer else c.onSurfaceVariant)
            }
        }
    }
    Text(DailyGoal.name(goal), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Text(DailyGoal.note(goal), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("You can change it any time on the You tab.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HabitStep(
    askReminder: Boolean, reminderOn: Boolean, reminderMinutes: Int, useMic: Boolean,
    onReminder: (Boolean) -> Unit, onPickTime: () -> Unit, onMic: (Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    // As the phone shows times: its 12 or 24-hour setting, as the picker uses (java.time's localized format ignores it).
    val timeLabel = android.text.format.DateFormat.getTimeFormat(androidx.compose.ui.platform.LocalContext.current)
        .format(java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, reminderMinutes / 60); set(java.util.Calendar.MINUTE, reminderMinutes % 60) }.time).replace(' ', ' ')
    PupSays(Pose.LISTENING, if (askReminder) "Nearly there. Two quick choices and we can start." else "Nearly there. One quick choice and we can start.",
        Modifier.fillMaxWidth(), size = 96.dp)
    StepTitle(if (askReminder) "A reminder, and how you play" else "How you play")
    if (askReminder) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(reminderOn, role = Role.Switch, onValueChange = onReminder),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Daily reminder", style = MaterialTheme.typography.titleSmall)
            Text("A nudge to practise, once a day.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Switch(reminderOn, onCheckedChange = null)
    }
    if (askReminder && reminderOn) TextButton(onClick = onPickTime, modifier = Modifier.semantics { contentDescription = "Reminder time, $timeLabel, tap to change" }) {
        Text("Remind me at $timeLabel")
    }
    Text("How will you play?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    Choice(!useMic, "Keys on screen or MIDI", "I'll tap the keys on the screen, or plug in a MIDI keyboard.") { onMic(false) }
    Choice(useMic, "Microphone", "I play a real piano or keyboard and the app listens.") { onMic(true) }
    Text(if (askReminder) "You can change both in Settings." else "You can change it in Settings.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Start)
}

/** One answer: an outlined row that fills with the accent tint when picked. */
@Composable
private fun Choice(selected: Boolean, title: String, detail: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(shape)
            .background(if (selected) c.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.outlineVariant, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) c.onPrimaryContainer else c.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = if (selected) c.onPrimaryContainer.copy(alpha = 0.85f) else c.onSurfaceVariant)
    }
}
