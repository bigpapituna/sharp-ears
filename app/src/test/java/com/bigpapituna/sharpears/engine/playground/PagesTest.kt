package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.EIGHTHS_PATTERN
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.HALVES_PATTERN
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.LIGHTLY_X
import com.bigpapituna.sharpears.engine.LONDON_X
import com.bigpapituna.sharpears.engine.MACDONALD_X
import com.bigpapituna.sharpears.engine.ODE_LINE
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.ROW_X
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.YANKEE_X
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.2: the Playground's second and third pages, the pond and the concert hall. The contract the kid home codes against
 * (PLAYGROUND_PAGES), their place in the course, and every lesson held to the garden's checks: one to three scenes a
 * read, copy for small children, every exercise playable, two to four minutes; the games where the plan puts them;
 * the songs the course already has reused note for note, cut at their first phrase.
 */
class PagesTest {
    private val pond = POND.lessons
    private val hall = HALL.lessons

    @Test fun threePagesInPathOrder() {
        assertEquals(listOf("garden", "pond", "hall"), PLAYGROUND_PAGES.map { it.id })
        assertEquals(listOf(PLAYGROUND_ID, POND_ID, HALL_ID), PLAYGROUND_PAGES.map { it.levelId })
        assertEquals(listOf("playground-pond", "playground-hall"), listOf(POND_ID, HALL_ID))
        assertEquals(listOf(Place.GARDEN, Place.POND, Place.CONCERT_HALL), PLAYGROUND_PAGES.map { it.place })
        assertEquals(listOf("The garden", "The pond", "The concert hall"), PLAYGROUND_PAGES.map { it.title })
        assertEquals((1..12).map { "pond-$it" }, PLAYGROUND_PAGES[1].lessonIds)
        assertEquals((1..12).map { "hall-$it" }, PLAYGROUND_PAGES[2].lessonIds)
        assertEquals(pond.map { it.id }, PLAYGROUND_PAGES[1].lessonIds)
        assertEquals(hall.map { it.id }, PLAYGROUND_PAGES[2].lessonIds)
        assertEquals("pond-12", PLAYGROUND_PAGES[1].lastLessonId)
        assertEquals("the last Playground lesson", "hall-12", PLAYGROUND_PAGES.last().lastLessonId)
        assertEquals(PLAYGROUND_PAGES[1], pageOf("pond-7"))
        assertEquals(PLAYGROUND_PAGES[2], pageOf("hall-1"))
        assertNull(pageOf("sit"))
        assertTrue(isPlaygroundLesson("hall-12") && isPlaygroundLesson("pond-1") && isPlaygroundLesson("playground-5"))
        assertFalse(isPlaygroundLesson("rep-row"))
        assertTrue(isPlaygroundLevel(POND_ID) && isPlaygroundLevel(HALL_ID) && !isPlaygroundLevel("basics"))
        assertEquals(Place.POND, PLAYGROUND_PLACES["pond-3"])
        assertEquals(Place.CONCERT_HALL, PLAYGROUND_PLACES["hall-12"])
        // A Perform step of any page lets go after three tries.
        assertTrue(StepRetry.letGo("pond-12", 3) && StepRetry.letGo("hall-7", 3))
        assertFalse(StepRetry.letGo("rep-london", 3))
    }

    @Test fun thePagesFollowTheGardenInTheCourse() {
        assertEquals(listOf(PLAYGROUND, POND, HALL), COURSE.take(3))
        assertEquals("basics", COURSE[3].id)
        assertSame(POND, COURSE.first { it.id == POND_ID })
        assertEquals("Pup's Pond", POND.title)
        assertEquals("Pup's Concert Hall", HALL.title)
    }

    @Test fun twelveLessonsEachInTheGardensShape() {
        assertEquals(listOf("Twinkle, Twinkle, Little Star", "Row, Row, Row Your Boat", "Copy the frogs", "Drum on a lily pad", "Ta and ti-ti",
            "Old MacDonald Had a Farm", "Left paw walks", "Both paws", "Your first chord", "London Bridge", "Twinkle all the way", "Pond concert"), pond.map { it.title })
        assertEquals(listOf("Letters on the keys", "Ode to Joy", "Lightly Row", "Yankee Doodle", "Both hands, five fingers", "Soft song, loud song",
            "With the band", "Ta-a and the rest", "Five notes to copy", "Hands together", "Pick your three", "The recital"), hall.map { it.title })
        for (l in pond + hall) {
            assertEquals("${l.id} starts by reading", StepKind.READ, l.steps.first().kind)
            assertTrue("${l.id}: ${l.steps.size} steps", l.steps.size in 2..7)
            assertTrue("${l.id} has a game to play", l.steps.any { it.exercise != null })
            // A game step still runs in the plain player when the screen does not know it.
            l.steps.filter { it.game != null }.forEach { assertTrue(l.id, it.kind == StepKind.PLAY || it.kind == StepKind.PERFORM) }
        }
    }

    @Test fun readStepsAreOneToThreeScenes() = PlaygroundChecks.readStepsAreOneToThreeScenes(pond + hall)

    @Test fun copyIsWrittenForSmallChildren() {
        PlaygroundChecks.copyIsWrittenForSmallChildren(POND, SHELF.map { it.title })
        PlaygroundChecks.copyIsWrittenForSmallChildren(HALL)
        PlaygroundChecks.linesAreKind(PageLines.ALL)
    }

    @Test fun everyExerciseParsesAndPlaysToTheEnd() = PlaygroundChecks.everyExerciseParsesAndPlaysToTheEnd(pond + hall)

    @Test fun lessonsTakeTwoToFourMinutes() { PlaygroundChecks.lessonsTakeTwoToFourMinutes(pond + hall) }

    @Test fun gamesSitWhereThePlanPutsThem() {
        fun games(lessons: List<com.bigpapituna.sharpears.engine.CourseLesson>, n: Int) = lessons[n - 1].steps.mapNotNull { it.game }
        assertEquals(listOf(Game.ECHO, Game.ECHO), games(pond, 3))
        assertEquals(listOf(Game.CLAP, Game.CLAP), games(pond, 4))
        assertEquals(listOf(Game.CLAP), games(pond, 5))
        assertEquals(listOf(Game.CHORD), games(pond, 9))
        assertEquals(listOf(Game.CONCERT, Game.CONCERT), games(pond, 12))
        assertEquals(List(3) { Game.KEY_HUNT }, games(hall, 1))
        assertEquals(listOf(Game.SOFT_LOUD_TUNE), games(hall, 6))
        assertEquals(listOf(Game.CLAP), games(hall, 8))
        assertEquals(listOf(Game.ECHO, Game.ECHO), games(hall, 9))
        assertEquals(listOf(Game.SET_LIST), games(hall, 11))
        assertEquals(List(3) { Game.CONCERT }, games(hall, 12))
        for (n in listOf(1, 2, 6, 7, 8, 10, 11)) assertTrue("pond-$n has no game", games(pond, n).isEmpty())
        for (n in listOf(2, 3, 4, 5, 7, 10)) assertTrue("hall-$n has no game", games(hall, n).isEmpty())
        // The drum at two speeds, 60 then 90.
        assertEquals(listOf(60.0, 90.0), pond[3].steps.filter { it.game == Game.CLAP }.map { it.exercise!!.bpm })
    }

    /** The songs the course already has are played note for note, cut at their first phrase (the kid's bite), then whole. */
    @Test fun songsAreTheCoursesOwnNotes() {
        fun rightOf(lesson: com.bigpapituna.sharpears.engine.CourseLesson) = lesson.steps.mapNotNull { it.exercise?.right }
        fun starts(whole: String, part: String) = whole.startsWith(part)
        for ((lesson, x, end) in listOf(Triple(pond[0], TWINKLE, 8.0), Triple(pond[1], ROW_X, 6.0), Triple(pond[5], MACDONALD_X, 8.0),
            Triple(pond[9], LONDON_X, 4.0), Triple(hall[1], ODE_LINE, 8.0), Triple(hall[2], LIGHTLY_X, 8.0), Triple(hall[3], YANKEE_X, 8.0))) {
            val bite = Bites.cut(x, end).right
            val parts = rightOf(lesson)
            assertTrue("${lesson.id} plays the first phrase: $bite", parts.count { it == bite } >= 2)
            assertTrue("${lesson.id} ends on the whole line", parts.last() == x.right)
            parts.forEach { assertTrue("${lesson.id}: $it is the song's own", starts(x.right, it)) }
            // The kid bite of the whole line is the whole line (no phrase end left on it).
            assertEquals(x.right, Bites.kidStep(lesson.steps.last()).exercise!!.right)
        }
        // Twinkle all the way: A, the middle, A again, the band on the left.
        val whole = pond[10].steps.last().exercise!!
        assertEquals(TWINKLE.right + " " + whole.right.removePrefix(TWINKLE.right + " ").removeSuffix(" " + TWINKLE.right) + " " + TWINKLE.right, whole.right)
        assertEquals(70.0, whole.bpm, 0.0)
        assertEquals(Practice.RIGHT, whole.practice)
        assertEquals("three lines of fourteen", 42, whole.toSong("t", "t").notes.count { it.hand == Hand.RIGHT })
        assertEquals(CONCERT_PASS, whole.pass)
        // The rhythm words reuse the Rhythm unit's bars.
        assertTrue(pond[4].steps.first { it.game == Game.CLAP }.exercise!!.right.startsWith(Bites.cut(Exercise(right = EIGHTHS_PATTERN), 4.0).right))
        assertTrue(hall[7].steps.first { it.game == Game.CLAP }.exercise!!.right.startsWith(Bites.cut(Exercise(right = HALVES_PATTERN), 4.0).right))
        // Both paws: the beginner "together" exercise (Twinkle, both hands) cut to two bars.
        assertEquals(TWINKLE.copy(phraseEnd = null).let { Bites.cut(it, 8.0) }.right, pond[7].steps.last().exercise!!.right)
    }

    @Test fun theLeftPawWalksLow() {
        val l = pond[6]
        for (s in l.steps.mapNotNull { it.exercise }) {
            assertEquals(Practice.LEFT, s.practice)
            assertTrue(s.toSong("l", "l").notes.all { it.midi in 48..55 && it.hand == Hand.LEFT })
        }
    }

    @Test fun theChordIsCEAndGTogether() {
        val ex = pond[8].steps.first { it.game == Game.CHORD }.exercise!!
        val song = ex.toSong("c", "c")
        assertEquals(3, song.groups.size)
        song.groups.forEach { assertEquals(StageKeys.C_CHORD, it.notes.map { n -> n.midi }.toSet()); assertFalse(it.anyOf) }
    }

    @Test fun theLetterHuntsAskForEveryLetter() {
        val pcs = { ex: Exercise -> ex.toSong("x", "x").groups.map { g -> g.notes.map { Math.floorMod(it.midi, 12) }.toSet() } }
        val hunts = hall[0].steps.filter { it.game == Game.KEY_HUNT }.map { it.exercise!! }
        assertEquals(List(3) { setOf(2) }, pcs(hunts[0]))
        assertEquals(listOf(setOf(4), setOf(5), setOf(4), setOf(5)), pcs(hunts[1]))
        assertEquals(listOf(setOf(7), setOf(0), setOf(2), setOf(5), setOf(4)), pcs(hunts[2]))
        for (h in hunts) for (g in h.toSong("x", "x").groups) {
            assertTrue(g.anyOf)
            val keys = g.notes.map { it.midi }
            assertEquals("the app shows the one by middle C", 60, keys.first() - Math.floorMod(keys.first(), 12))
            assertEquals(StageKeys.forHunt(Hunt.of(keys)!!), keys.toSet())
            assertTrue(keys.none(::isBlack) && keys.size <= 12)
        }
    }

    @Test fun phraseGamesHaveTheirRounds() {
        fun rounds(n: Int, lessons: List<com.bigpapituna.sharpears.engine.CourseLesson>, game: Game) =
            lessons[n - 1].steps.filter { it.game == game }.map { s -> StageRounds(s.exercise!!.toSong("p", "p"), s.exercise!!.practice, phrases = true) }
        // The frogs: three tunes of four, then two you know; the hall: five notes, two rounds, then two you know.
        assertEquals(listOf(3, 2), rounds(3, pond, Game.ECHO).map { it.rounds })
        assertEquals(listOf(2, 2), rounds(9, hall, Game.ECHO).map { it.rounds })
        val frogs = rounds(3, pond, Game.ECHO).first()
        assertEquals(listOf(60, 62, 64, 60), frogs.demoKeys())
        // Soft song, loud song: Ode to Joy's first phrase once per round.
        val tune = rounds(6, hall, Game.SOFT_LOUD_TUNE).single()
        assertEquals(TuneLoudness.ROUNDS.size, tune.rounds)
        assertTrue(hall[5].steps.first { it.game == Game.SOFT_LOUD_TUNE }.exercise!!.right.startsWith(ODE_LINE.bite().right + " r:2 "))
        // Every copy game and the tune listen in the C position: five keys, strict.
        for (s in (pond + hall).flatMap { it.steps }.filter { it.game == Game.ECHO || it.game == Game.SOFT_LOUD_TUNE })
            assertTrue(s.exercise!!.toSong("e", "e").notes.all { it.midi in StageKeys.FIVE })
    }

    @Test fun theConcertsPlayWithTheBandAtTwoStars() {
        assertEquals(listOf("Twinkle, Twinkle, Little Star", "Row, Row, Row Your Boat", "Old MacDonald Had a Farm"), POND_SONGS.map { it.title })
        val shelf = SHELF.map { it.concert }
        for (c in shelf) {
            assertEquals(c.title, Practice.RIGHT, c.exercise.practice)
            assertTrue("${c.title}: the band plays the left hand", c.exercise.toSong("c", c.title).notes.any { it.hand == Hand.LEFT })
            assertEquals(CONCERT_PASS, c.exercise.pass)
            assertNull(c.exercise.phraseEnd)
            assertTrue("${c.title} at a kid's tempo", c.exercise.bpm <= 84.0)
        }
        assertEquals(9, SHELF.size)
        assertEquals(SHELF.size, SHELF.map { it.id }.toSet().size)
        // The pond picks from its three; the garden from its own; the recital plays the kid's three, a step each.
        val pondConcert = pond[11].steps.first { it.game == Game.CONCERT }
        assertEquals(ConcertPlan(Place.POND, POND_SONGS, pick = true), concertPlan("pond-12", pondConcert))
        assertEquals(CONCERT_SONGS, concertPlan("playground-12", PLAYGROUND.lessons[11].steps.first { it.game == Game.CONCERT }).songs)
        val recital = hall[11].steps.filter { it.game == Game.CONCERT }
        assertEquals(Recital.SIZE, recital.size)
        val mine = listOf("yankee", "lamb", "bridge")
        assertEquals(mine.map { shelfSong(it)!!.concert }, recital.map { concertPlan("hall-12", it, mine).songs.single() })
        assertTrue(recital.all { !concertPlan("hall-12", it, mine).pick })
        // With nothing picked, the default three; the steps' own exercises are those, for a screen that does not know the game.
        assertEquals(Recital.songs(null).map { it.concert.exercise }, recital.map { it.exercise })
        assertEquals(Recital.songs(null).map { it.concert }, recital.map { concertPlan("hall-12", it).songs.single() })
        // Ode to Joy with the band at tempo, two stars to pass.
        val band = hall[6].steps.last()
        assertEquals(StepKind.PERFORM, band.kind)
        assertEquals(80.0, band.exercise!!.bpm, 0.0)
        assertEquals(CONCERT_PASS, band.exercise!!.pass)
    }

    @Test fun aRecitalIsThreeDistinctSongs() {
        assertEquals(Recital.DEFAULT, Recital.songs(null).map { it.id })
        assertEquals(listOf("row", "twinkle", "farm"), Recital.songs(listOf("row", "nope", "row")).map { it.id })
        assertEquals(listOf("a", "b"), Recital.parse(" a, b ,,"))
        assertEquals(listOf("lamb", "ode", "hat"), Recital.parse(Recital.format(listOf("lamb", "ode", "hat"))))
        assertEquals(listOf("lamb", "ode", "twinkle"), Recital.songs(listOf("lamb", "ode", "hat")).map { it.id })
        assertTrue(Recital.parse(null).isEmpty())
    }

    /** The kid home's finale plays after the last lesson; the path goes on to Basics. */
    @Test fun theRecitalIsTheLastPlaygroundLesson() {
        assertEquals("hall-12", COURSE.take(3).last().lessons.last().id)
        assertEquals("sit", COURSE[3].lessons.first().id)
    }
}
