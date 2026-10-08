package com.bigpapituna.sharpears.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bigpapituna.sharpears.engine.KeyboardLayout
import kotlin.math.PI
import kotlin.math.sin

/** The soft orange-red of a wrong note: warm, never the red of an error. */
val WrongGlow = Color(0xFFEF7B4E)

/**
 * Feedback at the hit line instead of coloured keys: a short bright flash rising from a correct note's column
 * (its hand colour with a white core) and a soft pulse along the whole line for a wrong note.
 *
 * The player adds lights; NoteLane draws them. Only [clock] is read while drawing, and it ticks only while a
 * light is alive, so a flash redraws the lane without recomposing anything.
 */
class HitLights(
    /** Debug: stretches every light (e.g. 8 for screenshots). */
    val slow: Float = 1f,
) {
    enum class Kind { HIT, WRONG }
    class Light(val midi: Int, val startNanos: Long, val kind: Kind, val color: Color)

    val items = ArrayList<Light>(8)
    /** Frame time while lights are alive; read in the draw phase only. */
    val clock = mutableLongStateOf(0L)
    /** Bumps on each new light, so the frame loop wakes up. */
    val added = mutableIntStateOf(0)

    val hitNanos get() = (HIT_MS * slow * 1_000_000).toLong()
    val wrongNanos get() = (WRONG_MS * slow * 1_000_000).toLong()

    fun hit(midi: Int, color: Color, now: Long = System.nanoTime()) = add(Light(midi, now, Kind.HIT, color))
    fun wrong(now: Long = System.nanoTime()) = add(Light(-1, now, Kind.WRONG, WrongGlow))

    private fun add(l: Light) {
        if (items.size >= 16) items.removeAt(0)
        items += l
        added.intValue++
    }

    /** Drops finished lights; true while any is left. */
    fun prune(now: Long): Boolean {
        items.removeAll { now - it.startNanos > if (it.kind == Kind.HIT) hitNanos else wrongNanos }
        return items.isNotEmpty()
    }

    /** Draws every live light over the lane, whose hit line is at [hitY]. */
    fun DrawScope.draw(keys: KeyboardLayout, hitY: Float, low: Int, high: Int) {
        val now = clock.longValue
        if (now == 0L) return
        for (l in items) {
            val t = ((now - l.startNanos).toFloat() / (if (l.kind == Kind.HIT) hitNanos else wrongNanos)).coerceIn(0f, 1f)
            if (l.kind == Kind.WRONG) {
                // A soft band that swells and fades along the whole line.
                val a = sin(PI * t).toFloat() * 0.55f
                val h = size.height * 0.16f
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, l.color.copy(alpha = a * 0.6f), l.color.copy(alpha = a)), startY = hitY - h, endY = hitY),
                    Offset(0f, hitY - h), Size(size.width, h))
                drawRect(l.color.copy(alpha = a), Offset(0f, hitY - 5f), Size(size.width, 5f))
            } else {
                if (l.midi !in low..high) continue
                val k = keys.rect(l.midi)
                val cx = (k.left + k.right) / 2f
                val w = keys.whiteWidth * 1.1f
                val fade = 1f - t
                val rise = size.height * (0.25f + 0.30f * (1f - (1f - t) * (1f - t)))      // eases out as it climbs
                // A wide soft halo, then the beam: hand colour fading upward from a white base.
                val halo = maxOf(w * 1.6f, rise * 0.7f)
                drawCircle(Brush.radialGradient(listOf(l.color.copy(alpha = 0.45f * fade), Color.Transparent), center = Offset(cx, hitY), radius = halo),
                    radius = halo, center = Offset(cx, hitY))
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.55f to l.color.copy(alpha = 0.85f * fade), 1f to Color.White.copy(alpha = fade),
                    startY = hitY - rise, endY = hitY), Offset(cx - w / 2f, hitY - rise), Size(w, rise))
                // The white core at the base.
                val r = w * (0.7f + 0.3f * t)
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = fade), l.color.copy(alpha = 0.7f * fade), Color.Transparent),
                    center = Offset(cx, hitY - 2f), radius = r), radius = r, center = Offset(cx, hitY - 2f))
            }
        }
    }

    companion object {
        const val HIT_MS = 260f
        const val WRONG_MS = 380f
    }
}
