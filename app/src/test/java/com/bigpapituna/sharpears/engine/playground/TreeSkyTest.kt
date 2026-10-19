package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.SpokenCatalog
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.3 part B: the treehouse (fingers that move) and the night sky (rhythm and listening), twelve lessons each, held to
 * the other pages' checks: reading steps of a few scenes, copy for small children, every exercise playable, two to four
 * minutes by the time model; the games where the plan puts them, each stage's data kept once, and the lines the kids hear.
 */
class TreeSkyTest {
    private val tree = TREEHOUSE.lessons
    private val sky = NIGHT_SKY.lessons
    private val all = tree + sky

    @Test fun twoLevelsOfTwelve() {
        assertEquals("playground-treehouse", TREEHOUSE_ID)
        assertEquals("playground-night", NIGHT_SKY_ID)
        assertEquals(listOf(TREEHOUSE_ID, NIGHT_SKY_ID), listOf(TREEHOUSE.id, NIGHT_SKY.id))
        assertEquals((1..12).map { "treehouse-$it" }, tree.map { it.id })
        assertEquals((1..12).map { "night-$it" }, sky.map { it.id })
        assertEquals(listOf("Finger walk", "Left hand walk", "Thumb tuck", "Pinky push", "Skips", "Five fingers in G", "Five fingers in F",
            "Same tune, other hand", "Copy my fingers", "Left hand echo", "Which finger?", "Treehouse concert"), tree.map { it.title })
        assertEquals(listOf("Steady beat", "Ta, ti-ti, ta-a", "Shh, a rest", "The metronome game", "Up or down", "Same or different", "Loud or quiet",
            "Fast or slow", "Long or short", "Happy or sad", "Black key tunes", "Night sky concert"), sky.map { it.title })
        // In the course as Playground pages, after the workshop and before the fair.
        assertTrue(COURSE.contains(TREEHOUSE) && COURSE.contains(NIGHT_SKY))
        assertEquals(listOf(WORKSHOP_ID, TREEHOUSE_ID, NIGHT_SKY_ID, FAIR_ID), PLAYGROUND_LEVELS.map { it.id }.drop(3))
        assertTrue(all.all { isPlaygroundLesson(it.id) })
        // So the plain player lets a key go after three tries and a Perform step goes on after three (PlayerScreen letGo, StepRetry).
        assertTrue(all.all { StepRetry.isPlayground(it.id) && StepRetry.letGo(it.id, PLAYGROUND_TRIES) })
        val ids = COURSE.flatMap { it.lessons }.map { it.id }
        assertEquals("unique lesson ids", ids.size, ids.toSet().size)
        for (l in all) {
            assertEquals("${l.id} starts by reading", StepKind.READ, l.steps.first().kind)
            // Three to six steps: inside both the Playground's two to seven and the course's three to six.
            assertTrue("${l.id}: ${l.steps.size} steps", l.steps.size in 3..6)
            assertTrue("${l.id} has something to play", l.steps.any { it.exercise != null && it.kind != StepKind.LISTEN })
            l.steps.forEach { assertEquals("${l.id}: only reading steps have no exercise", it.kind == StepKind.READ, it.exercise == null) }
        }
    }

    /** Two or three scenes a read: inside the Playground's one to three and the course's two to four. */
    @Test fun readStepsAreTwoOrThreeScenes() {
        PlaygroundChecks.readStepsAreOneToThreeScenes(all)
        for (l in all) for (s in l.steps.filter { it.kind == StepKind.READ }) assertTrue("${l.id}: ${s.scenes.size} scenes", s.scenes.size in 2..3)
    }

    @Test fun copyIsWrittenForSmallChildren() {
        PlaygroundChecks.copyIsWrittenForSmallChildren(TREEHOUSE)
        PlaygroundChecks.copyIsWrittenForSmallChildren(NIGHT_SKY)
        PlaygroundChecks.linesAreKind(TreeSkyLines.fixed())
    }

    /** What lands in the pup's bubble fits it on a phone (110 characters, as for every kid lesson), and no digits: numbers are words. */
    @Test fun kidLinesFitTheBubbleAndSayNumbersAsWords() {
        for (l in all) for (s in l.steps) {
            if (s.kind == StepKind.READ) s.scenes.forEach { assertTrue("${l.id}: ${it.caption.length} chars: \"${it.caption}\"", it.caption.length <= 110) }
            else assertTrue("${l.id}: ${s.text.length} chars: \"${s.text}\"", s.text.length <= 110)
        }
        for (line in TreeSkyLines.fixed()) assertFalse("a digit in a kid line: \"$line\"", line.any { it.isDigit() })
        // In the catalog, at the kids' speed.
        val kid = SpokenCatalog.kidLines().toSet()
        assertTrue(TreeSkyLines.fixed().all { SpokenCatalog.normalize(it) in kid })
    }

    /** The app hears keys, never fingers: no line says it saw or checked which finger was used. */
    @Test fun noLineClaimsToSeeAFinger() {
        val claims = Regex("(right|correct|same|good) fingers? (was|were) used|you used (finger|the right)|i saw|checked your finger|with the right finger|right fingers!",
            RegexOption.IGNORE_CASE)
        for (line in TreeSkyLines.fixed()) assertFalse("\"$line\"", claims.containsMatchIn(line))
    }

    @Test fun everyExerciseParsesAndPlaysToTheEnd() = PlaygroundChecks.everyExerciseParsesAndPlaysToTheEnd(all)

    @Test fun lessonsTakeTwoToFourMinutes() { PlaygroundChecks.lessonsTakeTwoToFourMinutes(all) }

    /**
     * Each stage's data is kept once, for a step that carries the data's game and has an exercise; the texts that find it
     * are unique in the whole course; every step whose game needs data has it.
     */
    @Test fun stageDataIsKeptOnceByUniqueText() {
        val marked = StageGames.marked
        assertTrue(marked.size >= 30)
        val texts = COURSE.flatMap { it.lessons }.flatMap { it.steps }.map { it.text }
        for ((text, game) in marked) {
            assertEquals("\"$text\" is one step", 1, texts.count { it == text })
            val step = all.flatMap { it.steps }.first { it.text == text }
            assertNotNull(step.exercise)
            assertEquals("\"$text\"", game.game, step.game)
            assertTrue("a game step plays: \"$text\"", step.kind == StepKind.PLAY || step.kind == StepKind.PERFORM)
        }
        val needsData = setOf(Game.COPY, Game.TAP, Game.BEAT, Game.HIGHER_LOWER, Game.LOUD_QUIET, Game.TEMPO, Game.JAM)
        for (l in COURSE.flatMap { it.lessons }) for (s in l.steps) if (s.game in needsData) {
            assertTrue("${l.id}: only the treehouse and the night sky", l in all)
            assertEquals("${l.id} \"${s.text}\"", s.game, StageGames.of(s)?.game)
        }
        // A kid's bite of a step is still found (LessonScreen hands the stage the bite).
        for (l in all) for (s in l.steps) StageGames.of(s)?.let { assertEquals(it, StageGames.of(Bites.kidStep(s))) }
        // Steps of other pages and reading steps have none.
        assertNull(StageGames.of(PLAYGROUND.lessons[0].steps[1]))
        assertNull(StageGames.of(Step(StepKind.READ, marked.keys.first())))
    }

    @Test fun gamesSitWhereThePlanPutsThem() {
        fun games(lessons: List<CourseLesson>, n: Int) = lessons[n - 1].steps.mapNotNull { it.game }
        for (n in listOf(1, 2, 3, 4, 5, 6, 7, 8)) assertEquals("treehouse-$n", listOf(Game.COPY), games(tree, n))
        assertEquals(listOf(Game.COPY, Game.COPY), games(tree, 9))
        assertEquals(listOf(Game.COPY, Game.COPY), games(tree, 10))
        assertEquals(listOf(Game.TAP, Game.TAP), games(tree, 11))
        assertEquals(listOf(Game.CONCERT, Game.CONCERT), games(tree, 12))
        assertEquals(listOf(Game.CLAP, Game.BEAT, Game.BEAT, Game.BEAT), games(sky, 1))
        assertEquals(listOf(Game.CLAP), games(sky, 2))
        assertEquals(listOf(Game.CLAP, Game.CLAP), games(sky, 3))
        assertEquals(listOf(Game.BEAT, Game.BEAT, Game.BEAT), games(sky, 4))
        assertEquals(listOf(Game.TAP, Game.HIGHER_LOWER), games(sky, 5))
        assertEquals(listOf(Game.TAP, Game.COPY), games(sky, 6))
        assertEquals(listOf(Game.TAP, Game.LOUD_QUIET), games(sky, 7))
        assertEquals(listOf(Game.TAP, Game.TEMPO), games(sky, 8))
        assertEquals(listOf(Game.TAP), games(sky, 9))
        assertEquals(listOf(Game.TAP, Game.SKY_CHORD), games(sky, 10))
        assertEquals(listOf(Game.JAM, Game.JAM), games(sky, 11))
        assertEquals(listOf(Game.CONCERT, Game.CONCERT), games(sky, 12))
        // A game step still runs in the plain player when the screen does not know it.
        for (l in all) l.steps.filter { it.game != null }.forEach { assertTrue(l.id, it.kind == StepKind.PLAY || it.kind == StepKind.PERFORM) }
        // The treehouse's tap games ask about fingers, the night sky's about what they hear; each stage sits in its page's place.
        for (l in all) for (s in l.steps) StageGames.of(s)?.let { assertEquals("${l.id} \"${s.text}\"", pageOf(l.id)!!.place, it.place) }
    }

    @Test fun theTapGamesAskWhatTheyPlay() {
        val taps = all.flatMap { it.steps }.mapNotNull { StageGames.of(it) as? StageGame.Tap }
        assertEquals(listOf(TapKind.FINGER, TapKind.FINGER, TapKind.UP_DOWN, TapKind.SAME_DIFFERENT, TapKind.LOUDER, TapKind.FAST_SLOW, TapKind.LONG_SHORT,
            TapKind.HAPPY_SAD), taps.map { it.kind })
        for (t in taps) for (r in t.rounds + t.example) {
            assertTrue("${t.kind}: ${r.answer}", r.answer in t.kind.choices)
            val keys = r.parts.flatten().flatMap { it.keys }
            assertTrue(keys.all { it in 21..108 })
            val sounds = r.parts.map { p -> p.flatMap { it.keys } }
            when (t.kind) {
                TapKind.UP_DOWN -> assertEquals(if (sounds[1].single() > sounds[0].single()) TapChoice.UP else TapChoice.DOWN, r.answer)
                TapKind.SAME_DIFFERENT -> assertEquals(if (sounds[0] == sounds[1]) TapChoice.SAME else TapChoice.DIFFERENT, r.answer)
                TapKind.LOUDER -> {
                    val (a, b) = r.parts.map { it.single().velocity }
                    assertTrue("a clear difference", kotlin.math.abs(a - b) >= 70)
                    assertEquals(if (a > b) TapChoice.FIRST else TapChoice.SECOND, r.answer)
                }
                TapKind.FAST_SLOW -> assertEquals(if (r.parts.single().first().ms < 300) TapChoice.FAST else TapChoice.SLOW, r.answer)
                TapKind.LONG_SHORT -> assertEquals(if (r.parts.single().single().ms > 1000) TapChoice.LONG else TapChoice.SHORT, r.answer)
                TapKind.HAPPY_SAD -> {
                    // The chord together at the end: a major third over its root is happy, a minor third sad.
                    val chord = r.parts.single().last().keys.sorted()
                    assertEquals(if (chord[1] - chord[0] == 4) TapChoice.HAPPY else TapChoice.SAD, r.answer)
                }
                TapKind.FINGER -> {
                    val s = r.parts.single().single()
                    assertEquals(TapChoice.finger(s.finger!!), r.answer)
                    assertEquals("the key to play is the one pressed", s.keys.single(), r.play)
                }
            }
            if (t.kind != TapKind.FINGER) assertNull("only which finger plays after the tap", r.play)
        }
        // Both answers come up, more than once, so tapping one button never wins.
        for (t in taps.filter { it.kind != TapKind.FINGER }) for (c in t.kind.choices) assertTrue("${t.kind} $c", t.rounds.count { it.answer == c } >= 2)
        // Which finger: every finger of each hand asked about.
        assertEquals((1..5).toSet(), taps[0].rounds.map { it.answer.finger }.toSet())
    }

    @Test fun theConcertsPlayWithTheBandAtTwoStars() {
        val concerts = all.flatMap { l -> l.steps.filter { it.game == Game.CONCERT }.map { l.id to it } }
        assertEquals(listOf("treehouse-12", "treehouse-12", "night-12", "night-12"), concerts.map { it.first })
        for ((id, s) in concerts) {
            val ex = s.exercise!!
            assertEquals(id, StepKind.PERFORM, s.kind)
            assertEquals(id, CONCERT_PASS, ex.pass)
            assertNull(ex.phraseEnd)
            assertTrue("$id at a kid's tempo", ex.bpm <= 80.0)
        }
        // concertPlan plays each step's own song, in its place, no picking (the stage shows that song's title).
        assertEquals(TREEHOUSE_CONCERT + NIGHT_CONCERT, concerts.map { (id, s) -> concertPlan(id, s).songs.single() })
        assertEquals(concerts.map { it.second.exercise }, concerts.map { (id, s) -> concertPlan(id, s).songs.single().exercise })
        assertEquals(listOf(Place.TREEHOUSE, Place.TREEHOUSE, Place.NIGHT_SKY, Place.NIGHT_SKY), concerts.map { (id, s) -> concertPlan(id, s).place })
        assertTrue(concerts.none { (id, s) -> concertPlan(id, s).pick })
        assertEquals(listOf("Hot Cross Buns", "Twinkle, Twinkle, Little Star", "Twinkle, Twinkle, Little Star", "Twinkle all the way"),
            concerts.map { (id, s) -> concertPlan(id, s).songs.single().title })
        // A kid's bite of a concert step is the same song.
        for ((id, s) in concerts) assertEquals(concertPlan(id, s), concertPlan(id, Bites.kidStep(s)))
        // Hot Cross Buns with both hands; Twinkle's tune by the band while the left hand plays the low notes.
        assertEquals(Practice.BOTH, BUNS_BOTH.practice)
        val buns = BUNS_BOTH.toSong("b", "b")
        assertTrue("the hands play together", buns.groups.count { g -> g.notes.map { it.hand }.toSet().size == 2 } >= 4)
        assertEquals(Practice.LEFT, TWINKLE_LEFT.practice)
        assertEquals(TWINKLE.left, TWINKLE_LEFT.left)
        assertEquals(TWINKLE.right, TWINKLE_LEFT.right)
        // The night sky plays the shelf's Twinkle, then the whole song: A, the middle, A.
        assertEquals(shelfSong("twinkle")!!.concert.exercise, sky[11].steps[1].exercise)
        assertEquals(42, TWINKLE_ALL.toSong("t", "t").notes.count { it.hand == Hand.RIGHT })
    }

    /** The beat games put a star on each beat of their exercise: middle C, the slow, quicker and quick drum, the clicks. */
    @Test fun theBeatGamesHaveTheirBeats() {
        val beats = sky.flatMap { l -> l.steps.filter { StageGames.of(it) is StageGame.Beat }.map { l.id to it.exercise!! } }
        assertEquals(listOf(60.0, 80.0, 100.0), beats.filter { it.first == "night-1" }.map { it.second.bpm })
        for ((id, ex) in beats) assertTrue(id, ex.toSong("b", "b").notes.all { it.midi == 60 })
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), beats.last().second.let { ex -> com.bigpapituna.sharpears.engine.parseTokens(ex.right).map { it.startBeat } })
    }

    /** The jam listens for black keys only (ten, under the twelve a stage may ask about), and its band stays below them. */
    @Test fun theJamIsBlackKeys() {
        assertEquals(10, JAM_KEYS.size)
        assertTrue(JAM_KEYS.all(::isBlack))
        assertTrue(StarJam.BAND_BAR.flatMap { it.second }.all { isBlack(it) && it < JAM_KEYS.min() })
        assertTrue(StarJam.NUDGE.all { it in JAM_KEYS })
        val jam = sky[10].steps.first { StageGames.of(it) is StageGame.Jam }.exercise!!.toSong("j", "j")
        assertTrue(jam.groups.all { g -> g.anyOf && g.notes.all { isBlack(it.midi) } })
    }

    /** The octave hint stays off where any key of a kind will do: these lessons' any-of groups (ANY_OF_LESSONS). */
    @Test fun anyOfLessonsAreListed() {
        val anyOf = all.filter { l -> l.steps.any { s -> s.exercise?.let { '|' in it.right || '|' in it.left } == true } }.map { it.id }
        assertEquals(listOf("night-5", "night-11"), anyOf)
        // Merged, the pages list them, so the octave hint stays off there.
        assertTrue(ANY_OF_LESSONS.containsAll(anyOf))
        assertTrue(ANY_OF_LESSONS.intersect(all.map { it.id }.toSet()) == anyOf.toSet())
    }
}
