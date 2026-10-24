package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.RunMode
import com.bigpapituna.sharpears.engine.brief.Sessions
import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/*
 * When a child's brief goes out by email (Roy, 2026-10-21, design D): a DAILY email each day the child played, once their
 * playing is done for the day; a WEEKLY email on the grown-up's day and time (the same slot as the weekly notification,
 * engine/brief/WeeklySchedule.kt); both skip a day or a week with no playing; the grown-up picks DAILY, WEEKLY, BOTH or OFF
 * after the first email; the first email (it carries the code that verifies the address) goes as soon as the address is
 * entered. Nothing goes between 21:30 and 07:00: it waits for the morning. Pure: the integrator asks [EmailSchedule.decide]
 * whenever something may have changed (a session ended, an alarm, the settings) and sends what it says.
 *
 * All local times are wall-clock times in the phone's zone through java.time, so 20:30 is 20:30 on the days the clocks
 * change; the 30 minutes after the last session are real minutes.
 */

/**
 * How often a grown-up wants the child's brief by email; [OFF] sends nothing. Since 2.8 it is two switches on the child's
 * row in Settings > Briefs and email, Daily and Weekly ([of]).
 */
enum class EmailFrequency {
    DAILY, WEEKLY, BOTH, OFF;

    val daily: Boolean get() = this == DAILY || this == BOTH
    val weekly: Boolean get() = this == WEEKLY || this == BOTH

    companion object {
        /** The frequency the two switches make. */
        fun of(daily: Boolean, weekly: Boolean): EmailFrequency = when {
            daily && weekly -> BOTH
            daily -> DAILY
            weekly -> WEEKLY
            else -> OFF
        }
    }
}

/** Which email: the first (with the verification code), a day's, or a week's. */
enum class EmailKind { FIRST, DAILY, WEEKLY }

/** One stretch on screen as the child: [startMs], [endMs] (null while it is still open), and whether anything was [played] in it. */
data class PlaySpan(val startMs: Long, val endMs: Long?, val played: Boolean)

/**
 * What the schedule needs to know of one child's email settings (data/EmailSettings gives it): the [frequency], the
 * [weekly] slot, whether the address is [verified], whether an address was entered whose first email (with the code) has
 * not gone yet ([firstPending]), the last day a daily email covered and the date of the last weekly slot sent, and when
 * emails went out ([sends], epoch ms, for the caps).
 */
data class EmailPlan(
    val frequency: EmailFrequency,
    val weekly: WeeklySlot,
    val verified: Boolean,
    val firstPending: Boolean = false,
    val lastDaily: LocalDate? = null,
    val lastWeekly: LocalDate? = null,
    val sends: List<Long> = emptyList(),
)

/** What to do now. */
sealed interface EmailDue {
    /**
     * Send [kind] now. It covers [day] ([period] DAY: that day; WEEK: the seven days ending that day): compute the findings
     * with `Insights.compute(events, profile, period, now = findingsAt)`, which is the end of that day for a day already
     * over, the slot's moment for a weekly, and now otherwise. Record it with the settings' `sent(kind, day, at)`.
     */
    data class Send(val kind: EmailKind, val day: LocalDate, val period: Period, val findingsAt: Long) : EmailDue

    /** Nothing to send now; ask again at [atMs] (epoch ms), or when something changes (null: nothing is waiting). */
    data class Wait(val atMs: Long?) : EmailDue
}

/** At most [perDay] emails per child per local day and [perWeek] per week (Monday to Sunday), the first email included. */
data class EmailCaps(val perDay: Int = 2, val perWeek: Int = 10) {
    fun allows(sends: List<Long>, now: Long, zone: ZoneId): Boolean = nextFree(sends, now, zone) == null

    /** Null when a send is allowed now, otherwise the moment the cap frees up (the next local midnight, or the next Monday). */
    fun nextFree(sends: List<Long>, now: Long, zone: ZoneId): Long? {
        val today = EmailSchedule.date(now, zone)
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val days = sends.map { EmailSchedule.date(it, zone) }
        if (days.count { it >= monday && it <= today } >= perWeek) return EmailSchedule.at(monday.plusWeeks(1), LocalTime.MIDNIGHT, zone)
        if (days.count { it == today } >= perDay) return EmailSchedule.at(today.plusDays(1), LocalTime.MIDNIGHT, zone)
        return null
    }
}

object EmailSchedule {
    /** A day's email goes at this time at the latest when the child is still playing then (once that day). */
    val DAY_CUTOFF: LocalTime = LocalTime.of(20, 30)
    /** Quiet hours: nothing goes from [QUIET_FROM] to [QUIET_TO]; what falls due in them waits for the morning. */
    val QUIET_FROM: LocalTime = LocalTime.of(21, 30)
    val QUIET_TO: LocalTime = LocalTime.of(7, 0)
    /** Playing is done for the day when no session has started for this long after the last one ended. */
    const val GAP_MS = 30 * 60_000L
    /** A day's email held overnight (quiet hours, a phone asleep) still goes the next morning until this time; then it is dropped. */
    val STALE_AT: LocalTime = LocalTime.NOON
    /** A weekly email whose slot was missed (the phone off) still goes for this long after the slot. */
    const val WEEKLY_LATE_MS = 48 * 60 * 60_000L
    private val PLAYED = setOf("step", "lesson", "song", "ear")

    /**
     * What to do at [now] for one child: the first email at once when it is pending (the grown-up is waiting for its code,
     * so quiet hours do not hold it; since 2.10.1 it is the code alone, [CodeEmail], covering no day); nothing else until
     * the address is verified or with the frequency OFF; otherwise the day's email (yesterday's held overnight first, then
     * today's) and the week's, whichever is due earliest, within the [caps]. [spans] come from [spans].
     */
    fun decide(now: Long, zone: ZoneId, plan: EmailPlan, spans: List<PlaySpan>, caps: EmailCaps = EmailCaps()): EmailDue {
        val today = date(now, zone)
        if (plan.firstPending) {
            caps.nextFree(plan.sends, now, zone)?.let { return EmailDue.Wait(it) }
            return EmailDue.Send(EmailKind.FIRST, today, Period.DAY, now)
        }
        if (!plan.verified || plan.frequency == EmailFrequency.OFF) return EmailDue.Wait(null)

        val ready = ArrayList<Pair<Long, EmailDue.Send>>()
        var wake: Long? = null
        fun later(at: Long) { wake = minOf(wake ?: Long.MAX_VALUE, at) }
        // Due at [dueAt], sent at the first moment from now on outside the quiet hours, and never at or after [deadline].
        fun consider(dueAt: Long, send: EmailDue.Send, deadline: Long? = null) {
            val at = afterQuiet(maxOf(dueAt, now), zone)
            if (deadline != null && at >= deadline) return
            if (at <= now) ready += dueAt to send else later(at)
        }

        if (plan.frequency.daily) for (day in listOf(today.minusDays(1), today)) {
            if (plan.lastDaily != null && plan.lastDaily >= day) continue
            val state = daily(day, spans, zone)
            state.recheck?.let { later(it) }
            val due = state.dueAt ?: continue
            val stale = if (day < today) at(day.plusDays(1), STALE_AT, zone) else null
            consider(due, EmailDue.Send(EmailKind.DAILY, day, Period.DAY, minOf(now, endOf(day, zone))), stale)
        }

        if (plan.frequency.weekly) {
            val slotAt = previousSlot(now, zone, plan.weekly)
            val slotDay = date(slotAt, zone)
            val playedThatWeek = spans.any { it.played && it.startMs <= slotAt && date(it.startMs, zone) >= slotDay.minusDays(6) }
            if ((plan.lastWeekly == null || plan.lastWeekly < slotDay) && playedThatWeek)
                consider(slotAt, EmailDue.Send(EmailKind.WEEKLY, slotDay, Period.WEEK, slotAt), deadline = slotAt + WEEKLY_LATE_MS)
            later(afterQuiet(nextSlot(now, zone, plan.weekly), zone))
        }

        val first = ready.minWithOrNull(compareBy({ it.first }, { it.second.kind.ordinal }, { it.second.day }))?.second
            ?: return EmailDue.Wait(wake)
        caps.nextFree(plan.sends, now, zone)?.let { return EmailDue.Wait(it) }
        return first
    }

    /** A day's email: when it is due ([dueAt], null while it is not known yet or nothing was played), and when to look again. */
    internal data class Daily(val dueAt: Long?, val recheck: Long?)

    internal fun daily(day: LocalDate, spans: List<PlaySpan>, zone: ZoneId): Daily {
        val those = spans.filter { date(it.startMs, zone) == day }
        if (those.none { it.played }) return Daily(null, null)
        val cutoff = at(day, DAY_CUTOFF, zone)
        val before = those.any { it.startMs < cutoff }
        if (those.any { it.endMs == null }) return if (before) Daily(cutoff, null) else Daily(null, null)
        val byGap = those.maxOf { it.endMs!! } + GAP_MS
        return Daily(if (before) minOf(byGap, cutoff) else byGap, null)
    }

    /**
     * The child's stretches on screen from their event log (data/Briefs.read, plus Briefs.sessionSoFar for one under way),
     * as the insights engine merges them (gaps under five minutes are one session). [openNow]: the app is on screen as this
     * child right now (Briefs.sessionSoFar is not null), so their last stretch is still open whatever the log says.
     * Played: practice counted, or a lesson step tried, a lesson finished, a song played (not Listen), an ear round.
     */
    fun spans(events: List<BriefEvent>, now: Long, openNow: Boolean = false): List<PlaySpan> {
        val sorted = events.filter { it !is BriefEvent.Unknown && it.at <= now }.sortedWith(compareBy({ it.at }, { it.encode() }))
        val sessions = Sessions.of(sorted, now)
        val activity = sorted.filter { e ->
            e.name in PLAYED && when (e) {
                is BriefEvent.LessonStep -> e.tries > 0 && !e.skipped
                is BriefEvent.SongRun -> e.mode != RunMode.LISTEN
                else -> true
            }
        }.map { it.at }
        return sessions.mapIndexed { i, s ->
            val played = s.practiceSec > 0 || activity.any { it >= s.start && it <= s.end }
            // Sessions.of marks any session with no end event as open; only one that reaches now is still under way (a
            // crash's session ends at its last event, five minutes or more ago).
            val open = (s.open && s.end >= now) || (openNow && i == sessions.lastIndex)
            PlaySpan(s.start, if (open) null else s.end, played)
        }
    }

    // ---- wall-clock helpers (the phone's zone) ----

    fun date(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** [day] at [time] in [zone] (a time the clocks skip is the first moment after the gap, as java.time does). */
    fun at(day: LocalDate, time: LocalTime, zone: ZoneId): Long = ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()

    /** The last moment of [day]. */
    fun endOf(day: LocalDate, zone: ZoneId): Long = at(day.plusDays(1), LocalTime.MIDNIGHT, zone) - 1

    /** True from 21:30 to 07:00, local. */
    fun quiet(ms: Long, zone: ZoneId): Boolean {
        val t = Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()
        return t >= QUIET_FROM || t < QUIET_TO
    }

    /** [ms], or the end of the quiet hours it falls in (07:00 that morning or the next). */
    fun afterQuiet(ms: Long, zone: ZoneId): Long {
        if (!quiet(ms, zone)) return ms
        val local = Instant.ofEpochMilli(ms).atZone(zone)
        val day = if (local.toLocalTime() < QUIET_TO) local.toLocalDate() else local.toLocalDate().plusDays(1)
        return at(day, QUIET_TO, zone)
    }

    /** The latest moment at or before [now] that [slot] came round. */
    fun previousSlot(now: Long, zone: ZoneId, slot: WeeklySlot): Long {
        val today = date(now, zone)
        val time = LocalTime.of(slot.minutes / 60, slot.minutes % 60)
        var day = today.with(TemporalAdjusters.previousOrSame(slot.day))
        var t = at(day, time, zone)
        if (t > now) { day = day.minusWeeks(1); t = at(day, time, zone) }
        return t
    }

    /** The first moment after [now] that [slot] comes round. */
    fun nextSlot(now: Long, zone: ZoneId, slot: WeeklySlot): Long {
        val time = LocalTime.of(slot.minutes / 60, slot.minutes % 60)
        var day = date(now, zone).with(TemporalAdjusters.nextOrSame(slot.day))
        var t = at(day, time, zone)
        while (t <= now) { day = day.plusWeeks(1); t = at(day, time, zone) }
        return t
    }
}
