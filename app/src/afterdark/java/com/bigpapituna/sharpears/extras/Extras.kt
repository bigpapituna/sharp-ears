package com.bigpapituna.sharpears.extras

import android.content.Context
import androidx.compose.runtime.Composable
import com.bigpapituna.sharpears.afterdark.CoachHost
import com.bigpapituna.sharpears.afterdark.CoachOverlay
import com.bigpapituna.sharpears.afterdark.CoachSettings
import com.bigpapituna.sharpears.audio.MicInput
import com.bigpapituna.sharpears.audio.Voice
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.engine.family.Profile

/**
 * The extra's door on a build with it (src/afterdark; app/build.gradle.kts picks this file when secrets.properties has
 * AFTERDARK=true): the 2.16 coach voice, for grown-ups only (CLAUDE.md "2.16 coach voice"). src/noafterdark has the same
 * object with the same calls doing nothing; the screens call only these.
 */
object Extras {
    /** This build has the extra. */
    const val BUILT = true

    /** What's new lines the extra adds, by version (merged after the app's own: engine/nav/Welcome.merged). */
    val WHATS_NEW: Map<String, List<String>> = mapOf(
        "2.16.0" to listOf("Grown-ups can turn on a coach voice in Settings, under Encouragement: pick a low, flirty voice to cheer you on. Adults only, and off until you turn it on."),
        // 2.16.1: the Fold never had 2.16.0, so this one says it all again, with the new tab and the four voices.
        "2.16.1" to listOf("Grown-ups can turn on a coach voice in Settings, Playing, under Encouragement: Liam, Rory, Claire or Sienna, low and flirty, to cheer you on. Adults only, and off until you turn it on."),
        // 2.16.2: the course narrated in Liam's voice (assets/coach/male_us/course); the app itself has nothing new to say.
        "2.16.2" to listOf("Liam now talks you through every lesson."),
        // 2.17.0: a third answer, a louder Liam, the notes a little quieter under him, quiet until the first step is passed.
        "2.17.0" to listOf("The coach can now just use your name, Liam is a little louder over the notes, and the coach waits for your first note before talking."),
    )

    /** MainActivity opened a profile: [who] is read live at every moment (a profile made a kid is a kid at once). */
    fun open(context: Context, who: () -> Profile, prefs: Prefs, mic: MicInput, voice: Voice) = CoachHost.open(context, who, prefs, mic, voice)

    /** The profile closed or the app left the screen: any line stops. */
    fun close() = CoachHost.close()

    /**
     * A moment the screen shows words for in its own bubble (a cheer pop, the step pop's line): the coach's words to show
     * instead, for how long, or null (the screen's own). [count]: a run's notes or the focus minutes; [afterMs]: the line starts
     * that much later (the step pop's chime first).
     */
    fun line(m: Moment, count: Int? = null, afterMs: Long = 0L): ExtraLine? = CoachHost.line(m, count, afterMs)

    /** A moment with no bubble of its own: the line plays when the voice is quiet, its words over the screen. */
    fun say(m: Moment) = CoachHost.say(m)

    /**
     * A spoken line's narration in the coach's voice (assets/coach/<voice>/course/<SpokenCatalog key>[_w|_m].ogg), as an asset
     * path, when the coach is on for a grown-up profile and that line is recorded; null otherwise (the normal voice). Called on
     * the voice's worker thread.
     */
    fun narration(key: String): String? = CoachHost.narration(key)

    /** A step, a try or a run began (a wrong key and a hint may have their line again). */
    fun stepBegan() = CoachHost.stepBegan()

    /** Runs [then] once no line of the coach's waits or plays (at once when none does). */
    fun afterLine(then: () -> Unit) = CoachHost.afterLine(then)

    /** The step pop's chime sounds for [ms]: a line playing stops, one waiting waits. */
    fun chime(ms: Long) = CoachHost.chime(ms)

    /** Whatever plays or waits stops (a page moving on, a screen left). */
    fun hush() = CoachHost.hush()

    /**
     * 2.17 (audit M3): [on] holds every line (none plays or waits) until it is let go: a profile's first lesson holds them until
     * a step is passed, so the first note is heard. What is held is dropped, not kept for later.
     */
    fun hold(on: Boolean) = CoachHost.hold(on)

    /** Settings > Playing, under Encouragement: Coach voice (a grown-up's profile only). */
    @Composable fun SettingsPart(prefs: Prefs, sub: @Composable (String) -> Unit) = CoachSettings(prefs, sub)

    /** The words of a line with no bubble of its own, over every screen. */
    @Composable fun Overlay() = CoachOverlay()
}
