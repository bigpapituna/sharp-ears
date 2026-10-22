package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.planSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.7: what the Today card is made of (today's Practice today plan as a short list) and what Continue opens. */
class TodayTest {
    private val chords = listOf("c:C", "c:F", "c:G")
    private val minuet = SessionSong("minuet-g", "Minuet in G", 1.6, troubleBar = null)

    @Test fun thePlanIsChordsThenTheLessonThenTheSongInTheOrderStartWalksIt() {
        val card = TodayCard.of(planSession(chords, "rests" to "Rests", minuet), summary = null)
        assertEquals(TodayCard.State.PLAN, card.state)
        assertEquals(listOf(TodayRow.Kind.CHORDS, TodayRow.Kind.LESSON, TodayRow.Kind.SONG), card.rows.map { it.kind })
        assertEquals(listOf("3 chords due", "Rests", "Minuet in G"), card.rows.map { it.title })
        assertEquals(listOf("From the daily chord review", "Your next lesson", "With the notes waiting for you"), card.rows.map { it.detail })
        assertEquals("about 10 min", card.about)
    }

    @Test fun aTroubleBarIsTheSongsRowAndOneChordIsSingular() {
        val card = TodayCard.of(planSession(listOf("c:C"), null, minuet.copy(troubleBar = 12)), summary = null)
        assertEquals(listOf("1 chord due", "Minuet in G"), card.rows.map { it.title })
        assertEquals("Bar 12, two minutes on a loop", card.rows[1].detail)
    }

    @Test fun onlyWhatThePlanHasIsListed() {
        assertEquals(listOf(TodayRow.Kind.LESSON), TodayCard.of(planSession(emptyList(), "rests" to "Rests", null), null).rows.map { it.kind })
        // Never more than the plan's three parts, however many chords are due (the plan takes six at most).
        val busy = TodayCard.of(planSession((1..20).map { "c:$it" }, "rests" to "Rests", minuet), null)
        assertEquals(3, busy.rows.size)
        assertEquals("6 chords due", busy.rows[0].title)
    }

    @Test fun nothingDueAndDoneForToday() {
        val empty = TodayCard.of(planSession(emptyList(), null, null), summary = null)
        assertEquals(TodayCard.State.NOTHING_DUE, empty.state)
        assertEquals(emptyList<TodayRow>(), empty.rows)
        // Once today's session ran, the card says so, whatever is still in the plan.
        val done = TodayCard.of(planSession(chords, "rests" to "Rests", minuet), summary = "Done today: 3 chords. 4 min.")
        assertEquals(TodayCard.State.DONE, done.state)
        assertEquals("Done today: 3 chords. 4 min.", done.summary)
    }

    @Test fun continueOpensWhatWasPlayedLast() {
        val all = setOf<ContinueWith>(ContinueWith.Song("twinkle"), ContinueWith.Song("ode"), ContinueWith.PopSong("wagon-wheel"), ContinueWith.Lesson("rests"))
        val exists = { c: ContinueWith -> c in all }
        assertEquals(ContinueWith.Lesson("rests"), ContinueWith.of("lesson:rests", "twinkle", exists))
        assertEquals(ContinueWith.PopSong("wagon-wheel"), ContinueWith.of("pop:wagon-wheel", "twinkle", exists))
        assertEquals(ContinueWith.Song("ode"), ContinueWith.of("song:ode", "twinkle", exists))
        // From before 2.7: only the last song was kept.
        assertEquals(ContinueWith.Song("twinkle"), ContinueWith.of(null, "twinkle", exists))
        // A lesson gone from the course or a deleted song falls back to the last song, then to nothing ("Start here").
        assertEquals(ContinueWith.Song("twinkle"), ContinueWith.of("lesson:gone", "twinkle", exists))
        assertNull(ContinueWith.of("song:deleted", "deleted", exists))
        assertNull(ContinueWith.of(null, null, exists))
        assertNull(ContinueWith.decode("garbage"))
        for (c in all) assertEquals(c, ContinueWith.decode(ContinueWith.encode(c)))
    }
}
