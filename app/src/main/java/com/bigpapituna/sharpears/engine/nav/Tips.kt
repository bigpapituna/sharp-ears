package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.family.Level

/**
 * 2.15 (show off): one-time tips that point a grown-up at something the app already does, the first time it is within reach
 * and still unused. Pure rules; the screens draw them (ui/TipCard.kt) and Prefs keeps what was seen.
 *
 * - Never on a kid's screen: [kid] is `Profile.isKid`, so big kids, who read the grown-up recap, get none either.
 * - Never twice: a tip is marked seen the moment it is put on a screen, and a feature used first marks its tip seen too.
 * - One a session: [TipSession] (a session is a process; a second tip waits for the next launch).
 */
object Tips {
    enum class Tip(val id: String, val line: String, /** The button, when the tip can open the thing itself. */ val action: String? = null) {
        /** After the first Perform run: the recap's bar strip. */
        TIMING("timing", "Tap a bar to see which notes were early or late."),
        /** On the third song recap that has hard bars: the Loop buttons. */
        LOOP("loop", "Loop the bars you missed."),
        /** In Practice, for players placed at Some or A lot: the staff drill. */
        SIGHT("sight", "Try a sight-reading drill at your level.", "Open"),
    }

    /** The recap that shows the loop tip: the third one with hard bars. */
    const val HARD_RECAPS_FOR_LOOP = 3

    fun parse(stored: String?): Set<String> = stored.orEmpty().split(',').filter { it.isNotBlank() }.toSet()
    fun encode(seen: Set<String>): String = seen.sorted().joinToString(",")

    private fun open(tip: Tip, kid: Boolean, seen: Set<String>, sessionShown: Boolean) = !kid && !sessionShown && tip.id !in seen

    /**
     * The tip for a song's recap, or null. [timingBars]: this was a Perform run and its strip has bars to tap. [hardRecaps]:
     * recaps with hard bars so far, this one counted. The timing tip comes first when both are due.
     */
    fun afterSong(kid: Boolean, seen: Set<String>, sessionShown: Boolean, timingBars: Boolean, hardRecaps: Int, hasHardBars: Boolean): Tip? = when {
        open(Tip.TIMING, kid, seen, sessionShown) && timingBars -> Tip.TIMING
        open(Tip.LOOP, kid, seen, sessionShown) && hasHardBars && hardRecaps >= HARD_RECAPS_FOR_LOOP -> Tip.LOOP
        else -> null
    }

    /**
     * The tip on the Practice tab: the staff drill, for a grown-up placed at Some or A lot whose layout opens it
     * ([staffOpen], SightLevels.staffOpen) and who has not tried a sight-reading drill at any level ([sightTried]).
     */
    fun inPractice(kid: Boolean, placement: Level, staffOpen: Boolean, sightTried: Boolean, seen: Set<String>, sessionShown: Boolean): Tip? =
        Tip.SIGHT.takeIf { open(it, kid, seen, sessionShown) && staffOpen && !sightTried && placement.ordinal >= Level.SOME.ordinal }

    /** The drill level the sight tip opens: the one Today's challenge starts at (4 at Some, 6 at A lot; NextChallenge.sightLevel). */
    fun sightLevelFor(placement: Level): Int = NextChallenge.sightLevel(if (placement == Level.A_LOT) 2 else 1)
}

/** Whether a tip has already been shown in this process. Reset by a new launch; tests reset it by hand. */
object TipSession {
    @Volatile var shown = false
    fun reset() { shown = false }
}
