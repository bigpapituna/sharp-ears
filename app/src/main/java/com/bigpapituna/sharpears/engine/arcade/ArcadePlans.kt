package com.bigpapituna.sharpears.engine.arcade

import com.bigpapituna.sharpears.engine.noteNumber
import kotlin.random.Random

/*
 * Each game's rounds by level (0 is where every kid starts). What grows with the level: the keys (three, then the C
 * position, then the octave, then both hands), the speed (streams come closer together and go by sooner, the tower's
 * beat quickens), and what is asked at once (two balloons or two moles together, two frogs taking turns). A round's
 * keys never pass [ArcadeKeys.MAX] and stay in [ArcadeKeys.RANGE]; ArcadeRulesTest checks every game and level.
 */
object ArcadePlans {
    /** A new round of [game] at [level], drawn with [seed]. */
    fun round(game: ArcadeGame, level: Int, seed: Long): ArcadeRound = when (val p = plan(game, level, seed)) {
        is StreamPlan -> StreamRound(p)
        is CallPlan -> CallRound(p)
    }

    /** The plan of a round: a [StreamPlan] or a [CallPlan]. */
    fun plan(game: ArcadeGame, level: Int, seed: Long): RoundPlan {
        val l = level.coerceIn(0, game.maxLevel)
        val rnd = Random(seed)
        return when (game) {
            ArcadeGame.BALLOONS -> balloons(l, rnd)
            ArcadeGame.FEED -> feed(l, rnd)
            ArcadeGame.MOLES -> moles(l, rnd)
            ArcadeGame.GARDEN -> garden(l)
            ArcadeGame.TOWER -> tower(l, rnd)
            ArcadeGame.FROGS -> frogs(l)
            ArcadeGame.BRIDGE -> bridge(l, rnd)
            ArcadeGame.FIREWORKS -> fireworks(l)
            ArcadeGame.PAINTER -> painter(l)
        }
    }

    // ---- Streams ---------------------------------------------------------------------------------------------------

    /** The gap between two targets and how long each stays, [frac] of the way through the round: both shrink a little as it goes. */
    fun streamGap(base: Long, frac: Double): Long = (base * (1 - 0.25 * frac.coerceIn(0.0, 1.0))).toLong()
    fun streamLife(base: Long, frac: Double): Long = (base * (1 - 0.15 * frac.coerceIn(0.0, 1.0))).toLong()

    val BALLOON_GAP = longArrayOf(2_600, 2_200, 2_000, 1_700, 1_450)
    val BALLOON_LIFE = longArrayOf(9_000, 8_000, 7_200, 6_400, 5_600)
    val FEED_GAP = longArrayOf(2_800, 2_400, 2_000, 1_700, 1_400)
    val FEED_LIFE = longArrayOf(6_500, 5_600, 4_800, 4_200, 3_600)
    val MOLE_GAP = longArrayOf(2_000, 1_700, 1_600, 1_400, 1_250)
    val MOLE_LIFE = longArrayOf(4_200, 3_600, 3_400, 3_000, 2_700)

    fun balloonKeys(level: Int): List<Int> = when (level) {
        0 -> ArcadeKeys.CDE; 1, 2 -> ArcadeKeys.FIVE; 3 -> ArcadeKeys.OCTAVE; else -> ArcadeKeys.LOW_OCTAVE
    }

    fun feedKeys(level: Int): List<Int> = when (level) {
        0 -> ArcadeKeys.CDE; 1 -> ArcadeKeys.FIVE; 2 -> ArcadeKeys.OCTAVE; else -> ArcadeKeys.LOW_OCTAVE
    }

    fun moleKeys(level: Int): List<Int> = when (level) {
        0 -> ArcadeKeys.FIVE; 1, 2 -> ArcadeKeys.OCTAVE; else -> ArcadeKeys.CHROMATIC
    }

    /**
     * Balloon pop: balloons rise from the bottom, each a key's colour (its letter for a reader); at levels 0 and 1 each
     * rises over its own key (the scaffold), from level 2 anywhere across the sky. From level 2 every fifth is a pair tied
     * together: two keys at once pop both.
     */
    private fun balloons(level: Int, rnd: Random): StreamPlan {
        val keys = balloonKeys(level)
        val pairs = level == 2 || level >= 4
        return stream(ArcadeGame.BALLOONS, level, 70_000, keys, BALLOON_GAP[level], BALLOON_LIFE[level], rnd,
            pairEvery = if (pairs) 5 else 0, overKeys = level <= 1)
    }

    /** Feed the pup: treats fly past with a key on them, at a height of their own; quicker than balloons. */
    private fun feed(level: Int, rnd: Random) =
        stream(ArcadeGame.FEED, level, 65_000, feedKeys(level), FEED_GAP[level], FEED_LIFE[level], rnd, pairEvery = 0, overKeys = false)

    /**
     * Boop the moles: a mole peeks out of a key of the big keyboard picture and ducks back; never two on one key. From
     * level 2 every fourth comes with a friend (a chord: both keys at once); level 3 adds the black keys.
     */
    private fun moles(level: Int, rnd: Random): StreamPlan {
        val keys = moleKeys(level)
        val pairs = level == 2 || level == 4
        return stream(ArcadeGame.MOLES, level, 65_000, keys, MOLE_GAP[level], MOLE_LIFE[level], rnd, pairEvery = if (pairs) 4 else 0,
            overKeys = true, oneAKey = true)
    }

    /** The two keys of a pair from [keys]: white keys a third apart (C and E, D and F...). */
    private fun pairFrom(keys: List<Int>, rnd: Random, free: (Int) -> Boolean): List<Int>? {
        val whites = keys.filter { !com.bigpapituna.sharpears.engine.isBlack(it) }
        val options = whites.indices.mapNotNull { i -> whites.getOrNull(i + 2)?.let { listOf(whites[i], it) } }.filter { it.all(free) }
        return options.takeIf { it.isNotEmpty() }?.random(rnd)
    }

    private fun stream(game: ArcadeGame, level: Int, roundMs: Long, keys: List<Int>, gap: Long, life: Long, rnd: Random,
                       pairEvery: Int, overKeys: Boolean, oneAKey: Boolean = false): StreamPlan {
        val out = mutableListOf<Target>()
        val busyUntil = HashMap<Int, Long>()
        var t = 0L
        var i = 0
        val recent = ArrayDeque<Int>()
        while (t <= roundMs - StreamPlan.TAIL_MS) {
            val frac = t.toDouble() / roundMs
            val l = streamLife(life, frac)
            fun free(k: Int) = !oneAKey || (busyUntil[k] ?: Long.MIN_VALUE) <= t
            val pair = pairEvery > 0 && i >= keys.size && i % pairEvery == pairEvery - 1
            val chosen: List<Int>? = when {
                pair -> pairFrom(keys, rnd, ::free)
                // Each key once first, low to high: the round's keys in the order the demo showed them.
                i < keys.size && free(keys[i]) -> listOf(keys[i])
                else -> keys.filter { k -> free(k) && !(recent.size >= 2 && recent.all { it == k }) }.takeIf { it.isNotEmpty() }?.let { listOf(it.random(rnd)) }
            }
            if (chosen != null) {
                val lifeMs = if (chosen.size > 1) (l * 1.3).toLong() else l
                val lane = if (overKeys) null else 0.1f + 0.8f * rnd.nextFloat()
                out += Target(out.size, chosen, atMs = t, lifeMs = lifeMs, lane = lane)
                chosen.forEach { busyUntil[it] = t + lifeMs + 300 }
                recent.addLast(chosen.first()); if (recent.size > 2) recent.removeFirst()
                i++
            }
            t += streamGap(gap, frac)
        }
        return StreamPlan(game, level, roundMs, out, demo = keys, keys = keys.toSet())
    }

    // ---- Calls -----------------------------------------------------------------------------------------------------

    private fun keysOf(line: String): List<Int> = line.trim().split(' ').map(::noteNumber)

    private fun lines(lines: List<Pair<List<Int>, Boolean>>, side: (Int) -> Side = { Side.ANY }): List<Line> {
        var id = 0
        return lines.mapIndexed { p, (keys, demo) -> Line(keys.mapIndexed { i, k -> Target(id++, listOf(k), side(k), phrase = p, index = i) }, demo) }
    }

    /** The two hands' side for a key in a two-hand game: below middle C is the left hand's. */
    private fun handSide(k: Int) = if (k < ArcadeKeys.MIDDLE_C) Side.LOW else Side.HIGH

    /** Grow the garden's walks by level: steps up and down, the C position, skips, a mix, then the left hand too. */
    val WALKS: List<List<String>> = listOf(
        listOf("C4 D4 E4", "E4 D4 C4", "C4 D4 E4 D4 C4", "D4 E4 D4 C4"),
        listOf("C4 D4 E4 F4 G4", "G4 F4 E4 D4 C4", "C4 D4 E4 F4 G4 F4 E4 D4 C4", "E4 F4 G4 F4 E4"),
        listOf("C4 E4 G4", "G4 E4 C4", "C4 E4 G4 E4 C4", "D4 F4 A4 F4 D4"),
        listOf("C4 D4 E4 C4", "E4 F4 G4 E4", "G4 E4 D4 C4", "C4 E4 D4 F4 E4 G4", "A4 G4 F4 E4 D4 C4"),
        listOf("C3 D3 E3 F3 G3", "C4 E4 G4", "G3 F3 E3 D3 C3", "G4 E4 C4", "C3 E3 G3 E3 C3"),
    )

    /**
     * Grow the garden: walk after walk (cycling the level's), each grows a flower a step a key and opens it at its last;
     * shown first every time at levels 0 and 1, the first time each walk comes from level 2.
     */
    private fun garden(level: Int): CallPlan {
        val walks = WALKS[level].map(::keysOf)
        val seen = HashSet<Int>()
        val ls = (0 until 40).map { n -> val w = n % walks.size; walks[w] to (level <= 1 || seen.add(w)) }
        return CallPlan(ArcadeGame.GARDEN, level, 75_000, lines(ls, side = { if (level >= 4) handSide(it) else Side.ANY }),
            walks.flatten().toSet(), gapMs = 1_300, needLines = ArcadeStars.minLines(ArcadeGame.GARDEN))
    }

    fun towerKeys(level: Int): List<Int> = when (level) {
        0 -> ArcadeKeys.CDE; 1 -> ArcadeKeys.FIVE; 2 -> ArcadeKeys.OCTAVE; 3 -> ArcadeKeys.LOW_OCTAVE; else -> ArcadeKeys.LEFT_FIVE + ArcadeKeys.FIVE
    }

    val TOWER_START = longArrayOf(3_400, 3_000, 2_700, 2_400, 2_100)
    val TOWER_MIN = longArrayOf(1_600, 1_300, 1_150, 1_000, 900)
    const val TOWER_STEP = 120L

    /** The tower's beat once [blocks] are up: quicker with every block, down to the level's floor. */
    fun towerBeat(level: Int, blocks: Int): Long = maxOf(TOWER_MIN[level], TOWER_START[level] - TOWER_STEP * blocks)

    /** Build the tower: one key a call, never the same twice running; the first call is shown. */
    private fun tower(level: Int, rnd: Random): CallPlan {
        val keys = towerKeys(level)
        var last = -1
        val calls = (0 until 80).map { i ->
            val k = keys.filter { it != last }.random(rnd).also { last = it }
            listOf(k) to (i == 0)
        }
        return CallPlan(ArcadeGame.TOWER, level, 70_000, lines(calls, side = { if (level >= 4) handSide(it) else Side.ANY }), keys.toSet(),
            gapMs = 150, beat = { blocks -> towerBeat(level, blocks) })
    }

    /** Frog hop's crossings by level: up the pads and back; from level 2 two frogs (the left hand's below middle C). */
    fun crossings(level: Int): List<List<Int>> {
        val r5 = ArcadeKeys.FIVE
        val l5 = ArcadeKeys.LEFT_FIVE
        fun zip(a: List<Int>, b: List<Int>) = a.zip(b).flatMap { (x, y) -> listOf(x, y) }
        return when (level) {
            0 -> listOf(r5, r5.reversed())
            1 -> listOf(ArcadeKeys.OCTAVE, ArcadeKeys.OCTAVE.reversed())
            2 -> listOf(r5, l5, r5.reversed(), l5.reversed())
            3 -> listOf(zip(l5, r5), zip(l5.reversed(), r5.reversed()))
            else -> listOf(zip(l5.reversed(), r5), zip(l5, r5.reversed()))
        }
    }

    /** Frog hop: crossing after crossing; each kind is shown the first time it comes. */
    private fun frogs(level: Int): CallPlan {
        val cs = crossings(level)
        val seen = HashSet<Int>()
        val ls = (0 until 30).map { n -> val c = n % cs.size; cs[c] to seen.add(c) }
        return CallPlan(ArcadeGame.FROGS, level, 75_000, lines(ls, side = { if (level >= 2) handSide(it) else Side.ANY }), cs.flatten().toSet(),
            gapMs = 900, needLines = ArcadeStars.minLines(ArcadeGame.FROGS))
    }

    /** Rainbow bridge's planks for one bridge: left and right hands take turns (left below middle C). */
    fun planks(level: Int, n: Int, rnd: Random): List<Int> {
        val l5 = ArcadeKeys.LEFT_FIVE
        val r5 = ArcadeKeys.FIVE
        fun zip(a: List<Int>, b: List<Int>) = a.zip(b).flatMap { (x, y) -> listOf(x, y) }
        return when (level) {
            0 -> if (n % 2 == 0) zip(l5.take(3), r5.take(3)) else zip(l5.take(3).reversed(), r5.take(3).reversed())
            1 -> if (n % 2 == 0) zip(l5, r5) else zip(l5.reversed(), r5.reversed())
            2 -> zip(List(4) { l5.random(rnd) }, List(4) { r5.random(rnd) })
            3 -> zip(List(5) { l5.random(rnd) }, List(5) { r5.random(rnd) })
            else -> if (n % 2 == 0) zip(l5.reversed(), r5) else zip(l5, r5.reversed())
        }
    }

    /** Rainbow bridge: bridge after bridge, shown first at levels 0 and 1 and the first time from level 2. */
    private fun bridge(level: Int, rnd: Random): CallPlan {
        val ls = (0 until 16).map { n -> planks(level, n, rnd) to (level <= 1 || n == 0) }
        return CallPlan(ArcadeGame.BRIDGE, level, 75_000, lines(ls, side = ::handSide), ls.flatMap { it.first }.toSet(),
            gapMs = 1_600, needLines = ArcadeStars.minLines(ArcadeGame.BRIDGE))
    }

    /**
     * Fireworks: the song of [level] in four-note bites, each shown by the pup first; then its phrases (bites joined),
     * shown first; then the whole song with no help, the finale. A slower beat than the other calls: a song is new.
     */
    private fun fireworks(level: Int): CallPlan {
        val song = ArcadeSongs.of(level)
        val bites = song.bites().map { it to true }
        val phrases = song.phrases().filter { p -> song.bites().none { it == p } }.map { it to true }
        val ls = encore(bites + phrases + listOf(song.notes to false), song)
        return CallPlan(ArcadeGame.FIREWORKS, level, 90_000, lines(ls), song.notes.toSet(), song = true, gapMs = 900,
            needLines = ls.size, beat = { SONG_BEAT_MS })
    }

    /**
     * Song painter: the song of [level] phrase by phrase, shown first (the picture's edges); the phrases again from
     * memory; then the whole song (its middle). One patch of the picture a note.
     */
    private fun painter(level: Int): CallPlan {
        val song = ArcadeSongs.of(level)
        val ls = encore(song.phrases().map { it to true } + song.phrases().map { it to false } + listOf(song.notes to false), song)
        return CallPlan(ArcadeGame.PAINTER, level, 90_000, lines(ls), song.notes.toSet(), song = true, gapMs = 900,
            needLines = ls.size, beat = { SONG_BEAT_MS })
    }

    const val SONG_BEAT_MS = 3_200L
    /** A short song's round plays it whole once more at the end (the encore), so every song round is about a minute. */
    const val ENCORE_UNDER = 40

    private fun encore(ls: List<Pair<List<Int>, Boolean>>, song: ArcadeSong) =
        if (ls.sumOf { it.first.size } < ENCORE_UNDER) ls + listOf(song.notes to false) else ls
}
