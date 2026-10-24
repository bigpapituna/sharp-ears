package com.bigpapituna.sharpears.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.bigpapituna.sharpears.MainActivity
import com.bigpapituna.sharpears.R
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.LessonStore
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.LessonItem
import com.bigpapituna.sharpears.engine.ReminderPlan
import com.bigpapituna.sharpears.engine.family.Profile
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily practice reminder. One inexact alarm (setAndAllowWhileIdle, so no exact-alarm permission) for the
 * next time; when it goes off the receiver posts the notification and sets tomorrow's. Boot and app start set it
 * again, since alarms do not survive a restart. Since 2.1 the reminder is the phone owner's: one alarm per phone, set
 * from the owner's Settings and read from the owner's files (other profiles do not see it).
 */
object Reminder {
    private const val CHANNEL = "practice"
    private const val NOTIFICATION = 1

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun ownerPrefs(context: Context) = Prefs(context, Profile.OWNER_ID)

    /** Sets the next alarm when the owner's reminder is on, cancels it when off. */
    fun schedule(context: Context) {
        val prefs = ownerPrefs(context)
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (!prefs.reminderOn) { alarms.cancel(pending(context)); Log.d(TAG, "reminder: off"); return }
        val at = ReminderPlan.next(LocalDateTime.now(), prefs.reminderMinutes)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending(context))
        Log.d(TAG, "reminder: next at $at")
    }

    fun canNotify(context: Context) = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts the reminder now, with what is waiting: chords due today and the song last played. */
    fun post(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Practice reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A daily nudge to practise, at the time set in Settings."
        })
        val prefs = ownerPrefs(context)
        // Nothing to nudge once today's goal is met or today's plan is done (the owner's own; QA D7).
        val progress = runCatching { com.bigpapituna.sharpears.data.ProgressStore(context, prefs) }.getOrNull()
        val today = com.bigpapituna.sharpears.data.AppClock.today()
        // 2.9: a kid's surprise ready to open (Tomorrow's surprise) is what the reminder says, whatever the owner's day.
        val surprise = runCatching {
            com.bigpapituna.sharpears.data.Profiles(context).all.filter { it.isKid }
                .any { com.bigpapituna.sharpears.engine.world.Surprise.ready(com.bigpapituna.sharpears.data.WorldStore(context, it.id).state.gift, today) }
        }.getOrDefault(false)
        if (progress != null && ReminderPlan.skip(progress.secondsToday(today), progress.goalMinutes, prefs.dailySummary(today) != null, surprise)) {
            Log.d(TAG, "reminder: not posted, today's goal is met or the plan is done"); return
        }
        val lessons = LessonStore(context, Profile.OWNER_ID)
        val due = runCatching {
            lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis()).count { it is LessonItem.ChordItem }
        }.getOrDefault(0)
        val song = prefs.lastSongId?.let { id -> runCatching { SongRepository(context).all().firstOrNull { it.id == id }?.title }.getOrNull() }
        val text = ReminderPlan.text(due, song, surprise)
        if (!canNotify(context)) { Log.d(TAG, "reminder: no notification permission; would say \"$text\""); return }
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        val n = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_mono)
            .setContentTitle("Sharp Ears")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION, n)
        Log.d(TAG, "reminder: posted \"$text\"")
    }
}

/**
 * The alarm went off: post the reminder, then set the next one. The phone restarted, changed time zone or had its clock
 * set (a system broadcast, with an action; the alarm's own intent has none): only set the next one again.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) { Reminder.schedule(context); return }
        Reminder.post(context)
        Reminder.schedule(context)
    }
}
