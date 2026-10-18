package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.play.Jam
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * The sounds the app plays while the microphone listens (2.1.1: a pop per key found in a stage, a ding when a stage's
 * rounds are done, a whoosh for the Jam's chord and rainbow) have no pitch: played through the note matcher at three
 * levels, with every key on the piano expected and with each stage's own keys, none is ever a key struck or a wrong
 * key. The matcher listens as the stages and the Jam do, with strict keys (NoteMatcher.strictKeys); the player's plain
 * matcher never listens while one of them plays (its own ticks wait for the mic to stop, Juice.onStrike). They are
 * made by scripts/gen_jingles.py.
 */
class StageSoundsTest {
    private val raw = File("src/main/res/raw")
    private val sounds = listOf("stage_pop", "stage_ding", "jam_whoosh")

    /** The WAV at 48 kHz, scaled to [gainDb], with a second of a quiet room before and after it. */
    private fun load(name: String, gainDb: Double): FloatArray {
        val w = Wav.read(File(raw, "$name.wav"))
        val k = 10.0.pow(gainDb / 20).toFloat()
        val n = (w.samples.size.toLong() * 48_000 / w.sampleRate).toInt()
        val sound = FloatArray(n) { i ->
            val p = i.toDouble() * w.sampleRate / 48_000
            val j = p.toInt().coerceAtMost(w.samples.size - 2)
            (w.samples[j] + (w.samples[j + 1] - w.samples[j]) * (p - j)).toFloat() * k
        }
        val room = PianoSynth.render(emptyList(), 2000.0 + n * 1000.0 / 48_000, noiseDb = -66.0)
        for (i in sound.indices) room[48_000 + i] += sound[i]
        return room
    }

    private fun heard(audio: FloatArray, expected: Set<Int>, strict: Boolean, groups: List<Set<Int>> = expected.map { setOf(it) }): List<MicEvent> {
        val m = NoteMatcher()
        m.strictKeys = strict
        m.expected = expected; m.groups = groups
        val out = ArrayList<MicEvent>()
        var i = 0
        while (i < audio.size) { val n = minOf(512, audio.size - i); out += m.process(audio.copyOfRange(i, i + n)); i += n }
        return out
    }

    @Test fun theFilesAreThere() {
        for (s in sounds) {
            val w = Wav.read(File(raw, "$s.wav"))
            assertEquals(44_100, w.sampleRate)
            assertTrue("$s is short", w.samples.size in 2_000..44_100)
        }
    }

    @Test fun noSoundIsEverAKeyWithEveryKeyExpected() {
        val bad = mutableListOf<String>()
        for (s in sounds) for (gain in listOf(0.0, -12.0, -24.0)) {
            val ev = heard(load(s, gain), (21..108).toSet(), strict = true)
            if (ev.any { it is MicEvent.Strike || it is MicEvent.Wrong })
                bad += "$s at $gain dB was heard as ${ev.map { (it as? MicEvent.Strike)?.midi?.let(::noteName) ?: it.toString() }}"
        }
        assertTrue(bad.joinToString("; "), bad.isEmpty())
    }

    @Test fun noSoundIsEverAKeyOrAWrongKeyOnTheStagesAndTheJam() {
        // 2.2: the concert hall's letters, the C position (the copy games, the soft and loud tune) and the pond's chord, one group.
        val sets = listOf(StageKeys.HIGH_LOW, StageKeys.DOG_HOUSES, StageKeys.FENCES, StageKeys.CS, StageKeys.SOFT_LOUD, Jam().micNotes()).map { it to it.map { k -> setOf(k) } } +
            listOf(2, 4, 5, 7).map { StageKeys.letter(it).let { set -> set to set.map { k -> setOf(k) } } } +
            listOf(StageKeys.FIVE to StageKeys.FIVE.map { setOf(it) }, StageKeys.C_CHORD to listOf(StageKeys.C_CHORD))
        val bad = mutableListOf<String>()
        for (s in sounds) for (gain in listOf(0.0, -12.0, -24.0)) for ((set, groups) in sets) {
            val ev = heard(load(s, gain), set, strict = true, groups = groups)
            if (ev.any { it is MicEvent.Strike || it is MicEvent.Wrong }) bad += "$s at $gain dB with ${set.map(::noteName)} was heard as $ev"
        }
        assertTrue(bad.joinToString("; "), bad.isEmpty())
    }

    /** The test is not blind: a piano note mixed in where the pop is still comes out as that note. */
    @Test fun aKeyStruckWithTheSoundIsStillHeard() {
        val audio = load("stage_pop", -12.0)
        val note = PianoSynth.render(listOf(SynthNote(61, 1000.0, 700.0)), audio.size * 1000.0 / 48_000)
        for (i in audio.indices) audio[i] += note[i]
        val ev = heard(audio, StageKeys.DOG_HOUSES, strict = true)
        assertEquals(listOf(61), ev.filterIsInstance<MicEvent.Strike>().map { it.midi })
    }
}
