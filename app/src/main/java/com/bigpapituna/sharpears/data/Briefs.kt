package com.bigpapituna.sharpears.data

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.engine.ForegroundClock
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import androidx.core.content.edit
import com.bigpapituna.sharpears.engine.brief.MinutesLedger
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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
 *
 * 2.14, the one minutes ledger (engine/brief/MinutesLedger.kt, Priya's P11): the open session is checkpointed ([checkpoint],
 * every 30 s and on every pause) into one small record ("session" preferences, never backed up); a session the app never
 * ended (force-stopped, swiped away mid-step, an install, a crash) is closed on the next start ([init]) from that record,
 * with the time on screen of its last checkpoint and every practice second the stores counted for it. Before a checkpoint
 * and an end, the screens that count practice in the background write what they hold ([addFlusher]), so the stores and
 * the log never disagree by the last few seconds. At midnight a checkpoint ends the session and starts the next one, so
 * a late evening never lends minutes to the next day.
 */
object Briefs {
    @Volatile private var filesDir: File? = null
    /** The app's preferences by file name (set by [init]; no Context is kept here). */
    @Volatile private var prefsOf: ((String) -> android.content.SharedPreferences)? = null
    /** The open session's record was looked at in this process (a second [init], from the email job, leaves it alone). */
    @Volatile private var recovered = false
    /** Who is playing: MainActivity sets it when it opens the app as someone (null while the picker asks). */
    @Volatile var currentId: String? = null
    private val logs = ConcurrentHashMap<String, EventLog>()
    /** The wall clock events are stamped with. */
    @Volatile var clock: () -> Long = System::currentTimeMillis
    /** Where the "brief:" lines go (logcat; JVM tests pass their own). */
    @Volatile var logcat: (String) -> Unit = { Log.d(TAG, it) }
    /** The open profile's practice seconds so far (ProgressStore.practiceSecondsTotal), for each session's end. */
    @Volatile var practiceSeconds: () -> Long = { 0L }

    /**
     * Called (on the thread that ended the session, the main one) after a session's end is logged, with whose it was: the email
     * job looks at that child again (reminder/EmailJob.kt). Set by MainActivity; null in JVM tests and previews.
     */
    @Volatile var onSessionEnd: ((String) -> Unit)? = null

    /** Sees every event as it is logged (analytics/Tester.briefEvent, while "Help Roy test" records). Null in JVM tests. */
    @Volatile var tap: ((BriefEvent) -> Unit)? = null

    /** The time on screen (ForegroundClock.app; JVM tests set their own). */
    @Volatile var foreground: () -> Long = { ForegroundClock.app.nowMs() }
    /** The day boundary the midnight checkpoint and today's time go by. */
    @Volatile var zone: ZoneId = ZoneId.systemDefault()
    /**
     * Any profile's practice seconds so far (ProgressStore's "totalSeconds", read from its file): what a session closed on
     * the next start counted, the total now less the total at its start. Tests set their own.
     */
    @Volatile var practiceTotalOf: (String) -> Long = ::storedPracticeTotal

    private fun storedPracticeTotal(id: String): Long = prefsOf?.invoke(ProgressFiles.fileFor("profile", id))?.getLong("totalSeconds", 0L) ?: 0L

    private class Open(val profileId: String, val startFg: Long, val practice0: Long, val startedAt: Long = 0L)
    @Volatile private var open: Open? = null

    /** What writes its practice now (a player, a stage, a game, the chords part): called before a checkpoint and an end. */
    private val flushers = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    /** [f] writes the practice its screen still holds; [removeFlusher] when the screen goes. */
    fun addFlusher(f: () -> Unit) { flushers += f }
    fun removeFlusher(f: () -> Unit) { flushers -= f }
    private fun flushPractice() = flushers.forEach { f -> runCatching(f).onFailure { logcat("brief: a practice flush failed: ${it.javaClass.simpleName}") } }

    /**
     * Called by MainActivity at start (and by the email job, which may wake a process with no screen): logs live under
     * [context]'s files folder. The first call in a process closes a session the app never ended ([recover]).
     */
    fun init(context: Context) {
        filesDir = context.applicationContext.filesDir
        val app = context.applicationContext
        prefsOf = { name -> app.getSharedPreferences(name, Context.MODE_PRIVATE) }
        if (!recovered && open == null) { recovered = true; recover() }
    }

    /** Tests only: back to before [init], every log closed. */
    internal fun reset() {
        logs.values.forEach { it.close() }; logs.clear()
        filesDir = null; prefsOf = null; recovered = false; currentId = null; open = null; flushers.clear(); today.clear()
        clock = System::currentTimeMillis; logcat = { Log.d(TAG, it) }; practiceSeconds = { 0L }; onSessionEnd = null; tap = null
        foreground = { ForegroundClock.app.nowMs() }; zone = ZoneId.systemDefault()
        practiceTotalOf = ::storedPracticeTotal
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
        tap?.let { t -> runCatching { t(event) } }
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
        val o = Open(profileId, foreground(), practiceSeconds(), now())
        open = o
        logFor(profileId, BriefEvent.SessionStarted(o.startedAt))
        mark(o, o.startedAt, 0L, 0)
    }

    /** The app left the screen, or someone else takes over: the open session ends with its time on screen and practice. */
    fun sessionEnd() = end(flush = true)

    private fun end(flush: Boolean) {
        if (open == null) return
        if (flush) flushPractice()                        // the last seconds a player or a stage still holds count in this session
        val o = open ?: return
        open = null
        val end = soFar(o)
        logFor(o.profileId, end)
        clearMark()
        today[o.profileId]?.let { t -> if (day(o.startedAt) == t.date) today[o.profileId] = t.copy(screenMs = t.screenMs + end.durationMs) }
        runCatching { onSessionEnd?.invoke(o.profileId) }.onFailure { logcat("brief: session end hook failed: ${it.javaClass.simpleName}") }
    }

    /**
     * The open session so far, kept (2.14): MainActivity calls it every [CHECKPOINT_MS] while the app is on screen and on every
     * pause. A session open across midnight ends and starts again here, so each day keeps its own minutes.
     */
    fun checkpoint() {
        val was = open ?: return
        if (day(now()) != day(was.startedAt)) {
            // What the screens still hold is the new day's (the stores date it when it is written): the old session ends first.
            logcat("brief: midnight, ${was.profileId}'s session ends and a new one starts")
            end(flush = false); sessionStart(was.profileId)
        }
        flushPractice()
        val o = open ?: return
        val e = soFar(o)
        mark(o, e.at, e.durationMs, e.practiceSec)
    }

    /** A session the app never ended: its end is written now from its last checkpoint (at most [CHECKPOINT_MS] behind). */
    private fun recover() {
        val p = prefs() ?: return
        val id = p.getString(MARK_PROFILE, null) ?: return
        val startedAt = p.getLong(MARK_STARTED, 0L)
        val lastAt = p.getLong(MARK_AT, startedAt)
        val ms = p.getLong(MARK_MS, 0L)
        val practice = (practiceTotalOf(id) - p.getLong(MARK_PRACTICE0, 0L)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        clearMark()
        if (startedAt <= 0L || !EventLog.ID_PATTERN.matches(id)) return
        logFor(id, BriefEvent.SessionEnded(maxOf(lastAt, startedAt) + 1, ms, practice))
        logcat("brief: $id's session was never ended (the app was closed while it played): kept ${ms / 1000} s on screen, $practice s of practice")
    }

    private fun mark(o: Open, at: Long, ms: Long, practice: Int) {
        prefs()?.edit {
            putString(MARK_PROFILE, o.profileId); putLong(MARK_STARTED, o.startedAt); putLong(MARK_PRACTICE0, o.practice0)
            putLong(MARK_AT, at); putLong(MARK_MS, ms); putInt(MARK_PRACTICE, practice)
        }
    }

    private fun clearMark() { prefs()?.edit { clear() } }

    private fun prefs() = prefsOf?.invoke(SESSION_FILE)

    private fun day(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    // ---- today's time on screen (2.14, a kid's daily time) ----

    private val today = ConcurrentHashMap<String, MinutesLedger.Day>()

    /**
     * [profileId]'s time on screen today so far, from the one ledger: the sessions ended today (read from the log once a day,
     * then kept up to date as sessions end) and the open one. Reads the log on first use: call it off the main thread.
     */
    fun screenMsToday(profileId: String): Long {
        val now = now()
        val d = day(now)
        val o = open?.takeIf { it.profileId == profileId }
        val closed = today[profileId]?.takeIf { it.date == d }?.screenMs ?: run {
            // Sessions before the open one (whose start is in the log already, and must not count twice).
            val events = read(profileId).filter { o == null || it.at < o.startedAt }
            MinutesLedger.day(events, d, now, zone).also { today[profileId] = it }.screenMs
        }
        val live = if (o != null && day(o.startedAt) == d) (foreground() - o.startFg).coerceAtLeast(0) else 0L
        return closed + live
    }

    /** Tests and debug walks: forget the kept day, so the next [screenMsToday] reads the log again. */
    fun forgetToday(profileId: String) { today.remove(profileId) }

    /**
     * Debug walks only (`--ei practice`, `--ei onscreen`, `--ez sleepy`, MainActivity's kid seeds): [screenMs] on screen with
     * [practiceSec] practised today, as one session that ended just before now (or before the open one began). The caller has
     * already written the practice to the stores; an open session of [profileId] counts it as this one's, not its own.
     */
    fun debugSession(profileId: String, screenMs: Long, practiceSec: Int) {
        val o = open
        val end = minOf(now(), o?.startedAt ?: Long.MAX_VALUE) - 1_000
        logFor(profileId, BriefEvent.SessionStarted(end - screenMs))
        logFor(profileId, BriefEvent.SessionEnded(end, screenMs, practiceSec))
        if (o != null && o.profileId == profileId) open = Open(o.profileId, o.startFg, o.practice0 + practiceSec, o.startedAt)
        today.remove(profileId)
    }

    /** Every 30 s while the app is on screen (and on every pause). */
    const val CHECKPOINT_MS = 30_000L
    /** The open session's record, never backed up (res/xml backup_rules.xml and data_extraction_rules.xml). */
    const val SESSION_FILE = "session"
    private const val MARK_PROFILE = "profile"
    private const val MARK_STARTED = "startedAt"
    private const val MARK_PRACTICE0 = "practice0"
    private const val MARK_AT = "at"
    private const val MARK_MS = "ms"
    private const val MARK_PRACTICE = "practice"

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
    private fun soFar(o: Open, at: Long? = null) = BriefEvent.SessionEnded(at ?: now(), (foreground() - o.startFg).coerceAtLeast(0),
        (practiceSeconds() - o.practice0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())

    // ---- profiles and backup ----

    /** [profileId] was deleted: its log goes with it (Profiles.delete). */
    fun forget(context: Context, profileId: String) {
        logs.remove(profileId)?.delete()
        today.remove(profileId)
        if (open?.profileId == profileId) { open = null; clearMark() }
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
        clearMark(); today.clear()
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
