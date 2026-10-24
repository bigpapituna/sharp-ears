package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * When a grown-up wants a child's weekly brief: a day of the week and a time (minutes after midnight, phone time). The
 * default is Sunday at 6 pm, asked at the end of the new-kid flow and changeable in the parent corner. [on] false means no
 * notification (the brief is still there to open).
 */
data class WeeklySlot(val day: DayOfWeek = DayOfWeek.SUNDAY, val minutes: Int = 18 * 60, val on: Boolean = true) {
    init { require(minutes in 0 until 24 * 60) { "minutes after midnight" } }

    /** "SUNDAY@1080" or "SUNDAY@1080!" for off, what BriefStore keeps. */
    fun encode(): String = "${day.name}@$minutes" + if (on) "" else "!"

    companion object {
        val DEFAULT = WeeklySlot()

        fun decode(text: String?): WeeklySlot? {
            if (text.isNullOrBlank()) return null
            val on = !text.endsWith("!")
            val body = text.removeSuffix("!")
            val day = runCatching { DayOfWeek.valueOf(body.substringBefore('@')) }.getOrNull() ?: return null
            val minutes = body.substringAfter('@', "").toIntOrNull()?.takeIf { it in 0 until 24 * 60 } ?: return null
            return WeeklySlot(day, minutes, on)
        }
    }
}

/**
 * The next time a weekly brief goes out, and what it says. All of it is wall-clock maths in the phone's time zone, so a
 * slot at 6 pm stays 6 pm across daylight saving; a time that does not exist on the night the clocks go forward (2:30 am)
 * is the first moment after the gap, and one that happens twice (1:30 am when they go back) is the first of the two.
 */
object WeeklyPlan {
    /** An alarm that fires just before its minute is not set again for the same minute. */
    private const val SLACK_SECONDS = 30L

    /** The first moment strictly after [now] (and the slack) that [slot] comes round in [zone]. */
    fun next(now: Instant, zone: ZoneId, slot: WeeklySlot): Instant {
        val local = now.atZone(zone)
        val time = LocalTime.of(slot.minutes / 60, slot.minutes % 60)
        var date: LocalDate = local.toLocalDate().with(TemporalAdjusters.nextOrSame(slot.day))
        var at = ZonedDateTime.of(date, time, zone)
        while (!at.toInstant().isAfter(now.plusSeconds(SLACK_SECONDS))) {
            date = date.plusWeeks(1)
            at = ZonedDateTime.of(date, time, zone)
        }
        return at.toInstant()
    }

    /** What the weekly brief's setting says when the phone does not let the app notify. */
    const val NOTIFICATIONS_OFF = "Off, notifications are off on this phone"

    /**
     * Whether [slot]'s notification is set: it is on, and the phone lets the app notify ([canNotify]). QA 2.8.3: a new kid's
     * brief was set for Sunday at 6 pm on a phone that had refused notifications, so nothing would ever show. The slot itself
     * is kept (the weekly email goes by its day and time), and the alarm comes back once notifications are allowed.
     */
    fun notifies(slot: WeeklySlot, canNotify: Boolean): Boolean = slot.on && canNotify

    /** The setting in words: when it comes ("Sunday at 6 pm"), "Off", or [NOTIFICATIONS_OFF] when the phone says no. */
    fun stateLabel(slot: WeeklySlot, canNotify: Boolean): String = when {
        !slot.on -> "Off"
        !canNotify -> NOTIFICATIONS_OFF
        else -> label(slot)
    }

    /** "Sunday at 6 pm", "Friday at 7:30 am", "Sunday at noon" (sentence case, no dashes). */
    fun label(slot: WeeklySlot): String {
        val day = slot.day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
        return "$day at ${timeLabel(slot.minutes)}"
    }

    fun timeLabel(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        if (minutes == 12 * 60) return "noon"
        if (minutes == 0) return "midnight"
        val h12 = if (h % 12 == 0) 12 else h % 12
        val ampm = if (h < 12) "am" else "pm"
        return if (m == 0) "$h12 $ampm" else "%d:%02d %s".format(Locale.ROOT, h12, m, ampm)
    }

    /** A stable alarm id per person, so each child has their own alarm (the daily reminder uses 0). */
    fun requestCode(profileId: String): Int = 1_000 + (profileId.hashCode() and 0xFFFF)

    /** Evening choices for the time picker: every half hour from 7 am to 9 pm. */
    val TIMES: List<Int> = (7 * 2..21 * 2).map { it * 30 }

    /** The earlier or later choice in [TIMES] (stays at the ends). */
    fun step(minutes: Int, by: Int): Int {
        val i = TIMES.indexOfFirst { it >= minutes }.let { if (it < 0) TIMES.lastIndex else it }
        return TIMES[(i + by).coerceIn(0, TIMES.lastIndex)]
    }
}

/** The weekly notification's words. No numbers, no scores, no scolding: a kind nudge to read. */
object BriefNotice {
    fun title(name: String, kind: ProfileKind): String = if (kind == ProfileKind.KID) "$name's weekly brief is ready" else "Your week at the piano"

    fun text(kind: ProfileKind): String =
        if (kind == ProfileKind.KID) "A quick read on how the week went, with one thing to try." else "A quick look at how your week went."

    /** Sent only when the week had some playing in it: a quiet week is no reason to buzz a phone. */
    fun shouldSend(f: Findings): Boolean = !f.isEmpty && f.stats.totals.sessions > 0
}
