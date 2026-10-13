package com.bigpapituna.sharpears.engine.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetronomeToolTest {
    @Test fun ticksFallOnTheBeatAndAccentTheDownbeat() {
        val t = ticksIn(MetronomeConfig(bpm = 120), 4100.0)
        assertEquals(9, t.size)                                   // 0, 500, ... 4000
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0), t.take(5).map { it.timeMs })
        assertEquals(listOf(TickLevel.STRONG, TickLevel.WEAK, TickLevel.MEDIUM, TickLevel.WEAK, TickLevel.STRONG), t.take(5).map { it.level })
        assertEquals(listOf(0, 1, 2, 3, 0), t.take(5).map { it.beat })
        assertEquals(listOf(0, 0, 0, 0, 1), t.take(5).map { it.bar })
    }

    @Test fun accentPatternsPerTimeSignature() {
        fun levels(sig: TimeSig) = ticksIn(MetronomeConfig(bpm = 60, timeSig = sig), sig.beats * 1000.0 + 1).take(sig.beats).map { it.level.name[0] }.joinToString("")
        assertEquals("SWMW", levels(TimeSig.FOUR_FOUR))
        assertEquals("SWW", levels(TimeSig.THREE_FOUR))
        assertEquals("SWWMWW", levels(TimeSig.SIX_EIGHT))
        assertEquals("SWWMW", levels(TimeSig.FIVE_FOUR))
        assertEquals("SWMWMWW", levels(TimeSig.SEVEN_EIGHT))
        // Every bar has exactly one downbeat.
        for (sig in TimeSig.entries) {
            val t = ticksIn(MetronomeConfig(bpm = 200, timeSig = sig), 20_000.0)
            val bars = t.groupBy { it.bar }.filter { it.value.size == sig.beats }
            assertTrue(bars.isNotEmpty())
            bars.values.forEach { b -> assertEquals(1, b.count { it.level == TickLevel.STRONG }) }
        }
    }

    @Test fun subdivisionsSplitTheBeatEvenly() {
        for (sub in Subdivision.entries) {
            val t = ticksIn(MetronomeConfig(bpm = 60, subdivision = sub), 1999.9)
            assertEquals(2 * sub.perBeat, t.size)
            val gap = 1000.0 / sub.perBeat
            t.forEachIndexed { i, tick ->
                assertEquals(i * gap, tick.timeMs, 1e-6)
                assertEquals(i % sub.perBeat, tick.sub)
                assertEquals(i % sub.perBeat == 0, tick.level != TickLevel.SUB)
            }
        }
    }

    @Test fun triplets() {
        val t = ticksIn(MetronomeConfig(bpm = 90, subdivision = Subdivision.TRIPLETS), 700.0)
        assertEquals(listOf(0.0, 222.2222, 444.4444, 666.6667), t.map { Math.round(it.timeMs * 10000) / 10000.0 })
    }

    @Test fun noDriftOverAnHour() {
        val s = TickSchedule(MetronomeConfig(bpm = 137, subdivision = Subdivision.SIXTEENTHS))
        var last: Tick? = null
        repeat(137 * 60 * 4) { last = s.next() }
        // The tick at index n sits at n * (60000 / 137 / 4), to within float rounding.
        assertEquals((137 * 60 * 4 - 1) * (60_000.0 / 137 / 4), last!!.timeMs, 1e-3)
    }

    @Test fun trainerSpeedsUpEveryFewBars() {
        val cfg = MetronomeConfig(bpm = 100, trainer = Trainer(stepBpm = 10, everyBars = 2, maxBpm = 120))
        val t = ticksIn(cfg, 60_000.0)
        val byBar = t.groupBy { it.bar }.mapValues { it.value.first().bpm }
        assertEquals(listOf(100, 100, 110, 110, 120, 120, 120), (0..6).map { byBar.getValue(it) })
        // A bar at 100 lasts 2400 ms, then 2 bars at 110.
        assertEquals(4800.0, t.first { it.bar == 2 }.timeMs, 1e-6)
        assertEquals(4800.0 + 2 * 4 * 60_000.0 / 110, t.first { it.bar == 4 }.timeMs, 1e-6)
    }

    @Test fun trainerNeverPassesTheLimits() {
        val t = ticksIn(MetronomeConfig(bpm = 290, trainer = Trainer(stepBpm = 25, everyBars = 1)), 30_000.0)
        assertEquals(MAX_BPM, t.maxOf { it.bpm })
        assertEquals(290, t.first().bpm)
        assertEquals(MIN_BPM, clampBpm(5))
        assertEquals(MAX_BPM, clampBpm(999))
    }

    @Test fun randomMuteSilencesWholeBarsButNotTheFirstOrTooMany() {
        val cfg = MetronomeConfig(bpm = 240, mute = RandomMute(chance = 0.5, maxInARow = 1), seed = 7)
        val t = ticksIn(cfg, 240_000.0)
        val bars = t.groupBy { it.bar }.toSortedMap()
        assertTrue(bars.values.all { b -> b.all { it.muted == b.first().muted } })
        assertTrue(!bars.getValue(0).first().muted)
        val muted = bars.map { it.value.first().muted }
        assertTrue(muted.count { it } in 50..110)
        assertTrue(muted.zipWithNext().none { (a, b) -> a && b })
        // The same seed gives the same bars; a different seed does not.
        assertEquals(muted, ticksIn(cfg, 240_000.0).groupBy { it.bar }.toSortedMap().map { it.value.first().muted })
        assertTrue(muted != ticksIn(cfg.copy(seed = 8), 240_000.0).groupBy { it.bar }.toSortedMap().map { it.value.first().muted })
        // Muting off: nothing is muted.
        assertTrue(ticksIn(MetronomeConfig(bpm = 240), 60_000.0).none { it.muted })
    }

    @Test fun muteLongerRuns() {
        val cfg = MetronomeConfig(bpm = 300, mute = RandomMute(chance = 1.0, maxInARow = 2), seed = 1)
        val flags = ticksIn(cfg, 60_000.0).groupBy { it.bar }.toSortedMap().map { it.value.first().muted }
        assertEquals(listOf(false, true, true, false, true, true, false), flags.take(7))
    }

    @Test fun changingTheTempoMidwayTakesEffectOnTheNextBeat() {
        val s = TickSchedule(MetronomeConfig(bpm = 60))
        assertEquals(0.0, s.next().timeMs, 1e-9)
        s.setBpm(120)
        assertEquals(1000.0, s.next().timeMs, 1e-9)                // the beat already scheduled keeps its slot
        assertEquals(1500.0, s.next().timeMs, 1e-9)
        s.setBpm(5)
        assertEquals(MIN_BPM, s.next().bpm)
        assertEquals("Allegro", tempoName(132))
        assertEquals("Largo", tempoName(50))
    }

    @Test fun tapTempoUsesTheMedianGap() {
        val tap = TapTempo()
        assertNull(tap.tap(0))
        assertEquals(120, tap.tap(500))
        assertEquals(120, tap.tap(1000))
        assertEquals(120, tap.tap(1500))
        // One stray late tap barely moves a median of the last gaps.
        assertEquals(120, tap.tap(2000))
        assertEquals(120, tap.tap(2800))                         // gaps 500 500 500 500 800: median 500
        assertEquals(120, tap.tap(3300))
    }

    @Test fun tapTempoResetsAfterAPause() {
        val tap = TapTempo()
        tap.tap(0); tap.tap(400); tap.tap(800)
        assertNull(tap.tap(10_000))                                // too long a pause: a new count
        assertEquals(60, tap.tap(11_000))
    }

    @Test fun tapTempoClampsToTheLimits() {
        val slow = TapTempo(); slow.tap(0)
        assertEquals(MIN_BPM, slow.tap(2400))                      // 25 bpm clamps to 30
        val fast = TapTempo(); fast.tap(0)
        assertEquals(MAX_BPM, fast.tap(100))                       // 600 bpm clamps to 300
    }
}
