package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Soft and loud (lesson 11): the grade from a strike's rise, and the sleeping-pup game around it. */
class SoftLoudTest {
    @Test fun fixedThresholdsBeforeTheFirstNote() {
        assertEquals(Loudness.SOFT, SoftLoud.grade(-45.0))
        assertEquals(Loudness.SOFT, SoftLoud.grade(SoftLoud.SOFT_AT_MOST_DB))
        assertEquals(Loudness.MEDIUM, SoftLoud.grade(-34.0))
        assertEquals(Loudness.LOUD, SoftLoud.grade(SoftLoud.LOUD_AT_LEAST_DB))
        assertEquals(Loudness.LOUD, SoftLoud.grade(-15.0))
        // A kid's profile makes the mic 3 dB easier: lighter hands still reach loud.
        assertEquals(Loudness.MEDIUM, SoftLoud.grade(-30.0))
        assertEquals(Loudness.LOUD, SoftLoud.grade(-30.0, offsetDb = 3.0))
    }

    @Test fun relativeToTheFirstNote() {
        // A quiet phone: everything reads low, but soft and loud still stand apart from the child's own normal note.
        assertEquals(Loudness.SOFT, SoftLoud.grade(-58.0, anchorDb = -52.0))
        assertEquals(Loudness.MEDIUM, SoftLoud.grade(-49.0, anchorDb = -52.0))
        assertEquals(Loudness.LOUD, SoftLoud.grade(-46.0, anchorDb = -52.0))
        assertEquals(Loudness.LOUD, SoftLoud.grade(-52.0 + SoftLoud.MARGIN_DB, anchorDb = -52.0))
    }

    @Test fun theGameAsksSoftSoftLoudAndNeverGetsStuck() {
        val g = SoftLoudGame()
        assertEquals("the first note only sets the level", Verdict(Loudness.MEDIUM, null, match = true, advance = true, woke = false), g.heard(-30.0))
        assertEquals(-30.0, g.anchorDb!!, 0.0)
        assertEquals(Loudness.SOFT, g.target)
        // Soft round: a medium note only makes him stir; that is fine.
        assertTrue(g.heard(-31.0).let { it.match && it.advance && !it.woke })
        // Soft round: a loud note wakes him and asks again; a second loud one is let go.
        val loud = g.heard(-20.0)
        assertTrue(loud.woke && !loud.match && !loud.advance)
        assertEquals(1, g.round)
        val again = g.heard(-20.0)
        assertTrue("let go after ${SoftLoud.TRIES} tries", !again.match && again.advance)
        // Loud round: medium is not enough, loud is.
        assertEquals(Loudness.LOUD, g.target)
        assertFalse(g.heard(-29.0).advance)
        assertTrue(g.heard(-22.0).let { it.match && it.advance && it.woke })
        // The rest, each as asked.
        while (!g.done) assertTrue(g.heard(if (g.target == Loudness.LOUD) -18.0 else -40.0).match)
        assertEquals(SoftLoud.ROUNDS.size - 1, g.matched)
    }

    @Test fun pianoSynthStrikesGradeByHowHardTheyWere() {
        // The same middle C struck medium, soft and hard, through the note matcher.
        fun rise(gainDb: Double): Double {
            val audio = PianoSynth.render(listOf(SynthNote(60, 400.0, 900.0, gainDb = gainDb)), 1600.0)
            val m = NoteMatcher(48_000).apply { expected = setOf(60) }
            var i = 0
            val strikes = mutableListOf<MicEvent.Strike>()
            while (i < audio.size) { val n = minOf(480, audio.size - i); strikes += m.process(audio.copyOfRange(i, i + n)).filterIsInstance<MicEvent.Strike>(); i += n }
            return strikes.single().riseDb
        }
        val normal = rise(-20.0); val soft = rise(-30.0); val hard = rise(-8.0)
        println("rise: normal %.1f, soft %.1f, hard %.1f dB".format(normal, soft, hard))
        assertEquals(Loudness.SOFT, SoftLoud.grade(soft, anchorDb = normal))
        assertEquals(Loudness.LOUD, SoftLoud.grade(hard, anchorDb = normal))
        assertEquals(Loudness.MEDIUM, SoftLoud.grade(rise(-21.0), anchorDb = normal))
        // And the whole game on PianoSynth: normal, then the rounds played as asked.
        val g = SoftLoudGame()
        g.heard(normal)
        for (t in SoftLoud.ROUNDS) assertTrue(g.heard(if (t == Loudness.LOUD) hard else soft).match)
        assertTrue(g.done)
    }

    @Test fun theExerciseHasANotePerRound() {
        val song = SoftLoud.exercise().toSong("s", "s")
        assertEquals(SoftLoud.ROUNDS.size + 1, song.groups.size)
        assertTrue(song.notes.all { it.midi == 60 })
    }
}
