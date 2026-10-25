package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.lessons.CheerTrigger

/**
 * The cheer pop's tester event (2.13): `cheer_pop` each time one shows, with its trigger's family (`run`, `recovery`,
 * `halfway`, `hard_passed`, `steady`, `focus`, `nudge`, `step_passed`) and the audience (`profile_kind`, kid or adult) and
 * nothing else: no name, no id, no line, no lesson. To judge it: how often each family shows per minute played, and whether
 * lessons and songs are finished more often (lesson_finished) with Encouragement on.
 */
object CheerPopEvent {
    const val NAME = "cheer_pop"
    const val TRIGGER = "trigger"
    const val KIND = "profile_kind"

    fun of(trigger: CheerTrigger, kid: Boolean): TesterEvent = TesterEvent(NAME, mapOf(TRIGGER to trigger.wire, KIND to TesterRules.profileKind(kid)))
}
