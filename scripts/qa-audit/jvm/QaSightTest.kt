package com.bigpapituna.sharpears.qa

import com.bigpapituna.sharpears.engine.*
import org.junit.Test
import java.io.File

// THROWAWAY (QA audit): every sight-reading level over many seeds.
class QaSightTest {
    @Test fun walk() {
        val out = StringBuilder(); var findings = 0
        fun bad(s: String) { findings++; out.appendLine("FINDING $s") }
        for (level in 1..2) for (seed in 1L..300L) {
            val tag = "sight L$level seed $seed"
            try {
                val p = sightPiece(level, seed)
                val right = parseTokens(p.exercise.right); val total = right.sumOf { it.beats }
                if (Math.abs(total - 16.0) > 1e-6) bad("$tag right hand ${total} beats, not 16")
                val song = p.exercise.toSong("s", "s")
                val lo = song.notes.minOf { it.midi }; val hi = song.notes.maxOf { it.midi }
                if (lo < 36 || hi > 84) bad("$tag range $lo..$hi")
                if (level == 1 && p.exercise.left.isNotBlank()) bad("$tag level 1 has a left hand")
                if (level == 2) { val l = parseTokens(p.exercise.left); if (l.size != 4) bad("$tag bass ${l.size} notes") }
                val lastKey = right.last().keys.first() % 12
                if (lastKey != p.key) bad("$tag ends on pitch class $lastKey not the tonic ${p.key}")
                val firstBeat = right.first().beats
            } catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
        }
        for (level in 3..8) for (seed in 1L..100L) {
            val tag = "staff L$level seed $seed"
            try {
                val cards = SightStaff.cards(level, seed, 60)
                var prev: StaffCard? = null
                for (c in cards) {
                    if (level < 8) {
                        if (c.keys.size != 1) bad("$tag card with ${c.keys.size} keys")
                        if (prev != null && prev.keys == c.keys) bad("$tag the same key twice running: ${c.code}")
                    } else {
                        val beats = c.notes.sumOf { it.beats.toDouble() }
                        if (Math.abs(beats - 4.0) > 1e-6) bad("$tag bar of $beats beats: ${c.timedCode}")
                        if (c.keys.isEmpty()) bad("$tag bar with no notes")
                    }
                    val k = c.keys.firstOrNull() ?: continue
                    val (lo, hi) = when (level) { 3 -> 60 to 67; 4 -> 60 to 81; 5 -> 40 to 60; 6 -> 40 to 81; 7 -> 39 to 80; else -> 60 to 67 }
                    if (k < lo || k > hi) bad("$tag key $k outside $lo..$hi (${c.code})")
                    prev = c
                }
            } catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
        }
        out.appendLine("findings: $findings")
        File("build/qa").mkdirs(); File("build/qa/sight-walk.txt").writeText(out.toString())
    }
}
