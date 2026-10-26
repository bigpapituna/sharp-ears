package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Praise
import com.bigpapituna.sharpears.engine.brief.PraiseKind
import com.bigpapituna.sharpears.engine.brief.SettingChange
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.14, Priya's P10 on the brief's words, and the new settings in "What you did". */
class ParentsWordsTest {
    private val day = 86_400_000L
    private val base = 1_792_000_000_000L
    private fun findings(at: Long, praise: List<Praise>, settings: List<SettingChange> = emptyList()) = Findings(
        ProfileKind.KID, Period.DAY, TimeRange(at - 3_600_000, at),
        facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 3, 5)), praise = praise, settings = settings)

    /** "Mia earned three stars" read as the latest lesson's, which had one: the headline always says which lesson. */
    @Test fun aThreeStarHeadlineNamesItsLesson() {
        for (seed in 0L until 40L) for (k in 0 until 7) {
            val f = findings(base + k * day, listOf(Praise(PraiseKind.THREE_STARS, 1, lessons = listOf("playground-1"))))
            val withTitle = TemplateWriter({ if (it == "playground-1") "High and low" else "" }, seed).write(f, "Mia").headline
            assertFalse(withTitle, Regex("earned three stars(?! on)").containsMatchIn(withTitle))
            val noTitle = TemplateWriter({ "" }, seed).write(f, "Mia").headline
            assertFalse(noTitle, Regex("earned three stars(?! on)").containsMatchIn(noTitle))
        }
    }

    /** "A request to hear it is the best praise" came back every evening: a day's brief reads differently on the next day. */
    @Test fun aDaysPraiseStepsOnEachDay() {
        val writer = TemplateWriter({ if (it == "playground-1") "High and low" else "" }, seed = 3)
        val praise = (0 until 7).map { k ->
            writer.write(findings(base + k * day, listOf(Praise(PraiseKind.THREE_STARS, 1, lessons = listOf("playground-1")))), "Mia").praise
        }
        assertTrue("the same line on 7 days: $praise", praise.distinct().size >= 3)
        for (k in 1 until 7) assertTrue("two days in a row: ${praise[k]}", praise[k] != praise[k - 1])
    }

    @Test fun theNewSettingsAreSaidPlainly() {
        val f = findings(base, emptyList(), listOf(SettingChange(base - 1000, "capMin", "15"), SettingChange(base - 900, "bedtimeSongs", "true"),
            SettingChange(base - 800, "bedtime", "true")))
        val text = TemplateWriter({ "" }, 0).write(f, "Mia").let { b -> (listOf(b.headline) + b.facts + b.sections.map { it.body }).joinToString("\n") }
        assertTrue(text, "The daily time was set to 15 minutes." in text)
        assertTrue(text, "Songs and the Jam now close after the goal too." in text)
        assertTrue(text, "Wind down after the goal was turned on." in text)
        assertFalse(text, "A setting was changed." in text)
    }
}
