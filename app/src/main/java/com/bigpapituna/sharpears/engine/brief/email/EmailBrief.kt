package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.ActionCopy
import com.bigpapituna.sharpears.engine.brief.ActionRules
import com.bigpapituna.sharpears.engine.brief.ActionTaken
import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.BriefTitles
import com.bigpapituna.sharpears.engine.brief.Candidate
import com.bigpapituna.sharpears.engine.brief.ExportLayout
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.FlagKind
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.IsoFields

/*
 * The parent brief by email, design D (Roy, 2026-10-21; docs/email-design-D.py draws the mockup): one email as structured
 * data, built from the findings and the brief the writer chain wrote. Not HTML: step 2's renderer draws it (the postcard,
 * the cards, the tap-to-open sections), PlainTextEmail writes the text part and the fallback. Android-free and tested.
 *
 * The email is read on the grown-up's own phone, where the app is usually not installed, so nothing in it can act: every
 * recommendation says "Do this in the app" with a plain line of where (grown-ups corner > Family > the child). Words follow
 * the brief's rules: warm, factual, numbers present, sentence case, no em or en dash, no comparison with anyone else, no
 * advice outside the app, the child's name rather than a pronoun.
 */

/** Which email: a day's or a week's (a session note is never emailed). */
enum class EmailPeriod {
    DAY, WEEK;

    companion object {
        fun of(p: Period): EmailPeriod = when (p) {
            Period.DAY -> DAY
            Period.WEEK -> WEEK
            Period.SESSION -> throw IllegalArgumentException("a session note is not emailed")
        }
    }
}

/** The pup pictures design D uses (art/pup_*.png): the postcard, "say this at home", the gold star. */
enum class PupPose { DANCING, STICKER, CROWN, THINKING, CLAPPING, FLYING }

/**
 * One email. Top to bottom as design D lays it out: [welcome] (the first email only), the [postcard], [oneMinute], [next]
 * (null when there is nothing to suggest), the [details] (tap-to-open sections), the [signOff] and the [footer].
 */
data class EmailBrief(
    val period: EmailPeriod,
    /** The child's first name, as the words use it. */
    val name: String,
    /** The subject line: the first of [subjects]. */
    val subject: String,
    /** Every subject variant that fits (at most [EmailWords.SUBJECT_MAX] characters, no dashes), warmest first. */
    val subjects: List<String>,
    /** The line a mail app shows after the subject (hidden in the body). */
    val preheader: String,
    val welcome: Welcome?,
    val postcard: Postcard,
    val oneMinute: OneMinute,
    val next: NextSteps?,
    val details: Details,
    val signOff: SignOff,
    val footer: Footer,
    val writtenBy: Brief.Writer,
    /** The full report's PDF file name ("Mia-weekly-brief-2026-10-21.pdf"), for the attachment. */
    val attachmentName: String,
) {
    /** Every piece of text in the email, in order (the rules' tests walk it; a renderer never needs it). */
    fun texts(): List<String> = buildList {
        add(subject); addAll(subjects); add(preheader)
        welcome?.let { w ->
            add(w.title); addAll(w.paragraphs); add(w.code); add(w.codeLine)
            add(w.howOften.title); add(w.howOften.where); w.howOften.choices.forEach { add(it.label); add(it.text) }; add(w.howOften.note)
        }
        with(postcard) { add(stamp); add(range); add(headline); addAll(chips); paws.forEach { add(it.day) }; pawCaption?.let(::add) }
        add(oneMinute.title); oneMinute.parts.forEach { add(it.label); add(it.text) }
        oneMinute.sayThis?.let { add(it.label); add(it.quote) }
        next?.let { n -> add(n.title); add(n.intro); n.items.forEach { i -> add(i.text); add(i.why); listOfNotNull(i.action, i.button, i.where).forEach(::add) } }
        add(details.title); add(details.intro)
        details.sections.forEach { s -> add(s.title); s.blocks.forEach { addAll(it.texts()) } }
        add(signOff.title); add(signOff.text); add(signOff.signature)
        addAll(footer.lines)
    }
}

/** The first email's welcome: what these are, the code that confirms the address, and how to choose how often. */
data class Welcome(val title: String, val paragraphs: List<String>, val code: String, val codeLine: String, val howOften: HowOften)

data class HowOften(val title: String, val where: String, val choices: List<FrequencyChoice>, val note: String)

data class FrequencyChoice(val frequency: EmailFrequency, val label: String, val text: String)

/**
 * The postcard at the top: the child's pup ([avatar], the sweatband colour index of Profile.avatar, and its colour's
 * name) in a [pose], the [stamp] ("WEEK 42", "WED 21 OCT"), the [range] covered, the brief's [headline], a few [chips]
 * ("78 minutes", "6 of 7 days") and the seven-day paw strip ([paws], oldest first) with its [pawCaption].
 */
data class Postcard(
    val avatar: Int, val avatarColour: String, val pose: PupPose, val stamp: String, val range: String, val headline: String,
    val chips: List<String>, val paws: List<Paw>, val pawCaption: String?,
)

/** One day of the strip: [day] ("Thu"), whether it had practice and reached the goal, and whether it is the last day. */
data class Paw(val day: String, val practised: Boolean, val goal: Boolean, val today: Boolean)

/** "In one minute": [parts] (what happened, the hard bit, getting better) and the quote to say at home. */
data class OneMinute(val title: String, val parts: List<Part>, val sayThis: SayThis?)

/** A paragraph that starts with a bold [label] ("What happened."). */
data class Part(val label: String, val text: String)

/** "Say this at home": a line for the grown-up to say to the child, beside the pup in [pose]. */
data class SayThis(val label: String, val quote: String, val pose: PupPose)

/** "What I'd do next": the brief's recommendations, each with why, and at most one thing to keep doing. */
data class NextSteps(val title: String, val intro: String, val items: List<NextItem>)

/**
 * One suggestion: [number], what to do ([text], the brief's words), [why] it helps. When it is an action in the app,
 * [candidate] and [action] (the app's own button name, "Set as next"), [button] ("Do this in the app") and [where]
 * ("Grown-ups corner > Family > Mia, then Set as next"); a thing to keep doing has none of them (nothing to tap).
 */
data class NextItem(
    val number: Int, val text: String, val why: String,
    val candidate: Candidate? = null, val action: String? = null, val button: String? = null, val where: String? = null,
)

/** "The details, if you want them": tap-to-open sections, the first open. */
data class Details(val title: String, val intro: String, val sections: List<DetailSection>)

/** The five sections of design D, in order. */
enum class SectionId { LESSONS, KEYS, RHYTHM, HABITS, READINESS }

data class DetailSection(val id: SectionId, val title: String, val open: Boolean, val blocks: List<Block>)

/** What a section holds; the renderer draws each its way (design D: a table, a key strip, stat tiles, bars, a meter). */
sealed interface Block {
    fun texts(): List<String>

    /** A paragraph, with a bold [label] when it has one ("Timing:"); [small] for a caption or a footnote. */
    data class Text(val text: String, val label: String? = null, val small: Boolean = false) : Block {
        override fun texts() = listOfNotNull(label, text)
    }

    /** A table: a lesson or a song per row. */
    data class Rows(val rows: List<Row>) : Block {
        override fun texts() = rows.flatMap { listOfNotNull(it.title, it.subtitle, it.value) }
    }

    /** The slip map: every key from a C to a C over the keys that slipped, low to high, with its counts and [caption]. */
    data class Keys(val keys: List<KeyCell>, val caption: String) : Block {
        override fun texts() = keys.map { it.name } + caption
    }

    /** Stat tiles: a big value and its label ("11", "near misses"). */
    data class Tiles(val tiles: List<Tile>) : Block {
        override fun texts() = tiles.flatMap { listOf(it.value, it.label) }
    }

    /** Bars oldest first (minutes per week), with a [caption] and the values in words below. */
    data class Bars(val bars: List<Bar>, val caption: String, val values: String) : Block {
        override fun texts() = bars.map { it.label } + caption + values
    }

    /** One bar split in parts (where the minutes went), percents adding up to 100. */
    data class Meter(val parts: List<MeterPart>) : Block {
        override fun texts() = parts.map { it.label }
    }
}

/** A lesson or song: [title], a [subtitle] (a lesson's summary; "Song"), the [value] in words, its [stars] (0 to 3, null unfinished), [highlight] a hard one. */
data class Row(val title: String, val subtitle: String?, val value: String, val stars: Int?, val highlight: Boolean = false)

/**
 * One key of the slip map: its MIDI number, its [name] ("E", "F sharp"), [black] for a black key, the wrong keys struck
 * when it was wanted ([near] next door, [far] further away) and its [heat], 0 (none) to 3 (the most slips of the map).
 */
data class KeyCell(val midi: Int, val name: String, val black: Boolean, val near: Int, val far: Int, val heat: Int)

data class Tile(val value: String, val label: String)

/** One bar: its [label] ("This week"), its [value], and its [share] of the tallest bar (0 to 1). */
data class Bar(val label: String, val value: Int, val share: Float)

data class MeterPart(val label: String, val percent: Int)

/** The gold star at the bottom: the brief's praise, signed by the pup. */
data class SignOff(val title: String, val text: String, val signature: String, val pose: PupPose)

data class Footer(val lines: List<String>)

/** Where the child is on the path, for "Readiness and what changed": [done] of [total] stones on [pageId], [nextPageId] after it. */
data class PathProgress(val pageId: String, val done: Int, val total: Int, val nextPageId: String? = null)

/** What the phone knows beyond the findings, all optional: the path, bedtime, and the phone's name for the footer ("Roy's phone"). */
data class EmailExtras(val path: PathProgress? = null, val bedtimeOn: Boolean? = null, val phoneName: String? = null)

/** Builds an [EmailBrief]. */
object EmailBriefs {
    /** A week's stamp: the ISO week of the year of [ms]. */
    fun weekNumber(ms: Long, zone: ZoneId): Int = Instant.ofEpochMilli(ms).atZone(zone).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /**
     * The email for [findings] (DAY or WEEK) and the [brief] written from them, about [name]. [avatar] is the child's
     * Profile.avatar, [week] the stamp's week number ([weekNumber]); [actions] what grown-ups did (default: those in the
     * findings); [frequency] and [weekly] what the grown-up chose (the footer says it); [first] the first email, which
     * carries the welcome and the verification [code]. [titles] names lessons, songs and pages; [summary] a lesson's one
     * line (COURSE's summary), shown under its name. [attached]: the full report's PDF goes with it (the footer says so).
     */
    fun build(
        findings: Findings,
        brief: Brief,
        name: String,
        avatar: Int,
        week: Int,
        frequency: EmailFrequency,
        first: Boolean,
        titles: BriefTitles,
        code: String? = null,
        actions: List<ActionTaken> = findings.actions,
        weekly: WeeklySlot = WeeklySlot.DEFAULT,
        extras: EmailExtras = EmailExtras(),
        summary: (String) -> String? = { null },
        zone: ZoneId = ZoneId.systemDefault(),
        attached: Boolean = true,
    ): EmailBrief {
        require(!first || code != null) { "the first email carries the code" }
        val period = EmailPeriod.of(findings.period)
        val n = EmailWords.name(name)
        val w = EmailWords(n, period, titles)
        val f = findings.copy(actions = actions)
        val last = Instant.ofEpochMilli(f.range.toMs).atZone(zone).toLocalDate()
        val recs = brief.recommendations.filter { ActionRules.allowed(f.profileKind, it.candidate) }.take(3)

        val postcard = Postcard(
            avatar = avatar, avatarColour = EmailWords.avatarColour(avatar),
            pose = when { f.isEmpty -> PupPose.THINKING; f.praise.isNotEmpty() -> PupPose.DANCING; else -> PupPose.CLAPPING },
            stamp = if (period == EmailPeriod.WEEK) "WEEK $week" else EmailWords.dayStamp(last),
            range = BriefFormat.rangeLabel(f, zone), headline = brief.headline,
            chips = w.chips(f), paws = paws(f, last), pawCaption = w.pawCaption(f),
        )
        val hard = f.flags.any { it.kind !in EmailWords.GOOD_FLAGS }
        val oneMinute = OneMinute("In one minute", buildList {
            add(Part("What happened.", brief.facts.take(3).joinToString(" ").ifBlank { w.quiet() }))
            if (hard && brief.notes.isNotEmpty()) add(Part("The hard bit.", brief.notes.first()))
            w.better(f)?.let { add(Part("Getting better.", it)) }
        }, if (f.isEmpty) null else SayThis("Say this at home:", w.sayThis(f.praise.firstOrNull()), PupPose.STICKER))

        val items = recs.mapIndexed { i, r ->
            val action = ActionCopy.of(r.candidate, titles, n).button
            NextItem(i + 1, r.text, w.whyFor(r.candidate, f), r.candidate, action, EmailWords.DO_IN_APP, w.where(action))
        }
        val keep = if (items.size < 3 && !f.isEmpty) w.keepDoing(f, recs.isEmpty())?.let { (text, why) -> NextItem(items.size + 1, text, why) } else null
        val next = (items + listOfNotNull(keep)).takeIf { it.isNotEmpty() }?.let { NextSteps("What I'd do next", w.nextIntro(), it) }

        return EmailBrief(
            period = period, name = n,
            subject = w.subjects(brief.headline, first, code).first(), subjects = w.subjects(brief.headline, first, code),
            preheader = w.preheader(f, recs.size, first),
            welcome = if (first) w.welcome(code!!, weekly, f.isEmpty) else null,
            postcard = postcard, oneMinute = oneMinute, next = next,
            details = Details("The details, if you want them", "Tap a section to open it.", EmailSections(w, f, titles, summary, extras, zone).all()),
            signOff = SignOff("Gold star at home", brief.praise.ifBlank { w.quiet() }, "Signed, the pup", PupPose.CROWN),
            footer = Footer(w.footer(brief.writtenBy, frequency, weekly, first, extras.phoneName, attached)),
            writtenBy = brief.writtenBy,
            attachmentName = ExportLayout.fileNames(n, f.period, last).second,
        )
    }

    private fun paws(f: Findings, last: LocalDate): List<Paw> = f.stats.days.map { d ->
        Paw(BriefFormat.dayShort(d.date), d.practiceMinutes > 0, d.goal, d.date == last.toString())
    }
}

/** The kid-only flags that are good news (they need no "hard bit"). */
internal fun FlagKind.good() = this in EmailWords.GOOD_FLAGS

internal fun Findings.kid() = profileKind == ProfileKind.KID
