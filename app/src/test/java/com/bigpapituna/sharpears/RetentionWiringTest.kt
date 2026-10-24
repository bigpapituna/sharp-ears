package com.bigpapituna.sharpears

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.9: the retention pieces reach their screens (read from the sources, as QaWiringTest does): the tomorrow slot on every
 * finish screen, the comeback and the surprise on the homes, the rest day on every seven-day strip, the reminder's
 * surprise line, and the debug clock.
 */
class RetentionWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText()

    @Test fun everyFinishScreenHasTheTomorrowSlot() {
        for (f in listOf("ui/LessonScreen.kt", "ui/RunRecap.kt", "ui/arcade/ArcadeFinish.kt")) assertTrue(f, "TomorrowSlot(" in src(f))
        val main = src("MainActivity.kt")
        // "Done for today!" (both kinds) and every screen under the one provider.
        assertTrue(Regex("""reward = \{ com\.bigpapituna\.sharpears\.ui\.TomorrowSlot\(\) \}""").findAll(main).count() >= 2)
        assertTrue("LocalRetention provides retention" in main)
    }

    @Test fun theHomesShowTheComebackAndTheSurprise() {
        val main = src("MainActivity.kt")
        for (s in listOf("Comeback.due(", "KidComebackCard(", "WelcomeBackCard(", "ReadyGift(", "GiftOpening(", "world.openGift()", "world.wrapGift(",
            "BriefEvent.Comeback(", "Retention.COMEBACK_SHOWN", "Retention.COMEBACK_PLAYED", "Retention.REST_DAY_USED",
            "Retention.SURPRISE_WRAPPED", "Retention.SURPRISE_OPENED")) assertTrue(s, s in main)
        assertTrue("gift: (@Composable () -> Unit)?" in src("ui/kid/KidHome.kt"))
        assertTrue("welcomeBack" in src("ui/TodayScreen.kt"))
    }

    @Test fun everySevenDayStripDrawsTheRestDay() {
        for (f in listOf("ui/skin/Goodbye.kt", "ui/family/FamilyWall.kt", "ui/brief/ReportParts.kt", "ui/profile/ProfileScreen.kt"))
            assertTrue(f, "drawRestPaw(" in src(f))
        assertTrue("d.rest" in src("ui/brief/BriefExport.kt"))
        assertTrue("Days played this month" in src("ui/profile/ProfileScreen.kt"))
    }

    @Test fun theReminderSaysTheSurpriseAndTheClockIsDebugOnly() {
        val reminder = src("reminder/Reminder.kt")
        assertTrue("ReminderPlan.text(due, song, surprise)" in reminder && "dailySummary(today) != null, surprise)" in reminder)
        val main = src("MainActivity.kt")
        val today = main.substringAfter("private fun debugToday(").substringBefore("override fun onNewIntent")
        // 2.10.1: only a launch the debug routes' gate let through (engine/DebugRoutes.kt, MainActivity.routesAllowed).
        assertTrue("if (!allowed) return" in today && "debugToday(intent, debugRoutes)" in main && "debugToday(intent, allowed)" in main)
        // No countdown on the gift: nothing in its screen counts hours, minutes or seconds.
        assertFalse(Regex("(?i)hours left|minutes left|seconds left|opens in [0-9]").containsMatchIn(src("ui/Retention.kt")))
    }
}
