package com.bigpapituna.sharpears.analytics

import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.data.FolderContext
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.tester.TesterLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID

/**
 * The tester switches on the phone (analytics/Tester.kt) over a real files folder: they persist in the phone-wide tester.xml,
 * off by default; the device id is random and made once; nothing is written while both are off; a keyless build never has the
 * SDK. (Run on this PC, whose build has no POSTHOG_KEY.)
 */
class TesterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val lines = ArrayList<String>()
    private lateinit var phone: FolderContext

    @Before fun start() {
        Tester.resetForTest()
        Tester.logcat = { lines += it }
        phone = FolderContext(tmp.root)
        Tester.init(phone)
    }

    @After fun stop() { Tester.log()?.flush(); Tester.resetForTest(); Tester.switchOn.value = false; Tester.diaryOn.value = false }

    private fun prefs() = phone.getSharedPreferences(Tester.FILE, 0)

    @Test fun bothSwitchesAreOffUntilTurnedOnAndThenStayOn() {
        assertFalse(Tester.switchOn.value)
        assertFalse(Tester.diaryOn.value)
        Tester.setOn(phone, true)
        Tester.setDiary(phone, true)
        assertEquals(true, prefs().getBoolean("on", false))
        assertEquals(true, prefs().getBoolean("diary", false))
        // A new process reads them back.
        Tester.resetForTest(); Tester.switchOn.value = false; Tester.diaryOn.value = false
        Tester.init(phone)
        assertTrue(Tester.switchOn.value)
        assertTrue(Tester.diaryOn.value)
        Tester.setOn(phone, false)
        Tester.resetForTest(); Tester.init(phone)
        assertFalse(Tester.switchOn.value)
        assertTrue(lines.none { "@" in it })
    }

    @Test fun theDeviceIdIsRandomAndMadeOnce() {
        val id = Tester.deviceId(phone)
        UUID.fromString(id)
        assertEquals(id, Tester.deviceId(phone))
        assertNotEquals("another phone has another id", id, Tester.deviceId(FolderContext(tmp.newFolder())))
    }

    @Test fun aKeylessBuildHasNoSdkAndStartsNothing() {
        // Roy's PC builds with the key in secrets.properties; the keyless checks only apply to a keyless build.
        org.junit.Assume.assumeTrue("this build has a key; the keyless checks do not apply", BuildConfig.POSTHOG_KEY.isEmpty())
        assertFalse(BuildConfig.POSTHOG_SDK)
        assertFalse(TesterSdk.present)
        assertTrue("PostHog is not even on the classpath", runCatching { Class.forName("com.posthog.PostHog") }.isFailure)
        Tester.setOn(phone, true)
        Tester.screen("home:LEARN")
        Tester.event("brief_opened")
        assertFalse(TesterSdk.running)
        assertTrue(lines.toString(), lines.any { it.contains("dashboard not in this build") })
        assertFalse(lines.any { it.contains("dashboard recording") })
    }

    @Test fun nothingIsWrittenWhileBothAreOff() {
        Tester.profile(isKid = true, profileId = "p1")
        Tester.screen("home:SONGS")
        Tester.error("song import")
        Tester.result("email", "NO_KEY", "email: DAILY not sent: NO_KEY")
        Tester.away()
        Tester.log()?.flush()
        assertFalse(File(tmp.root, Tester.DIR).exists())
    }

    @Test fun whileOnScreensNotesAndErrorsGoToTheTesterLog() {
        Tester.setOn(phone, true)
        Tester.profile(isKid = true, profileId = "p1")
        Tester.screen("home:LEARN")
        Tester.screen("home:LEARN")                           // the same route again: one line
        Tester.screen("home:LEARN/lesson:2:find-c")
        Tester.note("The C stayed dark, tell roy@example.com")
        Tester.error("song import")
        Tester.logLine("email: DAILY 2026-10-21 for p1 not sent: NO_KEY")
        Tester.logLine("email: planned (session ended): next look never")   // not a result
        Tester.away()
        Tester.back()
        val log = Tester.read(Long.MIN_VALUE, Long.MAX_VALUE).map { it as BriefEvent.Unknown }
        assertEquals(listOf(TesterLog.KIND, TesterLog.SCREEN, TesterLog.SCREEN, TesterLog.NOTE, TesterLog.ERROR, TesterLog.RESULT,
            TesterLog.SCREEN, TesterLog.SCREEN), log.map { it.name })
        val note = log.first { it.name == TesterLog.NOTE }.fields
        assertEquals("lesson:find-c", note["s"]); assertEquals("home:LEARN", note["before"])
        assertEquals("The C stayed dark, tell [address]", note["text"])
        assertEquals(listOf("home:LEARN", "lesson:find-c", TesterLog.AWAY, "lesson:find-c"), log.filter { it.name == TesterLog.SCREEN }.map { it.fields["s"] })
        assertEquals(mapOf("kind" to "kid", "id" to "p1"), log.first().fields)
        assertTrue("the log is the tester's own folder", File(File(tmp.root, Tester.DIR), "diary.log").exists())
    }

    /** Turned on on a screen already showing (the corner over the kid home): that screen and who plays start the log. */
    @Test fun turningItOnLogsTheScreenOnShow() {
        Tester.profile(isKid = true, profileId = "p3")
        Tester.screen("home:SONGS")
        Tester.setOn(phone, true)
        Tester.note("stuck")
        val log = Tester.read(Long.MIN_VALUE, Long.MAX_VALUE).map { it as BriefEvent.Unknown }
        assertEquals(listOf(TesterLog.KIND, TesterLog.SCREEN, TesterLog.NOTE), log.map { it.name })
        assertEquals("home:SONGS", log[1].fields["s"])
    }

    @Test fun theDiaryAloneRecordsOnlyOnABuildWithItsAddress() {
        Tester.setDiary(phone, true)
        Tester.screen("home:LEARN")
        Tester.log()?.flush()
        assertEquals(Tester.diaryBuilt, File(tmp.root, Tester.DIR).exists())
    }
}
