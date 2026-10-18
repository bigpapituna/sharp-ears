package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.InputResult
import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.Mode
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Transport
import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The range exercises with sound: PianoSynth notes through the note matcher or the pitch tracker into the transport in
 * wait mode. Since 2.1.1 the stages hear through the matcher with strict keys (StageHearingTest); the pitch tracker is
 * only their fallback guess, still checked here for what it names.
 */
class RangeAudioTest {
    private val fs = 48_000
    private fun step(lesson: Int, game: Game, nth: Int = 0) =
        PLAYGROUND.lessons[lesson - 1].steps.filter { it.game == game }[nth].exercise!!.toSong("p$lesson", "p$lesson")

    private class Run(val t: Transport, val results: List<Pair<Int, InputResult>>) {
        val done get() = t.report().groups
    }

    /** Notes [played] (name to the second it is struck) through a matcher listening as the player does. */
    private fun throughMatcher(song: com.bigpapituna.sharpears.engine.Song, played: List<Pair<String, Double>>, lengthS: Double, strict: Boolean = false): Run {
        val audio = PianoSynth.render(played.map { (n, at) -> SynthNote(noteNumber(n), at * 1000, 700.0) }, lengthS * 1000)
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        val m = NoteMatcher(fs).apply { strictKeys = strict }
        val results = mutableListOf<Pair<Int, InputResult>>()
        var i = 0
        while (i < audio.size) {
            m.expected = t.listenFor; m.groups = t.listenGroups
            val n = minOf(480, audio.size - i)
            for (e in m.process(audio.copyOfRange(i, i + n))) if (e is MicEvent.Strike) results += e.midi to t.noteInput(e.midi)
            t.tick(n * 1000.0 / fs)
            i += n
        }
        return Run(t, results)
    }

    /** The same through the pitch tracker (the high-and-low stage hears this way). */
    private fun throughPitch(song: com.bigpapituna.sharpears.engine.Song, played: List<Pair<String, Double>>, lengthS: Double): Run {
        val audio = PianoSynth.render(played.map { (n, at) -> SynthNote(noteNumber(n), at * 1000, 700.0) }, lengthS * 1000)
        val t = Transport(song).apply { mode = Mode.WAIT; play() }
        val p = PitchHeight(fs)
        val results = mutableListOf<Pair<Int, InputResult>>()
        var i = 0
        while (i < audio.size) {
            val n = minOf(480, audio.size - i)
            for (h in p.process(audio.copyOfRange(i, i + n))) results += h.key to t.noteInput(h.key)
            t.tick(n * 1000.0 / fs)
            i += n
        }
        return Run(t, results)
    }

    @Test fun theDogHouseHuntPassesOnAnyDogHouseAndNotOnItsNeighbours() {
        val song = step(2, Game.KEY_HUNT)
        val pass = throughMatcher(song, listOf("D#4" to 1.0, "C#5" to 3.5, "D#5" to 6.0), 8.0)
        assertEquals("three dog houses found: ${pass.results}", 3, pass.done)
        assertTrue(pass.results.all { it.second == InputResult.GROUP_DONE })
        // White keys right next to the dog house are not it.
        val fail = throughMatcher(song, listOf("E4" to 1.0, "C4" to 2.5, "D4" to 4.0, "F4" to 5.5), 7.0)
        assertEquals("nothing found: ${fail.results}", 0, fail.done)
        assertTrue(fail.t.waiting)
    }

    @Test fun theCHuntThroughTheMatcher() {
        val cs = throughMatcher(step(3, Game.KEY_HUNT), listOf("C5" to 1.0, "C3" to 3.5, "C4" to 6.0), 8.0)
        assertEquals("${cs.results}", 3, cs.done)
        // Keys that are not C, with strict keys as the stage listens (2.1.1): the plain matcher, asked about five Cs at
        // once, credited C3 for a G4 (its third partial) and C6 for a D4 on PianoSynth; strict, no key passes for a C.
        val notC = throughMatcher(step(3, Game.KEY_HUNT), listOf("G4" to 1.0, "D4" to 2.5, "A2" to 4.0, "E5" to 5.5), 7.5, strict = true)
        assertEquals("${notC.results}", 0, notC.results.size)
    }

    /** The pitch tracker names the struck key itself (the stages' fallback guess since 2.1.1). */
    @Test fun theHuntsThroughThePitchTracker() {
        val dog = throughPitch(step(2, Game.KEY_HUNT), listOf("D#4" to 1.0, "C#5" to 3.5, "D#5" to 6.0), 8.0)
        assertEquals("${dog.results}", 3, dog.done)
        val fence = throughPitch(step(2, Game.KEY_HUNT, 1), listOf("G#4" to 1.0, "F#5" to 3.5, "A#5" to 6.0), 8.0)
        assertEquals("${fence.results}", 3, fence.done)
        // A dog house is not a fence, and the white keys around a fence are not it either.
        val notFence = throughPitch(step(2, Game.KEY_HUNT, 1), listOf("C#4" to 1.0, "G4" to 2.5, "B4" to 4.0, "D#5" to 5.5), 7.0)
        assertEquals("${notFence.results}", 0, notFence.done)
        assertEquals(4, notFence.results.size)
        // Dog house, fence, fence, dog house: one of each group's keys, in any octave.
        val mixed = throughPitch(step(2, Game.KEY_HUNT, 2), listOf("C#4" to 1.0, "A#4" to 3.5, "G#5" to 6.0, "D#5" to 8.5), 10.5)
        assertEquals("${mixed.results}", 4, mixed.done)
        val cs = throughPitch(step(3, Game.KEY_HUNT), listOf("D4" to 1.0, "C5" to 2.5, "C2" to 5.0, "B3" to 7.5, "C6" to 8.5), 10.5)
        assertEquals("${cs.results}", listOf(InputResult.WRONG, InputResult.GROUP_DONE, InputResult.GROUP_DONE, InputResult.WRONG, InputResult.GROUP_DONE),
            cs.results.map { it.second })
    }

    @Test fun highAndLowThroughThePitchTracker() {
        val song = step(1, Game.HIGH_LOW, 2)          // high, low, high, low, high, low
        val pass = throughPitch(song, listOf("E6" to 1.0, "C3" to 3.5, "A5" to 6.0, "G2" to 8.5, "F#6" to 11.0, "D3" to 13.5), 15.5)
        assertEquals("${pass.results}", 6, pass.done)
        // The other side does not count: low keys while the pup waits to fly, then high ones while he waits to land.
        val fail = throughPitch(song, listOf("C3" to 1.0, "A2" to 2.5, "C4" to 4.0), 5.5)
        assertEquals("${fail.results}", 0, fail.done)
        assertTrue(fail.results.all { it.second == InputResult.WRONG })
    }

    @Test fun aHalfPianoRangeStillWorksThroughTheMatcherByItsSpreadKeys() {
        // Where a screen runs the high-and-low step in the plain player, the mic listens for twelve spread keys.
        val song = step(1, Game.HIGH_LOW)
        val pass = throughMatcher(song, listOf("C6" to 1.0, "G6" to 3.5, "C7" to 6.0), 8.0)
        assertEquals("${pass.results}", 3, pass.done)
    }
}
