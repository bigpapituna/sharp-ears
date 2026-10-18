package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage

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

    /** The signpost by the pup: Pup's quest as stars still to find today ([found] of three). */
    fun signpost(found: Int): String = when (found) {
        0 -> "Three stars to find today!"
        1 -> "Two more stars to find today!"
        2 -> "One more star to find today!"
        else -> "All three stars found today!"
    }

    /** The finale's headline and its first spoken line: "Mia finished the garden!" ("You finished ..." with no name). */
    fun finished(name: String?, page: PlaygroundPage): String =
        "${name?.trim()?.takeIf { it.isNotEmpty() } ?: "You"} finished ${KidPages.inSentence(page)}!"

    /** The finale's second line: where the path goes on. */
    fun onward(after: Finale.After): String = when (after) {
        is Finale.After.NextPage -> "Next stop, ${KidPages.inSentence(after.page)}!"
        Finale.After.Road -> ROAD
    }

    /** Every line here without a name, for the voice catalog. */
    fun fixed(pages: List<PlaygroundPage> = PLAYGROUND_PAGES): List<String> = buildList {
        add(LETS_GO); add(THIS_ONE_FIRST); add(ROAD); add(ASLEEP)
        (0..3).forEach { add(signpost(it)) }
        pages.forEach { p -> add(finished(null, p)); add(onward(Finale.after(p, pages))) }
    }.distinct()
}
