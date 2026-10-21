package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ForegroundClock
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * The one door into the brief's event logs (data/EventLog.kt): every place that knows something happened calls
 * `Briefs.log { at -> BriefEvent.X(at, ...) }` in one line, and the event lands in the log of whoever is playing
 * ([currentId], the profile MainActivity opened the app as). Stores that know whose they are (a duet partner's world,
 * the quest of each player) use [logFor]. Before [init] (JVM tests, previews) everything here does nothing.
 *
 * Also the session clock: MainActivity starts a session when the app comes on screen as someone and ends it when the app
 * leaves the screen or someone else takes over; the end carries the time on screen (ForegroundClock) and the practice
 * seconds the open profile's ProgressStore counted meanwhile ([practiceSeconds]).
 */
object Briefs {
    @Volatile private var filesDir: File? = null
    /** Who is playing: MainActivity sets it when it opens the app as someone (null while the picker asks). */
    @Volatile var currentId: String? = null
    private val logs = ConcurrentHashMap<String, EventLog>()
    /** The wall clock events are stamped with. */
    @Volatile var clock: () -> Long = System::currentTimeMillis
    /** Where the "brief:" lines go (logcat; JVM tests pass their own). */
    @Volatile var logcat: (String) -> Unit = { Log.d(TAG, it) }
    /** The open profile's practice seconds so far (ProgressStore.practiceSecondsTotal), for each session's end. */
    @Volatile var practiceSeconds: () -> Long = { 0L }

    private class Open(val profileId: String, val startFg: Long, val practice0: Long)
    @Volatile private var open: Open? = null

    /** Called by MainActivity at start: logs live under [context]'s files folder. */
    fun init(context: Context) { filesDir = context.applicationContext.filesDir }

    /** Tests only: back to before [init], every log closed. */
    internal fun reset() {
        logs.values.forEach { it.close() }; logs.clear()
        filesDir = null; currentId = null; open = null
        clock = System::currentTimeMillis; logcat = { Log.d(TAG, it) }; practiceSeconds = { 0L }
    }

    fun now(): Long = clock()

    /** Logs the event [make] builds (given the time) for whoever is playing; nothing while no one is (the picker). */
    fun log(make: (Long) -> BriefEvent) { currentId?.let { logFor(it, make(now())) } }

    /** Logs [event] for whoever is playing. */
    fun log(event: BriefEvent) { currentId?.let { logFor(it, event) } }

    /** Logs [events] in order for whoever is playing (a song run with its wrong keys). */
    fun logAll(events: List<BriefEvent>) { currentId?.let { id -> events.forEach { logFor(id, it) } } }

    /** Logs [event] for [profileId] (a store that knows whose it is). */
    fun logFor(profileId: String, event: BriefEvent) {
        val log = logOf(profileId) ?: return
        log.append(event)
        logcat("brief: $profileId ${event.encode()}")
    }

    /** Logs the event [make] builds for [profileId]. */
    fun logFor(profileId: String, make: (Long) -> BriefEvent) = logFor(profileId, make(now()))

    /** A setting changed for whoever is playing: [key] and its new value (BriefEvent.SettingChanged lists the keys). */
    fun setting(key: String, value: Any) = log { BriefEvent.SettingChanged(it, key, value.toString()) }

    /** A setting changed for [profileId]. */
    fun settingFor(profileId: String, key: String, value: Any) = logFor(profileId) { BriefEvent.SettingChanged(it, key, value.toString()) }

    /** A grown-up acted on a recommendation (the brief's action cards call this; ActionId names, Findings.kt). */
    fun action(actionId: String, params: Map<String, String> = emptyMap()) = log { BriefEvent.ParentAction(it, actionId, params) }

    /** [profileId]'s log (opened and pruned on first use), or null before [init]. */
    fun logOf(profileId: String): EventLog? {
        val dir = filesDir ?: return null
        return runCatching { logs.getOrPut(profileId) { EventLog(EventLog.fileFor(dir, profileId), clock = { clock() }) } }.getOrNull()
    }

    /**
     * [profileId]'s events from [since] to [until], oldest first. Waits for the writes before it: call it off the main
     * thread (the writers and screens of the brief: Insights.compute takes the result).
     */
    fun read(profileId: String, since: Long = Long.MIN_VALUE, until: Long = Long.MAX_VALUE): List<BriefEvent> =
        logOf(profileId)?.read(since, until).orEmpty()

    // ---- sessions ----

    /** The app is on screen as [profileId]: a session starts (nothing when one is already open for them). */
    fun sessionStart(profileId: String) {
        if (open?.profileId == profileId) return
        sessionEnd()
        open = Open(profileId, ForegroundClock.app.nowMs(), practiceSeconds())
        logFor(profileId, BriefEvent.SessionStarted(now()))
    }

    /** The app left the screen, or someone else takes over: the open session ends with its time on screen and practice. */
    fun sessionEnd() {
        val o = open ?: return
        open = null
        logFor(o.profileId, soFar(o))
    }

    /**
     * The open session as a provisional end now (not logged), for a session note while the session is still open: add it
     * to the events given to Insights.compute. Null when no session is open, or it is someone else's.
     */
    fun sessionSoFar(profileId: String, at: Long? = null): BriefEvent.SessionEnded? = open?.takeIf { it.profileId == profileId }?.let { soFar(it, at) }

    /**
     * [at]: when the provisional end is stamped. A reader computing findings "as of" a moment passes it: Insights drops
     * events after its `now`, and an end stamped a few ms later (read after the findings' clock) was dropped with its
     * practice, so every session note and the pup's card read an open session as no practice at all.
     */
    private fun soFar(o: Open, at: Long? = null) = BriefEvent.SessionEnded(at ?: now(), (ForegroundClock.app.nowMs() - o.startFg).coerceAtLeast(0),
        (practiceSeconds() - o.practice0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())

    // ---- profiles and backup ----

    /** [profileId] was deleted: its log goes with it (Profiles.delete). */
    fun forget(context: Context, profileId: String) {
        logs.remove(profileId)?.delete()
        if (open?.profileId == profileId) open = null
        runCatching { EventLog.fileFor(context.applicationContext.filesDir, profileId).delete() }
    }

    /** Every open log's pending lines written (before a backup reads the files). */
    fun flushAll() = logs.values.forEach { it.flush() }

    /** What a backup carries for [profileIds] (ProgressFiles.export). */
    fun exportLogs(context: Context, profileIds: List<String>): Map<String, List<String>> {
        flushAll()
        return EventLog.export(context.applicationContext.filesDir, profileIds)
    }

    /** A backup's logs put in place (ProgressFiles.replace; the activity is recreated afterwards). */
    fun restoreLogs(context: Context, events: Map<String, List<String>>?, profileIds: List<String>, allProfiles: Boolean) {
        if (!allProfiles) return
        logs.values.forEach { it.close() }
        logs.clear()
        open = null
        EventLog.restore(context.applicationContext.filesDir, events, profileIds, allProfiles)
    }
}

/**
 * Times how long the screen around it stays composed with the same [key], on the ForegroundClock (time off screen never
 * counts), and hands the ms to [onLeave] as it goes: one line for a toy's ToyOpened.
 */
@Composable
fun TimedOnScreen(key: Any?, onLeave: (Long) -> Unit) {
    DisposableEffect(key) {
        val start = ForegroundClock.app.nowMs()
        onDispose { onLeave(ForegroundClock.app.nowMs() - start) }
    }
}
