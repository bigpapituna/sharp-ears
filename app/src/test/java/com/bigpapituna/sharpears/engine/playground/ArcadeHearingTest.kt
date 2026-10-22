package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher
import com.bigpapituna.sharpears.engine.PianoSynth
import com.bigpapituna.sharpears.engine.SynthNote
import com.bigpapituna.sharpears.engine.arcade.ArcadeGame
import com.bigpapituna.sharpears.engine.arcade.ArcadeKeys
import com.bigpapituna.sharpears.engine.arcade.ArcadePlans
import com.bigpapituna.sharpears.engine.arcade.StreamPlan
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.noteName
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.7: the arcade hears the way the stages do (the note matcher with strict keys, through a StageEar). On PianoSynth,
 * for every key set a round of any game at any level listens for: each of its keys is heard as itself; a white key of
 * the kid's range outside the set is never taken for a key of another letter in it; and the two keys of every
 * two-at-once target, struck together, are both heard by their letters (the ear in chord mode, as the round asks; A4
 * with C5 in a set holding C4 comes back as A4 and C4, which the round takes as the pair).
 */
class ArcadeHearingTest {
    private val fs = 48_000

    private fun hear(audio: FloatArray, keys: Set<Int>, chords: Boolean): List<StageSound> {
        val m = NoteMatcher(fs).apply { strictKeys = true; expected = keys; groups = keys.map { setOf(it) } }
        val ear = StageEar(chords = chords)
        val out = ArrayList<StageSound>()
        var i = 0
        val buf = FloatArray(512)
        while (i < audio.size) {
            val n = minOf(512, audio.size - i)
            System.arraycopy(audio, i, buf, 0, n)
            val now = (i + n) * 1000L / fs
            for (e in m.process(buf, n)) when (e) {
                is MicEvent.Strike -> if (e.inferredFrom == null) out += ear.strike(e.midi, e.riseDb, now)
                is MicEvent.Wrong -> out += ear.wrong(e.midi, e.riseDb, now)
            }
            out += ear.poll(now)
            i += n
        }
        out += ear.poll(Long.MAX_VALUE / 2)
        return out.filter { it.final }
    }

    private fun render(vararg keys: Int) = PianoSynth.render(keys.map { SynthNote(it, 1000.0, 700.0, gainDb = -18.0) }, 2600.0, noiseDb = -66.0)

    /**
     * Every key set the arcade listens for, with whether its round wants two keys at once; level 0's three keys are rolled
     * per round (2.8), so every three it can roll is here too.
     */
    private val sets: Map<Set<Int>, Boolean> = (ArcadeGame.entries.flatMap { g -> (0..g.maxLevel).map { l -> ArcadePlans.plan(g, l, 1) } } +
        ArcadePlans.THREE_KEYS.flatMap { g -> (1L..60L).map { ArcadePlans.plan(g, 0, it) } })
        .groupBy { it.keys }.mapValues { (_, ps) -> ps.any { it is StreamPlan && it.pairs } }

    @Test fun everyRolledThreeIsListenedFor() {
        assertTrue(ArcadeKeys.OCTAVE.windowed(3).all { it.toSet() in sets })
    }

    @Test fun everyKeyOfEverySetIsHeardAsItself() {
        val cases = sets.keys.flatMap { s -> s.map { k -> s to k } }
        val problems = cases.zip(parallelMap(cases) { (s, k) -> hear(render(k), s, chords = sets.getValue(s)) }).mapNotNull { (c, heard) ->
            val (s, k) = c
            val first = heard.firstOrNull { it.midi != null }
            if (first?.by == HeardBy.MATCHER && first.midi == k) null else "${noteName(k)} in ${s.sorted().map(::noteName)}: $heard"
        }
        assertTrue("${problems.size} of ${cases.size}:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun aKeyOutsideTheSetIsNeverTakenForAnotherLetterInIt() {
        val cases = sets.keys.flatMap { s -> ArcadeKeys.RANGE.filter { !isBlack(it) && it !in s }.map { k -> s to k } }
        val problems = cases.zip(parallelMap(cases) { (s, k) -> hear(render(k), s, chords = sets.getValue(s)) }).flatMap { (c, heard) ->
            val (s, k) = c
            heard.filter { it.by == HeardBy.MATCHER && it.midi != null && Math.floorMod(it.midi!! - k, 12) != 0 }
                .map { "${noteName(k)} heard as ${noteName(it.midi!!)} in ${s.sorted().map(::noteName)}" }
        }
        assertTrue("${problems.size} of ${cases.size}:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun twoKeysAtOnceAreBothHeard() {
        val pairs = ArcadeGame.entries.flatMap { g -> (0..g.maxLevel).map { l -> ArcadePlans.plan(g, l, 1) } }.filterIsInstance<StreamPlan>()
            .flatMap { p -> p.targets.filter { it.pair }.map { p.keys to it.keys } }.distinct()
        assertTrue(pairs.isNotEmpty())
        val problems = pairs.zip(parallelMap(pairs) { (s, ks) -> hear(render(*ks.toIntArray()), s, chords = true) }).mapNotNull { (c, heard) ->
            val (s, ks) = c
            // The round takes a key by its letter (a chord's C5 heard as the C4 it listens for still finishes the pair).
            val got = heard.filter { it.by == HeardBy.MATCHER }.mapNotNull { it.midi }.toSet()
            if (got.map { it % 12 }.containsAll(ks.map { it % 12 })) null else "${ks.map(::noteName)} in ${s.sorted().map(::noteName)}: heard ${got.map(::noteName)}"
        }
        assertTrue("${problems.size} of ${pairs.size}:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
