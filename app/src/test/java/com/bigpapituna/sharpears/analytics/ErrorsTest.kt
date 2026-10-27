package com.bigpapituna.sharpears.analytics

import com.bigpapituna.sharpears.data.FolderContext
import com.bigpapituna.sharpears.engine.tester.AppEvents
import com.bigpapituna.sharpears.engine.tester.ErrorReport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 2.15, error tracking on the phone (analytics/Errors.kt) over a real files folder: nothing is sent while the dashboard does
 * not record (here it never does), the door's last look scrubs every exception message the SDK collected, marks the debug
 * route's test exception, stamps the test marks and holds the SDK's own reports to the limit, kept across a restart.
 */
class ErrorsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val lines = ArrayList<String>()
    private lateinit var phone: FolderContext

    @Before fun start() {
        Tester.resetForTest(); Errors.resetForTest()
        Tester.logcat = { lines += it }
        phone = FolderContext(tmp.root)
        Tester.init(phone)
    }

    @After fun stop() { Tester.resetForTest(); Tester.switchOn.value = false; Tester.diaryOn.value = false }

    @Test fun theTestExceptionIsTheOneTheRulesName() {
        assertEquals(ErrorReport.TEST_CLASS, ErrorTestException::class.java.name)
    }

    @Test fun aReportWhileTheDashboardDoesNotRecordSendsNothing() {
        Errors.report("Voice engine", IllegalStateException("status -1 for mia@example.com"))
        assertTrue(lines.toString(), lines.any { it.startsWith("errors: voice_engine not sent (") })
        assertTrue("no message, no address in the log", lines.none { "@" in it || "status -1" in it })
    }

    private fun exception(type: String, value: String, fatal: Boolean): MutableMap<String, Any> = mutableMapOf(
        "\$exception_list" to listOf(mapOf("type" to type, "value" to value, "mechanism" to mapOf("type" to "generic", "handled" to !fatal))),
        "\$exception_level" to if (fatal) "fatal" else "error",
    )

    @Test fun theLastLookScrubsMarksAndLimitsTheSdksOwnReports() {
        val crash = exception("com.bigpapituna.sharpears.analytics.ErrorTestException", "boom at /data/user/0/x/files/Mia.mid for roy@example.com", fatal = true)
        assertTrue(Errors.beforeSend("\$exception", crash))
        @Suppress("UNCHECKED_CAST") val item = (crash["\$exception_list"] as List<Map<String, Any?>>).single()
        assertEquals("boom at [path] for [address]", item["value"])
        assertEquals(true, crash[ErrorReport.IS_ERROR_TEST])
        assertEquals("uncaught", crash[ErrorReport.WHERE])
        assertTrue("the marks are stamped", crash[AppEvents.IS_EMULATOR] is Boolean && crash[AppEvents.APP_VERSION] is String && crash[AppEvents.BUILD_FLAVOR] is String)
        assertTrue(lines.toString(), lines.any { it.startsWith("errors: reported uncaught (ErrorTestException, fatal=true, is_error_test=true") })
        // The same crash again within ten minutes (a crash loop, a new process): dropped.
        Tester.resetForTest(); Errors.resetForTest(); Tester.init(phone)
        assertFalse(Errors.beforeSend("\$exception", exception("com.bigpapituna.sharpears.analytics.ErrorTestException", "boom at /data/user/0/x/files/Mia.mid for roy@example.com", fatal = true)))
        assertTrue(lines.any { it == "errors: uncaught not sent (limited)" })
    }

    @Test fun aHandledReportKeepsItsPlaceAndAnyOtherEventGetsTheMarks() {
        val handled = exception("java.io.IOException", "HTTP 503", fatal = false).apply { put(ErrorReport.WHERE, "email_send") }
        assertTrue(Errors.beforeSend("\$exception", handled))
        assertEquals("email_send", handled[ErrorReport.WHERE])
        assertFalse(ErrorReport.IS_ERROR_TEST in handled)
        val screen = mutableMapOf<String, Any>("route" to "home:LEARN", AppEvents.IS_EMULATOR to true)
        assertTrue(Errors.beforeSend("\$screen", screen))
        assertEquals("a mark the event has is kept", true, screen[AppEvents.IS_EMULATOR])
        assertTrue(screen[AppEvents.BUILD_FLAVOR] is String)
    }
}
