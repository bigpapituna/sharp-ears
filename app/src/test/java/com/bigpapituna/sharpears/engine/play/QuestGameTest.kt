package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeQuest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 2.7, the quest hook: on an arcade day a kid's middle task is a game, a round of it earns its star, and it is kept. */
class QuestGameTest {
    private val day = LocalDate.of(2026, 10, 22)
    private val balloons = ArcadeGame.BALLOONS.id to ArcadeQuest.title(ArcadeGame.BALLOONS)

    @Test fun aGameDayAsksForTheGameInsteadOfCopyingThePup() {
        val q = Quest.forDay(day, kid = true, plan = null, game = balloons)
        assertEquals(listOf("Play two songs", "Play Balloon pop", "Find all the Cs"), q.tasks.map { it.title })
        assertEquals(QuestKind.PLAY_GAME, q.tasks[1].kind)
        assertEquals("balloons", q.tasks[1].ref)
        // Another game's round does not count; this one's does, once.
        assertTrue(q.record(QuestEvent.GameRound("frogs")).isEmpty())
        assertEquals(QuestKind.PLAY_GAME, q.record(QuestEvent.GameRound("balloons")).single().kind)
        assertTrue(q.record(QuestEvent.GameRound("balloons")).isEmpty())
        assertEquals(1, q.stars)
        // Kept as it was made.
        assertEquals(q.tasks, Quest.decode(q.encode())!!.tasks)
        // Grown-ups never get one.
        assertTrue(Quest.forDay(day, kid = false, plan = null, game = balloons).tasks.none { it.kind == QuestKind.PLAY_GAME })
    }

    @Test fun theDaysQuestKeepsItsTasksWhateverIsOpenLater() {
        val store = HashMap<String, String>()
        val book = PlayBook(object : PlayStorage {
            override fun get(key: String) = store[key]
            override fun put(key: String, value: String) { store[key] = value }
        })
        val first = book.quest(day, kid = true, plan = null, game = balloons)
        assertEquals("Play Balloon pop", first.tasks[1].title)
        assertEquals(first.tasks, book.quest(day, kid = true, plan = null, game = null).tasks)
        assertEquals(QuestKind.PLAY_GAME, book.recordQuest(QuestEvent.GameRound("balloons"), day, kid = true).single().kind)
        // The next day without a game pick is the usual three.
        assertEquals(QuestKind.COPY_PUP, book.quest(day.plusDays(1), kid = true, plan = null).tasks[1].kind)
    }
}
