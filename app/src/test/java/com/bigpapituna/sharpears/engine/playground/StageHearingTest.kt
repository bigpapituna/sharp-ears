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
 * only), the same holds for the notes he played. 2.2 adds the pages' stages: the letter hunts (in the hunts' loop), the
 * copy games and the soft and loud tune (the C position) and the pond's chord (one group, every key its own sound).
 */
class StageHearingTest {
    private val fs = 48_000

    /**
     * Every sound [audio] gave the stage: matcher and pitch tracker through a StageEar, decided (times in ms of audio).
     * [groups] as the stage groups its keys (each its own by default); [chords]: the ear lets every key of a chord through.
     */
    private fun hear(audio: FloatArray, keys: Set<Int>, groups: List<Set<Int>> = keys.map { setOf(it) }, chords: Boolean = false): List<StageSound> {
        val m = NoteMatcher(fs).apply { strictKeys = true; expected = keys; this.groups = groups }
        val pitch = PitchHeight(fs)
        val ear = StageEar(chords = chords)
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

    /** [hear] for every key at both levels against [keys], on every core at once; by (key, level). */
    private fun heardEach(keys: Set<Int>, range: IntRange = 21..108, gains: List<Double> = listOf(-14.0, -26.0)): Map<Pair<Int, Double>, List<StageSound>> =
        heardEach(listOf(keys), range, gains).getValue(keys)

    /** The same for several sets in one go (all their cases share the cores); by set, then (key, level). */
    private fun heardEach(sets: List<Set<Int>>, range: IntRange = 21..108, gains: List<Double> = listOf(-14.0, -26.0)): Map<Set<Int>, Map<Pair<Int, Double>, List<StageSound>>> {
        val cases = sets.distinct().flatMap { s -> range.flatMap { k -> gains.map { Triple(s, k, it) } } }
        return cases.zip(parallelMap(cases) { (s, k, g) -> hear(key(k, g), s) }).groupBy({ it.first.first }, { (it.first.second to it.first.third) to it.second })
            .mapValues { it.value.toMap() }
    }

    @Test fun highAndLowOnPianoSynth() {
        val problems = mutableListOf<String>()
        var named = 0; var guessed = 0; var onsets = 0
        val each = heardEach(StageKeys.HIGH_LOW)
        for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            val heard = each.getValue(k to gain)
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
        val all = heardEach(Hunt.entries.map { StageKeys.forHunt(it) })
        for (hunt in Hunt.entries) {
            val members = StageKeys.forHunt(hunt)
            var otherOctave = 0; var nudged = 0
            val each = all.getValue(members)
            for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
                val heard = each.getValue(k to gain)
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

    /** The copy games and the soft and loud tune: each key of the C position is heard as itself; no other key passes for one. */
    @Test fun theCPositionOnPianoSynth() {
        val problems = mutableListOf<String>()
        var named = 0
        val each = heardEach(StageKeys.FIVE)
        for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            val heard = each.getValue(k to gain)
            if (heard.isEmpty()) { problems += "${noteName(k)} at $gain dB: nothing at all"; continue }
            val s = heard.first()
            if (k in StageKeys.FIVE && (s.by != HeardBy.MATCHER || s.midi != k)) problems += "${noteName(k)}: heard as $s"
            if (s.by == HeardBy.NAMED) named++
            for (want in StageKeys.FIVE) {
                val a = NoteJudge.judge(s, want)
                if (a is Answer.Found && Math.floorMod(k - want, 12) != 0) problems += "${noteName(k)} at $gain dB passed for ${noteName(want)} ($s)"
                if (k == want && a !is Answer.Found) problems += "${noteName(k)} at $gain dB was not itself ($s)"
            }
        }
        println("the C position: $named keys outside it named")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * 2.3: the workshop's left hand at home (C3 to G3) and every photo booth song's keys (the fair): each key of the set
     * heard as itself at two levels, no other key passing for one (NoteJudge: the same letter in another octave counts).
     * The C position (the hand stage's right hand, the pedal, the balloons, the carousel) and the strongman's keys (soft
     * and loud's) are tested above. Each key is rendered once and heard against every set.
     */
    @Test fun theWorkshopsAndTheFairsSetsOnPianoSynth() {
        val sets = (listOf(StageKeys.LEFT_FIVE) + SHELF.map { StageKeys.of(photoExercise(it)) }).distinct()
        assertTrue(sets.all { it.size <= 12 })
        val problems = mutableListOf<String>()
        val each = heardEach(sets)
        for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            for (set in sets) {
                val heard = each.getValue(set).getValue(k to gain)
                if (heard.isEmpty()) { problems += "${noteName(k)} at $gain dB with ${set.map(::noteName)}: nothing at all"; continue }
                val s = heard.first()
                if (k in set && (s.by != HeardBy.MATCHER || s.midi != k)) problems += "${noteName(k)} with ${set.map(::noteName)}: heard as $s"
                for (want in set) {
                    val a = NoteJudge.judge(s, want)
                    if (a is Answer.Found && Math.floorMod(k - want, 12) != 0) problems += "${noteName(k)} at $gain dB passed for ${noteName(want)} ($s)"
                    if (k == want && a !is Answer.Found) problems += "${noteName(k)} at $gain dB was not itself ($s)"
                }
            }
        }
        println("2.3 sets: ${sets.map { s -> s.map(::noteName) }}")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** A tune played one note after another, as a kid copies the frogs: every note heard once, in order. */
    @Test fun aTuneIsHeardNoteByNote() {
        val tune = listOf(60, 62, 64, 60, 67, 64, 67, 64)
        val audio = PianoSynth.render(tune.mapIndexed { i, k -> SynthNote(k, 1000.0 + i * 650, 520.0) }, 1000.0 + tune.size * 650 + 1500, noiseDb = -66.0)
        val heard = hear(audio, StageKeys.FIVE)
        assertEquals(heard.toString(), tune, heard.map { it.midi })
        assertTrue(heard.zip(tune).all { (s, k) -> NoteJudge.judge(s, k) == Answer.Found(k) })
    }

    /** The pond's chord: C, E and G together are found at two levels, rolled a little too; no single key and no other chord ever is. */
    @Test fun theChordOnPianoSynth() {
        val chord = StageKeys.C_CHORD
        fun judge(notes: List<Pair<Int, Double>>, gain: Double): List<Answer> {
            val audio = PianoSynth.render(notes.map { (k, at) -> SynthNote(k, at, 900.0, gainDb = gain) }, notes.maxOf { it.second } + 2000.0, noiseDb = -66.0)
            val j = ChordJudge(chord)
            return hear(audio, chord, listOf(chord), chords = true).map(j::judge)
        }
        val problems = mutableListOf<String>()
        for (gain in listOf(-14.0, -26.0)) {
            for (roll in listOf(0.0, 40.0, 150.0)) {
                val a = judge(listOf(60 to 1000.0, 64 to 1000.0 + roll, 67 to 1000.0 + 2 * roll), gain)
                if (a.none { it is Answer.Found }) problems += "C E G rolled $roll ms at $gain dB: $a"
            }
            val alone = parallelMap((21..108).toList()) { k -> judge(listOf(k to 1000.0), gain) }
            for (k in 21..108) {
                val a = alone[k - 21]
                if (a.any { it is Answer.Found }) problems += "${noteName(k)} alone at $gain dB found the chord"
            }
            for (other in listOf(listOf(60, 64, 69), listOf(60, 65, 69), listOf(59, 62, 67), listOf(62, 65, 69), listOf(48, 52, 55), listOf(72, 76, 79),
                    listOf(60, 64), listOf(64, 67), listOf(60, 67))) {
                val a = judge(other.map { it to 1000.0 }, gain)
                if (a.any { it is Answer.Found }) problems += "${other.map(::noteName)} at $gain dB found the chord: $a"
            }
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
        val sets = (Hunt.entries.map { StageKeys.forHunt(it) } + listOf(StageKeys.HIGH_LOW, StageKeys.FIVE, StageKeys.C_CHORD, StageKeys.SOFT_LOUD)).distinct()
        val heardBy = sets.zip(parallelMap(sets) { hear(audio, it) }).toMap()
        fun perNote(keys: Set<Int>): List<Pair<Int, List<StageSound>>> {
            val heard = heardBy[keys] ?: hear(audio, keys)
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
        // The copy games and the soft and loud tune: every C position note found as itself, nothing else passing for one.
        val five = perNote(StageKeys.FIVE)
        for ((midi, sounds) in five) for (want in StageKeys.FIVE) {
            val a = sounds.map { NoteJudge.judge(it, want) }
            if (midi == want && a.none { it is Answer.Found }) problems += "C position: ${noteName(midi)} was not found ($sounds)"
            if (Math.floorMod(midi - want, 12) != 0 && a.any { it is Answer.Found }) problems += "C position: ${noteName(midi)} passed for ${noteName(want)} ($sounds)"
        }
        println("the C position on Roy's scale: ${five.count { it.first in StageKeys.FIVE && it.second.any { s -> s.midi == it.first } }} of ${five.count { it.first in StageKeys.FIVE }} found")
        // The chord's keys one at a time: each heard as itself, never the chord.
        val chord = StageKeys.C_CHORD
        val chordNotes = perNote(chord)
        val j = ChordJudge(chord)
        if (chordNotes.flatMap { it.second }.map(j::judge).any { it is Answer.Found }) problems += "the chord was found in a scale"
        val sl = perNote(StageKeys.SOFT_LOUD)
        val graded = sl.count { (midi, s) -> midi in StageKeys.SOFT_LOUD && s.any { it.midi == midi && it.riseDb != null } }
        println("soft and loud on Roy's scale: $graded of ${sl.count { it.first in StageKeys.SOFT_LOUD }} of its keys graded")
        if (graded < sl.count { it.first in StageKeys.SOFT_LOUD }) problems += "soft and loud graded $graded"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
