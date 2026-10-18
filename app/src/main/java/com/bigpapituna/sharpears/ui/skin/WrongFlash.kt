package com.bigpapituna.sharpears.ui.skin

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.KeyRect
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.skin.WrongFlashCurve
import com.bigpapituna.sharpears.engine.skin.WrongKey
import com.bigpapituna.sharpears.engine.skin.WrongTone

/**
 * Wrong keys, shown (FAMILY.md, "detector and skin, together"): feed it with [show] and hand the same object to
 * NoteLane and Keyboard (`wrong = flash`). The key and its falling-note column glow yellow (a slip of up to two
 * semitones) or red (further) for about 600 ms with a short shake; kids see the same colours, never a word. With the
 * phone's animations off there is no shake and the glow holds still until it ends.
 *
 * It holds only start times and tones in flat arrays, so showing a flash allocates nothing; the lane and the keyboard
 * each run a frame clock only while a flash lives ([rememberFlashClock]). [count] and [last] are state, for anyone
 * who reacts to a wrong key (the World team's pup tilts its head: `LaunchedEffect(flash.count) { ... }`), and
 * [onWrong] is called on each one as well.
 */
@Stable
class WrongFlash(var onWrong: (WrongKey) -> Unit = {},
                 /** Debug: stretches the flash this many times so a screenshot can catch it (PlayerStart.lightSlow). */
                 private val slow: Float = 1f) {
    private val startNs = LongArray(128)
    private val tones = arrayOfNulls<WrongTone>(128)

    /** Bumps on every wrong key shown. */
    val count = mutableIntStateOf(0)

    /** The latest wrong key, or null before the first. */
    var last by mutableStateOf<WrongKey?>(null)
        private set

    fun show(w: WrongKey, nowNs: Long = System.nanoTime()) {
        if (w.midi !in 0..127) return
        startNs[w.midi] = nowNs
        tones[w.midi] = w.tone
        last = w
        count.intValue++
        Log.d(TAG, "wrong key ${noteName(w.midi)} distance=${w.distance} ${w.tone}")
        onWrong(w)
    }

    /**
     * Ms since [midi] was last shown wrong, or a negative number if never. A frame's time is its vsync, which can be a
     * little before the moment [show] ran in that frame: that counts as 0, not as "not yet".
     */
    fun msSince(midi: Int, nowNs: Long): Double = if (midi !in 0..127 || startNs[midi] == 0L) -1.0 else maxOf(0.0, (nowNs - startNs[midi]) / 1e6 / slow)

    fun tone(midi: Int): WrongTone? = if (midi in 0..127) tones[midi] else null

    fun glow(midi: Int, nowNs: Long, still: Boolean): Float = WrongFlashCurve.glow(msSince(midi, nowNs), still)

    /** Sideways shake for [midi] at [nowNs], in px for a key [reach] px wide at most. */
    fun shakePx(midi: Int, nowNs: Long, still: Boolean, reach: Float): Float = WrongFlashCurve.shake(msSince(midi, nowNs), still) * reach

    /** Any key still glowing at [nowNs]. */
    fun alive(nowNs: Long): Boolean {
        for (m in 0..127) if (startNs[m] != 0L && WrongFlashCurve.alive(msSince(m, nowNs))) return true
        return false
    }

    /** Calls [block] for each key glowing at [nowNs] with its glow 0..1 and tone. */
    inline fun forEachLit(nowNs: Long, still: Boolean, block: (midi: Int, glow: Float, tone: WrongTone) -> Unit) {
        for (m in 0..127) {
            val g = glow(m, nowNs, still)
            if (g > 0f) block(m, g, tone(m) ?: continue)
        }
    }

    fun clear() { startNs.fill(0L); tones.fill(null) }
}

/** A [WrongFlash] kept for the screen; [onWrong] always sees the latest lambda. */
@Composable
fun rememberWrongFlash(slow: Float = 1f, onWrong: (WrongKey) -> Unit = {}): WrongFlash {
    val latest by rememberUpdatedState(onWrong)
    return remember(slow) { WrongFlash({ latest(it) }, slow) }
}

/**
 * The frame time while [flash] has a key glowing, read only while drawing (so nothing recomposes); 0 when idle.
 * Null [flash]: always 0.
 */
@Composable
fun rememberFlashClock(flash: WrongFlash?): LongState {
    val clock = remember { mutableLongStateOf(0L) }
    if (flash != null) LaunchedEffect(flash) {
        snapshotFlow { flash.count.intValue }.collect {
            while (withFrameNanos { now -> clock.longValue = now; flash.alive(now) }) Unit
        }
    }
    return clock
}

/** A key's glow on the keyboard: the face washed in the tone and a halo spilling around it. */
fun DrawScope.drawWrongKey(k: KeyRect, glow: Float, tone: WrongTone, dx: Float) {
    val c = tone.colour
    val w = k.right - k.left
    val h = k.bottom - k.top
    val spill = w * 0.18f
    drawRoundRect(Brush.radialGradient(listOf(c.copy(alpha = 0.55f * glow), Color.Transparent),
        center = Offset(k.centerX + dx, k.top + h * 0.62f), radius = maxOf(w, h * 0.5f) + spill),
        Offset(k.left - spill + dx, k.top), Size(w + 2 * spill, h), CornerRadius(w * 0.3f))
    drawRoundRect(c, Offset(k.left + 1.5f + dx, k.top), Size(w - 3f, h), CornerRadius(w * 0.12f), alpha = 0.62f * glow)
    drawRoundRect(Color.White, Offset(k.left + w * 0.2f + dx, k.top + h * 0.08f), Size(w * 0.6f, h * 0.06f), CornerRadius(w * 0.1f), alpha = 0.35f * glow)
}

/** The key's column on the lane: light rising from the hit line in the tone, brightest at the line (drawn under the notes). */
fun DrawScope.drawWrongColumn(left: Float, right: Float, hitLineY: Float, glow: Float, tone: WrongTone, dx: Float) {
    val c = tone.colour
    val w = right - left
    val top = maxOf(0f, hitLineY - size.height * 0.55f)
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.22f * glow), c.copy(alpha = 0.6f * glow)), startY = top, endY = hitLineY),
        Offset(left + dx, top), Size(w, hitLineY - top))
}

/** A bright cap where the wrong key meets the lane, a little wider than its column (drawn over the hit line). */
fun DrawScope.drawWrongCap(left: Float, right: Float, hitLineY: Float, glow: Float, tone: WrongTone, dx: Float) {
    val w = right - left
    drawRoundRect(tone.colour, Offset(left - w * 0.08f + dx, hitLineY - 8f), Size(w * 1.16f, 10f), CornerRadius(5f), alpha = 0.95f * glow)
}
