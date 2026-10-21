package com.bigpapituna.sharpears.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bigpapituna.sharpears.MainActivity
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.BriefLoader
import com.bigpapituna.sharpears.data.BriefStore
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.BriefNotice
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.WeeklyPlan
import com.bigpapituna.sharpears.engine.family.Profile
import java.time.Instant
import java.time.ZoneId

/**
 * The weekly brief's notification (docs/PARENT-BRIEF-PLAN.md): one inexact alarm per person at the day and time their
 * grown-up chose ([BriefStore.weekly], default Sunday 6 pm), the same pattern as the daily practice reminder ([Reminder]).
 * When it goes off the receiver posts the weekly brief notification (only when the week had some playing in it) and sets the
 * next one; boot, a new time zone or a clock set by hand set every alarm again. The notification opens that person's week:
 * the launch intent carries [EXTRA_PROFILE] and [EXTRA_PERIOD] for MainActivity (ui/brief/INTEGRATION.md).
 */
object BriefReminder {
    const val EXTRA_PROFILE = "brief_profile"
    const val EXTRA_PERIOD = "brief_period"
    private const val CHANNEL = "brief"
    private const val NOTIFICATION_BASE = 200

    private fun pending(context: Context, profileId: String) = PendingIntent.getBroadcast(context, WeeklyPlan.requestCode(profileId),
        Intent(context, BriefReceiver::class.java).putExtra(EXTRA_PROFILE, profileId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Sets (or cancels, when it is off or the person is gone) [profile]'s next alarm; [id] names the alarm when [profile] is null. */
    fun schedule(context: Context, profile: Profile?, id: String = profile?.id.orEmpty()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val slot = profile?.let { BriefStore(context, it.id).weeklyOrDefault(it.isKid) }
        if (slot == null || !slot.on) { alarms.cancel(pending(context, id)); Log.d(TAG, "brief: weekly off for $id"); return }
        val at = WeeklyPlan.next(Instant.ofEpochMilli(System.currentTimeMillis()), ZoneId.systemDefault(), slot)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, id))
        Log.d(TAG, "brief: weekly for $id next at $at (${WeeklyPlan.label(slot)})")
    }

    /** Everyone's alarm again (app start, boot, a new time zone). */
    fun scheduleAll(context: Context) = Profiles(context).all.forEach { schedule(context, it) }

    /** Posts [profile]'s weekly notification now if the week had playing in it; false when it stayed quiet. Call off the main thread. */
    fun post(context: Context, profile: Profile, now: Long = System.currentTimeMillis()): Boolean {
        val store = BriefStore(context, profile.id)
        // A second alarm or a restart close to the first never sends it twice.
        if (store.lastNotice > 0 && now - store.lastNotice < 3L * 24 * 60 * 60 * 1000) { Log.d(TAG, "brief: ${profile.id} already told this week"); return false }
        val findings = runCatching { BriefLoader.findings(context, profile, Period.WEEK, now) }.getOrNull()
        if (findings == null || !BriefNotice.shouldSend(findings)) { Log.d(TAG, "brief: ${profile.id} quiet week, no notification"); return false }
        if (!Reminder.canNotify(context)) { Log.d(TAG, "brief: no notification permission for ${profile.id}"); return false }
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        // The week's brief is written now (the AI when it is on, within its weekly limit; the phone's words otherwise), so its
        // headline is the notification's text, and opening it reads the AI's cache instead of asking again.
        val headline = runCatching {
            kotlinx.coroutines.runBlocking { com.bigpapituna.sharpears.ui.brief.liveService(context, profile).write(findings, profile.name) }?.headline
        }.getOrNull()?.takeIf { it.isNotBlank() }
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Weekly brief", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A weekly note on how each player's week at the piano went, at the day and time set in the parent corner."
        })
        val open = PendingIntent.getActivity(context, WeeklyPlan.requestCode(profile.id),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_PROFILE, profile.id).putExtra(EXTRA_PERIOD, Period.WEEK.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // On a locked screen the name stays hidden: the public version says only that a brief is ready.
        val public = android.app.Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_logo_mono)
            .setContentTitle("A weekly brief is ready").build()
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setContentTitle(BriefNotice.title(profile.name, profile.kind))
            .setContentText(headline ?: BriefNotice.text(profile.kind))
            .setStyle(android.app.Notification.BigTextStyle().bigText(headline ?: BriefNotice.text(profile.kind)))
            .setContentIntent(open).setAutoCancel(true)
            .setVisibility(android.app.Notification.VISIBILITY_PRIVATE).setPublicVersion(public)
            .build()
        nm.notify(NOTIFICATION_BASE + (WeeklyPlan.requestCode(profile.id) and 0xFF), n)
        store.lastNotice = now
        Log.d(TAG, "brief: posted the weekly notification for ${profile.id}")
        return true
    }
}

/**
 * A person's weekly alarm went off: tell their grown-up (when there is something to say), then set next week's. A system
 * broadcast (boot, a new time zone, a clock set by hand: it has an action; the alarm's own intent has none) sets every alarm
 * again.
 */
class BriefReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        if (intent.action != null) { BriefReminder.scheduleAll(app); return }
        val id = intent.getStringExtra(BriefReminder.EXTRA_PROFILE) ?: return
        val pending = goAsync()
        Thread {
            try {
                val profile = Profiles(app).all.firstOrNull { it.id == id }
                if (profile != null) BriefReminder.post(app, profile)
                BriefReminder.schedule(app, profile, id)
            } catch (e: Exception) {
                Log.d(TAG, "brief: weekly alarm failed: $e")
            } finally { pending.finish() }
        }.start()
    }
}
