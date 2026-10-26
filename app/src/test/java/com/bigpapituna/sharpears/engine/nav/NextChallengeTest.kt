package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.DrillScore
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionSong
import com.bigpapituna.sharpears.engine.SessionWords
import com.bigpapituna.sharpears.engine.family.Level
import com.bigpapituna.sharpears.engine.planSession
import com.bigpapituna.sharpears.engine.play.QuestKind
import com.bigpapituna.sharpears.engine.play.QuestTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14 depth: Today's next challenge, who gets it, what it is and how the plan walks it. */
class NextChallengeTest {
    private val songs = listOf(
        NextChallenge.SongChoice("minuet-g", "Minuet in G", "medium", 120.0, 1.6),
        NextChallenge.SongChoice("fur-elise", "Fur Elise", "medium", 72.0, 2.2, threeStars = true),
        NextChallenge.SongChoice("greensleeves", "Greensleeves", "medium", 96.0, 1.4),
        NextChallenge.SongChoice("moonlight-1", "Moonlight Sonata, 1st movement", "hard", 54.0, 6.0),
        NextChallenge.SongChoice("twinkle", "Twinkle, Twinkle, Little Star", "easy", 100.0, 0.8),
    )

    @Test fun whoGetsOne() {
        assertTrue(NextChallenge.eligible(kid = false, placement = Level.SOME, unitIndex = 0))
        assertTrue(NextChallenge.eligible(false, Level.A_LOT, 0))
        assertFalse(NextChallenge.eligible(false, Level.A_LITTLE, 2))
        assertTrue("past Basics, Beginner and Rhythm", NextChallenge.eligible(false, Level.NEVER, 3))
        assertFalse("kids never", NextChallenge.eligible(true, Level.A_LOT, 6))
        assertEquals(1, NextChallenge.tier(Level.SOME, 3))
        assertEquals(2, NextChallenge.tier(Level.A_LOT, 0))
        assertEquals(2, NextChallenge.tier(Level.SOME, 4))
    }

    @Test fun roundRobinOneKindADayInTurn() {
        val week = (100L until 106L).map { NextChallenge.kindFor(it, seed = 7) }
        assertEquals(NextChallenge.Kind.entries.toSet(), week.take(3).toSet())
        assertEquals(week.take(3), week.drop(3))
        // Another profile's seed starts the turn elsewhere.
        assertTrue((0L until 3L).any { NextChallenge.kindFor(100, it) != NextChallenge.kindFor(100, 7) })
    }

    @Test fun aSongAtTheirLevelWithATempoTarget() {
        val day = (0L until 3L).first { NextChallenge.kindFor(it, 0) == NextChallenge.Kind.PERFORM }
        val c = NextChallenge.pick(1, day, 0, songs, staffOpen = true) { null } as Challenge.Perform
        assertTrue(c.songId in setOf("minuet-g", "greensleeves"))            // medium, not yet three-starred
        assertEquals("3 stars at ${c.bpm} bpm", c.target)
        assertTrue(c.bpm <= c.songBpm && c.speed <= 1.0)
        val hard = NextChallenge.pick(2, day, 0, songs, true) { null } as Challenge.Perform
        assertEquals("moonlight-1", hard.songId)
        assertEquals(50, hard.bpm)                                            // all of 54 bpm, to the 5 below
        assertEquals(100, NextChallenge.targetBpm(120.0, 0.85))
        assertEquals(60, NextChallenge.targetBpm(72.0, 0.85))
        assertEquals(95, NextChallenge.targetBpm(96.0, 1.0))                  // never faster than the song itself
        // The song moves on each time the kind comes round.
        val next = NextChallenge.pick(1, day + 3, 0, songs, true) { null } as Challenge.Perform
        assertTrue(next.songId != c.songId)
    }

    @Test fun aSightReadingDrillOrChordChanges() {
        val sightDay = (0L until 3L).first { NextChallenge.kindFor(it, 0) == NextChallenge.Kind.SIGHT }
        val sight = NextChallenge.pick(1, sightDay, 0, songs, true) { null } as Challenge.SightRead
        assertEquals(4, sight.level)
        assertEquals("15 right in 2 minutes", sight.target)
        assertEquals("Sight-read: level 4, treble staff", sight.title)
        // A best of 20 at a level moves the drill up one; the goal is one more than the best there.
        val up = NextChallenge.pick(1, sightDay, 0, songs, true) { if (it == 4) DrillScore(22, 1) else if (it == 5) DrillScore(16, 0) else null } as Challenge.SightRead
        assertEquals(5, up.level); assertEquals(17, up.goal)
        // Staff levels closed: the next kind in turn.
        assertFalse(NextChallenge.pick(1, sightDay, 0, songs, staffOpen = false) { null } is Challenge.SightRead)
        val chordDay = (0L until 3L).first { NextChallenge.kindFor(it, 0) == NextChallenge.Kind.CHORDS }
        val ch = NextChallenge.pick(1, chordDay, 0, songs, true) { null } as Challenge.ChordChange
        assertTrue(ch.progression in listOf(0, 2))
        assertEquals("3 stars at 80 bpm", ch.target)
        assertTrue(ch.title, ch.title.startsWith("Chord changes: I-"))
        assertEquals(100, (NextChallenge.pick(2, chordDay, 0, songs, true) { null } as Challenge.ChordChange).bpm)
        // No song at the tier, staff closed: chord changes still.
        assertTrue(NextChallenge.pick(1, 0, 0, emptyList(), false) { null } is Challenge.ChordChange)
    }

    @Test fun thePlanTakesItInPlaceOfTheEasySong() {
        val minuet = SessionSong("minuet-g", "Minuet in G", 1.6, troubleBar = null)
        val c = Challenge.Perform("greensleeves", "Greensleeves", 80, 96.0, 1.4)
        val plan = planSession(listOf("c:C"), "scales" to "Scales", minuet, challenge = c)
        assertEquals(listOf("Chords", "Lesson", "Challenge"), plan.parts.map { it::class.simpleName })
        // A worst bar on a loop stays; the challenge comes after it.
        val loop = planSession(emptyList(), null, minuet.copy(troubleBar = 4), challenge = c)
        assertEquals(listOf("Loop", "Challenge"), loop.parts.map { it::class.simpleName })
        // 2.14 merge: a beginner's lesson first (levels-ages' lessonFirst) and the challenge last (depth) in one plan.
        val first = planSession(listOf("c:C"), "scales" to "Scales", minuet, lessonFirst = true, challenge = c)
        assertEquals(listOf("Lesson", "Chords", "Challenge"), first.parts.map { it::class.simpleName })
        // Today's row and the words around the part.
        val card = TodayCard.of(plan, summary = null)
        assertEquals(TodayRow(TodayRow.Kind.CHALLENGE, "Greensleeves", "Next challenge: 3 stars at 80 bpm"), card.rows.last())
        val part = plan.parts.last() as SessionPart.Challenge
        assertEquals("Next: a challenge. Greensleeves, 3 stars at 80 bpm.", part.next)
        assertEquals("Challenge", SessionWords.chip(part))
        assertTrue(SessionWords.about(part).startsWith("At your level, aim for 3 stars at 80 bpm."))
        // A challenge song is the quest's song task; a drill is no task, the top-ups fill in.
        assertEquals(QuestTask(QuestKind.SONG, "Play Greensleeves to the end", 1, "greensleeves"), com.bigpapituna.sharpears.engine.play.Quest.adultTasks(plan).firstOrNull { it.kind == QuestKind.SONG })
        val drill = planSession(emptyList(), null, null, challenge = Challenge.SightRead(4, 15))
        assertEquals(3, com.bigpapituna.sharpears.engine.play.Quest.adultTasks(drill).size)
        // Without a challenge the plan is as before.
        assertNull(planSession(emptyList(), null, minuet).parts.firstOrNull { it is SessionPart.Challenge })
        listOf(part.next, part.row, SessionWords.about(part), card.rows.last().detail).forEach { assertFalse(it, it.contains('—') || it.contains('–')) }
    }
}
