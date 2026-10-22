package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.analytics.HELP_ROY_TEST_LINE
import com.bigpapituna.sharpears.data.ProgressFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tester analytics (CLAUDE.md "Tester analytics"), read from the sources: the PostHog SDK is compiled in only on a build with a
 * key, nothing outside its door touches it, and the switches sit where Roy asked (behind the gate, with the corner's dot).
 */
class TesterWiringTest {
    private val app = File(".")
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(main, f).readText()

    @Test fun theSdkIsInTheBuildOnlyWithAKey() {
        val gradle = File(app, "build.gradle.kts").readText()
        assertTrue(gradle.contains("val withPostHog = secret(\"POSTHOG_KEY\").isNotBlank()"))
        assertTrue(gradle.contains("if (withPostHog) implementation(libs.posthog.android)"))
        assertTrue(gradle.contains("kotlin.srcDir(if (withPostHog) \"src/posthog/java\" else \"src/noposthog/java\")"))
        for (key in listOf("POSTHOG_KEY", "POSTHOG_HOST", "DIARY_TO")) assertTrue(key, gradle.contains("buildConfigField(\"String\", \"$key\""))
        // Nothing in the app proper names PostHog's classes: only the door in src/posthog does.
        val offenders = File("src/main/java").walkTopDown().filter { it.extension == "kt" && "import com.posthog" in it.readText() }.toList()
        assertEquals(emptyList<File>(), offenders)
        // The door and its stub offer the same members.
        fun members(f: String) = Regex("""(?m)^\s*(?:@\S+\s+)*(?:inline\s+)?(?:fun|val|const val)\s+([\w.]+)""").findAll(File(f).readText()).map { it.groupValues[1] }.toSet()
        val real = members("src/posthog/java/com/bigpapituna/sharpears/analytics/TesterSdk.kt")
        val stub = members("src/noposthog/java/com/bigpapituna/sharpears/analytics/TesterSdk.kt")
        assertEquals(setOf("present", "start", "running", "resume", "pause", "capture", "screen", "register", "Modifier.testerMask"), stub)
        assertTrue("$real", real.containsAll(stub))
    }

    @Test fun theRealDoorMasksTextAndImagesAndRecordsNoLogcat() {
        val door = File("src/posthog/java/com/bigpapituna/sharpears/analytics/TesterSdk.kt").readText()
        for (line in listOf("sessionReplayConfig.screenshot = true", "sessionReplayConfig.maskAllTextInputs = true", "sessionReplayConfig.maskAllImages = true",
            "sessionReplayConfig.captureLogcat = false", "captureElementInteractions = false", "captureScreenViews = false", "getAnonymousId = { id }",
            "PostHog.stopSessionReplay(); PostHog.optOut()"))
            assertTrue(line, door.contains(line))
        assertTrue("never flushes on the way out", !door.contains("PostHog.flush("))
    }

    @Test fun theSwitchesAreBehindTheGateWithTheCornersDot() {
        assertEquals("Sends screen recordings and app events to Roy's test dashboard. No sound.", HELP_ROY_TEST_LINE)
        val corner = src("ui/kid/KidHome.kt")
        assertTrue(corner.contains("com.bigpapituna.sharpears.analytics.TesterSettings(Modifier.padding(top = 8.dp))"))
        assertTrue(corner.contains("dot = com.bigpapituna.sharpears.analytics.Tester.switchOn.value"))
        assertTrue("the corner is masked whole", Regex("""verticalScroll\([^)]*\)\)\.testerMask\(\)""").containsMatchIn(corner))
        assertTrue(src("ui/family/FamilySettings.kt").contains("TesterSettings(Modifier.padding(top = 16.dp), guard = { gate.guard(it) })"))
        val rows = src("analytics/TesterSettings.kt")
        assertTrue(rows.contains("SwitchRow(\"Help Roy test\", HELP_ROY_TEST_LINE"))
        assertTrue(rows.contains("SwitchRow(\"Email Roy a daily diary\""))
        assertTrue("the diary's row needs its address", rows.contains("val diaryRow = Tester.diaryBuilt"))
        val activity = src("MainActivity.kt")
        assertTrue(activity.contains("com.bigpapituna.sharpears.analytics.Tester.init(this)"))
        assertTrue(activity.contains("Briefs.tap = com.bigpapituna.sharpears.analytics.Tester::briefEvent"))
        assertTrue(activity.contains("Tester.screen(shownRoute!!)"))
        assertTrue(activity.contains("Tester.profile(profile.isKid, profile.id)"))
    }

    /** The switches are the phone's own: not per profile, not in the progress export (a restored phone starts with them off). */
    @Test fun theTesterFileIsThePhonesOwn() {
        assertEquals("tester", com.bigpapituna.sharpears.analytics.Tester.FILE)
        assertTrue("tester" !in ProgressFiles.NAMES)
        assertTrue(src("analytics/Tester.kt").contains("getSharedPreferences(FILE, Context.MODE_PRIVATE)"))
    }

    @Test fun thePrivacyPageAndTheOnboardingSayIt() {
        val about = src("ui/AboutScreens.kt")
        for (s in listOf("PostHog (posthog.com)", "No sound is ever recorded", "PostHog keeps recordings for 30 days", "Kid profiles are recorded too"))
            assertTrue(s, about.contains(s))
        assertTrue(src("ui/onboarding/OnboardingScreen.kt").contains("t.switchOn.value && t.hasKey"))
    }
}
