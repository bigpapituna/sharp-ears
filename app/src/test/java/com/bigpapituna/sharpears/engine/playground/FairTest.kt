package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.3, the fair (part A): a short page of four reward games, the last before the big kids' road. Each is a game of three
 * rounds with a star a round; held to every page's checks.
 */
class FairTest {
    private val lessons = FAIR.lessons

    @Test fun fourGamesOnTheLastPage() {
        assertEquals((1..4).map { "fair-$it" }, lessons.map { it.id })
        assertEquals(listOf("Balloon pop", "The carousel", "The strongman", "The photo booth"), lessons.map { it.title })
        assertEquals("playground-fair", FAIR_ID)
        val page = PLAYGROUND_PAGES.last()
        assertEquals("fair", page.id)
        assertEquals(Place.FAIR, page.place)
        assertEquals("fair-4", page.lastLessonId)
        for (l in lessons) {
            assertEquals("${l.id} starts by reading", StepKind.READ, l.steps.first().kind)
            assertTrue(l.steps.size in 2..7)
        }
        // The fair's last game leads a kid to the big kids' road; a grown-up has the fair as an extra.
        assertEquals("sit", CoursePath(PLAYGROUND_ID).after(lessons.last())?.id)
        assertTrue(CoursePath("basics").isExtra(FAIR))
    }

    @Test fun readStepsAreOneToThreeScenes() = PlaygroundChecks.readStepsAreOneToThreeScenes(lessons)

    @Test fun copyIsWrittenForSmallChildren() {
        PlaygroundChecks.copyIsWrittenForSmallChildren(FAIR)
        PlaygroundChecks.linesAreKind(FairLines.ALL)
    }

    @Test fun everyExerciseParsesAndPlaysToTheEnd() = PlaygroundChecks.everyExerciseParsesAndPlaysToTheEnd(lessons)

    @Test fun lessonsTakeTwoToFourMinutes() { PlaygroundChecks.lessonsTakeTwoToFourMinutes(lessons) }

    /** Every game is three rounds (three stars), in the C position (the strongman: middle C, any key near it will do). */
    @Test fun everyGameIsThreeRounds() {
        val games = lessons.flatMap { it.steps }.filter { it.game != null }
        assertEquals(listOf(Game.BALLOONS, Game.BALLOONS, Game.CAROUSEL, Game.STRONGMAN, Game.STRONGMAN, Game.PHOTO), games.map { it.game })
        for (s in games) {
            val ex = s.exercise!!
            assertEquals(s.text, 3, StageRounds(ex.toSong("f", "f"), ex.practice, phrases = true).rounds)
            if (s.game != Game.PHOTO) assertTrue(s.text, ex.toSong("f", "f").notes.all { it.midi in StageKeys.FIVE })
            // Each note's finger in the C position.
            val f = Fingering.of(ex.toSong("f", "f"))
            ex.toSong("f", "f").notes.filter { it.hand == Hand.RIGHT }.forEach { n ->
                assertEquals(s.text, mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)[n.midi] ?: f[n], f[n])
            }
        }
        // The carousel's runs: up to G and back, nine notes.
        val runs = lessons[1].steps.first { it.game == Game.CAROUSEL }.exercise!!
        assertEquals(27, runs.toSong("c", "c").notes.size)
    }

    @Test fun thePhotoBoothTakesThreePhotosOfAnySong() {
        for (song in SHELF) {
            val ex = photoExercise(song)
            assertEquals(song.title, PHOTOS, StageRounds(ex.toSong("p", "p"), ex.practice, phrases = true).rounds)
            assertTrue(song.title, StageKeys.of(ex).size <= 12)
            assertEquals(song.title, ex.toSong("p", "p").notes.size, ex.fingers.split(' ').count { it.isNotBlank() })
            assertEquals(song.title, PHOTOS * song.start.toSong("s", "s").notes.count { it.hand == Hand.RIGHT }, ex.toSong("p", "p").notes.size)
        }
        assertEquals(photoExercise(SHELF.first()), lessons[3].steps.first { it.game == Game.PHOTO }.exercise)
    }

    @Test fun theStrongmanTakesAnyKeyAndCheersEveryHeight() {
        val heard = StageSound(0, HeardBy.MATCHER, 57, riseDb = -35.0, final = true)
        assertEquals(Answer.Found(60), StrongmanJudge.judge(heard, 60))
        assertEquals(Answer.Found(60), StrongmanJudge.judge(heard.copy(by = HeardBy.NAMED, midi = 74), 60))
        assertEquals(Answer.Heard, StrongmanJudge.judge(StageSound(0, HeardBy.ONSET, final = true), 60))
        // Higher for a bigger sound, the bell at a loud note, a kid's lighter hand counted.
        val heights = listOf(-60.0, -46.0, -40.0, -34.0, -28.0, -20.0).map { StrongmanJudge.height(it) }
        assertTrue(heights.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(0.1f, heights.first(), 0.001f)
        assertEquals(StrongmanJudge.BELL, StrongmanJudge.height(SoftLoud.LOUD_AT_LEAST_DB), 0.001f)
        assertTrue(StrongmanJudge.height(-31.0, offsetDb = 3.0) >= StrongmanJudge.BELL)
        // Every height gets a cheer, never a fail.
        assertEquals(4, listOf(0.2f, 0.5f, 0.8f, 1f).map(FairLines::height).toSet().size)
    }

    @Test fun theCarouselSpinsFasterWithSmoothRuns() {
        val speeds = (0..3).map(::carouselSpeed)
        assertTrue(speeds.zipWithNext().all { (a, b) -> b > a })
        assertEquals(speeds.last(), carouselSpeed(9))
    }

    @Test fun theBalloonsAreCalledByColourOrLetter() {
        assertEquals("Pop the red balloon!", FairLines.balloon(60, reader = false))
        assertEquals("Pop the C balloon!", FairLines.balloon(60, reader = true))
        assertEquals("Now the teal one!", FairLines.next(67, reader = false))
        assertEquals("Now E!", FairLines.next(64, reader = true))
        for (k in StageKeys.FIVE) for (r in listOf(true, false)) assertTrue(FairLines.balloon(k, r) in FairLines.ALL && FairLines.next(k, r) in FairLines.ALL)
    }
}
