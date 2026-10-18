package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.PlayedNote
import com.bigpapituna.sharpears.engine.RunRecording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.abs

class ConcertCardTest {
    @Test fun everythingSitsInsideTheCardWithoutOverlapping() {
        for (title in listOf("Ode to Joy", "Minuet in G", "Twinkle Twinkle Little Star and friends", "")) for (stars in 0..3) {
            val l = ConcertCardLayout.of(title, stars)
            val parts = listOf(l.pup, l.title, l.sticker, l.footer) + l.stars
            parts.forEach { assertTrue("$title: $it", it.inside(l.card)) }
            for (i in parts.indices) for (j in i + 1 until parts.size) assertFalse("$title: ${parts[i]} and ${parts[j]}", parts[i].overlaps(parts[j]))
            assertEquals(stars, l.filled)
        }
    }

    @Test fun theCardIsPortraitAndThePupCentred() {
        val l = ConcertCardLayout.of("Ode to Joy", 2)
        assertEquals(1080f, l.card.width); assertEquals(1350f, l.card.height)
        assertEquals(l.card.centerX, l.pup.centerX, 0.5f)
        assertTrue(l.pup.bottom < l.title.top && l.title.bottom < l.stars[0].top && l.stars[0].bottom < l.footer.top)
    }

    @Test fun starsAreTheSameSizeEvenlySpacedAndTheRowIsCentred() {
        val l = ConcertCardLayout.of("Ode to Joy", 3)
        assertEquals(3, l.stars.size)
        l.stars.forEach { assertEquals(l.stars[0].width, it.width, 0.01f); assertEquals(l.stars[0].top, it.top, 0.01f) }
        val gaps = l.stars.zipWithNext { a, b -> b.left - a.right }
        assertEquals(gaps[0], gaps[1], 0.01f)
        assertTrue(l.sticker.left > l.stars.last().right)
        assertEquals(l.stars[0].centerY, l.sticker.centerY, 0.5f)                    // the sticker sits level with the stars
        val row = Box(l.stars.first().left, l.sticker.top, l.sticker.right, l.sticker.bottom)
        assertEquals(l.card.centerX, row.centerX, 0.5f)
        // Stars are clamped to what the card has room for.
        assertEquals(3, ConcertCardLayout.of("x", 9).filled)
        assertEquals(0, ConcertCardLayout.of("x", -1).filled)
    }

    @Test fun longTitlesWrapToTwoLinesAndShrinkToFit() {
        assertEquals(listOf("Ode to Joy"), ConcertCardLayout.wrap("Ode to Joy", 18))
        assertEquals(listOf("Twinkle Twinkle", "Little Star"), ConcertCardLayout.wrap("Twinkle Twinkle Little Star", 18))
        val long = ConcertCardLayout.wrap("The Entertainer by Scott Joplin in a very long arrangement", 18)
        assertEquals(2, long.size)
        assertEquals(listOf("The Entertainer by", "Scott Joplin in a..."), long)     // cut at a word
        val short = ConcertCardLayout.of("Ode", 1)
        assertEquals(short.titleSize, ConcertCardLayout.of("Twinkle Twinkle Little Star", 1).titleSize)   // two lines fit at full size
        val two = ConcertCardLayout.of("The Entertainer by Scott Joplin", 1)
        assertTrue(two.titleSize < short.titleSize)
        for (l in listOf(short, two, ConcertCardLayout.of("Supercalifragilisticexpialidocious", 1))) {
            val widest = l.titleLines.maxOf { it.length } * 0.55f * l.titleSize
            assertTrue("${l.titleLines} fits", widest <= l.title.width + 0.5f)
            assertTrue(l.titleLines.size * 1.15f * l.titleSize <= l.title.height + 0.5f)
        }
        assertEquals(listOf("My concert"), ConcertCardLayout.of("   ", 0).titleLines)
    }

    @Test fun aStarHasTenPointsInsideItsBox() {
        val b = Box(100f, 100f, 200f, 200f)
        val pts = ConcertCardLayout.starPoints(b)
        assertEquals(10, pts.size)
        assertEquals(150f, pts[0].first, 0.01f)                                       // the top point first
        pts.forEach { (x, y) -> assertTrue(x in 99f..201f && y in 99f..206f) }
    }

    @Test fun filesAreNamedForThePlayerAndTheSong() {
        assertEquals("Mia plays Minuet in G", ConcertFiles.title("Mia", "Minuet in G"))
        assertEquals("I play a song", ConcertFiles.title(" ", ""))
        assertEquals("Mia plays Minuet in G.wav" to "Mia plays Minuet in G.png", ConcertFiles.names("Mia plays Minuet in G"))
        assertEquals("Theo plays AC DC", ConcertFiles.stem("Theo plays AC/DC"))           // no path separators
        assertEquals("a b", ConcertFiles.stem("a:*?\"<>|\\b"))
        assertEquals("My concert", ConcertFiles.stem("///"))
        assertEquals("My concert", ConcertFiles.stem("..."))
        val long = ConcertFiles.stem("Mia plays " + "la ".repeat(40))
        assertTrue(long.length <= 60 && !long.endsWith(" "))
        assertEquals("Ma plays Für Elise", ConcertFiles.stem("Ma plays Für Elise"))     // letters of any language stay
        assertEquals("Sharp Ears, October 18, 2026", ConcertFiles.footer(LocalDate.of(2026, 10, 18)))
    }

    @Test fun theShareTypeFitsTheFiles() {
        assertEquals("audio/wav", ConcertFiles.mimeType(listOf("a.wav")))
        assertEquals("image/png", ConcertFiles.mimeType(listOf("a.png", "b.PNG")))
        assertEquals("image/*", ConcertFiles.mimeType(listOf("a.png", "b.jpg")))
        assertEquals("*/*", ConcertFiles.mimeType(listOf("a.wav", "a.png")))
        assertEquals("application/zip", ConcertFiles.mimeType(listOf("stuck-moments-20261018-1200.zip")))
    }

    // ---- the recording ----

    private val rate = 8_000
    /** A fake sample per key: a constant level, so where and how long a note sounds is easy to read. */
    private fun flat(len: Int) = FloatArray(len) { 0.5f }

    @Test fun anEmptyRunMakesNoSound() {
        assertEquals(0, ConcertMix.render(RunRecording("s", emptyList()), listOf(60), rate) { flat(100) }.size)
        assertEquals(0, ConcertMix.render(RunRecording("s", listOf(PlayedNote(60, 0, 100, 80))), emptyList(), rate) { flat(100) }.size)
    }

    @Test fun notesLandAtTheirRealTimeAfterTheLeadIn() {
        val run = RunRecording("s", listOf(PlayedNote(60, 5_000, 2_000, 100), PlayedNote(64, 6_000, 2_000, 100)), speed = 0.5)
        val out = ConcertMix.render(run, listOf(61, 65), rate) { flat(rate * 10) }
        fun at(ms: Long) = out[(ms * rate / 1000).toInt()]
        // Half speed: the second note sounds 2 s after the first in real time; the first is placed at the lead-in.
        assertEquals(0f, at(ConcertMix.LEAD_MS - 10), 1e-6f)
        assertTrue(at(ConcertMix.LEAD_MS + 10) > 0f)
        val one = at(ConcertMix.LEAD_MS + 1000)
        val two = at(ConcertMix.LEAD_MS + 2000 + 10)
        assertEquals(2f, two / one, 0.02f)                                             // both notes sounding
        // Ends with the tail after the last release (4 s held in real time).
        assertEquals((ConcertMix.LEAD_MS + 2000 + 4000 + ConcertMix.TAIL_MS) * rate / 1000, out.size.toLong())
        assertEquals(ConcertMix.PEAK, out.maxOf { abs(it) }, 1e-4f)                   // normalised
    }

    @Test fun theNearestSampleIsPitchedToTheKeyAndReleasedKeysFade() {
        // A ramp sample: reading it faster (a higher key) climbs faster.
        val ramp = FloatArray(rate) { it / rate.toFloat() }
        val samples = mutableListOf<Int>()
        val run = RunRecording("s", listOf(PlayedNote(62, 0, 300, 127)))
        val out = ConcertMix.render(run, listOf(61, 65, 69), rate) { k -> samples += k; ramp }
        assertEquals(listOf(61), samples)                                             // 61 is nearest to 62
        val start = (ConcertMix.LEAD_MS * rate / 1000).toInt()
        val ratio = Math.pow(2.0, 1 / 12.0)
        val k = 200
        val expected = (k * ratio / rate).toFloat()
        val scale = out[start + k] / expected
        assertEquals(out[start + 100] / ((100 * ratio / rate).toFloat()), scale, 0.01f)
        // 300 ms held, then the damper: a third of a second later it is all but silent.
        val release = start + 300 * rate / 1000
        assertTrue(abs(out[release + rate / 3]) < abs(out[release - 1]) * 0.1f)
    }

    @Test fun aRunLongerThanTenMinutesIsCut() {
        val run = RunRecording("s", listOf(PlayedNote(60, 0, 100, 90), PlayedNote(60, 20 * 60_000L, 100, 90)))
        val out = ConcertMix.render(run, listOf(60), 1_000) { FloatArray(10) { 0.5f } }
        assertEquals(ConcertMix.MAX_MS * 1_000 / 1000, out.size.toLong())
    }
}
