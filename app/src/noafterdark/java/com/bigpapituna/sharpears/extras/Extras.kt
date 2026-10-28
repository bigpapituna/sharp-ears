@file:Suppress("UNUSED_PARAMETER")

package com.bigpapituna.sharpears.extras

import android.content.Context
import androidx.compose.runtime.Composable
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.family.Profile

/**
 * The extra's door on a build without it (src/noafterdark; app/build.gradle.kts picks this file when secrets.properties has no
 * AFTERDARK=true, or with -Psharpears.afterdark=false). Every call does nothing: no line is ever taken, nothing is shown, the
 * screens keep their own words. src/afterdark has the same object with the same calls.
 */
object Extras {
    /** This build has the extra. */
    const val BUILT = false

    /** What's new lines the extra adds, by version: none. */
    val WHATS_NEW: Map<String, List<String>> = emptyMap()

    /** MainActivity opened a profile: [who] is read live at every moment. */
    fun open(context: Context, who: () -> Profile, prefs: Prefs, mic: MicInput, voice: Voice) {}

    /** The profile closed or the app left the screen. */
    fun close() {}

    /** A moment the screen shows words for in its own bubble: the words to show instead, or null for its own. */
    fun line(m: Moment, count: Int? = null, afterMs: Long = 0L): ExtraLine? = null

    /** A moment with no bubble of its own. */
    fun say(m: Moment) {}

    /** A spoken line's own narration (an asset path) for the profile open: none here. */
    fun narration(key: String): String? = null

    /** A step, a try or a run began. */
    fun stepBegan() {}

    /** Runs [then] once no line of the extra plays: at once here. */
    fun afterLine(then: () -> Unit) = then()

    /** The step pop's chime sounds for [ms]. */
    fun chime(ms: Long) {}

    /** Whatever the extra plays stops (a page moving on, a screen left). */
    fun hush() {}

    /** Its part of Settings > Playing, under Encouragement: nothing. */
    @Composable fun SettingsPart(prefs: Prefs, sub: @Composable (String) -> Unit) {}

    /** Its words over every screen: nothing. */
    @Composable fun Overlay() {}
}
