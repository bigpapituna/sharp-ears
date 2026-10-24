package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.Share
import com.bigpapituna.sharpears.engine.brief.BarRow
import com.bigpapituna.sharpears.engine.brief.BriefFormat
import com.bigpapituna.sharpears.engine.brief.DayStat
import com.bigpapituna.sharpears.engine.brief.ExportBlock
import com.bigpapituna.sharpears.engine.brief.ExportLayout
import com.bigpapituna.sharpears.engine.brief.ExportPage
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.Placed
import com.bigpapituna.sharpears.engine.brief.Spark
import com.bigpapituna.sharpears.engine.brief.TextKind
import com.bigpapituna.sharpears.engine.brief.TextMetrics
import com.bigpapituna.sharpears.engine.brief.TrendRow
import java.io.File
import java.time.LocalDate

/**
 * Draws a brief's document (engine/brief/ExportLayout.kt) on an Android Canvas: once on a tall bitmap for the PNG card, and
 * once per page on a PdfDocument for the PDF. The layout is the engine's; this only measures with real fonts and paints. The
 * colours are the app's warm light palette whatever the phone's theme, like the concert card, so a brief looks the same in
 * the chat it lands in.
 */
class BriefExport(private val context: Context) {
    private object Ink {
        const val PAPER = 0xFFFBF5EE.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val EDGE = 0xFFE38E5A.toInt()
        const val ACCENT = 0xFFA9521F.toInt()
        const val INK = 0xFF2B2420.toInt()
        const val DIM = 0xFF7A6D62.toInt()
        const val GOLD = 0xFFF2B84B.toInt()
        const val GOLD_SOFT = 0x55F2B84B
        const val GREEN = 0xFF2F7D4C.toInt()
        const val TRACK = 0xFFEADFD2.toInt()
        const val LINE = 0xFFD9CCBC.toInt()
    }

    private val geist = runCatching { ResourcesCompat.getFont(context, R.font.geist) }.getOrNull()
    private val nunito = runCatching { ResourcesCompat.getFont(context, R.font.nunito) }.getOrNull()
    private val mono = runCatching { ResourcesCompat.getFont(context, R.font.geist_mono) }.getOrNull()

    private fun paint(kind: TextKind): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = kind.size.toFloat()
        when (kind) {
            TextKind.TITLE -> { typeface = nunito; fontVariationSettings = "'wght' 800"; color = Ink.INK }
            TextKind.HEADLINE -> { typeface = nunito; fontVariationSettings = "'wght' 800"; color = Ink.INK }
            TextKind.SECTION -> { typeface = nunito; fontVariationSettings = "'wght' 800"; color = Ink.ACCENT }
            TextKind.ACTION -> { typeface = geist; fontVariationSettings = "'wght' 600"; color = Ink.ACCENT }
            TextKind.SMALL -> { typeface = geist; fontVariationSettings = "'wght' 400"; color = Ink.DIM }
            TextKind.BODY, TextKind.BULLET -> { typeface = geist; fontVariationSettings = "'wght' 400"; color = Ink.INK }
        }
    }

    private val paints = TextKind.entries.associateWith { paint(it) }
    val metrics = TextMetrics { text, kind -> paints.getValue(kind).measureText(text) }

    // ---- the PNG card ----

    /** The card for [blocks]: one tall bitmap, [ExportLayout.CARD_PX] wide. */
    fun card(blocks: List<ExportBlock>): Bitmap {
        val (placed, contentH) = ExportLayout.column(blocks, metrics)
        val h = ExportLayout.cardHeight(contentH)
        val bmp = Bitmap.createBitmap(ExportLayout.CARD_PX, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Ink.PAPER)
        // A thin accent frame, inset like a print (as the concert card has).
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Ink.EDGE }
        c.drawRoundRect(RectF(22f, 22f, ExportLayout.CARD_PX - 22f, h - 22f), 36f, 36f, frame)
        c.save(); c.translate(ExportLayout.MARGIN.toFloat(), ExportLayout.MARGIN.toFloat())
        ExportLayout.cardItems(placed, h).forEach { draw(c, it) }
        c.restore()
        return bmp
    }

    // ---- the PDF ----

    /** Writes the pages of [blocks] to [file] as a Letter PDF; returns the page count. */
    fun pdf(blocks: List<ExportBlock>, file: File): Int {
        // The one-minute read is a little over a page: shrunk onto one page rather than spilling a few lines onto a second.
        val (column, contentH) = ExportLayout.column(blocks, metrics)
        val fit = ExportLayout.fitOnePage(contentH)
        val pages = if (fit != null) listOf(ExportPage(column, contentH)) else ExportLayout.paginate(blocks, metrics)
        val shrink = fit ?: 1f
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { n, page -> drawPage(doc, page, n + 1, pages.size, shrink) }
            file.outputStream().use { doc.writeTo(it) }
        } finally { doc.close() }
        return pages.size
    }

    private fun drawPage(doc: PdfDocument, page: ExportPage, number: Int, of: Int, shrink: Float) {
        val p = doc.startPage(PdfDocument.PageInfo.Builder(ExportLayout.PDF_PAGE_W, ExportLayout.PDF_PAGE_H, number).create())
        val c = p.canvas
        c.drawColor(Ink.WHITE)
        c.save()
        c.translate(ExportLayout.PDF_MARGIN.toFloat(), ExportLayout.PDF_MARGIN.toFloat())
        c.scale(ExportLayout.PDF_SCALE * shrink, ExportLayout.PDF_SCALE * shrink)
        page.items.forEach { draw(c, it) }
        c.restore()
        if (of > 1) {
            val pg = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = geist; fontVariationSettings = "'wght' 400"; textSize = 9f; color = Ink.DIM; textAlign = Paint.Align.CENTER }
            c.drawText("$number of $of", ExportLayout.PDF_PAGE_W / 2f, ExportLayout.PDF_PAGE_H - 16f, pg)
        }
        doc.finishPage(p)
    }

    // ---- one block ----

    private fun draw(c: Canvas, item: Placed) {
        val y = item.y.toFloat()
        when (val b = item.block) {
            is ExportBlock.Header -> header(c, y, b.brand)
            is ExportBlock.Text -> text(c, y, b.kind, b.text, b.first)
            is ExportBlock.Strip -> strip(c, y, b.days, b.goalMinutes)
            is ExportBlock.Trends -> b.rows.forEach { trend(c, y, it) }
            is ExportBlock.Bars -> b.rows.forEach { bar(c, y, it) }
            ExportBlock.Rule -> c.drawRect(0f, y, ExportLayout.WIDTH.toFloat(), y + 2f, Paint().apply { color = Ink.LINE })
        }
    }

    private fun header(c: Canvas, y: Float, brand: String) {
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.GOLD_SOFT }
        val pupSize = ExportLayout.HEADER_H.toFloat()
        val cx = ExportLayout.WIDTH - pupSize / 2
        c.drawCircle(cx, y + pupSize / 2, pupSize * 0.5f, glow)
        drawRes(c, R.drawable.pup_great_job, RectF(cx - pupSize / 2, y, cx + pupSize / 2, y + pupSize))
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = nunito; fontVariationSettings = "'wght' 800"; textSize = 40f; color = Ink.ACCENT }
        c.drawText(brand, 0f, y + pupSize / 2 + 14f, p)
    }

    private fun text(c: Canvas, y: Float, kind: TextKind, s: String, first: Boolean) {
        val p = paints.getValue(kind)
        val baseline = y + kind.lineHeight * 0.72f
        if (kind == TextKind.BULLET) {
            // One dot per item, on its first line; the wrapped lines sit under its text (QA D3: a dot on every line).
            if (first) c.drawCircle(12f, y + kind.lineHeight * 0.52f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.EDGE })
            c.drawText(s, ExportLayout.BULLET_INDENT.toFloat(), baseline, p)
        } else if (kind == TextKind.ACTION) c.drawText(s, ExportLayout.BULLET_INDENT.toFloat(), baseline, p)
        else c.drawText(s, 0f, baseline, p)
    }

    private fun strip(c: Canvas, y: Float, days: List<DayStat>, goal: Int?) {
        if (days.isEmpty()) return
        val colW = ExportLayout.WIDTH / days.size.toFloat()
        val fractions = Spark.dayFractions(days, goal)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = geist; fontVariationSettings = "'wght' 500"; textSize = 26f; color = Ink.DIM; textAlign = Paint.Align.CENTER }
        val num = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 30f; color = Ink.INK; textAlign = Paint.Align.CENTER }
        days.forEachIndexed { i, d ->
            val cx = colW * i + colW / 2
            val practised = d.practiceMinutes > 0
            if (d.goal) {
                c.drawCircle(cx, y + 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.GOLD_SOFT })
                c.drawCircle(cx, y + 44f, 44f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = Ink.GOLD })
            }
            paw(c, cx, y + 44f, 56f, if (practised || d.rest) Ink.ACCENT else Ink.LINE, practised)
            // 2.9: a rest day, one missed day the streak kept: the outline paw with a small crescent moon.
            if (d.rest && !practised) {
                val moon = android.graphics.Path().apply {
                    addCircle(cx + 26f, y + 14f, 9f, android.graphics.Path.Direction.CW)
                    op(android.graphics.Path().apply { addCircle(cx + 30f, y + 11f, 7.5f, android.graphics.Path.Direction.CW) }, android.graphics.Path.Op.DIFFERENCE)
                }
                c.drawPath(moon, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.ACCENT })
            }
            c.drawText("${d.practiceMinutes}", cx, y + 118f, num)
            val track = RectF(cx - 24f, y + 134f, cx + 24f, y + 142f)
            c.drawRoundRect(track, 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.TRACK })
            if (fractions[i] > 0f) c.drawRoundRect(RectF(track.left, track.top, track.left + track.width() * fractions[i], track.bottom), 4f, 4f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.EDGE })
            c.drawText(BriefFormat.dayShort(d.date), cx, y + 180f, label)
        }
    }

    /** A paw print centred on ([cx], [cy]), [s] wide: a pad and four toes. */
    private fun paw(c: Canvas, cx: Float, cy: Float, s: Float, colour: Int, filled: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; style = if (filled) Paint.Style.FILL else Paint.Style.STROKE; strokeWidth = s * 0.08f }
        val x0 = cx - s / 2; val y0 = cy - s / 2
        c.drawOval(RectF(x0 + s * 0.24f, y0 + s * 0.44f, x0 + s * 0.76f, y0 + s * 0.88f), p)
        listOf(0.16f to 0.40f, 0.36f to 0.20f, 0.64f to 0.20f, 0.84f to 0.40f).forEach { (x, y) -> c.drawCircle(x0 + s * x, y0 + s * y, s * 0.11f, p) }
    }

    private fun trend(c: Canvas, y: Float, row: TrendRow) {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = geist; fontVariationSettings = "'wght' 500"; textSize = 30f; color = Ink.INK }
        val sub = paints.getValue(TextKind.SMALL)
        c.drawText(row.label, 0f, y + 36f, label)
        c.drawText(row.summary, 0f, y + 72f, sub)
        val box = RectF(ExportLayout.WIDTH - 320f, y + 8f, ExportLayout.WIDTH.toFloat(), y + 84f)
        val pts = Spark.points(row.points, box.width(), box.height(), pad = 10f)
        val measured = row.points.filterNotNull()
        val rising = measured.size >= 2 && measured.last() > measured.first()
        val falling = measured.size >= 2 && measured.last() < measured.first()
        val better = (rising && row.upIsGood) || (falling && !row.upIsGood)
        val colour = when { measured.size < 2 || measured.last() == measured.first() -> Ink.DIM; better -> Ink.GREEN; else -> Ink.ACCENT }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = colour; style = Paint.Style.STROKE; strokeWidth = 5f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = colour }
        for (run in Spark.runs(pts)) if (run.size >= 2) {
            val path = Path()
            run.forEachIndexed { i, p -> if (i == 0) path.moveTo(box.left + p.x, box.top + p.y) else path.lineTo(box.left + p.x, box.top + p.y) }
            c.drawPath(path, line)
        }
        val last = pts.lastOrNull { it != null }
        pts.forEach { p -> if (p != null) c.drawCircle(box.left + p.x, box.top + p.y, if (p === last) 9f else 5f, dot) }
    }

    private fun bar(c: Canvas, y: Float, row: BarRow) {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = geist; fontVariationSettings = "'wght' 400"; textSize = 30f; color = Ink.INK }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 28f; color = Ink.DIM; textAlign = Paint.Align.RIGHT }
        c.drawText(row.label, 0f, y + 30f, label)
        c.drawText(row.value, ExportLayout.WIDTH.toFloat(), y + 30f, value)
        val track = RectF(0f, y + 40f, ExportLayout.WIDTH.toFloat(), y + 50f)
        c.drawRoundRect(track, 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.TRACK })
        if (row.share > 0f) c.drawRoundRect(RectF(0f, track.top, track.width() * row.share, track.bottom), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ink.EDGE })
    }

    private fun drawRes(c: Canvas, res: Int, box: RectF) {
        val img = BitmapFactory.decodeResource(context.resources, res) ?: return
        val scale = minOf(box.width() / img.width, box.height() / img.height)
        val w = img.width * scale; val h = img.height * scale
        c.drawBitmap(img, null, RectF(box.centerX() - w / 2, box.centerY() - h / 2, box.centerX() + w / 2, box.centerY() + h / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        img.recycle()
    }

    companion object {
        /**
         * Writes "<name>.png" and "<name>.pdf" for [blocks] into a fresh cache/share and returns them (the PNG first). Call
         * off the main thread; [Share.files] hands them to the share sheet, started by the grown-up pressing Send.
         */
        fun pack(context: Context, blocks: List<ExportBlock>, name: String, period: Period, day: LocalDate = LocalDate.now()): List<File> {
            val dir = Share.fresh(context)
            val (pngName, pdfName) = ExportLayout.fileNames(name, period, day)
            val x = BriefExport(context)
            val card = x.card(blocks)
            val png = File(dir, pngName).also { f -> f.outputStream().use { card.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            card.recycle()
            val pdf = File(dir, pdfName)
            val pages = x.pdf(blocks, pdf)
            // The files are named after the person; the log never is (crash reports carry recent logcat).
            Log.d(TAG, "brief: exported a ${period.name} brief: png (${png.length() / 1024} KB) and pdf (${pages} page${if (pages == 1) "" else "s"}, ${pdf.length() / 1024} KB)")
            return listOf(png, pdf)
        }
    }
}
