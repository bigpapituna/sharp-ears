package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.ActionId
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Insights
import com.bigpapituna.sharpears.engine.brief.KidCard
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.StickerRarity
import com.bigpapituna.sharpears.engine.brief.TODAY
import com.bigpapituna.sharpears.engine.brief.ToyKind
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.at
import com.bigpapituna.sharpears.engine.brief.history
import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Prints one full sample of each kind to the test output (not the app), from findings the insights engine makes out of
 * a realistic history, so Roy and the integrator can read the prose: a kid's week, a kid's session note, a grown-up's
 * week and the child's card. The same four go in SAMPLES.md (this test also writes them to build/template-samples.md).
 */
class TemplateCatalogTest {
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    private val roy = Profile.owner()
    private fun d(day: Int): LocalDate = LocalDate.of(2026, 10, day)
    private val songs = mapOf("twinkle" to "Twinkle, Twinkle, Little Star", "ode-to-joy" to "Ode to Joy", "minuet-g" to "Minuet in G")
    private val titles = CourseTitles.lookup { songs[it] }
    private val writer = TemplateWriter(titles, seed = 20261021, clock = { NOW })

    /** Mia: a pre-reader, two practice days a week before, five this week, one stubborn lesson, a jam, a shiny sticker. */
    private val miaWeek: List<BriefEvent> = history {
        session(d(3), 18, 8) { step("playground-1"); done("playground-1", 3) }
        session(d(6), 18, 9) { step("playground-2"); done("playground-2", 3) }
        session(d(10), 18, 10) { step("playground-3"); done("playground-3", 2) }
        session(d(13), 18, 10) { step("playground-4"); done("playground-4", 3) }
        session(d(15), 18, 12) { step("pond-1"); done("pond-1", 3); goal() }
        session(d(16), 18, 15) { step("pond-2", tries = 4, stars = 2); step("pond-2", 2); done("pond-2", 3); sticker(StickerRarity.SHINY); goal() }
        session(d(17), 17, 10) { toy(ToyKind.JAM, 6); goal() }
        session(d(19), 18, 15) {
            step("pond-3", 1, tries = 3, letGo = true, stars = 1); step("pond-3", 2, tries = 3, stars = 1); done("pond-3", 1); goal()
        }
        session(d(20), 18, 14) { step("pond-4"); done("pond-4", 3); song("twinkle", 3); goal() }
        session(d(21), 17, 12) { step("pond-3", 1, tries = 3, letGo = true, stars = 1); done("pond-3", 1, first = false); goal() }
    }

    /** Roy: evenings, a song he is polishing, timing and ear work, one stuck lesson. */
    private val royWeek: List<BriefEvent> = history {
        session(d(5), 21, 20) { song("twinkle", 2, timing = 90, accuracy = 55) }
        session(d(8), 21, 25) { song("ode-to-joy", 2, timing = 80, accuracy = 60) }
        session(d(12), 21, 20) { song("twinkle", 2, timing = 85, accuracy = 58) }
        session(d(15), 21, 30) { song("ode-to-joy", 2, timing = 60, accuracy = 70); ear("pitch", true); ear("pitch", true); ear("pitch", false) }
        session(d(17), 21, 25) { song("minuet-g", 2, timing = 50, accuracy = 76); song("minuet-g", 3, timing = 40, accuracy = 82); ear("pitch", true) }
        session(d(19), 20, 35) { step("hall-5", tries = 4, stars = 1, near = 4, far = 1, wrong = 5); step("hall-5", 2, tries = 3, letGo = true, stars = 1); song("minuet-g", 3, timing = 38, accuracy = 85) }
        session(d(21), 19, 30) { song("minuet-g", 3, timing = 35, accuracy = 88, near = 1); song("ode-to-joy", 3, timing = 40, accuracy = 84); setting("colourNotes", "true"); action("SETTING", mapOf("setting" to "colourNotes", "value" to "true")) }
    }

    private fun render(b: Brief): String = buildString {
        appendLine("**${b.headline}**")
        appendLine()
        b.facts.forEach { appendLine("- $it") }
        if (b.notes.isNotEmpty()) { appendLine(); b.notes.forEach { appendLine("> $it") } }
        if (b.recommendations.isNotEmpty()) { appendLine(); appendLine("Recommended:"); b.recommendations.forEach { appendLine("- ${it.text}  [${it.candidate.action}]") } }
        appendLine(); appendLine("To praise: ${b.praise}")
        for (s in b.sections) { appendLine(); appendLine("*${s.title}.* ${s.body}") }
    }

    private fun render(k: KidCard): String = (k.lines + listOfNotNull(k.nextTry)).joinToString(NL, postfix = NL) { "- \"$it\"" }
    private val NL = Char(10).toString()

    @Test fun printsOneFullSampleOfEachKind() {
        val kidWeek: Findings = Insights.compute(miaWeek, mia, Period.WEEK, NOW, zone = ZONE)
        // The session note: Mia's last session, right after it, with a fresh finish and the stubborn lesson again.
        val sessionNow = at(d(21), 17, 30)
        val kidSession = Insights.compute(miaWeek, mia, Period.SESSION, sessionNow, zone = ZONE)
        val adultWeek = Insights.compute(royWeek, roy, Period.WEEK, NOW, zone = ZONE)
        val kidCardFindings = Insights.compute(miaWeek, mia, Period.DAY, NOW, zone = ZONE)

        val wk = writer.write(kidWeek, "Mia")
        val ss = writer.write(kidSession, "Mia")
        val aw = writer.write(adultWeek, "Roy")
        val kc = writer.kidCard(kidCardFindings, "Mia")
        val card2 = writer.kidCard(kidWeek, "Mia")

        val out = buildString {
            appendLine("## A kid's week (Mia, pre-reader, week of 15 to 21 October)"); appendLine(); appendLine(render(wk))
            appendLine("## A kid's session note"); appendLine(); appendLine(render(ss))
            appendLine("## A grown-up's week (\"My week\")"); appendLine(); appendLine(render(aw))
            appendLine("## The child's card (after today's session)"); appendLine(); appendLine(render(kc))
            appendLine("## The child's card (from the week's findings)"); appendLine(); appendLine(render(card2))
            appendLine("## The same findings, four weeks running (how the wording moves on)"); appendLine()
            for (i in 0..3) {
                val shifted = kidWeek.copy(range = kidWeek.range.copy(fromMs = kidWeek.range.fromMs + i * 7 * 86_400_000L, toMs = kidWeek.range.toMs + i * 7 * 86_400_000L))
                val b = writer.write(shifted, "Mia")
                appendLine("- Week $i. ${b.headline} ${b.facts.first()} ${b.praise}")
            }
            appendLine("## Asking the brief"); appendLine()
            for ((_, q) in EXAMPLE_QUESTIONS) appendLine("- \"$q\" -> ${writer.answer(kidWeek, "Mia", q)}")
            appendLine("- \"What is the weather like?\" -> ${writer.answer(kidWeek, "Mia", "What is the weather like?")}")
        }
        println(out)
        File("build").mkdirs()
        File("build/template-samples.md").writeText(out)

        assertTrue(kidWeek.flags.isNotEmpty() && kidWeek.praise.isNotEmpty())
        assertTrue(wk.sections.isNotEmpty() && aw.sections.isNotEmpty())
        assertEquals(emptyList<Brief.Section>(), ss.sections)
        assertTrue(kc.lines.size in 2..3)
        assertTrue(ActionId.SETTING in adultWeek.actions.map { it.action })
        @Suppress("UNUSED_VARIABLE") val unused = TODAY
    }
}
