package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderPlanTest {
    @Test fun nextIsLaterTodayOrTomorrow() {
        val now = LocalDateTime.of(2026, 10, 8, 17, 0)
        assertEquals(LocalDateTime.of(2026, 10, 8, 18, 30), ReminderPlan.next(now, 18 * 60 + 30))
        assertEquals(LocalDateTime.of(2026, 10, 9, 9, 0), ReminderPlan.next(now, 9 * 60))
        assertEquals(LocalDateTime.of(2026, 10, 9, 17, 0), ReminderPlan.next(now, 17 * 60))        // just fired: tomorrow
    }

    @Test fun theMessageNamesWhatIsWaiting() {
        assertEquals("Time to practise. 5 chords due, Minuet in G is waiting.", ReminderPlan.text(5, "Minuet in G"))
        assertEquals("Time to practise. 1 chord due.", ReminderPlan.text(1, null))
        assertEquals("Time to practise. Twinkle is waiting.", ReminderPlan.text(0, "Twinkle"))
        assertEquals("Time to practise.", ReminderPlan.text(0, null))
        assertEquals("7:05", ReminderPlan.label(7 * 60 + 5))
    }
}
