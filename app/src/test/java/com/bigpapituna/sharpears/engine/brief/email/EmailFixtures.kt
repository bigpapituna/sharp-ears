package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefSamples
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.ClapStat
import com.bigpapituna.sharpears.engine.brief.EarStat
import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.HandStat
import com.bigpapituna.sharpears.engine.brief.HesitationStat
import com.bigpapituna.sharpears.engine.brief.HintStats
import com.bigpapituna.sharpears.engine.brief.KeySlips
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.PlayMix
import com.bigpapituna.sharpears.engine.brief.SlipPair
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.templates.CourseTitles
import com.bigpapituna.sharpears.engine.brief.templates.TemplateWriter

/** The sample emails the renderer's tests and snapshots read: Mia (the blue pup, avatar 1), p1, a week, a day, the first. */
object EmailFixtures {
    private val songs = mapOf("twinkle" to "Twinkle, Twinkle, Little Star", "hot-cross-buns" to "Hot Cross Buns", "ode-to-joy" to "Ode to Joy")
    private val lookup = CourseTitles.lookup { songs[it] }
    val titles = object : BriefTitles {
        override fun lesson(id: String) = lookup(id)
        override fun song(id: String) = lookup(id)
        override fun page(id: String) = lookup(id)
    }
    private val writer = TemplateWriter(lookup, seed = 3, clock = { NOW })

    /** The kid's sample week with the email's extra stats in it (as EmailBriefTest builds it). */
    fun weekFindings(): Findings = BriefSamples.kid(Period.WEEK, NOW, ZONE).let { f ->
        f.copy(stats = f.stats.copy(
            keySlips = listOf(KeySlips(62, 5, 0), KeySlips(64, 1, 0), KeySlips(67, 0, 2), KeySlips(61, 1, 1)), topSlip = SlipPair(62, 64, 5),
            notesDue = 412, skippedNotes = 4, hesitation = HesitationStat(1_100, 380, 67, 2_400), tempoSpreadMs = 38, tempoRuns = 3,
            left = HandStat(40, 88, 1_500), right = HandStat(372, 97, 900), hintsBefore = HintStats(2, 9, 4, 0),
            clap = ClapStat(14, 16, 1), ear = listOf(EarStat("pitch", 6, 5), EarStat("chord:build", 4, 3)), weeks = listOf(19, 22, 38, 40),
            sessionMinutes = 13, sessionMinutesBefore = 9, mix = PlayMix(30, 9, 3),
        ), facts = f.facts + Fact(FactKind.TIMING, 44, 4))
    }

    fun dayFindings(): Findings = BriefSamples.kid(Period.DAY, NOW, ZONE)

    fun email(f: Findings, first: Boolean = false, name: String = "Mia", avatar: Int = 1, profileId: String = "p1",
              frequency: EmailFrequency = EmailFrequency.BOTH, brief: Brief = writer.write(f, name)): EmailBrief =
        EmailBriefs.build(f, brief, name, avatar, week = 43, frequency = frequency, first = first, titles = titles, code = if (first) "482913" else null,
            extras = EmailExtras(path = PathProgress("pond", 7, 12, "hall"), bedtimeOn = false, phoneName = "Roy's phone"),
            summary = { id -> if (id == "pond-3") "Listening: copy four notes" else null }, zone = ZONE, profileId = profileId)

    fun week(first: Boolean = false) = email(weekFindings(), first)
    fun day() = email(dayFindings())
    fun first() = email(dayFindings(), first = true)
    fun firstNeverPlayed() = email(BriefSamples.empty(Period.DAY, NOW, ZONE), first = true)
}
