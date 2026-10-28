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
import com.bigpapituna.sharpears.engine.tester.AppEvents
import com.bigpapituna.sharpears.engine.tester.BuildMarks
import com.bigpapituna.sharpears.engine.tester.TesterEvent
import com.bigpapituna.sharpears.engine.tester.TesterLog
import com.bigpapituna.sharpears.engine.tester.TesterRules
import com.bigpapituna.sharpears.engine.tester.TesterTally
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Tester analytics on the phone (CLAUDE.md "Tester analytics"; the rules in engine/tester). Two phone-wide switches behind the
 * parent gate, kept in [FILE] (tester.xml, left out of Android's backup with the tester log). "Help Roy test" is on by default
 * since 2.13 where its row shows (TesterRules.switchOn: an unset switch becomes on, once, saved, with a one-time notice for a
 * grown-up, [noticePending]); a choice made on the phone stays. The diary is off by default.
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
    /** 2.13: the default turned the switch on and no grown-up has tapped OK on the notice yet. */
    private const val NOTICE = "defaultNotice"
    /** 2.13: the default turned the switch on and `tester_default_on` has not gone out yet (whichever process applied it). */
    internal const val DEFAULT_EVENT = "defaultEvent"
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
    /** 2.13: the notice that the default turned Help Roy test on waits for a grown-up (TesterDefaultNotice). */
    val noticePending = mutableStateOf(false)

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

    /** [rowShown], or false where the context cannot say (a test's folder context). */
    private fun rowShownOrNot(context: Context) = runCatching { rowShown(context) }.getOrDefault(false)

    /**
     * Reads the switches (once per process; the email job's alarm may run in a process with no screen). Starts nothing. 2.13: an
     * unset Help Roy test where the row shows ([shown]) becomes on and is saved, with the notice pending (`tester: default on`);
     * a saved choice, on or off, is kept (TesterRules.switchOn).
     */
    fun load(context: Context, shown: Boolean = rowShownOrNot(context)) {
        if (app != null) return
        val a = context.applicationContext
        val p = prefs(a)
        val stored = if (p.contains(ON)) p.getBoolean(ON, false) else null
        if (TesterRules.defaultTurnsOn(stored, shown)) {
            p.edit(commit = true) { putBoolean(ON, true); putBoolean(NOTICE, true); putBoolean(DEFAULT_EVENT, true) }
            logcat("tester: default on")
        }
        switchOn.value = TesterRules.switchOn(stored, shown)
        diaryOn.value = p.getBoolean(DIARY, false)
        noticePending.value = p.getBoolean(NOTICE, false)
        app = a
    }

    /** Called at the app's start: reads the switches and sets PostHog up only when it may start. */
    fun init(context: Context, shown: Boolean = rowShownOrNot(context)) {
        val a = context.applicationContext
        load(a, shown)
        logcat("tester: help Roy test ${if (switchOn.value) "on" else "off"}, diary ${if (diaryOn.value) "on" else "off"}, " +
            "dashboard ${if (hasKey && TesterSdk.present) "built in" else "not in this build"}")
        startIfAllowed(a)
        // 2.13: the default turned it on (here, or in a background process that loaded first): the event, once, when the
        // dashboard is up; on a build without it the mark waits (nothing is ever sent there).
        sendDefaultEvent(prefs(a))
        capture(AppEvents.appOpen(cold = true))
    }

    /** The pending `tester_default_on`, sent and cleared only while the dashboard records. */
    internal fun sendDefaultEvent(p: SharedPreferences, capturingNow: Boolean = capturing) {
        if (!p.getBoolean(DEFAULT_EVENT, false) || !capturingNow) return
        capture(TesterEvent(TesterRules.DEFAULT_ON_EVENT), force = true)
        p.edit { putBoolean(DEFAULT_EVENT, false) }
        logcat("tester: default on, event sent")
    }

    /** 2.13: a grown-up tapped OK on the notice: it never shows again on this phone. */
    fun noticeSeen(context: Context) {
        prefs(context).edit { putBoolean(NOTICE, false) }
        noticePending.value = false
        logcat("tester: default on, notice seen")
    }

    private fun startIfAllowed(a: Context) {
        if (!TesterRules.sdkMayStart(TesterSdk.present, hasKey, switchOn.value)) return
        val ok = runCatching { TesterSdk.start(a, BuildConfig.POSTHOG_KEY, BuildConfig.POSTHOG_HOST, deviceId(a), Errors::beforeSend) }
            .onFailure { logcat("tester: the dashboard did not start (${it.javaClass.simpleName})") }.getOrDefault(false)
        if (ok) {
            logcat("tester: dashboard recording")
            // 2.15: the test marks (is_emulator, build_flavor, app_version), who is playing and the look, with every event.
            runCatching { marks().forEach { (k, v) -> TesterSdk.register(k, v) } }
            kind?.let { (k, _) -> TesterSdk.register(AppEvents.PROFILE_KIND, k) }
            look.forEach { (k, v) -> TesterSdk.register(k, v) }
            route?.let { r -> TesterSdk.screen(TesterRules.screenOf(r), mapOf("route" to TesterRules.safeRoute(r))) }
            Errors.reportPastExits(a)
            Errors.installCrashHandler()
        }
    }

    /**
     * 2.15, what marks test traffic, with every event (and stamped on any event that went before they were registered,
     * Errors.beforeSend): `is_emulator` (true or false, never missing), `build_flavor` (BuildMarks.buildFlavor) and `app_version`.
     */
    internal fun marks(): Map<String, Any> = marks ?: mapOf(
        AppEvents.IS_EMULATOR to runCatching {
            BuildMarks.isEmulator(android.os.Build.FINGERPRINT, android.os.Build.MODEL, android.os.Build.PRODUCT, android.os.Build.HARDWARE, android.os.Build.MANUFACTURER)
        }.getOrDefault(false),
        AppEvents.BUILD_FLAVOR to BuildMarks.buildFlavor(BuildConfig.DEBUG, BuildConfig.BUILD_TYPE, BuildConfig.DEBUG_ROUTE_KEY_SHA256.isNotBlank()),
        AppEvents.APP_VERSION to BuildConfig.VERSION_NAME,
    ).also { marks = it }
    @Volatile private var marks: Map<String, Any>? = null

    /** The random id this phone goes by in the dashboard and the diary, made once (never a name, an email or a hardware id). */
    fun deviceId(context: Context): String {
        val p = prefs(context)
        p.getString(DEVICE, null)?.let { return it }
        return UUID.randomUUID().toString().also { id -> p.edit(commit = true) { putString(DEVICE, id) } }
    }

    fun setOn(context: Context, on: Boolean) {
        val a = context.applicationContext
        val was = recording
        // A choice made here also answers the notice: no need to tell them what they just chose.
        prefs(a).edit { putBoolean(ON, on); putBoolean(NOTICE, false) }
        noticePending.value = false
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
    /** 2.15: the dashboard records now (Errors and AppFlags ask). */
    internal val capturingNow: Boolean get() = capturing

    /** Why nothing goes to the dashboard now (Errors' log line). */
    internal fun notSentWhy(): String = when {
        !hasKey || !TesterSdk.present -> "no dashboard in this build"
        !switchOn.value -> "help Roy test off"
        else -> "the dashboard is not running"
    }

    /** 2.15: an event's own `profile_kind` agrees with who is playing (a big kid's retention and cheer events say big_kid). */
    private fun capture(e: TesterEvent, force: Boolean = false) {
        if (!capturing && !force) return
        val own = e.props[AppEvents.PROFILE_KIND]
        val props = if (own != null) e.props + (AppEvents.PROFILE_KIND to (AppEvents.refineKind(own, kind?.first) ?: own)) else e.props
        runCatching { TesterSdk.capture(e.name, props) }
    }

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
    fun back() { lastRoute?.let { if (route == null) { capture(AppEvents.appOpen(cold = false)); screen(it) } } }

    /** Who is playing changed: their kind goes with every later event; the diary gets the kind and the profile id. */
    fun profile(isKid: Boolean, profileId: String) {
        // 2.15: a big kid goes out as big_kid (AppEvents.profileKind); the roster is read here, never sent.
        val roster = runCatching { app?.let { a -> Profiles(a).all } }.getOrNull()
        names = roster?.map { it.name }
        val big = isKid && roster?.firstOrNull { it.id == profileId }?.bigKid == true
        val k = AppEvents.profileKind(isKid, big) to profileId
        if (k == kind) return
        kind = k
        AppFlags.forget()
        write(TesterLog.kind(now(), k.first, profileId))
        if (capturing) { runCatching { TesterSdk.register("profile_kind", k.first) }; capture(TesterEvent("profile_kind", mapOf("kind" to k.first))) }
    }

    // ---- 2.15: the look, the app's own events, flags and the diary's error lines ----

    /** The layout and the Plain look on show (the super properties `layout` and `plain_look`). */
    private val look = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** The layout on show changed (MainActivity), with the Plain look where the build has it (null: not in this build). */
    fun look(layout: String, plainLook: Boolean? = null) {
        val next = buildMap<String, Any> { put(AppEvents.LAYOUT, layout.lowercase()); plainLook?.let { put(AppEvents.PLAIN_LOOK, it) } }
        if (next.all { (k, v) -> look[k] == v }) return
        look.putAll(next)
        if (capturing) next.forEach { (k, v) -> runCatching { TesterSdk.register(k, v) } }
    }

    /**
     * 2.16: a super property the build's extra adds (extras/Extras.kt; analytics/EVENTS.md), with every later event like the
     * look's; again only when its value changes. A build without the extra adds none.
     */
    fun extra(key: String, value: Any) {
        if (look[key] == value) return
        look[key] = value
        if (capturing) runCatching { TesterSdk.register(key, value) }
    }

    /** 2.17: how the person open plays now (the super property `input_mode`: mic, midi or touch; engine/nav/FirstRun.inputOf). */
    fun input(mode: String) = extra(AppEvents.INPUT_MODE, mode)

    /** One of the app's own 2.15 events (engine/tester/AppEvents.kt; analytics/Events.kt sends them). */
    fun track(e: TesterEvent) = capture(e)

    /** A feature flag's value from the dashboard (AppFlags), or null while it does not record. */
    internal fun flag(key: String): Any? = if (capturing) runCatching { TesterSdk.flag(key) }.getOrNull() else null

    /** The family's names (to scrub from error messages, never sent), read at the last profile change. */
    @Volatile private var names: List<String>? = null
    internal fun namesNow(): List<String> = names ?: names().also { names = it }

    /** An error line for the diary (Errors), while a switch records. */
    internal fun diaryLine(text: String) = write(TesterLog.line(now(), TesterRules.safeLine(text, namesNow())))

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

    /** 2.13, the cheer pop (engine/tester/CheerPopEvent.kt): `cheer_pop` with the trigger's family and the audience, nothing else. */
    fun cheerPop(trigger: com.bigpapituna.sharpears.engine.lessons.CheerTrigger, kid: Boolean) =
        capture(com.bigpapituna.sharpears.engine.tester.CheerPopEvent.of(trigger, kid))

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

    /** The application context once [load] ran, else null (Errors). */
    internal fun appContext(): Context? = app

    /** Tests only: forget the process state. */
    internal fun resetForTest() { route = null; lastRoute = null; screen = null; before = null; kind = null; logFile = null; app = null
        noticePending.value = false; names = null; look.clear() }
}
