package com.bigpapituna.sharpears.engine.brief

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The one minutes ledger (2.14, Priya's P11): how long someone was on screen and how long they practised, per day, from
 * their event log's sessions. Every view of a person's minutes reads it: the brief's Session, Today and Week (Insights'
 * windows sum the same sessions), the seven-day paw strip, the email, the family card on a grown-up's Today and a kid's
 * daily time cap. A session is a start and an end (data/Briefs.kt); one the app never ended (killed, swiped away mid-step,
 * an install) is closed on the next start from its last checkpoint, so its minutes stay.
 *
 * One rounding for every number a person reads ([minutes]): whole minutes counted down, and anything above zero is at
 * least 1, as PracticeStats has always counted the week ("Goal met, 16 min today" over "This week: 16 min").
 */
object MinutesLedger {
    /** One day: [screenMs] on screen and [practiceSec] practised (time a player, stage or game ran). */
    data class Day(val date: LocalDate, val screenMs: Long = 0, val practiceSec: Int = 0) {
        val minutes: Int get() = minutesOfMs(screenMs)
        val practiceMinutes: Int get() = minutes(practiceSec.toLong())
    }

    /** Whole minutes in [seconds], counted down; any time at all is at least 1. */
    fun minutes(seconds: Long): Int = if (seconds <= 0) 0 else maxOf(1L, seconds / 60).toInt()

    /** The same for milliseconds (a part of a second still counts as some time). */
    fun minutesOfMs(ms: Long): Int = if (ms <= 0) 0 else maxOf(1L, ms / 60_000).toInt()

    /**
     * Every day with a session, from [events] (any order) up to [now]. A session counts on the day it started; the app ends
     * and restarts its session at midnight, so a late evening never lends minutes to the next day.
     */
    fun byDay(events: List<BriefEvent>, now: Long, zone: ZoneId): Map<LocalDate, Day> {
        val sorted = events.filter { it !is BriefEvent.Unknown && it.at <= now }.sortedWith(compareBy({ it.at }, { it.encode() }))
        return Sessions.of(sorted, now, zone).groupBy { day(it.start, zone) }
            .mapValues { (d, s) -> Day(d, s.sumOf { it.onScreenMs }, s.sumOf { it.practiceSec }) }
    }

    /** One day of [byDay] (an empty day when nothing was played). */
    fun day(events: List<BriefEvent>, date: LocalDate, now: Long, zone: ZoneId): Day = byDay(events, now, zone)[date] ?: Day(date)

    /** Practice seconds per day with any practice: the map PracticeStats reads (streaks, rest days, the week's minutes). */
    fun practiceByDay(events: List<BriefEvent>, now: Long, zone: ZoneId): Map<LocalDate, Int> =
        byDay(events, now, zone).mapValues { it.value.practiceSec }.filterValues { it > 0 }

    internal fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
}
