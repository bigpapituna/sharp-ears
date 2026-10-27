package com.bigpapituna.sharpears.engine.tester

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.15, error tracking's rules (engine/tester/ErrorRules.kt): what a report may carry (no address, key, path, file name,
 * title or family name; 200 characters at most), how often one goes (one per place, class and message in ten minutes, 30 a
 * day, kept across starts), the test marks, and reading a past ANR.
 */
class ErrorRulesTest {
    @Test fun aMessageLosesAddressesKeysAndTokens() {
        assertEquals("send to [address] failed", ErrorScrub.message("send to roy.younes@example.com failed"))
        assertEquals("HTTP 401 [key] refused", ErrorScrub.message("HTTP 401 Bearer abcdef123456 refused"))
        for (key in listOf("phc_AbCdEf1234567890xyz", "sk-or-v1-0123456789abcdef", "xkeysib-0123456789abcdef-XyZ", "sk-ant-api03-abcdef123"))
            assertEquals(key, "key [key] leaked", ErrorScrub.message("key $key leaked"))
        assertEquals("token [key] here", ErrorScrub.message("token a8F3kQ9zL2mN7pR4tV6wX1yB5cD here"))
        assertEquals("a long class name stays", "IllegalStateExceptionHandlerFactoryBean",
            ErrorScrub.message("IllegalStateExceptionHandlerFactoryBean"))
    }

    @Test fun aMessageLosesPathsLinksFileNamesAndQuotedWords() {
        assertEquals("open failed: [path] (ENOENT)", ErrorScrub.message("open failed: /storage/emulated/0/Download/Alicia.mid (ENOENT)"))
        assertEquals("no [link] here", ErrorScrub.message("no content://com.android.providers.downloads/document/42 here"))
        assertEquals("could not read [file]", ErrorScrub.message("could not read Mias_recital.mid"))
        assertEquals("song [text] refused", ErrorScrub.message("song \"Mia's Lullaby\" refused"))
        assertEquals("call [number] now", ErrorScrub.message("call 613 555 0199 now"))
        assertEquals("pick [file] then", ErrorScrub.message("pick song.midi then"))
    }

    @Test fun aMessageLosesTheFamilysNamesAsWholeWords() {
        assertEquals("[name] played, Miami stays", ErrorScrub.message("Mia played, Miami stays", listOf("Mia", "T")))
        assertEquals("[name] and [name]", ErrorScrub.message("THEO and mia", listOf("Mia", "Theo")))
    }

    @Test fun aMessageIsOneLineAndAtMost200() {
        assertEquals("a b c", ErrorScrub.message("a\nb\t c"))
        val long = ErrorScrub.message("word ".repeat(100))
        assertTrue(long.length <= ErrorScrub.MAX); assertTrue(long.endsWith("..."))
        assertEquals("", ErrorScrub.message(null)); assertEquals("", ErrorScrub.message("   "))
        assertEquals(40, ErrorScrub.message("x ".repeat(60), max = 40).length)
    }

    @Test fun placesAndClassesAreTheAppsOwnWords() {
        assertEquals("ai_transport", ErrorScrub.where("AI transport"))
        assertEquals("unknown", ErrorScrub.where("  "))
        assertEquals(40, ErrorScrub.where("x".repeat(80)).length)
        assertEquals("java.lang.IllegalStateException", ErrorScrub.className("java.lang.IllegalStateException"))
        assertEquals("a\$b", ErrorScrub.className("a\$b"))
        assertEquals("Throwable", ErrorScrub.className("not a class / mia@example.com"))
        assertEquals("Throwable", ErrorScrub.className(null))
    }

    @Test fun oneReportPerKeyInTenMinutesAnd30ADay() {
        val l = ErrorLimiter()
        val k = ErrorLimiter.key("voice_engine", "java.lang.IllegalStateException", "status -1")
        assertTrue(l.allow(k, 1_000, 100))
        assertFalse("the same within ten minutes", l.allow(k, 1_000 + 9 * 60_000, 100))
        assertTrue("another message is another key", l.allow(ErrorLimiter.key("voice_engine", "java.lang.IllegalStateException", "status -2"), 2_000, 100))
        assertTrue("ten minutes later", l.allow(k, 1_000 + 10 * 60_000, 100))
        val day = ErrorLimiter()
        repeat(30) { i -> assertTrue("report $i", day.allow("k$i", 5_000L + i, 7)) }
        assertFalse("the 31st of the day", day.allow("k31", 6_000, 7))
        assertEquals(30, day.countToday(7))
        assertTrue("a new day", day.allow("k31", 7_000, 8))
        assertEquals(1, day.countToday(8))
    }

    @Test fun theLimitSurvivesARestart() {
        val l = ErrorLimiter()
        repeat(29) { l.allow("k$it", 1_000L + it, 9) }
        assertTrue(l.allow("crash", 2_000, 9))
        val back = ErrorLimiter.decode(l.encode())
        assertFalse("the day is full after a restart (a crash loop)", back.allow("other", 3_000, 9))
        val one = ErrorLimiter().apply { allow("crash", 2_000, 9) }
        assertFalse("the same crash after a restart", ErrorLimiter.decode(one.encode()).allow("crash", 60_000, 9))
        assertTrue("garbage starts fresh", ErrorLimiter.decode("not|a state").allow("x", 1, 1))
        assertTrue(ErrorLimiter.decode(null).allow("x", 1, 1))
        assertTrue("a clock that went back drops the old marks", ErrorLimiter.decode(one.encode()).allow("crash", 1_000, 9))
    }

    @Test fun aReportCarriesOnlyItsPlaceAndSafeExtras() {
        val p = ErrorReport.props("AI transport", mapOf("kind" to Kind.SERVER, "status" to 503, "name" to "Mia", "file_name" to "x.mid", "songTitle" to "t",
            "BadKey" to 1, "ok" to true, "ratio" to 0.5, "nan" to Double.NaN, "code" to "E 12 at roy@example.com", "obj" to Any(), "where" to "elsewhere"))
        assertEquals(mapOf("kind" to "server", "status" to 503L, "ok" to true, "ratio" to 0.5, "code" to "E 12 at [address]", "where" to "ai_transport", "handled" to true), p)
        assertEquals(true, ErrorReport.props("error_test", test = true)[ErrorReport.IS_ERROR_TEST])
        assertFalse(ErrorReport.IS_ERROR_TEST in ErrorReport.props("x"))
    }

    private enum class Kind { SERVER }

    @Test fun emulatorsAreMarkedAndTheFoldIsNot() {
        assertTrue(BuildMarks.isEmulator("google/sdk_gphone64_x86_64/emu64xa:16/BP22.250124.002/12917452:userdebug/dev-keys", "sdk_gphone64_x86_64",
            "sdk_gphone64_x86_64", "ranchu", "Google"))
        assertTrue(BuildMarks.isEmulator("generic/sdk/generic:9/PSR1/1:eng/test-keys", "Android SDK built for x86", "sdk", "goldfish", "unknown"))
        assertTrue("the hardware alone", BuildMarks.isEmulator(null, null, null, "ranchu", null))
        assertFalse(BuildMarks.isEmulator("samsung/q7qxxx/q7q:16/BP2A.250605.031.A2/F966BXXU1AYH1:user/release-keys", "SM-F966B", "q7qxxx", "qcom", "samsung"))
        assertFalse(BuildMarks.isEmulator(null, null, null, null, null))
    }

    @Test fun theBuildFlavorSaysWhoseBuild() {
        assertEquals("debug", BuildMarks.buildFlavor(debug = true, buildType = "debug", keyed = false))
        assertEquals("tester", BuildMarks.buildFlavor(debug = true, buildType = "debug", keyed = true))
        assertEquals("profile", BuildMarks.buildFlavor(debug = false, buildType = "profile", keyed = true))
        assertEquals("release", BuildMarks.buildFlavor(debug = false, buildType = "", keyed = false))
    }

    @Test fun pastAnrsAreReportedOnceNewestFirst() {
        assertEquals("the first start reports nothing", emptyList<Long>() to 500L, ExitRules.fresh(listOf(100, 200), null, 500))
        assertEquals(listOf(400L, 300L) to 400L, ExitRules.fresh(listOf(100, 300, 400), 200, 900))
        assertEquals(emptyList<Long>() to 400L, ExitRules.fresh(listOf(100, 300, 400), 400, 900))
        assertEquals(listOf(9L, 8L, 7L), ExitRules.fresh((1L..9L).toList(), 0, 10).first)
        assertEquals("the watermark passes even the ones not reported", 9L, ExitRules.fresh((1L..9L).toList(), 0, 10).second)
    }

    @Test fun anAnrTraceGivesTheMainThreadsStack() {
        val trace = """
            ----- pid 1234 at 2026-10-27 10:00:00 -----
            "Signal Catcher" daemon prio=10 tid=4 Runnable
              at dalvik.system.VMStack.getThreadStackTrace(Native method)

            "main" prio=5 tid=1 Sleeping
              | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72a1 self=0xb400
              at java.lang.Thread.sleep(Native method)
              - sleeping on <0x0bf2d6c4> (a java.lang.Object)
              at java.lang.Thread.sleep(Thread.java:450)
              at com.bigpapituna.sharpears.audio.MicInput.start(MicInput.kt:212)
              at com.bigpapituna.sharpears.MainActivity${'$'}onCreate${'$'}1.invoke(Unknown Source:4)
              native: #00 pc 000000000004f2c8  /apex/com.android.runtime/lib64/bionic/libc.so (syscall+24)

            "binder:1234_1" prio=5 tid=8 Native
              at android.os.BinderProxy.transact(Native method)
        """.trimIndent()
        val frames = ExitRules.mainFrames(trace)
        assertEquals(4, frames.size)
        assertEquals(TraceFrame("java.lang.Thread", "sleep", null, -2), frames[0])
        assertEquals(TraceFrame("com.bigpapituna.sharpears.audio.MicInput", "start", "MicInput.kt", 212), frames[2])
        assertEquals(TraceFrame("com.bigpapituna.sharpears.MainActivity\$onCreate\$1", "invoke", null, 4), frames[3])
        assertEquals(emptyList<TraceFrame>(), ExitRules.mainFrames("no threads here"))
        assertEquals(2, ExitRules.mainFrames(trace, max = 2).size)
    }
}
