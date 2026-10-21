package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.ui.brief.FakeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExportLayoutTest {
    /** Every character is half its text size wide: a font with no surprises. */
    private val metrics = TextMetrics { text, kind -> text.length * kind.size * 0.5f }
    private val zone = ZoneId.of("America/Toronto")
    private val now = 1_792_000_000_000L
    private fun brief(f: Findings) = FakeWriter(IdTitles, Brief.Writer.AI) { now }.write(f, "Mia")
    private fun doc(full: Boolean, period: Period = Period.WEEK): List<ExportBlock> {
        val f = BriefSamples.kid(period, now, zone)
        return ExportDoc.of(brief(f), f, "Mia", IdTitles, full, now, zone)
    }
    private fun texts(blocks: List<ExportBlock>) = blocks.filterIsInstance<ExportBlock.Text>().map { it.text }

    // ---- wrapping ----

    @Test fun wrapBreaksAtWordsAndNeverExceedsTheWidth() {
        val lines = ExportLayout.wrap("the quick brown fox jumps over the lazy dog", 200f, TextKind.BODY, metrics)    // 15.5 per char: 12 chars a line
        assertTrue(lines.size > 3)
        for (l in lines) assertTrue("'$l'", metrics.width(l, TextKind.BODY) <= 200f)
        assertEquals("the quick brown fox jumps over the lazy dog", lines.joinToString(" "))
    }

    @Test fun aWordWiderThanTheLineIsBrokenNotLost() {
        val lines = ExportLayout.wrap("Supercalifragilisticexpialidocious ok", 150f, TextKind.BODY, metrics)
        assertEquals("Supercalifragilisticexpialidocious ok".replace(" ", ""), lines.joinToString("").replace(" ", ""))
        for (l in lines) assertTrue(metrics.width(l, TextKind.BODY) <= 150f)
    }

    @Test fun wrapKeepsParagraphBreaksAndNeverReturnsNothing() {
        assertEquals(listOf("a", "b"), ExportLayout.wrap("a\nb", 500f, TextKind.BODY, metrics))
        assertEquals(listOf(""), ExportLayout.wrap("", 500f, TextKind.BODY, metrics))
    }

    // ---- the tall card ----

    @Test fun theColumnStacksEverythingWithoutOverlapAndTheCardIsClamped() {
        val (placed, h) = ExportLayout.column(doc(full = true), metrics)
        placed.zipWithNext { a, b -> assertTrue("overlap at ${b.y}", b.y >= a.y + a.height) }
        assertEquals(placed.last().y + placed.last().height, h)
        assertEquals(ExportLayout.MIN_CARD_PX, ExportLayout.cardHeight(10))
        assertEquals(ExportLayout.MAX_CARD_PX, ExportLayout.cardHeight(100_000))
        assertEquals(1000 + 2 * ExportLayout.MARGIN, ExportLayout.cardHeight(1000))
    }

    @Test fun aCappedCardDropsWholeLinesNotHalfOfOne() {
        val (placed, _) = ExportLayout.column(doc(full = true), metrics)
        val kept = ExportLayout.cardItems(placed, 1500)
        assertTrue(kept.isNotEmpty() && kept.size < placed.size)
        assertEquals(placed.take(kept.size), kept)
        assertTrue(kept.all { it.y + it.height <= 1500 - ExportLayout.MARGIN })
    }

    @Test fun theShortReadFitsOneCardWithRoomToSpare() {
        val (_, h) = ExportLayout.column(doc(full = false), metrics)
        assertTrue("short card is $h units", ExportLayout.cardHeight(h) < ExportLayout.MAX_CARD_PX)
    }

    // ---- the PDF pages ----

    @Test fun everyAtomIsOnExactlyOnePageInOrderAndNoPageOverflows() {
        val blocks = doc(full = true)
        val (placed, _) = ExportLayout.column(blocks, metrics)
        val pages = ExportLayout.paginate(blocks, metrics)
        assertTrue(pages.size > 1)
        assertEquals(placed.size, pages.sumOf { it.items.size })
        assertEquals(placed.map { it.block }, pages.flatMap { it.items }.map { it.block })
        for (p in pages) {
            assertTrue("page used ${p.used}", p.used <= ExportLayout.PDF_CONTENT_H)
            p.items.zipWithNext { a, b -> assertTrue(b.y >= a.y + a.height) }
            assertTrue(p.items.first().y == 0)
            assertTrue(p.items.last().y + p.items.last().height <= ExportLayout.PDF_CONTENT_H)
        }
    }

    @Test fun aHeadingIsNeverLeftAloneAtTheBottomOfAPage() {
        val blocks = doc(full = true)
        for (p in ExportLayout.paginate(blocks, metrics)) {
            val last = p.items.last().block
            assertFalse("a heading ends the page: $last", last is ExportBlock.Text && last.kind == TextKind.SECTION)
        }
    }

    @Test fun aShortDocumentIsOneOrTwoPagesAndAnEmptyOneStillMakesAPage() {
        assertTrue(ExportLayout.paginate(doc(full = false), metrics).size <= 2)
        assertEquals(1, ExportLayout.paginate(emptyList(), metrics).size)
    }

    @Test fun theLetterPageMathsAddsUp() {
        assertEquals(ExportLayout.PDF_PAGE_H - 2 * ExportLayout.PDF_MARGIN, (ExportLayout.PDF_CONTENT_H * ExportLayout.PDF_SCALE).toInt())
        assertEquals(ExportLayout.PDF_PAGE_W - 2 * ExportLayout.PDF_MARGIN, (ExportLayout.WIDTH * ExportLayout.PDF_SCALE).toInt())
        assertEquals(ExportLayout.WIDTH + 2 * ExportLayout.MARGIN, ExportLayout.CARD_PX)
    }

    // ---- the document ----

    @Test fun theShortDocumentIsTheOneMinuteReadAndTheFullOneHasTheNumbers() {
        val short = texts(doc(false)); val full = texts(doc(true))
        assertTrue("Mia's weekly brief" in short)
        assertTrue(short.none { it == "Lessons" || it == "Skills over four weeks" })
        for (s in listOf("The week", "Lessons", "Songs", "Skills over four weeks", "Toys", "Time of day", "The room and the hints", "Since last time")) assertTrue(s, s in full)
        assertTrue(doc(true).any { it is ExportBlock.Strip } && doc(true).any { it is ExportBlock.Trends } && doc(true).count { it is ExportBlock.Bars } == 2)
        assertTrue(full.size > short.size)
    }

    @Test fun everyRecommendationIsThereWithItsButtonAndNothingIsEmpty() {
        val f = BriefSamples.kid(Period.WEEK, now, zone)
        val b = brief(f)
        val t = texts(ExportDoc.of(b, f, "Mia", IdTitles, false, now, zone))
        for (r in b.recommendations) { assertTrue(r.text, r.text in t); assertTrue(t.any { it == "In the app: " + ActionCopy.of(r.candidate, IdTitles, "Mia").button }) }
        assertTrue(texts(doc(true)).none { it.isBlank() })
    }

    @Test fun theDocumentFollowsTheCopyRules() {
        for (p in Period.entries) for (full in listOf(false, true)) for (t in texts(doc(full, p))) {
            assertFalse("no dashes: $t", Regex("[—–]| - ").containsMatchIn(t))
            assertFalse("no double spaces: $t", "  " in t)
        }
    }

    @Test fun grownUpsGetTheirOwnTitles() {
        val f = BriefSamples.adult(Period.WEEK, now, zone)
        val t = texts(ExportDoc.of(brief(f), f, "Roy", IdTitles, false, now, zone))
        assertTrue("Your week" in t)
        assertTrue(t.last().contains("your playing"))
    }

    @Test fun fileNamesAreSafeAndSayWhatTheyAre() {
        val d = LocalDate.of(2026, 10, 21)
        assertEquals("Mia-weekly-brief-2026-10-21.png" to "Mia-weekly-brief-2026-10-21.pdf", ExportLayout.fileNames("Mia", Period.WEEK, d))
        assertEquals("Anne-marie-session-note-2026-10-21.png", ExportLayout.fileNames("  anne/marie ", Period.SESSION, d).first)
        assertEquals("Player-daily-brief-2026-10-21.pdf", ExportLayout.fileNames("éè", Period.DAY, d).second)
    }
}
