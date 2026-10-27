package com.bigpapituna.sharpears.analytics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.bigpapituna.sharpears.engine.tester.AppEvents
import com.bigpapituna.sharpears.engine.tester.ErrorLimiter
import com.bigpapituna.sharpears.engine.tester.ErrorReport
import com.bigpapituna.sharpears.engine.tester.ErrorScrub
import com.bigpapituna.sharpears.engine.tester.ExitRules
import java.time.LocalDate

/** The debug route's test exception (`--es screen errortest`): every report of it carries is_error_test=true. */
class ErrorTestException(message: String) : RuntimeException(message)

/** An ANR of an earlier run, read at this start (ApplicationExitInfo); its stack is the main thread's from Android's trace. */
class ApplicationNotResponding(message: String) : RuntimeException(message)

/**
 * 2.15, error tracking (CLAUDE.md "2.15 posthog"; the rules in engine/tester/ErrorRules.kt). Three ways in, all only while
 * Help Roy test is on, on a build with the PostHog key (no SDK, no report: the keyless build has no PostHog class at all):
 *
 * - [report]: a handled exception at one of the app's risk points (the voice, the natural voice, the AI and email
 *   transports, a refused MIDI file, a pup clip that did not decode, an earlier run's ANR). Scrubbed, limited (one per place,
 *   class and message in ten minutes, 30 a day, kept in tester.xml across starts), logged `errors: reported <where>`.
 * - Uncaught exceptions and native crashes: the SDK catches them itself (TesterSdk's errorTrackingConfig).
 * - [beforeSend]: every event passes it last. It stamps the test marks, scrubs the messages of every exception (the SDK's own
 *   too), applies the limit to the ones the SDK caught, marks the debug route's test exception and logs a key-free line.
 *
 * Log lines say "errors: ..." with the place and the exception's class, never the message.
 */
object Errors {
    /** In tester.xml (never backed up): the limiter's state and the newest past ANR already looked at. */
    private const val LIMIT = "errLimit"
    private const val EXIT_MARK = "exitMark"
    const val EXCEPTION = "\$exception"

    @Volatile private var limiter: ErrorLimiter? = null

    private fun app(): Context? = Tester.appContext()

    private fun limiter(): ErrorLimiter = limiter ?: synchronized(this) {
        limiter ?: ErrorLimiter.decode(app()?.let { runCatching { Tester.prefsOf(it).getString(LIMIT, null) }.getOrNull() }).also { limiter = it }
    }

    /** Written at once (commit): a crash may follow, and a crash loop must not reset the day's count. */
    private fun save() { app()?.let { a -> runCatching { Tester.prefsOf(a).edit(commit = true) { putString(LIMIT, limiter().encode()) } } } }

    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Tests only: a new process (the limit is read back from tester.xml). */
    internal fun resetForTest() { limiter = null }

    /**
     * A handled [throwable] at [where] (a short place name of the app's own, "voice_engine"), with [extra] (ErrorReport.props
     * keeps only what may go). Never throws; never sends a name, a title, a path or the raw message.
     */
    fun report(where: String, throwable: Throwable, extra: Map<String, Any?> = emptyMap()) {
        runCatching { send(where, throwable, extra) }.onFailure { Tester.logcat("errors: a report failed (${it.javaClass.simpleName})") }
    }

    private fun send(where: String, t: Throwable, extra: Map<String, Any?>) {
        val w = ErrorScrub.where(where)
        val cls = ErrorScrub.className(t.javaClass.name)
        Tester.diaryLine("error at $w: ${t.javaClass.simpleName}")
        if (!Tester.capturingNow) { Tester.logcat("errors: $w not sent (${Tester.notSentWhy()})"); return }
        val message = ErrorScrub.message(t.message, Tester.namesNow())
        val allowed = limiter().allow(ErrorLimiter.key(w, cls, message), System.currentTimeMillis(), today())
        save()
        if (!allowed) { Tester.logcat("errors: $w not sent (limited)"); return }
        TesterSdk.captureException(t, ErrorReport.props(w, extra, test = cls == ErrorReport.TEST_CLASS))
        Tester.logcat("errors: reported $w (${t.javaClass.simpleName})")
    }

    /**
     * The door's last look at every event before it leaves (TesterSdk's beforeSend). Every event gets the test marks it lacks
     * (`is_emulator`, `build_flavor`, `app_version`). A `$exception` gets each message
     * scrubbed (ErrorScrub.message), `is_error_test` for the debug route's exception, and, when the SDK caught it itself (no
     * `where`: an uncaught exception or a native crash), `where` and the limit. False drops the event. Never throws.
     */
    fun beforeSend(event: String, props: MutableMap<String, Any>): Boolean = runCatching {
        Tester.marks().forEach { (k, v) -> if (props[k] == null) props[k] = v }
        if (event != EXCEPTION) return@runCatching true
        val names = Tester.namesNow()
        var first: Map<*, *>? = null
        (props["\$exception_list"] as? List<*>)?.let { list ->
            props["\$exception_list"] = list.map { item ->
                val m = item as? Map<*, *> ?: return@map item
                if (first == null) first = m
                LinkedHashMap<Any?, Any?>(m).apply { (m["value"] as? String)?.let { put("value", ErrorScrub.message(it, names)) } }
            }
        }
        val type = first?.get("type") as? String
        val test = type != null && (type == ErrorReport.TEST_CLASS || type.endsWith(".ErrorTestException") || type == "ErrorTestException")
        if (test) props[ErrorReport.IS_ERROR_TEST] = true
        val fatal = props["\$exception_level"] == "fatal"
        if (props[ErrorReport.WHERE] == null) {
            val mechanism = ((first?.get("mechanism") as? Map<*, *>)?.get("type") as? String).orEmpty().lowercase()
            val where = when { "native" in mechanism || "signal" in mechanism || "tombstone" in mechanism -> "native_crash"; fatal -> "uncaught"; else -> "auto" }
            props[ErrorReport.WHERE] = where
            val message = ErrorScrub.message(first?.get("value") as? String, names)
            val allowed = limiter().allow(ErrorLimiter.key(where, ErrorScrub.className(type), message), System.currentTimeMillis(), today())
            save()
            if (!allowed) { Tester.logcat("errors: $where not sent (limited)"); return@runCatching false }
            Tester.logcat("errors: reported $where (${type?.substringAfterLast('.') ?: "?"}, fatal=$fatal, is_error_test=$test, is_emulator=${props[AppEvents.IS_EMULATOR]})")
        } else Tester.logcat("errors: sending ${props[ErrorReport.WHERE]} (fatal=$fatal, is_error_test=$test, is_emulator=${props[AppEvents.IS_EMULATOR]})")
        true
    }.getOrElse { Tester.logcat("errors: beforeSend failed (${it.javaClass.simpleName})"); true }

    /**
     * The ANRs of earlier runs not reported yet (Android 11 and up keeps why a process ended): each as an
     * [ApplicationNotResponding] at "anr" with the main thread's stack (ExitRules). Off the main thread; once per start.
     */
    fun reportPastExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val a = context.applicationContext
        Thread({ runCatching { pastExits(a) }.onFailure { Tester.logcat("errors: past exits not read (${it.javaClass.simpleName})") } }, "errors-exits").start()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun pastExits(a: Context) {
        val am = a.getSystemService(ActivityManager::class.java) ?: return
        val anrs = am.getHistoricalProcessExitReasons(null, 0, 16).filter { it.reason == ApplicationExitInfo.REASON_ANR }
        val p = Tester.prefsOf(a)
        val mark = if (p.contains(EXIT_MARK)) p.getLong(EXIT_MARK, 0L) else null
        val (fresh, next) = ExitRules.fresh(anrs.map { it.timestamp }, mark, System.currentTimeMillis())
        p.edit(commit = true) { putLong(EXIT_MARK, next) }
        for (at in fresh) {
            val info = anrs.firstOrNull { it.timestamp == at } ?: continue
            val trace = runCatching { info.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
            val e = ApplicationNotResponding(info.description ?: "the app did not respond")
            e.stackTrace = ExitRules.mainFrames(trace).map { StackTraceElement(it.className, it.method, it.file, it.line) }.toTypedArray()
            report("anr", e, mapOf("reason" to "anr", "importance" to info.importance))
        }
        if (anrs.isNotEmpty()) Tester.logcat("errors: past exits read, ${anrs.size} ANR(s) kept by Android, ${fresh.size} new")
    }
}

/**
 * 2.15, the debug route `--es screen errortest` (MainActivity, through engine/DebugRoutes.kt; a debug build only): proves
 * error tracking end to end. One handled [ErrorTestException] through [Errors.report] at once ("errors: reported error_test"),
 * then an uncaught one on the main thread four seconds later, which closes the app: the SDK keeps that report and sends it at
 * the next start ("errors: reported uncaught"). Both carry is_error_test=true, so Roy can find and delete them in PostHog.
 * Rate limited like any report: a second run within ten minutes sends only the crash.
 */
object ErrorTest {
    fun run() {
        Tester.logcat("errors: test route, a handled test exception now and an uncaught one in 4 s")
        Errors.report("error_test", ErrorTestException("a handled test exception from the debug route"))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Tester.logcat("errors: test route, throwing the uncaught test exception")
            throw ErrorTestException("an uncaught test exception from the debug route")
        }, 4_000)
    }
}
