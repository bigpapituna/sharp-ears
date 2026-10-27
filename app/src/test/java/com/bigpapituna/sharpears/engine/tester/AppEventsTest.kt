package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Hands
import com.bigpapituna.sharpears.engine.brief.RunMode
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.hints.NotHeard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.15, the event taxonomy (engine/tester/AppEvents.kt, analytics/EVENTS.md): no property key holds a denied word (name,
 * email, title, text, file, path), every event the code sends is in the catalog and in EVENTS.md, a song of the person's own
 * goes out as my_song, and the brief's events become the dashboard's (steps, songs, charts, the arcade, sessions, settings).
 */
class AppEventsTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")

    @Test fun noPropertyKeyHoldsADeniedWord() {
        for ((event, keys) in AppEvents.CATALOG) for (k in keys) assertFalse("$event.$k", AppEvents.denied(k))
        for (k in AppEvents.SUPER) assertFalse(k, AppEvents.denied(k))
        for (k in listOf("name", "email", "title", "text", "file", "path", "song_title", "file_name", "fileName", "kid-name", "username", "email_address",
            "filepath", "Path", "subtitle"))
            assertTrue(k, AppEvents.denied(k))
        for (k in listOf("profile_kind", "minutes_bucket", "lesson", "accuracy_bucket", "where", "route", "streak", "context", "nickel"))
            assertFalse(k, AppEvents.denied(k))
    }

    @Test fun namesAndKeysAreSnakeCase() {
        for ((event, keys) in AppEvents.CATALOG) {
            assertTrue(event, Regex("\\$?[a-z][a-z0-9_]*").matches(event))
            for (k in keys) assertTrue("$event.$k", Regex("[a-z][a-z0-9_]*").matches(k))
        }
    }

    /** Every event the app's code names (TesterEvent("..."), Tester.event("...")) is in the catalog: nothing goes out unlisted. */
    @Test fun everyEventTheCodeSendsIsInTheCatalog() {
        val sent = Regex("""(?:TesterEvent|Tester\.event|analytics\.Tester\.event)\(\s*"([^"]+)"""")
        val found = main.walkTopDown().filter { it.extension == "kt" }.flatMap { f -> sent.findAll(f.readText()).map { it.groupValues[1] to f.name } }.toList()
        assertTrue("the scan finds the events", found.size >= 10)
        for ((name, file) in found) assertTrue("$name ($file) is not in AppEvents.CATALOG", name in AppEvents.CATALOG)
    }

    /** analytics/EVENTS.md is the table Roy reads: every event and every property is in it. */
    @Test fun theEventsDocListsEveryEventAndProperty() {
        val doc = File(main, "analytics/EVENTS.md").readText()
        for ((event, keys) in AppEvents.CATALOG) {
            assertTrue("$event is not in EVENTS.md", "`$event`" in doc)
            for (k in keys) assertTrue("$event.$k is not in EVENTS.md", "`$k`" in doc)
        }
        for (k in AppEvents.SUPER) assertTrue("$k is not in EVENTS.md", "`$k`" in doc)
        for (f in Flags.ALL) assertTrue("$f is not in EVENTS.md", "`$f`" in doc)
        assertFalse("no em or en dashes", '—' in doc || '–' in doc)
    }

    /** The builders only use keys the catalog lists for their event. */
    @Test fun theBuildersKeepToTheCatalog() {
        val events = listOf(
            AppEvents.appOpen(true), AppEvents.sessionLength(61_000), AppEvents.practiceSession(600, 3),
            AppEvents.lessonStepPassed(BriefEvent.LessonStep(1, "find-c", 2, "PLAY", tries = 2, stars = 3))!!,
            AppEvents.songRun(BriefEvent.SongRun(1, "twinkle", RunMode.PERFORM, Hands.RIGHT, accuracy = 91, stars = 2, duet = true)),
            AppEvents.songRun(BriefEvent.SongRun(1, "pop-wagon-wheel", RunMode.WAIT, Hands.RIGHT, stars = 3)),
            AppEvents.songPlayed("twinkle", "listen", null, null), AppEvents.chartHeard("wagon-wheel"),
            AppEvents.arcadeGamePlayed(BriefEvent.GameRound(1, "balloons", 2, 8, level = 3)),
            AppEvents.sightDrillDone(2, 14, "staff"), AppEvents.dailyPlanStarted(3), AppEvents.dailyPlanFinished(2, 3, 300, false),
            AppEvents.onboardingStep(1, true), AppEvents.bigKidMade(), AppEvents.familyCardOpened(), AppEvents.briefEmailSetUp(true),
            AppEvents.timeCapReached(30), AppEvents.gateOpened(), AppEvents.gateFailed(), AppEvents.tourCardSeen("TODAY"), AppEvents.micPermission(false),
            AppEvents.fromSetting(BriefEvent.SettingChanged(1, "layout", "simple"))!!, AppEvents.fromSetting(BriefEvent.SettingChanged(1, "capMin", "30"))!!,
            AppEvents.fromSetting(BriefEvent.SettingChanged(1, "level", "A_LITTLE"))!!,
            AppEvents.micNotHeard(BriefEvent.MicNotHeard(1, NotHeard.NOISE)),
        )
        for (e in events) {
            val keys = AppEvents.CATALOG[e.name]
            assertTrue("${e.name} is not in the catalog", keys != null)
            for (k in e.props.keys) assertTrue("${e.name}.$k is not listed", k in keys!!)
            for ((k, v) in e.props) assertTrue("${e.name}.$k = $v", v is Number || v is Boolean || (v is String && Regex("[a-z0-9:_+-]+").matches(v)))
        }
    }

    @Test fun bucketsHaveFixedEdges() {
        assertEquals(listOf("0", "1-4", "5-9", "10-19", "20-29", "30-59", "60+"),
            listOf(59L, 60L, 300L, 600L, 1200L, 1800L, 3600L).map(AppEvents::minutesBucket))
        assertEquals("0", AppEvents.minutesBucket(-5))
        assertEquals(listOf("0-49", "50-69", "70-84", "85-94", "95-100"), listOf(49, 50, 84, 85, 100).map(AppEvents::accuracyBucket))
    }

    @Test fun aSongOfThePersonsOwnIsMySong() {
        assertEquals("my_song", AppEvents.songId("user-mias-recital"))
        assertEquals("my_song", AppEvents.songId("mias-lullaby-3fa2b1"))
        assertEquals("my_song", AppEvents.songId("song-00ff10"))
        assertEquals("twinkle", AppEvents.songId("twinkle"))
        // Every bundled song keeps its id (none looks like a My songs id).
        @Suppress("UNCHECKED_CAST")
        val songs = (MiniJson.read(File("src/main/assets/songs/index.json").readText()) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>
        assertTrue(songs.size > 20)
        for (s in songs) { val id = s["id"] as String; assertEquals(id, id, AppEvents.songId(id)) }
    }

    @Test fun whoIsPlayingHasThreeKinds() {
        assertEquals("kid", AppEvents.profileKind(kid = true, bigKid = false))
        assertEquals("big_kid", AppEvents.profileKind(kid = true, bigKid = true))
        assertEquals("adult", AppEvents.profileKind(kid = false, bigKid = true))
        assertEquals("big_kid", AppEvents.refineKind("kid", "big_kid"))
        assertEquals("kid", AppEvents.refineKind("kid", "kid"))
        assertEquals("adult", AppEvents.refineKind("adult", "big_kid"))
        assertEquals("kid", AppEvents.refineKind("kid", null))
    }

    @Test fun theBriefsEventsBecomeTheDashboards() {
        val t = TesterTally()
        assertNull("a step skipped with Next", AppEvents.lessonStepPassed(BriefEvent.LessonStep(1, "find-c", 0, "READ", skipped = true)))
        assertNull("a step left part way", AppEvents.lessonStepPassed(BriefEvent.LessonStep(1, "find-c", 0, "PLAY", tries = 2, left = true)))
        assertEquals(TesterEvent("lesson_step_passed", mapOf("lesson" to "find-c", "step" to 2, "kind" to "play", "tries" to 1, "let_go" to false, "stars" to 3)),
            t.eventsFor(BriefEvent.LessonStep(1, "find-c", 2, "PLAY", tries = 1, stars = 3)).single())
        assertEquals(TesterEvent("chord_chart_played", mapOf("chart" to "wagon-wheel", "mode" to "wait", "stars" to 3)),
            t.eventsFor(BriefEvent.SongRun(2, "pop-wagon-wheel", RunMode.WAIT, Hands.RIGHT, stars = 3)).single())
        assertEquals(TesterEvent("song_played", mapOf("song" to "twinkle", "mode" to "perform", "stars" to 2, "accuracy_bucket" to "95-100", "duet" to true)),
            t.eventsFor(BriefEvent.SongRun(3, "twinkle", RunMode.PERFORM, Hands.RIGHT, accuracy = 97, stars = 2, duet = true)).single())
        assertEquals(TesterEvent("arcade_game_played", mapOf("game" to "moles", "level" to 2, "stars" to 1)),
            t.eventsFor(BriefEvent.GameRound(4, "moles", 1, 6, level = 2)).single())
        assertEquals(TesterEvent("layout_chosen", mapOf("layout" to "advanced")), t.eventsFor(BriefEvent.SettingChanged(5, "layout", "advanced")).single())
        assertEquals(TesterEvent("time_cap_set", mapOf("minutes" to 0)), t.eventsFor(BriefEvent.SettingChanged(5, "capMin", "0")).single())
        assertEquals(TesterEvent("placement_result", mapOf("level" to "some")), t.eventsFor(BriefEvent.SettingChanged(5, "level", "SOME")).single())
        assertTrue("other settings are not tracked", t.eventsFor(BriefEvent.SettingChanged(5, "voice", "false")).isEmpty())
        // 2.15 merge: mic-trust's could-not-hear hint is one brief event, so one mic_not_heard with the cause only.
        assertEquals(listOf(TesterEvent("mic_not_heard", mapOf("cause" to "noise"))), t.eventsFor(BriefEvent.MicNotHeard(6, NotHeard.NOISE)))
        assertEquals(listOf("cause"), AppEvents.CATALOG["mic_not_heard"])
    }

    @Test fun aSessionsLengthAndPracticeWithItsKindsOfPlay() {
        val t = TesterTally()
        assertTrue(t.eventsFor(BriefEvent.SessionStarted(0)).isEmpty())
        t.eventsFor(BriefEvent.LessonStep(1, "find-c", 0, "PLAY", tries = 1))
        t.eventsFor(BriefEvent.LessonDone(2, "find-c", 3))
        t.eventsFor(BriefEvent.SongRun(3, "twinkle", RunMode.WAIT, Hands.RIGHT, stars = 2))
        t.eventsFor(BriefEvent.ToyOpened(4, ToyKind.JAM, 30_000))
        val end = t.eventsFor(BriefEvent.SessionEnded(5, durationMs = 13 * 60_000L, practiceSec = 9 * 60))
        assertEquals(listOf(TesterEvent("session_length", mapOf("minutes_bucket" to "10-19")),
            TesterEvent("practice_session", mapOf("minutes_bucket" to "5-9", "parts" to 3))), end)
        assertEquals("no practice: the length only", listOf("session_length"),
            t.eventsFor(BriefEvent.SessionEnded(6, durationMs = 40_000L, practiceSec = 0)).map { it.name })
        t.eventsFor(BriefEvent.SessionStarted(7))
        assertEquals(0, t.eventsFor(BriefEvent.SessionEnded(8, durationMs = 70_000L, practiceSec = 61)).last().props["parts"])
    }

    @Test fun onboardingStepsAreNamed() {
        assertEquals(TesterEvent("onboarding_step", mapOf("step" to "experience", "result" to "completed")), AppEvents.onboardingStep(1, true))
        assertEquals(TesterEvent("onboarding_step", mapOf("step" to "welcome", "result" to "skipped")), AppEvents.onboardingStep(0, false))
        assertEquals("step_9", AppEvents.onboardingStep(9, true).props["step"])
    }
}
