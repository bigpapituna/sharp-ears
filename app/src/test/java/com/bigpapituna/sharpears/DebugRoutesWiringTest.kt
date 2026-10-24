package com.bigpapituna.sharpears

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.10.1 (security review 2026-10-22, finding 1), read from the sources as the other wiring tests do: every launch extra the
 * app acts on goes through one gate (engine/DebugRoutes.kt, MainActivity.routesAllowed), which strips a refused launch's
 * extras before anything reads them; the build keeps the key's digest only; the scripts never print the key.
 */
class DebugRoutesWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText()
    private val readsExtras = Regex("""get(String|Boolean|Int|Long|Float|Double|StringArray|Bundle)Extra\(|intent\??\.extras""")

    /** A new file reading launch extras fails here, so it gets the same gate (or is the notification's own receiver). */
    @Test fun onlyTheKnownDoorsReadIntentExtras() {
        val readers = root.walkTopDown().filter { it.extension == "kt" && readsExtras.containsMatchIn(it.readText()) }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toSortedSet()
        assertEquals(sortedSetOf("MainActivity.kt", "reminder/BriefReminder.kt", "ui/brief/BriefDemo.kt", "ui/brief/email/EmailDemo.kt",
            "ui/pup/PupDebug.kt", "ui/skin/SkinDemo.kt", "ui/theory/TheoryScreen.kt", "ui/world/WorldDemo.kt"), readers)
    }

    @Test fun mainActivityAsksTheGateForEveryLaunchAndStripsWhatItRefuses() {
        val main = src("MainActivity.kt")
        assertEquals(1, Regex("""DebugRoutes\.verdict\(BuildConfig\.DEBUG_ROUTE_KEY_SHA256""").findAll(main).count())
        val create = main.substringAfter("override fun onCreate(").substringBefore("sampler = Sampler(this)")
        assertTrue("debugRoutes = routesAllowed(intent)" in create && "if (!debugRoutes) intent = withoutDebugExtras(intent)" in create)
        val newIntent = main.substringAfter("override fun onNewIntent(").substringBefore("override fun onSaveInstanceState")
        assertTrue("val allowed = routesAllowed(raw)" in newIntent && "if (allowed) raw else withoutDebugExtras(raw)" in newIntent)
        // The weekly brief notification's two extras are the only ones kept, in every build.
        assertTrue("private val NOTIFICATION_EXTRAS = setOf(com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PROFILE, " +
            "com.bigpapituna.sharpears.reminder.BriefReminder.EXTRA_PERIOD)" in main)
        // Launcher icons, shortcuts and browsers count as the launcher.
        for (s in listOf("CATEGORY_LAUNCHER", "CATEGORY_BROWSABLE", "sourceBounds != null", "android.intent.extra.shortcut.ID")) assertTrue(s, s in main)
        // The debuggable flag that once opened every route is read only by the gate and for Microphone setup's recorder.
        val flagged = main.lines().filter { "FLAG_DEBUGGABLE" in it }
        assertEquals(flagged.joinToString("\n"), 2, flagged.size)
        assertTrue(flagged.none { readsExtras.containsMatchIn(it) })
        // The routes read the gate's answer, and the demos open only from a route it let through.
        assertTrue("val routes = fromLaunch && debugRoutes" in main)
        assertTrue("val route = intent.getStringExtra(\"screen\")?.takeIf { routes }" in main)
        for (demo in listOf("WorldDemo(intent)", "PupDebug(intent)", "BriefDemo(intent)", "EmailDemo(intent, profile)", "SkinDemo(intent,"))
            assertTrue(demo, Regex("""route == "\w+" -> return setContent \{ [\w.]*""" + Regex.escape(demo)).containsMatchIn(main))
        // TheoryScreen asks the activity's gate, not the debuggable flag.
        val theory = src("ui/theory/TheoryScreen.kt")
        assertTrue("activity.debugRoutes" in theory && "FLAG_DEBUGGABLE" !in theory)
    }

    @Test fun theBuildKeepsTheKeysDigestAndTheScriptsNeverPrintTheKey() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue(gradle.contains("buildConfigField(\"String\", \"DEBUG_ROUTE_KEY_SHA256\", \"\\\"\$debugRouteKeySha256\\\"\")"))
        assertTrue("the test override is read before the file",
            gradle.contains("providers.gradleProperty(\"debugRouteKey\").orNull ?: secrets.getProperty(\"DEBUG_ROUTE_KEY\")"))
        assertFalse("never the key itself", Regex("buildConfigField\\([^)]*\"DEBUG_ROUTE_KEY\"").containsMatchIn(gradle))
        val set = File("../scripts/set-debug-key.ps1").readText()
        assertEquals(listOf("Write-Host 'Saved.'"), set.lines().map { it.trim() }.filter { "Write-Host" in it || "Write-Output" in it })
        assertTrue(set.contains("RandomNumberGenerator") && set.contains("-lt 24") && set.contains("check-ignore"))
        val route = File("../scripts/adb-route.ps1").readText()
        assertTrue(route.contains("'--es', 'dk', \$key") && route.contains("com.bigpapituna.sharpears/.MainActivity"))
        assertFalse("the key is never shown", Regex("Write-(Host|Output)[^\\n]*\\\$key").containsMatchIn(route))
    }
}
