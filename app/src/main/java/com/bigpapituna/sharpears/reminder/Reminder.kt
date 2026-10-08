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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The daily practice reminder. One inexact alarm (setAndAllowWhileIdle, so no exact-alarm permission) for the
 * next time; when it goes off the receiver posts the notification and sets tomorrow's. Boot and app start set it
 * again, since alarms do not survive a restart.
 */
object Reminder {
    private const val CHANNEL = "practice"
    private const val NOTIFICATION = 1

    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Sets the next alarm when the reminder is on, cancels it when off. */
    fun schedule(context: Context, prefs: Prefs = Prefs(context)) {
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
        val prefs = Prefs(context)
        val lessons = LessonStore(context)
        val due = runCatching {
            lessons.curriculum().queue(lessons.mastery(), System.currentTimeMillis()).count { it is LessonItem.ChordItem }
        }.getOrDefault(0)
        val song = prefs.lastSongId?.let { id -> runCatching { SongRepository(context).all().firstOrNull { it.id == id }?.title }.getOrNull() }
        val text = ReminderPlan.text(due, song)
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

/** The alarm went off (or the phone restarted): post the reminder, then set the next one. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminder.schedule(context); return }
        Reminder.post(context)
        Reminder.schedule(context)
    }
}
