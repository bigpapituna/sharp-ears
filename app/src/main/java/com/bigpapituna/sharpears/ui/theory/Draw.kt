package com.bigpapituna.sharpears.ui.theory

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.ui.Geist
import com.bigpapituna.sharpears.ui.GeistMono
import com.bigpapituna.sharpears.ui.LeftHandColor
import com.bigpapituna.sharpears.ui.Nunito
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Theory Academy's drawing kit: the colours of the stage (the one raised panel a scene is drawn on), text drawn
 * straight onto a Canvas, a compact piano, and the music glyphs (clefs, notes, rests, sharps and flats) built
 * from paths, so nothing depends on a music font being on the phone.
 */

/** Colours and text for a scene. Built once per theme; text layouts are cached by text, style and size. */
internal class StageInk(
    val ink: Color, val dim: Color, val faint: Color, val line: Color, val accent: Color, val onAccent: Color,
    val accentSoft: Color, val blue: Color, val paper: Color, private val measurer: TextMeasurer, val density: Density,
    /** Colour notes are on (kids, or the grown-ups' setting): lit keys and falling notes take their key's colour. */
    val colourNotes: Boolean = false,
) {
    private val cache = HashMap<String, TextLayoutResult>()

    fun layout(text: String, kind: TextKind, size: TextUnit): TextLayoutResult = cache.getOrPut("$kind|${size.value}|$text") {
        measurer.measure(text, kind.style.copy(fontSize = size))
    }
}

internal enum class TextKind(val style: TextStyle) {
    LABEL(TextStyle(fontFamily = Geist, fontWeight = FontWeight.SemiBold)),
    BODY(TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium)),
    TITLE(TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold)),
    NUMBER(TextStyle(fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")),
}

@Composable
internal fun rememberStageInk(): StageInk {
    val c = MaterialTheme.colorScheme
    val m = rememberTextMeasurer(cacheSize = 64)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val colourNotes = com.bigpapituna.sharpears.ui.skin.colourNotesOn()
    return remember(c, m, density, colourNotes) {
        StageInk(
            ink = c.onSurface, dim = c.onSurfaceVariant, faint = c.outlineVariant, line = c.onSurface.copy(alpha = 0.78f),
            accent = c.primary, onAccent = c.onPrimary, accentSoft = c.primaryContainer, blue = LeftHandColor,
            paper = c.surfaceContainerHigh, measurer = m, density = density, colourNotes = colourNotes,
        )
    }
}

/** The grey of a context key (Tint.DIM): it stays grey with colour notes on. */
internal val DimKey = Color(0xFF9A8F85)

/**
 * The colour a scene paints [midi] that it lights in [lit]: with colour notes on, the key's own colour (engine/skin
 * NoteColours, a black key the blend of its neighbours), as on the player's lane and keys, at [lit]'s alpha; else [lit].
 * (2.2.1: the concert hall said "C is a red cat and D an orange dog" over keys all lit in the accent.)
 */
internal fun sceneNoteColour(midi: Int, lit: Color, colourNotes: Boolean): Color =
    if (!colourNotes || lit.copy(alpha = 1f) == DimKey) lit
    else Color(com.bigpapituna.sharpears.engine.skin.NoteColours.colour(midi)).copy(alpha = lit.alpha)

/** Draws [text] centred on [at] (or with its top-left there when [centred] is false), optionally scaled about its centre. */
internal fun DrawScope.text(
    ink: StageInk, text: String, at: Offset, kind: TextKind = TextKind.LABEL, size: TextUnit = 13.sp, color: Color = ink.ink,
    alpha: Float = 1f, scale: Float = 1f, centred: Boolean = true,
): Size {
    if (text.isEmpty() || alpha <= 0.01f) return Size.Zero
    val l = ink.layout(text, kind, size)
    val w = l.size.width.toFloat(); val h = l.size.height.toFloat()
    val topLeft = if (centred) Offset(at.x - w / 2, at.y - h / 2) else at
    if (scale == 1f) drawText(l, color, topLeft, alpha = alpha.coerceIn(0f, 1f))
    else scale(scale, Offset(topLeft.x + w / 2, topLeft.y + h / 2)) { drawText(l, color, topLeft, alpha = alpha.coerceIn(0f, 1f)) }
    return Size(w, h)
}

internal fun DrawScope.textWidth(ink: StageInk, text: String, kind: TextKind, size: TextUnit) = ink.layout(text, kind, size).size.width.toFloat()

/** Pixels to sp for text sized to the drawing. */
internal fun DrawScope.pxSp(px: Float) = (px / density / fontScale).sp

// ---- Easing ------------------------------------------------------------------------------------------------------

internal fun smooth(x: Float) = x.coerceIn(0f, 1f).let { it * it * (3 - 2 * it) }
internal fun outCubic(x: Float) = 1 - (1 - x.coerceIn(0f, 1f)).let { it * it * it }
/** Overshoots a little past 1 and settles: a pop. */
internal fun backOut(x: Float): Float { val t = x.coerceIn(0f, 1f) - 1; return 1 + 2.7f * t * t * t + 1.7f * t * t }
/** 1 at the moment of a strike, fading to 0 over [ms]. */
internal fun flash(t: Int, at: Int, ms: Int = 450) = if (t < at || t > at + ms) 0f else 1f - (t - at).toFloat() / ms

// ---- The compact piano -------------------------------------------------------------------------------------------

private val WhiteTop = Color(0xFFFFFDF9)
private val WhiteKey = Color(0xFFF5F1EA)
private val WhiteLow = Color(0xFFE6E0D6)
private val WhiteLip = Color(0xFFBFB6A9)
private val BlackTop = Color(0xFF3F3833)
private val BlackKey = Color(0xFF141110)
private val BlackFront = Color(0xFF4F4740)
private val KeyEdge = Color(0xFF2A2420)
private val KeyLabel = Color(0xFF6B6058)

/**
 * A piano drawn into the scene at [origin] with the geometry of [l] (the same layout the real keyboard uses).
 * [lit] gives a key's colour (null: plain), [sink] how far it is pressed (0..1), [name] the text on it.
 */
internal fun DrawScope.drawPiano(
    l: KeyboardLayout, origin: Offset, ink: StageInk, lit: (Int) -> Color?, sink: (Int) -> Float = { 0f }, name: (Int) -> String? = { null },
) {
    val keyH = l.height
    val r = CornerRadius(l.whiteWidth * 0.12f)
    drawRoundRect(KeyEdge, origin - Offset(3f, 3f), Size(l.width + 6f, keyH + 6f), CornerRadius(l.whiteWidth * 0.18f))
    val body = Brush.verticalGradient(listOf(WhiteTop, WhiteKey, WhiteLow), startY = origin.y, endY = origin.y + keyH)
    val labelSize = pxSp((l.whiteWidth * 0.36f).coerceIn(9f * density, 15f * density))
    for (k in l.whiteKeys) {
        val c = lit(k.midi)?.let { sceneNoteColour(k.midi, it, ink.colourNotes) }
        val press = sink(k.midi).coerceIn(0f, 1f)
        val x = origin.x + k.left + 1.2f; val w = k.right - k.left - 2.4f
        val lip = keyH * (0.065f - 0.035f * press)
        val down = keyH * 0.018f * press
        drawRoundRect(if (c != null) lerp(WhiteLip, c.darken(0.6f), c.alpha) else WhiteLip, Offset(x, origin.y), Size(w, keyH), r)
        drawRoundRect(body, Offset(x, origin.y + down), Size(w, keyH - lip - down), r)
        if (c != null) drawRoundRect(Brush.verticalGradient(listOf(c.copy(alpha = c.alpha * 0.75f), c), startY = origin.y, endY = origin.y + keyH),
            Offset(x, origin.y + down), Size(w, keyH - lip - down), r)
        name(k.midi)?.let { t ->
            text(ink, t, Offset(x + w / 2, origin.y + keyH - lip - keyH * 0.13f), TextKind.LABEL, labelSize,
                color = if (c != null && c.alpha > 0.5f) Color.White else KeyLabel)
        }
    }
    drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), startY = origin.y, endY = origin.y + keyH * 0.07f),
        origin, Size(l.width, keyH * 0.07f))
    for (k in l.blackKeys) {
        val c = lit(k.midi)?.let { sceneNoteColour(k.midi, it, ink.colourNotes) }
        val press = sink(k.midi).coerceIn(0f, 1f)
        val x = origin.x + k.left; val w = k.right - k.left; val h = k.bottom - k.top
        val br = CornerRadius(w * 0.16f)
        drawRoundRect(Color.Black.copy(alpha = 0.32f), Offset(x + w * 0.12f, origin.y), Size(w, h * 1.05f), br)
        val front = h * (0.12f - 0.06f * press)
        val down = h * 0.02f * press
        drawRoundRect(if (c != null) lerp(BlackFront, c.darken(0.5f), c.alpha) else BlackFront, Offset(x, origin.y), Size(w, h), br)
        drawRoundRect(Brush.verticalGradient(listOf(BlackTop, BlackKey), startY = origin.y, endY = origin.y + h), Offset(x, origin.y + down), Size(w, h - front - down), br)
        if (c != null) drawRoundRect(Brush.verticalGradient(listOf(c.lighten(0.2f), c.darken(0.75f)), startY = origin.y, endY = origin.y + h),
            Offset(x, origin.y + down), Size(w, h - front - down), br, alpha = c.alpha)
        drawRoundRect(Color.White.copy(alpha = 0.10f), Offset(x + w * 0.18f, origin.y + h * 0.05f), Size(w * 0.64f, h * 0.28f), CornerRadius(w * 0.2f))
        name(k.midi)?.let { t ->
            text(ink, t, Offset(x + w / 2, origin.y + h - front - h * 0.16f), TextKind.LABEL, pxSp((w * 0.42f).coerceIn(8f * density, 13f * density)),
                color = Color.White, alpha = if (c != null) 1f else 0.75f)
        }
    }
}

/** A keyboard laid out to fit [width] x at most [maxHeight], keys roughly in piano proportions. */
internal fun pianoFor(low: Int, high: Int, width: Float, maxHeight: Float): KeyboardLayout {
    val whites = (low..high).count { !com.bigpapituna.sharpears.engine.isBlack(it) }
    val h = minOf(maxHeight, width / whites * 4.6f)
    return KeyboardLayout(low, high, width, h)
}

internal fun Color.darken(f: Float) = Color(red * f, green * f, blue * f, 1f)
internal fun Color.lighten(f: Float) = Color(red + (1 - red) * f, green + (1 - green) * f, blue + (1 - blue) * f, 1f)

// ---- Music glyphs ------------------------------------------------------------------------------------------------

/** A path of the treble clef in staff spaces: x from the clef's left, y down from the top line (the G line is y = 3). */
private val TrebleClef: (Float, Offset) -> Path = { s, o ->
    fun X(x: Float) = o.x + x * s
    fun Y(y: Float) = o.y + y * s
    Path().apply {
        moveTo(X(1.15f), Y(3.05f))
        cubicTo(X(0.75f), Y(3.1f), X(0.7f), Y(2.4f), X(1.2f), Y(2.3f))
        cubicTo(X(1.85f), Y(2.2f), X(2.1f), Y(3.1f), X(1.75f), Y(3.6f))
        cubicTo(X(1.35f), Y(4.15f), X(0.3f), Y(4.0f), X(0.2f), Y(3.1f))
        cubicTo(X(0.1f), Y(2.2f), X(0.95f), Y(1.55f), X(1.5f), Y(0.9f))
        cubicTo(X(2.0f), Y(0.3f), X(2.1f), Y(-1.0f), X(1.75f), Y(-1.7f))
        cubicTo(X(1.55f), Y(-2.1f), X(1.1f), Y(-1.6f), X(1.05f), Y(-0.9f))
        lineTo(X(1.6f), Y(5.0f))
        cubicTo(X(1.7f), Y(5.6f), X(1.15f), Y(6.0f), X(0.75f), Y(5.6f))
    }
}

private val BassClef: (Float, Offset) -> Path = { s, o ->
    fun X(x: Float) = o.x + x * s
    fun Y(y: Float) = o.y + y * s
    Path().apply {
        moveTo(X(0.35f), Y(1.0f))
        cubicTo(X(0.35f), Y(0.05f), X(1.75f), Y(-0.25f), X(1.8f), Y(0.95f))
        cubicTo(X(1.85f), Y(2.3f), X(1.0f), Y(3.3f), X(0.1f), Y(4.0f))
    }
}

/**
 * A clef traced [progress] of the way (0..1), its left edge at [x], [top] the staff's top line, [s] one space.
 * The treble clef's dot and the bass clef's head and dots appear at the end of the stroke.
 */
internal fun DrawScope.drawClef(treble: Boolean, x: Float, top: Float, s: Float, color: Color, progress: Float) {
    if (progress <= 0f) return
    val full = if (treble) TrebleClef(s, Offset(x, top)) else BassClef(s, Offset(x, top))
    val path = if (progress >= 1f) full else Path().also { dst ->
        val pm = PathMeasure(); pm.setPath(full, false)
        pm.getSegment(0f, pm.length * progress, dst, true)
    }
    drawPath(path, color, style = Stroke(width = s * (if (treble) 0.2f else 0.26f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    if (treble) {
        if (progress > 0.96f) drawCircle(color, s * 0.3f * ((progress - 0.96f) / 0.04f), Offset(x + 0.8f * s, top + 5.45f * s))
    } else {
        drawCircle(color, s * 0.36f * minOf(1f, progress * 8), Offset(x + 0.38f * s, top + 1.0f * s))
        val dots = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f)
        drawCircle(color, s * 0.17f * dots, Offset(x + 2.35f * s, top + 0.5f * s))
        drawCircle(color, s * 0.17f * dots, Offset(x + 2.35f * s, top + 1.5f * s))
    }
}

/** Adds an ellipse (as a polygon, so it can turn) to [p]. */
private fun ellipse(p: Path, c: Offset, rx: Float, ry: Float, deg: Float) {
    val a = Math.toRadians(deg.toDouble())
    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
    val n = 32
    for (i in 0..n) {
        val th = 2 * Math.PI * i / n
        val ex = rx * cos(th).toFloat(); val ey = ry * sin(th).toFloat()
        val px = c.x + ex * ca - ey * sa; val py = c.y + ex * sa + ey * ca
        if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
    }
    p.close()
}

/** Half the width of a note head of [value], in spaces. */
internal fun headRx(value: NoteValue) = if (value == NoteValue.WHOLE) 0.78f else 0.62f

/** A note head (hollow for whole and half notes). */
internal fun DrawScope.drawHead(c: Offset, s: Float, value: NoteValue, color: Color, scale: Float = 1f) {
    val p = Path().apply { fillType = PathFillType.EvenOdd }
    when (value) {
        NoteValue.WHOLE -> { ellipse(p, c, 0.78f * s * scale, 0.5f * s * scale, 0f); ellipse(p, c, 0.4f * s * scale, 0.26f * s * scale, -55f) }
        NoteValue.HALF -> { ellipse(p, c, 0.62f * s * scale, 0.43f * s * scale, -20f); ellipse(p, c, 0.5f * s * scale, 0.17f * s * scale, -30f) }
        else -> ellipse(p, c, 0.62f * s * scale, 0.43f * s * scale, -20f)
    }
    drawPath(p, color)
}

/**
 * A stem from the head at [c] (up: on the right going up; down: on the left going down) with [value]'s flags,
 * spanning [extra] more spaces (for chords). Returns nothing; whole notes have no stem.
 */
internal fun DrawScope.drawStem(c: Offset, s: Float, value: NoteValue, up: Boolean, color: Color, extra: Float = 0f) {
    if (value == NoteValue.WHOLE) return
    val rx = headRx(value) * s * 0.93f
    val x = if (up) c.x + rx else c.x - rx
    val len = (3.3f + extra) * s
    val y0 = c.y + (if (up) -0.15f else 0.15f) * s
    val y1 = if (up) c.y - len else c.y + len
    drawLine(color, Offset(x, y0), Offset(x, y1), strokeWidth = 0.12f * s, cap = StrokeCap.Butt)
    val flags = when (value) { NoteValue.EIGHTH -> 1; NoteValue.SIXTEENTH -> 2; else -> 0 }
    repeat(flags) { i ->
        val d = if (up) 1f else -1f
        val y = y1 + d * i * 0.8f * s
        val f = Path().apply {
            moveTo(x, y)
            cubicTo(x + 0.1f * s, y + d * 1.0f * s, x + 1.2f * s, y + d * 1.3f * s, x + 0.8f * s, y + d * 2.5f * s)
            cubicTo(x + 0.95f * s, y + d * 1.6f * s, x + 0.25f * s, y + d * 1.35f * s, x, y + d * 1.05f * s)
            close()
        }
        drawPath(f, color)
    }
}

/** A sharp centred on [c]. */
internal fun DrawScope.drawSharp(c: Offset, s: Float, color: Color, scale: Float = 1f) {
    val k = s * scale
    drawLine(color, Offset(c.x - 0.2f * k, c.y - 1.25f * k), Offset(c.x - 0.2f * k, c.y + 1.35f * k), 0.1f * k)
    drawLine(color, Offset(c.x + 0.2f * k, c.y - 1.35f * k), Offset(c.x + 0.2f * k, c.y + 1.25f * k), 0.1f * k)
    drawLine(color, Offset(c.x - 0.48f * k, c.y - 0.3f * k), Offset(c.x + 0.48f * k, c.y - 0.55f * k), 0.24f * k)
    drawLine(color, Offset(c.x - 0.48f * k, c.y + 0.5f * k), Offset(c.x + 0.48f * k, c.y + 0.25f * k), 0.24f * k)
}

/** A flat with its bowl centred on [c]. */
internal fun DrawScope.drawFlat(c: Offset, s: Float, color: Color, scale: Float = 1f) {
    val k = s * scale
    drawLine(color, Offset(c.x - 0.3f * k, c.y - 1.9f * k), Offset(c.x - 0.3f * k, c.y + 0.5f * k), 0.11f * k)
    val bowl = Path().apply {
        moveTo(c.x - 0.3f * k, c.y - 0.15f * k)
        cubicTo(c.x + 0.1f * k, c.y - 0.75f * k, c.x + 0.8f * k, c.y - 0.55f * k, c.x + 0.42f * k, c.y - 0.02f * k)
        cubicTo(c.x + 0.2f * k, c.y + 0.25f * k, c.x - 0.1f * k, c.y + 0.42f * k, c.x - 0.3f * k, c.y + 0.5f * k)
    }
    drawPath(bowl, color, style = Stroke(0.17f * k, cap = StrokeCap.Round))
}

/** A natural sign centred on [c]. */
internal fun DrawScope.drawNatural(c: Offset, s: Float, color: Color) {
    drawLine(color, Offset(c.x - 0.25f * s, c.y - 1.3f * s), Offset(c.x - 0.25f * s, c.y + 0.55f * s), 0.1f * s)
    drawLine(color, Offset(c.x + 0.25f * s, c.y - 0.55f * s), Offset(c.x + 0.25f * s, c.y + 1.3f * s), 0.1f * s)
    drawLine(color, Offset(c.x - 0.25f * s, c.y - 0.3f * s), Offset(c.x + 0.25f * s, c.y - 0.5f * s), 0.22f * s)
    drawLine(color, Offset(c.x - 0.25f * s, c.y + 0.5f * s), Offset(c.x + 0.25f * s, c.y + 0.3f * s), 0.22f * s)
}

/**
 * A rest of [value] centred at [x]; [mid] is the staff's middle line and [s] a space. Whole rests hang from the
 * fourth line, half rests sit on the middle one.
 */
internal fun DrawScope.drawRest(value: NoteValue, x: Float, mid: Float, s: Float, color: Color, scale: Float = 1f) {
    val k = s * scale
    when (value) {
        NoteValue.WHOLE -> drawRect(color, Offset(x - 0.6f * s, mid - s), Size(1.2f * s, 0.5f * s * scale))
        NoteValue.HALF -> drawRect(color, Offset(x - 0.6f * s, mid - 0.5f * s * scale), Size(1.2f * s, 0.5f * s * scale))
        NoteValue.QUARTER -> {
            val p = Path().apply {
                moveTo(x - 0.3f * k, mid - 1.45f * k); lineTo(x + 0.3f * k, mid - 0.75f * k); lineTo(x - 0.22f * k, mid - 0.15f * k)
                lineTo(x + 0.32f * k, mid + 0.5f * k)
                quadraticTo(x - 0.45f * k, mid + 0.35f * k, x + 0.02f * k, mid + 1.3f * k)
            }
            drawPath(p, color, style = Stroke(0.24f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        else -> {
            val hooks = if (value == NoteValue.EIGHTH) 1 else 2
            val top = mid - 0.9f * k
            drawLine(color, Offset(x + 0.45f * k, top), Offset(x - 0.15f * k, mid + (if (hooks == 2) 1.6f else 1.0f) * k), 0.13f * k, cap = StrokeCap.Round)
            repeat(hooks) { i ->
                val y = top + i * 0.85f * k
                drawCircle(color, 0.24f * k, Offset(x - 0.3f * k, y + 0.15f * k))
                drawLine(color, Offset(x - 0.3f * k, y + 0.32f * k), Offset(x + 0.45f * k - i * 0.25f * k, y), 0.12f * k, cap = StrokeCap.Round)
            }
        }
    }
}

/** One note glyph standing alone (for the value tree): head at [c], stem up. */
internal fun DrawScope.drawLoneNote(value: NoteValue, c: Offset, s: Float, color: Color, scale: Float = 1f) {
    drawHead(c, s * scale, value, color)
    drawStem(c, s * scale, value, up = true, color = color)
}
