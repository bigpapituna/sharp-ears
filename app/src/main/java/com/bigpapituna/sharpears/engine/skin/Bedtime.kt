package com.bigpapituna.sharpears.engine.skin

import java.time.LocalDate

/**
 * The daily goal and bedtime. Reaching the goal (5 minutes before reading, 8 after; the live goal is
 * ProgressStore.goalMinutes) earns a cheer once a day and nothing more: a kid plays as long as they like (Roy,
 * 2026-10-20). Bedtime is a parental control, off by default, set per kid in Settings > Family behind the gate: with
 * it on, the pup yawns and says goodnight at the goal, songs and the Jam stay open, and no new lesson starts that
 * day unless a grown-up says one more. Grown-ups are never sent to bed. Pure rules; ui/skin/Goodbye.kt draws it.
 */
object Bedtime {
    /** Today's practice reached the goal. A goal of zero never does. */
    fun goalReached(practisedSec: Int, goalMin: Int): Boolean = goalMin > 0 && practisedSec >= goalMin * 60

    /**
     * Say goodnight now? Call it when something ends (a lesson, a song run, a Practice today part), never mid-run:
     * true for a kid whose practice today reached the goal and who has not had today's goodbye ([shownOn] is the
     * last day it was shown, null for never).
     */
    fun sayGoodnight(kid: Boolean, practisedSec: Int, goalMin: Int, shownOn: LocalDate?, today: LocalDate, bedtime: Boolean = false): Boolean =
        bedtime && kid && goalReached(practisedSec, goalMin) && shownOn != today

    /** Cheer the goal once a day (any kid, bedtime on or off); [shownOn] is the last day it was said. */
    fun sayWellDone(kid: Boolean, practisedSec: Int, goalMin: Int, shownOn: LocalDate?, today: LocalDate): Boolean =
        kid && goalReached(practisedSec, goalMin) && shownOn != today

    /**
     * May a new lesson start? Always, unless [bedtime] is on for this kid: then until the goal is reached, or when a
     * grown-up let one more through today ([grownUpSaidOn] is that day).
     */
    fun lessonOpen(kid: Boolean, practisedSec: Int, goalMin: Int, grownUpSaidOn: LocalDate? = null, today: LocalDate = LocalDate.now(), bedtime: Boolean = false): Boolean =
        !kid || !bedtime || !goalReached(practisedSec, goalMin) || grownUpSaidOn == today

    /** Songs and the Jam never close: the goodbye ends lessons only. */
    const val SONGS_OPEN = true
}
