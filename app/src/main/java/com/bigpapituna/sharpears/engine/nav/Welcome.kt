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
        Card("Learn is the road, with chords at the top",
            "Learn chords, Games and Progressions sit above the course path. Simple shows each place's first steps, Advanced adds lessons, songs and practice, Full shows it all.",
            NavTab.LEARN),
    )

    const val MAX_LINES = 3

    /** What's new, by versionName: three lines at most, shown once to a grown-up opening that version for the first time. */
    val WHATS_NEW: Map<String, List<String>> = mapOf(
        "2.7.0" to listOf(
            "Today is the new home: your plan for the day with one Start.",
            "Songs holds everything you can play. Chords sit at the top of Learn, the metronome and tuner under Tools.",
            "Simple mode shows each place's first steps. Advanced, on Today, You or in Settings, shows it all.",
        ),
        "2.8.0" to listOf(
            "Three layouts: Simple shows the first steps, Advanced lessons, songs and practice, Full everything. Change it on You or in Settings.",
            "One email place: your address and each child's daily or weekly brief live in Settings, under Briefs and email.",
            "The games roll three new keys each round once a game's first round is done.",
        ),
        "2.8.1" to listOf(
            "My songs, at the top of Songs, takes a MIDI file of a song you love and makes its tune easy to play.",
        ),
        "2.9.0" to listOf(
            "Rest days: miss a single day in a week and your streak stays.",
            "Back after a few days away? Today starts you with three easy minutes.",
            "Reach the day's goal and the finish screen names tomorrow's next step. Kids get a surprise that opens the next day.",
        ),
        "2.10.0" to listOf(
            "The pup comes alive: he breathes, blinks, jumps for joy when you finish well and tilts his head at a wrong note.",
            "Kids see him wag at every right note, point at the keys in a lesson and bob along in Clap along.",
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
