package com.bigpapituna.sharpears.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.MidiDevices
import com.bigpapituna.sharpears.audio.Sampler
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Report
import com.bigpapituna.sharpears.engine.feedbackLine
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.RunWords
import com.bigpapituna.sharpears.engine.ForegroundClock
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.RunTally
import com.bigpapituna.sharpears.engine.brief.StepRecorder
import com.bigpapituna.sharpears.engine.family.PawWeek
import com.bigpapituna.sharpears.engine.skin.Bedtime
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.world.PupReaction
import com.bigpapituna.sharpears.ui.family.LocalParentGate
import com.bigpapituna.sharpears.ui.family.profile
import com.bigpapituna.sharpears.ui.skin.Goodbye
import com.bigpapituna.sharpears.ui.skin.rememberBedtimeStore
import com.bigpapituna.sharpears.ui.theory.SceneBar
import com.bigpapituna.sharpears.ui.theory.SceneControls
import com.bigpapituna.sharpears.ui.theory.ScenePreview
import com.bigpapituna.sharpears.ui.theory.SceneStage
import com.bigpapituna.sharpears.ui.theory.pose
import com.bigpapituna.sharpears.ui.theory.rememberScenePlayback
import com.bigpapituna.sharpears.ui.pup.Pose
import com.bigpapituna.sharpears.ui.pup.rememberPupController
import com.bigpapituna.sharpears.ui.pup.mood
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.lessons.StepOutcome
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.bigpapituna.sharpears.ui.pup.inRect

/** Everything a lesson needs from the activity. */
class LessonDeps(val sampler: Sampler, val midi: MidiDevices, val mic: MicInput, val voice: Voice, val prefs: Prefs, val ready: Boolean)

/**
 * One lesson, a step at a time, under a row of progress dots, with Back and Next. A reading step is a short
 * explainer (2.0.1): the pup says each scene's caption (aloud too) over its animated picture on the stage, the
 * scenes playing one after another; Pause and Watch again work as in Theory, and Next moves through the scenes and
 * then on. An exercise step shows the pup with its instruction (read aloud) over the embedded player and completes
 * itself when its run ends (a Perform step at its pass mark); then the feedback line is spoken and the next step
 * opens. The lesson is done when its last step completes. [inject] (debug) feeds the mic for the step opened first.
 *
 * Kids (2.1, docs/KIDS-PLAN.md section 3) get bites: a reading step shows its first scene, then a "More?" pill for the
 * rest; an exercise is cut at its phrase end ([phraseEnd], beats; null plays it all) and one run passes (a Perform step
 * at two stars, a ramp as one run at three quarters speed); feedback is the pup's kid words with their name, never a
 * number; one main button per screen (no Back or scene controls in the bottom row). When a kid's daily goal is reached
 * the lesson ends on the goodbye (ui/skin/Goodbye) and new lessons wait for tomorrow ([onSongs] and [onJam] stay open).
 * Grown-ups see none of this.
 *
 * The Playground's game steps (2.1) draw their own stage (ui/playground's registry) where the embedded player would be.
 * [onDone] reports the lesson and returns what it dropped in the pup's world: the finish shows it flipping in, and
 * "one more" offers [next] with a peek at its drop. A wrong key makes the pup tilt his head.
 *
 * 2.2 (the kid home): [onDone] also gets the lesson's stars (engine/kid/LessonStars: each graded step's passing run by
 * the recap rules, a step let go after three tries as one, their mean), and a kid's finish shows them over the drop.
 * [finishLabel] names a kid's way on when the caller has somewhere special to go (the page's finale); [holdGoodnight]
 * keeps today's goodbye for after it.
 */
@Composable
fun LessonScreen(
    lesson: CourseLesson, startStep: Int, deps: LessonDeps, inject: String?,
    /** Debug: MIDI input played into the first step (see MidiDevices.debugPlay). */
    midiScript: String? = null,
    /** Debug (--ei scene n): the scene a reading step opened first starts on. */
    startScene: Int = 0,
    onStep: (Int) -> Unit, onSong: (() -> Unit)?, onDone: (stars: Int) -> com.bigpapituna.sharpears.engine.world.Reward?, onNext: (() -> Unit)?, onBack: () -> Unit,
    /** Where a kid's bite of an exercise ends, in beats (null: the whole exercise). The playground team's phraseEnd. */
    phraseEnd: (Exercise) -> Double? = Bites::phraseEndBeats,
    /** The goodbye's ways on: the songs, and the Jam. Null: Back. */
    onSongs: (() -> Unit)? = null, onJam: (() -> Unit)? = null,
    /** The lesson [onNext] opens, for "one more" (its title and the peek at its drop). */
    next: CourseLesson? = null,
    /** A kid's finish button when the caller has its own way on ([onNext] or [onBack] runs it); null: the usual words. */
    finishLabel: String? = null,
    /** Today's goodbye waits for the caller (it shows it after the finale); the lesson does not say goodnight itself. */
    holdGoodnight: Boolean = false,
    /** The top bar's back arrow and its label when it goes somewhere other than [onBack] (Practice today: back to the plan). */
    onArrow: (() -> Unit)? = null, arrowLabel: String? = null,
    /**
     * 2.14: "Try for 3 stars" on the finish, offered under three stars: the caller opens this lesson again at [step], its first
     * graded step (engine/kid/LessonStars.firstGraded), not its reading page. Null: not offered.
     */
    onRetry: ((step: Int) -> Unit)? = null,
) {
    val who = profile()
    // 2.14: the kid skin (bites, the pup's words, one button) is a little kid's; bedtime and the brief's card are every kid's.
    val kid = who.kidSkin
    val anyKid = who.isKid
    val path = remember(who.firstUnitId, who.reader) { com.bigpapituna.sharpears.engine.CoursePath(who.firstUnitId, bigKid = kid && who.reader) }
    val level = path.levelOf(lesson) ?: ALL.first { it.second == lesson }.first
    val world = com.bigpapituna.sharpears.ui.world.LocalWorld.current
    var reward by remember { mutableStateOf<com.bigpapituna.sharpears.engine.world.Reward?>(null) }
    var index by remember { mutableIntStateOf(startStep.coerceIn(0, lesson.steps.lastIndex)) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    var run by remember { mutableIntStateOf(0) }              // bumps to restart the step's player
    var advancedByItself by remember { mutableStateOf(false) }
    var reaction by remember { mutableStateOf<Pose?>(null) }     // the pup's answer to the last try, for a few seconds
    var fails by remember { mutableIntStateOf(0) }               // tries of this step that did not pass
    val stageMemo = remember(lesson.id) { mutableMapOf<String, Any>() }  // a stage's choices kept across its retries
    // Each graded step's stars as it passes (2.2): the lesson's stars are their mean (engine/kid/LessonStars).
    val stepStars = remember { androidx.compose.runtime.mutableStateListOf<Int>() }
    var lessonStars by remember { mutableIntStateOf(0) }
    LaunchedEffect(reaction) { if (reaction != null) { kotlinx.coroutines.delay(4000); reaction = null } }
    // The bar after a try (the pup's verdict and one button), and what the finish celebration counts.
    var bar by remember { mutableStateOf<Triple<Cheer, String, String?>?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var cheers by remember { mutableIntStateOf(lesson.id.hashCode()) }
    val openedAt = remember { com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() }   // on-screen time: never negative
    // XP: each step passed is reported here, the lesson itself by onDone (MainActivity); the celebration shows the sum.
    val progress = com.bigpapituna.sharpears.ui.profile.LocalProgress.current
    val xpAtOpen = remember { progress?.xp ?: 0 }
    var xpAtEnd by remember { mutableIntStateOf(0) }
    var tookSecs by remember { mutableStateOf(0L) }
    // The step as this person plays it: a kid's is cut to a bite.
    val step = remember(index, kid) { lesson.steps[index].let { if (kid) Bites.kidStep(it, it.exercise?.let(phraseEnd)) else it } }
    val last = index == lesson.steps.lastIndex
    // The parent brief's log (data/Briefs): each step's tries, let go, hints and time, written once as it is left.
    val stepLog = remember(index) { StepRecorder(lesson.id, index, step, ForegroundClock.app.nowMs()) }
    val runTally = remember(index) { arrayOfNulls<RunTally>(1) }      // the player's tally of the try that just ended
    // Left without passing (Back, or the lesson closed) after a try, or during one (the player hands over the unfinished
    // try's tally as it goes, just before this runs): still a step the brief should see.
    DisposableEffect(stepLog) { onDispose {
        runTally[0]?.let { stepLog.abandoned(it); runTally[0] = null }
        if (stepLog.runs > 0) Briefs.logAll(stepLog.events(Briefs.now(), ForegroundClock.app.nowMs(), passed = bar?.first == Cheer.GOOD))
    } }
    // Bedtime (kids only): the goodbye once today's goal is reached; a lesson opened after it finds the pup asleep.
    val bedtime = rememberBedtimeStore()
    val gate = LocalParentGate.current
    val today = remember { com.bigpapituna.sharpears.data.AppClock.today() }
    var asleep by remember { mutableStateOf(anyKid && progress != null &&
        !Bedtime.lessonOpen(true, progress.secondsToday(today), progress.goalMinutes, bedtime.oneMoreOn, today, bedtime.bedtime)) }
    var goodnight by remember { mutableStateOf(false) }
    val week = remember(goodnight, asleep) { PawWeek.of(progress?.practiceLog() ?: emptyMap(), today) }
    // 2.14: wind down's "Close Songs and the Jam too": the goodbye then offers only the goodbye.
    // (2.14 merge: every kid, a big kid in the grown-up skin too: wind down is a parental control, anyKid not the skin.)
    val songsOpen = !anyKid || progress == null || Bedtime.songsOpen(true, progress.secondsToday(today), progress.goalMinutes, bedtime.bedtime, bedtime.closeSongs)
    // A kid's reading step: one scene, then "More?" for the rest.
    var more by remember(index) { mutableStateOf(false) }
    // 2.14: graded steps passed by with Next (after the sheet): any makes the finish neutral, never "You did it!".
    var skippedGraded by remember { mutableIntStateOf(0) }
    var askSkip by remember { mutableStateOf(false) }
    // After today's goodbye the pup is asleep: nothing of the lesson starts (no voice, no player) unless a grown-up says.
    if (asleep) {
        LaunchedEffect(Unit) { deps.voice.stop(); Log.d(TAG, "bedtime: ${lesson.id} asleep") }
        Goodbye(who.name, week, onSongs = { (onSongs ?: onBack)() }, onClose = onBack, onJam = onJam, asleep = true, picture = kid && !who.reader,
            songsOpen = songsOpen,
            onOneMore = { gate.guard { bedtime.oneMoreOn = today; asleep = false; Log.d(TAG, "bedtime: a grown-up allowed one more lesson") } },
            say = { deps.voice.speak(it, interrupt = true) })
        return
    }
    val voice = deps.voice
    val context = androidx.compose.ui.platform.LocalContext.current
    val clicks = remember { runCatching { com.bigpapituna.sharpears.audio.Clicks(context) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { clicks?.release() } }
    // 2.11, the step pop: a clean step with another to come gets one of four pictures over the step, the pup's beat and a
    // chime that climbs a scale step with each clean step in a row (engine/lessons/StepPopRules.kt). A new lesson, a new streak.
    val still = reducedMotion()
    val pops = rememberStepPops(deps.sampler, deps.mic, sound = { deps.prefs.sound }, key = lesson.id,
        tonic = remember(lesson.id) { StepPopRules.tonicOf(lesson.steps.mapNotNull { it.exercise }) })
    // 2.13, cheer pops: now and then, while a step plays, the pup pops up over the header with a few kind words
    // (ui/pup/CheerPop.kt, engine/lessons/CheerRules.kt). The embedded player and the Playground stages feed the same rules
    // through LocalCheers; a step passed hands its line to the step pop. A new lesson, new rules; the bars it missed stay.
    val cheerPops = com.bigpapituna.sharpears.ui.pup.rememberCheerPops(deps.sampler, deps.mic, deps.voice, deps.prefs, kid, key = lesson.id,
        tonic = remember(lesson.id) { StepPopRules.tonicOf(lesson.steps.mapNotNull { it.exercise }) }, profileKid = anyKid)
    cheerPops.chiming = { pops.chiming }
    // The header (the title row and the step dots): the cheer pop sits over its end, above the instruction.
    val headerBox = remember { mutableStateOf<Rect?>(null) }
    // The pup's beat when a step passes: the pop's hop (none with animations off), or the jump as before.
    var passBeat by remember { mutableStateOf(PupMood.HAPPY) }
    // Where the step is on screen and how tall the cheer bar is, for the pop (read only while it draws).
    val stepArea = remember { mutableStateOf<Rect?>(null) }
    val screenBox = remember { mutableStateOf(Rect.Zero) }
    val barPx = remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // A reading step plays its scenes; the pup says each caption as it comes (queued behind a feedback line after
    // an automatic advance).
    val reading = step.kind == StepKind.READ && step.scenes.isNotEmpty() && !finished
    val bite = kid && !more && step.scenes.size > 1
    val scenes = if (reading) key(index, more) {
        rememberScenePlayback(if (bite) step.scenes.take(Bites.scenesShown(step.scenes.size, kid = true, more = false)) else step.scenes,
            deps.sampler, clicks, voice, start = if (kid && more) 1 else if (index == startStep) startScene else 0,
            tag = "lesson ${lesson.id} step ${index + 1}" + if (bite) " (bite)" else "", interruptFirst = !advancedByItself || more)
    } else null
    // "More?" appears once the first scene has played.
    val firstSceneDone = scenes?.let { pb -> remember(pb) { androidx.compose.runtime.derivedStateOf { pb.fraction() >= 1f } }.value } ?: false

    // Read each exercise step out as it opens. After an automatic advance the feedback line is already queued, so no interrupt.
    LaunchedEffect(index) {
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1}/${lesson.steps.size} ${step.kind}" +
            if (kid) " kid" + (step.exercise?.let { e -> if (e != lesson.steps[index].exercise) " bite ${Bites.beats(e.right).coerceAtLeast(Bites.beats(e.left))} beats" else "" } ?: "") else "")
        onStep(index)
        if (step.scenes.isEmpty()) voice.speak(step.text.replace("\n\n", " "), interrupt = !advancedByItself)
        advancedByItself = false
    }

    // 2.13: each step and each try starts the cheer pops' counts again (a stage's retry is a new stage; the player starts it
    // again when its run starts). The part is the exercise, so a bar that went astray is remembered across the tries.
    LaunchedEffect(index, run) { cheerPops.stepBegan("${lesson.id}-${index + 1}") }

    fun go(to: Int) { pops.flush(); cheerPops.clear(); feedback = null; bar = null; finished = false; index = to; fails = 0 }
    fun complete() {
        pops.flush()
        cheerPops.clear()
        Briefs.logAll(stepLog.events(Briefs.now(), ForegroundClock.app.nowMs(), passed = true))
        progress?.record(com.bigpapituna.sharpears.engine.progress.ProgressEvent.LessonStepPassed)
        if (last) {
            finished = true
            tookSecs = (com.bigpapituna.sharpears.engine.ForegroundClock.app.nowMs() - openedAt) / 1000
            lessonStars = com.bigpapituna.sharpears.engine.kid.LessonStars.ofLesson(stepStars.toList())
            val firstFinish = !deps.prefs.lessonPlayed(lesson.id)
            reward = onDone(lessonStars)
            Briefs.log { BriefEvent.LessonDone(it, lesson.id, lessonStars, tookSecs * 1000, firstFinish) }
            // A grown-up's "one more" (bedtime on) is spent with this lesson: the next one sleeps again.
            if (anyKid && progress != null && Bedtime.oneMoreSpent(true, progress.secondsToday(today), progress.goalMinutes, bedtime.oneMoreOn, today, bedtime.bedtime)) {
                bedtime.oneMoreOn = null; Log.d(TAG, "bedtime: one more lesson spent on ${lesson.id}")
            }
            xpAtEnd = progress?.xp ?: 0
            Log.d(TAG, "lesson ${lesson.id} done")
            // 2.14: a skip is never "You did it!": the neutral finish says nothing (its words are on screen; no new recorded line).
            if (skippedGraded == 0) voice.speak(if (kid) RunWords.kidLessonLine(who.name) else "Lesson complete.")
        } else { advancedByItself = true; feedback = null; index++ }
    }
    fun exerciseEnded(s: Step, report: Report) {
        val judged = if (kid) Bites.kidPassed(s, report) else s.passed(report)
        // Pup's Playground (2.1.1, no dead ends): the third try that does not pass lets the step go on.
        val letGo = !judged && s.kind != StepKind.LISTEN && com.bigpapituna.sharpears.engine.playground.StepRetry.letGo(lesson.id, fails + 1)
        val passed = judged || letGo
        val tally = runTally[0]
        runTally[0] = null
        stepLog.run(report, tally, letGo)
        // 2.11: the pop for a clean step with another to come. Not on a let-go (nor a pass made of let-go rounds or notes),
        // not on the last step (Celebrate has it), not on a listening step; the streak starts again on a let-go.
        val outcome = StepPopRules.outcome(listen = s.kind == StepKind.LISTEN, judged = judged, letGo = letGo, last = last,
            mostlyLetGo = StepPopRules.mostlyLetGo(report.letGoRounds, report.rounds) || StepPopRules.mostlyLetGo(tally?.letGos ?: 0, report.groups))
        val streak = pops.streak.after(outcome)
        // 2.13: the try is over, so a cheer pop on screen goes; a step passed gives its line to the step pop's bubble.
        cheerPops.clear()
        // One of four pictures, rolled per lesson step (never the last one again); the pup's beat goes with it.
        val pop = if (outcome == StepOutcome.POP) pops.fire(streak, StepPopRules.seedOf(lesson.id, index), line = cheerPops.stepPassed()) else null
        passBeat = if (pop == null) PupMood.HAPPY else if (still) PupMood.IDLE else StepPopRules.pupBeat(pop.variant)
        // A Listen step has nothing to judge; Play and Perform steps get one line naming the main mistake (a kid: the pup's
        // words with their name, no numbers).
        val line = if (s.kind == StepKind.LISTEN) null
            else if (letGo) com.bigpapituna.sharpears.engine.playground.PlaygroundLines.LET_GO.let { com.bigpapituna.sharpears.engine.Feedback(it, it) }
            else if (kid) RunWords.kidStepLine(passed, last, who.name).let { com.bigpapituna.sharpears.engine.Feedback(it, it) }
            else if (s.kind.needsMidi) (report.extraScore ?: 0).let { score ->
                val what = if (s.kind == StepKind.PEDAL) "Pedal changes in time" else "Notes at the right loudness"
                val text = "$what: $score percent. " + if (passed) (if (last) "Passed." else "Passed. On to the next step.") else "You need ${s.exercise?.pass ?: 80}. Play it again."
                com.bigpapituna.sharpears.engine.Feedback(text, text)
            }
            else if (s.kind == StepKind.RAMP) com.bigpapituna.sharpears.engine.Feedback("Full speed. " + if (last) "Lesson done." else "On to the next step.",
                "Full speed. " + if (last) "Lesson done." else "On to the next step.")
            else feedbackLine(report, perform = s.kind == StepKind.PERFORM, pass = s.exercise?.pass ?: 80, next = !last, flats = s.exercise?.flats == true)
        Log.d(TAG, "lesson ${lesson.id} step ${index + 1} ended passed=$passed${if (letGo) " (let go after ${fails + 1} tries)" else ""} feedback=\"${line?.text}\"")
        feedback = line?.text
        // The pup's line waits for the chime when one plays (it never talks over it).
        line?.let { l -> pops.afterChime { voice.speak(l.spoken) } }
        if (s.kind != StepKind.LISTEN) reaction = if (judged) Pose.GREAT_JOB else if (kid || letGo) Pose.GET_READY else Pose.MISTAKE
        if (s.kind == StepKind.LISTEN) { if (passed) complete(); return }
        if (!passed) { fails++; retries++; bar = Triple(Cheer.MISS, if (kid) "Once more!" else "Not quite", line?.text); return }
        // 2.14: notes the player let go after three tries keep the third star away too (Stars.three).
        stepStars += if (letGo) com.bigpapituna.sharpears.engine.kid.LessonStars.LET_GO else com.bigpapituna.sharpears.engine.kid.LessonStars.ofRun(report, tally?.letGos ?: 0)
        // Passing a step that took a few tries earns the high-five (not a step let go).
        val highFive = fails >= 2 && !letGo
        if (highFive) { pops.afterChime { voice.speak(com.bigpapituna.sharpears.engine.HIGH_FIVE) }; Log.d(TAG, "pup: high five after $fails tries") }
        fails = 0
        // The last step goes straight to the celebration; any other waits on Continue.
        if (last) complete() else bar = Triple(Cheer.GOOD, if (letGo) "Good try!" else if (highFive) "High five!" else cheerTitle(cheers++),
            if (highFive) "You stuck with it. " + (line?.text ?: "") else line?.text)
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { screenBox.value = it.boundsInRoot() }) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { headerBox.value = it.boundsInRoot() }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { voice.stop(); (onArrow ?: onBack)() }) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = arrowLabel ?: if (kid) "Back to the map" else "Back to lessons")
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    if (!kid) Text("${level.title}, lesson ${path.numberOf(lesson)}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text(lesson.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, modifier = Modifier.semantics { heading() })
                }
            }
            StepDots(lesson.steps.size, index, finished, Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp))
            }
            if (finished) {
                // The celebration covers the screen (below).
            } else if (step.kind.needsMidi && deps.midi.connected.value == null) {
                // No keyboard: the pup says why over a keyboard being plugged in; the step starts once one is, or is skipped.
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PupSays(Pose.LEARNING, if (deps.prefs.micDefault) "This step needs a MIDI keyboard. The microphone hears which notes you play, not the pedal or how hard you strike."
                        else "This step needs a MIDI keyboard connected by USB: only MIDI reports the pedal and how hard each key is struck.",
                        Modifier.padding(horizontal = 4.dp), size = 64.dp, style = MaterialTheme.typography.bodyLarge)
                    Box(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = 300.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        ScenePreview(MIDI_NEEDED, Modifier.fillMaxSize().padding(6.dp), loop = true,
                            description = "A digital piano plugged into the phone by a USB cable, sending the pedal and how hard each key is struck")
                    }
                    Text("Plug one in by USB and this step starts by itself.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
                    OutlinedButton(onClick = { Log.d(TAG, "lesson ${lesson.id} step ${index + 1} skipped (needs MIDI)")
                        Briefs.logAll(stepLog.events(Briefs.now(), ForegroundClock.app.nowMs(), passed = false, skipped = true)); complete() }) { Text("Skip (needs MIDI)") }
                }
            } else if (scenes != null) {
                // The explainer: the scene bar, the pup with the scene's caption, the stage.
                Column(Modifier.weight(1f).semantics { contentDescription = step.text }) {
                    if (scenes.scenes.size > 1) SceneBar(scenes.scenes.size, scenes.index, scenes::fraction)
                    PupSays(scenes.scene.mood.pose(), scenes.scene.caption, Modifier.padding(top = 12.dp, bottom = 12.dp).heightIn(min = 72.dp), size = 72.dp)
                    SceneStage(scenes, Modifier.weight(1f), roomier = true)
                    // A kid's bite: the rest of the step only if they ask.
                    if (bite) androidx.compose.animation.AnimatedVisibility(firstSceneDone,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)) {
                        OutlinedButton(onClick = { Log.d(TAG, "lesson ${lesson.id} step ${index + 1} more"); more = true }) {
                            Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp)); Text("More?")
                        }
                    }
                    if (last && onSong != null) ChunkyButton(onClick = { voice.stop(); onSong() }, Modifier.padding(top = 12.dp)) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp)); Text("Open the song")
                    }
                }
            } else if (step.exercise == null) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The coach explains: his first paragraph in his bubble, the rest as plain notes under it.
                    val paras = step.text.split("\n\n")
                    PupSays(Pose.LEARNING, paras.first(), Modifier.padding(top = 4.dp), size = 96.dp)
                    paras.drop(1).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    if (last && onSong != null) ChunkyButton(onClick = { voice.stop(); onSong() }) {
                        Icon(painterResource(R.drawable.ic_play_arrow), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(6.dp)); Text("Open the song")
                    }
                }
            } else {
                // The pup gives the instruction; his face answers each try for a few seconds, and that face is the
                // message, so no idle acts over it. He tilts his head at a wrong key. While he plays a listening step
                // ("My turn. Listen.") he keeps listening: nothing is pressed meanwhile, and he used to scratch or sneeze.
                val pup = rememberPupController(idle = reaction == null && step.kind != StepKind.LISTEN)
                val pupNow by androidx.compose.runtime.rememberUpdatedState(pup)
                // A clapping step shows him clapping, not at the piano ("Clap with my drum!"). 2.10: while the app plays a
                // listening step's demo he points at the keys below (a little slide and lean their way); a step passed, he
                // jumps for joy; a wrong key tilts his head (onWrongKey) and a right one wags him (onRightKey).
                val asks = when { step.game == com.bigpapituna.sharpears.engine.playground.Game.CLAP -> Pose.CLAPPING
                    step.kind == StepKind.LISTEN -> Pose.GET_READY; else -> Pose.PRACTICING }
                val shown = reaction ?: asks
                LaunchedEffect(pup) { pup.pointAt = com.bigpapituna.sharpears.engine.pup.PupMotion.NEAR_POINT }
                PupSays(shown, step.text,
                    Modifier.padding(horizontal = 4.dp).padding(bottom = 10.dp), size = 56.dp, style = MaterialTheme.typography.bodyLarge,
                    controller = pup, mood = when {
                        reaction == Pose.GREAT_JOB -> passBeat
                        reaction == null && step.kind == StepKind.LISTEN -> PupMood.POINT
                        else -> shown.mood
                    })
                val ex = step.exercise
                androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.pup.LocalCheers provides cheerPops) {
                Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { stepArea.value = it.boundsInRoot() }, contentAlignment = Alignment.TopCenter) {
                    // A Playground game draws its own stage (the pup flying, the key hunt, the drum, the nap, the concert).
                    if (com.bigpapituna.sharpears.ui.playground.hasPlaygroundStage(step)) key(index, run) {
                        androidx.compose.runtime.CompositionLocalProvider(com.bigpapituna.sharpears.ui.playground.LocalStageMemo provides stageMemo) {
                            com.bigpapituna.sharpears.ui.playground.PlaygroundStage(step, lesson.id, deps,
                                inject = inject?.takeIf { index == startStep && run == 0 },
                                onEnded = { exerciseEnded(step, it) },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).fillMaxHeight())
                        }
                    } else
                    // The stage is always dark, like the full player: the one raised panel on this screen.
                    SharpEarsTheme(dark = true) {
                        key(index, run) {
                            PlayerScreen(
                                ex.toSong("${lesson.id}-${index + 1}", lesson.title), null, deps.sampler, deps.midi, deps.mic, voice, deps.prefs, deps.ready,
                                0.0, false,
                                PlayerStart(step.mode, ex.practice, mic = step.kind != StepKind.LISTEN && !step.kind.needsMidi && deps.prefs.micDefault && deps.mic.hasPermission(),
                                    midiScript = midiScript?.takeIf { index == startStep && run == 0 },
                                    inject = inject?.takeIf { index == startStep && run == 0 && step.kind != StepKind.LISTEN },
                                    beatLines = ex.beatLines),
                                onBack = onBack,
                                onFinished = { exerciseEnded(step, it) },
                                embedded = Embedded(if (kid) kidStateLabel(step) else stateLabel(step), labels = step.labels, ramp = step.kind == StepKind.RAMP,
                                    special = step.kind.takeIf { it.needsMidi }, onTally = { runTally[0] = it }),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).fillMaxHeight().clip(MaterialTheme.shapes.medium),
                                anyOfLessons = com.bigpapituna.sharpears.engine.playground.ANY_OF_LESSONS,
                                onWrongKey = { pupNow.react(PupReaction.HEAD_TILT) },
                                onRightKey = { pupNow.play(PupMood.WAG) },
                                letGo = com.bigpapituna.sharpears.engine.playground.StepRetry.isPlayground(lesson.id),
                            )
                        }
                    }
                }
                }
            }
        }
        Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (finished && onNext != null && !kid) OutlinedButton(onClick = onBack) { Text("All lessons") }
            // Back steps through the scenes of a reading step first, then to the step before. A kid gets one button only.
            if (!kid && !finished && (index > 0 || (scenes?.index ?: 0) > 0)) OutlinedButton(onClick = {
                val sc = scenes
                if (sc != null && sc.index > 0) sc.go(sc.index - 1) else { go(index - 1); run++ }
            }) { Text("Back") }
            if (scenes != null && !kid) SceneControls(scenes, voice, "lesson ${lesson.id}:")
            Spacer(Modifier.weight(1f))
            when {
                finished -> onNext?.let { ChunkyButton(onClick = it) { Text("Next lesson") } } ?: ChunkyButton(onClick = onBack) { Text("Done") }
                scenes != null && !scenes.last -> ChunkyButton(onClick = { scenes.go(scenes.index + 1) }) { Text("Next") }
                last && step.exercise == null -> ChunkyButton(onClick = { complete() }) { Text("Finish") }
                last -> {}                                            // an exercise as the last step finishes the lesson itself
                else -> ChunkyButton(onClick = {
                    // 2.14: Next over a graded step not passed asks first ("Skip this one? It stays at one star.").
                    if (bar == null && com.bigpapituna.sharpears.engine.kid.LessonStars.graded(step)) { askSkip = true; Log.d(TAG, "lesson ${lesson.id} step ${index + 1} skip asked") }
                    else {
                        (step.exercise == null || bar?.first == Cheer.GOOD).let { ok -> Briefs.logAll(stepLog.events(Briefs.now(), ForegroundClock.app.nowMs(), passed = ok, skipped = !ok)) }
                        go(index + 1); run++
                    }
                }) {
                    Text("Next")
                    // A pre-reader sees where it goes (QA 2.8.3: "Next" was words only).
                    if (kid) { Spacer(Modifier.size(6.dp)); Icon(painterResource(R.drawable.ic_arrow_forward), contentDescription = null, modifier = Modifier.size(20.dp)) }
                }
            }
        }
    }
    // 2.14: the sheet before Next passes a graded step by. A graded step skipped counts as one star, so Next through every
    // game is not three stars, nor a clean step: the pop's climb starts again (2.11). The finish is then neutral.
    if (askSkip) androidx.compose.material3.AlertDialog(onDismissRequest = { askSkip = false },
        title = { Text(com.bigpapituna.sharpears.engine.kid.LessonStars.SKIP_TITLE) },
        text = { Text(com.bigpapituna.sharpears.engine.kid.LessonStars.SKIP_LINE) },
        confirmButton = { androidx.compose.material3.TextButton(onClick = {
            askSkip = false
            com.bigpapituna.sharpears.engine.kid.LessonStars.ofSkip(step)?.let { stepStars += it; pops.streak.skipped(); skippedGraded++ }
            Log.d(TAG, "lesson ${lesson.id} step ${index + 1} skipped (it stays at one star)")
            Briefs.logAll(stepLog.events(Briefs.now(), ForegroundClock.app.nowMs(), passed = false, skipped = true))
            go(index + 1); run++
        }) { Text(com.bigpapituna.sharpears.engine.kid.LessonStars.SKIP_YES) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { askSkip = false; Log.d(TAG, "lesson ${lesson.id} step ${index + 1} skip: kept playing") }) {
            Text(com.bigpapituna.sharpears.engine.kid.LessonStars.SKIP_NO) } })
    // The step pop over the part of the step the cheer bar leaves in view (2.11), under the bar; it takes no touches.
    val popArea: () -> Rect? = {
        stepArea.value?.let { a ->
            val box = screenBox.value
            val barTop = if (bar != null && barPx.intValue > 0) box.bottom - barPx.intValue + with(density) { CHEER_BAR_PEEK.toPx() } else a.bottom
            val bottom = maxOf(minOf(a.bottom, barTop), a.top + with(density) { POP_MIN_HEIGHT.toPx() })
            Rect(a.left, a.top, a.right, bottom).translate(-box.left, -box.top)
        }
    }
    StepPop(pops.cue, Modifier.fillMaxSize(), area = popArea)
    // 2.13: the step passed line in a bubble over the check, and the cheer pop over the end of the header (the title and
    // the step dots), never over the instruction, the keys or the notes. Neither takes a touch.
    com.bigpapituna.sharpears.ui.pup.StepPopLine(pops.cue?.seq, pops.cue?.line, area = popArea, Modifier.fillMaxSize())
    if (!finished) com.bigpapituna.sharpears.ui.pup.CheerPop(cheerPops.cue,
        Modifier.inRect { headerBox.value?.translate(-screenBox.value.left, -screenBox.value.top) })
    bar?.let { (cheer, title, line) ->
        CheerBar(cheer, title, line, if (cheer == Cheer.GOOD) "Continue" else "Try again",
            Modifier.align(Alignment.BottomCenter).onSizeChanged { barPx.intValue = it.height }, onButton = {
            Log.d(TAG, "lesson ${lesson.id} step ${index + 1} ${if (cheer == Cheer.GOOD) "continue" else "try again"}")
            bar = null
            if (cheer == Cheer.GOOD) complete() else { feedback = null; run++ }
        })
    }
    // Today's goal reached with this lesson, bedtime off: the goal's cheer, once a day, after the pup's line (songs and
    // finales cheer in MainActivity). Checked once the last step's player or stage has written its time.
    // (2.14 merge: every kid, as MainActivity's maybeGoodnight has it for songs; bedtime is the grown-ups' rule, not the skin.)
    if (anyKid && finished && progress != null && !holdGoodnight) LaunchedEffect(Unit) {
        if (!bedtime.bedtime && Bedtime.sayWellDone(true, progress.secondsToday(today), progress.goalMinutes, bedtime.wellDoneOn, today)) {
            bedtime.wellDoneOn = today
            voice.speak(com.bigpapituna.sharpears.engine.SessionWords.wellDone(who.name))
            Log.d(TAG, "bedtime: goal cheered after ${lesson.id}")
        }
    }
    // A kid who reached today's goal with this lesson: the celebration's way on is goodnight, not the next lesson.
    val bedtimeNow = anyKid && finished && progress != null && !holdGoodnight &&
        Bedtime.sayGoodnight(true, progress.secondsToday(today), progress.goalMinutes, bedtime.goodnightOn, today, bedtime.bedtime)
    // 2.14: the finish's stars part (ui/Celebrate.kt FinishStars): "Try for 3 stars" straight to the first graded step, and a
    // neutral finish after a skip. A grown-up offered it sees the stars it is about.
    val neutral = com.bigpapituna.sharpears.engine.kid.LessonStars.finish(skippedGraded) == com.bigpapituna.sharpears.engine.kid.LessonStars.Finish.NEUTRAL
    val retry = if (onRetry != null && com.bigpapituna.sharpears.engine.kid.LessonStars.tryForThree(lessonStars, lesson.steps))
        com.bigpapituna.sharpears.engine.kid.LessonStars.TRY_FOR_THREE to {
            voice.stop(); Log.d(TAG, "lesson ${lesson.id}: try for 3 stars from step ${com.bigpapituna.sharpears.engine.kid.LessonStars.firstGraded(lesson.steps) + 1}")
            onRetry(com.bigpapituna.sharpears.engine.kid.LessonStars.firstGraded(lesson.steps))
        } else null
    val finishStars = FinishStars(retry, neutral)
    // The world's slots on the finish: the sticker (and the egg) flipping in, and "one more" with a peek at its drop.
    // 2.14: a child who cannot read yet gets pictures (the pup, the stars, the sticker) and one spoken line; a reader the same
    // reveals with their words. Either way one at a time (ui/world/RewardViews.kt KidRewardStage).
    val pictures = kid && !who.reader
    val drop: (@Composable () -> Unit)? = reward?.let { rw -> { LessonDrop(rw, stars = if (kid) lessonStars else null, sampler = deps.sampler.takeIf { deps.prefs.sound },
        say = { line -> deps.voice.speak(line) }, reader = !pictures) } }
    // 2.14 (merge, levels-ages): a grown-up or big kid offered "Try for 3 stars" sees the stars it is about; the calm finish has
    // no drop slot (its rewards are in the sheet), so the stars stand alone in the reward slot.
    val grownStars: (@Composable () -> Unit)? = if (!kid && retry != null && lessonStars > 0) { { com.bigpapituna.sharpears.ui.skin.StarRow(lessonStars, size = 40.dp) } } else null
    // 2.9: once today's goal is met, Tomorrow's surprise under a kid's drop (the last thing they get); a grown-up's tomorrow line
    // rides in their calm finish (it is where they go next).
    val dropSlot: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            drop?.invoke()
            com.bigpapituna.sharpears.ui.TomorrowSlot()
        }
    }
    val nextDrop = remember(reward, next) {
        val n = next
        if (reward == null || n == null) null
        else world?.preview(com.bigpapituna.sharpears.engine.kid.KidPages.dropPlace(n.id))
    }
    // Bedtime on and a grown-up's one more spent with this lesson: the next one sleeps, so no way on into its asleep screen.
    val nextAsleep = anyKid && finished && progress != null && !holdGoodnight &&
        !Bedtime.lessonOpen(true, progress.secondsToday(today), progress.goalMinutes, bedtime.oneMoreOn, today, bedtime.bedtime)
    // The pup's card for the session (the parent brief, 2.4): read while the celebration plays, shown after its button.
    val cardStep = com.bigpapituna.sharpears.ui.brief.rememberKidCardStep(who, ready = anyKid && finished)
    // A kid's finish reads in one order (2.5.0): the stars, the stone's sticker, the drop, something new to wear, then the
    // pup's card (when one is due this session) whichever way they go on, "one more" included.
    val oneMore: (@Composable () -> Unit)? = if (kid && !pictures && next != null && onNext != null && nextDrop != null && !bedtimeNow && !nextAsleep) {
        { com.bigpapituna.sharpears.ui.world.OneMoreButton(nextDrop, onClick = cardStep.then("One more") { voice.stop(); onNext() }, title = next.title) }
    } else null
    if (finished && !goodnight) {
        val kidPrimary = when { bedtimeNow -> "Goodnight, pup"; finishLabel != null -> finishLabel; oneMore != null || nextAsleep -> "Back to the map"; onNext != null -> "Next lesson"; else -> "Done" }
        if (kid) Celebration(
            if (neutral) com.bigpapituna.sharpears.engine.kid.LessonStars.NEUTRAL_TITLE else RunWords.KID_LESSON_TITLE,
            if (neutral) com.bigpapituna.sharpears.engine.kid.LessonStars.NEUTRAL_LINE else RunWords.kidLessonLine(who.name), emptyList(),
            primary = kidPrimary,
            // A child who cannot read yet has one big arrow: on to the next stone when there is one, else the map (2.14).
            onPrimary = cardStep.then(kidPrimary) {
                voice.stop()
                if (bedtimeNow) goodnight = true
                else if (pictures && !nextAsleep && onNext != null) onNext()
                else if (oneMore != null || nextAsleep) onBack() else (onNext ?: onBack)()
            },
            sampler = deps.sampler.takeIf { deps.prefs.sound },
            reward = dropSlot, oneMore = oneMore, picture = pictures,
            clip = if (neutral) null else com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE,
            pose = if (neutral) Pose.STANDING else Pose.LEVEL_UP,      // 2.14: no trophy for a skip
            stars = finishStars,
        ) else {
            // 2.14, a grown-up's calm finish: one reward line, a "See rewards (3)" chip for the one sheet, one Next button. A
            // level-up joins the sheet's queue (and is consumed when the finish goes, so no second screen follows).
            val items = remember(reward, finished) { com.bigpapituna.sharpears.engine.world.RewardQueue.of(reward, progress?.levelUp?.value) }
            val calm = com.bigpapituna.sharpears.ui.CalmFinish(
                com.bigpapituna.sharpears.engine.world.RewardQueue.line(items, (xpAtEnd - xpAtOpen).takeIf { it > 0 }, xpAtEnd),
                items, say = { line -> deps.voice.speak(line) }, onLeave = { progress?.consumeLevelUp() },
                below = { com.bigpapituna.sharpears.ui.TomorrowSlot() })
            // A big kid (grown-up skin) still gets the pup's card for the brief after the button (levels-ages: cardStep).
            Celebration(
                if (neutral) com.bigpapituna.sharpears.engine.kid.LessonStars.NEUTRAL_TITLE else "Lesson complete!", lesson.summary,
                listOf(Stat("${lesson.steps.size}", "Steps"), Stat("%d:%02d".format(java.util.Locale.ROOT, tookSecs / 60, tookSecs % 60), "Time"),
                    Stat("$retries", if (retries == 1) "Retry" else "Retries")),
                // A big kid who reached the goal with bedtime on: the goodnight, as a little kid's finish has it.
                primary = if (bedtimeNow) "Done for today" else if (onNext != null) "Next lesson" else "Done",
                onPrimary = cardStep.then(if (bedtimeNow) "Done for today" else if (onNext != null) "Next lesson" else "Done") {
                    voice.stop(); if (bedtimeNow) goodnight = true else (onNext ?: onBack)()
                },
                secondary = if (onNext != null) "All lessons" else null, onSecondary = { voice.stop(); onBack() },
                sampler = deps.sampler.takeIf { deps.prefs.sound },
                reward = grownStars,
                clip = if (neutral) null else com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE, calm = calm,
                pose = if (neutral) Pose.STANDING else Pose.LEVEL_UP,      // 2.14: no trophy for a skip
                stars = finishStars,
            )
        }
        if (anyKid) com.bigpapituna.sharpears.ui.brief.KidCardStepHost(cardStep, who, say = { voice.speak(it, interrupt = true) })
    }
    // The goodbye after the lesson that reached the goal: shown once today.
    if (goodnight) {
        LaunchedEffect(Unit) { voice.stop(); bedtime.goodnightOn = today; Log.d(TAG, "bedtime: goodnight after ${lesson.id}") }
        Goodbye(who.name, week, onSongs = { voice.stop(); (onSongs ?: onBack)() }, onClose = { voice.stop(); onBack() },
            onJam = onJam?.let { j -> { voice.stop(); j() } }, say = { voice.speak(it, interrupt = true) }, picture = kid && !who.reader,
            songsOpen = songsOpen)
    }
    }
}

/**
 * What a finished lesson dropped: the cards flipping in, then anything more (an egg laid, the dog house grown). A kid
 * sees the lesson's [stars] first (2.2: the same stars land on its stone on the map).
 */
@Composable
internal fun LessonDrop(reward: com.bigpapituna.sharpears.engine.world.Reward, stars: Int? = null, sampler: Sampler? = null, say: ((String) -> Unit)? = null,
                        /** False for a child who cannot read yet: pictures only (2.14). */ reader: Boolean = true) {
    // In order (2.14: one at a time, the same place): the stars stay, then the stone's own sticker (its shape turning into the
    // sticker; on a first lesson already stuck on the map), the drop flipping in, an egg, and something new to wear last.
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        stars?.takeIf { it > 0 }?.let { com.bigpapituna.sharpears.ui.skin.StarRow(it, size = if (reader) 44.dp else 64.dp, sampler = sampler) }
        val items = remember(reward) { com.bigpapituna.sharpears.engine.world.RewardQueue.of(reward, null, starter = reward.starter, placedAt = reward.placedAt) }
        com.bigpapituna.sharpears.ui.world.KidRewardStage(items, reader, say)
    }
}

/** The cheer bar's see-through top (where its pup peeks over the edge): the pop shows through it. */
private val CHEER_BAR_PEEK = 44.dp
/** The least height the pop is given, however much of the step the cheer bar covers. */
private val POP_MIN_HEIGHT = 200.dp

/** A kid's version of the bar label: what to do, no tempo or pass mark. */
private fun kidStateLabel(step: Step): String = when (step.kind) {
    StepKind.LISTEN -> "Listen"
    StepKind.PLAY -> "Your turn"
    StepKind.FROM_MEMORY -> "From memory"
    StepKind.PERFORM, StepKind.RAMP -> "Play along"
    StepKind.PEDAL -> "Pedal with each chord"
    StepKind.DYNAMICS -> "Soft, then loud"
    StepKind.READ -> ""
}

/** What the MIDI-needed state shows: a keyboard plugged in, sending the pedal and how hard each key is struck. */
private val MIDI_NEEDED = com.bigpapituna.sharpears.engine.theory.MidiVisual(listOf(
    com.bigpapituna.sharpears.engine.theory.MidiSignal(null, 0, "Pedal down"),
    com.bigpapituna.sharpears.engine.theory.MidiSignal(60, 40, "Soft, 40"),
    com.bigpapituna.sharpears.engine.theory.MidiSignal(60, 112, "Loud, 112")))

/** The step's fixed hand and mode, shown in the embedded player's bar. */
private fun stateLabel(step: Step): String {
    val ex = step.exercise ?: return ""
    val hand = when (ex.practice) { Practice.BOTH -> "Both hands"; Practice.LEFT -> "Left hand"; Practice.RIGHT -> "Right hand" }
    return when (step.kind) {
        StepKind.LISTEN -> "Listen"
        StepKind.PLAY -> "$hand, the notes wait"
        StepKind.FROM_MEMORY -> "$hand, from memory"
        StepKind.PERFORM -> "$hand, in time, pass at ${ex.pass}%"
        StepKind.RAMP -> "$hand, up to ${ex.bpm.toInt()} bpm"
        StepKind.PEDAL -> "Pedal with each chord"
        StepKind.DYNAMICS -> "Soft, then loud"
        StepKind.READ -> ""
    }
}

/** One dot per step: done steps filled, the current one a short accent bar. */
@Composable
private fun StepDots(count: Int, current: Int, finished: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = if (finished) "All $count steps done" else "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val now = i == current && !finished
            Box(Modifier.height(8.dp).width(if (now) 22.dp else 8.dp).clip(CircleShape).background(
                when {
                    now -> MaterialTheme.colorScheme.primary
                    i < current || finished -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.outlineVariant
                }))
        }
    }
}
