package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.playground.Place as PagePlace

/**
 * What the pup says on the kid home and in the finale (2.2). Same rules as the kid skin's words: short, warm, second
 * person, sentence case, no numbers shown, no dashes. Lines with the kid's name are made on the phone (the on-phone
 * voice can say names); [fixed] lists the nameless ones for the voice catalog (recorded at the kid speed).
 */
object KidLines {
    /** The bubble over the pup at the next stone: tap it to start. */
    const val LETS_GO = "Let's go!"
    /** Tapping a stone further on than the next one: the pup points back at the next. */
    const val THIS_ONE_FIRST = "This one first!"
    /** After the last page's finale, once, at the gate and on the big kids' road. */
    const val ROAD = "You're ready for the big kids' road!"
    /** The bubble while lessons sleep after today's goodbye (songs and play stay open). */
    const val ASLEEP = "Shh, the pup is asleep. Play and songs are open!"
    /** Tapping the full treat jar in the garden: the pup eats. */
    const val YUM = "Yum! Thank you!"
    /** Tapping the mystery egg in the garden. */
    const val EGG = "A mystery egg! It hatches when you finish your next game."
    /** The bubble over the sleeping pup (the voice says [ASLEEP]). */
    const val ASLEEP_SHORT = "Zzz. New games tomorrow!"

    /** The signpost by the pup: Pup's quest as stars still to find today ([found] of three). */
    fun signpost(found: Int): String = when (found) {
        0 -> "Three stars to find today!"
        1 -> "Two more stars to find today!"
        2 -> "One more star to find today!"
        else -> "All three stars found today!"
    }

    /** The same in a few words, for the pup's bubble on the map (the voice says the whole line when the signpost is tapped). */
    fun signpostShort(found: Int): String = when (found) {
        0 -> "Three stars to find!"
        1 -> "Two more stars!"
        2 -> "One more star!"
        else -> "All three stars!"
    }

    /** The finale's headline and its first spoken line: "Mia finished the garden!" ("You finished ..." with no name). */
    fun finished(name: String?, page: PlaygroundPage): String = finished(name, page.title)

    private fun finished(name: String?, title: String) = "${name?.trim()?.takeIf { it.isNotEmpty() } ?: "You"} finished ${KidPages.inSentence(title)}!"

    /** The finale's second line: where the path goes on. */
    fun onward(after: Finale.After): String = when (after) {
        is Finale.After.NextPage -> nextStop(after.page.title)
        Finale.After.Road -> ROAD
    }

    private fun nextStop(title: String) = "Next stop, ${KidPages.inSentence(title)}!"

    /** Every line here without a name, for the voice catalog. */
    fun fixed(pages: List<PlaygroundPage> = PLAYGROUND_PAGES): List<String> = buildList {
        add(LETS_GO); add(THIS_ONE_FIRST); add(ROAD); add(ASLEEP); add(ASLEEP_SHORT); add(YUM); add(EGG)
        (0..3).forEach { add(signpost(it)); add(signpostShort(it)) }
        pages.forEach { p -> add(finished(null, p)); add(onward(Finale.after(p, pages))) }
        // 2.3: every place a page can be, whichever pages this build has, so the lines are recorded before a page lands
        // (the treehouse and the night sky come from another branch) and whatever order the pages end up in.
        PagePlace.entries.forEach { add(finished(null, it.title)); add(nextStop(it.title)) }
    }.distinct()
}
