package com.bigpapituna.sharpears.engine.lessons

/**
 * What the pup says in a cheer pop (2.13), in two banks: KID (his own voice, playful, two to six words, a stop or an
 * exclamation at the end; the nudge may take seven, for Roy's "Find the next key, I'm right here.") and GROWN-UP (warm and
 * plain, never childish or gushy: no exclamation marks, no woofs). At least [MIN_PER_FAMILY] lines per trigger family per
 * audience, plus the runs' lines with the number ("10 in a row." for a grown-up, "Ten in a row!" for a kid: kid lines carry
 * no digits). Copy rules: sentence case, no dashes or symbol glyphs, nothing about wrong notes (a recovery celebrates the
 * save). Android-free; CheerLinesTest keeps every rule, SpokenCatalog lists every line that can be said aloud.
 *
 * The step passed lines are shown with the step pop (2.11) and never said: the step's own line is spoken after its chime.
 * None of them is one of the cheer bar's titles (CHEER_TITLES), which show at the same moment.
 */
object CheerLines {
    const val MIN_PER_FAMILY = 12

    /** The runs that get a pop, in clean notes in a row. */
    val RUN_MILESTONES = listOf(5, 10, 20, 40)
    /** The focus pops that name their minutes; longer stretches use the plain focus lines. */
    val FOCUS_MINUTES = listOf(5, 10, 15, 20, 30)

    /** A number as a kid hears it (the kid lines carry no digits). */
    fun word(n: Int): String = WORDS[n] ?: error("no word for $n")
    private val WORDS = mapOf(5 to "five", 10 to "ten", 15 to "fifteen", 20 to "twenty", 30 to "thirty", 40 to "forty")
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

    // ---- Runs ---------------------------------------------------------------------------------------------------------

    /** A run's line with its number: {N} the number as a word, capitalised; {n} lower case; {d} digits (grown-ups). */
    private val KID_RUN_NUMBER = listOf(
        "{N} in a row!", "Woof! {N} in a row!", "That's {n} in a row!", "{N} in a row! Keep going!", "Wow, {n} in a row!",
        "{N} notes in a row!", "Paws up! {N} in a row!", "{N} in a row! Yay!",
    )
    private val GROWN_RUN_NUMBER = listOf(
        "{d} in a row.", "{d} clean notes in a row.", "That's {d} in a row.", "{d} in a row. Nice.", "{d} in a row. Keep going.",
        "{d} notes, all clean.", "{d} in a row. Lovely.", "A run of {d}. Good hands.",
    )

    /** The run's plain lines (Roy's "Keep going", "Don't stop. You're doing so well"): a run of five or ten may say one. */
    private val KID_RUN = listOf(
        "You're on fire!", "Keep going!", "Don't stop, you're doing so well!", "Wow, look at you go!", "Super playing!",
        "Yay! Keep it up!", "Your fingers are dancing!", "That sounds so good!", "Woof woof! Amazing!", "You're a piano star!",
        "Tail wags for you!", "Go, go, go!",
    )
    private val GROWN_RUN = listOf(
        "Nice and steady.", "Keep going, you've got this.", "Don't stop. You're doing really well.", "That's the way.",
        "Lovely playing.", "You're in the flow.", "Keep it rolling.", "Sounding good.", "Clean playing.", "That's how it's done.",
        "Smooth. Keep going.", "You're finding it.", "Lovely touch.",
    )

    fun runNumbered(n: Int, kid: Boolean): List<String> =
        if (kid) KID_RUN_NUMBER.map { it.replace("{N}", cap(word(n))).replace("{n}", word(n)) }
        else GROWN_RUN_NUMBER.map { it.replace("{d}", "$n") }

    // ---- The other families ---------------------------------------------------------------------------------------------

    private val KID_RECOVERY = listOf(
        "Nice save!", "Back on track!", "You found it again!", "Great comeback!", "Woof! You fixed it!", "There you go!",
        "Back in the groove!", "You kept going! Yay!", "That's the spirit!", "Super save!", "Paws up! Back on track!",
        "You never give up!",
    )
    private val GROWN_RECOVERY = listOf(
        "Nice save.", "Back on track.", "Good recovery.", "That's how you keep going.", "Right back in it.", "Smooth recovery.",
        "You kept the flow.", "Picked it right back up.", "Good, straight back in.", "That's the save.", "Back in the groove.",
        "Steady again.", "You found your place.",
    )

    private val KID_HALFWAY = listOf(
        "Halfway there!", "Half done! Woof!", "Halfway! Keep going!", "Half the way already!", "Halfway! You're doing great!",
        "Look, halfway there!", "Halfway! Paws up!", "Half done! So good!", "Halfway there! Yay!", "We're halfway! Keep playing!",
        "Halfway! You've got this!", "Halfway! Don't stop now!",
    )
    private val GROWN_HALFWAY = listOf(
        "Halfway there.", "Half done. Keep going.", "Halfway, and sounding good.", "That's the first half.",
        "Halfway. Nice and steady.", "Half of it down.", "Halfway. Keep the flow.", "The first half is yours.",
        "Halfway through. Lovely.", "Halfway. You've got this.", "Over the halfway mark.", "Halfway, and still going strong.",
    )

    private val KID_HARD = listOf(
        "You got the tricky bit!", "Tricky part, done!", "You beat the hard part!", "Woof! You did it this time!",
        "That tricky bit? Easy now!", "You learned the hard part!", "Yes! The tricky part!", "Practice works! Yay!",
        "You cracked it!", "Hard part? No problem!", "Paws up! Tricky bit done!", "You worked it out!",
        "That part sounds great now!",
    )
    private val GROWN_HARD = listOf(
        "That tricky bar came out clean.", "You got the hard part.", "Clean through the tricky spot.", "That bar is yours now.",
        "The practice is paying off.", "Clean this time. Well earned.", "You worked that one out.", "That passage sits better now.",
        "Through the hard part, cleanly.", "That's the bar you practised.", "Much smoother there.", "You cracked that bar.",
    )

    private val KID_STEADY = listOf(
        "Right on the beat!", "You're keeping the beat!", "Tick tock, so steady!", "Perfect timing!", "Woof! Great rhythm!",
        "You and the beat!", "So steady!", "Right in time!", "Dancing with the beat!", "Paws tapping along!",
        "That's the rhythm!", "Bounce, bounce, on time!", "Super steady!",
    )
    private val GROWN_STEADY = listOf(
        "That's the rhythm.", "Right on the beat.", "Lovely timing.", "Steady as a clock.", "You're locked in.", "Nice and even.",
        "Good, steady pulse.", "Right in time.", "Your timing is lovely.", "In the pocket.", "Even and steady.",
        "That pulse is solid.", "Keeping good time.",
    )

    private val KID_FOCUS = listOf(
        "Wow, you're so focused!", "Super practice!", "You're working so hard!", "Great focus! Woof!", "Look at you practise!",
        "Such a busy pianist!", "Keep it up, superstar!", "You're doing so well!", "Busy paws! Great work!",
        "Practice makes you shine!", "So much playing! Yay!", "You stayed so focused!",
    )
    private val GROWN_FOCUS = listOf(
        "Lovely focus.", "Good, steady practice.", "You've been at it a while. Nice work.", "This is how progress happens.",
        "Focused practice. Lovely.", "Keep that focus.", "Time well spent.", "Good work staying with it.",
        "Your focus is paying off.", "Steady practice. Well done.", "That's real practice.", "Nice, unhurried focus.",
    )

    /** A focus pop at [minutes] of practice may name them (five to thirty); none for other stretches. */
    fun focusMinutes(minutes: Int, kid: Boolean): List<String> {
        if (minutes !in FOCUS_MINUTES) return emptyList()
        // Always five or more (FOCUS_MINUTES), so always "minutes" (CopyCountsTest).
        val m = cap(word(minutes)) + " minutes"
        return if (kid) listOf("$m! Super focus!") else listOf("$m of focus. Lovely.", "$m in. Nice work.")
    }

    private val KID_NUDGE = listOf(
        "Find the next key, I'm right here.", "Take your time!", "Where's the next key? Let's look!", "You can do it!", "Look at the keys!",
        "I'm here, no rush!", "Ready when you are!", "Let's find it together!", "Have a little look!", "Try the next one!",
        "Psst! Your turn!", "Take a breath, then play!", "I believe in you!",
    )
    private val GROWN_NUDGE = listOf(
        "Take your time.", "No rush. Find the next note.", "Take a breath, then the next note.", "Whenever you're ready.",
        "The next note is waiting.", "Have a look, then play.", "Easy does it.", "No hurry at all.",
        "Find your place, then go on.", "Ready when you are.", "One note at a time.", "Take it slowly. That's fine.",
    )

    private val KID_STEP = listOf(
        "Woof! Great job!", "Paws up! Nice!", "Step done! Yay!", "On to the next!", "Ta da!", "Woof woof! Done!", "Nailed it!",
        "Look at you!", "Super job!", "Hip hip hooray!", "You're so clever!", "Another one done!", "Tail wag for that!",
    )
    private val GROWN_STEP = listOf(
        "Good hands.", "Clean step.", "One more down.", "That's a step done.", "Nicely played.", "On to the next one.",
        "Solid playing.", "Neat and tidy.", "Good, that's settled in.", "That one's yours.", "Step by step.",
        "Well done, keep going.", "Lovely, next step.",
    )

    /** The family's own lines (no numbers): what [CheerCoach] picks from, with [runNumbered] and [focusMinutes] added. */
    fun family(trigger: CheerTrigger, kid: Boolean): List<String> = when (trigger) {
        CheerTrigger.RUN -> if (kid) KID_RUN else GROWN_RUN
        CheerTrigger.RECOVERY -> if (kid) KID_RECOVERY else GROWN_RECOVERY
        CheerTrigger.HALFWAY -> if (kid) KID_HALFWAY else GROWN_HALFWAY
        CheerTrigger.HARD_PASSED -> if (kid) KID_HARD else GROWN_HARD
        CheerTrigger.STEADY -> if (kid) KID_STEADY else GROWN_STEADY
        CheerTrigger.FOCUS -> if (kid) KID_FOCUS else GROWN_FOCUS
        CheerTrigger.NUDGE -> if (kid) KID_NUDGE else GROWN_NUDGE
        CheerTrigger.STEP_PASSED -> if (kid) KID_STEP else GROWN_STEP
    }

    /** Every line of [kid]'s bank: each family, every run's numbered lines and every focus minute line. */
    fun all(kid: Boolean): List<String> = (CheerTrigger.entries.flatMap { family(it, kid) } +
        RUN_MILESTONES.flatMap { runNumbered(it, kid) } + FOCUS_MINUTES.flatMap { focusMinutes(it, kid) }).distinct()

    /** The bubble on the lane at a long run of right notes (the player's old "High five!"): a kid's own word, a grown-up's calm one (Dana: it had an exclamation mark in the quiet register). Shown, never said. */
    fun highFive(kid: Boolean): String = if (kid) "High five!" else "Nice playing."

    /** The lines that can be said aloud (every family but the step passed one, which the step pop shows without a word). */
    fun spoken(kid: Boolean): List<String> = all(kid) - family(CheerTrigger.STEP_PASSED, kid).toSet()
}
