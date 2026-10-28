package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.Ducking
import com.bigpapituna.sharpears.engine.SpokenCatalog
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.family.Audience
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.family.PlainLook
import com.bigpapituna.sharpears.engine.tester.AppEvents
import com.bigpapituna.sharpears.engine.tester.TesterRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.17 onboarding (sharp-ears-audits/onboarding-audit-2026-10-28.md): the first run's rules in engine/nav/FirstRun.kt and the
 * pieces round it (the tester notice, the Plain look's offer, first_note, the duck).
 */
class FirstRunTest {
    private val sit get() = COURSE.flatMap { it.lessons }.first { it.id == FirstRun.FIRST_LESSON }

    // ---- H1: how they play, asked in context ----

    @Test fun aGrownUpIsAskedHowTheyPlayOnTheirFirstPlayingStepOnly() {
        assertTrue(FirstRun.asksInput(kid = false, asked = false, midi = false, kind = StepKind.PLAY))
        for (k in listOf(StepKind.FROM_MEMORY, StepKind.PERFORM, StepKind.RAMP)) assertTrue(FirstRun.asksInput(false, false, false, k))
        // Not on a step where nothing is played, nor one that needs MIDI anyway.
        for (k in listOf(StepKind.READ, StepKind.LISTEN, StepKind.PEDAL, StepKind.DYNAMICS)) assertFalse(k.name, FirstRun.asksInput(false, false, false, k))
        // Once answered, never again.
        assertFalse(FirstRun.asksInput(kid = false, asked = true, midi = false, kind = StepKind.PLAY))
    }

    @Test fun aMidiKeyboardSkipsTheQuestionAndKidsKeepTheirParentGatedPath() {
        assertFalse(FirstRun.asksInput(kid = false, asked = false, midi = true, kind = StepKind.PLAY))
        assertFalse(FirstRun.asksInput(kid = true, asked = false, midi = false, kind = StepKind.PLAY))
    }

    @Test fun howSomeonePlaysIsMidiThenTheMicThenTheKeys() {
        assertEquals(FirstRun.Input.MIDI, FirstRun.inputOf(midi = true, micOn = true))
        assertEquals(FirstRun.Input.MIC, FirstRun.inputOf(midi = false, micOn = true))
        assertEquals(FirstRun.Input.TOUCH, FirstRun.inputOf(midi = false, micOn = false))
        assertEquals(listOf("mic", "midi", "touch"), FirstRun.Input.entries.map { it.wire })
    }

    @Test fun theCheckAsksForTheMicUnlessTheKeysOrMidiWereChosen() {
        assertTrue(FirstRun.checkAsksMic(useMic = false, kid = true, inputAsked = true, midi = false))
        assertTrue(FirstRun.checkAsksMic(useMic = true, kid = false, inputAsked = true, midi = false))
        assertTrue(FirstRun.checkAsksMic(useMic = false, kid = false, inputAsked = false, midi = false))
        assertFalse(FirstRun.checkAsksMic(useMic = false, kid = false, inputAsked = true, midi = false))     // chose the screen's keys
        assertFalse(FirstRun.checkAsksMic(useMic = false, kid = false, inputAsked = false, midi = true))     // a MIDI player
    }

    @Test fun theQuestionNamesMiddleCAndSaysMidiConnectsByItself() {
        assertEquals("Got a piano or keyboard here?", FirstRun.INPUT_TITLE)
        assertEquals("Play middle C and I'll listen.", FirstRun.INPUT_LINE)
        assertEquals("Use my piano (mic)", FirstRun.USE_MIC)
        assertEquals("Use screen keys", FirstRun.USE_KEYS)
        assertTrue("automatically" in FirstRun.MIDI_LINE)
    }

    // ---- H2: the nudge after the first finished lesson ----

    @Test fun theNudgeIsTheOwnersOnceAfterALessonAndOnlyYesAllowedTurnsTheReminderOn() {
        assertTrue(FirstRun.asksNudge(owner = true, kid = false, asked = false, reminderOn = false))
        assertFalse(FirstRun.asksNudge(owner = false, kid = false, asked = false, reminderOn = false))   // another grown-up: one alarm per phone
        assertFalse(FirstRun.asksNudge(owner = true, kid = true, asked = false, reminderOn = false))
        assertFalse(FirstRun.asksNudge(owner = true, kid = false, asked = true, reminderOn = false))     // Yes or Not now, once
        assertFalse(FirstRun.asksNudge(owner = true, kid = false, asked = false, reminderOn = true))
        assertTrue(FirstRun.reminderAfter(FirstRun.NudgeResult.GRANTED))
        assertFalse(FirstRun.reminderAfter(FirstRun.NudgeResult.DENIED))
        assertFalse(FirstRun.reminderAfter(FirstRun.NudgeResult.NOT_NOW))
        assertEquals("Want a nudge tomorrow at 6:30 PM?", FirstRun.nudgeLine("6:30 PM"))
        assertEquals(listOf("granted", "denied", "not_now"), FirstRun.NudgeResult.entries.map { it.wire })
    }

    // ---- M2: the layout chooser and the tour wait ----

    @Test fun aBeginnerIsSimpleSilentlyUntilTwoLessonsThenAsked() {
        for (lv in listOf(Level.NEVER, Level.A_LITTLE)) {
            assertFalse(FirstRun.asksLayout(kid = false, chosen = null, level = lv, lessonsPlayed = 0, daysPlayed = 1))
            assertFalse(FirstRun.asksLayout(kid = false, chosen = null, level = lv, lessonsPlayed = 1, daysPlayed = 1))
            assertTrue(FirstRun.asksLayout(kid = false, chosen = null, level = lv, lessonsPlayed = 2, daysPlayed = 1))
            assertEquals(Layout.SIMPLE, Simple.layoutOf(false, null, lv))
        }
    }

    @Test fun someAndALotAreAskedWhereTheyAlwaysWereAndAChoiceOrAKidIsNeverAsked() {
        for (lv in listOf(Level.SOME, Level.A_LOT)) assertTrue(FirstRun.asksLayout(false, null, lv, lessonsPlayed = 0, daysPlayed = 0))
        assertFalse(FirstRun.asksLayout(false, Layout.FULL, Level.SOME, 0, 0))
        assertFalse(FirstRun.asksLayout(true, null, Level.NEVER, 5, 5))
    }

    @Test fun theTourWaitsForTwoLessonsOrASecondDay() {
        assertFalse(FirstRun.tourReady(lessonsPlayed = 0, daysPlayed = 1))
        assertFalse(FirstRun.tourReady(lessonsPlayed = 1, daysPlayed = 1))
        assertTrue(FirstRun.tourReady(lessonsPlayed = 2, daysPlayed = 1))
        assertTrue(FirstRun.tourReady(lessonsPlayed = 0, daysPlayed = 2))
    }

    @Test fun theTourShowsNoBandCardToAPlanWithoutChords() {
        val without = FirstRun.tour(plain = false, chords = false).map { it.art }
        assertFalse(Welcome.TourArt.BAND in without)
        assertEquals(4, without.size)
        assertEquals(Welcome.TOUR, FirstRun.tour(plain = false, chords = true))
        assertEquals(Welcome.tour(true), FirstRun.tour(plain = true, chords = true))
        assertFalse(Welcome.TourArt.STREAK in FirstRun.tour(plain = true, chords = false).map { it.art })
    }

    // ---- H3: the first lesson starts with the note ----

    @Test fun aNeverGrownUpHearsAndPlaysMiddleCBeforeThePosture() {
        val l = FirstRun.lessonFor(sit, Level.NEVER, kid = false)
        assertEquals(sit.steps.size + 2, l.steps.size)
        assertEquals(StepKind.LISTEN, l.steps[0].kind)
        assertEquals(FirstRun.HEAR_C, l.steps[0].text)
        assertEquals(StepKind.PLAY, l.steps[1].kind)
        assertEquals(FirstRun.PLAY_C, l.steps[1].text)
        assertEquals("C4:2", l.steps[1].exercise?.right)
        assertEquals(StepKind.READ, l.steps[2].kind)              // posture second
        assertEquals(sit.steps, l.steps.drop(2))
        assertEquals(1, FirstRun.firstNoteStep(sit, Level.NEVER, kid = false))
    }

    @Test fun everyoneElseKeepsTheLessonAsItIs() {
        assertSame(sit, FirstRun.lessonFor(sit, Level.A_LITTLE, kid = false))
        assertSame(sit, FirstRun.lessonFor(sit, Level.NEVER, kid = true))            // a big kid on Basics
        val other = COURSE.flatMap { it.lessons }.first { it.id == "find-c" }
        assertSame(other, FirstRun.lessonFor(other, Level.NEVER, kid = false))
        assertEquals(-1, FirstRun.firstNoteStep(other, Level.NEVER, kid = false))
    }

    @Test fun skipToPlayingGoesToTheNextStepWhereTheyPlay() {
        val l = FirstRun.lessonFor(sit, Level.NEVER, kid = false)
        // From the posture (step 3): past the listening step to the Play step.
        assertEquals(4, FirstRun.skipToPlaying(l.steps, 2))
        assertEquals(2, FirstRun.skipToPlaying(sit.steps, 0))
        assertNull(FirstRun.skipToPlaying(sit.steps, sit.steps.lastIndex))
    }

    @Test fun theLaneSaysWaitUntilTheFirstNoteArrives() {
        assertTrue(FirstRun.waitCue(waitMode = true, playing = true, rightSoFar = 0, waiting = false))
        assertFalse(FirstRun.waitCue(waitMode = true, playing = true, rightSoFar = 0, waiting = true))    // at the line: play now
        assertFalse(FirstRun.waitCue(waitMode = true, playing = true, rightSoFar = 1, waiting = false))
        assertFalse(FirstRun.waitCue(waitMode = false, playing = true, rightSoFar = 0, waiting = false))
        assertFalse(FirstRun.waitCue(waitMode = true, playing = false, rightSoFar = 0, waiting = false))
    }

    @Test fun theNewSpokenLinesAreInTheVoiceCatalog() {
        val all = SpokenCatalog.all()
        assertTrue(FirstRun.HEAR_C in all)
        assertTrue(FirstRun.PLAY_C in all)
    }

    // ---- M3: the extra waits for the first passed step of a profile's first lesson ----

    @Test fun theExtraIsHeldInTheFirstLessonUntilAStepIsPassed() {
        assertTrue(FirstRun.holdsExtra(firstLesson = true, passedSteps = 0))
        assertFalse(FirstRun.holdsExtra(firstLesson = true, passedSteps = 1))
        assertFalse(FirstRun.holdsExtra(firstLesson = false, passedSteps = 0))
    }

    // ---- H4: who it is for ----

    @Test fun theOwnerIsAskedWhoItIsForUntilThereIsAKid() {
        assertTrue(FirstRun.asksWho(owner = true, hasKid = false))
        assertFalse(FirstRun.asksWho(owner = true, hasKid = true))
        assertFalse(FirstRun.asksWho(owner = false, hasKid = false))
    }

    @Test fun onlyTheFirstKidIsAddedWithoutTheGate() {
        assertFalse(FirstRun.addKidNeedsGate(hasKid = false))
        assertTrue(FirstRun.addKidNeedsGate(hasKid = true))
    }

    // ---- M1: Help Roy test's notice ----

    @Test fun theTesterNoticeNeverSitsOverTheFirstRunCardsAndComesAfterTheWelcome() {
        assertFalse(TesterRules.showsNotice(pending = true, kid = false, onboarding = true))
        assertTrue(TesterRules.showsNotice(pending = true, kid = false, onboarding = false))
        assertTrue(TesterRules.noticeAfterWelcome(pending = true, step = 0))
        assertFalse(TesterRules.noticeAfterWelcome(pending = true, step = 1))
        assertFalse(TesterRules.noticeAfterWelcome(pending = false, step = 0))
    }

    // ---- M6: the Plain look's offer at the end of session 2 ----

    @Test fun thePlainLookOfferWaitsForTheSecondDay() {
        assertFalse(PlainLook.offerDue(Audience.GROWN_UP, stored = false, offeredBefore = false, moment = PlainLook.Moment.LESSON_FINISHED, daysPlayed = 1))
        assertTrue(PlainLook.offerDue(Audience.GROWN_UP, stored = false, offeredBefore = false, moment = PlainLook.Moment.LESSON_FINISHED, daysPlayed = 2))
    }

    // ---- M5: analytics ----

    @Test fun firstNoteGoesOnceWithTheInputAndTheSecondsSinceTheProfileFirstOpened() {
        assertTrue(FirstRun.sendsFirstNote(sent = false))
        assertFalse(FirstRun.sendsFirstNote(sent = true))
        assertEquals(42L, FirstRun.secondsSinceFirstOpen(firstOpenMs = 1_000_000L, nowMs = 1_042_500L))
        assertEquals(0L, FirstRun.secondsSinceFirstOpen(firstOpenMs = 0L, nowMs = 5_000L))
        assertEquals(0L, FirstRun.secondsSinceFirstOpen(firstOpenMs = 9_000L, nowMs = 5_000L))
        val e = AppEvents.firstNote("mic", 42)
        assertEquals(AppEvents.FIRST_NOTE, e.name)
        assertEquals(mapOf("input" to "mic", "seconds_since_first_open" to 42L), e.props)
        assertEquals(AppEvents.CATALOG[AppEvents.FIRST_NOTE]!!.toSet(), e.props.keys)
        assertTrue(AppEvents.INPUT_MODE in AppEvents.SUPER)
    }

    @Test fun theOnboardingCarriesItsAnswersAndTheNewEventsAreCatalogued() {
        val who = AppEvents.onboardingStep(0, completed = true, who = "child")
        assertEquals("welcome", who.props["step"]); assertEquals("child", who.props["who"])
        assertEquals("never", AppEvents.onboardingStep(1, completed = true, level = "NEVER").props["level"])
        assertEquals(10, AppEvents.onboardingStep(2, completed = true, goal = 10).props["goal"])
        assertEquals(listOf("welcome", "experience", "goal"), AppEvents.ONBOARDING_STEPS)
        assertEquals("touch", AppEvents.inputChosen(mic = false).props["answer"])
        assertEquals("not_now", AppEvents.notificationAsk("not_now").props["result"])
        for (k in listOf(AppEvents.FIRST_NOTE, AppEvents.INPUT_CHOSEN, AppEvents.NOTIFICATION_ASK))
            assertTrue(AppEvents.CATALOG.getValue(k).none(AppEvents::denied))
    }

    // ---- item 10: the notes ducked under a spoken line ----

    @Test fun theNotesGoDownAboutFourDecibelsUnderALineAndBackAfter() {
        assertEquals(1f, Ducking.gain(false), 0f)
        assertEquals(0.631f, Ducking.gain(true), 0.001f)
        assertEquals(0.5f * 0.631f, Ducking.volume(0.5f, true), 0.001f)
        assertEquals(1f, Ducking.volume(1.4f, false), 0f)
    }
}
