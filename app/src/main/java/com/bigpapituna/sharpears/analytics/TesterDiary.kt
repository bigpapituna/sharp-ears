package com.bigpapituna.sharpears.analytics

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.BriefEmail
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.email.EmailMessage
import com.bigpapituna.sharpears.engine.brief.email.EmailRound
import com.bigpapituna.sharpears.engine.brief.email.EmailSendException
import com.bigpapituna.sharpears.engine.tester.Diary
import com.bigpapituna.sharpears.engine.tester.DiaryDue
import com.bigpapituna.sharpears.engine.tester.DiaryPerson
import com.bigpapituna.sharpears.engine.tester.DiarySchedule
import com.bigpapituna.sharpears.engine.tester.TesterLog
import com.bigpapituna.sharpears.engine.tester.TesterRules
import java.time.ZoneId

/**
 * The tester diary on the phone: once a day ([DiarySchedule], the email brief's quiet hours), a plain-text email to Roy's own
 * address (BuildConfig.DIARY_TO, fixed at build time; never anyone else) through the brief's Brevo transport. It runs inside
 * the email job (reminder/EmailJob.kt: its alarm, its receiver, its thread), which asks [look] on every run and plan.
 *
 * The email holds the random device id, the app's version, the screens with the time on each, lessons and stages with tries
 * and let-gos, songs and toys, refusals and error lines, the AI and email results and every "Something felt off" note; people
 * are "kid p1" or "adult owner", never a name, and no address is in it.
 */
object TesterDiary {
    private const val DAY_MS = 24 * 60 * 60_000L

    /**
     * Looks at the diary now: sends it when [send] and it is due, else says when to look again (null: no need). Off the main
     * thread (it reads the logs and may send).
     */
    fun look(context: Context, send: Boolean, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        val app = context.applicationContext
        Tester.load(app)
        if (!Tester.diaryBuilt || !Tester.diaryOn.value) return null
        val prefs = Tester.prefsOf(app)
        val last = prefs.getLong(Tester.DIARY_LAST, 0L).takeIf { it > 0 }
        val from = last ?: (now - DAY_MS)
        val news = Tester.read(from, now).filterNot { it.name == TesterLog.SCREEN && (it as? com.bigpapituna.sharpears.engine.brief.BriefEvent.Unknown)?.fields?.get("s") == TesterLog.AWAY }
            .minOfOrNull { it.at }
        when (val due = DiarySchedule.decide(now, zone, last, news)) {
            is DiaryDue.Wait -> return due.atMs
            DiaryDue.Send -> if (!send) return now + EmailRound.SOON_MS
        }
        if (!BriefEmail.hasKey) { Log.d(TAG, "tester: diary not sent: NO_KEY"); return null }
        Tester.collectLog(app)
        val text = build(app, from, now, zone)
        val message = EmailMessage(Tester.diaryTo, Diary.subject(Tester.deviceId(app), now, zone), html(text), text)
        return try {
            BriefEmail.transport(app).send(message)
            prefs.edit(commit = true) { putLong(Tester.DIARY_LAST, now) }
            Tester.result("diary", "sent", "diary sent (${text.length} chars)")
            Log.d(TAG, "tester: diary sent (${text.length} chars)")
            null
        } catch (e: EmailSendException) {
            Tester.result("diary", e.kind.name, "diary not sent: ${e.kind}")
            Log.d(TAG, "tester: diary not sent: ${e.kind}")
            when (e.kind) {
                EmailSendException.Kind.NOT_ALLOWED -> now + EmailRound.METERED_RETRY_MS
                EmailSendException.Kind.NETWORK, EmailSendException.Kind.SERVER, EmailSendException.Kind.TIMEOUT -> now + EmailRound.RETRY_MS
                else -> null
            }
        }
    }

    /** The diary's text for [from] to [until]: the tester log and every profile's brief events. */
    fun build(context: Context, from: Long, until: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val app = context.applicationContext
        val people = runCatching { Profiles(app).all }.getOrDefault(emptyList()).map { p ->
            DiaryPerson("${TesterRules.profileKind(p.isKid)} ${p.id}", Briefs.read(p.id, from, until))
        }
        val version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "?"
        return Diary.text(Tester.deviceId(app), "$version (${BuildConfig.BUILD_TYPE})", from, until, zone, Tester.read(from, until), people)
    }

    /** The same text as a page (Brevo wants an HTML body): pre-formatted, escaped. */
    private fun html(text: String) = "<pre style=\"font-family:monospace;white-space:pre-wrap\">" +
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</pre>"
}
