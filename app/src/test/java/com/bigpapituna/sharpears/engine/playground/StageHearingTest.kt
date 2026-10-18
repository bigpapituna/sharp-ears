package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.Wav
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The stages hear the way the app hears them (2.1.1): the note matcher with strict keys listening for the stage's keys,
 * the pitch tracker on the raw audio, both through a StageEar, each decided sound judged by the stage. On PianoSynth,
 * every key of every stage's set advances its round, a key outside the set is never taken for one in it (nor for the
 * other side of middle C), and no key on the piano goes unanswered. On Roy's real scale recording (session-155837, local
 * only), the same holds for the notes he played.
 */
class StageHearingTest {
    private val fs = 48_000

    /** Every sound [audio] gave the stage: matcher and pitch tracker through a StageEar, decided (times in ms of audio). */
    private fun hear(audio: FloatArray, keys: Set<Int>): List<StageSound> {
        val m = NoteMatcher(fs).apply { strictKeys = true; expected = keys; groups = keys.map { setOf(it) } }
        val pitch = PitchHeight(fs)
        val ear = StageEar()
        val out = ArrayList<StageSound>()
        var noiseAt = -1L
        m.noise = { s -> noiseAt = s }
        var i = 0
        val buf = FloatArray(512)
        while (i < audio.size) {
            val n = minOf(512, audio.size - i)
            System.arraycopy(audio, i, buf, 0, n)
            val now = (i + n) * 1000L / fs
            pitch.process(buf, n).forEach { h -> out += ear.pitch(h.midi, h.clarity, now) }
            for (e in m.process(buf, n)) when (e) {
                is MicEvent.Strike -> if (e.inferredFrom == null) out += ear.strike(e.midi, e.riseDb, now)
                is MicEvent.Wrong -> out += ear.wrong(e.midi, e.riseDb, now)
            }
            if (noiseAt >= 0) { out += ear.noise(now); noiseAt = -1 }
            out += ear.poll(now)
            i += n
        }
        out += ear.poll(Long.MAX_VALUE / 2)
        return out.filter { it.final }
    }

    private fun key(k: Int, gainDb: Double) = PianoSynth.render(listOf(SynthNote(k, 1000.0, 700.0, gainDb = gainDb)), 2600.0, noiseDb = -66.0)

    @Test fun highAndLowOnPianoSynth() {
        val problems = mutableListOf<String>()
        var named = 0; var guessed = 0; var onsets = 0
        for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            val heard = hear(key(k, gain), StageKeys.HIGH_LOW)
            if (heard.isEmpty()) { problems += "${noteName(k)} at $gain dB: nothing at all"; continue }
            val s = heard.first()
            if (k in StageKeys.HIGH_LOW && (s.by != HeardBy.MATCHER || s.midi != k)) problems += "${noteName(k)}: heard as $s"
            when (s.by) { HeardBy.NAMED -> named++; HeardBy.PITCH -> guessed++; HeardBy.ONSET -> onsets++; else -> {} }
            for (wantHigh in listOf(true, false)) {
                val a = HighLowJudge.judge(s, wantHigh)
                val rightSide = if (k == 60) null else (k > 60) == wantHigh
                if (a is Answer.Found && rightSide != true) problems += "${noteName(k)} at $gain dB found a ${if (wantHigh) "high" else "low"} round ($s)"
                if (a is Answer.Miss && rightSide == true) problems += "${noteName(k)} at $gain dB missed its own side ($s)"
            }
        }
        println("high and low: outside the set, $named named, $guessed guessed by pitch, $onsets onsets only")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun theHuntsOnPianoSynth() {
        val problems = mutableListOf<String>()
        for (hunt in Hunt.entries) {
            val members = StageKeys.forHunt(hunt)
            var otherOctave = 0; var nudged = 0
            for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
                val heard = hear(key(k, gain), members)
                if (heard.isEmpty()) { problems += "$hunt ${noteName(k)} at $gain dB: nothing at all"; continue }
                val a = HuntJudge.judge(heard.first(), hunt)
                val right = Math.floorMod(k, 12) in hunt.pitchClasses
                when {
                    k in members && a != Answer.Found(k) -> problems += "$hunt ${noteName(k)} at $gain dB: $a (${heard.first()})"
                    !right && a is Answer.Found -> problems += "$hunt ${noteName(k)} at $gain dB was found (${heard.first()})"
                    right && k !in members && a is Answer.Found -> otherOctave++
                    !right && a is Answer.Miss && (a.why == MissWhy.WHITE_KEY || a.why == MissWhy.NOT_C) && !isBlack(k) -> nudged++
                }
            }
            println("$hunt: $otherOctave of the group's keys in other octaves found by name, $nudged white keys named for the nudge")
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun softAndLoudGradesEveryKeyOfItsSet() {
        for (k in StageKeys.SOFT_LOUD) for (gain in listOf(-8.0, -20.0, -32.0)) {
            val s = hear(key(k, gain), StageKeys.SOFT_LOUD).first()
            assertEquals("${noteName(k)} at $gain dB", HeardBy.MATCHER, s.by)
            assertEquals(k, s.midi)
            assertTrue(s.riseDb != null)
        }
        // Louder strikes rise more: the game can tell them apart.
        val soft = hear(key(60, -32.0), StageKeys.SOFT_LOUD).first().riseDb!!
        val loud = hear(key(60, -8.0), StageKeys.SOFT_LOUD).first().riseDb!!
        assertTrue("soft $soft, loud $loud", loud - soft > 2 * SoftLoud.MARGIN_DB)
    }

    @Test fun keysPlayedOneAfterAnotherAreEachHeardOnce() {
        // A kid plays the dog house's keys one after another, then a white key: one find each, then a nudge.
        val notes = listOf(61 to 1000.0, 63 to 2500.0, 73 to 4000.0, 64 to 5500.0)
        val audio = PianoSynth.render(notes.map { SynthNote(it.first, it.second, 700.0) }, 7500.0, noiseDb = -66.0)
        val heard = hear(audio, StageKeys.DOG_HOUSES)
        assertEquals(heard.toString(), listOf(61, 63, 73, 64), heard.map { it.midi })
        assertEquals(listOf(Answer.Found(61), Answer.Found(63), Answer.Found(73), Answer.Miss(MissWhy.WHITE_KEY, 64)),
            heard.map { HuntJudge.judge(it, Hunt.DOG_HOUSE) })
    }

    /** Roy's real piano: his C major scale (with MIDI truth), heard through each stage. Local only (the recording is not committed). */
    @Test fun roysScaleThroughTheStages() {
        val wav = File("src/test/resources/fixtures/session-155837.wav")
        if (!wav.exists()) { println("no session-155837 here (local only)"); return }
        val audio = Wav.read(wav).samples
        val truth = File(wav.path.removeSuffix(".wav") + ".notes").readLines().filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split(",").map(String::trim) }.filter { it[2].toInt() > 0 }.map { it[0].toLong() * 1000 / fs to it[1].toInt() }
        /** The sounds decided around each truth note (from 50 ms before it to the next, at most 600 ms). */
        fun perNote(keys: Set<Int>): List<Pair<Int, List<StageSound>>> {
            val heard = hear(audio, keys)
            return truth.mapIndexed { i, (at, midi) ->
                val end = minOf(at + 600, truth.getOrNull(i + 1)?.first?.minus(50) ?: Long.MAX_VALUE)
                midi to heard.filter { it.atMs in (at - 50) until end }
            }
        }
        val problems = mutableListOf<String>()
        for (hunt in Hunt.entries) {
            val notes = perNote(StageKeys.forHunt(hunt))
            var found = 0; var answered = 0
            for ((midi, sounds) in notes) {
                val answers = sounds.map { HuntJudge.judge(it, hunt) }
                val right = Math.floorMod(midi, 12) in hunt.pitchClasses
                if (sounds.isNotEmpty()) answered++
                if (right && answers.any { it is Answer.Found }) found++
                if (right && midi in StageKeys.forHunt(hunt) && answers.none { it is Answer.Found }) problems += "$hunt: ${noteName(midi)} was not found ($sounds)"
                if (!right && answers.any { it is Answer.Found }) problems += "$hunt: ${noteName(midi)} was found ($sounds)"
            }
            println("$hunt on Roy's scale: $found of ${notes.count { Math.floorMod(it.first, 12) in hunt.pitchClasses }} found, $answered of ${notes.size} notes answered")
        }
        val hl = perNote(StageKeys.HIGH_LOW)
        var wrongSide = 0
        for ((midi, sounds) in hl) for (wantHigh in listOf(true, false)) {
            val a = sounds.map { HighLowJudge.judge(it, wantHigh) }
            if (midi != 60 && (midi > 60) != wantHigh && a.any { it is Answer.Found }) { wrongSide++; problems += "high and low: ${noteName(midi)} found a ${if (wantHigh) "high" else "low"} round ($sounds)" }
        }
        println("high and low on Roy's scale: ${hl.count { it.second.isNotEmpty() }} of ${hl.size} notes answered, $wrongSide on the wrong side")
        val sl = perNote(StageKeys.SOFT_LOUD)
        val graded = sl.count { (midi, s) -> midi in StageKeys.SOFT_LOUD && s.any { it.midi == midi && it.riseDb != null } }
        println("soft and loud on Roy's scale: $graded of ${sl.count { it.first in StageKeys.SOFT_LOUD }} of its keys graded")
        if (graded < sl.count { it.first in StageKeys.SOFT_LOUD }) problems += "soft and loud graded $graded"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
