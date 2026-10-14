package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.theory.ArticulationVisual
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.engine.theory.DynamicsVisual
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import com.bigpapituna.sharpears.engine.theory.HandShapeVisual
import com.bigpapituna.sharpears.engine.theory.IntervalVisual
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.LaneVisual
import com.bigpapituna.sharpears.engine.theory.MapPass
import com.bigpapituna.sharpears.engine.theory.MidiVisual
import com.bigpapituna.sharpears.engine.theory.PedalVisual
import com.bigpapituna.sharpears.engine.theory.PhraseHands
import com.bigpapituna.sharpears.engine.theory.PhraseVisual
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.PostureStep
import com.bigpapituna.sharpears.engine.theory.PostureVisual
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.SongMapVisual
import com.bigpapituna.sharpears.engine.theory.Visual
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.1: every reading step of the course is a short animated explainer. */
class CourseScenesTest {
    private val reads = COURSE.flatMap { it.lessons }.flatMap { l -> l.steps.filter { it.kind == StepKind.READ }.map { l.id to it } }

    @Test fun everyReadStepIsTwoToFourScenesAndItsTextIsTheCaptions() {
        assertTrue("read steps: ${reads.size}", reads.size >= 41)
        for ((id, step) in reads) {
            assertTrue("$id: ${step.scenes.size} scenes", step.scenes.size in 2..4)
            assertEquals("$id: the text is the captions in order", step.scenes.joinToString(" ") { it.caption }, step.text)
        }
        // Only reading steps carry scenes.
        COURSE.flatMap { it.lessons }.flatMap { it.steps }.filter { it.kind != StepKind.READ }.forEach { assertTrue(it.text, it.scenes.isEmpty()) }
    }

    @Test fun captionsAreShortSpeakableSentences() {
        val forbidden = Regex("[—–#&/♯♭·]| - ")
        for ((id, step) in reads) for (s in step.scenes) {
            listOf(s.caption, s.say).forEach {
                assertFalse("$id: \"$it\"", forbidden.containsMatchIn(it))
                assertTrue("$id: a sentence: \"$it\"", it.first().isUpperCase() && it.last() in ".!?")
            }
            assertTrue("$id: short enough for the bubble: \"${s.caption}\"", s.caption.length <= 200)
        }
    }

    @Test fun visualsStayOnThePianoAndTheirSoundsFitTheirTime() {
        for ((id, step) in reads) for (s in step.scenes) check(id, s.visual)
    }

    private fun check(id: String, v: Visual) {
        val name = "$id ${v::class.simpleName}"
        assertTrue("$name has time", v.durationMs > 0)
        v.cues.forEach { c ->
            assertTrue("$name: cue at ${c.atMs} past ${v.durationMs}", c.atMs in 0..v.durationMs)
            c.notes.forEach { assertTrue("$name: note $it on the piano", it in 21..108) }
            assertTrue("$name: velocity ${c.velocity}", c.velocity in 1..127)
        }
        fun white(m: Int) = assertFalse("$name: keyboard ends on white keys", isBlack(m))
        when (v) {
            is KeysVisual -> { white(v.low); white(v.high); v.frames.flatMap { it.notes }.forEach { assertTrue("$name: key $it off ${v.low}..${v.high}", it in v.low..v.high) } }
            is IntervalVisual -> { white(v.low); white(v.high); v.pairs.forEach { assertTrue(name, it.low.midi >= v.low && it.high.midi <= v.high && it.low.midi < it.high.midi) } }
            is ChordVisual -> v.chords.flatMap { it.notes }.forEach { assertTrue("$name: ${it.code} off the keys", it.midi in v.low..v.high) }
            is RhythmVisual -> {
                v.hits.forEach { assertTrue("$name: hit past the pattern", it.start + it.length <= v.beats + 0.001f) }
                if (v.counts.isNotEmpty()) assertEquals("$name: one count per subdivision", v.beats * v.sub, v.counts.size)
            }
            is FingerNumbersVisual -> {
                white(v.low); white(v.high)
                assertTrue("$name: five keys a hand", v.rightKeys.size in setOf(0, 5) && v.leftKeys.size in setOf(0, 5) && (v.rightKeys + v.leftKeys).isNotEmpty())
                v.steps.flatMap { it.presses }.forEach {
                    assertTrue("$name: finger ${it.finger}", it.finger in 1..5)
                    assertTrue("$name: key ${it.key} off the keys", it.key in v.low..v.high)
                    assertTrue("$name: a hand that plays is shown", (if (it.right) v.rightKeys else v.leftKeys).isNotEmpty())
                }
            }
            is HandShapeVisual -> assertEquals(name, 5, v.keys.size)
            is PostureVisual -> assertEquals("$name: each cue once", v.steps.size, v.steps.map { it.cue }.toSet().size)
            is PedalVisual -> { white(v.low); white(v.high); assertTrue(name, v.chords.isNotEmpty() && v.chords.all { it.isNotEmpty() }) }
            is DynamicsVisual -> { assertEquals(name, v.notes.size, v.levels.size); assertTrue(name, v.marks.keys.all { it in v.notes.indices }) }
            is ArticulationVisual -> { white(v.low); white(v.high); assertTrue(name, v.notes.isNotEmpty()) }
            is PhraseVisual -> {
                assertTrue("$name: the tokens parse to notes", v.notes.any { !it.rest })
                white(v.low); white(v.high)
                v.notes.flatMap { it.pitches }.forEach { assertTrue("$name: ${it.code} off the keys", it.midi in v.low..v.high) }
                assertTrue("$name: passes", v.passes.isNotEmpty() && v.span > 0f)
            }
            is LaneVisual -> {
                assertTrue("$name: notes", v.notes.isNotEmpty())
                white(v.low); white(v.high)
                v.notes.flatMap { it.pitches }.forEach { assertTrue("$name: ${it.code} off the keys", it.midi in v.low..v.high) }
            }
            is SongMapVisual -> v.passes.forEach { assertTrue("$name: pass $it", it.from in v.sections.indices && it.to in it.from..v.sections.lastIndex && it.speed in 10..100) }
            is MidiVisual -> assertTrue(name, v.signals.isNotEmpty())
            else -> {}
        }
    }

    @Test fun theNewPicturesAreUsedWhereTheyBelong() {
        fun kinds(lessonId: String) = reads.filter { it.first == lessonId }.flatMap { (_, s) -> s.scenes.map { it.visual::class.simpleName } }
        assertTrue(kinds("sit").containsAll(listOf("PostureVisual", "HandShapeVisual")))
        assertTrue("FingerNumbersVisual" in kinds("fingers-right"))
        assertTrue("PedalVisual" in kinds("pedal") && "MidiVisual" in kinds("pedal"))
        assertTrue("DynamicsVisual" in kinds("t2-shaping") && "ArticulationVisual" in kinds("t2-touch") && "PedalVisual" in kinds("t2-pedal-progression"))
        // Each repertoire lesson previews its opening phrase.
        COURSE.filter { it.id.startsWith("repertoire-") }.flatMap { it.lessons }.forEach { assertTrue(it.id, "PhraseVisual" in kinds(it.id)) }
    }

    @Test fun thePedalLiftsWithEachChordAndGoesDownAfterIt() {
        val v = PedalVisual(listOf(listOf(60, 64, 67), listOf(60, 65, 69)))
        assertEquals(0f, v.pedal(v.chordAt(0) + 10), 0f)
        assertEquals(1f, v.pedal(v.downAt(0) + 200), 0f)
        assertTrue("up as the new chord sounds", v.pedal(v.chordAt(1) + 150) < 0.01f)
        assertEquals(1f, v.pedal(v.downAt(1) + 200), 0f)
        // Each chord rings until the pedal comes up for the next.
        assertEquals(v.liftAt(1) + 60 - v.chordAt(0), v.cues[0].holdMs)
        val late = v.copy(late = true)
        assertTrue("late: the old chord still rings over the new one", late.cues[0].atMs + late.cues[0].holdMs > late.chordAt(1) + 500)
    }

    @Test fun aPhraseSoundsInRhythmSkipsRestsAndFrontsOneHand() {
        val v = PhraseVisual("C4:1 r:1 E4:2", "C3:4", bpm = 60, passes = listOf(PhraseHands.RIGHT, PhraseHands.BOTH))
        assertEquals(4f, v.span, 0f)
        assertEquals(listOf(0, 0, 2000), v.cues.take(3).map { it.atMs - v.passAt(0) })
        assertEquals("the left hand is quiet while the right is in front", 38, v.cues.first { it.notes == listOf(48) }.velocity)
        assertEquals(6, v.cues.size)                                         // three notes a pass, the rest silent
        assertEquals(0 to 1f, v.cursor(v.at(0, 1f)))
        assertNull("between passes", v.cursor(v.passAt(1) - 200))
        assertEquals(2f, PhraseVisual("C4:1 D4:1 E4:2", upTo = 2f).span, 0f)
    }

    @Test fun songMapLoopsASectionAtItsSpeed() {
        val v = SongMapVisual(listOf("A", "B"), listOf(MapPass(0, 1), MapPass(1, 1, 50)), melody = listOf(60, 62, 64, 65), sectionMs = 1000)
        assertEquals(2000, v.length(v.passes[0])); assertEquals(2000, v.length(v.passes[1]))
        assertEquals(1 to 1.5f, v.playhead(v.passAt(1) + 1000))
        assertEquals("two notes a section, four, then the loop's two", 6, v.cues.size)
        assertEquals(listOf(64, 65), v.cues.takeLast(2).flatMap { it.notes })
    }

    @Test fun postureCuesSettleOneAfterAnother() {
        val v = PostureVisual(listOf(PostureStep(PostureCue.BENCH, "a"), PostureStep(PostureCue.BACK, "b")))
        assertEquals(0f, v.settled(PostureCue.BACK, v.stepAt(1)), 0f)
        assertEquals(1f, v.settled(PostureCue.BENCH, v.stepAt(1)), 0f)
        assertEquals("parts not listed start right", 1f, v.settled(PostureCue.LEVEL, 0), 0f)
    }
}
