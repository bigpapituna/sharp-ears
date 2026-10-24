package com.bigpapituna.sharpears.engine.tester

/**
 * The retention events (2.9), for judging the three mechanics in the tester dashboard: each carries the profile's kind
 * ("kid" or "adult", the same words as the dashboard's profile_kind) and nothing else: no name, no id, no title.
 *
 * - comeback_shown: the comeback card showed (a return after three or more days away).
 * - comeback_played: its easy piece was started from the card.
 * - rest_day_used: a rest day kept a streak (once per rest day, when the day after it was played).
 * - surprise_wrapped: a kid's gift was wrapped on a finish screen.
 * - surprise_opened: it was opened on the kid home the next day or later.
 */
object Retention {
    const val COMEBACK_SHOWN = "comeback_shown"
    const val COMEBACK_PLAYED = "comeback_played"
    const val REST_DAY_USED = "rest_day_used"
    const val SURPRISE_WRAPPED = "surprise_wrapped"
    const val SURPRISE_OPENED = "surprise_opened"
    val ALL = listOf(COMEBACK_SHOWN, COMEBACK_PLAYED, REST_DAY_USED, SURPRISE_WRAPPED, SURPRISE_OPENED)

    /** The event [name] for a kid or a grown-up. */
    fun event(name: String, kid: Boolean): TesterEvent {
        require(name in ALL) { "not a retention event: $name" }
        return TesterEvent(name, mapOf("profile_kind" to TesterRules.profileKind(kid)))
    }
}
