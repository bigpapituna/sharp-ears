package com.bigpapituna.sharpears.engine.skin

import java.time.LocalDate

/**
 * The session goodbye (docs/KIDS-PLAN.md section 3, item 9): once a kid's daily goal is reached (5 minutes before
 * reading, 8 after; the live goal is ProgressStore.goalMinutes) the pup yawns and says goodnight, once a day. Songs
 * and the Jam stay open; no new lesson starts that day unless a grown-up says one more (behind the parent gate).
 * Grown-ups are never sent to bed. Pure rules; ui/skin/Goodbye.kt draws it and the integrator calls these.
 */
object Bedtime {
    /** Today's practice reached the goal. A goal of zero never does. */
    fun goalReached(practisedSec: Int, goalMin: Int): Boolean = goalMin > 0 && practisedSec >= goalMin * 60

    /**
     * Say goodnight now? Call it when something ends (a lesson, a song run, a Practice today part), never mid-run:
     * true for a kid whose practice today reached the goal and who has not had today's goodbye ([shownOn] is the
     * last day it was shown, null for never).
     */
    fun sayGoodnight(kid: Boolean, practisedSec: Int, goalMin: Int, shownOn: LocalDate?, today: LocalDate): Boolean =
        kid && goalReached(practisedSec, goalMin) && shownOn != today

    /**
     * May a new lesson start? Always for grown-ups; for a kid until the goal is reached, or when a grown-up let one
     * more through today ([grownUpSaidOn] is that day).
     */
    fun lessonOpen(kid: Boolean, practisedSec: Int, goalMin: Int, grownUpSaidOn: LocalDate? = null, today: LocalDate = LocalDate.now()): Boolean =
        !kid || !goalReached(practisedSec, goalMin) || grownUpSaidOn == today

    /** Songs and the Jam never close: the goodbye ends lessons only. */
    const val SONGS_OPEN = true
}
