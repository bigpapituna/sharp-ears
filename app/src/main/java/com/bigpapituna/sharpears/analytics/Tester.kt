package com.bigpapituna.sharpears.analytics

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import com.bigpapituna.sharpears.BuildConfig
import com.bigpapituna.sharpears.TAG
import com.bigpapituna.sharpears.data.EventLog
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.tester.TesterEvent
import com.bigpapituna.sharpears.engine.tester.TesterLog
import com.bigpapituna.sharpears.engine.tester.TesterRules
import com.bigpapituna.sharpears.engine.tester.TesterTally
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Tester analytics on the phone (CLAUDE.md "Tester analytics"; the rules in engine/tester). Two phone-wide switches behind the
 * parent gate, both off by default, kept in [FILE] (tester.xml, left out of Android's backup with the tester log):
 *
 * - "Help Roy test": while on, and only on a build with POSTHOG_KEY (the SDK is compiled in only then), PostHog records the
 *   screen (masked text and images, never sound) and the app's own events, under a random device id made once on this phone.
 *   Off: nothing is set up; turned off after it ran, the replay stops and PostHog is opted out, nothing flushed.
 * - "Email Roy a daily diary" (only on a build with DIARY_TO): a plain-text email to Roy once a day (TesterDiary).
 *
 * While either is on, what happens on screen goes to the tester log (files/tester/diary.log, EventLog's format), read only by
 * the diary. Nothing in either carries a name or an address: screens are route names, profiles are "kid" or "adult" (and the
 * diary's profile id), notes and log lines lose addresses and the family's names. Log lines here say "tester: ..." and carry
 * no key, id or text.
 */
@android.annotation.SuppressLint("StaticFieldLeak")      // holds the application context only ([app])
object Tester {
    /** The switches, the device id and the diary's marks: tester.xml, phone-wide, never backed up (res/xml/backup_rules.xml). */
    const val FILE = "tester"
    /** The tester log's folder under the app's files folder; never backed up either. */
    const val DIR = "tester"
    private const val ON = "on"
    private const val DIARY = "diary"
    private const val DEVICE = "device"
    internal const val DIARY_LAST = "diaryLast"
    private const val LOG_SINCE = "logSince"

    /** This build carries a PostHog project key. */
    val hasKey: Boolean get() = BuildConfig.POSTHOG_KEY.isNotBlank()
    /** The diary's address (Roy's own), or "" on a build without one. Read only here and by TesterDiary. */
    internal val diaryTo: String get() = BuildConfig.DIARY_TO.trim()
    /** This build can email the diary (the switch shows). */
    val diaryBuilt: Boolean get() = TesterRules.diaryShown(diaryTo)

    /** The switches as the screens show them (the whistle's dot reads [switchOn]). */
    val switchOn = mutableStateOf(false)
    val diaryOn = mutableStateOf(false)

    /** Where the "tester: ..." lines go (logcat; JVM tests pass their own). */
    @Volatile var logcat: (String) -> Unit = { Log.d(TAG, it) }

    /** The application context only (never an activity): it lives as long as the process anyway. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    @Volatile private var app: Context? = null
    @Volatile private var logFile: EventLog? = null
    private val tally = TesterTally()
    @Volatile private var route: String? = null
    @Volatile private var lastRoute: String? = null
    @Volatile private var screen: String? = null
    @Volatile private var before: String? = null
    @Volatile private var kind: Pair<String, String>? = null

    private fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun debuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** "Help Roy test" shows here (a build with a key, or Roy's debug build without one). */
    fun rowShown(context: Context) = TesterRules.rowShown(hasKey, debuggable(context))

    /** Reads the switches (once per process; the email job's alarm may run in a process with no screen). Starts nothing. */
    fun load(context: Context) {
        if (app != null) return
        val a = context.applicationContext
        val p = prefs(a)
        switchOn.value = p.getBoolean(ON, false)
        diaryOn.value = p.getBoolean(DIARY, false)
        app = a
    }

    /** Called at the app's start: reads the switches and sets PostHog up only when it may start. */
    fun init(context: Context) {
        val a = context.applicationContext
        load(a)
        logcat("tester: help Roy test ${if (switchOn.value) "on" else "off"}, diary ${if (diaryOn.value) "on" else "off"}, " +
            "dashboard ${if (hasKey && TesterSdk.present) "built in" else "not in this build"}")
        startIfAllowed(a)
    }

    private fun startIfAllowed(a: Context) {
        if (!TesterRules.sdkMayStart(TesterSdk.present, hasKey, switchOn.value)) return
        val ok = runCatching { TesterSdk.start(a, BuildConfig.POSTHOG_KEY, BuildConfig.POSTHOG_HOST, deviceId(a)) }
            .onFailure { logcat("tester: the dashboard did not start (${it.javaClass.simpleName})") }.getOrDefault(false)
        if (ok) {
            logcat("tester: dashboard recording")
            kind?.let { (k, _) -> TesterSdk.register("profile_kind", k) }
            route?.let { r -> TesterSdk.screen(TesterRules.screenOf(r), mapOf("route" to TesterRules.safeRoute(r))) }
        }
    }

    /** The random id this phone goes by in the dashboard and the diary, made once (never a name, an email or a hardware id). */
    fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(DEVICE, null)?.let { return it }
        return UUID.randomUUID().toString().also { id -> p.edit(commit = true) { putString(DEVICE, id) } }
    }

    fun setOn(context: Context, on: Boolean) {
        val a = context.applicationContext
        val was = recording
        prefs(a).edit { putBoolean(ON, on) }
        switchOn.value = on
        logcat("tester: help Roy test ${if (on) "on" else "off"}")
        if (on) { if (TesterSdk.running) TesterSdk.resume() else startIfAllowed(a) } else TesterSdk.pause()
        startedRecording(was)
    }

    /** Recording just began: the log gets who is playing and the screen on show, so the diary's first minutes count. */
    private fun startedRecording(was: Boolean) {
        if (was || !recording) return
        kind?.let { (k, id) -> write(TesterLog.kind(now(), k, id)) }
        screen?.takeIf { route != null }?.let { write(TesterLog.screen(now(), it)) }
    }

    fun setDiary(context: Context, on: Boolean) {
        val was = recording
        prefs(context).edit { putBoolean(DIARY, on) }
        diaryOn.value = on
        startedRecording(was)
        logcat("tester: diary ${if (on) "on" else "off"}")
    }

    private val recording get() = TesterRules.records(switchOn.value, diaryOn.value, diaryTo)
    private val capturing get() = switchOn.value && TesterSdk.running

    private fun capture(e: TesterEvent) { if (capturing) runCatching { TesterSdk.capture(e.name, e.props) } }

    /** The tester log (opened on first use), or null before [init]. */
    internal fun log(): EventLog? {
        logFile?.let { return it }
        val a = app ?: return null
        return synchronized(this) { logFile ?: EventLog(File(File(a.filesDir, DIR), "diary.log"), maxEvents = 5_000, keepMs = 14L * 24 * 60 * 60 * 1000).also { logFile = it } }
    }

    private fun write(e: BriefEvent) { if (recording) log()?.append(e) }

    private fun now() = System.currentTimeMillis()

    // ---- what happens ----

    /** The screen on show changed ([route] as MainActivity.routeOf writes it; the same route again is ignored). */
    fun screen(route: String) {
        if (route == this.route) return
        val was = this.route
        this.route = route; lastRoute = route
        val name = TesterRules.screenOf(route)
        if (name != screen) { before = screen; screen = name }
        write(TesterLog.screen(now(), name))
        if (capturing) {
            runCatching { TesterSdk.screen(name, mapOf("route" to TesterRules.safeRoute(route))) }
            TesterRules.lessonStarted(was, route)?.let(::capture)
        }
    }

    /** The app left the screen (onStop): the time on the last screen ends. */
    fun away() { if (route == null) return; route = null; write(TesterLog.screen(now(), TesterLog.AWAY)) }

    /** The app is back (onStart): the screen it shows starts again. */
    fun back() { lastRoute?.let { if (route == null) screen(it) } }

    /** Who is playing changed: their kind goes with every later event; the diary gets the kind and the profile id. */
    fun profile(isKid: Boolean, profileId: String) {
        val k = TesterRules.profileKind(isKid) to profileId
        if (k == kind) return
        kind = k
        write(TesterLog.kind(now(), k.first, profileId))
        if (capturing) { runCatching { TesterSdk.register("profile_kind", k.first) }; capture(TesterEvent("profile_kind", mapOf("kind" to k.first))) }
    }

    /** A brief event was logged (Briefs.tap): the lesson, stage, song and toy events it makes. */
    fun briefEvent(e: BriefEvent) { if (capturing) runCatching { tally.eventsFor(e) }.getOrDefault(emptyList()).forEach(::capture) }

    /** One of the app's own events ([name] and ids or numbers only). */
    fun event(name: String, props: Map<String, Any> = emptyMap()) = capture(TesterEvent(name, props))

    /**
     * 2.9, the retention events (engine/tester/Retention.kt): comeback_shown, comeback_played, rest_day_used, surprise_wrapped,
     * surprise_opened, each with the profile's kind ("kid" or "adult") and nothing else.
     */
    fun retention(name: String, kid: Boolean) {
        capture(com.bigpapituna.sharpears.engine.tester.Retention.event(name, kid))
        logcat("retention: $name (${TesterRules.profileKind(kid)})")
    }

    /** 2.11, the step pop (engine/tester/StepPopEvent.kt): `step_pop` with the streak's length and nothing else. */
    fun stepPop(streak: Int) = capture(com.bigpapituna.sharpears.engine.tester.StepPopEvent.of(streak))

    /** An error was put on screen at [where] (a short place name of the app's own, never the message). */
    fun error(where: String) {
        write(TesterLog.error(now(), where))
        capture(TesterEvent("error_shown", buildMap { put("where", where); screen?.let { put("screen", it) } }))
    }

    /** An "ai: ..." or "email: ..." log line (data/BriefAi.kt, data/BriefEmail.kt): a result becomes an event and a diary line. */
    fun logLine(line: String) {
        val (kind, outcome) = TesterRules.resultOf(line) ?: return
        result(kind, outcome, line)
    }

    /** A send's result: "ai", "email" or "diary", the outcome in one word, the (safe) log line. */
    fun result(kind: String, outcome: String, line: String) {
        if (!recording) return
        val names = names()
        write(TesterLog.result(now(), kind, outcome, TesterRules.safeLine(line.substringBefore(" dropped "), names)))
        when (kind) {
            "ai" -> capture(TesterEvent("ai_brief_result", mapOf("result" to outcome)))
            "email" -> capture(TesterEvent("email_sent_result", mapOf("result" to outcome)))
        }
    }

    /** "Something felt off": the tester's note, with the screen it was on and the one before. */
    fun note(text: String) {
        val t = TesterRules.safeLine(text, names(), max = 300).ifBlank { return }
        val on = screen ?: "?"
        log()?.append(TesterLog.note(now(), on, before, t))
        capture(TesterEvent("felt_off", mapOf("screen" to on)))
        logcat("tester: note on $on (${t.length} chars)")
    }

    private fun names(): List<String> = runCatching { app?.let { a -> Profiles(a).all.map { it.name } } }.getOrNull().orEmpty()

    // ---- the log's own lines ----

    private val INTERESTING = Regex("(?i)\\b(refus\\w*|denied|not allowed|failed|fail|error|exception|crash\\w*|NO_KEY|NOT_ALLOWED|ANR)\\b")

    /**
     * Copies the app's own refusal and error lines from logcat (tag SharpEars, and crashes) since the last copy into the tester
     * log, without addresses or the family's names; the ai: and email: results are in it already. Best effort: logcat keeps
     * only the last few hundred KB. Off the main thread (a session's end, the diary).
     */
    fun collectLog(context: Context) {
        if (!recording) return
        val a = context.applicationContext
        val p = prefs(a)
        val since = p.getLong(LOG_SINCE, now() - 10 * 60_000L)
        var last = since
        val names = names()
        var kept = 0
        runCatching {
            val proc = ProcessBuilder("logcat", "-d", "-v", "epoch", "-t", String.format(Locale.ROOT, "%d.%03d", since / 1000, since % 1000),
                "-s", "$TAG:D", "AndroidRuntime:E").redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val m = LINE.find(raw) ?: continue
                    val at = (m.groupValues[1].toDouble() * 1000).toLong()
                    if (at <= since) continue
                    last = maxOf(last, at)
                    val text = m.groupValues[2]
                    if (text.startsWith("ai: ") || text.startsWith("email: ") || text.startsWith("tester: ") || text.startsWith("brief: ")) continue
                    if (!INTERESTING.containsMatchIn(text) || kept >= 40) continue
                    log()?.append(TesterLog.line(at, TesterRules.safeLine(text, names)))
                    kept++
                }
            }
            proc.waitFor()
        }.onFailure { logcat("tester: logcat not read (${it.javaClass.simpleName})") }
        p.edit { putLong(LOG_SINCE, maxOf(last + 1, since)) }
        if (kept > 0) logcat("tester: $kept log lines kept")
    }

    /** "  1729512345.123  1234  1234 D SharpEars: text" -> the time and the text. */
    private val LINE = Regex("^\\s*(\\d+\\.\\d+)\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]+:\\s?(.*)$")

    /** The tester log's lines from [since] to [until] (off the main thread). */
    internal fun read(since: Long, until: Long): List<BriefEvent> = log()?.read(since, until).orEmpty()

    internal fun prefsOf(context: Context) = prefs(context)

    /** Tests only: forget the process state. */
    internal fun resetForTest() { route = null; lastRoute = null; screen = null; before = null; kind = null; logFile = null; app = null }
}
