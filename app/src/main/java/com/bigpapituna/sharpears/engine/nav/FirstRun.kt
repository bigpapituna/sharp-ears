package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.family.Level

/**
 * 2.17 onboarding (sharp-ears-audits/onboarding-audit-2026-10-28.md, findings H1 to H4, M1 to M6, L1 to L4): the first run's
 * rules, one per question, pure (FirstRunTest). The screens ask them.
 *
 * - Who it is for (H4): the first card asks the phone's owner "Who is this for?" while the phone has no kid; "My child" goes
 *   straight to the add-a-kid form, without the parent gate for the first kid (there is nothing to protect yet).
 * - How they play (H1): the onboarding no longer asks. A grown-up's first playing step asks in context ("Got a piano or
 *   keyboard here?"), unless a MIDI keyboard is plugged in (MIDI is found by itself). Kids keep their parent-gated mic path.
 * - The reminder (H2): nothing is asked in the onboarding; the owner's first finished lesson offers a nudge tomorrow, and only
 *   Yes asks Android for notifications.
 * - The first lesson (H3): a grown-up who never played starts by hearing middle C and playing it, posture second.
 * - The layout chooser and the tour (M2): a beginner is Simple, silently, until two lessons are played; the tour waits as long.
 */
object FirstRun {
    // ---- who it is for (H4) ----

    const val WHO_QUESTION = "Who is this for?"
    const val WHO_ME = "Me"
    const val WHO_CHILD = "My child"

    /** The first card asks who it is for: the phone's owner, while no kid is on the phone (later kids come from Family). */
    fun asksWho(owner: Boolean, hasKid: Boolean): Boolean = owner && !hasKid

    /**
     * Adding a kid asks the parent gate once a kid is on the phone (the kid could be the one tapping). The first kid is made by
     * the grown-up setting the phone up, with nothing yet to protect: no gate.
     */
    fun addKidNeedsGate(hasKid: Boolean): Boolean = hasKid

    // ---- how they play (H1) ----

    const val INPUT_TITLE = "Got a piano or keyboard here?"
    const val INPUT_LINE = "Play middle C and I'll listen."
    const val INPUT_LINE_ANY = "Play along and I'll listen."
    const val USE_MIC = "Use my piano (mic)"
    const val USE_KEYS = "Use screen keys"
    const val MIDI_LINE = "MIDI keyboards connect automatically."

    /** The steps where the player plays (a reading or listening step has nothing to hear; pedal and dynamics need MIDI anyway). */
    fun plays(kind: StepKind): Boolean = kind == StepKind.PLAY || kind == StepKind.FROM_MEMORY || kind == StepKind.PERFORM || kind == StepKind.RAMP

    /**
     * The in-context question shows on this step: a grown-up ([kid] false: kids and big kids keep the parent-gated mic card)
     * who was never asked ([asked]) on a step where they play, with no MIDI keyboard plugged in ([midi]: it is found by itself,
     * so a MIDI player is never asked about the mic).
     */
    fun asksInput(kid: Boolean, asked: Boolean, midi: Boolean, kind: StepKind): Boolean = !kid && !asked && !midi && plays(kind)

    /** How someone plays now, for analytics (`input_mode`, `first_note`'s `input`). */
    enum class Input { MIC, MIDI, TOUCH; val wire: String get() = name.lowercase() }

    /** A MIDI keyboard plugged in wins; else the mic when it is on and allowed; else the screen's keys. */
    fun inputOf(midi: Boolean, micOn: Boolean): Input = when { midi -> Input.MIDI; micOn -> Input.MIC; else -> Input.TOUCH }

    /** "Let's hear you play" (Some, A lot) asks for the mic: a kid always; a grown-up who said mic, or was never asked and has no MIDI. */
    fun checkAsksMic(useMic: Boolean, kid: Boolean, inputAsked: Boolean, midi: Boolean): Boolean =
        kid || useMic || (!inputAsked && !midi)

    // ---- the reminder (H2) ----

    const val NUDGE_YES = "Yes"
    const val NUDGE_NO = "Not now"

    /** "Want a nudge tomorrow at 6:30 PM?" ([time] as the phone shows times). */
    fun nudgeLine(time: String): String = "Want a nudge tomorrow at $time?"

    /**
     * The nudge is offered under a finished lesson: the phone's owner (the reminder is one alarm per phone), a grown-up, never
     * asked before ([asked]: Yes or Not now, once), with the reminder not on already.
     */
    fun asksNudge(owner: Boolean, kid: Boolean, asked: Boolean, reminderOn: Boolean): Boolean = owner && !kid && !asked && !reminderOn

    /** What the nudge's answer does: Yes asks Android when notifications are not allowed yet; the reminder is on only once allowed. */
    enum class NudgeResult { GRANTED, DENIED, NOT_NOW; val wire: String get() = name.lowercase() }

    fun reminderAfter(result: NudgeResult): Boolean = result == NudgeResult.GRANTED

    // ---- the layout chooser and the tour (M2) ----

    /** Lessons (or days) played before a beginner is asked for a layout and anyone sees the tour. */
    const val AFTER_LESSONS = 2

    /** The tour shows once two lessons were played, or on the second day played (Some and A lot start on songs). */
    fun tourReady(lessonsPlayed: Int, daysPlayed: Int): Boolean = lessonsPlayed >= AFTER_LESSONS || daysPlayed >= AFTER_LESSONS

    /**
     * The launch chooser asks a grown-up who never chose ([Simple.asks]): Some and A lot where it always did (after the first
     * run), a beginner (Never, A little: Simple until then, silently, Simple.default) only once [tourReady].
     */
    fun asksLayout(kid: Boolean, chosen: Layout?, level: Level, lessonsPlayed: Int, daysPlayed: Int): Boolean =
        Simple.asks(kid, chosen) && (!DayOne.beginner(level) || tourReady(lessonsPlayed, daysPlayed))

    /**
     * The tour's cards for this player: the Plain look's four or the five (Welcome.tour), without "Play songs by chords with a
     * band" while their plan has no chords yet ([chords]: DayOne.chordsOpen), so no card shows what they do not have.
     */
    fun tour(plain: Boolean, chords: Boolean): List<Welcome.Card> =
        Welcome.tour(plain).filter { chords || it.art != Welcome.TourArt.BAND }

    // ---- the first lesson (H3) ----

    /** The course's first lesson, "Sit, then shape your hand". */
    const val FIRST_LESSON = "sit"

    /** Hear middle C, then play it: one note each, before the posture. Spoken lines, so they are in the voice catalog. */
    const val HEAR_C = "Listen. This is middle C."
    const val PLAY_C = "Your turn. Play middle C."
    private val ONE_C = Exercise(right = "C4:2", bpm = 60.0, practice = Practice.RIGHT)
    val FIRST_NOTE_STEPS: List<Step> = listOf(Step(StepKind.LISTEN, HEAR_C, ONE_C), Step(StepKind.PLAY, PLAY_C, ONE_C))

    /** The cheer bar's title when the first note is played: the big moment. */
    const val FIRST_NOTE_TITLE = "Your first note!"

    /** A grown-up who never played starts the first lesson with the note ([level] the profile's; kids keep theirs). */
    fun firstNoteFirst(lessonId: String, level: Level, kid: Boolean): Boolean = lessonId == FIRST_LESSON && level == Level.NEVER && !kid

    /** [lesson] as this player gets it: the first lesson with middle C heard and played first, posture second; any other unchanged. */
    fun lessonFor(lesson: CourseLesson, level: Level, kid: Boolean): CourseLesson =
        if (firstNoteFirst(lesson.id, level, kid)) lesson.copy(steps = FIRST_NOTE_STEPS + lesson.steps) else lesson

    /** The step that plays the first note in [lesson] as [lessonFor] built it (its cheer bar says [FIRST_NOTE_TITLE]); -1 for none. */
    fun firstNoteStep(lesson: CourseLesson, level: Level, kid: Boolean): Int = if (firstNoteFirst(lesson.id, level, kid)) 1 else -1

    /** "Skip to playing" on a reading step (a grown-up's): the next step after [index] where they play, or null for none. */
    fun skipToPlaying(steps: List<Step>, index: Int): Int? =
        (index + 1 until steps.size).firstOrNull { steps[it].exercise != null && plays(steps[it].kind) }

    const val SKIP_TO_PLAYING = "Skip to playing"

    /** The lane's cue while the first note falls toward the line, and the hint after a key played too early. */
    const val WAIT_CUE = "Wait for the note to reach the line"
    const val EARLY_HINT = "Not yet. Play it when it reaches the line."

    /**
     * The lane shows [WAIT_CUE] while a run waits for its first note to arrive: Wait mode, playing, no right note yet in the run
     * ([rightSoFar] 0), the note still on its way (not waiting at the line).
     */
    fun waitCue(waitMode: Boolean, playing: Boolean, rightSoFar: Int, waiting: Boolean): Boolean = waitMode && playing && rightSoFar == 0 && !waiting

    // ---- the coach (afterdark builds; M3) ----

    /** In a profile's first lesson ([firstLesson]: none played yet) the extra's lines wait until a step is passed. */
    fun holdsExtra(firstLesson: Boolean, passedSteps: Int): Boolean = firstLesson && passedSteps == 0

    // ---- analytics (M5) ----

    /** `first_note`'s seconds since the profile first opened ([firstOpenMs] 0: unknown, then 0), never negative. */
    fun secondsSinceFirstOpen(firstOpenMs: Long, nowMs: Long): Long = if (firstOpenMs <= 0) 0 else ((nowMs - firstOpenMs) / 1000).coerceAtLeast(0)

    /** `first_note` goes once per profile: the first right note heard ([sent]: already gone). */
    fun sendsFirstNote(sent: Boolean): Boolean = !sent

    // ---- "You're all set" (L1, L4) ----

    /** Skip on the first card: "You're all set" as a beginner, with the first lesson's button (L4). */
    val SKIP_LEVEL: Level = Level.NEVER
}
