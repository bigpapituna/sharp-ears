package com.bigpapituna.sharpears.theory

import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.engine.theory.Choice
import com.bigpapituna.sharpears.engine.theory.CircleVisual
import com.bigpapituna.sharpears.engine.theory.IntervalVisual
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.Listen
import com.bigpapituna.sharpears.engine.theory.PlayIt
import com.bigpapituna.sharpears.engine.theory.PopVisual
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.THEORY
import com.bigpapituna.sharpears.engine.theory.THEORY_LESSONS
import com.bigpapituna.sharpears.engine.theory.ValueTreeVisual
import com.bigpapituna.sharpears.engine.theory.Visual
import com.bigpapituna.sharpears.engine.theory.rightAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TheoryContentTest {
    @Test fun sixUnitsOfThreeToFiveLessons() {
        assertEquals(6, THEORY.size)
        assertEquals((1..6).toList(), THEORY.map { it.number })
        THEORY.forEach { assertTrue("${it.id}: ${it.lessons.size} lessons", it.lessons.size in 3..5) }
        val ids = THEORY_LESSONS.map { it.second.id }
        assertEquals("lesson ids are unique", ids.size, ids.toSet().size)
    }

    @Test fun everyLessonHasScenesAndAQuiz() {
        THEORY_LESSONS.forEach { (_, l) ->
            assertTrue("${l.id}: ${l.scenes.size} scenes", l.scenes.size in 3..6)
            assertTrue("${l.id}: ${l.quiz.size} questions", l.quiz.size in 3..6)
            l.scenes.forEach { assertTrue("${l.id}: empty caption", it.caption.isNotBlank() && it.say.isNotBlank()) }
        }
    }

    @Test fun everyQuestionHasExactlyOneRightAnswer() {
        THEORY_LESSONS.forEach { (_, l) ->
            l.quiz.forEach { q ->
                assertEquals("${l.id}: ${q.prompt}", 1, q.rightAnswers())
                when (q) {
                    is Choice -> assertTrue("${l.id}: ${q.prompt} has ${q.options.size} options", q.options.size in 2..4)
                    is Listen -> {
                        assertTrue(q.options.size in 2..4)
                        assertTrue("${q.prompt}: clips", q.clips.isNotEmpty() && q.clips.all { it.isNotEmpty() })
                    }
                    is PlayIt -> {
                        assertFalse("${q.prompt}: keyboard ends on white keys", isBlack(q.low) || isBlack(q.high))
                        assertTrue("${q.prompt}: playable on the keyboard shown", (q.low..q.high).any { q.accepts(it) })
                        if (!q.anyOctave) assertTrue(q.target in q.low..q.high)
                    }
                }
            }
        }
    }

    /** Captions, titles, options and explanations: no em or en dashes; the words the pup says spell notes in words. */
    @Test fun copyFollowsTheHouseRules() {
        val dashes = Regex("[–—]| - ")
        all().forEach { (where, text) -> assertFalse("$where: \"$text\"", dashes.containsMatchIn(text)) }
        THEORY_LESSONS.forEach { (_, l) ->
            l.scenes.forEach { s ->
                listOf(s.caption, s.say).forEach { assertFalse("${l.id}: symbols in \"$it\"", it.contains('#') || it.contains('♯') || it.contains('♭')) }
            }
            assertTrue("${l.id}: sentence case title", l.title.first().isUpperCase() && l.title.drop(1).split(' ').none { w -> w.length > 3 && w.first().isUpperCase() && w != "Roman" })
        }
    }

    @Test fun visualsStayInRangeAndCuesFitTheirTime() {
        val visuals = THEORY_LESSONS.flatMap { (_, l) -> l.scenes.map { l.id to it.visual } + l.quiz.mapNotNull { q -> (q as? Choice)?.show ?: (q as? PlayIt)?.show }.map { l.id to it } }
        visuals.forEach { (id, v) ->
            assertTrue("$id: ${v::class.simpleName} has no time", v.durationMs > 0)
            v.cues.forEach { c -> assertTrue("$id: cue at ${c.atMs} beyond ${v.durationMs}", c.atMs in 0..v.durationMs) }
            when (v) {
                is KeysVisual -> {
                    assertFalse("$id: keyboard ends on white keys", isBlack(v.low) || isBlack(v.high))
                    v.frames.flatMap { it.notes }.forEach { assertTrue("$id: key $it outside ${v.low}..${v.high}", it in v.low..v.high) }
                }
                is IntervalVisual -> v.pairs.forEach { assertTrue("$id: interval off the keys", it.low.midi >= v.low && it.high.midi <= v.high && it.low.midi < it.high.midi) }
                is ChordVisual -> v.chords.flatMap { it.notes }.forEach { assertTrue("$id: chord note ${it.code} off the keys", it.midi in v.low..v.high) }
                is RhythmVisual -> v.hits.forEach { assertTrue("$id: hit past the pattern", it.start + it.length <= v.beats + 0.001f) }
                is CircleVisual -> v.stops.forEach { assertTrue(it in 0..11) }
                else -> {}                                  // staff, words, the value tree; the course's visuals are checked in CourseScenesTest
            }
            if (v is RhythmVisual && v.counts.isNotEmpty()) assertEquals("$id: one count per subdivision", v.beats * v.sub, v.counts.size)
        }
    }

    @Test fun keysScenesSoundEveryLitFrameInOrder() {
        val v = THEORY_LESSONS.first { it.second.id == "sc-major" }.second.scenes[1].visual as KeysVisual
        assertEquals(listOf("W", "W", "H", "W", "W", "W", "H"), v.frames.drop(1).map { it.gap })
        assertEquals(listOf(60, 62, 64, 65, 67, 69, 71, 72), v.cues.map { it.notes.single() })
        assertEquals(v.cues.map { it.atMs }.sorted(), v.cues.map { it.atMs })
        assertEquals(-1, v.current(0)); assertEquals(0, v.current(v.frameAt(0))); assertEquals(7, v.current(v.durationMs))
    }

    @Test fun progressFindsTheNextLessonAndCompletedUnits() {
        val done = mutableSetOf<String>()
        val p = com.bigpapituna.sharpears.engine.theory.TheoryProgress({ it in done }, { if (it in done) 80 else null })
        assertEquals("keys-pattern", p.next?.second?.id)
        THEORY[0].lessons.forEach { done += it.id }
        assertEquals(1, p.unitsDone)
        assertEquals(80, p.unitScore(THEORY[0]))
        assertEquals(THEORY[1].lessons[0].id, p.next?.second?.id)
        assertEquals(THEORY[1].lessons[0].id, com.bigpapituna.sharpears.engine.theory.lessonAfter(THEORY[0].lessons.last().id)?.second?.id)
        assertEquals(null, com.bigpapituna.sharpears.engine.theory.lessonAfter(THEORY_LESSONS.last().second.id))
        assertEquals(67, com.bigpapituna.sharpears.engine.theory.percent(4, 6))
    }

    private fun all(): List<Pair<String, String>> = THEORY.flatMap { u ->
        listOf(u.id to u.title, u.id to u.about) + u.lessons.flatMap { l ->
            listOf(l.id to l.title, l.id to l.summary) +
                l.scenes.flatMap { listOf(l.id to it.caption, l.id to it.say) + labels(it.visual).map { t -> l.id to t } } +
                l.quiz.flatMap { q ->
                    listOf(l.id to q.prompt, l.id to q.explain) + when (q) { is Choice -> q.options; is Listen -> q.options; is PlayIt -> emptyList() }.map { l.id to it }
                }
        }
    }

    private fun labels(v: Visual): List<String> = when (v) {
        is KeysVisual -> v.frames.flatMap { listOfNotNull(it.label, it.gap) + it.names.values }
        is StaffVisual -> v.notes.mapNotNull { it.label }
        is IntervalVisual -> v.pairs.flatMap { listOfNotNull(it.name, it.note) }
        is ChordVisual -> v.chords.flatMap { listOfNotNull(it.name, it.numeral) + it.tags }
        is RhythmVisual -> listOfNotNull(v.term) + v.counts
        is PopVisual -> v.items + listOfNotNull(v.small)
        else -> emptyList()
    }
}
