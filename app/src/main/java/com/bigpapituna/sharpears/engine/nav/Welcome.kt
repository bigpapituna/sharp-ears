package com.bigpapituna.sharpears.engine.nav

/**
 * The 2.7 tour and the What's new sheet, for grown-ups only (kids see neither). The tour is three cards with the pup, shown
 * once per profile (after the first-run questions and "Let's hear you play", or on a profile's first open of 2.7) and
 * skippable; What's new is up to three lines from [WHATS_NEW], shown once per versionName to a profile that was here before
 * it. Kept per profile in Prefs (tourDone, whatsNewSeen). Pure, so WelcomeTest checks when each shows.
 */
object Welcome {
    data class Card(val title: String, val line: String, val tab: NavTab)

    /** The tour: one card per tab that changed, each over its tab (the titles are Roy's). */
    val TOUR = listOf(
        Card("Today is your plan", "One card holds today's chords, lesson and song. Press Start and it walks you through them.", NavTab.TODAY),
        Card("Songs is everything you can play", "Your library, songs by chords with the band, and the Play corner at the top.", NavTab.SONGS),
        Card("Learn is the road, with chords at the top", "Learn chords, Games and Progressions sit above the course path.", NavTab.LEARN),
    )

    const val MAX_LINES = 3

    /** What's new, by versionName: three lines at most, shown once to a grown-up opening that version for the first time. */
    val WHATS_NEW: Map<String, List<String>> = mapOf(
        "2.7.0" to listOf(
            "Today is the new home: your plan for the day with one Start.",
            "Songs holds the library, songs by chords and the Play corner.",
            "Chords moved to the top of Learn. The metronome and tuner are under Tools in Practice.",
        ),
    )

    fun whatsNew(version: String, table: Map<String, List<String>> = WHATS_NEW): List<String> = table[version].orEmpty().take(MAX_LINES)

    enum class Show { NOTHING, WHATS_NEW, TOUR }

    /**
     * What a grown-up's home shows now: What's new first (once per [version], if the table has lines for it and [whatsNewSeen]
     * is another version), then the tour (once per profile), then nothing. A kid: nothing, ever.
     */
    fun next(kid: Boolean, tourDone: Boolean, whatsNewSeen: String?, version: String, table: Map<String, List<String>> = WHATS_NEW): Show = when {
        kid -> Show.NOTHING
        whatsNewSeen != version && whatsNew(version, table).isNotEmpty() -> Show.WHATS_NEW
        !tourDone -> Show.TOUR
        else -> Show.NOTHING
    }
}
