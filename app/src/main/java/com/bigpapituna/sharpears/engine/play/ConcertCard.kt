package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.RunRecording
import com.bigpapituna.sharpears.engine.sampleFor
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/** A rectangle in card pixels. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    fun inside(o: Box) = left >= o.left && top >= o.top && right <= o.right && bottom <= o.bottom
    fun overlaps(o: Box) = left < o.right && o.left < right && top < o.bottom && o.top < bottom
}

/**
 * Where everything sits on the "Share my concert" card: a portrait card (4:5, the shape chat apps show whole) with
 * the pup on top, the song title under him (one or two lines, the size chosen to fit), three stars (the ones earned
 * filled), the sticker slot beside the stars, and a footer line. Pure maths; ui/play/ShareConcert.kt draws it with
 * Android's Canvas.
 */
data class ConcertCardLayout(
    val card: Box, val pup: Box, val title: Box, val titleLines: List<String>, val titleSize: Float,
    val stars: List<Box>, val filled: Int, val sticker: Box, val footer: Box, val footerSize: Float, val corner: Float,
) {
    companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val MAX_STARS = 3
        /** Longest title line, in characters, before it wraps (at the largest size). */
        private const val LINE_CHARS = 18

        /**
         * The layout for a [width] x [height] card, [title] and [stars] (clamped to 0..3). [charWidth] is the average
         * glyph width as a share of the font size (Nunito bold is about 0.55), used to shrink a long title until it fits.
         */
        fun of(title: String, stars: Int, width: Int = WIDTH, height: Int = HEIGHT, charWidth: Float = 0.55f): ConcertCardLayout {
            val w = width.toFloat(); val h = height.toFloat()
            val m = w * 0.07f                                           // margin
            val card = Box(0f, 0f, w, h)
            val pupSize = h * 0.36f
            val pup = Box(w / 2 - pupSize / 2, h * 0.06f, w / 2 + pupSize / 2, h * 0.06f + pupSize)

            val lines = wrap(title.trim().ifEmpty { "My concert" }, LINE_CHARS)
            val titleTop = pup.bottom + h * 0.04f
            val titleBox = Box(m, titleTop, w - m, titleTop + h * 0.2f)
            // Largest size whose longest line fits the width and whose lines fit the box (1.15 line height).
            val longest = lines.maxOf { it.length }.coerceAtLeast(1)
            val byWidth = titleBox.width / (longest * charWidth)
            val byHeight = titleBox.height / (lines.size * 1.15f)
            val titleSize = minOf(byWidth, byHeight, h * 0.075f)

            val starSize = w * 0.15f
            val gap = w * 0.035f
            val rowTop = titleBox.bottom + h * 0.04f
            val stickerSize = starSize * 1.25f
            val rowWidth = MAX_STARS * starSize + (MAX_STARS - 1) * gap + gap * 1.6f + stickerSize
            var x = w / 2 - rowWidth / 2
            val starTop = rowTop + (stickerSize - starSize) / 2
            val starBoxes = List(MAX_STARS) { i -> Box(x + i * (starSize + gap), starTop, x + i * (starSize + gap) + starSize, starTop + starSize) }
            x += MAX_STARS * starSize + (MAX_STARS - 1) * gap + gap * 1.6f
            val sticker = Box(x, rowTop, x + stickerSize, rowTop + stickerSize)

            val footerSize = h * 0.028f
            val footer = Box(m, h - m - footerSize * 1.4f, w - m, h - m)
            return ConcertCardLayout(card, pup, titleBox, lines, titleSize, starBoxes, stars.coerceIn(0, MAX_STARS), sticker,
                footer, footerSize, w * 0.05f)
        }

        /**
         * Splits [text] into at most two lines of about [max] characters at spaces; a second line that would still be
         * too long ends in "..." rather than a third line.
         */
        fun wrap(text: String, max: Int): List<String> {
            if (text.length <= max) return listOf(text)
            val words = text.split(Regex("\\s+"))
            fun line(from: Int): Pair<String, Int> {                     // words from [from] that fit; next index
                val b = StringBuilder()
                var i = from
                while (i < words.size && (b.isEmpty() || b.length + 1 + words[i].length <= max)) {
                    if (b.isNotEmpty()) b.append(' ')
                    b.append(words[i]); i++
                }
                return b.toString() to i
            }
            val (first, i) = line(0)
            if (i >= words.size) return listOf(first)
            val (second, j) = line(i)
            val cut = if (second.length > max) second.take(max).trimEnd() + "..." else if (j < words.size) "$second..." else second
            return listOf(first, cut)
        }

        /** The five points (outer and inner, ten in all) of a star inside [b], top point first, for drawing it. */
        fun starPoints(b: Box): List<Pair<Float, Float>> {
            val r = minOf(b.width, b.height) / 2
            val inner = r * 0.48f
            return List(10) { k ->
                val a = -PI / 2 + k * PI / 5
                val rr = if (k % 2 == 0) r else inner
                (b.centerX + (rr * cos(a)).toFloat()) to (b.centerY + r * 0.06f + (rr * sin(a)).toFloat())
            }
        }
    }
}

/** What "Share my concert" sends and what the files are called. */
object ConcertFiles {
    /** Letters, digits and a little punctuation (no slashes, colons, quotes); everything else becomes a space. */
    private val UNSAFE = Regex("[^\\p{L}\\p{N} ,.'()&!-]")

    /** "Mia plays Minuet in G": the share's title, the card's footer and the file names' stem. */
    fun title(playerName: String, songTitle: String): String {
        val who = playerName.trim().ifEmpty { "I" }
        val verb = if (who == "I") "play" else "plays"
        return "$who $verb ${songTitle.trim().ifEmpty { "a song" }}"
    }

    /** A safe file stem for [title]: no path or odd characters, at most [max] characters, never empty. */
    fun stem(title: String, max: Int = 60): String {
        val clean = title.replace(UNSAFE, " ").replace(Regex("\\s+"), " ").trim().trim('.', '-')
        return clean.take(max).trimEnd().trimEnd('.', '-', ' ').ifEmpty { "My concert" }
    }

    /** The two files: the recording and the card. */
    fun names(title: String): Pair<String, String> = stem(title).let { "$it.wav" to "$it.png" }

    /** The card's footer: "Sharp Ears, October 18, 2026". */
    fun footer(date: LocalDate): String =
        "Sharp Ears, " + date.month.name.lowercase().replaceFirstChar { it.uppercase() } + " " + date.dayOfMonth + ", " + date.year

    /** The share's MIME type: the one type when all files share it, else the family ("audio/\*"), else anything. */
    fun mimeType(fileNames: List<String>): String {
        val types = fileNames.map { typeOf(it) }.distinct()
        if (types.size == 1) return types[0]
        val families = types.map { it.substringBefore('/') }.distinct()
        return if (families.size == 1) families[0] + "/*" else "*/*"
    }

    fun typeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "wav" -> "audio/wav"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "zip" -> "application/zip"
        "txt" -> "text/plain"
        else -> "application/octet-stream"
    }
}

/**
 * The run as sound: the notes of a [RunRecording] (what was played, as the mic or MIDI heard it) played back with
 * the app's own piano samples into one mono buffer, for the WAV that goes with the card. The phone does not keep the
 * microphone audio of a run (release builds never record), so this is the run replayed, the same as "Hear my run".
 */
object ConcertMix {
    /** Silence before the first note. */
    const val LEAD_MS = 400L
    /** Ring after the last note ends. */
    const val TAIL_MS = 1500L
    /** Damper fade after a key's release. */
    const val RELEASE_MS = 90.0
    /** Longest recording sent: ten minutes. */
    const val MAX_MS = 10 * 60_000L
    /** Peak level of the mix (about -1 dBFS), up or down. */
    const val PEAK = 0.89f

    /**
     * Mixes [run] at [rate] Hz. [samples] gives the decoded sample for a sample key (mono, at [rate]) or null when it
     * could not be read; the keys available are [available] (assets/samples: every 4 semitones). Notes are placed by
     * real time (song time over the run's speed) and shifted to start [LEAD_MS] in.
     */
    fun render(run: RunRecording, available: Collection<Int>, rate: Int, samples: (Int) -> FloatArray?): FloatArray {
        if (run.isEmpty || available.isEmpty()) return FloatArray(0)
        val speed = run.speed.takeIf { it > 0 } ?: 1.0
        val first = run.notes.minOf { it.atMs }
        fun real(ms: Long) = ((ms - first) / speed).toLong() + LEAD_MS
        val endMs = minOf(MAX_MS, run.notes.maxOf { real(it.atMs) + (it.durationMs / speed).toLong() } + TAIL_MS)
        val out = FloatArray((endMs * rate / 1000).toInt())
        for (n in run.notes) {
            val startMs = real(n.atMs)
            if (startMs >= endMs) continue
            val (key, ratio) = sampleFor(n.midi, available)
            val src = samples(key) ?: continue
            val gain = 0.3f + 0.7f * n.velocity.coerceIn(1, 127) / 127f
            val start = (startMs * rate / 1000).toInt()
            val release = start + ((n.durationMs / speed) * rate / 1000).toInt()
            val fade = RELEASE_MS / 1000 * rate
            var pos = 0.0
            var i = start
            while (i < out.size) {
                val k = pos.toInt()
                if (k + 1 >= src.size) break
                val s = src[k] + (src[k + 1] - src[k]) * (pos - k).toFloat()            // linear interpolation
                val damp = if (i < release) 1.0 else exp(-(i - release) / fade)
                if (damp < 0.001) break
                out[i] += (s * gain * damp).toFloat()
                pos += ratio
                i++
            }
        }
        val peak = out.maxOfOrNull { abs(it) } ?: 0f
        if (peak > 0f) { val g = PEAK / peak; for (i in out.indices) out[i] *= g }     // soft playing comes out as loud as firm
        return out
    }
}
