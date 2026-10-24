package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.planSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QuestTest {
    private val day = LocalDate.of(2026, 10, 18)
    private val fullPlan = planSession(listOf("c1", "c2", "c3"), "rests" to "Rests", SessionSong("minuet", "Minuet in G", 2.0, troubleBar = 12))

    @Test fun kidsGetTheirThreeTasksWhateverThePlan() {
        val q = Quest.forDay(day, kid = true, plan = fullPlan)
        assertEquals(listOf("Play two songs", "Copy the pup three times", "Find all the Cs"), q.tasks.map { it.title })
        assertEquals(listOf(2, 3, 5), q.tasks.map { it.target })
        assertEquals(0, q.stars)
    }

    @Test fun grownUpsGetTheirTasksFromThePlan() {
        val q = Quest.forDay(day, kid = false, plan = fullPlan)
        assertEquals(listOf("Review 3 due chords", "Finish the lesson Rests", "Smooth out bar 12 of Minuet in G"), q.tasks.map { it.title })
        assertEquals(listOf(QuestKind.DUE_CHORDS, QuestKind.LESSON, QuestKind.TROUBLE_BAR), q.tasks.map { it.kind })
        assertEquals("rests", q.tasks[1].ref)
        assertEquals("minuet", q.tasks[2].ref)
        // A song with no trouble bar is played through; one due chord reads as one.
        val song = Quest.forDay(day, false, planSession(listOf("c1"), null, SessionSong("ode", "Ode to Joy", 1.5, null)))
        assertEquals(listOf("Review the chord that is due", "Play Ode to Joy to the end", "Play two songs to the end"), song.tasks.map { it.title })
    }

    @Test fun anEmptyPlanIsToppedUpToThree() {
        for (plan in listOf(null, planSession(emptyList(), null, null))) {
            val q = Quest.forDay(day, false, plan)
            assertEquals(listOf(QuestKind.PLAY_SONGS, QuestKind.COPY_PUP, QuestKind.LESSON), q.tasks.map { it.kind })
            assertNull(q.tasks[2].ref)                                                 // any lesson
        }
        // A plan with a lesson does not get "Finish any lesson" on top.
        val q = Quest.forDay(day, false, planSession(emptyList(), "rests" to "Rests", null))
        assertEquals(listOf(QuestKind.LESSON, QuestKind.PLAY_SONGS, QuestKind.COPY_PUP), q.tasks.map { it.kind })
    }

    @Test fun eventsMoveTheRightTasksAndEachCompletionIsOneStar() {
        val q = Quest.forDay(day, true, null)
        assertTrue(q.record(QuestEvent.SongFinished("ode")).isEmpty())
        val done = q.record(QuestEvent.SongFinished("ode"))                            // the same song twice counts twice
        assertEquals(listOf(QuestKind.PLAY_SONGS), done.map { it.kind })
        assertTrue(q.record(QuestEvent.SongFinished("mary")).isEmpty())               // already done: no second star
        assertEquals(1, q.stars)
        repeat(2) { q.record(QuestEvent.PupCopied(it + 2)) }
        assertEquals(2, q.tasks[1].done)
        assertEquals(QuestKind.COPY_PUP, q.record(QuestEvent.PupCopied(4)).single().kind)
        assertEquals(2, q.stars)
        assertFalse(q.allDone)
    }

    @Test fun findAllTheCsCountsEachCOnceAndNothingElse() {
        val q = Quest.forDay(day, true, null)
        listOf(60, 60, 62, 61, 72, 48).forEach { q.record(QuestEvent.NotePlayed(it)) }
        assertEquals(3, q.tasks[2].done)
        assertEquals(setOf("48", "60", "72"), q.tasks[2].seen)
        q.record(QuestEvent.NotePlayed(24))                                           // C1: not one of the five
        q.record(QuestEvent.NotePlayed(36))
        assertEquals(QuestKind.FIND_CS, q.record(QuestEvent.NotePlayed(84)).single().kind)
        // Or the find-c lesson does it in one go.
        val other = Quest.forDay(day, true, null)
        assertEquals(QuestKind.FIND_CS, other.record(QuestEvent.LessonDone("find-c")).single().kind)
        assertTrue(other.record(QuestEvent.LessonDone("find-c")).isEmpty())
    }

    /**
     * QA 2.8.3: the quest opens the Jam, whose keys are C4 to C5, so by touch only two of the five Cs could ever be played and
     * the star never came. Touched, every C on the keys there is all the Cs; a piano (mic or MIDI) still needs all five.
     */
    @Test fun findAllTheCsCanBeFinishedOnTheJamsKeys() {
        val jam = 60..72
        val q = Quest.forDay(day, true, null)
        assertTrue(q.record(QuestEvent.NotePlayed(60, touchKeys = jam)).isEmpty())
        assertEquals(1, q.tasks[2].done)
        assertTrue("not a C", q.record(QuestEvent.NotePlayed(64, touchKeys = jam)).isEmpty())
        assertEquals(QuestKind.FIND_CS, q.record(QuestEvent.NotePlayed(72, touchKeys = jam)).single().kind)
        assertTrue(q.tasks[2].complete)
        // Heard on a piano, C4 and C5 are two of five.
        val piano = Quest.forDay(day, true, null)
        listOf(60, 72).forEach { piano.record(QuestEvent.NotePlayed(it)) }
        assertFalse(piano.tasks[2].complete)
        assertEquals(2, piano.tasks[2].done)
        // A C from the piano and the other from the screen: the screen's two are found, so it is done.
        val both = Quest.forDay(day, true, null)
        both.record(QuestEvent.NotePlayed(72))
        assertEquals(QuestKind.FIND_CS, both.record(QuestEvent.NotePlayed(60, touchKeys = jam)).single().kind)
        // Keys with no C on them never finish it.
        val none = Quest.forDay(day, true, null)
        assertTrue(none.record(QuestEvent.NotePlayed(62, touchKeys = 61..71)).isEmpty())
        assertFalse(none.tasks[2].complete)
    }

    @Test fun grownUpTasksMatchTheirSongLessonAndChords() {
        val q = Quest.forDay(day, false, fullPlan)
        q.record(QuestEvent.ChordReviewed("c1")); q.record(QuestEvent.ChordReviewed("c1"))
        assertEquals(1, q.tasks[0].done)                                             // the same chord twice is one
        q.record(QuestEvent.ChordReviewed("x")); assertEquals(QuestKind.DUE_CHORDS, q.record(QuestEvent.ChordReviewed("y")).single().kind)
        assertTrue(q.record(QuestEvent.LessonDone("other")).isEmpty())
        assertEquals(QuestKind.LESSON, q.record(QuestEvent.LessonDone("rests")).single().kind)
        assertTrue(q.record(QuestEvent.LoopDone("ode", 3)).isEmpty())
        assertEquals(QuestKind.TROUBLE_BAR, q.record(QuestEvent.LoopDone("minuet", 12)).single().kind)
        assertTrue(q.allDone)
        assertEquals(3, q.stars)
    }

    @Test fun aQuestLastsItsDay() {
        val q = Quest.forDay(day, true, null)
        q.record(QuestEvent.SongFinished("a"))
        assertSame(q, Quest.today(q, day, true, null))
        val tomorrow = Quest.today(q, day.plusDays(1), true, null)
        assertEquals(day.plusDays(1), tomorrow.day)
        assertEquals(0, tomorrow.tasks[0].done)
        // Switched to grown-up mode during the day: a grown-up's quest.
        assertFalse(Quest.today(q, day, false, fullPlan).kid)
    }

    @Test fun aQuestSurvivesBeingSaved() {
        val q = Quest.forDay(day, false, fullPlan)
        q.record(QuestEvent.ChordReviewed("c2")); q.record(QuestEvent.LessonDone("rests"))
        val back = Quest.decode(q.encode())!!
        assertEquals(q.day, back.day); assertEquals(q.kid, back.kid); assertEquals(q.tasks, back.tasks)
        assertNull(Quest.decode(null)); assertNull(Quest.decode("{")); assertNull(Quest.decode("""{"day":"x"}"""))
        assertNull(Quest.decode("""{"day":"2026-10-18","kid":true,"tasks":[{"kind":"NOPE","title":"t","target":1,"ref":null,"done":0,"seen":[]}]}"""))
    }

    // ---- PlayBook: the per-profile memory behind data/PlayStore.kt ----

    private class MapStorage : PlayStorage {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    @Test fun theBookKeepsTodaysQuestAndItsProgress() {
        val s = MapStorage(); val book = PlayBook(s)
        val q = book.quest(day, false, fullPlan)
        assertTrue(s.map.containsKey(PlayBook.KEY_QUEST))
        assertEquals(QuestKind.LESSON, book.recordQuest(QuestEvent.LessonDone("rests"), day, false).single().kind)
        val again = PlayBook(s).quest(day, false, null)                               // a new plan later in the day changes nothing
        assertEquals(q.tasks.map { it.title }, again.tasks.map { it.title })
        assertEquals(1, again.stars)
        assertEquals(0, PlayBook(s).quest(day.plusDays(1), false, null).stars)
    }

    @Test fun aKidsQuestIsMadeOnTheFirstEventButAGrownUpsWaitsForThePlan() {
        val kid = MapStorage()
        assertTrue(PlayBook(kid).recordQuest(QuestEvent.SongFinished("a"), day, true).isEmpty())
        assertEquals(1, PlayBook(kid).quest(day, true, null).tasks[0].done)
        val adult = MapStorage()
        assertTrue(PlayBook(adult).recordQuest(QuestEvent.SongFinished("a"), day, false).isEmpty())
        assertFalse(adult.map.containsKey(PlayBook.KEY_QUEST))
    }

    @Test fun pupSaysKeepsTheBestOfTheDay() {
        val s = MapStorage(); val book = PlayBook(s)
        assertEquals(0, book.pupSaysBest(day))
        assertEquals(PupSaysResult("p1", 5), book.savePupSays("p1", 5, day))
        assertEquals(PupSaysResult("p1", 5), book.savePupSays("p1", 3, day))          // a shorter run keeps the best
        assertEquals(7, book.savePupSays("p1", 7, day).best)
        assertEquals(0, book.pupSaysBest(day.plusDays(1)))                            // a new day starts at nothing
        assertEquals(2, book.savePupSays("p1", 2, day.plusDays(1)).best)
        s.map[PlayBook.KEY_PUP_SAYS] = "garbage"
        assertEquals(0, book.pupSaysBest(day))
    }
}
