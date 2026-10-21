package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.playground.StageClipWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Audit (docs/AUDIT-ENGINE.md): numbers the app formats read the same on every phone. String.format follows the
 * phone's language: a German phone wrote "+0,95 s" into stuck-moment reports and "-24,8" into the matcher's traces
 * (the stall review's "rise (-?[\d.]+) dB" then found nothing), and an Egyptian one wrote the sight-reading time as
 * "٢:٠٥" between English words. The app speaks English; its numbers are ASCII everywhere.
 */
class LocaleFormatTest {
    private fun <T> inLocale(tag: String, body: () -> T): T {
        val before = Locale.getDefault()
        return try { Locale.setDefault(Locale.forLanguageTag(tag)); body() } finally { Locale.setDefault(before) }
    }

    private val locales = listOf("de-DE", "fr-CA", "ar-EG", "fa-IR", "hi-IN-u-nu-deva")

    @Test fun reportsAndTimesAreTheSameInEveryLanguage() {
        for (tag in locales) inLocale(tag) {
            val w = StallWatch().apply { start(1_000, listOf("C4"), "song=twinkle") }
            w.heard(1_950, "C4 rise -24.8 dB")
            val report = w.end(9_000, "left")!!.report
            assertTrue("$tag: $report", "+0.95 s  C4 rise -24.8 dB" in report)
            val clips = StageClipWatch(quietMs = 0, minSounds = 1).apply { reset(0, listOf("C4")) }
            clips.sound(1_250, "noise")
            assertTrue(tag, "+1.25 s  noise" in clips.check(5_000, "lesson=x", "kid")!!.report)
            assertEquals(tag, "2:05, no wrong notes", SightScore(125, 0).label)
            assertEquals(tag, "7:05", ReminderPlan.label(425))
        }
    }

    /** The matcher's trace (StallReviewTest and the debug log read it) keeps its decimal points. */
    @Test fun theMatcherTraceReadsTheSameInEveryLanguage() {
        for (tag in locales) inLocale(tag) {
            val m = NoteMatcher(48_000).apply { expected = setOf(60); groups = listOf(setOf(60)) }
            val lines = mutableListOf<String>()
            m.trace = { lines += it }
            val audio = PianoSynth.render(listOf(SynthNote(64, 300.0), SynthNote(60, 900.0)), 1600.0)
            var i = 0
            while (i < audio.size) { val n = minOf(480, audio.size - i); m.process(audio.copyOfRange(i, i + n)); i += n }
            assertTrue(tag, lines.isNotEmpty())
            for (l in lines) assertTrue("$tag: $l", l.none { it.isDigit() && it !in '0'..'9' } && !Regex("\\d,\\d").containsMatchIn(l))
            assertTrue(tag, lines.any { Regex("-?\\d+\\.\\d").containsMatchIn(it) })
        }
    }
}
