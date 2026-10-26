package com.bigpapituna.sharpears.engine.family

/*
 * Levels and ages (2.14). The persona walk of 2.13 (docs/PERSONA-PLAN-2026-10-26.md) put a 9 year old and a 12 year old on
 * the same pond, frogs and nursery songs as a 5 year old. A kid's profile now carries an age band: a little kid (4 to 7)
 * keeps the pup's world; a big kid (8 to 13) gets the tabs in Simple with their own progress, the pop charts first on Songs,
 * the arcade and Pup says, a small helper pup and plain words, and stays a kid for the parent gate, the parent brief and
 * its email, bedtime and the tester's profile kind. [Profile.isKid] keeps meaning "a kid" for all of those; every place that
 * draws the little kids' world asks [Ages] instead. Pure, so the routing is tested on the JVM (AgesTest).
 */

/** How old a kid is, as the grown-up adding them says it: the little ones get the pup's world, the big kids the tabs. */
enum class AgeBand(val title: String, val ages: String) {
    LITTLE("Little", "4 to 7"),
    BIG("Big kid", "8 to 13"),
    ;

    /** "Little, 4 to 7": the answer as the new-kid flow and Family show it (words, never a dash). */
    val answer: String get() = "$title, $ages"

    companion object {
        /** The band stored as [name]; anything else, or nothing (every kid made before 2.14), is [LITTLE]. */
        fun parse(name: String?): AgeBand = entries.firstOrNull { it.name == name } ?: LITTLE

        /** The new-kid flow's question. */
        fun question(name: String): String = if (name.isBlank()) "How old are they?" else "How old is $name?"

        /** What each answer gives, under the two pills. */
        fun line(band: AgeBand?): String = when (band) {
            LITTLE -> "The pup's world: a map of games, stickers and colour notes."
            BIG -> "The tabs, with pop songs first, plain words and a small helper pup. Still a kid for the grown-ups' gate."
            null -> "Little ones get the pup's world. Big kids get the tabs and pop songs."
        }
    }
}

/** Who the app is talking to: a little kid (the pup's world), a big kid (the tabs, plain words) or a grown-up. */
enum class Audience {
    LITTLE_KID, BIG_KID, GROWN_UP;

    companion object {
        fun of(kind: ProfileKind, band: AgeBand): Audience = when {
            kind == ProfileKind.ADULT -> GROWN_UP
            band == AgeBand.BIG -> BIG_KID
            else -> LITTLE_KID
        }
    }
}

/** One rule per question the screens ask about who is playing (AgesTest has the table). */
object Ages {
    /** The pup's world is the home (the map, the sticker trail, the finales, the kid songs, the wrapped gift): little kids. */
    fun mapHome(a: Audience): Boolean = a == Audience.LITTLE_KID

    /** The five tabs (Today, Songs, Learn, Practice, You): grown-ups and big kids. */
    fun tabs(a: Audience): Boolean = !mapHome(a)

    /**
     * The kid skin in lessons, the player and the finish screens: colours and animals, bites, the pup's words with the name,
     * no numbers, slower phone speech. Little kids only; a big kid reads the grown-up screens.
     */
    fun kidSkin(a: Audience): Boolean = a == Audience.LITTLE_KID

    /** The cheer pops' kid bank (the pup's own voice, woofs and all): little kids. Big kids get the grown-up bank. */
    fun kidCheers(a: Audience): Boolean = a == Audience.LITTLE_KID

    /** A kid for the grown-ups: the parent gate, the parent brief and its email, bedtime, the mic offset, the tester's kind. */
    fun kidForGrownUps(a: Audience): Boolean = a != Audience.GROWN_UP

    /** The course: the Playground's pages first for a little kid; Basics first (the pages an extra) for everyone else. */
    fun firstUnitId(a: Audience): String = if (a == Audience.LITTLE_KID) "playground" else "basics"

    /** Songs by chords whole, whatever the layout (Simple keeps 35 of the songs for a grown-up beginner): big kids. */
    fun allPopSongs(a: Audience): Boolean = a == Audience.BIG_KID

    /** The Songs tab opens on Songs by chords (the songs their friends know) rather than the library: big kids. */
    fun songsOpenOnChords(a: Audience): Boolean = a == Audience.BIG_KID

    /** The arcade (ui/arcade) from the Play corner in the tabs: big kids (a little kid's is on the map's Play sheet). */
    fun arcadeInTabs(a: Audience): Boolean = a == Audience.BIG_KID

    /** Levels, XP and the level-up screen: whoever is not in the pup's world. */
    fun showsLevels(a: Audience): Boolean = a != Audience.LITTLE_KID

    /** Tomorrow's surprise, the wrapped gift on the map: little kids; the tabs get the "Tomorrow:" line instead. */
    fun gift(a: Audience): Boolean = a == Audience.LITTLE_KID

    /** The comeback's line about the streak that ended: grown-ups and big kids (a little kid sees no numbers and no losses). */
    fun streakLine(a: Audience): Boolean = a != Audience.LITTLE_KID

    /** Lines made on the phone: slower for a little kid (KIDS-PLAN 3.8); recorded lines carry their own speed. */
    fun voiceSpeed(a: Audience): Float = if (a == Audience.LITTLE_KID) 0.85f else 1f

    /** The launch's layout chooser: a grown-up who never chose. A big kid starts in Simple and is never asked. */
    fun asksLayout(a: Audience, chosen: com.bigpapituna.sharpears.engine.nav.Layout?): Boolean = a == Audience.GROWN_UP && chosen == null
}
