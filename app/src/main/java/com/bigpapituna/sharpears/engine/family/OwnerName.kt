package com.bigpapituna.sharpears.engine.family

/**
 * The phone owner's name (2.14, persona walks 2026-10-26: every fresh owner was greeted "Hi, Roy" because no name was ever
 * asked). Pure, so OwnerNameTest checks the rules; data/Profiles.kt keeps the "asked" flag and ui/onboarding asks.
 *
 * - The first-run flow asks "What should we call you?" (first name, optional). A name is kept as typed ([Profile.cleanName]);
 *   none leaves the owner unnamed ([Profile.UNSET_NAME], [Profile.named] false) and every greeting says "Hi there".
 * - A phone from before 2.14 has an owner called "Roy" that nobody chose ([Profile.fromMap]): it is asked once, on the next
 *   launch, and never again whatever the answer ([askDue]). An owner whose name was typed (any other name) is not asked.
 */
object OwnerName {
    const val QUESTION = "What should we call you?"
    const val HINT = "Your first name, if you like. You can skip this."
    const val SAVE = "Save"
    const val LATER = "Not now"
    /** The pup's line over the one-time question on a phone that already has an owner. */
    const val ASK_LINE = "Hi! What should I call you?"

    /** Whether the one-time question is due: an owner with no name of their own who has not been asked, after the first-run flow. */
    fun askDue(who: Profile, asked: Boolean, onboardingDone: Boolean): Boolean = who.isOwner && !who.isKid && !who.named && !asked && onboardingDone

    /** [owner] with [typed] as their name; blank (or only spaces) leaves them unnamed, with the unset name shown where a name is listed. */
    fun apply(owner: Profile, typed: String?): Profile {
        val clean = Profile.cleanName(typed.orEmpty())
        return if (clean.isEmpty()) owner.copy(name = Profile.UNSET_NAME, named = false) else owner.copy(name = clean, named = true)
    }
}

/**
 * What the app says when it greets someone by name (2.14): "Hi, Dana." with a name, "Hi there." without one. Every greeting
 * reads this (Today, the layout chooser, the tour, My week), never [Profile.name] directly, so an unnamed owner is never
 * greeted by the unset name.
 */
object Greeting {
    fun clean(name: String?): String? = name?.let(Profile::cleanName)?.takeIf { it.isNotEmpty() }

    /** "Hi, Dana." or "Hi there." */
    fun hi(name: String?): String = clean(name)?.let { "Hi, $it." } ?: "Hi there."

    /** "Welcome back, Dana." or "Welcome back." */
    fun welcomeBack(name: String?): String = clean(name)?.let { "Welcome back, $it." } ?: "Welcome back."

    /** The layout chooser's hello, before "One quick choice and we can start." */
    fun layout(name: String?, returning: Boolean): String = if (returning) welcomeBack(name) else hi(name)

    /** "Good evening, Dana." or "Good evening." ([part]: "Good morning", "Good afternoon", "Good evening"). */
    fun timeOfDay(part: String, name: String?): String = clean(name)?.let { "$part, $it." } ?: "$part."

    /** The tour's first card: the hello, then the card's own line. */
    fun tour(name: String?, line: String): String = "${hi(name)} $line"

    /** The line over "My week" (a grown-up's own week): "Dana, here is your week." or "Here is your week." */
    fun myWeek(name: String?): String = clean(name)?.let { "$it, here is your week." } ?: "Here is your week."

    /** The You tab's "My week" card line: "Dana, here is how your playing went, with one or two things to try." */
    fun myWeekCard(name: String?): String = (clean(name)?.let { "$it, here" } ?: "Here") + " is how your playing went, with one or two things to try."
}
