package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.world.GiftKind
import com.bigpapituna.sharpears.engine.world.Surprise

/** What the pup says for the 2.9 retention pieces (kid lines, recorded at the kid speed: engine/SpokenCatalog.kidLines). */
object RetentionLines {
    /** On a kid's finish screen, the gift just wrapped. */
    const val WRAPPED = "Something for tomorrow! A surprise from me."
    /** On the kid home, the gift ready to open. */
    const val READY = "Your surprise is ready! Tap the present."
    /** The kid's comeback card, with a stone they know, or with the next one. */
    val COMEBACK = Comeback.KID_TITLE + " " + Comeback.KID_LINE
    val COMEBACK_NEXT = Comeback.KID_TITLE + " " + Comeback.KID_LINE_NEXT

    fun fixed(): List<String> = listOf(WRAPPED, READY, COMEBACK, COMEBACK_NEXT) + GiftKind.entries.map(Surprise::line)
}
