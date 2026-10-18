package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.NoteEvent
import com.bigpapituna.sharpears.engine.OnsetGroup
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Recap
import com.bigpapituna.sharpears.engine.Song
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The checks every Playground page's lessons pass (the garden's since 2.1, the pond's and the concert hall's since 2.2):
 * reading steps of one to three scenes, copy written for small children, every exercise playable to its end, and a
 * lesson length of two to four minutes by the time model.
 */
object PlaygroundChecks {
    /** Song titles keep their own capitals; every other lesson title is in sentence case. */
    val SONG_TITLES = setOf("Hot Cross Buns", "Mary Had a Little Lamb", "Twinkle, Twinkle, Little Star", "Row, Row, Row Your Boat",
        "Old MacDonald Had a Farm", "London Bridge", "Ode to Joy", "Lightly Row", "Yankee Doodle")

    private val banned = Regex("[—–#&/%]| - |\\bwrong\\b|\\bpercent\\b|\\bmistake\\b|\\bfail", RegexOption.IGNORE_CASE)

    fun readStepsAreOneToThreeScenes(lessons: List<CourseLesson>) {
        for (l in lessons) for (s in l.steps.filter { it.kind == StepKind.READ }) {
            assertTrue("${l.id}: ${s.scenes.size} scenes", s.scenes.size in 1..3)
            assertEquals(s.scenes.joinToString(" ") { it.caption }, s.text)
        }
    }

    /** No dashes, no percentages, nothing called wrong, and a capital first, in every line of [lines]. */
    fun linesAreKind(lines: List<String>) {
        for (line in lines) {
            assertFalse("\"$line\"", banned.containsMatchIn(line))
            assertTrue("starts with a capital: \"$line\"", line.first().isUpperCase())
        }
    }

    /** Short, warm, second person, sentence case; no dashes, no percentages, nothing called wrong. */
    fun copyIsWrittenForSmallChildren(level: CourseLevel, extra: List<String> = emptyList()) {
        val lessons = level.lessons
        linesAreKind(lessons.flatMap { l -> listOf(l.title, l.summary) + l.steps.flatMap { s -> listOf(s.text) + s.scenes.flatMap { listOf(it.caption, it.say) } } } +
            listOf(level.title, level.about) + extra)
        for (l in lessons) {
            for (s in l.steps) {
                val sentences = s.text.split(Regex("(?<=[.!?]) ")).filter { it.isNotBlank() }
                sentences.forEach { assertTrue("${l.id}: a short sentence: \"$it\"", it.split(' ').size <= 20) }
                assertTrue("${l.id}: ends a sentence: \"${s.text}\"", s.text.last() in ".!?")
            }
            // Sentence case: no capital after the first word but a note's letter; song titles keep their own capitals.
            if (l.title !in SONG_TITLES)
                assertTrue("${l.id}: sentence case \"${l.title}\"", l.title.split(' ').drop(1).none { it.first().isUpperCase() && it.length > 1 })
            assertTrue("${l.id}: the pup talks to you", l.steps.any { s -> Regex("\\b(you|your|me|my|i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(s.text) })
        }
    }

    fun everyExerciseParsesAndPlaysToTheEnd(lessons: List<CourseLesson>) {
        for (l in lessons) for ((i, s) in l.steps.withIndex()) {
            val ex = s.exercise ?: continue
            val song = ex.toSong("${l.id}-${i + 1}", l.title)
            assertTrue("${l.id} step ${i + 1} has notes", song.notes.isNotEmpty())
            when (s.kind) {
                StepKind.LISTEN -> {
                    val t = Transport(song).apply { mode = Mode.LISTEN; play() }
                    var sounded = 0
                    while (t.playing) sounded += t.tick(16.0).count { it is NoteEvent.On }
                    assertEquals("${l.id} step ${i + 1}: every note sounds, an any-of group only its example",
                        song.groups.sumOf { g -> if (g.anyOf) 1 else g.notes.size }, sounded)
                }
                StepKind.PERFORM -> {
                    val r = perform(song, ex.practice, skip = emptySet())
                    assertEquals("${l.id} step ${i + 1}: a clean run", 100, r.accuracy)
                    assertTrue(s.passed(r))
                }
                else -> {
                    val t = playWait(song, ex.practice) { g -> g.notes.first().midi }
                    assertFalse(t.playing)
                    assertEquals("${l.id} step ${i + 1}: no slips", 0, t.report().wrongKeys)
                    assertEquals("${l.id} step ${i + 1}: every group played", song.groups.count { g -> g.notes.any(t::required) }, t.report().groups)
                }
            }
        }
    }

    /** Each lesson's time by [estimatedSeconds], all two to four minutes (as the garden's: 100 to 250 s by this model). */
    fun lessonsTakeTwoToFourMinutes(lessons: List<CourseLesson>): Map<String, Int> {
        val secs = lessons.associate { it.id to estimatedSeconds(it.steps).toInt() }
        println(secs)
        assertTrue("$secs", secs.values.all { it in 100..250 })
        return secs
    }

    /**
     * A rough time for a lesson as a small child meets it. A scene: its animation or its caption read aloud (two words a
     * second: the pup speaks slower to kids), whichever is longer, and a breath. An exercise: the instruction read aloud,
     * the run itself, nearly two seconds to find each key in wait mode (three seconds a round in a game, four for soft and loud, where the pup reacts),
     * the count-in in Perform, and the cheer and Continue after it; the concert adds picking a song. 2.2's games: a copy
     * game's tune is shown (its notes, under half a second each) and played back (two seconds a note); a chord round
     * takes four and a half seconds; the soft and loud tune says what it wants, shows the phrase, hears it and reacts;
     * the set list is picking and three warm ups in wait mode.
     */
    fun estimatedSeconds(steps: List<Step>): Double = steps.sumOf { s ->
        if (s.kind == StepKind.READ) s.scenes.sumOf { maxOf(it.visual.durationMs / 1000.0, it.say.split(' ').size / 2.0) + 1.5 }
        else {
            val ex = s.exercise!!
            val song = ex.toSong("e", "e")
            val run = song.durationMs / 1000.0
            val keys = song.groups.size
            val beat = 60 / ex.bpm
            val phrases = { StageRounds(song, ex.practice, phrases = true).let { r -> r.rounds } }
            s.text.split(' ').size / 2.0 + 1.5 + when (s.game) {
                Game.ECHO -> phrases() * (1.3 + 0.65 + 1.4) + keys * (0.45 + 2.0)
                Game.CHORD -> run + keys * 4.5
                Game.SOFT_LOUD_TUNE -> phrases() * (1.3 + 0.65 + 2.5 + 1.4) + keys * (0.42 + 1.2)
                Game.SET_LIST -> 20.0 + Recital.songs(null).sumOf { sh -> sh.start.toSong("w", "w").let { w -> w.durationMs / 1000.0 + w.groups.size * 1.8 + 1.6 } }
                else -> run + when {
                    s.game == Game.SOFT_LOUD -> keys * 4.0
                    s.game == Game.HIGH_LOW || s.game == Game.KEY_HUNT -> keys * 3.0
                    s.game == Game.CONCERT -> 2 * ex.beats * beat + 20
                    s.kind == StepKind.PERFORM -> 2 * ex.beats * beat
                    s.kind == StepKind.LISTEN -> 0.0
                    else -> keys * 1.8
                }
            } + if (s.kind == StepKind.LISTEN) 0.0 else 4.0
        }
    }

    /** Wait mode from the top: [answer] picks the key for an any-of group; chords get every note. */
    fun playWait(song: Song, practice: Practice, answer: (OnsetGroup) -> Int): Transport {
        val t = Transport(song).apply { mode = Mode.WAIT; this.practice = practice; play() }
        var guard = 0
        while (t.playing && guard++ < 200_000) {
            t.tick(10.0)
            if (!t.waiting) continue
            val g = t.nextGroup!!
            val keys = if (g.anyOf) listOf(answer(g)) else t.expected.map { it.midi }
            keys.forEach { k -> assertTrue("${song.id}: ${noteName(k)}", t.noteInput(k) != InputResult.WRONG) }
        }
        return t
    }

    /** Perform from the top, playing each required group on time except those whose index is in [skip]. */
    fun perform(song: Song, practice: Practice, skip: Set<Int>): Recap {
        val t = Transport(song).apply { mode = Mode.PERFORM; this.practice = practice; play() }
        val groups = song.groups.filter { g -> g.notes.any(t::required) }
        groups.forEachIndexed { i, g ->
            while (t.positionMs < g.startMs) t.tick(minOf(5.0, g.startMs - t.positionMs))
            if (i !in skip) assertEquals(InputResult.CORRECT, t.noteInput(g.notes.first(t::required).midi))
        }
        while (t.playing) t.tick(16.0)
        return t.score!!.recap()
    }
}
