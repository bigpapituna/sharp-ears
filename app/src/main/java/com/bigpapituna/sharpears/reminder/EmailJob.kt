package com.bigpapituna.sharpears.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.BriefAi
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.data.BriefLoader
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.EmailSettings
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.email.EmailDue
import com.bigpapituna.sharpears.engine.brief.email.EmailFrequency
import com.bigpapituna.sharpears.engine.brief.email.EmailKind
import com.bigpapituna.sharpears.engine.brief.email.EmailRound
import com.bigpapituna.sharpears.engine.brief.email.EmailSchedule
import com.bigpapituna.sharpears.engine.brief.email.EmailSender
import com.bigpapituna.sharpears.engine.brief.email.PlaySpan
import com.bigpapituna.sharpears.engine.family.Profile
import java.time.ZoneId

/**
 * The parent brief by email, on the phone (engine/brief/email/INTEGRATION.md, "Scheduling the sends"): one inexact alarm
 * for the whole family, the same pattern as [BriefReminder] and [Reminder] (setAndAllowWhileIdle, a receiver, goAsync and a
 * thread), not WorkManager: the repo has no such dependency and the rule is "stdlib and platform APIs first". The decisions
 * are [EmailRound]'s (pure and tested); this loads each child's settings and play, sends through [BriefEmail]'s sender and
 * sets the alarm.
 *
 * It runs: when the alarm fires, at app start, at boot and when the time zone or clock changes ([EmailReceiver]), after
 * every session ends (a look only: it sets the alarm for the half hour after and sends nothing as the app leaves the
 * screen), at 07:00 and 20:30 every day (the alarm's checkpoints), and right after the grown-up enters an address, a code, a
 * choice or a weekly slot (the screen calls [run]). Sending never happens on the main thread; one run at a time, so nothing
 * goes twice.
 */
object EmailJob {
    private const val REQUEST = 70_001
    private val LOCK = Any()

    /** What a run did, per child: [EmailRound.Result] (what was sent, the last outcome, why it was held). */
    class Report(val results: Map<String, EmailRound.Result>)

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, REQUEST,
        Intent(context, EmailReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun setAlarm(context: Context, at: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (at == null) alarms.cancel(pending(context)) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    private class Kid(val profile: Profile, val store: EmailSettings)

    private fun kids(app: Context) = Profiles(app).all.filter { it.isKid }.map { Kid(it, EmailSettings.of(app, it.id)) }

    private fun spans(profileId: String, now: Long): List<PlaySpan> {
        val open = Briefs.sessionSoFar(profileId, at = now)
        return EmailSchedule.spans(Briefs.read(profileId) + listOfNotNull(open), now, openNow = open != null)
    }

    /**
     * Sends what is due now for every child with an address, records it, sets the family's alarm. Call off the main thread
     * (writing a brief may take 24 s, sending a few seconds). [reason] is for the log only.
     */
    fun run(context: Context, reason: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Report = synchronized(LOCK) {
        val app = context.applicationContext
        Briefs.init(app)          // the alarm may wake a process with no screen: the logs are read from the files folder
        val results = LinkedHashMap<String, EmailRound.Result>()
        val wakes = ArrayList<Long?>()
        var active = false
        val todo = kids(app).filter { it.store.load().address != null }
        // A process killed mid-send comes back through this alarm; the real one replaces it at the end.
        if (todo.isNotEmpty()) setAlarm(app, now + EmailRound.RETRY_MS)
        for (k in todo) {
            val prefs = k.store.load()
            val weekly = BriefStore(app, k.profile.id).weeklyOrDefault(kid = true)
            val child by lazy { BriefEmail.child(app, k.profile, weekly) }
            val writers by lazy { BriefAi.writers(app, k.profile) }
            val result = try {
                EmailRound.run(now, zone, prefs, weekly, spans(k.profile.id, now),
                    send = { due, p -> BriefEmail.sender(app, k.profile, zone).send(due, p, BriefLoader.findings(app, k.profile, due.period, due.findingsAt), writers.chain, child) },
                    record = { due, at -> k.store.update { it.sent(due.kind, due.day, at) } })
            } catch (e: Exception) {
                Log.d(TAG, "email: ${k.profile.id} failed in the job (${e.javaClass.simpleName})")
                EmailRound.Result(prefs, emptyList(), null, now + EmailRound.RETRY_MS, null)
            }
            // Why something is held (or nothing), for the screen to say.
            val held = result.held?.name
            val saved = if (k.store.load().held != held) k.store.update { it.copy(held = held) } else k.store.load()
            results[k.profile.id] = result.copy(prefs = saved)
            wakes += result.wakeAt
            active = active || EmailRound.active(saved)
        }
        // The tester diary (analytics/TesterDiary.kt) rides on the same job and alarm.
        wakes += runCatching { com.bigpapituna.sharpears.analytics.TesterDiary.look(app, send = true, now = now, zone = zone) }
            .onFailure { Log.d(TAG, "tester: diary failed in the job (${it.javaClass.simpleName})") }.getOrNull()
        val at = EmailRound.alarmAt(now, zone, wakes, active)
        setAlarm(app, at)
        Log.d(TAG, "email: job ($reason): ${results.values.sumOf { it.sent.size }} sent, ${todo.size} with an address, next look " +
            (at?.let { "in ${(it - now) / 60_000} min" } ?: "never"))
        Report(results)
    }

    /** [run] on a new thread (app start, a screen's button), calling [then] with the report when it is done. */
    fun runAsync(context: Context, reason: String, then: (Report) -> Unit = {}) {
        val app = context.applicationContext
        Thread { try { then(run(app, reason)) } catch (e: Exception) { Log.d(TAG, "email: job ($reason) failed: ${e.javaClass.simpleName}") } }.start()
    }

    /**
     * A look at every child that sends nothing: sets the family's alarm for the earliest wake (a day's half hour after the last
     * session, 20:30, 07:00, the weekly slot), or in a moment when something is due. For a session's end: the app is leaving the
     * screen, and the receiver, which has a proper lifetime, does the sending. Call off the main thread.
     */
    fun plan(context: Context, reason: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) = synchronized(LOCK) {
        val app = context.applicationContext
        Briefs.init(app)
        val wakes = ArrayList<Long?>()
        var active = false
        wakes += runCatching { com.bigpapituna.sharpears.analytics.TesterDiary.look(app, send = false, now = now, zone = zone) }.getOrNull()
        for (k in kids(app)) {
            val prefs = k.store.load()
            if (prefs.address == null) continue
            val weekly = BriefStore(app, k.profile.id).weeklyOrDefault(kid = true)
            wakes += EmailRound.wake(now, zone, prefs, weekly, spans(k.profile.id, now))
            active = active || EmailRound.active(prefs)
        }
        val at = EmailRound.alarmAt(now, zone, wakes, active)
        setAlarm(app, at)
        Log.d(TAG, "email: planned ($reason): next look " + (at?.let { "in ${(it - now) / 60_000} min" } ?: "never"))
    }

    /** A child's session ended (Briefs.onSessionEnd): plan on a thread, since the log is read. */
    fun afterSession(context: Context) {
        val app = context.applicationContext
        Thread { try { plan(app, "session ended") } catch (e: Exception) { Log.d(TAG, "email: plan failed: ${e.javaClass.simpleName}") } }.start()
    }

    /**
     * "Send me a test email now": today's brief (or the week's) to the verified address, through the same sender and the same
     * rules as the schedule, whatever the frequency; it counts toward the two a day and changes nothing else. Off the main thread.
     */
    fun sendTest(context: Context, profile: Profile, period: Period, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): EmailSender.Outcome = synchronized(LOCK) {
        val app = context.applicationContext
        val store = EmailSettings.of(app, profile.id)
        val prefs = store.load()
        if (!prefs.verified) return EmailSender.Outcome.Refused(EmailSender.Refusal.UNVERIFIED)
        val weekly = BriefStore(app, profile.id).weeklyOrDefault(profile.isKid)
        val due = EmailDue.Send(if (period == Period.WEEK) EmailKind.WEEKLY else EmailKind.DAILY, EmailSchedule.date(now, zone), period, now)
        val findings = BriefLoader.findings(app, profile, period, now)
        // Whatever the frequency: a test is asked for. Only its count is kept.
        val out = BriefEmail.sender(app, profile, zone).send(due, prefs.copy(frequency = EmailFrequency.BOTH), findings, BriefAi.writers(app, profile).chain,
            BriefEmail.child(app, profile, weekly))
        if (out is EmailSender.Outcome.Sent) store.update { it.counted(out.prefs.sends.last()) }
        out
    }
}

/**
 * The email alarm went off, or the phone restarted, or its clock or time zone changed: send what is due and set the next
 * alarm. One receiver for all of them (the job does the same each time).
 */
class EmailReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        val reason = intent.action?.substringAfterLast('.')?.lowercase() ?: "alarm"
        Thread {
            try { EmailJob.run(app, reason) }
            catch (e: Exception) { Log.d(TAG, "email: alarm failed: ${e.javaClass.simpleName}") }
            finally { pending.finish() }
        }.start()
    }
}
