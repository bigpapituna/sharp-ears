package com.bigpapituna.sharpears.engine.nav

/**
 * How much each place shows (2.8, Roy; 2.7 had one Simple switch): Simple keeps each place to its first steps, Advanced adds
 * the whole course, Practice, every song and the song packs, Full is the whole app. Kept per grown-up (Prefs.layout), asked
 * once at launch, changed on You or in Settings; a kid is never asked and never cut. The rules per place are in [Simple].
 */
enum class Layout(val title: String, val line: String) {
    SIMPLE("Simple", "The first steps: six chords, two games and three scales."),
    ADVANCED("Advanced", "Lessons, songs and practice: the whole course, every song and scale."),
    FULL("Full", "Everything: every chord, game and setting."),
    ;

    /** As Prefs keeps it and the brief logs it: "simple", "advanced", "full". */
    val wire: String get() = name.lowercase()

    companion object {
        /** The launch chooser's title and note. */
        const val QUESTION = "How much do you want on screen?"
        const val NOTE = "Change any time on You or in Settings."

        /** A wire name back to its layout; anything else ("unset", null) is no choice. */
        fun parse(s: String?): Layout? = s?.trim()?.lowercase()?.let { w -> entries.firstOrNull { it.wire == w } }

        /** 2.7's Simple switch, carried over: on is Simple, off is Full (2.7's Advanced was the whole app). */
        fun fromSimpleMode(on: Boolean): Layout = if (on) SIMPLE else FULL
    }
}
