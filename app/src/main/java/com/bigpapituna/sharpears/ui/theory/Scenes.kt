package com.bigpapituna.sharpears.ui.theory

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bigpapituna.sharpears.engine.KeyboardLayout
import com.bigpapituna.sharpears.engine.isBlack
import com.bigpapituna.sharpears.engine.theory.CIRCLE_MAJORS
import com.bigpapituna.sharpears.engine.theory.CIRCLE_MINORS
import com.bigpapituna.sharpears.engine.theory.ArticulationVisual
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.engine.theory.DynamicsVisual
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import com.bigpapituna.sharpears.engine.theory.HandShapeVisual
import com.bigpapituna.sharpears.engine.theory.LaneVisual
import com.bigpapituna.sharpears.engine.theory.MidiVisual
import com.bigpapituna.sharpears.engine.theory.PedalVisual
import com.bigpapituna.sharpears.engine.theory.PhraseVisual
import com.bigpapituna.sharpears.engine.theory.PostureVisual
import com.bigpapituna.sharpears.engine.theory.SongMapVisual
import com.bigpapituna.sharpears.engine.theory.CircleVisual
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.IntervalVisual
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.Marks
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.Pitch
import com.bigpapituna.sharpears.engine.theory.PopVisual
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.StaffNote
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.theory.ValueTreeVisual
import com.bigpapituna.sharpears.engine.theory.Visual
import com.bigpapituna.sharpears.engine.theory.keyLabel
import com.bigpapituna.sharpears.engine.theory.keySignature
import com.bigpapituna.sharpears.engine.theory.keySignaturePositions
import com.bigpapituna.sharpears.engine.theory.ledgerLines
import com.bigpapituna.sharpears.engine.theory.lineNames
import com.bigpapituna.sharpears.engine.theory.progress
import com.bigpapituna.sharpears.engine.theory.spaceNames
import com.bigpapituna.sharpears.engine.theory.staffPosition
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws [visual] as it is [time] ms into its animation. [time] is read only while drawing, so the clock ticking
 * redraws the Canvas without recomposing anything. [description] is what TalkBack reads for the picture.
 */
@Composable
internal fun SceneCanvas(visual: Visual, time: () -> Int, modifier: Modifier = Modifier, description: String = "") {
    val ink = rememberStageInk()
    val hands = com.bigpapituna.sharpears.ui.motion.rememberHandFrames()
    val kid = com.bigpapituna.sharpears.ui.family.profile().isKid
    Canvas(modifier.semantics { if (description.isNotEmpty()) contentDescription = description }) {
        val t = time()
        when (visual) {
            is KeysVisual -> keysScene(visual, t, ink)
            is StaffVisual -> staffScene(visual, t, ink)
            is RhythmVisual -> rhythmScene(visual, t, ink, kid)
            is IntervalVisual -> intervalScene(visual, t, ink)
            is CircleVisual -> circleScene(visual, t, ink)
            is ChordVisual -> chordScene(visual, t, ink)
            is ValueTreeVisual -> valueTreeScene(visual, t, ink)
            is PopVisual -> popScene(visual, t, ink)
            is PostureVisual -> postureScene(visual, t, ink)
            is com.bigpapituna.sharpears.engine.theory.ArmVisual -> armScene(visual, t, ink)
            is HandShapeVisual -> handShapeScene(visual, t, ink, hands.value)
            is FingerNumbersVisual -> fingerNumbersScene(visual, t, ink, hands.value)
            is PedalVisual -> pedalScene(visual, t, ink)
            is DynamicsVisual -> dynamicsScene(visual, t, ink)
            is ArticulationVisual -> articulationScene(visual, t, ink)
            is PhraseVisual -> phraseScene(visual, t, ink)
            is LaneVisual -> laneScene(visual, t, ink)
            is SongMapVisual -> songMapScene(visual, t, ink)
            is MidiVisual -> midiScene(visual, t, ink)
        }
    }
}

internal fun tint(t: Tint, ink: StageInk) = when (t) { Tint.ACCENT -> ink.accent; Tint.BLUE -> ink.blue; Tint.GREEN -> com.bigpapituna.sharpears.ui.Good; Tint.DIM -> DimKey }

/** How far a struck key is pressed [u] ms after the strike: down fast, back up slowly. */
internal fun press(u: Int) = when { u < 0 || u > 380 -> 0f; u < 70 -> u / 70f; else -> 1f - (u - 70) / 310f }

internal fun centerX(l: KeyboardLayout, midi: Int) = l.rect(midi).centerX

// ---- Keys --------------------------------------------------------------------------------------------------------

private fun DrawScope.keysScene(v: KeysVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.5f)
    // The keys and the room their labels need above them, centred together on the stage.
    val labelRoom = (if (v.frames.any { it.gap != null }) 104 else 60).dp.toPx()
    val top = minOf(size.height - l.height - pad, (size.height - labelRoom - l.height) / 2 + labelRoom)
    val o = Offset(pad, top)
    val cur = v.current(t)
    val first = if (cur < 0) 0 else (0..cur).lastOrNull { v.frames[it].clear } ?: 0
    val run = if (cur < 0) IntRange.EMPTY else first..cur
    val colors = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>(); val names = HashMap<Int, String>()
    val finale = if (v.finale.isNotEmpty()) press(t - v.finaleAt) else 0f
    for (i in run) {
        val f = v.frames[i]; val at = v.frameAt(i)
        val on = smooth(progress(t, at, 140))
        val older = if (i < cur) 1f - 0.4f * smooth(progress(t, v.frameAt(i + 1), 300)) else 1f
        val c = tint(f.tint, ink)
        for (n in f.notes) {
            colors[n] = c.copy(alpha = on * older)
            sinks[n] = maxOf(press(t - at), if (n in v.finale) finale else 0f)
            f.names[n]?.let { names[n] = it }
        }
    }
    drawPiano(l, o, ink, lit = { colors[it] }, sink = { sinks[it] ?: 0f }, name = { m ->
        names[m] ?: when (v.names) {
            KeyNames.LIT -> if (m in colors) keyLabel(m, v.flats) else null
            KeyNames.WHITES -> if (!isBlack(m)) keyLabel(m) else null
            KeyNames.C_ONLY -> if (m % 12 == 0) "C" else null
            KeyNames.NONE -> null
        }
    })
    // Step brackets between consecutive frames: an arc that draws itself, then its W or H pops up.
    val hasGaps = v.frames.any { it.gap != null }
    for (i in run) {
        val f = v.frames[i]
        val g = f.gap ?: continue
        if (i == first) continue
        val from = v.frames[i - 1].notes.max(); val to = f.notes.min()
        val x0 = o.x + centerX(l, from); val x1 = o.x + centerX(l, to)
        val y = top - 6.dp.toPx()
        val hgt = 16.dp.toPx() + abs(x1 - x0) * 0.12f
        val c = when (g) { "H" -> ink.accent; "W" -> ink.ink; else -> ink.blue }
        val path = Path().apply { moveTo(x0, y); quadraticTo((x0 + x1) / 2, y - hgt * 2, x1, y) }
        val e = outCubic(progress(t, v.frameAt(i), 280))
        drawPath(trim(path, e), c, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val pop = progress(t, v.frameAt(i) + 140, 320)
        if (pop > 0f) text(ink, g, Offset((x0 + x1) / 2, y - hgt - 12.dp.toPx()), TextKind.TITLE, 18.sp, c, alpha = smooth(pop * 2), scale = backOut(pop))
    }
    // The newest frame's label above its keys; the one before fades out.
    val labelled = run.filter { v.frames[it].label != null }
    val labelY = top - (if (hasGaps) 78 else 34).dp.toPx()
    labelled.takeLast(2).forEach { i ->
        val f = v.frames[i]
        val newest = i == labelled.last()
        val x = f.notes.map { o.x + centerX(l, it) }.average().toFloat()
        val w = textWidth(ink, f.label!!, TextKind.TITLE, 22.sp)
        val cx = x.coerceIn(pad + w / 2, size.width - pad - w / 2)
        val pop = progress(t, v.frameAt(i), 360)
        val fade = if (newest) 1f else 1f - smooth(progress(t, v.frameAt(labelled.last()), 250))
        text(ink, f.label, Offset(cx, labelY), TextKind.TITLE, 22.sp, tint(f.tint, ink).let { if (f.tint == Tint.DIM) ink.dim else it },
            alpha = smooth(pop * 2) * fade, scale = 0.7f + 0.3f * backOut(pop))
        // A small tick from the label down toward its keys.
        if (fade > 0.5f && pop > 0.5f) drawLine(ink.faint, Offset(x, labelY + 16.dp.toPx()), Offset(x, labelY + 26.dp.toPx()), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** The first [e] (0..1) of [path]. */
internal fun trim(path: Path, e: Float): Path {
    if (e >= 1f) return path
    val pm = PathMeasure(); pm.setPath(path, false)
    return Path().also { pm.getSegment(0f, pm.length * e.coerceIn(0f, 1f), it, true) }
}

// ---- Staff -------------------------------------------------------------------------------------------------------

/** One stave's geometry: [bottom] is the y of its bottom line; [s] one space. */
internal class Stave(val clef: Clef, val bottom: Float, val s: Float) {
    val top get() = bottom - 4 * s
    fun y(pos: Int) = bottom - pos * s / 2
    val mid get() = y(4)
}

private fun DrawScope.staffScene(v: StaffVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    val marksW = if (v.marks != Marks.NONE) 30.dp.toPx() else 0f
    val s = if (v.grand) minOf(size.height / 17f, 15.dp.toPx(), (size.width - 2 * pad) / 18f)
        else minOf(size.height / 12.5f, 24.dp.toPx(), (size.width - 2 * pad - marksW) / 17f)
    val staves = if (v.grand) {
        val gap = 3.6f * s
        val trebleBottom = size.height / 2 - gap / 2
        listOf(Stave(Clef.TREBLE, trebleBottom, s), Stave(Clef.BASS, trebleBottom + gap + 4 * s, s))
    } else listOf(Stave(v.clef, size.height * 0.5f + 2 * s - (if (v.notes.any { it.label != null } || v.names) s else 0f), s))
    val left = pad
    val right = size.width - pad - marksW
    // Lines sweep in from the left, one after another.
    staves.forEach { st ->
        for (i in 0..4) {
            val e = outCubic(progress(t, i * 70, 420))
            if (e <= 0f) continue
            drawLine(ink.line, Offset(left, st.y(i * 2)), Offset(left + (right - left) * e, st.y(i * 2)), 0.09f * s)
        }
    }
    if (v.grand) {
        val e = smooth(progress(t, 200, 400))
        drawLine(ink.line, Offset(left, staves[0].top), Offset(left, staves[0].top + (staves[1].bottom - staves[0].top) * e), 0.14f * s)
    }
    // The clef traces itself.
    val clefP = if (v.drawClef) progress(t, v.clefStart, v.clefMs) else 1f
    staves.forEach { st -> drawClef(st.clef == Clef.TREBLE, left + 0.35f * s, st.top, s, ink.ink, clefP) }
    var x = left + 3.3f * s
    // Marks: line numbers or letters at the right, each line (or space) glowing as it is named.
    if (v.marks != Marks.NONE) {
        val st = staves[0]
        val labels = when (v.marks) { Marks.LINE_NUMBERS -> (1..5).map { "$it" }; Marks.LINE_NAMES -> lineNames(st.clef); else -> spaceNames(st.clef) }
        val positions = if (v.marks == Marks.SPACE_NAMES) listOf(1, 3, 5, 7) else listOf(0, 2, 4, 6, 8)
        labels.forEachIndexed { k, lab ->
            val at = v.marksStart + k * v.markMs
            val p = progress(t, at, 320)
            if (p <= 0f) return@forEachIndexed
            val y = st.y(positions[k])
            val glow = flash(t, at, 700)
            if (v.marks == Marks.SPACE_NAMES) drawRect(ink.accent.copy(alpha = 0.18f * glow), Offset(left, y - s / 2), Size(right - left, s))
            else drawLine(ink.accent.copy(alpha = glow), Offset(left, y), Offset(right, y), 0.22f * s)
            text(ink, lab, Offset(right + marksW / 2 + 4.dp.toPx(), y), if (v.marks == Marks.LINE_NUMBERS) TextKind.NUMBER else TextKind.TITLE, pxSp(1.15f * s),
                if (glow > 0.2f) ink.accent else ink.ink, alpha = smooth(p * 2), scale = backOut(p))
        }
    }
    // Key signature: each sharp or flat pops in turn.
    val sigCount = abs(v.keyFifths)
    if (sigCount > 0) staves.forEach { st ->
        keySignaturePositions(v.keyFifths, st.clef).forEachIndexed { i, pos ->
            val p = progress(t, v.keyStart + i * v.keyMs, 300)
            if (p <= 0f) return@forEachIndexed
            val c = Offset(x + i * 1.15f * s + 0.5f * s, st.y(pos))
            val col = lerp(ink.ink, ink.accent, flash(t, v.keyStart + i * v.keyMs, 600))
            if (v.keyFifths > 0) drawSharp(c, s, col, backOut(p)) else drawFlat(c, s, col, backOut(p))
        }
    }
    x += sigCount * 1.15f * s + if (sigCount > 0) 0.8f * s else 0f
    // Time signature.
    v.time?.let { (top, bottom) ->
        val p = progress(t, v.timeStart, 380)
        staves.forEach { st ->
            text(ink, "$top", Offset(x + 0.9f * s, st.y(6)), TextKind.TITLE, pxSp(2.3f * s), ink.ink, alpha = smooth(p * 2), scale = backOut(p))
            text(ink, "$bottom", Offset(x + 0.9f * s, st.y(2)), TextKind.TITLE, pxSp(2.3f * s), ink.ink, alpha = smooth(p * 2), scale = backOut(p))
        }
        x += 2.4f * s
    }
    // Notes: evenly spaced slots, the group centred in the room left.
    val n = v.notes.size
    if (n == 0) return
    val room = right - x - 0.6f * s
    val slot = minOf(room / n, 5.4f * s)
    val x0 = x + (room - slot * n) / 2
    val sigAcc = keySignature(v.keyFifths).associate { it.letter to it.accidental }
    fun staveOf(nt: StaffNote): Stave = if (!v.grand) staves[0] else when (nt.staff ?: if ((nt.pitches.firstOrNull()?.midi ?: 60) >= 60) Clef.TREBLE else Clef.BASS) {
        Clef.TREBLE -> staves[0]; Clef.BASS -> staves[1]
    }
    val xs = FloatArray(n) { x0 + slot * (it + 0.5f) }
    // Barlines once the note after them lands.
    v.barlines.filter { it + 1 < n }.forEach { k ->
        if (t < v.arrival(k + 1) - 200) return@forEach
        val bx = (xs[k] + xs[k + 1]) / 2
        drawLine(ink.line, Offset(bx, staves.first().top), Offset(bx, staves.last().bottom), 0.12f * s)
    }
    v.notes.forEachIndexed { i, nt ->
        val slide = v.slide(i, t)
        if (slide <= 0f) return@forEachIndexed
        val st = staveOf(nt)
        val at = v.arrival(i)
        val nx = (right + 2.5f * s) + (xs[i] - right - 2.5f * s) * outCubic(slide)
        val alpha = smooth(slide * 2.5f)
        val col = if (t < at) ink.accent.copy(alpha = alpha) else lerp(ink.accent, ink.ink, progress(t, at, 800))
        val pulse = 1f + 0.3f * flash(t, at, 320)
        // A ripple where it lands.
        if (t >= at && t < at + 520 && !nt.rest) {
            val k = (t - at) / 520f
            val cy = st.y(nt.pitches.map { staffPosition(it, st.clef) }.average().toInt())
            drawCircle(ink.accent.copy(alpha = 0.45f * (1 - k)), s * (0.9f + 1.8f * k), Offset(nx, cy), style = Stroke(0.12f * s))
        }
        if (nt.rest) { drawRest(nt.value, nx, st.mid, s, col, pulse); dot(nt, nx, st.y(5), s, col); label(nt, i, v, nx, st, s, t, at, ink, staves); return@forEachIndexed }
        drawChordNote(nt, nx, st, s, col, pulse, sigAcc, ink)
        label(nt, i, v, nx, st, s, t, at, ink, staves)
        // A tie to the next note, drawn as it arrives.
        if (nt.tie && i + 1 < n) {
            val e = smooth(progress(t, v.arrival(i + 1) - 250, 350))
            if (e > 0f) {
                val pos = staffPosition(nt.pitches[0], st.clef)
                val below = pos < 4
                val y = st.y(pos) + (if (below) 0.7f else -0.7f) * s
                val x1 = nx + 0.7f * s; val x2 = xs[i + 1] - 0.7f * s
                val path = Path().apply { moveTo(x1, y); quadraticTo((x1 + x2) / 2, y + (if (below) 1.3f else -1.3f) * s, x2, y) }
                drawPath(trim(path, e), col, style = Stroke(0.2f * s, cap = StrokeCap.Round))
            }
        }
    }
}

private fun DrawScope.dot(nt: StaffNote, x: Float, y: Float, s: Float, col: Color) {
    if (nt.dotted) drawCircle(col, 0.17f * s, Offset(x + (headRx(nt.value) + 0.55f) * s, y))
}

private fun DrawScope.label(nt: StaffNote, i: Int, v: StaffVisual, x: Float, st: Stave, s: Float, t: Int, at: Int, ink: StageInk, staves: List<Stave>) {
    val text = nt.label ?: if (v.names && nt.pitches.isNotEmpty()) nt.pitches.joinToString(" ") { it.label } else return
    val p = progress(t, at, 380)
    if (p <= 0f) return
    val lowest = nt.pitches.minOfOrNull { staffPosition(it, st.clef) } ?: 0
    val highest = nt.pitches.maxOfOrNull { staffPosition(it, st.clef) } ?: 8
    val aboveTreble = v.grand && st == staves[0]
    val y = if (aboveTreble) minOf(st.top - 2.2f * s, st.y(highest) - 3.9f * s) else maxOf(st.bottom + 2.6f * s, st.y(lowest) + 2.4f * s)
    text(ink, text, Offset(x, y), TextKind.TITLE, pxSp((1.15f * s).coerceAtMost(18.dp.toPx())), if (t < at + 600) ink.accent else ink.dim,
        alpha = smooth(p * 2), scale = 0.8f + 0.2f * backOut(p))
}

/** The heads, ledger lines, accidentals and stem of a note or chord at [x] on [st]. */
internal fun DrawScope.drawChordNote(nt: StaffNote, x: Float, st: Stave, s: Float, col: Color, pulse: Float, sigAcc: Map<Int, Int>, ink: StageInk) {
    val sorted = nt.pitches.sortedBy { it.diatonic }
    val pos = sorted.map { staffPosition(it, st.clef) }
    val rx = headRx(nt.value)
    val up = pos.average() < 4
    // Seconds in a chord: the upper head moves to the other side of the stem.
    val shifted = BooleanArray(pos.size)
    for (k in 1 until pos.size) if (pos[k] - pos[k - 1] == 1 && !shifted[k - 1]) shifted[k] = true
    val shiftX = 2 * rx * s * 0.92f
    val ledgerW = (rx + 0.45f) * s
    ledgerLines(pos.min()).plus(ledgerLines(pos.max())).toSet().forEach { lp ->
        drawLine(ink.line, Offset(x - ledgerW, st.y(lp)), Offset(x + ledgerW + if (shifted.any { it }) shiftX else 0f, st.y(lp)), 0.11f * s)
    }
    sorted.forEachIndexed { k, pt ->
        val cx = x + if (shifted[k]) shiftX else 0f
        val c = Offset(cx, st.y(pos[k]))
        drawHead(c, s, nt.value, col, pulse)
        val want = sigAcc[pt.letter] ?: 0
        if (pt.accidental != want) {
            val ax = x - (rx + 0.95f) * s - k % 2 * 0.0f
            when (pt.accidental) { 1 -> drawSharp(Offset(ax, c.y), s, col, 0.95f); -1 -> drawFlat(Offset(ax, c.y), s, col, 0.95f); 0 -> drawNatural(Offset(ax, c.y), s, col) }
        }
        if (nt.dotted) drawCircle(col, 0.17f * s, Offset(cx + (rx + 0.55f) * s, c.y - if (pos[k] % 2 == 0) 0.5f * s else 0f))
    }
    val anchor = if (up) Offset(x, st.y(pos.first())) else Offset(x, st.y(pos.last()))
    drawStem(anchor, s, nt.value, up, col, extra = (pos.last() - pos.first()) / 2f)
}

// ---- Rhythm ------------------------------------------------------------------------------------------------------

private fun DrawScope.rhythmScene(v: RhythmVisual, t: Int, ink: StageInk, kid: Boolean = false) {
    val pad = 16.dp.toPx()
    val appear = smooth(progress(t, 0, v.leadMs))
    val cursor = v.cursor(t)
    // Time signature, big, top left; the tempo on the right. Not for a kid: "4 over 4" and "80 bpm" on the garden's
    // drum explain nothing their lessons say (the beat dots and boxes do the counting).
    if (!kid) {
    val tsX = pad + 18.dp.toPx()
    text(ink, "${v.top}", Offset(tsX, size.height * 0.10f), TextKind.TITLE, 34.sp, ink.ink, alpha = appear)
    text(ink, "${v.bottom}", Offset(tsX, size.height * 0.10f + 34.dp.toPx()), TextKind.TITLE, 34.sp, ink.ink, alpha = appear)
    v.term?.let { term ->
        val tw = textWidth(ink, term, TextKind.TITLE, 26.sp)
        text(ink, term, Offset(size.width - pad - tw / 2, size.height * 0.10f), TextKind.TITLE, 26.sp, ink.accent, alpha = appear)
    }
    val bpmText = "${v.bpm} bpm"
    val bw = textWidth(ink, bpmText, TextKind.NUMBER, 14.sp)
    text(ink, bpmText, Offset(size.width - pad - bw / 2, size.height * 0.10f + (if (v.term != null) 28 else 0).dp.toPx()), TextKind.NUMBER, 14.sp, ink.dim, alpha = appear)
    }
    // The beat dots: one per beat, filling as the cursor reaches them.
    val x0 = pad; val x1 = size.width - pad
    val beatW = (x1 - x0) / v.beats
    val dotsY = size.height * 0.33f
    for (b in 0 until v.beats) {
        val strong = b % v.top in v.strong
        val cx = x0 + (b + 0.5f) * beatW
        val here = cursor != null && cursor >= b && cursor < b + 1
        val beatStart = cursor?.let { ((it - b) * v.beatMs).toInt() } ?: -1
        val pulse = if (here) 1f - (beatStart / 260f).coerceIn(0f, 1f) else 0f
        val r = (if (strong) 9f else 6.5f) * (1f + 0.5f * pulse) * density
        drawCircle(if (here) ink.accent else ink.faint, r, Offset(cx, dotsY), alpha = appear)
    }
    // The grid: one cell per beat, strong beats shaded, bars split by a heavier line.
    val gTop = size.height * 0.42f; val gBot = size.height * 0.64f
    for (b in 0 until v.beats) {
        val strong = b % v.top in v.strong
        drawRoundRect(ink.ink.copy(alpha = if (strong) 0.08f else 0.04f), Offset(x0 + b * beatW + 2f, gTop), Size(beatW - 4f, gBot - gTop), CornerRadius(10.dp.toPx()), alpha = appear)
        for (k in 1 until v.sub) {
            val sx = x0 + (b + k.toFloat() / v.sub) * beatW
            drawLine(ink.faint, Offset(sx, gTop + 8.dp.toPx()), Offset(sx, gBot - 8.dp.toPx()), 1.dp.toPx(), alpha = appear)
        }
    }
    for (bar in 1 until v.bars) {
        val bx = x0 + bar * v.top * beatW
        drawLine(ink.dim, Offset(bx, gTop - 6.dp.toPx()), Offset(bx, gBot + 6.dp.toPx()), 2.5.dp.toPx(), alpha = appear)
    }
    // Notes as bars of their length; each lights while it sounds and its head pops as the cursor reaches it.
    val laneH = (gBot - gTop) * 0.46f
    val laneY = (gTop + gBot) / 2
    v.hits.forEach { h ->
        val l = x0 + h.start * beatW + 3.dp.toPx(); val r = x0 + (h.start + h.length) * beatW - 3.dp.toPx()
        val sounding = cursor != null && cursor >= h.start && cursor < h.start + h.length
        val since = cursor?.let { (it - h.start) * v.beatMs } ?: -1f
        val pop = if (sounding) (1f - since / 300f).coerceIn(0f, 1f) else 0f
        val hh = minOf(laneH, (r - l) * 0.62f) * (1f + 0.22f * pop)
        if (h.rest) {
            drawRoundRect(ink.dim, Offset(l, laneY - hh / 2), Size(r - l, hh), CornerRadius(hh / 2), style = Stroke(1.5.dp.toPx()), alpha = appear)
            text(ink, "rest", Offset((l + r) / 2, laneY), TextKind.LABEL, 12.sp, ink.dim, alpha = appear)
            return@forEach
        }
        val fill = if (sounding) ink.accent else ink.ink.copy(alpha = 0.22f)
        drawRoundRect(fill, Offset(l, laneY - hh / 2), Size(r - l, hh), CornerRadius(hh / 2), alpha = appear)
        drawCircle(if (sounding) ink.onAccent else ink.ink, hh * 0.28f * (1f + 0.3f * pop), Offset(l + hh / 2, laneY), alpha = appear)
    }
    // The counts under the grid, the one being said lit and larger.
    val cy = size.height * 0.76f
    val cells = v.beats * v.sub
    if (v.counts.isNotEmpty()) for (k in 0 until cells) {
        val cx = x0 + (k + 0.5f) * beatW / v.sub
        val on = cursor != null && cursor * v.sub >= k && cursor * v.sub < k + 1
        val beatWord = k % v.sub == 0
        val sz = if (beatWord) (if (cells > 12) 15 else 20).sp else (if (cells > 12) 12 else 15).sp
        text(ink, v.counts[k], Offset(cx, cy), if (beatWord) TextKind.TITLE else TextKind.LABEL, sz, if (on) ink.accent else ink.dim,
            alpha = appear, scale = if (on) 1.25f else 1f)
    }
    // The cursor sweeping across, with a knob on top.
    if (cursor != null) {
        val cx = x0 + cursor * beatW
        drawLine(ink.accent.copy(alpha = 0.22f), Offset(cx, gTop - 10.dp.toPx()), Offset(cx, gBot + 10.dp.toPx()), 10.dp.toPx(), cap = StrokeCap.Round)
        drawLine(ink.accent, Offset(cx, gTop - 10.dp.toPx()), Offset(cx, gBot + 10.dp.toPx()), 3.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(ink.accent, 6.dp.toPx(), Offset(cx, gTop - 12.dp.toPx()))
    }
}

// ---- Intervals ---------------------------------------------------------------------------------------------------

private fun DrawScope.intervalScene(v: IntervalVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.32f)
    val arcRoom = size.height * 0.42f
    val top = minOf(size.height - l.height - pad, (size.height - arcRoom - l.height) / 2 + arcRoom)
    val o = Offset(pad, top)
    val i = v.current(t)
    val pr = v.pairs[i]
    val u = t - v.segmentAt(i)
    val last = i == v.pairs.lastIndex
    val fadeOut = if (last) 1f else 1f - smooth(progress(u, v.segmentMs - 380, 300))
    val lo = pr.low.midi; val hi = pr.high.midi
    val xl = o.x + centerX(l, lo); val xh = o.x + centerX(l, hi)
    val e = outCubic(progress(u, 150, v.stretchMs))
    val tip = xl + (xh - xl) * e
    val colors = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>()
    if (u >= 0) { colors[lo] = ink.accent.copy(alpha = fadeOut.coerceAtLeast(0.001f)); sinks[lo] = maxOf(press(u), press(u - v.togetherAt)) }
    if (u >= v.highAt) { colors[hi] = ink.accent.copy(alpha = fadeOut.coerceAtLeast(0.001f)); sinks[hi] = maxOf(press(u - v.highAt), press(u - v.togetherAt)) }
    drawPiano(l, o, ink, lit = { colors[it] }, sink = { sinks[it] ?: 0f }, name = { m ->
        when (m) { lo -> if (u >= 0) pr.low.label else null; hi -> if (u >= v.highAt) pr.high.label else null; else -> null }
    })
    // The arc, traced from the low key to the high one.
    val y0 = top - 6.dp.toPx()
    val hgt = (abs(xh - xl) * 0.42f).coerceIn(56.dp.toPx(), arcRoom * 0.55f)
    val path = Path().apply { moveTo(xl, y0); cubicTo(xl, y0 - hgt * 1.33f, xh, y0 - hgt * 1.33f, xh, y0) }
    val together = flash(u, v.togetherAt, 700)
    drawPath(trim(path, e), ink.accent.copy(alpha = 0.18f * fadeOut), style = Stroke((10f + 8f * together).dp.toPx(), cap = StrokeCap.Round))
    drawPath(trim(path, e), ink.accent.copy(alpha = fadeOut), style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
    if (e > 0f && e < 1f) {
        val pm = PathMeasure(); pm.setPath(path, false)
        drawCircle(ink.accent, 6.dp.toPx(), pm.getPosition(pm.length * e))
    }
    // Half steps counted key by key as the arc passes them.
    val passed = (lo + 1..hi).filter { o.x + centerX(l, it) <= tip + 1f }
    if (v.countSteps) passed.forEachIndexed { k, m ->
        val cx = o.x + centerX(l, m)
        val by = y0 - 14.dp.toPx() - (if (isBlack(m)) 0f else 0f)
        drawCircle(ink.accentSoft, 10.dp.toPx(), Offset(cx, by), alpha = fadeOut)
        text(ink, "${k + 1}", Offset(cx, by), TextKind.NUMBER, 11.sp, ink.ink, alpha = fadeOut)
    }
    // Over the arc: the count while it stretches, then the name.
    val apex = y0 - hgt - 30.dp.toPx()
    val nameAt = if (v.countSteps) v.togetherAt else v.highAt
    val named = progress(u, nameAt, 380)
    if (v.countSteps && named <= 0f && passed.isNotEmpty()) {
        text(ink, "${passed.size} half step${if (passed.size == 1) "" else "s"}", Offset((xl + xh) / 2, apex), TextKind.NUMBER, 18.sp, ink.ink, alpha = fadeOut)
    }
    if (named > 0f) {
        val cx = ((xl + xh) / 2).coerceIn(pad + 90.dp.toPx(), size.width - pad - 90.dp.toPx())
        text(ink, pr.name.replaceFirstChar { it.uppercase() }, Offset(cx, apex), TextKind.TITLE, 26.sp, ink.accent, alpha = smooth(named * 2) * fadeOut, scale = 0.7f + 0.3f * backOut(named))
        pr.note?.let { text(ink, it, Offset(cx, apex + 28.dp.toPx()), TextKind.BODY, 15.sp, ink.dim, alpha = smooth(named * 2) * fadeOut) }
    }
}

// ---- Circle of fifths --------------------------------------------------------------------------------------------

private fun DrawScope.circleScene(v: CircleVisual, t: Int, ink: StageInk) {
    val c = Offset(size.width / 2, size.height / 2 + 8.dp.toPx())
    val r = minOf(size.width, size.height - 24.dp.toPx()) * 0.45f
    val ringW = r * (if (v.minors) 0.27f else 0.32f)
    val outerMid = r - ringW / 2
    val innerW = r * 0.2f
    val innerMid = r - ringW - innerW / 2 - 2.dp.toPx()
    val turn = v.angle(t) * 30f
    val stopI = v.currentStop(t)
    val stopKey = if (stopI >= 0) v.stops[stopI] else -1
    val arrived = if (stopI >= 0) smooth(progress(t, v.stopAt(stopI) + v.turnMs - 150, 320)) else 0f
    // The pointer at the top, fixed while the wheel turns under it.
    val tri = Path().apply {
        val y = c.y - r - 4.dp.toPx()
        moveTo(c.x - 9.dp.toPx(), y - 12.dp.toPx()); lineTo(c.x + 9.dp.toPx(), y - 12.dp.toPx()); lineTo(c.x, y); close()
    }
    drawPath(tri, ink.accent)
    for (i in 0 until 12) {
        val appear = if (v.build) progress(t, i * v.wedgeMs + 120, 260) else smooth(progress(t, 0, 300))
        if (appear <= 0f) continue
        val ang = i * 30f - turn                        // degrees clockwise from the top
        val chosen = i == stopKey
        val fill = lerp(ink.ink.copy(alpha = 0.07f), ink.accent, if (chosen) arrived else 0f)
        val a = backOut(appear)
        drawArc(fill, ang - 90f - 14f, 28f, false, Offset(c.x - outerMid, c.y - outerMid), Size(outerMid * 2, outerMid * 2), style = Stroke(ringW * a), alpha = smooth(appear * 2))
        val rad = Math.toRadians((ang - 90f).toDouble())
        val p = Offset(c.x + outerMid * cos(rad).toFloat(), c.y + outerMid * sin(rad).toFloat())
        val label = if (i == 6) "F♯/G♭" else CIRCLE_MAJORS[i].tonic.label
        text(ink, label, p, TextKind.TITLE, (if (i == 6) 13 else 19).sp, if (chosen && arrived > 0.5f) ink.onAccent else ink.ink, alpha = smooth(appear * 2), scale = a)
        if (v.minors) {
            val mFill = lerp(ink.ink.copy(alpha = 0.05f), ink.blue, if (chosen) arrived else 0f)
            drawArc(mFill, ang - 90f - 14f, 28f, false, Offset(c.x - innerMid, c.y - innerMid), Size(innerMid * 2, innerMid * 2), style = Stroke(innerW), alpha = smooth(appear * 2))
            val q = Offset(c.x + innerMid * cos(rad).toFloat(), c.y + innerMid * sin(rad).toFloat())
            text(ink, CIRCLE_MINORS[i].tonic.label + "m", q, TextKind.LABEL, 11.sp, if (chosen && arrived > 0.5f) Color.White else ink.dim, alpha = smooth(appear * 2))
        }
    }
    // The middle: the chosen key's signature.
    if (stopKey >= 0 && arrived > 0f) {
        val key = CIRCLE_MAJORS[stopKey]
        val f = key.fifths
        val count = when { f > 0 -> "$f♯"; f < 0 -> "${-f}♭"; else -> "0" }
        val pop = backOut(arrived)
        val inner = if (v.minors) innerMid - innerW / 2 else outerMid - ringW / 2
        val big = (inner * 0.42f).coerceAtMost(44.dp.toPx())
        text(ink, count, Offset(c.x, c.y - big * 0.35f), TextKind.TITLE, pxSp(big), ink.accent, alpha = arrived, scale = pop)
        text(ink, if (stopKey == 6) "F♯ or G♭" else key.label, Offset(c.x, c.y + big * 0.45f), TextKind.TITLE, pxSp((big * 0.46f).coerceAtLeast(14.dp.toPx())), ink.ink, alpha = arrived)
        val sub = if (v.minors) CIRCLE_MINORS[stopKey].label else keySignature(f).joinToString(" ") { it.label }.ifEmpty { "No sharps or flats" }
        text(ink, sub, Offset(c.x, c.y + big * 1.0f), TextKind.LABEL, pxSp((big * 0.34f).coerceAtLeast(12.dp.toPx())), if (v.minors) ink.blue else ink.dim, alpha = arrived)
    }
}

// ---- Chords ------------------------------------------------------------------------------------------------------

private fun DrawScope.chordScene(v: ChordVisual, t: Int, ink: StageInk) {
    val pad = 14.dp.toPx()
    val l = pianoFor(v.low, v.high, size.width - 2 * pad, size.height * 0.30f)
    val kTop = size.height - l.height - pad
    val o = Offset(pad, kTop)
    val staffRoom = kTop - 18.dp.toPx()
    val s = minOf(staffRoom / 12f, 19.dp.toPx(), (size.width - 2 * pad) / 18f)
    val st = Stave(v.clef, staffRoom * 0.56f + 2 * s, s)
    val left = pad; val right = size.width - pad
    for (i in 0..4) drawLine(ink.line, Offset(left, st.y(i * 2)), Offset(right, st.y(i * 2)), 0.09f * s)
    drawClef(v.clef == Clef.TREBLE, left + 0.35f * s, st.top, s, ink.ink, 1f)
    val single = v.chords.size == 1
    val x0 = left + 3.6f * s
    val room = right - x0 - (if (single) 5.5f * s else 0.6f * s)
    val slot = minOf(room / v.chords.size, 6.5f * s)
    val gx = x0 + (room - slot * v.chords.size) / 2
    val cur = v.current(t)
    val lit = HashMap<Int, Color>(); val sinks = HashMap<Int, Float>(); val names = HashMap<Int, String>()
    v.chords.forEachIndexed { i, ch ->
        if (t < v.chordAt(i)) return@forEachIndexed
        val cx = gx + slot * (i + 0.5f)
        val current = i == cur
        val ring = flash(t, v.togetherAt(i), 600)
        val sorted = ch.notes.sortedBy { it.diatonic }
        val pos = sorted.map { staffPosition(it, st.clef) }
        val shifted = BooleanArray(pos.size)
        for (k in 1 until pos.size) if (pos[k] - pos[k - 1] == 1 && !shifted[k - 1]) shifted[k] = true
        val arrivedNotes = sorted.indices.filter { t >= v.noteAt(i, ch.notes.indexOf(sorted[it])) }
        // Ledger lines for the notes that have arrived.
        arrivedNotes.flatMap { ledgerLines(pos[it]) }.toSet().forEach { lp ->
            drawLine(ink.line, Offset(cx - 1.25f * s, st.y(lp)), Offset(cx + 1.25f * s, st.y(lp)), 0.11f * s)
        }
        for (k in arrivedNotes) {
            val pt = sorted[k]
            val at = v.noteAt(i, ch.notes.indexOf(pt))
            val e = outCubic(progress(t, at, 320))
            val y = st.y(pos[k]) + (1 - e) * 2.6f * s
            val x = cx + if (shifted[k]) 1.45f * s else 0f
            val col = when {
                !current -> ink.dim
                t < at + 700 && ch.build -> lerp(ink.accent, ink.ink, progress(t, at, 700))
                else -> lerp(ink.ink, ink.accent, ring)
            }
            drawHead(Offset(x, y), s, NoteValue.WHOLE, col.copy(alpha = col.alpha * smooth(e * 2)), 1f + 0.15f * ring)
            if (pt.accidental != 0) {
                val ax = cx - 1.8f * s
                if (pt.accidental > 0) drawSharp(Offset(ax, y), s, col, 0.9f) else drawFlat(Offset(ax, y), s, col, 0.9f)
            }
            if (single) {
                val tag = ch.tags.getOrNull(ch.notes.indexOf(pt))
                val step = maxOf(s * 1.0f, 17.dp.toPx())
                val ty = minOf(st.y(pos[0]), st.y(pos[k]) + step * 0.4f) - k * step + (y - st.y(pos[k]))
                if (tag != null) drawLine(ink.faint.copy(alpha = smooth(e * 2)), Offset(cx + 1.0f * s, y), Offset(cx + 2.2f * s, ty), 1.2.dp.toPx())
                if (tag != null) text(ink, tag, Offset(cx + 2.4f * s, ty - 9.dp.toPx()), TextKind.LABEL, pxSp((0.95f * s).coerceIn(11.dp.toPx(), 14.dp.toPx())),
                    if (t < at + 700) ink.accent else ink.dim, alpha = smooth(e * 2), centred = false)
            }
            if (current) { lit[pt.midi] = ink.accent; sinks[pt.midi] = maxOf(press(t - at), press(t - v.togetherAt(i))); names[pt.midi] = pt.label }
        }
        // Name above (current chord only when there are several), numeral below.
        val nameAlpha = smooth(progress(t, v.togetherAt(i), 350))
        if (ch.name.isNotEmpty() && (single || current)) {
            text(ink, ch.name, Offset(if (single) size.width / 2 else cx, st.top - 2.6f * s), TextKind.TITLE, if (single) 24.sp else 20.sp,
                ink.accent, alpha = if (ch.build) nameAlpha else 1f)
        }
        ch.numeral?.let {
            val lowestY = maxOf(st.bottom + 2.4f * s, st.y(pos.min()) + 2.2f * s)
            text(ink, it, Offset(cx, lowestY), TextKind.TITLE, 19.sp, if (current) ink.accent else ink.dim)
        }
    }
    drawPiano(l, o, ink, lit = { lit[it] }, sink = { sinks[it] ?: 0f }, name = { names[it] })
}

// ---- Value tree --------------------------------------------------------------------------------------------------

private val TREE_VALUES = listOf(NoteValue.WHOLE, NoteValue.HALF, NoteValue.QUARTER, NoteValue.EIGHTH, NoteValue.SIXTEENTH)
private val TREE_NAMES = listOf("1 whole", "2 halves", "4 quarters", "8 eighths", "16 sixteenths")
private val REST_NAMES = listOf("Whole rest", "Half rests", "Quarter rests", "Eighth rests", "Sixteenth rests")

private fun DrawScope.valueTreeScene(v: ValueTreeVisual, t: Int, ink: StageInk) {
    val pad = 16.dp.toPx()
    val rowH = (size.height - 2 * pad) / v.rows
    val w = size.width - 2 * pad
    // Positions of every glyph, for the tree lines between a note and the two that split it.
    fun xOf(r: Int, k: Int) = pad + (k + 0.5f) * w / (1 shl r)
    for (r in 0 until v.rows) {
        val at = v.rowAt(r)
        if (t < at - 150) break
        val n = 1 shl r
        val s = minOf(rowH / 5.2f, w / n / 2.1f, 14.dp.toPx())
        val yTop = pad + rowH * r
        val headY = yTop + rowH * 0.66f
        val each = 4f / n * v.beatMs
        val labelP = progress(t, at - 150, 300)
        text(ink, if (v.rests) REST_NAMES[r] else TREE_NAMES[r], Offset(pad, yTop + 2.dp.toPx()), TextKind.LABEL, 12.sp, ink.dim, alpha = labelP, centred = false)
        for (k in 0 until n) {
            val appear = outCubic(progress(t, at - 150 + k * 18, 260))
            if (appear <= 0f) continue
            val hit = (at + k * each).toInt()
            val f = flash(t, hit, 420)
            val col = lerp(ink.ink, ink.accent, if (t >= hit && t < hit + each) maxOf(f, 0.6f) else 0f)
            val x = xOf(r, k)
            val y = headY + (1 - appear) * 12.dp.toPx()
            if (r > 0) {
                val px = xOf(r - 1, k / 2)
                val py = yTop - rowH * 0.05f
                drawLine(ink.faint, Offset(px, py), Offset(x, y - 3.6f * s), 1.2.dp.toPx(), alpha = appear)
            }
            if (v.rests) drawRest(TREE_VALUES[r], x, y - 1.2f * s, s, col.copy(alpha = appear), 1f + 0.25f * f)
            else drawLoneNote(TREE_VALUES[r], Offset(x, y), s, col.copy(alpha = appear), 1f + 0.25f * f)
        }
    }
}

// ---- Words -------------------------------------------------------------------------------------------------------

private fun DrawScope.popScene(v: PopVisual, t: Int, ink: StageInk) {
    val long = v.items.any { it.length > 4 }
    val newest = v.items.indices.lastOrNull { t >= v.itemAt(it) } ?: -1
    if (long) {
        val gap = minOf(size.height / (v.items.size + 1), 64.dp.toPx())
        val y0 = size.height / 2 - gap * (v.items.size - 1) / 2
        v.items.forEachIndexed { i, s ->
            val p = progress(t, v.itemAt(i), 380)
            if (p <= 0f) return@forEachIndexed
            text(ink, s, Offset(size.width / 2, y0 + i * gap), TextKind.TITLE, 32.sp, if (i == newest) ink.accent else ink.ink, alpha = smooth(p * 3), scale = 0.5f + 0.5f * backOut(p))
        }
    } else {
        val size1 = minOf(44f, (size.width / v.items.size / density) * 0.75f).sp
        val gap = (size.width - 32.dp.toPx()) / v.items.size
        v.items.forEachIndexed { i, s ->
            val p = progress(t, v.itemAt(i), 380)
            if (p <= 0f) return@forEachIndexed
            val x = 16.dp.toPx() + gap * (i + 0.5f)
            text(ink, s, Offset(x, size.height * 0.45f), TextKind.TITLE, size1, if (i == newest) ink.accent else ink.ink, alpha = smooth(p * 3), scale = 0.4f + 0.6f * backOut(p))
            val k = flash(t, v.itemAt(i), 500)
            if (k > 0f) drawCircle(ink.accent.copy(alpha = 0.35f * k), (1.4f - k) * 34.dp.toPx(), Offset(x, size.height * 0.45f), style = Stroke(2.dp.toPx()))
        }
    }
    v.small?.let { text(ink, it, Offset(size.width / 2, size.height * 0.85f), TextKind.BODY, 15.sp, ink.dim) }
}
