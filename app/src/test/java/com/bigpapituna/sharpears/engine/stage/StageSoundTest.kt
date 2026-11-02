package com.bigpapituna.sharpears.engine.stage

import com.bigpapituna.sharpears.engine.Ducking
import com.bigpapituna.sharpears.engine.lessons.ChimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.18: the Stage's rule, its deaf windows and its sounds, through its interface with a recording bank and a fake mic. */
class StageSoundTest {
    private var now = 0L
    private val timers = ArrayList<Pair<Long, () -> Unit>>()
    private fun advance(ms: Long) {
        val until = now + ms
        while (true) {
            val next = timers.filter { it.first <= until }.minByOrNull { it.first } ?: break
            timers.remove(next); now = next.first; next.second()
        }
        now = until
    }

    private class Bank : SoundBank {
        val played = ArrayList<Triple<Int, Float, Float>>()
        val volumes = HashMap<Int, Float>()
        val stopped = ArrayList<Int>()
        private var next = 100
        override fun play(sound: Int, volume: Float, rate: Float, priority: Int): Int { played += Triple(sound, volume, rate); volumes[next] = volume; return next++ }
        override fun volume(stream: Int, volume: Float) { volumes[stream] = volume }
        override fun stop(stream: Int) { stopped += stream }
    }

    private inner class Mic : StageSound.Ear {
        override var listening = false
        override val window = DeafWindow { now }
    }

    private val bank = Bank()
    private val mic = Mic()
    private var phones = false
    private var cues = 0
    private val stage = StageSound(bank, { phones }, { mic }, { ms, block -> timers += (now + ms) to block }).also { it.onCue = { cues++ } }

    @Test fun theRuleWithAndWithoutHeadphones() {
        // Mic off: the app may make every sound.
        assertFalse(stage.quiet()); assertTrue(stage.echo(soundOn = true)); assertEquals(ChimeMode.PLAY, stage.cue(soundOn = true))
        // Mic on, speaker: echoes stay quiet, cues play with the mic deaf.
        mic.listening = true
        assertTrue(stage.quiet()); assertFalse(stage.echo(soundOn = true)); assertEquals(ChimeMode.PLAY_DEAF, stage.cue(soundOn = true))
        // Mic on, headphones: nothing comes out next to the mic.
        phones = true
        assertFalse(stage.quiet()); assertTrue(stage.echo(soundOn = true)); assertEquals(ChimeMode.PLAY, stage.cue(soundOn = true))
        // App sound off: nothing, whatever the rest.
        assertFalse(stage.echo(soundOn = false)); assertEquals(ChimeMode.SKIP, stage.cue(soundOn = false))
        // A screen's own idea of listening wins (a stage that keeps its mic between rounds).
        phones = false
        assertTrue(stage.echo(soundOn = true, listening = false))
    }

    @Test fun aCueOnTheSpeakerMakesTheMicDeafForItsLengthOnly() {
        mic.listening = true
        assertTrue(stage.deafFor(800))
        assertTrue(mic.window.deaf())
        advance(799); assertTrue(mic.window.deaf())
        advance(2); assertFalse(mic.window.deaf())
        // With headphones the mic keeps hearing.
        phones = true
        assertFalse(stage.deafFor(800))
        assertFalse(mic.window.deaf())
    }

    /**
     * Bug 3 (2.18): the voice's window over the mic was capped at 8 s (MicInput.voiceDeafLimit), so a long natural line (Liam's
     * narration runs past it) let the mic hear the rest of it; the coach's 500 ms slices kept it deaf up to half a second
     * past a line cut short. Now the window is the line's: deaf from its start until exactly its end, then the room's tail.
     */
    @Test fun aLineKeepsTheMicDeafUntilExactlyItsEndPlusTheTail() {
        mic.listening = true
        stage.lineStarts("voice-1", lengthMs = null)
        advance(12_000)
        assertTrue("still deaf 12 s into a long line", mic.window.deaf())
        stage.lineEnds("voice-1", tailMs = 300)
        assertTrue("the room's ring", mic.window.deaf())
        advance(299); assertTrue(mic.window.deaf())
        advance(2); assertFalse("hears again right after the tail", mic.window.deaf())
    }

    @Test fun aLineThatErrorsOrIsHushedNeverLeavesTheMicDeaf() {
        mic.listening = true
        // A clip that fails reports its end like any other: the window closes.
        stage.lineStarts("clip", 3_000)
        advance(100)
        stage.lineEnds("clip", tailMs = 0)
        assertFalse(mic.window.deaf())
        // A screen left mid-line: every line lets go at once, no tail.
        stage.lineStarts("coach", 5_000); stage.lineStarts("voice", null)
        advance(1_000)
        stage.hushLines()
        assertFalse(mic.window.deaf())
        assertFalse(stage.playing("coach"))
        // A line whose end is never reported frees the mic just after its known length.
        stage.lineStarts("lost", null)
        stage.lineLength("lost", 4_000)
        advance(4_000 + StageRule.LIMIT_SLACK_MS - 1); assertTrue(mic.window.deaf())
        advance(2); assertFalse(mic.window.deaf())
        // ...and its late end adds no tail after the fact.
        stage.lineEnds("lost", tailMs = 300)
        assertFalse(mic.window.deaf())
    }

    @Test fun headphonesKeepTheMicHearingThroughALine() {
        mic.listening = true; phones = true
        stage.lineStarts("coach", 3_000, duck = true, cue = true)
        assertFalse(mic.window.deaf())
        stage.lineEnds("coach", 300)
        advance(1_000)
        assertEquals("no deaf window, no Listening again", 0, cues)
    }

    @Test fun listeningAgainAfterADeafeningLineOnceTheRingHasPassed() {
        mic.listening = true
        stage.lineStarts("coach", 3_000, cue = true)
        advance(3_000)
        stage.lineEnds("coach", 300)
        assertEquals(0, cues)
        advance(300)
        assertEquals(1, cues)
        // Not while the mic is off, nor for a line that did not ask for it, nor while another line still holds the mic.
        mic.listening = false
        stage.lineStarts("coach", 1_000, cue = true); stage.lineEnds("coach", 300); advance(400)
        mic.listening = true
        stage.lineStarts("voice", 1_000); stage.lineEnds("voice", 300); advance(400)
        stage.lineStarts("coach", 1_000, cue = true); stage.lineStarts("voice", null); stage.lineEnds("coach", 300); advance(400)
        assertEquals(1, cues)
    }

    @Test fun aDuckingLineTurnsTheNotesDownAndBackNotesSoundingIncluded() {
        stage.piano(mapOf(60 to 1, 72 to 2))
        stage.noteOn(60, 127)
        assertEquals(1f, bank.played.last().second, 1e-6f)
        stage.lineStarts("coach", 2_000, duck = true)
        assertTrue(stage.isDucked)
        val stream = bank.volumes.keys.max()
        assertEquals(Ducking.gain(true), bank.volumes.getValue(stream), 1e-6f)
        stage.noteOn(62, 127)
        assertEquals(Ducking.gain(true), bank.played.last().second, 1e-6f)
        stage.lineEnds("coach", 0)
        assertFalse(stage.isDucked)
        assertEquals(1f, bank.volumes.getValue(stream), 1e-6f)
        // A voice line does not duck.
        stage.lineStarts("voice", 1_000)
        assertFalse(stage.isDucked)
    }

    @Test fun pianoNotesPickTheNearestSampleAndStopOnRelease() {
        stage.piano(mapOf(60 to 1, 72 to 2))
        stage.noteOn(71, 64)
        assertEquals(2, bank.played.last().first)
        assertEquals(Math.pow(2.0, -1.0 / 12).toFloat(), bank.played.last().third, 1e-5f)
        val stream = bank.volumes.keys.max()
        val struck = bank.volumes.getValue(stream)
        stage.noteOff(71)
        // 2.19.0: the 8 s samples are let go with a short damper fade, then stopped.
        assertTrue(bank.stopped.isEmpty())
        advance(StageSound.RELEASE_MS / 2)
        assertTrue(bank.volumes.getValue(stream) in 0.01f..struck * 0.6f)
        advance(StageSound.RELEASE_MS)
        assertEquals(listOf(stream), bank.stopped)
        stage.noteOff(71)                                       // already off: nothing more
        advance(StageSound.RELEASE_MS * 2)
        assertEquals(1, bank.stopped.size)
    }
}
