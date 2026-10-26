package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.PracticeStats
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.CanDoWords
import com.bigpapituna.sharpears.engine.brief.MinutesLedger
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today's paw on the family card: played today, kept by yesterday's rest day (today still open), or nothing yet. */
enum class TodayPaw { PLAYED, REST, NONE }

/**
 * One child's row on the family today card (2.14, Priya's P8): [minutes] on screen and [practiceMinutes] practised today
 * (the one minutes ledger, so the row says what the child's brief says), [lessons] finished today and the [stars] they
 * earned, today's [paw] and the [streak] of days in a row with its [rest] days (PracticeStats over the ledger's days).
 */
data class ChildToday(
    val profileId: String,
    val minutes: Int = 0,
    val practiceMinutes: Int = 0,
    val lessons: Int = 0,
    val stars: Int = 0,
    val paw: TodayPaw = TodayPaw.NONE,
    val streak: Int = 0,
    val rest: Int = 0,
) {
    val played: Boolean get() = minutes > 0 || practiceMinutes > 0 || lessons > 0
}

/**
 * The family today card's rows, from each child's event log (pure; data reads the logs off the main thread). A grown-up's
 * Today shows it when the family has kids; a tap on a row opens that child's brief.
 */
object FamilyToday {
    fun of(profileId: String, events: List<BriefEvent>, today: LocalDate, now: Long, zone: ZoneId): ChildToday {
        val ledger = MinutesLedger.byDay(events, now, zone)
        val day = ledger[today] ?: MinutesLedger.Day(today)
        val done = events.filterIsInstance<BriefEvent.LessonDone>().filter { it.at <= now && Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == today }
        val practised = ledger.filterValues { it.practiceSec > 0 }.keys.filter { !it.isAfter(today) }.toSet()
        val run = PracticeStats.streakRun(practised, today)
        val rest = PracticeStats.restDays(practised, today)
        val paw = when {
            today in practised -> TodayPaw.PLAYED
            today.minusDays(1) in rest -> TodayPaw.REST
            else -> TodayPaw.NONE
        }
        return ChildToday(profileId, day.minutes, day.practiceMinutes, done.size, done.sumOf { it.stars }, paw, run.days, run.rest)
    }

    /** "14 min playing of 22 min on screen. 2 lessons, 5 stars." or "Not at the piano yet today." */
    fun line(c: ChildToday): String {
        if (!c.played) return "Not at the piano yet today."
        val minutes = CanDoWords.minutesLine(c.practiceMinutes, c.minutes).orEmpty()
        val lessons = if (c.lessons == 0) "" else " " + count(c.lessons, "lesson") + ", " + count(c.stars, "star") + "."
        return (minutes + lessons).trim()
    }

    /** What the paw says to TalkBack and under it: "3 days in a row", "Rest day yesterday", "No playing yet today". */
    fun pawWords(c: ChildToday): String = when {
        c.paw == TodayPaw.REST -> "Yesterday was a rest day"
        c.streak >= 2 -> PracticeStats.streakWords(c.streak, c.rest)
        c.paw == TodayPaw.PLAYED -> "Played today"
        else -> "No playing yet today"
    }

    private fun count(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
}
