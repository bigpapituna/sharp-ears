package com.bigpapituna.sharpears.reminder

import com.bigpapituna.sharpears.engine.ReminderPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Audit (docs/AUDIT-ENGINE.md): the reminder's one alarm is a wall-clock time, set at the last boot, app start or
 * reminder. Travel to another time zone (or a clock set by hand) left it at the old local time until the next one had
 * fired. The receiver now hears both changes and sets the alarm again; only the alarm itself (no action) posts.
 */
class ReminderWiringTest {
    @Test fun aNewTimeZoneOrClockSetsTheNextReminderAgain() {
        val filter = Regex("(?s)<receiver android:name=\".reminder.ReminderReceiver\".*?</receiver>").find(File("src/main/AndroidManifest.xml").readText())!!.value
        for (a in listOf("BOOT_COMPLETED", "TIMEZONE_CHANGED", "TIME_SET")) assertTrue(a, "android.intent.action.$a" in filter)
        assertTrue(File("src/main/java/com/bigpapituna/sharpears/reminder/Reminder.kt").readText().contains("if (intent.action != null) { Reminder.schedule(context); return }"))
        // What the receiver then works out: 18:30 in the new zone, from the new local time.
        val tokyo = LocalDateTime.of(2026, 10, 20, 9, 0)
        val next = ReminderPlan.next(tokyo, 18 * 60 + 30)
        assertEquals(LocalDateTime.of(2026, 10, 20, 18, 30), next)
        assertEquals(9L, java.time.Duration.between(tokyo.atZone(ZoneId.of("Asia/Tokyo")), next.atZone(ZoneId.of("Asia/Tokyo"))).toHours())
    }
}
