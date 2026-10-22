package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Hands
import com.bigpapituna.sharpears.engine.brief.RunMode
import com.bigpapituna.sharpears.engine.brief.ToyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tester analytics' rules (CLAUDE.md "Tester analytics"): when anything may start, and what an event may carry. */
class TesterRulesTest {
    @Test fun theSdkStartsOnlyWithTheSdkTheKeyAndTheSwitch() {
        for (sdk in listOf(true, false)) for (key in listOf(true, false)) for (on in listOf(true, false))
            assertEquals("sdk=$sdk key=$key on=$on", sdk && key && on, TesterRules.sdkMayStart(sdk, key, on))
    }

    @Test fun theRowsShowOnlyWhereTheyCanDoSomething() {
        assertTrue(TesterRules.rowShown(hasKey = true, debuggable = false))
        assertTrue("Roy's debug build shows it, saying nothing is sent", TesterRules.rowShown(hasKey = false, debuggable = true))
        assertFalse("a release without a key has no row", TesterRules.rowShown(hasKey = false, debuggable = false))
        assertFalse(TesterRules.diaryShown(""))
        assertFalse(TesterRules.diaryShown("  "))
        assertTrue(TesterRules.diaryShown("roy@example.com"))
    }

    @Test fun theTesterLogRecordsOnlyWhileASwitchIsOn() {
        assertFalse(TesterRules.records(switchOn = false, diaryOn = false, diaryTo = "roy@example.com"))
        assertTrue(TesterRules.records(switchOn = true, diaryOn = false, diaryTo = ""))
        assertTrue(TesterRules.records(switchOn = false, diaryOn = true, diaryTo = "roy@example.com"))
        assertFalse("a diary switch left on in a build without the address records nothing", TesterRules.records(false, true, ""))
    }

    @Test fun screensAreRouteNamesWithoutStepsOrImportedFileNames() {
        assertEquals("home:LEARN", TesterRules.screenOf("home:LEARN"))
        assertEquals("lesson:find-c", TesterRules.screenOf("home:LEARN/lesson:2:find-c"))
        assertEquals("song:twinkle", TesterRules.screenOf("home:SONGS/song:twinkle"))
        assertEquals("song:user-song", TesterRules.screenOf("home:SONGS/song:user-mias-recital"))
        assertEquals("settings", TesterRules.screenOf("home:YOU/settings"))
        assertEquals("popsong:let-it-be", TesterRules.screenOf("home:CHORDS/popsong:let-it-be:-2:HALVES:72"))
        assertEquals("kidchords", TesterRules.screenOf("home:SONGS/kidsongs/kidchords"))
        assertEquals("home:SONGS/song:user-song", TesterRules.safeRoute("home:SONGS/song:user-mias-recital"))
        assertEquals("twinkle", TesterRules.safeId("twinkle"))
        assertNull(TesterRules.safeId(null))
    }

    @Test fun aLessonStartsWhenItsRouteFirstShows() {
        assertEquals(TesterEvent("lesson_started", mapOf("lesson" to "find-c")), TesterRules.lessonStarted("home:LEARN", "home:LEARN/lesson:0:find-c"))
        assertNull("the next step is the same lesson", TesterRules.lessonStarted("home:LEARN/lesson:0:find-c", "home:LEARN/lesson:1:find-c"))
        assertEquals("find-d", TesterRules.lessonStarted("home:LEARN/lesson:3:find-c", "home:LEARN/lesson:0:find-d")!!.props["lesson"])
        assertNull(TesterRules.lessonStarted(null, "home:LEARN"))
        assertEquals("find-c", TesterRules.lessonStarted(null, "home:LEARN/lesson:0:find-c")!!.props["lesson"])
    }

    @Test fun profilesAreAKindNeverAName() {
        assertEquals("kid", TesterRules.profileKind(true))
        assertEquals("adult", TesterRules.profileKind(false))
    }

    /** The log lines data/BriefAi.kt and engine/brief/email/EmailSender.kt write today. */
    @Test fun aiAndEmailLinesBecomeOneWordResults() {
        assertEquals("ai" to "reply", TesterRules.resultOf("ai: reply in 812 ms (attempt 1, 900 chars)"))
        assertEquals("ai" to "NETWORK", TesterRules.resultOf("ai: NETWORK after 20 ms (attempt 1): UnknownHostException"))
        assertEquals("ai" to "timeout", TesterRules.resultOf("ai: no reply within 3000 ms"))
        assertEquals("ai" to "cache", TesterRules.resultOf("ai: WEEK from the cache"))
        assertEquals("ai" to "not_asked", TesterRules.resultOf("ai: DAY not asked: nothing happened in it"))
        assertEquals("ai" to "unusable", TesterRules.resultOf("ai: DAY reply not usable"))
        assertNull("a switch is not a result", TesterRules.resultOf("ai: briefs off (parent corner)"))
        assertEquals("email" to "sent", TesterRules.resultOf("email: DAILY 2026-10-21 for p1 sent (AI, html 40 KB, pdf 80 KB)"))
        assertEquals("email" to "NO_KEY", TesterRules.resultOf("email: DAILY 2026-10-21 for p1 not sent: NO_KEY"))
        assertEquals("email" to "NETWORK", TesterRules.resultOf("email: WEEKLY 2026-10-18 for p1 failed: NETWORK: UnknownHostException"))
        assertNull("the transport's own line is not counted twice", TesterRules.resultOf("email: sent in 300 ms (attempt 1, 120 KB)"))
        assertNull(TesterRules.resultOf("email: job (app start): 0 sent, 0 with an address, next look never"))
        assertNull(TesterRules.resultOf("brief: p1 {}"))
    }

    @Test fun aSafeLineHasNoAddressNoNameAndOneLine() {
        val line = TesterRules.safeLine("Mia's\nbutton  did nothing, write to roy.younes@example.com  please", names = listOf("Mia", "Theo"))
        assertEquals("[name]'s button did nothing, write to [address] please", line)
        assertEquals(20, TesterRules.safeLine("x".repeat(400), max = 20).length)
        assertTrue("a name inside another word stays", TesterRules.safeLine("Miami", listOf("Mia")) == "Miami")
    }

    @Test fun briefEventsBecomeStageLessonSongAndToyEvents() {
        val t = TesterTally()
        assertTrue("one easy try: nothing", t.eventsFor(BriefEvent.LessonStep(1, "find-c", 0, "PLAY", tries = 1)).isEmpty())
        val stuck = t.eventsFor(BriefEvent.LessonStep(2, "find-c", 1, "PLAY", tries = 4, letGo = true, hintsUsed = listOf(1, 3)))
        assertEquals(listOf("stage_stuck", "hint_step"), stuck.map { it.name })
        assertEquals(true, stuck[0].props["let_go"]); assertEquals(4, stuck[0].props["tries"])
        assertEquals(2, stuck[1].props["hints"]); assertEquals(3, stuck[1].props["deepest"])
        assertEquals("three tries is stuck", "stage_stuck", t.eventsFor(BriefEvent.LessonStep(3, "find-c", 2, "PLAY", tries = 3)).single().name)
        val done = t.eventsFor(BriefEvent.LessonDone(4, "find-c", stars = 2, durationMs = 61_000)).single()
        assertEquals(TesterEvent("lesson_finished", mapOf("lesson" to "find-c", "stars" to 2, "tries" to 8, "let_go" to 1, "seconds" to 61L)), done)
        assertEquals("the tally starts again", 0, t.eventsFor(BriefEvent.LessonDone(5, "find-c", stars = 3)).single().props["tries"])
        val song = t.eventsFor(BriefEvent.SongRun(6, "user-mias-recital", RunMode.PERFORM, Hands.BOTH, accuracy = 87, stars = 2)).single()
        assertEquals(mapOf("song" to "user-song", "mode" to "perform", "stars" to 2, "accuracy" to 87, "duet" to false), song.props)
        assertEquals(TesterEvent("toy_opened", mapOf("toy" to "jam", "seconds" to 95L)), t.eventsFor(BriefEvent.ToyOpened(7, ToyKind.JAM, 95_000)).single())
        assertTrue(t.eventsFor(BriefEvent.GoalReached(8, 10)).isEmpty())
    }

    /** Nothing an event carries is a free text: only ids, words of the app's own, numbers and booleans. */
    @Test fun eventPropertiesAreIdsAndNumbers() {
        val t = TesterTally()
        val all = t.eventsFor(BriefEvent.LessonStep(1, "pond-3", 2, "PLAY", tries = 5, hintsUsed = listOf(2))) +
            t.eventsFor(BriefEvent.LessonDone(2, "pond-3", 1)) + t.eventsFor(BriefEvent.SongRun(3, "ode", RunMode.WAIT, Hands.RIGHT, stars = 1))
        for (e in all) for ((k, v) in e.props) assertTrue("${e.name}.$k = $v", v is Number || v is Boolean || (v is String && Regex("[a-z0-9:_-]+").matches(v)) || k == "kind")
    }
}
