package com.bigpapituna.sharpears.engine.tester

/**
 * The step pop's tester event (2.11): `step_pop` each time a lesson step pops (passed cleanly, another step follows), with
 * the length of the run of clean steps it crowns and nothing else: no name, no id, no lesson, no kind. To judge the
 * climb: how long the streaks get, and whether lessons with pops are finished more often (lesson_finished).
 */
object StepPopEvent {
    const val NAME = "step_pop"
    const val STREAK = "streak"

    fun of(streak: Int): TesterEvent = TesterEvent(NAME, mapOf(STREAK to streak))
}
