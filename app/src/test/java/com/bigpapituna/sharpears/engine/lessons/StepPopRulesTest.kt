package com.bigpapituna.sharpears.engine.lessons

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.pup.PupMood
import com.bigpapituna.sharpears.engine.pup.PupMotion
import com.bigpapituna.sharpears.engine.tester.StepPopEvent
import com.bigpapituna.sharpears.engine.tester.TesterEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.11, the step pop: when a step pops, the streak and its climbing chime, the mic rule, and the tester event. */
class StepPopRulesTest {
    private val r = StepPopRules

    @Test fun aStepPopsOnlyWhenPassedCleanlyWithAnotherStepToCome() {
        assertEquals(StepOutcome.POP, r.outcome(listen = false, judged = true, letGo = false, last = false))
        assertEquals(StepOutcome.LAST, r.outcome(listen = false, judged = true, letGo = false, last = true))
        // A let-go is never a pop, last step or not, and a pass made of let-go rounds counts as one.
        assertEquals(StepOutcome.LET_GO, r.outcome(listen = false, judged = false, letGo = true, last = false))
        assertEquals(StepOutcome.LET_GO, r.outcome(listen = false, judged = false, letGo = true, last = true))
        assertEquals(StepOutcome.LET_GO, r.outcome(listen = false, judged = true, letGo = false, last = false, mostlyLetGo = true))
        assertEquals(StepOutcome.TRY_AGAIN, r.outcome(listen = false, judged = false, letGo = false, last = false))
        // A listening step has nothing to judge.
        for (judged in listOf(true, false)) assertEquals(StepOutcome.NONE, r.outcome(listen = true, judged = judged, letGo = false, last = false))
    }

    @Test fun halfTheRoundsLetGoIsALetGo() {
        assertFalse(r.mostlyLetGo(0, 6))
        assertFalse(r.mostlyLetGo(2, 6))
        assertTrue(r.mostlyLetGo(3, 6))
        assertTrue(r.mostlyLetGo(6, 6))
        assertFalse(r.mostlyLetGo(0, 0))      // nothing counted (a run outside the stages)
        assertFalse(r.mostlyLetGo(1, 0))
    }

    @Test fun theStreakGrowsWithEachCleanStepAndStartsAgainOnALetGoOrASkip() {
        val s = StepStreak()
        assertEquals(1, s.after(StepOutcome.POP))
        assertEquals(1, s.after(StepOutcome.TRY_AGAIN))      // a try that did not pass changes nothing (no punishment for trying)
        assertEquals(1, s.after(StepOutcome.NONE))
        assertEquals(2, s.after(StepOutcome.POP))
        assertEquals(3, s.after(StepOutcome.POP))
        assertEquals(0, s.after(StepOutcome.LET_GO))
        assertEquals(1, s.after(StepOutcome.POP))
        s.skipped()
        assertEquals(0, s.length)
        assertEquals(1, s.after(StepOutcome.LAST))
        // A new lesson is a new streak.
        assertEquals(0, StepStreak().length)
    }

    @Test fun theChimeClimbsOneScaleStepPerCleanStepUpToFive() {
        assertEquals(listOf(0, 0, 1, 2, 3, 4, 5, 5, 5), listOf(0, 1, 2, 3, 4, 5, 6, 7, 50).map(r::climb))
        // In C: C6 E6 G6 first, then D F A, E G B, F A C, G B D, A C E, and no higher.
        assertEquals(listOf(84, 88, 91), r.chime(0, 1).map { it.midi })
        assertEquals(listOf(86, 89, 93), r.chime(0, 2).map { it.midi })
        assertEquals(listOf(93, 96, 100), r.chime(0, 6).map { it.midi })
        assertEquals(r.chime(0, 6), r.chime(0, 12))
    }

    @Test fun everyChimeIsInTheLessonsKeyRisingBrightAndOnThePiano() {
        val major = setOf(0, 2, 4, 5, 7, 9, 11)
        for (tonic in 0..11) {
            var lastTop = -1
            for (streak in 1..6) {
                val c = r.chime(tonic, streak)
                assertEquals(3, c.size)
                // Every note a note of the key; the notes rise; 60 ms apart; louder as they rise, within 70..90.
                c.forEach { assertTrue("$tonic $streak ${it.midi}", Math.floorMod(it.midi - tonic, 12) in major) }
                assertTrue(c.zipWithNext().all { (a, b) -> b.midi > a.midi && b.atMs - a.atMs == r.NOTE_GAP_MS && b.velocity > a.velocity })
                assertTrue(c.all { it.velocity in 70..90 } && c.first().atMs == 0)
                // A consonant triad (a third and a fifth over its root): never a diminished one.
                assertTrue(c[1].midi - c[0].midi in 3..4 && c[2].midi - c[0].midi == 7)
                // Higher with each step of the climb, high and bright, and on the piano's samples (top sample p108).
                assertTrue(c.last().midi > lastTop); lastTop = c.last().midi
                assertTrue(c.first().midi in 78..101 && c.last().midi <= 108)
            }
            // The first chime is the key's own chord, its root the tonic.
            assertEquals(tonic, Math.floorMod(r.chime(tonic, 1).first().midi, 12))
        }
        // The chime and its fade are over in about 400 ms; the mic is deaf for it and the room's ring.
        assertTrue(r.CHIME_MS in 300..450 && r.FADE_MS < r.CHIME_MS - 2 * r.NOTE_GAP_MS)
        assertEquals(r.CHIME_MS + r.ROOM_TAIL_MS, r.DEAF_MS)
        assertTrue(r.VOICE_AFTER_MS >= r.CHIME_MS)
    }

    /** The mic rule: the chime is never heard by a listening mic through the speaker (never counted as the learner's note). */
    @Test fun theChimeIsSkippedWithSoundOffAndDeafensAListeningMic() {
        for (mic in listOf(true, false)) for (phones in listOf(true, false))
            assertEquals(ChimeMode.SKIP, r.chimeMode(micListening = mic, headphones = phones, soundOn = false))
        assertEquals(ChimeMode.PLAY_DEAF, r.chimeMode(micListening = true, headphones = false, soundOn = true))
        assertEquals(ChimeMode.PLAY, r.chimeMode(micListening = true, headphones = true, soundOn = true))
        assertEquals(ChimeMode.PLAY, r.chimeMode(micListening = false, headphones = false, soundOn = true))
        assertEquals(ChimeMode.PLAY, r.chimeMode(micListening = false, headphones = true, soundOn = true))
    }

    @Test fun theLessonsKeyIsWhereItsLastExerciseComesHome() {
        assertEquals(0, r.tonicOf(emptyList()))
        assertEquals(0, r.tonicOf(listOf(Exercise("C4:1 D4:1 E4:1 C4:1"))))
        assertEquals(7, r.tonicOf(listOf(Exercise("G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 F#5:1 G5:1"))))
        // The last exercise speaks for the lesson; the lowest key still sounding at its end (a held bass) is home.
        assertEquals(5, r.tonicOf(listOf(Exercise("C4:4"), Exercise("A4:2 C5:2", left = "F3:4"))))
        assertEquals(0, r.tonicOf(listOf(Exercise("C4+E4+G4:2"))))
        // A last moment that is a choice of keys says nothing: the exercise before it does.
        assertEquals(2, r.tonicOf(listOf(Exercise("D4:2"), Exercise("C6|C#4..C8:2"))))
        assertEquals(0, r.tonicOf(listOf(Exercise("C6|C#4..C8:2"))))
        // Every lesson in the course has a key the chime can use.
        for (lesson in COURSE.flatMap { it.lessons }) assertTrue(lesson.id, r.tonicOf(lesson.steps.mapNotNull { it.exercise }) in 0..11)
    }

    /** Four pictures to keep it fresh: never the same twice in a row, the same roll for the same lesson step, the small ones early and the big ones later. */
    @Test fun thePictureIsRolledPerStepAndNeverTheSameTwiceInARow() {
        assertEquals(setOf(PopVariant.BURST, PopVariant.PAW), r.preferred(1))
        assertEquals(setOf(PopVariant.BURST, PopVariant.PAW), r.preferred(2))
        assertEquals(PopVariant.entries.toSet(), r.preferred(3))
        assertEquals(setOf(PopVariant.RAINBOW, PopVariant.CONFETTI), r.preferred(4))
        assertEquals(setOf(PopVariant.RAINBOW, PopVariant.CONFETTI), r.preferred(9))
        // Deterministic: the same lesson step, streak and last picture roll the same picture.
        for (step in 0..20) {
            val seed = r.seedOf("garden-3", step)
            assertEquals(r.variant(2, PopVariant.BURST, seed), r.variant(2, PopVariant.BURST, seed))
        }
        assertTrue(r.seedOf("garden-3", 1) != r.seedOf("garden-3", 2) && r.seedOf("garden-3", 1) != r.seedOf("garden-4", 1))
        // Never twice in a row, for any streak, last picture and seed.
        for (streak in 1..8) for (prev in PopVariant.entries + listOf(null)) for (seed in 0L until 300L)
            assertTrue(r.variant(streak, prev, seed) != prev)
        // A whole lesson walked: every step a different picture from the one before, and all four turn up over the course.
        val seen = mutableSetOf<PopVariant>()
        for (lesson in COURSE.flatMap { it.lessons }) {
            var prev: PopVariant? = null
            for (step in lesson.steps.indices) {
                val v = r.variant(step + 1, prev, r.seedOf(lesson.id, step))
                assertTrue(lesson.id, v != prev); prev = v; seen += v
            }
        }
        assertEquals(PopVariant.entries.toSet(), seen)
        // Preferred, not forced: over many rolls the streak's pictures win most of the time, the others still turn up.
        fun share(streak: Int, liked: Set<PopVariant>): Double {
            var prev: PopVariant? = null
            val rolls = (0 until 2000).map { i -> r.variant(streak, prev, r.seedOf("share", i)).also { prev = it } }
            assertTrue("all four at streak $streak", rolls.toSet().size == 4)
            return rolls.count { it in liked } / 2000.0
        }
        assertTrue(share(1, setOf(PopVariant.BURST, PopVariant.PAW)) > 0.6)
        assertTrue(share(5, setOf(PopVariant.RAINBOW, PopVariant.CONFETTI)) > 0.6)
    }

    /** The pup's beat goes with the picture: his happy hop with BURST and PAW, the cheer wag with CONFETTI and RAINBOW. */
    @Test fun thePupsBeatPairsWithThePicture() {
        assertEquals(PupMood.POP, r.pupBeat(PopVariant.BURST))
        assertEquals(PupMood.POP, r.pupBeat(PopVariant.PAW))
        assertEquals(PupMood.CHEER_WAG, r.pupBeat(PopVariant.CONFETTI))
        assertEquals(PupMood.CHEER_WAG, r.pupBeat(PopVariant.RAINBOW))
        PopVariant.entries.forEach { assertTrue(PupMotion.beatMs(r.pupBeat(it)) in 1 until 700) }
        // The big pictures are the ones a long streak prefers.
        assertEquals(setOf(PopVariant.CONFETTI, PopVariant.RAINBOW), PopVariant.entries.filter { it.big }.toSet())
    }

    /** The tester event carries the streak's length and nothing else: no name, no id, no lesson, no kind. */
    @Test fun theTesterEventCarriesTheStreakOnly() {
        assertEquals(TesterEvent("step_pop", mapOf("streak" to 3)), StepPopEvent.of(3))
        assertEquals(setOf("streak"), StepPopEvent.of(1).props.keys)
    }
}
