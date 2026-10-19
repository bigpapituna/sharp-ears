package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pup's Playground (2.1): the unit, its twelve lessons, their games, copy and phrase cuts. */
class PlaygroundTest {
    private val lessons = PLAYGROUND.lessons

    @Test fun theUnitComesFirstWithTwelveLessons() {
        assertSame(PLAYGROUND, COURSE.first())
        assertEquals(PLAYGROUND_ID, COURSE.first().id)
        assertEquals("Pup's Playground", PLAYGROUND.title)
        assertEquals((1..12).map { "playground-$it" }, lessons.map { it.id })
        assertEquals(listOf("High and low", "Two black, three black", "Where C lives", "Pup's steps", "Hot Cross Buns", "Copy the pup",
            "The steady drum", "Mary Had a Little Lamb", "Left paw", "Up to G", "Soft and loud", "Pup's concert"), lessons.map { it.title })
        for (l in lessons) {
            assertEquals("${l.id} starts by reading", StepKind.READ, l.steps.first().kind)
            assertTrue("${l.id}: ${l.steps.size} steps", l.steps.size in 2..7)
            assertTrue("${l.id} has a game to play", l.steps.any { it.exercise != null })
        }
        // Three pages of twelve (2.2: the garden, the pond, the concert hall); the big-kid path is five of the garden's.
        assertEquals(PLAYGROUND_PAGES.sumOf { it.lessonIds.size }, PLAYGROUND_PLACES.size)
        assertEquals(List(12) { Place.GARDEN }, lessons.map { PLAYGROUND_PLACES.getValue(it.id) })
        assertEquals(lessons.map { it.id }, PLAYGROUND_PAGES.first().lessonIds)
        assertEquals(PLAYGROUND_PAGES.first(), pageOf("playground-12"))
        assertTrue(BIG_KID_LESSONS.all { id -> lessons.any { it.id == id } })
    }

    @Test fun readStepsAreOneToThreeScenes() = PlaygroundChecks.readStepsAreOneToThreeScenes(lessons)

    /** Short, warm, second person, sentence case; no dashes, no percentages, nothing called wrong. */
    @Test fun copyIsWrittenForSmallChildren() =
        PlaygroundChecks.copyIsWrittenForSmallChildren(PLAYGROUND, CONCERT_SONGS.map { it.title } + Place.entries.map { it.title })

    @Test fun everyExerciseParsesAndPlaysToTheEnd() = PlaygroundChecks.everyExerciseParsesAndPlaysToTheEnd(lessons)

    @Test fun anyMemberOfARangeWillDoAndTheOtherSideWillNot() {
        val updown = lessons[0].steps[3]
        assertEquals(Game.HIGH_LOW, updown.game)
        val song = updown.exercise!!.toSong("p1", "p1")
        assertEquals("three highs and three lows", 6, song.groups.count { it.anyOf })
        for (g in song.groups) {
            val keys = g.notes.map { it.midi }
            assertTrue("one side of middle C: $keys", keys.all { it > 60 } || keys.all { it < 60 })
            assertFalse("middle C itself is neither", 60 in keys)
        }
        // Answer each high with a different high key and each low with a different low one.
        var n = 0
        val t = PlaygroundChecks.playWait(song, Practice.BOTH) { g -> g.notes.map { it.midi }.let { k -> k[(n++ * 7) % k.size] } }
        assertEquals(6, t.report().groups)
        // A low key while the pup waits to fly: no advance.
        val u = Transport(song).apply { mode = Mode.WAIT; play() }
        while (!u.waiting) u.tick(10.0)
        assertEquals(InputResult.WRONG, u.noteInput(48))
        assertEquals(InputResult.WRONG, u.noteInput(60))
        assertEquals(InputResult.GROUP_DONE, u.noteInput(61))
    }

    @Test fun theHuntsAskForDogHousesFencesAndCs() {
        val pcs = { ex: Exercise -> ex.toSong("x", "x").groups.map { g -> g.notes.map { it.midi % 12 }.toSet() } }
        val hunts = lessons.flatMap { l -> l.steps.filter { it.game == Game.KEY_HUNT } }
        assertEquals(4, hunts.size)
        assertEquals(List(3) { setOf(1, 3) }, pcs(hunts[0].exercise!!))
        assertEquals(List(3) { setOf(6, 8, 10) }, pcs(hunts[1].exercise!!))
        assertEquals(listOf(setOf(1, 3), setOf(6, 8, 10), setOf(6, 8, 10), setOf(1, 3), setOf(6, 8, 10), setOf(1, 3)), pcs(hunts[2].exercise!!))
        assertEquals(List(4) { setOf(0) }, pcs(hunts[3].exercise!!))
        hunts.forEach { h -> h.exercise!!.toSong("x", "x").groups.forEach { g -> assertTrue(g.anyOf && g.notes.size <= 12) } }
        assertTrue("black keys only", hunts.take(3).all { h -> h.exercise!!.toSong("x", "x").notes.all { isBlack(it.midi) } })
    }

    @Test fun gamesSitWhereThePlanPutsThem() {
        fun games(n: Int) = lessons[n - 1].steps.mapNotNull { it.game }
        assertEquals(List(3) { Game.HIGH_LOW }, games(1))
        assertEquals(List(3) { Game.KEY_HUNT }, games(2))
        assertEquals(listOf(Game.KEY_HUNT), games(3))
        assertEquals(listOf(Game.CLAP, Game.CLAP), games(7))
        assertEquals(listOf(Game.SOFT_LOUD), games(11))
        assertEquals(listOf(Game.CONCERT, Game.CONCERT), games(12))
        // A game step still runs in the plain player when the screen does not know it.
        lessons.flatMap { it.steps }.filter { it.game != null }.forEach { assertTrue(it.kind == StepKind.PLAY || it.kind == StepKind.PERFORM) }
        // The drum: eight beats at 80.
        val drum = lessons[6].steps.first { it.game == Game.CLAP }.exercise!!
        assertEquals(80.0, drum.bpm, 0.0)
        assertEquals(8, drum.toSong("d", "d").groups.size)
        // Soft and loud: one note to set the level, then a note per round.
        assertEquals(SoftLoud.ROUNDS.size + 1, lessons[10].steps.first { it.game == Game.SOFT_LOUD }.exercise!!.toSong("s", "s").groups.size)
    }

    @Test fun theConcertPassesAtTwoStars() {
        val concert = lessons[11].steps.first { it.game == Game.CONCERT }
        assertEquals(StepKind.PERFORM, concert.kind)
        assertEquals(CONCERT_PASS, concert.exercise!!.pass)
        assertTrue("lower than the course's 80", CONCERT_PASS < 80)
        assertEquals(CONCERT_PASS, lessons[6].steps.last().exercise!!.pass)
        assertEquals(3, CONCERT_SONGS.size)
        for (c in CONCERT_SONGS) {
            val song = c.exercise.toSong("c", c.title)
            assertEquals(c.title, Practice.RIGHT, c.exercise.practice)
            assertTrue("${c.title}: the band plays the left hand", song.notes.any { it.hand == com.bigpapituna.sharpears.engine.Hand.LEFT })
            assertEquals(CONCERT_PASS, c.exercise.pass)
            assertNull(c.exercise.phraseEnd)
        }
        // Hot Cross Buns has 17 notes: missing 6 (65 out of a hundred) passes, missing 7 (59) does not.
        val buns = CONCERT_SONGS.first().exercise.toSong("b", "b")
        assertEquals(17, buns.notes.count { it.hand == com.bigpapituna.sharpears.engine.Hand.RIGHT })
        val six = PlaygroundChecks.perform(buns, Practice.RIGHT, skip = setOf(1, 4, 6, 9, 12, 15))
        val seven = PlaygroundChecks.perform(buns, Practice.RIGHT, skip = setOf(1, 4, 6, 9, 11, 12, 15))
        assertEquals(65, six.accuracy); assertEquals(59, seven.accuracy)
        assertTrue(concert.passed(six)); assertFalse(concert.passed(seven))
        assertFalse("the course's own pass mark would not have taken it", Step(StepKind.PERFORM, "x", CONCERT_SONGS.first().exercise.copy(pass = 80)).passed(six))
    }

    @Test fun lessonsTakeTwoToFourMinutes() { PlaygroundChecks.lessonsTakeTwoToFourMinutes(lessons) }

    /** 2.2: the kid bite cut the garden's "whole song" steps back to their first phrase; now they play whole. */
    @Test fun theWholeSongStepsPlayWhole() {
        for ((n, title) in listOf(5 to "Hot Cross Buns", 8 to "Mary Had a Little Lamb")) {
            val last = lessons[n - 1].steps.last()
            assertEquals(title, lessons[n - 1].title)
            assertNull("$title: no phrase end, so a kid plays it all", last.exercise!!.phraseEnd)
            assertEquals(last, com.bigpapituna.sharpears.engine.skin.Bites.kidStep(last))
        }
    }
}
