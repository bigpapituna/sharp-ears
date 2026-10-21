package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.time.ZoneId
import java.util.Locale

/*
 * Export (docs/PARENT-BRIEF-PLAN.md: "any brief as an image or PDF through the share sheet"). The document is built here as
 * blocks, laid out here in plain units (pure, tested), and drawn by ui/brief/BriefExport.kt on an Android Canvas: once on a
 * tall bitmap for the PNG card and once per page on a PdfDocument for the PDF.
 *
 * Layout units: the content is [ExportLayout.WIDTH] wide (the PNG draws 1 unit as 1 px with a margin of [ExportLayout.MARGIN];
 * the PDF scales the same layout by [ExportLayout.PDF_SCALE] onto a Letter page), so both look the same.
 */

/** The look of a run of text; the drawer maps each to a font, a size and a colour. */
enum class TextKind(val size: Int, val lineHeight: Int, val gapBefore: Int, val keepWithNext: Boolean = false) {
    TITLE(60, 70, 14),
    HEADLINE(44, 58, 26),
    SECTION(34, 44, 54, keepWithNext = true),
    BODY(31, 45, 10),
    BULLET(31, 45, 8),
    SMALL(25, 34, 10),
    ACTION(30, 42, 8),
}

/** What goes into a document. */
sealed interface ExportBlock {
    data class Text(val kind: TextKind, val text: String) : ExportBlock
    /** The seven-day strip: a day label, minutes (practised) and whether the goal was reached. */
    data class Strip(val days: List<DayStat>, val goalMinutes: Int?) : ExportBlock
    /** One small line per skill (a title, its summary and the points). */
    data class Trends(val rows: List<TrendRow>) : ExportBlock
    /** Horizontal bars: toys and the time of day. */
    data class Bars(val rows: List<BarRow>) : ExportBlock
    /** The top of the card: the brand and the pup. */
    data class Header(val brand: String) : ExportBlock
    /** A thin rule between sections. */
    data object Rule : ExportBlock
}

data class TrendRow(val label: String, val summary: String, val points: List<Double?>, val upIsGood: Boolean)
data class BarRow(val label: String, val value: String, val share: Float)

/** A laid out piece: a line of text, or a graphic block, at [y] (units from the top of its page or card) with [height]. */
data class Placed(val block: ExportBlock, val y: Int, val height: Int)

/** One PDF page: what is on it and how much of its height is used. */
data class ExportPage(val items: List<Placed>, val used: Int)

/** How wide a string is in a kind of text, in layout units (the drawer measures with its Paint; tests with a fixed width per character). */
fun interface TextMetrics { fun width(text: String, kind: TextKind): Float }

object ExportLayout {
    const val WIDTH = 960
    const val MARGIN = 60
    /** The card's width in pixels: content plus both margins. */
    const val CARD_PX = WIDTH + 2 * MARGIN
    /** A Letter page, 612 by 792 pt, with a 36 pt margin: the content is 540 pt wide, so 540/960 pt per unit. */
    const val PDF_SCALE = 0.5625f
    const val PDF_PAGE_W = 612
    const val PDF_PAGE_H = 792
    const val PDF_MARGIN = 36
    /** The content height of a PDF page in layout units: (792 - 2 * 36) / 0.5625. */
    const val PDF_CONTENT_H = 1280
    /** The PNG card is never taller than this many pixels (a very long full report is cut: the PDF has all of it). */
    const val MAX_CARD_PX = 7200
    const val MIN_CARD_PX = 900

    const val HEADER_H = 130
    const val STRIP_H = 210
    const val TREND_ROW_H = 96
    const val BAR_ROW_H = 56
    const val BULLET_INDENT = 40

    /** Greedy word wrap into lines no wider than [maxWidth]; a word wider than the line is broken by character. Never returns an empty list for non-empty text. */
    fun wrap(text: String, maxWidth: Float, kind: TextKind, metrics: TextMetrics): List<String> {
        val lines = ArrayList<String>()
        for (para in text.split('\n')) {
            var line = ""
            for (word in para.trim().split(' ').filter { it.isNotEmpty() }) {
                var w = word
                // A word that does not fit alone is cut where it overflows.
                while (metrics.width(w, kind) > maxWidth && w.length > 1) {
                    var cut = w.length - 1
                    while (cut > 1 && metrics.width(w.take(cut), kind) > maxWidth) cut--
                    if (line.isNotEmpty()) { lines += line; line = "" }
                    lines += w.take(cut); w = w.drop(cut)
                }
                val tryLine = if (line.isEmpty()) w else "$line $w"
                if (line.isEmpty() || metrics.width(tryLine, kind) <= maxWidth) line = tryLine else { lines += line; line = w }
            }
            if (line.isNotEmpty() || para.isBlank()) lines += line
        }
        return lines.ifEmpty { listOf("") }
    }

    /** One drawable atom: a line of text, or a graphic; [keep] glues it to the next atom across a page break. */
    internal data class Atom(val block: ExportBlock, val height: Int, val gapBefore: Int, val keep: Boolean)

    /** Breaks [blocks] into atoms: text blocks become one atom per wrapped line (a paragraph's gap sits before its first line only). */
    internal fun atoms(blocks: List<ExportBlock>, metrics: TextMetrics): List<Atom> = buildList {
        for (b in blocks) when (b) {
            is ExportBlock.Text -> {
                val indent = if (b.kind == TextKind.BULLET || b.kind == TextKind.ACTION) BULLET_INDENT else 0
                val lines = wrap(b.text, (WIDTH - indent).toFloat(), b.kind, metrics)
                lines.forEachIndexed { i, l ->
                    add(Atom(ExportBlock.Text(b.kind, l), b.kind.lineHeight, if (i == 0) b.kind.gapBefore else 0, b.kind.keepWithNext || (lines.size > 2 && i == 0)))
                }
            }
            is ExportBlock.Strip -> add(Atom(b, STRIP_H, 22, false))
            is ExportBlock.Trends -> b.rows.forEachIndexed { i, r -> add(Atom(ExportBlock.Trends(listOf(r)), TREND_ROW_H, if (i == 0) 14 else 4, false)) }
            is ExportBlock.Bars -> b.rows.forEachIndexed { i, r -> add(Atom(ExportBlock.Bars(listOf(r)), BAR_ROW_H, if (i == 0) 14 else 2, false)) }
            is ExportBlock.Header -> add(Atom(b, HEADER_H, 0, false))
            ExportBlock.Rule -> add(Atom(b, 2, 30, false))
        }
    }

    /**
     * The whole document as one tall column (the PNG card): each atom with its y, and the height of the content. The
     * drawer adds [MARGIN] above and below; the card is [cardHeight] pixels, cut at [MAX_CARD_PX].
     */
    fun column(blocks: List<ExportBlock>, metrics: TextMetrics): Pair<List<Placed>, Int> {
        var y = 0
        val placed = ArrayList<Placed>()
        for (a in atoms(blocks, metrics)) {
            if (placed.isNotEmpty()) y += a.gapBefore
            placed += Placed(a.block, y, a.height)
            y += a.height
        }
        return placed to y
    }

    /** The PNG card's height in pixels for [contentHeight]: margins added, never under [MIN_CARD_PX] or over [MAX_CARD_PX]. */
    fun cardHeight(contentHeight: Int): Int = (contentHeight + 2 * MARGIN).coerceIn(MIN_CARD_PX, MAX_CARD_PX)

    /** The atoms of [placed] that fit on a card [cardHeight] tall (the rest stay in the PDF). */
    fun cardItems(placed: List<Placed>, cardHeight: Int): List<Placed> = placed.filter { it.y + it.height <= cardHeight - MARGIN }

    /**
     * The same document in pages [pageHeight] units tall (the PDF): atoms fill a page until the next does not fit; a
     * heading is never left alone at the bottom (it moves to the next page with the line after it); a gap is dropped at the
     * top of a page. Every atom is on exactly one page, in order.
     */
    fun paginate(blocks: List<ExportBlock>, metrics: TextMetrics, pageHeight: Int = PDF_CONTENT_H): List<ExportPage> {
        val atoms = atoms(blocks, metrics)
        val pages = ArrayList<ExportPage>()
        var items = ArrayList<Placed>()
        var y = 0
        fun flush() { pages += ExportPage(items, y); items = ArrayList(); y = 0 }
        var i = 0
        while (i < atoms.size) {
            // A run of kept atoms (a heading and the line after it) moves as one.
            var j = i
            while (j < atoms.lastIndex && atoms[j].keep) j++
            val group = atoms.subList(i, j + 1)
            val gap0 = if (items.isEmpty()) 0 else group[0].gapBefore
            val need = gap0 + group[0].height + group.drop(1).sumOf { it.gapBefore + it.height }
            if (items.isNotEmpty() && y + need > pageHeight) flush()
            for (a in group) {
                if (items.isNotEmpty()) y += a.gapBefore
                // An atom taller than a page (it cannot be: the tallest is a strip) still goes on a page of its own.
                if (items.isNotEmpty() && y + a.height > pageHeight) { flush() }
                items += Placed(a.block, y, a.height)
                y += a.height
            }
            i = j + 1
        }
        if (items.isNotEmpty() || pages.isEmpty()) flush()
        return pages
    }

    /**
     * A document a little taller than one PDF page (the one-minute read) is drawn on one page, shrunk to fit: the factor to
     * multiply [PDF_SCALE] by (1 when it fits as it is), or null when it is too long to shrink (more than [MAX_SHRINK_OVER]
     * taller than a page), and the PDF keeps its pages.
     */
    fun fitOnePage(contentHeight: Int, pageHeight: Int = PDF_CONTENT_H): Float? = when {
        contentHeight <= pageHeight -> 1f
        contentHeight <= pageHeight * MAX_SHRINK_OVER -> pageHeight.toFloat() / contentHeight
        else -> null
    }
    const val MAX_SHRINK_OVER = 1.4f

    /** The file names a share carries: "Mia-weekly-brief-2026-10-21.png" and ".pdf". */
    fun fileNames(name: String, period: Period, date: java.time.LocalDate): Pair<String, String> {
        val slug = name.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "player" }
            .replaceFirstChar { it.uppercase() }
        val what = when (period) { Period.SESSION -> "session-note"; Period.DAY -> "daily-brief"; Period.WEEK -> "weekly-brief" }
        val base = "$slug-$what-$date"
        return "$base.png" to "$base.pdf"
    }
}

/** Builds the document for a brief: the one-minute read for "short", the whole report for "full". Pure. */
object ExportDoc {
    fun of(
        brief: Brief, findings: Findings, name: String, titles: BriefTitles, full: Boolean,
        now: Long, zone: ZoneId = ZoneId.systemDefault(),
    ): List<ExportBlock> = buildList {
        val kid = findings.profileKind == ProfileKind.KID
        add(ExportBlock.Header("Sharp Ears"))
        add(ExportBlock.Text(TextKind.TITLE, title(name, findings.period, kid)))
        add(ExportBlock.Text(TextKind.SMALL, BriefFormat.rangeLabel(findings, zone) + ". " + BriefFormat.writtenNote(brief, now, zone) + "."))
        add(ExportBlock.Text(TextKind.HEADLINE, brief.headline))
        brief.facts.take(if (full) brief.facts.size else 3).forEach { add(ExportBlock.Text(TextKind.BULLET, it)) }
        if (full) brief.notes.forEach { add(ExportBlock.Text(TextKind.BODY, it)) }
        if (brief.recommendations.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Things to try"))
            brief.recommendations.take(if (full) brief.recommendations.size else 3).forEach { r ->
                add(ExportBlock.Text(TextKind.BODY, r.text))
                val v = ActionCopy.of(r.candidate, titles, name)
                add(ExportBlock.Text(TextKind.ACTION, "In the app: ${v.button}"))
            }
        }
        add(ExportBlock.Text(TextKind.SECTION, "One thing to praise"))
        add(ExportBlock.Text(TextKind.BODY, brief.praise))
        if (full) fullReport(brief, findings, titles)
        add(ExportBlock.Rule)
        add(ExportBlock.Text(TextKind.SMALL, "Made on the phone from ${if (kid) name + "'s" else "your"} playing. Nothing leaves it unless you send it."))
    }

    private fun MutableList<ExportBlock>.fullReport(brief: Brief, f: Findings, titles: BriefTitles) {
        val s = f.stats
        if (s.days.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, if (f.period == Period.WEEK) "The week" else "The last seven days"))
            add(ExportBlock.Strip(s.days, f.goalMinutes))
        }
        if (s.lessons.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Lessons"))
            s.lessons.forEach { add(ExportBlock.Text(TextKind.BULLET, BriefFormat.lessonLine(it, titles))) }
        }
        if (s.songs.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Songs"))
            s.songs.forEach { add(ExportBlock.Text(TextKind.BULLET, BriefFormat.songLine(it, titles))) }
        }
        val rows = s.skills.filter { it.points.count { p -> p != null } >= 2 }
            .map { TrendRow(BriefFormat.skillLabel(it.kind), BriefFormat.skillSummary(it), it.points, BriefFormat.upIsGood(it.kind)) }
        if (rows.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Skills over four weeks"))
            add(ExportBlock.Trends(rows))
        }
        val toys = s.toys.filter { it.minutes > 0 || it.opens > 0 }
        if (toys.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Toys"))
            val shares = Spark.shares(toys.map { it.minutes })
            add(ExportBlock.Bars(toys.mapIndexed { i, t -> BarRow(BriefFormat.toyLabel(t.toy), BriefFormat.minutes(t.minutes), shares[i]) }))
        }
        val parts = s.timeOfDay.filter { it.minutes > 0 }
        if (parts.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Time of day"))
            val shares = Spark.shares(parts.map { it.minutes })
            add(ExportBlock.Bars(parts.mapIndexed { i, p -> BarRow(BriefFormat.partLabel(p.part), BriefFormat.minutes(p.minutes), shares[i]) }))
        }
        add(ExportBlock.Text(TextKind.SECTION, "The room and the hints"))
        add(ExportBlock.Text(TextKind.BODY, BriefFormat.noisyLine(s.noisyRoom)))
        BriefFormat.hintLines(s.hints).forEach { add(ExportBlock.Text(TextKind.BULLET, it)) }
        brief.sections.forEach { sec ->
            add(ExportBlock.Text(TextKind.SECTION, sec.title))
            add(ExportBlock.Text(TextKind.BODY, sec.body))
        }
        if (f.actions.isNotEmpty()) {
            add(ExportBlock.Text(TextKind.SECTION, "Since last time"))
            f.actions.forEach { add(ExportBlock.Text(TextKind.BULLET, BriefFormat.actionTaken(it, titles))) }
        }
    }

    /** "Mia's weekly brief", "Mia's session note", "Your week" for a grown-up. */
    fun title(name: String, period: Period, kid: Boolean): String = when {
        !kid -> when (period) { Period.SESSION -> "Your session note"; Period.DAY -> "Your day"; Period.WEEK -> "Your week" }
        else -> when (period) { Period.SESSION -> "$name's session note"; Period.DAY -> "$name's day"; Period.WEEK -> "$name's weekly brief" }
    }
}
