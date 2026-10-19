package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Fingering
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.theory.ArmVisual
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import com.bigpapituna.sharpears.engine.theory.HandShapeVisual
import com.bigpapituna.sharpears.engine.theory.PedalVisual
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.PostureVisual
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.3, the workshop (part A): twelve lessons on how to play a piano, after the concert hall. Held to every page's checks
 * (PlaygroundChecks), plus what this page is about: the fingers each lesson names are the fingers its notes show, every
 * hand in its home; the app never claims to see a finger; the pictures each lesson needs are there.
 */
class WorkshopTest {
    private val lessons = WORKSHOP.lessons

    @Test fun twelveLessonsAfterTheHall() {
        assertEquals((1..12).map { "workshop-$it" }, lessons.map { it.id })
        assertEquals(listOf("Sit like a pianist", "The bubble hand", "Finger numbers", "Right hand home", "Left hand home", "Thumbs meet",
            "Wrist like a bridge", "Heavy arm, soft arm", "Short and long", "The pedal", "Two hands take turns", "The workshop concert"), lessons.map { it.title })
        assertEquals("playground-workshop", WORKSHOP_ID)
        val i = PLAYGROUND_PAGES.indexOfFirst { it.id == "workshop" }
        assertEquals("the page after the concert hall", "hall", PLAYGROUND_PAGES[i - 1].id)
        assertEquals(Place.WORKSHOP, PLAYGROUND_PAGES[i].place)
        assertEquals(lessons.map { it.id }, PLAYGROUND_PAGES[i].lessonIds)
        for (l in lessons) {
            assertEquals("${l.id} starts by reading", StepKind.READ, l.steps.first().kind)
            assertTrue("${l.id}: ${l.steps.size} steps", l.steps.size in 2..7)
            assertTrue("${l.id} has something to play", l.steps.any { it.exercise != null })
            l.steps.filter { it.game != null }.forEach { assertTrue(l.id, it.kind == StepKind.PLAY || it.kind == StepKind.PERFORM) }
        }
        assertTrue(StepRetry.letGo("workshop-12", 3))
    }

    /** Grown-ups see the workshop as an Extra unit, never as their next lesson. */
    @Test fun aGrownUpHasItAsAnExtra() {
        val adult = CoursePath("basics")
        assertTrue(adult.isExtra(WORKSHOP))
        assertTrue(adult.counted.none { it.id.startsWith("workshop-") })
        assertEquals("sit", adult.next { false }?.id)
        assertEquals("workshop-2", adult.after(lessons.first())?.id)
    }

    @Test fun readStepsAreOneToThreeScenes() = PlaygroundChecks.readStepsAreOneToThreeScenes(lessons)

    @Test fun copyIsWrittenForSmallChildren() {
        PlaygroundChecks.copyIsWrittenForSmallChildren(WORKSHOP, listOf("Mary Had a Little Lamb"))
        PlaygroundChecks.linesAreKind(WorkshopLines.ALL)
    }

    @Test fun everyExerciseParsesAndPlaysToTheEnd() = PlaygroundChecks.everyExerciseParsesAndPlaysToTheEnd(lessons)

    @Test fun lessonsTakeTwoToFourMinutes() { PlaygroundChecks.lessonsTakeTwoToFourMinutes(lessons) }

    @Test fun gamesSitWhereThePlanPutsThem() {
        fun games(n: Int) = lessons[n - 1].steps.mapNotNull { it.game }
        assertEquals(listOf(Game.PICK), games(1))
        assertEquals(listOf(Game.HAND, Game.HAND), games(2))
        assertEquals(listOf(Game.HAND, Game.HAND), games(3))
        assertEquals(listOf(Game.HAND), games(4))
        assertEquals(listOf(Game.HAND), games(5))
        assertTrue(games(6).isEmpty())
        assertEquals(listOf(Game.PICK), games(7))
        assertEquals(listOf(Game.SOFT_LOUD), games(8))
        assertEquals(listOf(Game.PICK), games(9))
        assertEquals(listOf(Game.PICK, Game.PEDAL), games(10))
        assertTrue(games(11).isEmpty())
        assertEquals(listOf(Game.CONCERT), games(12))
        assertEquals(mapOf("workshop-2" to HandLook.BUBBLE, "workshop-3" to HandLook.NUMBERS, "workshop-4" to HandLook.HOME), (2..4).associate { "workshop-$it" to handLook("workshop-$it") })
    }

    /** The pictures the plan asks for: the soft figure at the bench, the painted hand, the arm at the key, the foot on the pedal. */
    @Test fun theRealPicturesAreUsed() {
        fun visuals(n: Int) = lessons[n - 1].steps.flatMap { s -> s.scenes.map { it.visual } }
        val sit = visuals(1).filterIsInstance<PostureVisual>().flatMap { v -> v.steps.map { it.cue } }
        assertTrue("sit tall, not too close, feet flat", sit.containsAll(listOf(PostureCue.BACK, PostureCue.CLOSE, PostureCue.FEET)))
        assertTrue(visuals(2).any { it is HandShapeVisual && it.has(com.bigpapituna.sharpears.engine.theory.HandMove.BALL) })
        assertTrue(visuals(3).filterIsInstance<FingerNumbersVisual>().any { it.intro && it.rightKeys.isNotEmpty() && it.leftKeys.isNotEmpty() })
        assertTrue(visuals(7).filterIsInstance<ArmVisual>().flatMap { v -> v.steps.map { it.move } }.containsAll(com.bigpapituna.sharpears.engine.theory.ArmMove.entries.take(3)))
        assertTrue(visuals(8).any { it is ArmVisual })
        assertTrue(visuals(10).any { it is PedalVisual && it.foot })
        assertTrue(visuals(12).any { it is FingerNumbersVisual && it.steps.isNotEmpty() })
        // The arm's sounds land in its time.
        for (v in lessons.flatMap { l -> l.steps.flatMap { s -> s.scenes.map { it.visual } } }.filterIsInstance<ArmVisual>())
            v.cues.forEach { assertTrue(it.atMs in 0..v.durationMs) }
    }

    /** The app hears keys, not fingers: the copy asks for a finger and never says it saw one. */
    @Test fun theCopyNeverClaimsToSeeFingers() {
        val all = lessons.flatMap { l -> l.steps.flatMap { s -> listOf(s.text) + s.scenes.map { it.caption } } } + WorkshopLines.ALL
        val claims = Regex("\\b(I (can )?see|I saw|watching) (your|which|that) finger|(right|wrong|correct) finger|you used (your|the|a) finger", RegexOption.IGNORE_CASE)
        all.forEach { assertFalse(it, claims.containsMatchIn(it)) }
        assertTrue("the finger game says what it listens for", lessons[2].steps.first { it.game == Game.HAND }.text.contains("I hear the key"))
        assertTrue("short and long says what it listens for", lessons[8].steps.first().text.contains("I listen for when each note starts"))
        assertTrue("loudness is a guess, and says so", lessons[7].steps.first().text.contains("only guess"))
    }

    /** What the player shows for one hand of [ex]: each note's key and finger, in time order. */
    private fun lane(ex: Exercise, hand: Hand): List<Pair<Int, Int?>> {
        val song = ex.toSong("t", "t")
        val f = Fingering.of(song)
        return song.notes.filter { it.hand == hand }.map { it.midi to f[it] }
    }

    /** Every exercise names its fingers (book fingers, every note of each hand), and each hand plays from its home. */
    @Test fun everyNoteIsPlayedByItsHomeFinger() {
        val rightHome = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)
        val leftHome = mapOf(48 to 5, 50 to 4, 52 to 3, 53 to 2, 55 to 1)
        // "Thumbs meet": both thumbs on middle C, the left little finger on F below it.
        val leftMiddleC = mapOf(60 to 1, 59 to 2, 57 to 3, 55 to 4, 53 to 5)
        for (l in lessons) for (s in l.steps) {
            val ex = s.exercise ?: continue
            val song = ex.toSong("t", "t")
            val rights = song.notes.count { it.hand == Hand.RIGHT }; val lefts = song.notes.count { it.hand == Hand.LEFT }
            if (rights > 0) assertEquals("${l.id}: a book finger for every right-hand note", rights, ex.fingers.split(' ').count { it.isNotBlank() })
            if (lefts > 0 && ex.practice != Practice.RIGHT) assertEquals("${l.id}: a book finger for every left-hand note", lefts, ex.leftFingers.split(' ').count { it.isNotBlank() })
            for ((k, f) in lane(ex, Hand.RIGHT)) assertEquals("${l.id} right ${noteName(k)}", rightHome[k], f)
            if (ex.practice == Practice.RIGHT) continue                            // the band's left hand is not the kid's
            val left = if (l.id == "workshop-6") leftMiddleC else leftHome
            for ((k, f) in lane(ex, Hand.LEFT)) assertEquals("${l.id} left ${noteName(k)}", left[k], f)
        }
    }

    /** The hand stage puts the painted hand on the home its exercise's fingers name, and listens for those keys. */
    @Test fun theHandStagesSitInTheirHomes() {
        for (l in lessons) for (s in l.steps.filter { it.game == Game.HAND || it.game == Game.PEDAL }) {
            val ex = s.exercise!!
            val right = ex.right.isNotBlank()
            assertArrayEquals(l.id, if (right) intArrayOf(60, 62, 64, 65, 67) else intArrayOf(55, 53, 52, 50, 48), handHome(ex, right))
            assertTrue(l.id, StageKeys.of(ex).all { it in (if (right) StageKeys.FIVE else StageKeys.LEFT_FIVE) })
            // Each round's finger is the one under its key.
            bookFingers(ex).forEach { (k, f) -> assertEquals("${l.id} ${noteName(k)}", handHome(ex, right)[f - 1], k) }
        }
        // The finger numbers lesson never calls the same finger twice in a row (the call would not be said again).
        for (s in lessons[2].steps.filter { it.game == Game.HAND }) assertTrue(bookFingers(s.exercise!!).zipWithNext().none { (a, b) -> a.second == b.second })
    }

    @Test fun thePickGamesAskWhatTheLessonTeaches() {
        val picks = lessons.flatMap { it.steps }.filter { it.game == Game.PICK }
        assertEquals(4, picks.size)
        val rounds = picks.map(::pickRounds)
        assertTrue("sitting: the tall one, every time", rounds[0].all { it.answer == Pick.SIT_TALL })
        assertTrue(rounds[0].flatMap { it.options }.containsAll(listOf(Pick.SIT_SLOUCH, Pick.SIT_CLOSE, Pick.SIT_FEET)))
        assertTrue("wrists: the level one", rounds[1].all { it.answer == Pick.WRIST_LEVEL })
        assertEquals(setOf(Pick.SHORT, Pick.LONG), rounds[2].map { it.answer }.toSet())
        assertTrue("a listening round plays first", rounds[2].all { it.sound != null } && rounds[3].all { it.sound != null })
        assertEquals(setOf(Pick.PEDAL_DOWN, Pick.PEDAL_UP), rounds[3].map { it.answer }.toSet())
        // The sound matches the answer.
        val heard = mapOf(PickSound.SHORT to Pick.SHORT, PickSound.LONG to Pick.LONG, PickSound.PEDAL to Pick.PEDAL_DOWN, PickSound.DRY to Pick.PEDAL_UP)
        rounds.flatten().filter { it.sound != null }.forEach { assertEquals(it.ask, heard[it.sound], it.answer) }
        // Three or four rounds a game, two or three pictures a round, the answer not always in the same place.
        rounds.forEach { r -> assertTrue(r.size in 3..4); assertTrue(r.map { it.options.indexOf(it.answer) }.toSet().size > 1) }
        PlaygroundChecks.linesAreKind(rounds.flatMap(PickLines::all))
    }

    @Test fun theHeavyArmIsLoudThenSoft() {
        assertEquals(SoftLoud.ARM_ROUNDS, SoftLoud.roundsFor("workshop-8"))
        assertEquals(SoftLoud.ROUNDS, SoftLoud.roundsFor("playground-11"))
        val ex = lessons[7].steps.first { it.game == Game.SOFT_LOUD }.exercise!!
        assertEquals(SoftLoud.ARM_ROUNDS.size + 1, ex.toSong("a", "a").groups.size)
        assertEquals(List(3) { listOf(Loudness.LOUD, Loudness.SOFT) }.flatten(), SoftLoud.ARM_ROUNDS)
    }

    @Test fun theConcertIsMaryWithEveryFinger() {
        val step = lessons[11].steps.first { it.game == Game.CONCERT }
        val plan = concertPlan("workshop-12", step)
        assertEquals(Place.WORKSHOP, plan.place)
        assertFalse(plan.pick)
        val mary = plan.songs.single()
        assertEquals("Mary Had a Little Lamb", mary.title)
        assertEquals(WORKSHOP_MARY, mary.exercise)
        assertEquals(step.exercise, mary.exercise)
        assertEquals(CONCERT_PASS, mary.exercise.pass)
        assertEquals(Practice.RIGHT, mary.exercise.practice)
        assertTrue("the band plays the left hand", mary.exercise.toSong("m", "m").notes.any { it.hand == Hand.LEFT })
        assertEquals(listOf(3, 2, 1, 2, 3, 3, 3, 2, 2, 2, 3, 5, 5), lane(mary.exercise, Hand.RIGHT).map { it.second })
    }

    @Test fun thePedalTunesAreInTheCPosition() {
        val ex = lessons[9].steps.first { it.game == Game.PEDAL }.exercise!!
        assertEquals(2, StageRounds(ex.toSong("p", "p"), ex.practice, phrases = true).rounds)
        assertTrue(ex.toSong("p", "p").notes.all { it.midi in StageKeys.FIVE })
    }
}
