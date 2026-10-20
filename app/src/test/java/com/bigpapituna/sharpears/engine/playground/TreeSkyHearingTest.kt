package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.clickSamples
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.toSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * The 2.3 stages hear the way the app hears them (as StageHearingTest for the other pages): the note matcher with
 * strict keys listening for each stage's keys (never more than twelve), the pitch tracker for a guess, a StageEar, the
 * stage's judge. On PianoSynth, every key of every set the treehouse and the night sky ask about is heard as itself, no
 * key outside a set passes for one in it, higher or lower never takes a key on the wrong side, the jam never draws a
 * star for a white key, and the beat games' sounds (the pup's drum, the metronome) and the jam's band are never a key.
 */
class TreeSkyHearingTest {
    private val fs = 48_000

    private fun hear(audio: FloatArray, keys: Set<Int>, deaf: List<LongRange> = emptyList()): List<StageSound> {
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
            val isDeaf = deaf.any { now in it }
            pitch.process(buf, n).forEach { h -> if (!isDeaf) out += ear.pitch(h.midi, h.clarity, now) }
            for (e in m.process(buf, n)) if (!isDeaf) when (e) {
                is MicEvent.Strike -> if (e.inferredFrom == null) out += ear.strike(e.midi, e.riseDb, now)
                is MicEvent.Wrong -> out += ear.wrong(e.midi, e.riseDb, now)
            }
            if (noiseAt >= 0) { if (!isDeaf) out += ear.noise(now); noiseAt = -1 }
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

    /** The copy games' and the which-finger games' key sets: each key of the stage's exercise. */
    private fun copySets(): List<Set<Int>> = (TREEHOUSE.lessons + NIGHT_SKY.lessons).flatMap { it.steps }
        .filter { StageGames.of(it) is StageGame.Copy || StageGames.of(it) is StageGame.Tap && (StageGames.of(it) as StageGame.Tap).kind == TapKind.FINGER }
        .map { s -> s.exercise!!.toSong("k", "k").notes.map { it.midi }.toSet() }.distinct()

    @Test fun everySetIsTwelveKeysAtMost() {
        val sets = copySets() + listOf(JAM_KEYS, StageKeys.C_CHORD, setOf(60)) + PITCH_SETS
        assertTrue(sets.all { it.size in 1..12 })
        // The positions the treehouse plays in: C (both hands), G and F (with B flat), the tune high and low.
        assertTrue(copySets().containsAll(listOf(setOf(48, 50, 52, 53, 55), setOf(67, 69, 71, 72, 74), setOf(65, 67, 69, 70, 72), setOf(48, 50, 52, 60, 62, 64))))
    }

    /** Each copy game's keys heard as themselves; nothing else passes for the note a round waits for. */
    @Test fun theTreehousePositionsOnPianoSynth() {
        val problems = mutableListOf<String>()
        var named = 0
        val each = heardEach(copySets().filter { it != StageKeys.FIVE && it.size > 1 })
        for (set in each.keys) for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            val heard = each.getValue(set).getValue(k to gain)
            if (heard.isEmpty()) { problems += "${set.map(::noteName)}: ${noteName(k)} at $gain dB: nothing at all"; continue }
            val s = heard.first()
            if (k in set && (s.by != HeardBy.MATCHER || s.midi != k)) problems += "${set.map(::noteName)}: ${noteName(k)} heard as $s"
            if (s.by == HeardBy.NAMED) named++
            for (want in set) {
                val a = NoteJudge.judge(s, want)
                if (a is Answer.Found && Math.floorMod(k - want, 12) != 0) problems += "${noteName(k)} at $gain dB passed for ${noteName(want)} ($s)"
                if (k == want && a !is Answer.Found) problems += "${noteName(k)} at $gain dB was not itself ($s)"
            }
        }
        println("treehouse positions: $named keys outside them named")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** Higher or lower: each round's twelve keys heard as themselves, and no key ever found on the wrong side of the pup's. */
    @Test fun higherOrLowerOnPianoSynth() {
        val problems = mutableListOf<String>()
        val rounds = (NIGHT_SKY.lessons.flatMap { it.steps }.mapNotNull { StageGames.of(it) as? StageGame.HigherLower }).single().rounds
        var found = 0
        val each = heardEach(rounds.map { it.keys }, 36..96)
        for (r in rounds) for (k in 36..96) for (gain in listOf(-14.0, -26.0)) {
            val heard = each.getValue(r.keys).getValue(k to gain)
            if (heard.isEmpty()) { problems += "$r ${noteName(k)} at $gain dB: nothing at all"; continue }
            val s = heard.first()
            if (k in r.keys && (s.by != HeardBy.MATCHER || s.midi != k)) problems += "$r: ${noteName(k)} heard as $s"
            val a = HigherLowerJudge.judge(s, r)
            val rightWay = k != r.ref && (k > r.ref) == (r.way == Way.HIGHER)
            if (a is Answer.Found && !rightWay) problems += "$r: ${noteName(k)} at $gain dB found the round ($s)"
            if (a is Answer.Found) found++
            if (k in r.keys && rightWay && a !is Answer.Found) problems += "$r: ${noteName(k)} at $gain dB, the right way, was not found ($s)"
        }
        println("higher or lower: $found finds over ${rounds.size} rounds")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The jam: every black key it listens for draws its star; a white key never does. Middle C for the beat games: only a C lights a star. */
    @Test fun theJamAndTheBeatOnPianoSynth() {
        val problems = mutableListOf<String>()
        val both = heardEach(listOf(JAM_KEYS, setOf(60))); val jams = both.getValue(JAM_KEYS); val beats = both.getValue(setOf(60))
        for (k in 21..108) for (gain in listOf(-14.0, -26.0)) {
            val jam = jams.getValue(k to gain)
            if (k in JAM_KEYS && (jam.firstOrNull()?.by != HeardBy.MATCHER || jam.first().midi != k)) problems += "jam: ${noteName(k)} heard as $jam"
            if (!isBlack(k) && jam.any { (it.by == HeardBy.MATCHER || it.by == HeardBy.NAMED) && it.midi?.let(::isBlack) == true }) problems += "jam: white ${noteName(k)} drew a star ($jam)"
            val beat = beats.getValue(k to gain)
            val c = beat.any { (it.by == HeardBy.MATCHER || it.by == HeardBy.NAMED) && it.midi?.let { m -> m % 12 == 0 } == true }
            if (k == 60 && beat.firstOrNull()?.midi != 60) problems += "beat: middle C heard as $beat"
            if (k % 12 != 0 && c) problems += "beat: ${noteName(k)} at $gain dB lit a star ($beat)"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The beat games' own sounds and the jam's band: never a key, with the stages' keys expected and strict. */
    @Test fun thePupsDrumTheClicksAndTheBandAreNeverAKey() {
        fun scaled(samples: FloatArray, gainDb: Double): FloatArray {
            val k = 10.0.pow(gainDb / 20).toFloat()
            val room = PianoSynth.render(emptyList(), 2000.0 + samples.size * 1000.0 / fs, noiseDb = -66.0)
            for (i in samples.indices) room[fs + i] += samples[i] * k
            return room
        }
        val bad = mutableListOf<String>()
        val sounds = listOf("drum" to drumSamples(), "click" to clickSamples(false), "accent" to clickSamples(true))
        for ((name, samples) in sounds) for (gain in listOf(0.0, -12.0, -24.0)) for (set in listOf(setOf(60), StageKeys.FIVE, JAM_KEYS)) {
            val heard = hear(scaled(samples, gain), set).filter { it.by == HeardBy.MATCHER || it.by == HeardBy.NAMED }
            if (heard.isNotEmpty()) bad += "$name at $gain dB with ${set.map(::noteName)}: $heard"
        }
        // The band, two bars, the mic deaf for a moment at each of its notes (as the jam does): nothing is heard.
        val beatMs = 60_000.0 / 84
        val notes = (0 until 2).flatMap { bar -> StarJam.BAND_BAR.flatMap { (b, keys) -> keys.map { SynthNote(it, 1000 + (bar * 4 + b) * beatMs, beatMs * 0.9, gainDb = -20.0) } } }
        val deaf = notes.map { it.startMs.toLong() until it.startMs.toLong() + StarJam.DEAF_MS }
        val band = PianoSynth.render(notes, 1000 + 8 * beatMs + 1500, noiseDb = -66.0)
        val heard = hear(band, JAM_KEYS, deaf).filter { it.by == HeardBy.MATCHER || it.by == HeardBy.NAMED }
        if (heard.isNotEmpty()) bad += "the band was heard: $heard"
        // A black key the child plays between the band's notes still draws its star.
        val kid = PianoSynth.render(notes + SynthNote(70, 1000 + 2.5 * beatMs, 500.0, gainDb = -14.0), 1000 + 8 * beatMs + 1500, noiseDb = -66.0)
        val played = hear(kid, JAM_KEYS, deaf).filter { it.by == HeardBy.MATCHER || it.by == HeardBy.NAMED }
        if (played.map { it.midi } != listOf(70)) bad += "the child's A sharp with the band: $played"
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    private companion object {
        val PITCH_SETS: List<Set<Int>> get() = NIGHT_SKY.lessons.flatMap { it.steps }.mapNotNull { StageGames.of(it) as? StageGame.HigherLower }
            .flatMap { it.rounds }.map { it.keys }
    }
}
