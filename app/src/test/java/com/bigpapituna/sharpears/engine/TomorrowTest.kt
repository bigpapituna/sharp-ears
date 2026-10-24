package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.tester.Retention
import com.bigpapituna.sharpears.engine.tester.TesterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.9: a grown-up's finish screen names tomorrow's next thing (no gift), and the retention events carry the kind only. */
class TomorrowTest {
    @Test fun theLineNamesTheNextThingFromThePlan() {
        assertEquals("Tomorrow: the left hand of Twinkle", Tomorrow.line("Twinkle", "left", "Finger numbers"))
        assertEquals("Tomorrow: the right hand of Ode to Joy", Tomorrow.line("Ode to Joy", "right", null))
        assertEquals("Tomorrow: Minuet in G with both hands", Tomorrow.line("Minuet in G", "together", null))
        assertEquals("Tomorrow: Finger numbers", Tomorrow.line("Twinkle", null, "Finger numbers"))
        assertEquals("Tomorrow: Twinkle again", Tomorrow.line("Twinkle", null, null))
        assertNull(Tomorrow.line(null, "left", null))
        assertNull(Tomorrow.line(null, null, null))
    }

    @Test fun retentionEventsCarryTheProfileKindOnly() {
        assertEquals(TesterEvent("comeback_shown", mapOf("profile_kind" to "kid")), Retention.event(Retention.COMEBACK_SHOWN, kid = true))
        assertEquals(TesterEvent("surprise_opened", mapOf("profile_kind" to "kid")), Retention.event(Retention.SURPRISE_OPENED, kid = true))
        assertEquals(mapOf("profile_kind" to "adult"), Retention.event(Retention.REST_DAY_USED, kid = false).props)
        assertEquals(listOf("comeback_shown", "comeback_played", "rest_day_used", "surprise_wrapped", "surprise_opened"), Retention.ALL)
        Retention.ALL.forEach { assertEquals(setOf("profile_kind"), Retention.event(it, kid = false).props.keys) }
    }
}
