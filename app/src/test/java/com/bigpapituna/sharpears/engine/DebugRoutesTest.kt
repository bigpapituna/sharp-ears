package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.DebugRoutes.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.10.1 (security review 2026-10-22, finding 1): who may open the debug routes. A keyless build (the worktree builds agents
 * use on emulators) as before; a keyed build (the ones Roy gives testers) only with `--es dk <key>`; never a link, a browser,
 * another app's action or the launcher, keyed or not.
 */
class DebugRoutesTest {
    private val adb: String? = null                       // `adb shell am start -n ...` sends no action
    private val view = "android.intent.action.VIEW"
    private val key = DebugRoutes.digest("test123")

    @Test fun aKeylessBuildTakesRoutesFromAdbAsBefore() {
        assertTrue(DebugRoutes.allowed("", null, adb))
        assertTrue(DebugRoutes.allowed("", "anything", adb))
        assertTrue("am start -a android.intent.action.MAIN -n ...", DebugRoutes.allowed("", null, DebugRoutes.ACTION_MAIN))
    }

    @Test fun aKeyedBuildWantsItsKey() {
        assertEquals(Verdict.ALLOWED, DebugRoutes.verdict(key, "test123", adb))
        assertEquals(Verdict.NO_KEY, DebugRoutes.verdict(key, null, adb))
        assertEquals(Verdict.NO_KEY, DebugRoutes.verdict(key, "", adb))
        assertEquals(Verdict.WRONG_KEY, DebugRoutes.verdict(key, "test124", adb))
        assertEquals(Verdict.WRONG_KEY, DebugRoutes.verdict(key, "TEST123", adb))
        assertEquals("the digest itself is not the key", Verdict.WRONG_KEY, DebugRoutes.verdict(key, key, adb))
        assertTrue("spaces around it, as a file or a shell may leave them", DebugRoutes.allowed(key, " test123 ", adb))
        assertTrue("the build's digest in either case", DebugRoutes.allowed(key.uppercase(), "test123", adb))
    }

    @Test fun neverALinkABrowserAnotherAppsActionOrTheLauncher() {
        for (build in listOf("", key)) {
            assertEquals(Verdict.ACTION, DebugRoutes.verdict(build, "test123", view))
            assertEquals(Verdict.ACTION, DebugRoutes.verdict(build, "test123", "com.example.ANY"))
            assertEquals(Verdict.ACTION, DebugRoutes.verdict(build, "test123", "android.intent.action.SEND"))
            assertEquals(Verdict.LAUNCHER, DebugRoutes.verdict(build, "test123", DebugRoutes.ACTION_MAIN, fromLauncher = true))
            assertEquals(Verdict.LAUNCHER, DebugRoutes.verdict(build, "test123", adb, fromLauncher = true))
            assertFalse(DebugRoutes.allowed(build, "test123", view))
        }
    }

    /** The build writes the same digest (app/build.gradle.kts): SHA-256 in lower-case hex, the key trimmed. */
    @Test fun theDigestIsSha256InLowerCaseHex() {
        assertEquals("ecd71870d1963316a97e3ac3408c9835ad8cf0f3c1bc703527c30265534f75ae", DebugRoutes.digest("test123"))
        assertEquals(DebugRoutes.digest("test123"), DebugRoutes.digest("  test123\n"))
        assertEquals(64, DebugRoutes.digest("x").length)
        assertTrue(Regex("[0-9a-f]{64}").matches(DebugRoutes.digest("Zq3")))
    }
}
