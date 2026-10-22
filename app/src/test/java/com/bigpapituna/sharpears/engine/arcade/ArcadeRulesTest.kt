package com.bigpapituna.sharpears.engine.arcade

import com.bigpapituna.sharpears.engine.isBlack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The arcade's rules: every game's rounds at every level (keys, range, length, speed), the stream and call rounds' cues
 * (the demo first, hits, slips, the stream's help, two at once, stuck beats and the let go), the stars and levels, a
 * whole round played by a quick kid, a silent kid and a kid who only hits other keys (every round ends; stars one to
 * three), the book, the unlock rule, the quest's pick and the copy rules.
 */
class ArcadeRulesTest {
    private val seeds = 1L..12L

    // ---- Plans -------------------------------------------------------------------------------------------------------

    @Test fun everyRoundListensForTwelveKeysAtMostInTheKidsRange() {
        for (g in ArcadeGame.entries) for (level in 0..g.maxLevel) for (seed in seeds) {
            val p = ArcadePlans.plan(g, level, seed)
            val where = "${g.id} level $level seed $seed"
            assertTrue("$where: ${p.keys.size} keys", p.keys.size in 1..ArcadeKeys.MAX)
            assertTrue("$where: in range ${p.keys}", p.keys.all { it in ArcadeKeys.RANGE })
            assertTrue("$where: the ear hears every target", p.targets.flatMap { it.keys }.all { it in p.keys })
            assertTrue("$where: targets to play", p.targets.isNotEmpty())
            // White keys (they have a colour and an animal), but for the moles' black keys at level three and up.
            if (!(g == ArcadeGame.MOLES && level >= 3)) assertTrue("$where: white keys only", p.keys.none(::isBlack))
            // A target's side holds its own key (a left frog's key is below middle C).
            for (t in p.targets) assertTrue("$where: ${t.keys} on ${t.side}", t.keys.all { t.takes(it) })
        }
    }

    @Test fun roundsLastOneToOneAndAHalfMinutes() {
        for (g in ArcadeGame.entries) for (level in 0..g.maxLevel) {
            val p = ArcadeGame.entries.let { ArcadePlans.plan(g, level, 3) }
            assertTrue("${g.id}: ${p.roundMs}", p.roundMs in 60_000..90_000)
            if (p is StreamPlan) assertTrue("${g.id} level $level ends by ${p.endsBy}", p.endsBy in 60_000..90_000)
        }
    }

    @Test fun streamsGetQuickerWithEachLevelAndThroughTheRound() {
        for (arr in listOf(ArcadePlans.BALLOON_GAP, ArcadePlans.BALLOON_LIFE, ArcadePlans.FEED_GAP, ArcadePlans.FEED_LIFE, ArcadePlans.MOLE_GAP, ArcadePlans.MOLE_LIFE))
            assertTrue(arr.toList().zipWithNext().all { (a, b) -> b < a })
        for (f in listOf(0.0, 0.3, 0.6)) {
            assertTrue(ArcadePlans.streamGap(2_000, f + 0.3) < ArcadePlans.streamGap(2_000, f))
            assertTrue(ArcadePlans.streamLife(8_000, f + 0.3) < ArcadePlans.streamLife(8_000, f))
        }
        // Within a round the targets come closer together.
        val t = (ArcadePlans.plan(ArcadeGame.BALLOONS, 1, 1) as StreamPlan).targets
        val gaps = t.zipWithNext { a, b -> b.atMs - a.atMs }
        assertTrue(gaps.last() < gaps.first())
        // The tower's beat quickens block by block, down to its floor, and each level is quicker.
        for (level in 0..4) {
            val beats = (0..30).map { ArcadePlans.towerBeat(level, it) }
            assertTrue(beats.zipWithNext().all { (a, b) -> b <= a })
            assertEquals(ArcadePlans.TOWER_MIN[level], beats.last())
            if (level > 0) assertTrue(ArcadePlans.towerBeat(level, 0) < ArcadePlans.towerBeat(level - 1, 0))
        }
    }

    @Test fun keysGrowWithTheLevel() {
        for (g in listOf(ArcadeGame.BALLOONS, ArcadeGame.FEED, ArcadeGame.MOLES, ArcadeGame.TOWER, ArcadeGame.GARDEN, ArcadeGame.FROGS, ArcadeGame.BRIDGE)) {
            val counts = (0..g.maxLevel).map { ArcadePlans.plan(g, it, 1).keys.size }
            assertTrue("${g.id}: $counts", counts.zipWithNext().all { (a, b) -> b >= a } && counts.last() > counts.first())
        }
        assertEquals(setOf(60, 62, 64), ArcadePlans.plan(ArcadeGame.BALLOONS, 0, 1).keys)
        // Two at once from level two: balloons tied together, moles with a friend.
        assertFalse((ArcadePlans.plan(ArcadeGame.BALLOONS, 1, 1) as StreamPlan).pairs)
        assertTrue((ArcadePlans.plan(ArcadeGame.BALLOONS, 2, 1) as StreamPlan).pairs)
        assertTrue((ArcadePlans.plan(ArcadeGame.MOLES, 2, 1) as StreamPlan).pairs)
    }

    @Test fun balloonsRiseOverTheirKeysFirstAndEachKeyComesOnceFirst() {
        val p = ArcadePlans.plan(ArcadeGame.BALLOONS, 0, 1) as StreamPlan
        assertTrue(p.targets.all { it.lane == null })
        assertEquals(listOf(60, 62, 64), p.targets.take(3).map { it.key })
        assertEquals(listOf(60, 62, 64), p.demo)
        assertTrue((ArcadePlans.plan(ArcadeGame.BALLOONS, 2, 1) as StreamPlan).targets.all { it.lane != null })
        // No key three times running.
        for (seed in seeds) (ArcadePlans.plan(ArcadeGame.FEED, 0, seed) as StreamPlan).targets.map { it.key }.windowed(3)
            .forEach { assertFalse("seed $seed: $it", it.toSet().size == 1) }
    }

    @Test fun molesNeverPeekOutOfABusyKey() {
        for (level in 0..4) for (seed in seeds) {
            val t = (ArcadePlans.plan(ArcadeGame.MOLES, level, seed) as StreamPlan).targets
            for (a in t) for (b in t) if (a.id < b.id && a.keys.intersect(b.keys.toSet()).isNotEmpty())
                assertTrue("level $level seed $seed: ${a.keys} at ${a.atMs}", b.atMs >= a.atMs + a.lifeMs)
        }
    }

    @Test fun theGardenWalksStepThenSkipThenTakeTheLeftHand() {
        assertEquals(listOf(60, 62, 64), (ArcadePlans.plan(ArcadeGame.GARDEN, 0, 1) as CallPlan).lines.first().keys)
        assertEquals(listOf(60, 62, 64, 65, 67), (ArcadePlans.plan(ArcadeGame.GARDEN, 1, 1) as CallPlan).lines.first().keys)
        assertEquals(listOf(60, 64, 67), (ArcadePlans.plan(ArcadeGame.GARDEN, 2, 1) as CallPlan).lines.first().keys)
        val left = (ArcadePlans.plan(ArcadeGame.GARDEN, 4, 1) as CallPlan).lines.first()
        assertTrue(left.targets.all { it.side == Side.LOW })
        // Shown every time at first; from level two only the first time a walk comes.
        assertTrue((ArcadePlans.plan(ArcadeGame.GARDEN, 0, 1) as CallPlan).lines.all { it.demo })
        val l2 = (ArcadePlans.plan(ArcadeGame.GARDEN, 2, 1) as CallPlan).lines
        assertEquals(ArcadePlans.WALKS[2].size, l2.count { it.demo })
    }

    @Test fun frogsCrossUpAndBackAndTwoFrogsTakeTurns() {
        assertEquals(listOf(listOf(60, 62, 64, 65, 67), listOf(67, 65, 64, 62, 60)), ArcadePlans.crossings(0))
        val two = ArcadePlans.plan(ArcadeGame.FROGS, 3, 1) as CallPlan
        val hop = two.lines.first().targets
        assertEquals(listOf(Side.LOW, Side.HIGH, Side.LOW, Side.HIGH), hop.take(4).map { it.side })
        // The left frog's pad does not take the same letter played by the right hand.
        assertFalse(hop.first().takes(60))
        assertTrue(hop.first().takes(48))
    }

    @Test fun theBridgeAlternatesHands() {
        for (level in 0..4) for (seed in seeds) {
            val p = ArcadePlans.plan(ArcadeGame.BRIDGE, level, seed) as CallPlan
            for (l in p.lines) assertEquals(l.targets.indices.map { if (it % 2 == 0) Side.LOW else Side.HIGH }, l.targets.map { it.side })
        }
    }

    @Test fun songsComeInFourNoteBitesThenPhrasesThenWhole() {
        for (s in ArcadeSongs.ALL) {
            assertEquals(s.notes, s.bites().flatten())
            assertEquals(s.notes, s.phrases().flatten())
            assertTrue("${s.id}: ${s.bites()}", s.bites().all { it.size in 2..4 })
        }
        assertEquals(listOf(listOf(60, 60, 67, 67), listOf(69, 69, 67), listOf(65, 65, 64, 64), listOf(62, 62, 60)), ArcadeSongs.of(2).bites())
        for (level in 0 until ArcadeSongs.ALL.size) {
            val f = ArcadePlans.plan(ArcadeGame.FIREWORKS, level, 1) as CallPlan
            assertEquals(ArcadeSongs.of(level).notes, f.lines.last().keys)
            assertFalse("the finale has no help", f.lines.last().demo)
            assertTrue("only the whole song goes without help", f.lines.filter { !it.demo }.all { it.keys == ArcadeSongs.of(level).notes })
            assertEquals(ArcadeSongs.of(level).bites(), f.lines.take(ArcadeSongs.of(level).bites().size).map { it.keys })
            val p = ArcadePlans.plan(ArcadeGame.PAINTER, level, 1) as CallPlan
            val n = ArcadeSongs.of(level).phrases().size
            assertEquals(List(n) { true }, p.lines.take(n).map { it.demo })
            assertTrue(p.lines.drop(n).none { it.demo })
            assertEquals(ArcadeSongs.of(level).notes, p.lines.last().keys)
            assertTrue(f.song && p.song)
            assertEquals(f.lines.size, f.needLines)
        }
    }

    // ---- The stream round --------------------------------------------------------------------------------------------

    private fun plan(vararg ts: Target, pairs: Boolean = false) = StreamPlan(ArcadeGame.BALLOONS, 0, 60_000, ts.toList(), listOf(60, 62), setOf(60, 62, 64))

    @Test fun aStreamShowsItsKeysFirstAndIsDeafMeanwhile() {
        val r = StreamRound(plan(Target(0, listOf(60), atMs = 0, lifeMs = 5_000)))
        val demo = r.start(0).single() as Cue.Demo
        assertEquals(listOf(60, 62), demo.keys)
        assertTrue(r.key(60, 100).isEmpty())
        assertTrue(r.tick(500).isEmpty())
        val end = CallRound.FIRST_LEAD_MS + 2 * 420 + CallRound.DEMO_TAIL_MS
        val cues = r.tick(end)
        assertTrue(cues.first() is Cue.Listen && cues[1] is Cue.Spawned)
    }

    @Test fun aKeyHitsTheOldestTargetItMatchesInAnyOctaveAndOthersAreSlips() {
        val r = StreamRound(plan(Target(0, listOf(60), atMs = 0, lifeMs = 5_000), Target(1, listOf(60), atMs = 100, lifeMs = 5_000),
            Target(2, listOf(64), atMs = 200, lifeMs = 5_000)))
        r.start(0); r.tick(10_000); r.tick(10_300)
        assertEquals(3, r.live.size)
        val slip = r.key(62, 10_400).single() as Cue.Slip
        assertEquals(60, slip.near)
        val hit = r.key(72, 10_500).single() as Cue.Hit
        assertEquals(0, hit.target.id)
        assertEquals(1, hit.streak)
        assertEquals(2, (r.key(52, 10_600).first() as Cue.Hit).target.id)
        assertEquals(1, r.slips)
    }

    @Test fun twoGoneByInARowLightTheNextAndAHitTurnsItOff() {
        val ts = (0 until 5).map { Target(it, listOf(60), atMs = it * 1_000L, lifeMs = 1_500) }
        val r = StreamRound(plan(*ts.toTypedArray()))
        r.start(0)
        val cues = (0..60).flatMap { r.tick(5_000L + it * 100) }
        val spawned = cues.filterIsInstance<Cue.Spawned>()
        assertFalse(spawned[0].helped); assertFalse(spawned[1].helped)
        assertTrue("lit after two went by", spawned.last().helped)
        assertTrue(r.helping)
        // Played: the help goes off.
        val r2 = StreamRound(plan(*ts.toTypedArray()))
        r2.start(0)
        (0..40).forEach { r2.tick(5_000L + it * 100) }
        assertTrue(r2.helping)
        r2.key(60, 9_100)
        assertFalse(r2.helping)
    }

    @Test fun twoAtOnceNeedsBothKeysClose() {
        val r = StreamRound(plan(Target(0, listOf(60, 64), atMs = 0, lifeMs = 8_000)))
        r.start(0); r.tick(5_000)
        assertTrue(r.key(60, 5_100).single() is Cue.Half)
        // Too late for its partner: a half again.
        assertTrue(r.key(64, 5_900).single() is Cue.Half)
        assertTrue(r.key(60, 6_000).first() is Cue.Hit)
        assertTrue(r.over)
        assertEquals(1, r.result(6_000).hits)
    }

    @Test fun aStreamEndsWhenItsLastTargetIsGone() {
        val r = StreamRound(plan(Target(0, listOf(60), atMs = 0, lifeMs = 1_000)))
        r.start(0)
        val cues = (0..100).flatMap { r.tick(it * 100L) }
        val done = cues.filterIsInstance<Cue.Finished>().single()
        assertEquals(1, done.result.offered)
        assertEquals(1, done.result.stars)
        assertTrue(r.over)
    }

    // ---- The call round ----------------------------------------------------------------------------------------------

    private fun call(vararg lines: List<Int>, song: Boolean = false, roundMs: Long = 60_000) = CallRound(CallPlan(ArcadeGame.GARDEN, 0, roundMs,
        lines.mapIndexed { p, ks -> Line(ks.mapIndexed { i, k -> Target(p * 10 + i, listOf(k), phrase = p, index = i) }, demo = true) },
        lines.flatMap { it }.toSet(), song = song, needLines = lines.size))

    private fun listenAfterDemo(r: CallRound, from: Long): Long {
        var t = from
        while (!r.listening) { t += 50; r.tick(t) }
        return t
    }

    @Test fun aCallIsShownFirstThenHeardNoteByNote() {
        val r = call(listOf(60, 62, 64))
        val d = r.start(0).single() as Cue.Demo
        assertEquals(listOf(60, 62, 64), d.keys)
        assertTrue(d.first)
        assertTrue("deaf during the demo", r.key(60, 100).isEmpty())
        val t = listenAfterDemo(r, 0)
        assertEquals(60, r.current!!.key)
        assertTrue(r.key(48, t + 100).single() is Cue.Hit)          // any octave
        assertTrue(r.key(62, t + 200).single() is Cue.Hit)
        val last = r.key(64, t + 300)
        assertTrue(last[0] is Cue.Hit)
        assertEquals(Cue.LineDone(0, clean = true), last[1])
        val end = (1..40).flatMap { r.tick(t + 300 + it * 50) }
        assertTrue(end.last() is Cue.Finished)
        assertEquals(3, (end.last() as Cue.Finished).result.stars)
    }

    @Test fun threeStuckBeatsLetTheNoteGoAndNothingDeadEnds() {
        val r = call(listOf(60, 62))
        r.start(0)
        val t = listenAfterDemo(r, 0)
        // A slip: the key is lit (beat one).
        val slip = r.key(65, t + 10)
        assertTrue(slip[0] is Cue.Slip)
        assertEquals(Cue.Nudge(r.current!!, 1), slip[1])
        // A quiet beat: the pup plays it too (beat two).
        val two = r.tick(t + 10 + CallPlan.DEFAULT_BEAT_MS)
        assertEquals(2, (two.single() as Cue.Nudge).beat)
        // Another: let go, and the line moves on.
        val three = r.tick(t + 10 + 2 * CallPlan.DEFAULT_BEAT_MS)
        assertTrue(three.single() is Cue.LetGo)
        assertEquals(62, r.current!!.key)
        assertEquals(1, r.helped)
        r.key(62, t + 7_000)
        assertEquals(1, r.linesDone); assertEquals(0, r.cleanLines)
    }

    @Test fun aSongsRestIsPlayedByThePupWhenTimeIsUp() {
        val r = call(listOf(60, 62), listOf(64, 65, 67), song = true, roundMs = 20_000)
        r.start(0)
        val t = listenAfterDemo(r, 0)
        r.key(60, t + 100)
        val cues = r.tick(20_000)
        assertEquals(Cue.Rest(listOf(62, 64, 65, 67)), cues[0])
        val result = (cues[1] as Cue.Finished).result
        assertEquals(1, result.hits); assertEquals(4, result.helped)
        assertEquals(1, result.stars)
    }

    // ---- Whole rounds, three kinds of kid ----------------------------------------------------------------------------

    private enum class Kid { QUICK, SILENT, OTHER_KEYS }

    /** Plays a whole round with a [kid] who answers [reactMs] after a target is ready; the result and the clock at the end. */
    private fun play(r: ArcadeRound, kid: Kid, reactMs: Long = 450): Pair<RoundResult, Long> {
        // A key of no letter the round asks for (the moles' twelve leave none: never asked of them).
        val other = ArcadeKeys.RANGE.firstOrNull { k -> r.keys.none { Math.floorMod(it - k, 12) == 0 } } ?: 59
        val due = sortedMapOf<Long, MutableList<Int>>()
        fun press(at: Long, k: Int) { due.getOrPut(at) { mutableListOf() } += k }
        var now = 0L
        fun handle(cues: List<Cue>) {
            for (c in cues) when (c) {
                is Cue.Spawned -> when (kid) {
                    Kid.QUICK -> c.target.keys.forEach { press(now + reactMs, it) }
                    Kid.OTHER_KEYS -> press(now + reactMs, other)
                    Kid.SILENT -> {}
                }
                is Cue.Listen, is Cue.Hit, is Cue.LetGo, is Cue.Nudge -> if (r is CallRound && r.listening) r.current?.let { t ->
                    when (kid) {
                        Kid.QUICK -> press(now + reactMs, t.key)
                        Kid.OTHER_KEYS -> if (c !is Cue.Nudge) press(now + reactMs, other)
                        Kid.SILENT -> {}
                    }
                }
                else -> {}
            }
        }
        handle(r.start(0))
        var result: RoundResult? = null
        while (result == null) {
            now += 20
            val cues = r.tick(now).toMutableList()
            due.headMap(now + 1).values.flatten().forEach { k -> cues += r.key(k, now) }
            due.headMap(now + 1).clear()
            handle(cues)
            result = cues.filterIsInstance<Cue.Finished>().firstOrNull()?.result
            assertTrue("${r.game.id} level ${r.level} never ends", now < 200_000)
        }
        return result to now
    }

    @Test fun aQuickKidEarnsEveryStarInEveryGameAtEveryLevel() {
        for (g in ArcadeGame.entries) for (level in 0..g.maxLevel) {
            val (res, ms) = play(ArcadePlans.round(g, level, 5), Kid.QUICK)
            assertEquals("${g.id} level $level: $res", 3, res.stars)
            assertTrue("${g.id} level $level lasted $ms", ms <= 95_000)
            assertEquals(0, res.slips)
        }
    }

    @Test fun aSilentKidAndAKidOnOtherKeysReachTheEndWithAStar() {
        for (kid in listOf(Kid.SILENT, Kid.OTHER_KEYS)) for (g in ArcadeGame.entries) for (level in listOf(0, g.maxLevel)) {
            if (kid == Kid.OTHER_KEYS && ArcadePlans.plan(g, level, 7).keys.map { it % 12 }.toSet().size == 12) continue
            val (res, ms) = play(ArcadePlans.round(g, level, 7), kid)
            assertEquals("${g.id} level $level, $kid", 1, res.stars)
            assertEquals(0, res.hits)
            assertTrue("${g.id} level $level, $kid: lasted $ms", ms <= 95_000)
            assertTrue("${g.id}: something went by", res.helped > 0)
        }
    }

    @Test fun aSongRoundTakesAboutAMinuteAtAKidsPace() {
        for (g in listOf(ArcadeGame.FIREWORKS, ArcadeGame.PAINTER)) for (level in 0..g.maxLevel) {
            val (res, ms) = play(ArcadePlans.round(g, level, 1), Kid.QUICK, reactMs = 900)
            assertTrue("${g.id} level $level: $ms ms", ms in 50_000..90_000)
            assertEquals(3, res.stars)
        }
    }

    // ---- Stars, levels, the book --------------------------------------------------------------------------------------

    private fun result(game: ArcadeGame, hits: Int, helped: Int, lines: Int = 0, level: Int = 0, need: Int = 0) =
        RoundResult(game, level, hits + helped, hits, helped, 0, lines, lines, 3, 60_000, need)

    @Test fun starsComeFromAPureRule() {
        assertEquals(3, result(ArcadeGame.BALLOONS, 15, 5).stars)        // three quarters
        assertEquals(2, result(ArcadeGame.BALLOONS, 14, 6).stars)
        assertEquals(2, result(ArcadeGame.BALLOONS, 9, 11).stars)        // 45%
        assertEquals(1, result(ArcadeGame.BALLOONS, 8, 12).stars)
        assertEquals(1, result(ArcadeGame.BALLOONS, 0, 0).stars)         // never none
        assertEquals(3, result(ArcadeGame.TOWER, 10, 4, lines = 10).stars)
        assertEquals(2, result(ArcadeGame.TOWER, 5, 0, lines = 5).stars)
        assertEquals(1, result(ArcadeGame.TOWER, 4, 0, lines = 4).stars)
        assertEquals(2, result(ArcadeGame.TOWER, 12, 0, lines = 12, level = 4).stars)   // the clouds are higher at level four
        assertEquals(3, result(ArcadeGame.GARDEN, 17, 3, lines = 4, need = 4).stars)
        assertEquals(2, result(ArcadeGame.GARDEN, 20, 0, lines = 3, need = 4).stars)   // not enough flowers
        assertEquals(2, result(ArcadeGame.GARDEN, 11, 9, lines = 5, need = 4).stars)
        assertEquals(1, result(ArcadeGame.GARDEN, 10, 10, lines = 5, need = 4).stars)
    }

    @Test fun theLevelMovesUpOnThreeStarsAndDownWhenItWasTooMuch() {
        assertEquals(1, ArcadeLevels.next(ArcadeGame.BALLOONS, 0, result(ArcadeGame.BALLOONS, 20, 0)))
        assertEquals(4, ArcadeLevels.next(ArcadeGame.BALLOONS, 4, result(ArcadeGame.BALLOONS, 20, 0)))
        assertEquals(2, ArcadeLevels.next(ArcadeGame.BALLOONS, 2, result(ArcadeGame.BALLOONS, 10, 10)))
        assertEquals(1, ArcadeLevels.next(ArcadeGame.BALLOONS, 2, result(ArcadeGame.BALLOONS, 2, 18)))
        assertEquals(0, ArcadeLevels.next(ArcadeGame.BALLOONS, 0, result(ArcadeGame.BALLOONS, 0, 18)))
        assertEquals(3, ArcadeLevels.next(ArcadeGame.FIREWORKS, 3, result(ArcadeGame.FIREWORKS, 20, 0, lines = 20, level = 3, need = 3)))
    }

    @Test fun theBookKeepsBestsAndGivesTheStickerOnTheFirstThreeStars() {
        var s = ArcadeState()
        val (s1, e1) = ArcadeBook.record(s, result(ArcadeGame.BALLOONS, 10, 10)); s = s1
        assertEquals(2, e1.stars); assertFalse(e1.firstThree); assertTrue(e1.newBest)
        val (s2, e2) = ArcadeBook.record(s, result(ArcadeGame.BALLOONS, 20, 0)); s = s2
        assertTrue(e2.firstThree); assertEquals(1, e2.levelAfter)
        val (s3, e3) = ArcadeBook.record(s, result(ArcadeGame.BALLOONS, 20, 0, level = 1)); s = s3
        assertFalse("once", e3.firstThree); assertFalse(e3.newBest)
        assertEquals(GameRecord(best = 3, level = 2, rounds = 3, sticker = true, last = 3), s.of(ArcadeGame.BALLOONS))
        // A painted song is kept from two stars.
        val (s4, e4) = ArcadeBook.record(s, result(ArcadeGame.PAINTER, 15, 5, lines = 7, level = 1, need = 7)); s = s4
        assertTrue(e4.paintedNew); assertEquals(setOf("mary"), s.painted)
        // Round trip, and nonsense reads as an empty arcade.
        val back = ArcadeState.decode(s.copy(base = 12, seen = 5).encode())
        assertEquals(s.copy(base = 12, seen = 5), back)
        assertEquals(ArcadeState(), ArcadeState.decode("{oops"))
        assertEquals(ArcadeState(), ArcadeState.decode(null))
    }

    @Test fun threeGamesOpenAtOnceThenOneMoreForEveryFewSessionsOfSparkles() {
        val first = ArcadeUnlocks.opened(ArcadeState(), sparkles = 200)
        assertEquals(200, first.base)
        assertEquals(first, ArcadeUnlocks.opened(first, 900))            // counted from the first open only
        assertEquals(3, ArcadeUnlocks.open(first, 200))
        assertEquals(3, ArcadeUnlocks.open(first, 239))
        assertEquals(4, ArcadeUnlocks.open(first, 240))
        assertEquals(0.5f, ArcadeUnlocks.towardNext(first, 260)!!, 0.001f)
        assertEquals(ArcadeGame.entries.size, ArcadeUnlocks.open(first, 5_000))
        assertNull(ArcadeUnlocks.towardNext(first, 5_000))
        assertTrue(ArcadeUnlocks.isOpen(ArcadeGame.FEED, first, 200))
        assertFalse(ArcadeUnlocks.isOpen(ArcadeGame.TOWER, first, 200))
        // The first three are varied: key memory, sequences, reaction.
        assertEquals(listOf(Skill.KEY_MEMORY, Skill.SEQUENCES, Skill.REACTION), ArcadeGame.entries.take(3).map { it.skill })
    }

    @Test fun theQuestAsksForAnOpenGameEveryOtherDay() {
        val open = ArcadeGame.entries.take(3)
        val days = (0L..11L).map { LocalDate.of(2026, 10, 20).plusDays(it) }
        val picks = days.map { ArcadeQuest.pick(it, open) }
        assertEquals(6, picks.count { it != null })
        assertEquals(open.toSet(), picks.filterNotNull().toSet())
        assertNull(ArcadeQuest.pick(days.first { it.toEpochDay() % 2 == 0L }, emptyList()))
        assertEquals("Play Balloon pop", ArcadeQuest.title(ArcadeGame.BALLOONS))
    }

    // ---- Words ----------------------------------------------------------------------------------------------------------

    @Test fun everyArcadeLineKeepsTheKidCopyRules() {
        val banned = Regex("[—–·#&/%]| - |\\b(wrong|mistakes?|errors?|fail\\w*|percent|lose|lost)\\b", RegexOption.IGNORE_CASE)
        val quest = ArcadeGame.entries.map(ArcadeQuest::title).toSet()
        val titles = ArcadeGame.entries.map { it.title }.toSet()
        for (l in ArcadeLines.all()) {
            assertFalse("\"$l\"", banned.containsMatchIn(l))
            assertFalse("no numbers: \"$l\"", l.any { it.isDigit() })
            assertTrue("single spaces: \"$l\"", "  " !in l)
            assertTrue("starts a sentence: \"$l\"", l.first().isUpperCase())
            if (l !in titles && l !in quest) assertTrue("ends a sentence: \"$l\"", l.last() in ".!?")
            assertTrue("short enough for the bubble: \"$l\"", l.length <= 80)
        }
        assertTrue(ArcadeLines.spoken().containsAll(ArcadeGame.entries.map { ArcadeLines.intro(it, true) }))
        assertNotNull(ArcadeLines.lineDone(ArcadeGame.GARDEN, 7))
    }
}
