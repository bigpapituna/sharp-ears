package com.bigpapituna.sharpears.engine

import kotlin.math.abs

/**
 * Suggested fingering (1 = thumb .. 5 = little finger) for every note of a song, per hand.
 *
 * Model: a hand position h (in semitones) is where the thumb would rest; finger f in its natural place
 * plays h + OFFSET[f] (the C-D-E-F-G shape). Each onset group of one hand is an "event"; every way of
 * giving its notes ascending fingers is a candidate. Dynamic programming picks the sequence with the lowest
 * cost: stretching away from the natural shape, moving the hand, the same finger on a new key, crossings
 * other than thumb-under / finger-over-thumb, and thumbs or little fingers on black keys.
 * The left hand is the right hand mirrored (pitches negated), so the thumb is its highest finger.
 * ponytail: a hand-position heuristic, not a full ergonomic model; good for single lines and plain chords,
 * rough for fast ornaments. Upgrade: Parncutt-style per-pair costs if the numbers look odd in real pieces.
 */
object Fingering {
    private val OFFSET = doubleArrayOf(0.0, 0.0, 2.0, 3.5, 5.5, 7.0)          // index = finger; 3.5 sits between minor and major thirds
    // Widest comfortable span (semitones) between finger a < b in one hand shape.
    private val MAX_SPAN = arrayOf(
        intArrayOf(), intArrayOf(0, 0, 7, 8, 10, 12), intArrayOf(0, 0, 0, 4, 6, 8),
        intArrayOf(0, 0, 0, 0, 4, 6), intArrayOf(0, 0, 0, 0, 0, 4),
    )

    private class Cand(val fingers: IntArray, val h: Double, val own: Double)

    /** Finger per note; notes in chords wider than five notes past the fifth are left out. */
    fun of(song: Song): Map<Note, Int> {
        val out = HashMap<Note, Int>(song.notes.size * 2)
        for (hand in Hand.entries) {
            val notes = song.notes.filter { it.hand == hand }
            if (notes.isEmpty()) continue
            val mirror = hand == Hand.LEFT
            val events = groupOnsets(notes).map { g -> g.notes.sortedBy { if (mirror) -it.midi else it.midi }.take(5) }
            val fingers = solve(events.map { ev -> ev.map { if (mirror) -it.midi else it.midi } }, events.map { ev -> ev.map { isBlack(it.midi) } })
            events.forEachIndexed { i, ev -> ev.forEachIndexed { j, n -> out[n] = fingers[i][j] } }
        }
        // A lesson can give the standard fingering (scales that start on a black key, thumb crossings by the book), for
        // either hand: what its pictures and words name is then what the notes, the ghost hand and the hints show.
        song.fingering?.let { fixed -> song.notes.filter { it.hand == Hand.RIGHT }.zip(fixed).forEach { (n, f) -> out[n] = f } }
        song.leftFingering?.let { fixed -> song.notes.filter { it.hand == Hand.LEFT }.zip(fixed).forEach { (n, f) -> out[n] = f } }
        return out
    }

    /** Fingers for each event's pitches (already sorted ascending in the right-hand frame). */
    fun solve(pitches: List<List<Int>>, black: List<List<Boolean>>): List<IntArray> {
        if (pitches.isEmpty()) return emptyList()
        val cands = pitches.mapIndexed { i, ps -> candidates(ps, black[i]) }
        val cost = Array(cands.size) { DoubleArray(cands[it].size) }
        val back = Array(cands.size) { IntArray(cands[it].size) }
        cands[0].forEachIndexed { k, c -> cost[0][k] = c.own }
        for (i in 1 until cands.size) {
            for ((k, c) in cands[i].withIndex()) {
                var best = Double.MAX_VALUE; var arg = 0
                for ((j, p) in cands[i - 1].withIndex()) {
                    val v = cost[i - 1][j] + move(pitches[i - 1], p, pitches[i], c)
                    if (v < best) { best = v; arg = j }
                }
                cost[i][k] = best + c.own; back[i][k] = arg
            }
        }
        val result = arrayOfNulls<IntArray>(cands.size)
        var k = cost.last().indices.minBy { cost.last()[it] }
        for (i in cands.indices.reversed()) { result[i] = cands[i][k].fingers; k = back[i][k] }
        return result.map { it!! }
    }

    private fun candidates(ps: List<Int>, black: List<Boolean>): List<Cand> {
        val out = mutableListOf<Cand>()
        fun rec(i: Int, from: Int, acc: IntArray) {
            if (i == ps.size) { out += cand(ps, black, acc.copyOf()); return }
            for (f in from..5 - (ps.size - 1 - i)) {
                if (i > 0) {
                    val span = ps[i] - ps[i - 1]
                    if (span <= 0 || span > MAX_SPAN[acc[i - 1]][f]) continue
                }
                acc[i] = f; rec(i + 1, f + 1, acc)
            }
        }
        rec(0, 1, IntArray(ps.size))
        // A chord too wide for any shape still gets fingers, just expensive ones.
        if (out.isEmpty()) out += cand(ps, black, IntArray(ps.size) { listOf(1, 2, 3, 4, 5)[it] }).let { Cand(it.fingers, it.h, it.own + 10) }
        return out
    }

    private fun cand(ps: List<Int>, black: List<Boolean>, f: IntArray): Cand {
        val hs = ps.indices.map { ps[it] - OFFSET[f[it]] }
        val h = hs.average()
        var own = hs.sumOf { abs(it - h) } * 0.5                            // stretch away from the natural shape
        ps.indices.forEach { i -> if (black[i]) own += when (f[i]) { 1 -> 2.0; 5 -> 1.0; else -> 0.0 } }
        return Cand(f, h, own)
    }

    private fun move(pa: List<Int>, a: Cand, pb: List<Int>, b: Cand): Double {
        val dh = abs(b.h - a.h)
        var c = 0.4 * dh + if (dh > 1.5) 1.0 else 0.0                      // moving the hand; a real shift costs extra
        if (pa.size == 1 && pb.size == 1) {
            val fa = a.fingers[0]; val fb = b.fingers[0]; val step = pb[0] - pa[0]
            when {
                step == 0 -> if (fa != fb) c += 0.5                            // repeated key: same finger is easiest
                fa == fb -> c += 6.0                                           // same finger on a new key
                step > 0 && fb < fa -> c += if (fb == 1 && step <= 5) 1.0 else 8.0   // thumb under
                step < 0 && fb > fa -> c += if (fa == 1 && fb in 2..4 && -step <= 5) 1.0 else 8.0  // finger over thumb
            }
        }
        return c
    }
}
