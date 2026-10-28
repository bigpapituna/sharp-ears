package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.SpokenCatalog
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.Stats
import com.bigpapituna.sharpears.engine.hints.MicHearing
import com.bigpapituna.sharpears.engine.hints.NotHeard
import com.bigpapituna.sharpears.engine.tester.TesterEvent
import com.bigpapituna.sharpears.engine.tester.TesterTally
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.15 mic trust, read from the sources: one way to ask for the microphone, the card first, the hint wired where it hears. */
class MicTrustWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(main, f).readText()

    /**
     * Every microphone request goes through rememberMicAsk (the card first). The parent brief's dictation (AskBox) is speech
     * for a typed question, not notes, and keeps Android's own prompt.
     */
    @Test fun everyMicRequestGoesThroughTheCard() {
        val allowed = setOf("MicCard.kt", "AskBox.kt")
        val offenders = main.walkTopDown().filter { it.extension == "kt" && it.name !in allowed && "launch(Manifest.permission.RECORD_AUDIO)" in it.readText().replace("android.Manifest", "Manifest") }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), offenders)
        for (f in listOf("ui/LessonScreen.kt", "ui/family/ProfilePicker.kt", "ui/family/PlacementCheckScreen.kt", "ui/play/PlayCommon.kt",
            "ui/PlayerScreen.kt", "ui/ChordGamesScreen.kt", "ui/ChordsScreen.kt", "ui/ear/KeysPlay.kt", "ui/practice/TunerScreen.kt", "ui/MicSetupScreen.kt"))
            assertTrue("$f asks without the card", "rememberMicAsk(" in src(f))
        // Screens that ask by themselves say so (auto), so a Not now is never nagged; the picker's grown-up is already past the gate.
        for (f in listOf("ui/family/PlacementCheckScreen.kt", "ui/play/PlayCommon.kt")) assertTrue(f, "ask(auto = true" in src(f))
        // A new kid is a new decision: the picker shows the card for each, even after an earlier Not now (never auto).
        val picker = src("ui/family/ProfilePicker.kt")
        assertTrue("rememberMicAsk(gated = false)" in picker && "askMic.ask(child = p.name)" in picker && "ask(auto = true" !in picker)
        val card = src("ui/MicCard.kt")
        assertTrue("auto && mic.micNotNow -> " in card && "behindGate -> gate.guard(" in card && "val behindGate = gated ?: kid" in card)
        assertTrue("mic.micCardSeen = true; mic.micNotNow = true" in card)
        // A kid never gets Android's prompt by itself once the card was answered: only through the gate and the card again.
        assertTrue("auto && mic.micCardSeen -> if (behindGate)" in card)
        // 2.17 (audit H1, H2): the onboarding asks Android for nothing; a grown-up's first playing step asks how they play, with
        // the short card (three lines and More), and its answer (Not now included) is kept as the mic default.
        val onboarding = src("ui/onboarding/OnboardingScreen.kt")
        assertTrue("rememberMicAsk(" !in onboarding && "POST_NOTIFICATIONS" !in onboarding && "RequestPermission" !in onboarding)
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("val micAsk = rememberMicAsk(deps.mic, short = true) { granted -> answerInput(granted) }" in lesson)
        assertTrue("deps.prefs.micDefault = mic" in lesson && "deps.prefs.inputAsked = true" in lesson)
        assertTrue("if (shortCard) MicTrust.shortCard(micFacts(mic)) else MicTrust.card(micFacts(mic), forChild)" in card)
    }

    @Test fun theCardIsReachableFromMicSetupAndThePrivacyPage() {
        val setup = src("ui/MicSetupScreen.kt")
        assertTrue("MicCardInfo(mic)" in setup && "MicTrust.TITLE" in setup && "RoomCheckSection(mic, inject)" in setup)
        val about = src("ui/AboutScreens.kt")
        assertTrue("MicTrust.privacy(facts)" in about && "MicCardInfo(mic)" in about)
        // The old microphone and storage sections are gone; the details after the three stay.
        assertTrue("Heading(\"Microphone\")" !in about && "Heading(\"What is stored\")" !in about && "Heading(\"Internet\")" !in about)
        assertTrue("PrivacyScreen(com.bigpapituna.sharpears.ui.micFacts(mic)" in src("MainActivity.kt"))
        val facts = src("ui/MicCard.kt")
        assertTrue("testerDashboard = com.bigpapituna.sharpears.analytics.Tester.hasKey" in facts)
        assertTrue("sendsReviewClips = mic.canRecord && mic.reporter.enabled" in facts)
    }

    @Test fun theHintIsWiredWhereTheMicHears() {
        val player = src("ui/PlayerScreen.kt")
        assertTrue("HearingWatch(v, mic, voice, transport, kid)" in player && "NotHeardHint(cause, kid" in player)
        assertTrue("MicHearing(mic.hearingLimit)" in player && "hearing?.pitched()" in player)
        assertTrue("onTouch = { notHeard = null; micOn = false" in player)          // the step, not the setting
        assertTrue("BriefEvent.MicNotHeard(it, cause)" in player && "voice.sayInPupVoice(cause.line)" in player)
        assertTrue("!voice.speaking.value && !mic.deaf" in player)
        val kit = src("ui/playground/PlaygroundKit.kt")
        assertTrue("StageHearingWatch(st, hearing, hearingUi, mic, voice)" in kit && "MicHearing(mic.hearingLimit)" in kit)
        assertTrue("hearingUi?.touchOnly != true" in kit)
        assertTrue("NotHeardHint(cause, kid" in src("ui/playground/PlaygroundStage.kt"))
        assertTrue("val hearingLimit = com.bigpapituna.sharpears.engine.hints.HearingLimit()" in src("audio/MicInput.kt"))
        // The level meter starts again with each session (it froze for as long as the session before had run, so the rule read
        // a stale level after every restart of the mic: found on the emulator walk).
        assertEquals(2, Regex("""framesRead = 0\s+lastLevelPost = 0""").findAll(src("audio/MicInput.kt")).count())
    }

    @Test fun theKidLinesAreRecordedLines() {
        val kid = SpokenCatalog.kidLines()
        for (l in MicHearing.KID_LINES) assertTrue(l, SpokenCatalog.normalize(l) in kid)
    }

    @Test fun theEventCarriesTheCauseOnly() {
        val e = BriefEvent.MicNotHeard(1_792_000_000_000, NotHeard.NOISE)
        assertEquals("""{"t":1792000000000,"e":"notHeard","cause":"noise"}""", e.encode())
        assertEquals(e, BriefEvent.decode(e.encode()))
        assertEquals(listOf(TesterEvent("mic_not_heard", mapOf("cause" to "silence"))), TesterTally().eventsFor(BriefEvent.MicNotHeard(1, NotHeard.SILENCE)))
    }

    @Test fun theBriefsRoomSection() {
        assertNull(BriefFormat.notHeardLine(0, 0))
        assertEquals(listOf("No noisy moments. The microphone heard the piano clearly."), BriefFormat.roomLines(Stats()))
        assertEquals("The microphone could not hear the piano twice over noise in the room and once when it heard nothing at all.",
            BriefFormat.notHeardLine(2, 1))
        assertEquals(listOf("The microphone could not hear the piano once over noise in the room."), BriefFormat.roomLines(Stats(notHeardNoise = 1)))
        assertEquals(listOf("1 noisy moment, when the microphone heard the room.", "The microphone could not hear the piano once when it heard nothing at all."),
            BriefFormat.roomLines(Stats(noisyRoom = 1, notHeardQuiet = 1)))
    }
}
