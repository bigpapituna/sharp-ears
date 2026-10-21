package com.bigpapituna.sharpears.engine.brief.email

import com.bigpapituna.sharpears.engine.brief.WeeklySlot
import java.time.ZoneId

/**
 * One child's turn in the email job (reminder/EmailJob.kt calls it on the phone; Android-free, so the job's decisions are
 * tested with a fake clock and a fake sender). It asks [EmailSchedule.decide], sends what is due through [send] (at most
 * [MAX_ROUNDS], since two go a day at most), and says when to look again.
 *
 * The job never sends from the UI thread and never twice: [send] is the real [EmailSender] on the phone, and every send is
 * recorded through [record] on the freshest settings (a grown-up may have changed the frequency while it was sending).
 */
object EmailRound {
    /** How long to wait before trying again after a failure (offline, Brevo down), or a brief that could not be written. */
    const val RETRY_MS = 15 * 60_000L
    /** How long to wait before trying again while the network is metered and the grown-up has not allowed mobile data. */
    const val METERED_RETRY_MS = 30 * 60_000L
    /** Something is due now: look again in a moment (the receiver, which has a proper lifetime, sends it). */
    const val SOON_MS = 2_000L
    const val MAX_ROUNDS = 3

    /** How a turn ended: what [sent], the [last] outcome (null when nothing was due), when to look again ([wakeAt]) and the [held] reason. */
    data class Result(val prefs: EmailPrefs, val sent: List<EmailKind>, val last: EmailSender.Outcome?, val wakeAt: Long?, val held: EmailSender.Refusal?)

    /**
     * Sends what is due now for one child whose settings are [prefs]. [send] sends one email (the sender's `send`); [record]
     * saves a send that went (the kind and day, the time it left) and returns the settings as saved (without it the turn's own settings record it).
     */
    fun run(
        now: Long, zone: ZoneId, prefs: EmailPrefs, weekly: WeeklySlot, spans: List<PlaySpan>, caps: EmailCaps = EmailCaps(),
        send: (EmailDue.Send, EmailPrefs) -> EmailSender.Outcome,
        record: ((EmailDue.Send, Long) -> EmailPrefs)? = null,
    ): Result {
        var current = prefs
        val sent = ArrayList<EmailKind>()
        var last: EmailSender.Outcome? = null
        repeat(MAX_ROUNDS) {
            val due = EmailSchedule.decide(now, zone, current.plan(weekly), spans, caps)
            if (due is EmailDue.Wait) return Result(current, sent, last, due.atMs, held = null)
            due as EmailDue.Send
            when (val out = send(due, current)) {
                is EmailSender.Outcome.Sent -> {
                    val at = out.prefs.sends.lastOrNull() ?: now
                    current = record?.invoke(due, at) ?: current.sent(due.kind, due.day, at)
                    sent += due.kind; last = out
                }
                is EmailSender.Outcome.Refused -> return Result(current, sent, out, retryFor(out.why, now), out.why)
                is EmailSender.Outcome.Failed -> return Result(current, sent, out, now + RETRY_MS, held = null)
            }
        }
        return Result(current, sent, last, now + SOON_MS, held = null)
    }

    /** When to try again after a refusal: a metered network and a brief that was not written are tried again; the rest wait for a change. */
    fun retryFor(why: EmailSender.Refusal, now: Long): Long? = when (why) {
        EmailSender.Refusal.METERED -> now + METERED_RETRY_MS
        EmailSender.Refusal.NOT_WRITTEN -> now + RETRY_MS
        else -> null
    }

    /**
     * When to look at one child again, without sending anything (after a session ends, at start, at boot): [SOON_MS] from now
     * when an email is due, else the schedule's own wake (a day's half hour after the last session, 20:30, 07:00, the weekly
     * slot, the caps freeing up). Null when this child needs nothing.
     */
    fun wake(now: Long, zone: ZoneId, prefs: EmailPrefs, weekly: WeeklySlot, spans: List<PlaySpan>, caps: EmailCaps = EmailCaps()): Long? =
        when (val due = EmailSchedule.decide(now, zone, prefs.plan(weekly), spans, caps)) {
            is EmailDue.Send -> now + SOON_MS
            is EmailDue.Wait -> due.atMs
        }

    /** The next 07:00 or 20:30 after [now]: the job looks at every child then, whatever else happened (a phone that missed a session's end). */
    fun nextCheckpoint(now: Long, zone: ZoneId): Long {
        val today = EmailSchedule.date(now, zone)
        return listOf(
            EmailSchedule.at(today, EmailSchedule.QUIET_TO, zone), EmailSchedule.at(today, EmailSchedule.DAY_CUTOFF, zone),
            EmailSchedule.at(today.plusDays(1), EmailSchedule.QUIET_TO, zone),
        ).first { it > now }
    }

    /** True when emails may go for [prefs] at all (an address waiting for its first email, or a verified one that is not off). */
    fun active(prefs: EmailPrefs): Boolean = prefs.address != null && (prefs.pending && !prefs.firstSent || prefs.verified && prefs.frequency != EmailFrequency.OFF)

    /**
     * The family's one alarm: the earliest of the children's wakes (not before [MIN_AHEAD_MS] from [now]) and the next
     * checkpoint when any child is [active]; null when nobody needs one.
     */
    fun alarmAt(now: Long, zone: ZoneId, wakes: List<Long?>, anyActive: Boolean): Long? {
        val at = (wakes.filterNotNull() + listOfNotNull(if (anyActive) nextCheckpoint(now, zone) else null)).minOrNull() ?: return null
        return maxOf(at, now + MIN_AHEAD_MS)
    }

    const val MIN_AHEAD_MS = 1_500L
}
