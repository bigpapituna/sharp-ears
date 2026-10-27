package com.bigpapituna.sharpears.engine.nav

/**
 * The 2.7 tour and the What's new sheet, for grown-ups only (kids see neither). The tour is shown once per profile (after the
 * first-run questions and "Let's hear you play", or on a profile's first open of 2.7) and skippable; What's new is up to three
 * lines from [WHATS_NEW], shown once per versionName to a profile that was here before it. Kept per profile in Prefs (tourDone,
 * whatsNewSeen). Pure, so WelcomeTest checks when each shows.
 *
 * 2.15 (show off): the tour became the five things that matter most, each with a small picture of the real screen ([TourArt]),
 * and a short "For grown-ups" tour ([PARENTS_TOUR]) follows the first kid a grown-up adds, once per phone.
 */
object Welcome {
    /** The little picture on a card (ui/tour/TourArt.kt draws each from the app's own colours and icons). */
    enum class TourArt { PLAN, BAND, TIMING, LOOP, STREAK, FAMILY, LIMIT, GATE }

    data class Card(val title: String, val line: String, val art: TourArt)

    /** The tour: the five things that matter most, one card each, one sentence each (2.15). Skippable; five at most. */
    val TOUR = listOf(
        Card("One Start for your day", "Press Start and Today walks you through your chords, a lesson and a song.", TourArt.PLAN),
        Card("Play songs by chords with a band", "Pick a song you know and play its chords while a drum and bass band keeps time.", TourArt.BAND),
        Card("See your timing, bar by bar", "After a song in Perform, a strip shows each bar as clean, rushed or dragged, and a tap shows its notes.", TourArt.TIMING),
        Card("Loop the bars you missed", "After a song, the hardest bars get a Loop button that repeats just that bar, slowly.", TourArt.LOOP),
        Card("A streak that forgives", "Miss one day in a week and your streak stays, shown as a rest paw.", TourArt.STREAK),
    )

    const val MAX_TOUR_CARDS = 5

    /**
     * The tour as this grown-up sees it (2.15.0 merge): in the Plain look ([plain]: no streak counter anywhere) card 5, "A streak
     * that forgives", is skipped rather than reworded (its picture is a week of paws and a rest paw, which Plain also hides), so
     * the tour is four cards and the last of them ends it. Without Plain, the five.
     */
    fun tour(plain: Boolean): List<Card> = if (plain) TOUR.filter { it.art != TourArt.STREAK } else TOUR

    /**
     * "For grown-ups": three cards after a grown-up adds the first kid (2.15). The four things a parent needs are paired so the
     * tour stays at three: the Family today card with the brief it opens, the daily time, and the gate with Lock now. [emailed]
     * is whether this build can email a brief (BriefEmail.hasKey): without it the brief card does not promise an email.
     */
    fun parentsTour(emailed: Boolean): List<Card> = listOf(
        Card("Family today, and the brief",
            "Each child's day is on your Today: minutes, lessons and stars. Tap a child to read their brief, what they played and what to try next" +
                (if (emailed) ", and have it emailed to you." else "."), TourArt.FAMILY),
        Card("A daily limit that closes the app",
            "Set a daily time for each child in Family. When it is up, lessons, songs and games close until tomorrow.", TourArt.LIMIT),
        Card("A gate, and Lock now",
            "The grown-ups' corner opens with a maths question and shuts again after two minutes. Lock now closes it at once.", TourArt.GATE),
    )

    const val PARENTS_TOUR_CARDS = 3

    /** The phone's "For grown-ups" state, kept in the profiles file: nothing yet, waiting for a grown-up's screen, or seen. */
    const val PARENTS_PENDING = "pending"
    const val PARENTS_DONE = "done"

    /** A kid was added: the first kid on a phone that never had the tour starts it ([hadKid]: there was a kid already). */
    fun parentsStateAfterAdd(hadKid: Boolean, state: String?): String? = if (!hadKid && state == null) PARENTS_PENDING else state

    fun parentsTourDue(state: String?): Boolean = state == PARENTS_PENDING

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
            "He cheers after each lesson, and kids see him wave hello, dance at the finale and curl up at bedtime.",
        ),
        "2.11.0" to listOf(
            "Every step you pass pops with a check and a quick chime that climbs higher with each step in a row.",
        ),
        "2.12.0" to listOf(
            "The pup has new moves: a bigger jump, a proper wave, and a celebration after every lesson.",
        ),
        "2.13.0" to listOf(
            "While you play, the pup now pops up with a few kind words when you're on a roll. Change how often in Settings.",
        ),
        "2.14.0" to listOf(
            "Big kids from 8 to 13 get their own tabs with pop songs first, and day one starts at a level that fits you.",
            "Parents get a family card on Today, a daily time limit for each child, and Lock now to close the grown-ups' corner at once.",
            "Tap the pup to see him cheer again, and after a song in Perform see your timing bar by bar.",
        ),
    )

    fun whatsNew(version: String, table: Map<String, List<String>> = WHATS_NEW): List<String> = table[version].orEmpty().take(MAX_LINES)

    enum class Show { NOTHING, WHATS_NEW, TOUR, PARENTS }

    /**
     * What a grown-up's home shows now: What's new first (once per [version], if the table has lines for it and [whatsNewSeen]
     * is another version), then the tour (once per profile), then "For grown-ups" ([parentsTourDue]: a first kid was added and
     * nobody has seen it yet; once per phone), then nothing. A kid: nothing, ever. Never two at once.
     */
    fun next(kid: Boolean, tourDone: Boolean, whatsNewSeen: String?, version: String, table: Map<String, List<String>> = WHATS_NEW,
             parentsTourDue: Boolean = false): Show = when {
        kid -> Show.NOTHING
        whatsNewSeen != version && whatsNew(version, table).isNotEmpty() -> Show.WHATS_NEW
        !tourDone -> Show.TOUR
        parentsTourDue -> Show.PARENTS
        else -> Show.NOTHING
    }
}
