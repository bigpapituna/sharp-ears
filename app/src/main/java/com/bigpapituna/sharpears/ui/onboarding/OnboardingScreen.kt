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
import com.bigpapituna.sharpears.engine.nav.FirstRun
import com.bigpapituna.sharpears.engine.family.HomeFirst
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.OwnerName
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.PlacementCheck
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.ui.Celebration
import com.bigpapituna.sharpears.ui.ChunkyButton
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.PupSays
import com.bigpapituna.sharpears.ui.Stat
import com.bigpapituna.sharpears.ui.paper
import com.bigpapituna.sharpears.ui.profile.mono
import com.bigpapituna.sharpears.ui.reducedMotion

private const val STEPS = 3

/**
 * First run, three short cards with the pup talking: welcome (the owner's name; 2.17, while the phone has no kid, "Who is this
 * for? Me / My child"), have you played before, and a daily goal. It ends on a calm "You're all set" (2.17, audit L1: the
 * trophy waits for the first lesson's finish). Answers go to [store] (experience, goal, onboardingDone).
 *
 * 2.17 (sharp-ears-audits/onboarding-audit-2026-10-28.md): the fourth card is gone. How someone plays is asked in context on
 * their first playing step (engine/nav/FirstRun.kt, H1), and the daily reminder after their first finished lesson (H2), so
 * nothing here asks Android for a permission. "Help Roy test is on" comes as a sheet after the welcome card, never over its
 * Continue (M1). Skip on any card goes to "You're all set" as a beginner (Never unless answered) with the first lesson's button
 * (L4). [onForChild]: "My child" was picked (null: the question is not asked): the caller opens the add-a-kid form.
 */
@Composable
fun OnboardingScreen(store: ProgressStore, prefs: Prefs,
                     /** The level they said (2.6, engine/family/Placement.kt), once the questions are answered: the profile keeps it and the course is placed. */
                     onLevel: (Level) -> Unit = {},
                     /** Offered after the questions to anyone above "never": the short listen ("Let's hear you play", ui/family/PlacementCheckScreen.kt). Null: not offered. */
                     onCheck: ((Level) -> Unit)? = null,
                     /** 2.14: ask the grown-up's name on the welcome card (the phone owner only); [onName] gets what was typed, blank when nothing was. */
                     askName: Boolean = true, onName: (String) -> Unit = {},
                     /** 2.17 (H4): "Who is this for?" on the welcome card; "My child" calls this. Null: not asked. */
                     onForChild: (() -> Unit)? = null,
                     onDone: (startLessonId: String?) -> Unit) {
    val context = LocalContext.current
    val still = reducedMotion()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var experience by rememberSaveable { mutableIntStateOf(-1) }
    var goal by rememberSaveable { mutableIntStateOf(store.goalMinutes.takeIf { it in DailyGoal.OPTIONS } ?: DailyGoal.DEFAULT) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var forChild by rememberSaveable { mutableStateOf(false) }
    var notice by rememberSaveable { mutableStateOf(false) }

    fun finish() {
        Level.entries.getOrNull(experience)?.let(onLevel)
        if (askName) onName(name)
        store.goalMinutes = goal
        store.onboardingDone = true
        finished = true
    }

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
        AllSet(
            line = if (level != Level.NEVER) "${plan.line} $firstLine" else firstLine,
            detail = "$goal minutes a day, starting at ${unit?.title ?: "Basics"}.",
            primary = if (check != null) "Let's hear you play" else if (songsFirst) "Show me the songs" else "Start learning",
            onPrimary = { if (check != null) check(level) else start() },
            secondary = if (check != null) (if (songsFirst) "Skip, show me the songs" else "Skip, start learning") else "Look around first",
            onSecondary = { if (check != null) start() else onDone(null) },
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
                // 2.17 (L4): Skip is "You're all set" as a beginner (or at the level already answered), with the first lesson's button.
                TextButton(onClick = {
                    com.bigpapituna.sharpears.analytics.Events.onboardingStep(step, completed = false)
                    if (experience < 0) experience = FirstRun.SKIP_LEVEL.ordinal
                    android.util.Log.d(com.bigpapituna.sharpears.TAG, "onboarding: skipped at card ${step + 1}")
                    finish()
                }) { Text("Skip") }
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
                        0 -> WelcomeStep(if (askName) name else null, { name = it }, if (onForChild != null) forChild else null) { forChild = it }
                        1 -> ExperienceStep(experience) { experience = it }
                        else -> GoalStep(goal) { goal = it }
                    }
                }
            }
            ChunkyButton(
                onClick = {
                    when (step) {
                        0 -> {
                            com.bigpapituna.sharpears.analytics.Events.onboardingStep(0, completed = true,
                                who = if (onForChild != null) (if (forChild) "child" else "me") else null)
                            if (forChild && onForChild != null) {
                                // H4: the child's profile first (the add-a-kid form); the grown-up's own cards wait, optional.
                                if (askName) onName(name)
                                android.util.Log.d(com.bigpapituna.sharpears.TAG, "onboarding: for my child, to the add-a-kid form")
                                onForChild()
                            } else {
                                // M1: Help Roy test's notice as a sheet, now that the welcome card's Continue was pressed.
                                if (com.bigpapituna.sharpears.engine.tester.TesterRules.noticeAfterWelcome(
                                        com.bigpapituna.sharpears.analytics.Tester.noticePending.value, step)) notice = true
                                step++
                            }
                        }
                        1 -> {
                            com.bigpapituna.sharpears.analytics.Events.onboardingStep(1, completed = true, level = Level.entries.getOrNull(experience)?.name)
                            step++
                        }
                        else -> { com.bigpapituna.sharpears.analytics.Events.onboardingStep(step, completed = true, goal = goal); finish() }
                    }
                },
                enabled = canContinue, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 20.dp),
            ) { Text(if (step == STEPS - 1) "Finish" else "Continue") }
        }
    }
    if (notice && com.bigpapituna.sharpears.analytics.Tester.noticePending.value)
        com.bigpapituna.sharpears.analytics.TesterDefaultNotice { com.bigpapituna.sharpears.analytics.Tester.noticeSeen(context); notice = false }
}

/**
 * 2.17 (audit L1): "You're all set", calm: the pup standing, one line, the plan in a sentence, the way in. No trophy, no
 * confetti, no fanfare: those wait for the first lesson's finish.
 */
@Composable
private fun AllSet(line: String, detail: String, primary: String, onPrimary: () -> Unit, secondary: String, onSecondary: () -> Unit) {
    Column(Modifier.fillMaxSize().paper().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            PupSays(Pose.STANDING, line, Modifier.fillMaxWidth(), size = 112.dp)
            StepTitle("You're all set")
            Text(detail, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ChunkyButton(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) { Text(primary) }
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(secondary) }
        }
    }
}

@Composable
private fun StepTitle(text: String) =
    Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })

@Composable
private fun WelcomeStep(name: String?, onName: (String) -> Unit, forChild: Boolean? = null, onForChild: (Boolean) -> Unit = {}) {
    PupSays(Pose.WELCOME, "Hi, I'm your coach. I'll help you learn piano, a few minutes a day.", Modifier.fillMaxWidth(), size = 132.dp)
    StepTitle("Welcome to Sharp Ears")
    Text("Lessons that start from the very first key, songs that fall onto the keyboard, chords, ear training and theory. " +
        "It listens as you play and tells you how it went.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 2.14: the grown-up's first name, optional: every greeting reads it, and none is "Hi there" (engine/family/OwnerName.kt).
    if (name != null) {
        val focus = androidx.compose.ui.platform.LocalFocusManager.current
        androidx.compose.material3.OutlinedTextField(name, { onName(it.take(Profile.MAX_NAME)) }, label = { Text(OwnerName.QUESTION) },
            supportingText = { Text(OwnerName.HINT) }, singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
    }
    // 2.17 (H4): who it is for, while the phone has no kid; "My child" goes to the add-a-kid form next.
    if (forChild != null) {
        Text(FirstRun.WHO_QUESTION, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { Choice(!forChild, FirstRun.WHO_ME, "I'm learning myself.") { onForChild(false) } }
            Box(Modifier.weight(1f)) { Choice(forChild, FirstRun.WHO_CHILD, "Set up my child first.") { onForChild(true) } }
        }
    }
    // True on every build: one with an AI key sends the parent brief's numbers (never a name or a sound), and says so.
    val ai = com.bigpapituna.sharpears.data.BriefAi.hasKey
    val email = com.bigpapituna.sharpears.data.BriefEmail.hasKey
    // Roy's testing (analytics/Tester.kt): only while "Help Roy test" (with the dashboard's key) or the diary (with an email key) is on.
    val testing = com.bigpapituna.sharpears.analytics.Tester.let { t ->
        t.switchOn.value && t.hasKey || t.diaryOn.value && t.diaryBuilt && email }
    Text(when {
        ai && email -> "Everything stays on this phone, except the parent brief's numbers, sent to an AI to write it, and the brief by email if a grown-up turns it on. " +
            "Settings, Family, Briefs and email turns those off."
        ai -> "Everything stays on this phone, except the parent brief's numbers, sent to an AI to write it. Settings, Family, Briefs and email turns that off."
        email -> "Everything stays on this phone, except the brief by email, if a grown-up turns it on. Settings, Family, Briefs and email turns that off."
        testing -> "Everything stays on this phone, except what Roy's testing sends him while it is on (Settings, About, Testing). Never any sound."
        else -> "Everything stays on this phone. Nothing is sent anywhere."
    } + if (testing && (ai || email)) " Roy's testing is on too: " + com.bigpapituna.sharpears.engine.tester.TesterRules.SENDS + " go to Roy. Never any sound." else "",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ExperienceStep(picked: Int, onPick: (Int) -> Unit) {
    PupSays(Pose.LEARNING, "Any answer is fine. I'll pick a good place to start, and everything before it stays open.", Modifier.fillMaxWidth(), size = 96.dp)
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
