package com.bigpapituna.sharpears.ui.motion

import java.util.Locale
import kotlin.math.floor

private val TIME = Regex("""^(\D*)(\d+):(\d{2})(\D*)$""")
private val NUMBER = Regex("""\d+(\.\d+)?""")

/**
 * [value] as it reads [progress] (0..1) of the way through a count-up from zero, for numbers that roll up on a
 * celebration. A time ("3:12") counts in seconds; otherwise the first number in the text counts (keeping its
 * decimals and zero padding) and the rest stays: "95%", "+120 XP", "8/10", "1.5 s". Text with no number, and
 * any [progress] of 1 or more, comes back unchanged.
 */
fun countUp(value: String, progress: Float): String {
    if (progress >= 1f) return value
    val p = progress.coerceAtLeast(0f)
    TIME.matchEntire(value)?.let { m ->
        val (pre, min, sec, post) = m.destructured
        val total = min.toInt() * 60 + sec.toInt()
        val now = floor(total * p).toInt()
        return pre + (now / 60) + ":" + (now % 60).toString().padStart(2, '0') + post
    }
    val m = NUMBER.find(value) ?: return value
    val text = m.value
    val decimals = m.groups[1]?.value?.length?.minus(1) ?: 0
    val now = text.toDouble() * p
    val shown = if (decimals > 0) String.format(Locale.ROOT, "%.${decimals}f", floor(now * Math.pow(10.0, decimals.toDouble())) / Math.pow(10.0, decimals.toDouble()))
        else floor(now).toLong().toString().padStart(if (text.startsWith("0")) text.length else 1, '0')
    return value.replaceRange(m.range, shown)
}

/** Ease-out for count-ups: fast at first, settling on the final number. */
fun easeOutCubic(t: Float): Float { val u = 1f - t.coerceIn(0f, 1f); return 1f - u * u * u }
