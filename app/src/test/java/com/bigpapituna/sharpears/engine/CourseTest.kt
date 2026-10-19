package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseTest {
    @Test fun noteNames() {
        assertEquals(60, noteNumber("C4")); assertEquals(54, noteNumber("F#3")); assertEquals(46, noteNumber("Bb2")); assertEquals(21, noteNumber("A0"))
    }

    @Test fun lineTimingRestsAndChords() {
        val notes = parseLine("C4:1 r:1 C4+E4+G4:2", Hand.RIGHT, 500.0)
        assertEquals(listOf(60, 60, 64, 67), notes.map { it.midi })
        assertEquals(listOf(0L, 1000L, 1000L, 1000L), notes.map { it.startMs })
    }

    @Test fun everyLessonIsUsable() {
        val ids = COURSE.flatMap { it.lessons }.map { it.id }
        assertEquals("unique lesson ids", ids.size, ids.toSet().size)
        // 2.3: 106 before the workshop's twelve, the treehouse's twelve, the night sky's twelve and the fair's four.
        assertEquals(146, ids.size)
        assertEquals(76, com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES.sumOf { it.lessonIds.size })
        // The Playground's pages first, in path order (the garden, the pond, the hall, the workshop, the treehouse, the
        // night sky, the fair), then the course.
        assertEquals(listOf("playground", "playground-pond", "playground-hall", "playground-workshop", "playground-treehouse", "playground-night", "playground-fair",
            "basics", "beginner", "rhythm", "intermediate", "keys", "advanced", "technique", "repertoire-1", "repertoire-2", "technique-2"),
            COURSE.map { it.id })
        for (lesson in COURSE.flatMap { it.lessons }) {
            // Playground lessons are games for small children: as short as a read and one game.
            val steps = if (com.bigpapituna.sharpears.engine.playground.isPlaygroundLesson(lesson.id)) 2..7 else 3..6
            assertTrue("${lesson.id} has $steps steps", lesson.steps.size in steps)
            assertEquals("${lesson.id} starts by reading", StepKind.READ, lesson.steps.first().kind)
            assertTrue("${lesson.id} has something to play", lesson.steps.any { it.exercise != null })
            if (lesson.songId != null) assertEquals("${lesson.id} ends by opening its song", StepKind.READ, lesson.steps.last().kind)
            for (step in lesson.steps) {
                assertTrue("${lesson.id} step text", step.text.isNotBlank() && step.text.none { it in "—–·#&/" })
                assertEquals("${lesson.id}: only reading steps have no exercise", step.kind == StepKind.READ, step.exercise == null)
                val ex = step.exercise ?: continue
                val song = ex.toSong(lesson.id, lesson.title)
                assertTrue("${lesson.id} has notes", song.notes.isNotEmpty())
                // The practiced hand has notes, and every note gets a suggested finger.
                when (ex.practice) {
                    Practice.LEFT -> assertTrue(lesson.id, song.notes.any { it.hand == Hand.LEFT })
                    Practice.RIGHT -> assertTrue(lesson.id, song.notes.any { it.hand == Hand.RIGHT })
                    Practice.BOTH -> {}
                }
                // Alternatives ("any key above middle C") are not fingered: any finger will do.
                val f = Fingering.of(song)
                val choices = song.groups.filter { it.anyOf }.flatMap { it.notes }.toSet()
                assertTrue("${lesson.id} fingering", song.notes.filter { it !in choices }.all { f[it] in 1..5 })
            }
        }
    }

    @Test fun stepKindsSetTheModeAndLabels() {
        val ex = Exercise(right = "C4:1")
        assertEquals(Mode.LISTEN, Step(StepKind.LISTEN, "x", ex).mode)
        assertEquals(Mode.WAIT, Step(StepKind.PLAY, "x", ex).mode)
        assertEquals(Mode.WAIT, Step(StepKind.FROM_MEMORY, "x", ex).mode)
        assertEquals(Mode.PERFORM, Step(StepKind.PERFORM, "x", ex).mode)
        assertTrue(Step(StepKind.PLAY, "x", ex).labels)
        assertFalse(Step(StepKind.FROM_MEMORY, "x", ex).labels)
    }

    @Test fun performStepsPassAtTheirMark() {
        val step = Step(StepKind.PERFORM, "x", Exercise(right = "C4:1", pass = 80))
        assertTrue(step.passed(Recap(good = 8, ok = 0, miss = 2, biasMs = 0, longestStreak = 8)))
        assertFalse(step.passed(Recap(good = 7, ok = 0, miss = 3, biasMs = 0, longestStreak = 7)))
        assertFalse("a Perform step needs a run", step.passed(null))
        assertTrue(Step(StepKind.PLAY, "x", Exercise(right = "C4:1")).passed(null))
    }

    @Test fun fivePositionLessonsUseOneFingerPerKey() {
        fun ex(id: String) = COURSE.flatMap { it.lessons }.first { it.id == id }.steps.first { it.kind == StepKind.PLAY }.exercise!!
        val right = ex("fingers-right").toSong("x", "x")
        val f = Fingering.of(right)
        assertEquals(listOf(1, 2, 3, 4, 5, 4, 3, 2, 1), right.notes.map { f[it] })
        val left = ex("fingers-left").toSong("x", "x")
        val g = Fingering.of(left)
        assertEquals(listOf(5, 4, 3, 2, 1, 2, 3, 4, 5), left.notes.map { g[it] })
    }

    @Test fun rhythmLessonsTapOneKeyThenATuneInTheSameRhythm() {
        val level = COURSE.first { it.id == "rhythm" }
        assertEquals(6, level.lessons.size)
        for (l in level.lessons) {
            assertEquals(listOf(StepKind.READ, StepKind.LISTEN, StepKind.PERFORM, StepKind.PERFORM), l.steps.map { it.kind })
            val one = l.steps[2].exercise!!
            val tune = l.steps[3].exercise!!
            assertTrue(l.id, one.beatLines && one.pass == 80)
            val a = one.toSong("a", "a"); val b = tune.toSong("b", "b")
            assertEquals("${l.id}: one key", setOf(60), a.notes.map { it.midi }.toSet())
            assertEquals("${l.id}: same rhythm", a.notes.map { it.startMs }, b.notes.map { it.startMs })
            val beats = one.right.split(" ").sumOf { it.substringAfter(':').toDouble() }
            assertEquals("${l.id}: whole bars", 0.0, beats % one.beats, 1e-9)
        }
        assertEquals(3, level.lessons.first { it.id == "rhythm-waltz" }.steps[2].exercise!!.toSong("w", "w").timeSignature.first)
    }

    @Test fun performScoresARhythmOnOneKey() {
        val song = COURSE.first { it.id == "rhythm" }.lessons.first { it.id == "rhythm-eighths" }.steps[2].exercise!!.toSong("e", "e")
        fun run(offsetMs: Double, skip: Set<Int> = emptySet()): Recap {
            val t = Transport(song).apply { mode = Mode.PERFORM; play() }
            song.notes.forEachIndexed { i, n ->
                while (t.positionMs < n.startMs + offsetMs) t.tick(minOf(4.0, n.startMs + offsetMs - t.positionMs))
                if (i !in skip) assertEquals("note $i", InputResult.CORRECT, t.noteInput(60))
            }
            while (t.playing) t.tick(16.0)
            return t.score!!.recap()
        }
        assertEquals(Recap(11, 0, 0, 0, 11), run(0.0))                          // every eighth on its own target
        assertEquals(11, run(90.0).good)                                        // a little late is still good
        val two = run(0.0, skip = setOf(3, 4))
        assertEquals(9, two.good); assertEquals(2, two.miss); assertEquals(82, two.accuracy)
    }

    @Test fun leftHandPatternLessons() {
        val lessons = COURSE.first { it.id == "intermediate" }.lessons.filter { it.id.startsWith("lh-") }
        assertEquals(listOf("lh-waltz", "lh-pop", "lh-octaves", "lh-tenths"), lessons.map { it.id })
        for (l in lessons) {
            assertEquals(listOf(StepKind.READ, StepKind.LISTEN, StepKind.PLAY, StepKind.PLAY), l.steps.map { it.kind })
            val alone = l.steps[2].exercise!!; val both = l.steps[3].exercise!!
            assertTrue(l.id, alone.right.isEmpty() && alone.practice == Practice.LEFT)
            fun beats(s: String) = s.split(" ").sumOf { it.substringAfter(':').toDouble() }
            assertEquals("${l.id}: four bars of melody", 4.0 * both.beats, beats(both.right), 1e-9)
            assertEquals("${l.id}: the pattern fills the same bars", beats(both.right), beats(both.left), 1e-9)
        }
    }

    @Test fun techniqueDrillsEndOnATempoRamp() {
        val level = COURSE.first { it.id == "technique" }
        assertEquals(listOf("five-c", "five-g", "five-f", "five-d", "five-a", "hanon-1", "contrary"), level.lessons.map { it.id })
        for (l in level.lessons) {
            val last = l.steps.last()
            assertEquals(l.id, StepKind.RAMP, last.kind)
            assertEquals(Mode.PERFORM, last.mode)
            val song = last.exercise!!.toSong(l.id, l.title)
            assertTrue("${l.id}: both hands", song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT })
        }
        // Five-finger drills: both hands an octave apart, in the key's position; F names its black key B flat.
        val g = level.lessons.first { it.id == "five-g" }.steps.last().exercise!!.toSong("g", "g")
        assertEquals((67..74).toSet().intersect(setOf(67, 69, 71, 72, 74)), g.notes.filter { it.hand == Hand.RIGHT }.map { it.midi }.toSet())
        assertTrue(level.lessons.first { it.id == "five-f" }.steps.last().exercise!!.toSong("f", "f").preferFlats)
        val hanon = level.lessons.first { it.id == "hanon-1" }.steps.last().exercise!!.toSong("h", "h")
        assertEquals(32, hanon.notes.count { it.hand == Hand.RIGHT })
        assertEquals(hanon.notes.filter { it.hand == Hand.RIGHT }.map { it.midi - 12 }, hanon.notes.filter { it.hand == Hand.LEFT }.map { it.midi })
        // A ramp from 50% needs five clean passes to reach full speed.
        val r = TempoRamp()
        val clean = Report(emptyList(), 0, 0, Recap(10, 0, 0, 0, 10))
        repeat(4) { r.passEnded(Mode.PERFORM, clean) }
        assertFalse(r.atTop)
        r.passEnded(Mode.PERFORM, clean)
        assertTrue(r.atTop)
    }

    @Test fun pedalAndDynamicsStepsNeedMidiAndAreGradedOnTheirOwnScore() {
        val inter = COURSE.first { it.id == "intermediate" }.lessons
        val pedal = inter.first { it.id == "pedal" }.steps.last()
        val loud = inter.first { it.id == "dynamics" }.steps.last()
        assertTrue(pedal.kind.needsMidi && loud.kind.needsMidi)
        assertEquals(Mode.PERFORM, pedal.mode); assertEquals(Mode.WAIT, loud.mode)
        assertEquals(4, PedalGrader.chordsOf(pedal.exercise!!.toSong("p", "p")).size)
        assertEquals(10, loud.exercise!!.toSong("d", "d").notes.size)
        val r = Report(emptyList(), 0, 0, null)
        assertTrue(pedal.passed(r.copy(extraScore = 100)))
        assertFalse(pedal.passed(r.copy(extraScore = 75)))
        assertFalse(loud.passed(r))
        assertTrue(Step(StepKind.PERFORM, "x", Exercise(right = "C4:1")).passed(r.copy(recap = Recap(9, 0, 1, 0, 9))))
    }

    private val newSongIds = listOf("mary-had-a-little-lamb", "hot-cross-buns", "row-row-row-your-boat", "london-bridge", "yankee-doodle",
        "old-macdonald", "haydn-surprise", "au-clair-de-la-lune", "lightly-row", "we-wish-you-a-merry-christmas",
        "eine-kleine-nachtmusik", "gymnopedie-1", "hall-of-the-mountain-king", "rondo-alla-turca", "bach-toccata-opening")

    private fun libraryIds(): Set<String> =
        Regex("\"id\": \"([^\"]+)\",\\s*\"file\"").findAll(java.io.File("src/main/assets/songs/index.json").readText()).map { it.groupValues[1] }.toSet()

    @Test fun repertoireLessonsTeachAPhraseThenSendYouToTheSong() {
        val library = libraryIds()
        for ((levelId, songs) in listOf(
            "repertoire-1" to listOf("mary-had-a-little-lamb", "hot-cross-buns", "row-row-row-your-boat", "london-bridge", "yankee-doodle", "old-macdonald"),
            "repertoire-2" to listOf("lightly-row", "we-wish-you-a-merry-christmas", "eine-kleine-nachtmusik", "gymnopedie-1", "bach-toccata-opening", "scarborough-fair"),
        )) {
            val level = COURSE.first { it.id == levelId }
            assertEquals(levelId, 6, level.lessons.size)
            assertEquals("$levelId: one lesson per song, in order", songs, level.lessons.map { it.songId })
            for (l in level.lessons) {
                assertTrue("${l.songId} is in the library", l.songId in library)
                assertEquals(l.id, listOf(StepKind.READ, StepKind.LISTEN, StepKind.PLAY, StepKind.PLAY, StepKind.PERFORM, StepKind.READ), l.steps.map { it.kind })
                val phrase = l.steps[1].exercise!!
                assertEquals(l.id, Practice.RIGHT, l.steps[2].exercise!!.practice)
                assertEquals(l.id, Practice.LEFT, l.steps[3].exercise!!.practice)
                assertEquals(l.id, 80, l.steps[4].exercise!!.pass)
                val song = phrase.toSong(l.id, l.title)
                assertTrue("${l.id}: both hands in the phrase", song.notes.any { it.hand == Hand.LEFT } && song.notes.any { it.hand == Hand.RIGHT })
                fun beats(s: String) = s.split(" ").sumOf { it.substringAfter(':').toDouble() }
                assertEquals("${l.id}: the hands fill the same bars", beats(phrase.right), beats(phrase.left), 1e-9)
                assertEquals("${l.id}: whole bars", 0.0, beats(phrase.right) % phrase.beats, 1e-9)
            }
        }
        // The repertoire covers every easy and medium song written for it, so none of the new tunes is orphaned in the library.
        val taught = COURSE.flatMap { it.lessons }.mapNotNull { it.songId }.toSet()
        assertTrue(newSongIds.filter { it !in taught }.toString(), newSongIds.filter { it !in taught }.toSet() ==
            setOf("haydn-surprise", "au-clair-de-la-lune", "hall-of-the-mountain-king", "rondo-alla-turca"))
    }

    @Test fun technique2CoversScalesChordsTouchShapingPedalAndSightReading() {
        val level = COURSE.first { it.id == "technique-2" }
        assertEquals(listOf("t2-scales", "t2-broken", "t2-touch", "t2-shaping", "t2-pedal-progression", "t2-sight"), level.lessons.map { it.id })
        val scales = level.lessons.first { it.id == "t2-scales" }
        assertEquals(listOf(StepKind.READ, StepKind.PERFORM, StepKind.PERFORM, StepKind.PERFORM), scales.steps.map { it.kind })
        for (step in scales.steps.drop(1)) {
            val ex = step.exercise!!
            val song = ex.toSong("s", "s")
            assertEquals("a fingering for every right-hand note", song.notes.count { it.hand == Hand.RIGHT }, ex.fingers.split(" ").size)
            assertEquals("one octave up and back, both hands", 15, song.notes.count { it.hand == Hand.LEFT })
            assertEquals(80, ex.pass)
        }
        assertTrue(scales.steps[3].exercise!!.toSong("f", "f").preferFlats)
        assertTrue(level.lessons.first { it.id == "t2-shaping" }.steps.last().kind == StepKind.DYNAMICS)
        val pedal = level.lessons.first { it.id == "t2-pedal-progression" }.steps.last()
        assertEquals(StepKind.PEDAL, pedal.kind)
        assertEquals(4, PedalGrader.chordsOf(pedal.exercise!!.toSong("p", "p")).size)
        // Staccato is shown as notes with rests: as many rests as notes in the short version.
        val touch = level.lessons.first { it.id == "t2-touch" }
        val short = touch.steps[3].exercise!!.right.split(" ")
        assertTrue(short.count { it.startsWith("r:") } >= short.count { !it.startsWith("r:") } - 2)
        assertEquals(StepKind.PERFORM, level.lessons.first { it.id == "t2-sight" }.steps.last().kind)
    }

    @Test fun everyLessonSongExistsInTheLibrary() {
        val library = libraryIds()
        for (l in COURSE.flatMap { it.lessons }) l.songId?.let { assertTrue("${l.id}: $it", it in library) }
    }
}
