package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.lessons.CheerFrequency

/*
 * 2.15, the Plain look. The MiroFish launch-month simulation (docs/MIROFISH-REPORT-2026-10-26.md, untracked): 23 of 40
 * personas found the mascot, the cheers and the rewards childish, and 25 of 40 asked for a plain mode with the pup, the
 * cheers and the streaks off. Settings > Look > "Plain look", per profile, off by default, offered to grown-ups and big
 * kids only: a little kid's profile never shows it and ignores it if it is set (a band change keeps the stored value).
 *
 * The screens read ONE switch, ui/Plain.kt `LocalPlain` (MainActivity provides [on] for the open profile); every number and
 * word here is Android-free and tested (PlainLookTest). Nothing in the data changes: rest days, freezes, XP, stickers, the
 * brief and the tester events go on underneath; only the setting change is logged (Briefs.setting "plainLook").
 */

/** What one element of the app does for one audience: as before, a plain stand-in, not drawn, or off until asked for. */
enum class PlainShow { SHOWN, PLAIN, HIDDEN, ON_REQUEST }

/** Every element the Plain look touches (the rule table's rows, [PlainLook.rule]). */
enum class PlainPart(val label: String) {
    PUP("The pup, everywhere he is drawn"),
    PUP_TAP("Tap the pup to replay"),
    PUP_WORDS("The pup's speech bubble"),
    CHEER_POPS("Cheer pops"),
    ENCOURAGEMENT_SETTING("Settings: Encouragement"),
    STEP_POP("Step pop picture"),
    STEP_CHIME("Step pop chime"),
    FINISH_PARTY("Confetti, sunburst, fanfare"),
    STREAK("Streak counter and streak lines"),
    STREAK_ENDED("The comeback's ended streak line"),
    REST_MOON("Rest day moon"),
    DAYS_STRIP("Days played strip"),
    REWARD_REVEALS("Stickers, wearables, eggs, sparkles"),
    REWARDS_CHIP("See rewards chip"),
    XP_ON_FINISH("XP line on a finish"),
    LEVEL_UP_SCREEN("Level up screen and achievement banners"),
    LEVEL_ON_YOU("XP and level on You"),
    SURPRISE_GIFT("Tomorrow's surprise"),
    PUP_WORLD_CARD("The pup's world card on You"),
    QUEST("Pup's quest stars on Today"),
    SPOKEN_LINES("Spoken lines"),
    PUP_COPY("The pup's voice and exclamation lines"),
    COMEBACK_CARD("The comeback card"),
    PLAIN_SETTING("Settings: Plain look"),
    PLAIN_OFFER("The one-time Plain look offer"),
}

object PlainLook {
    // ---- who and when ----

    /** The switch is offered (Settings, the one-time card) to grown-ups and big kids; never on a little kid's profile. */
    fun offered(a: Audience): Boolean = a != Audience.LITTLE_KID

    /** The Plain look in force: the stored switch on a profile it is offered to. A little kid's profile ignores it. */
    fun on(a: Audience, stored: Boolean): Boolean = stored && offered(a)

    /**
     * The rule table: what [part] does for [a] with the stored switch [stored]. A little kid's column is the same with the
     * switch on or off (PlainLookTest). The off columns are the app as it was (2.14).
     */
    fun rule(part: PlainPart, a: Audience, stored: Boolean): PlainShow {
        val little = a == Audience.LITTLE_KID
        if (!on(a, stored)) return off(part, little)
        return when (part) {
            PlainPart.PUP, PlainPart.PUP_TAP, PlainPart.CHEER_POPS, PlainPart.FINISH_PARTY, PlainPart.STREAK, PlainPart.STREAK_ENDED,
            PlainPart.REST_MOON, PlainPart.REWARD_REVEALS, PlainPart.REWARDS_CHIP, PlainPart.XP_ON_FINISH, PlainPart.LEVEL_UP_SCREEN,
            PlainPart.SURPRISE_GIFT, PlainPart.PUP_WORLD_CARD, PlainPart.QUEST, PlainPart.PLAIN_OFFER -> PlainShow.HIDDEN
            PlainPart.PUP_WORDS, PlainPart.ENCOURAGEMENT_SETTING, PlainPart.STEP_POP, PlainPart.DAYS_STRIP, PlainPart.PUP_COPY,
            PlainPart.COMEBACK_CARD -> PlainShow.PLAIN
            PlainPart.STEP_CHIME, PlainPart.SPOKEN_LINES -> PlainShow.ON_REQUEST
            PlainPart.LEVEL_ON_YOU, PlainPart.PLAIN_SETTING -> PlainShow.SHOWN
        }
    }

    /** The app without the Plain look: a little kid's ([little]) and a grown-up's or big kid's. */
    private fun off(part: PlainPart, little: Boolean): PlainShow = when (part) {
        // Things a little kid never had: the ended streak's line (no losses), the chip, XP numbers, the level-up screen.
        PlainPart.STREAK_ENDED, PlainPart.REWARDS_CHIP, PlainPart.XP_ON_FINISH, PlainPart.LEVEL_UP_SCREEN ->
            if (little) PlainShow.HIDDEN else PlainShow.SHOWN
        // The wrapped gift is the map's (Ages.gift): the tabs get the "Tomorrow:" line instead.
        PlainPart.SURPRISE_GIFT -> if (little) PlainShow.SHOWN else PlainShow.HIDDEN
        // The switch and its offer are never a little kid's.
        PlainPart.PLAIN_SETTING, PlainPart.PLAIN_OFFER -> if (little) PlainShow.HIDDEN else PlainShow.SHOWN
        else -> PlainShow.SHOWN
    }

    // ---- what the switch changes underneath (read where the app decides; nothing stored is rewritten) ----

    /** Encouragement as the cheer pops read it: Off in the Plain look, whatever was chosen (the choice is kept). */
    fun cheerFrequency(plain: Boolean, chosen: CheerFrequency): CheerFrequency = if (plain) CheerFrequency.OFF else chosen

    /**
     * Spoken guidance: off by default in the Plain look. [plainVoice] is the switch as set while Plain is on (null until
     * then); [voice] the usual one, kept for when Plain goes off again.
     */
    fun voiceOn(plain: Boolean, voice: Boolean, plainVoice: Boolean?): Boolean = if (plain) plainVoice ?: false else voice

    /** The step pop's chime: App sound on, and in the Plain look only with "Step sound" on (off by default). */
    fun stepChime(plain: Boolean, sound: Boolean, stepSound: Boolean): Boolean = sound && (!plain || stepSound)

    /** A finish screen's confetti, sunburst, fanfare and flourish: none in the Plain look. */
    fun party(plain: Boolean, party: Boolean): Boolean = party && !plain

    // ---- the one-time offer ----

    /** The moments the offer may show: the first lesson finished after 2.15, or opening the app after [OFFER_DAYS] days played. */
    enum class Moment { LESSON_FINISHED, APP_OPENED }

    /**
     * 2.17 (audit M6): a lesson's finish offers it at the end of session 2, not at the first lesson's peak: on the second day
     * played or later (days in the practice log, today included).
     */
    const val SESSION_TWO_DAYS = 2

    /** Days played (any practice) before opening the app brings the offer. */
    const val OFFER_DAYS = 7

    /**
     * The card "Prefer a plainer look?" now: a grown-up or big kid ([a]) without the Plain look ([stored] off), never
     * offered before on this profile ([offeredBefore]), at a lesson's finish, or on opening the app after [daysPlayed] of
     * at least [OFFER_DAYS]. Once per profile, never to a little kid. 2.15 merge: [flag] is the dashboard's
     * `plain_look_offer` (AppFlags.plainLookOffer, true without PostHog), asked last, only when everything else says yes, so
     * its exposure event counts only those who would see the card; false holds the card back and marks nothing.
     */
    fun offerDue(a: Audience, stored: Boolean, offeredBefore: Boolean, moment: Moment, daysPlayed: Int, flag: () -> Boolean = { true }): Boolean =
        offered(a) && !stored && !offeredBefore && when (moment) {
            Moment.LESSON_FINISHED -> daysPlayed >= SESSION_TWO_DAYS
            Moment.APP_OPENED -> daysPlayed >= OFFER_DAYS
        } && flag()

    // ---- words (sentence case, no dashes, no exclamation marks) ----

    const val TITLE = "Plain look"
    const val LINE = "No pup, no cheers, no streak counter. Just you and the piano."
    const val ENCOURAGEMENT_OFF = "Off in Plain look"
    const val STEP_SOUND = "Step sound"
    const val STEP_SOUND_LINE = "A short chime when you pass a step."
    const val OFFER_LINE = "Prefer a plainer look? Turn off the pup, cheers and streaks."
    const val OFFER_YES = "Try Plain look"
    const val OFFER_NO = "No thanks"

    /** The comeback card's title in the Plain look (a grown-up's and a big kid's). */
    const val COMEBACK_TITLE = "Welcome back."

    /** Its line: "Pick up where you left off: Your first melody." (no item: the sentence alone). */
    fun comebackLine(item: String?): String = item?.takeIf { it.isNotBlank() }?.let { "Pick up where you left off: $it." } ?: "Pick up where you left off."

    /** The card's text as one read: "Welcome back. Pick up where you left off: <item>." */
    fun comeback(item: String?): String = "$COMEBACK_TITLE ${comebackLine(item)}"

    /** A lesson's finish title. */
    const val LESSON_DONE = "Lesson complete."

    /** The cheer bar's titles for a step passed (the grown-up bank's CHEER_TITLES, without the exclamations). */
    val STEP_TITLES = listOf("Step passed.", "Well played.", "That's it.", "Nicely done.", "Clean.", "Passed.")

    fun stepTitle(seed: Int): String = STEP_TITLES[Math.floorMod(seed, STEP_TITLES.size)]

    /** A step let go after three tries ("Good try!"), and one that took a few tries ("High five!"). */
    const val LET_GO_TITLE = "Moving on."
    const val STUCK_TITLE = "Passed."
    const val STUCK_SPOKEN = "You stuck with it."

    /** The Goodbye's words for a big kid in the Plain look ("Goodnight from the pup!" and the sleeping pup are not). */
    fun goodbye(name: String?, asleep: Boolean, capped: Boolean): String {
        val n = name?.trim()?.takeIf { it.isNotEmpty() }?.let { ", $it" }.orEmpty()
        return when {
            capped -> "That's all for today$n. See you tomorrow."
            asleep -> "Lessons open again tomorrow$n."
            else -> "That's today's playing done$n. See you tomorrow."
        }
    }

    /** The Goodbye's Jam button ("Jam with the pup"). */
    const val JAM = "Open the Jam"

    /** Lines that fall back to a plain one of their own (the rest go through [calm]'s rule). */
    private val BANK: Map<String, String> = buildMap {
        com.bigpapituna.sharpears.engine.CHEER_TITLES.forEachIndexed { i, t -> put(t, STEP_TITLES[i % STEP_TITLES.size]) }
        put("High five!", STUCK_TITLE)
        put(com.bigpapituna.sharpears.engine.HIGH_FIVE, STUCK_SPOKEN)
        put("Good try!", LET_GO_TITLE)
        put("Lesson complete!", LESSON_DONE)
        put("Session done!", "Session done.")
        put("Level up!", "Level up.")
    }

    private val SENTENCE = Regex("""(?<=[.!?])\s+""")
    private val PUP_VOICE = Regex("""\bwoof\b|\bthe pup\b|\bpaws?\b""", RegexOption.IGNORE_CASE)

    /**
     * Any line a grown-up or big kid sees or hears in the Plain look: a line of the plain bank when it has one ([BANK]);
     * otherwise its pup-voiced sentences go ("Woof!", "Goodnight from the pup!") and a trailing exclamation mark becomes a
     * full stop. Only the end: "Oh! Susanna" inside a line keeps its own. A line that was nothing but the pup's becomes
     * [PUP_ONLY] on screen; said aloud ([spoken], the voice's path) it is kept, calmed, since it may be an instruction
     * ("Find the paw key!").
     */
    fun calm(text: String, spoken: Boolean = false): String {
        BANK[text]?.let { return it }
        if (text.isBlank()) return text
        val all = text.trim().split(SENTENCE)
        val kept = all.filterNot { PUP_VOICE.containsMatchIn(it) }.ifEmpty { if (spoken) all else emptyList() }
        if (kept.isEmpty()) return PUP_ONLY
        val joined = kept.joinToString(" ").trim()
        return if (joined.endsWith("!")) joined.trimEnd('!').trimEnd() + "." else joined
    }

    /** What a line made only of the pup's words ("Woof!") becomes. */
    const val PUP_ONLY = "Well played."

    /**
     * A streak line in the brief's own week (a fact, the praise): hidden in the Plain look. Days played and minutes are not
     * streak lines.
     */
    fun mentionsStreak(line: String): Boolean = STREAK_WORDS.containsMatchIn(line)

    private val STREAK_WORDS = Regex("""\bstreaks?\b|\bdays? in a row\b|\bin a row with\b|\bdays running\b""", RegexOption.IGNORE_CASE)

    /**
     * A grown-up's own week in the Plain look is written without the goal streak's praise (its headline, its "one thing to
     * praise"): the brief's writers pick the next praise. Read from the findings as they are loaded; nothing stored changes.
     */
    fun withoutStreaks(f: com.bigpapituna.sharpears.engine.brief.Findings): com.bigpapituna.sharpears.engine.brief.Findings =
        f.copy(praise = f.praise.filterNot { it.kind == com.bigpapituna.sharpears.engine.brief.PraiseKind.GOAL_STREAK })
}
