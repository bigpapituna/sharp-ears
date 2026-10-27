package com.bigpapituna.sharpears.engine.tester

/*
 * 2.15, error tracking (CLAUDE.md "2.15 posthog", analytics/EVENTS.md). What an error report may carry and how often one
 * goes, Android-free and tested on the JVM (ErrorRulesTest). analytics/Errors.kt on the phone asks these rules; a report
 * leaves the phone only while Help Roy test is on, on a build with the PostHog key (the same switch as the tester events).
 *
 * Never in a report: a name, an email address, a postal address, a child's name, a song's title or file name, a key, a path,
 * or any sound. A message is scrubbed of all of those and cut to [ErrorScrub.MAX] characters.
 */

/** Cleans an exception's message (and the few words that go with a report) before it leaves the phone. */
object ErrorScrub {
    /** The longest message a report carries. */
    const val MAX = 200

    private val ADDRESS = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+")
    private val BEARER = Regex("(?i)\\bbearer\\s+\\S+")
    private val KEY = Regex("(?i)\\b(?:phc|phx|sk-or|sk-ant|sk|xkeysib|pk|rk|key)[-_][A-Za-z0-9_-]{6,}")
    private val URI = Regex("(?i)\\b(?:content|file|https?|sharpears|intent)://\\S+")
    private val PATH = Regex("(?:^|(?<=[\\s(\\[=:'\"]))/(?:[^\\s/:]+/)+[^\\s/:]*")
    private val FILE = Regex("(?i)[^\\s/\\\\:'\"()\\[\\]]+\\.(?:mid|midi|kar|wav|mp3|m4a|aac|ogg|opus|flac|zip|json|pdf|png|jpe?g|webp|txt|xml|log|onnx|bin)\\b")
    private val QUOTED = Regex("\"[^\"]*\"|“[^”]*”|‘[^’]*’|'[^'\\s][^']*'")
    private val DIGITS = Regex("\\d[\\d ().-]{6,}\\d")
    private val TOKEN = Regex("[A-Za-z0-9_+=-]{24,}")

    /**
     * [raw] safe to send: no address, no key or bearer token, no long opaque token, no link, path or file name, nothing quoted
     * (a title or what someone typed is usually quoted), no phone-number-like run of digits, none of [names] (the family's
     * names, whole words, any case), one line, at most [max] characters. Null or blank gives "".
     */
    fun message(raw: String?, names: List<String> = emptyList(), max: Int = MAX): String {
        var t = raw?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        if (t.isEmpty()) return ""
        t = ADDRESS.replace(t, "[address]")
        t = BEARER.replace(t, "[key]")
        t = URI.replace(t, "[link]")
        t = PATH.replace(t, "[path]")
        t = FILE.replace(t, "[file]")
        t = QUOTED.replace(t, "[text]")
        t = KEY.replace(t, "[key]")
        t = TOKEN.replace(t) { m -> if (m.value.any(Char::isDigit) && m.value.any(Char::isLetter)) "[key]" else m.value }
        t = DIGITS.replace(t, "[number]")
        for (n in names.map { it.trim() }.filter { it.length >= 2 }.distinct())
            t = t.replace(Regex("(?i)(?<![\\p{L}\\p{N}])" + Regex.escape(n) + "(?![\\p{L}\\p{N}])"), "[name]")
        return if (t.length > max) t.take(max - 3).trimEnd() + "..." else t
    }

    /** A place name of the app's own ("ai_transport"): lower case, letters, digits and underscores, at most 40; never blank. */
    fun where(raw: String): String = raw.lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').take(40).ifEmpty { "unknown" }

    /** An exception's class as sent: its full name, kept only when it looks like a class name (anything else "Throwable"). */
    fun className(raw: String?): String =
        raw?.takeIf { it.length <= 120 && Regex("[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*").matches(it) } ?: "Throwable"
}

/**
 * How often a report may go (Roy's rule): one per place, exception class and message within ten minutes ([PER_KEY_MS]),
 * and at most [PER_DAY] a day in all. Kept across process starts (a crash loop restarts the process): [encode] and [decode]
 * go to tester.xml. Thread-safe: the voice and the transports report from worker threads.
 */
class ErrorLimiter(private val perKeyMs: Long = PER_KEY_MS, private val perDay: Int = PER_DAY) {
    private var day: Long = Long.MIN_VALUE
    private var count = 0
    private val last = LinkedHashMap<String, Long>()

    /** True (and counted) when a report under [key] may go at [now], on the phone's local day [today] (an epoch day). */
    @Synchronized fun allow(key: String, now: Long, today: Long): Boolean {
        if (today != day) { day = today; count = 0 }
        last.entries.removeAll { now - it.value >= perKeyMs || it.value > now }
        if (count >= perDay || key in last) return false
        last[key] = now
        count++
        return true
    }

    /** Reports allowed so far on [today]. */
    @Synchronized fun countToday(today: Long): Int = if (today == day) count else 0

    /** The state as one line: "day|count|key=ms;key=ms". */
    @Synchronized fun encode(): String = "$day|$count|" + last.entries.joinToString(";") { "${it.key}=${it.value}" }

    companion object {
        const val PER_KEY_MS = 10 * 60_000L
        const val PER_DAY = 30

        /** Back from [encode]; anything unreadable starts fresh. */
        fun decode(s: String?, perKeyMs: Long = PER_KEY_MS, perDay: Int = PER_DAY): ErrorLimiter {
            val l = ErrorLimiter(perKeyMs, perDay)
            val parts = s?.split('|', limit = 3) ?: return l
            if (parts.size != 3) return l
            l.day = parts[0].toLongOrNull() ?: return ErrorLimiter(perKeyMs, perDay)
            l.count = parts[1].toIntOrNull() ?: 0
            parts[2].split(';').filter { '=' in it }.forEach { e ->
                e.substringAfterLast('=').toLongOrNull()?.let { ms -> l.last[e.substringBeforeLast('=')] = ms }
            }
            return l
        }

        /** The limiter's key: the place, the class and a hash of the (scrubbed) message. */
        fun key(where: String, className: String, message: String): String =
            "${ErrorScrub.where(where)}:${className.replace(Regex("[^\\w.$]"), "")}:${Integer.toHexString(message.hashCode())}"
    }
}

/** The properties a handled report carries, besides PostHog's own exception fields. */
object ErrorReport {
    /** The test exception the debug route throws (`--es screen errortest`): its reports carry is_error_test=true. */
    const val TEST_CLASS = "com.bigpapituna.sharpears.analytics.ErrorTestException"
    const val WHERE = "where"
    const val HANDLED = "handled"
    const val IS_ERROR_TEST = "is_error_test"

    private val KEY = Regex("[a-z][a-z0-9_]{0,31}")

    /**
     * [where] and the [extra] that may go: keys of the app's own (lower case, never a denied word, AppEvents.denied), values
     * a boolean, a whole or finite number, an enum's name, or a short word scrubbed like a message (40 characters). Anything
     * else is dropped.
     */
    fun props(where: String, extra: Map<String, Any?> = emptyMap(), test: Boolean = false, handled: Boolean = true): Map<String, Any> = buildMap {
        for ((k, v) in extra) {
            if (!KEY.matches(k) || AppEvents.denied(k) || k == WHERE || k == HANDLED || k == IS_ERROR_TEST) continue
            when (v) {
                is Boolean -> put(k, v)
                is Int, is Long, is Short, is Byte -> put(k, (v as Number).toLong())
                is Double -> if (v.isFinite()) put(k, v)
                is Float -> if (v.isFinite()) put(k, v.toDouble())
                is Enum<*> -> put(k, v.name.lowercase())
                is String -> ErrorScrub.message(v, max = 40).takeIf { it.isNotEmpty() }?.let { put(k, it) }
                else -> {}
            }
        }
        put(WHERE, ErrorScrub.where(where))
        put(HANDLED, handled)
        if (test) put(IS_ERROR_TEST, true)
    }
}

/** What marks test traffic, so Roy can filter it out in PostHog: an emulator, and which kind of build. */
object BuildMarks {
    /**
     * True on an emulator, from the build's fingerprint, model, product, hardware and maker as Android reports them
     * (android.os.Build). The Fold7 says samsung/q7q...; the emulators say sdk_gphone..., ranchu or generic.
     */
    fun isEmulator(fingerprint: String?, model: String?, product: String?, hardware: String?, manufacturer: String?): Boolean {
        val f = fingerprint.orEmpty().lowercase()
        val m = model.orEmpty().lowercase()
        val p = product.orEmpty().lowercase()
        val h = hardware.orEmpty().lowercase()
        val maker = manufacturer.orEmpty().lowercase()
        return f.startsWith("generic") || f.startsWith("unknown") || "emulator" in f || "sdk_gphone" in f || "/sdk_" in f || "vbox" in f ||
            "google_sdk" in m || "emulator" in m || "android sdk built for" in m || "sdk_gphone" in m ||
            p.startsWith("sdk") || "sdk_gphone" in p || "emulator" in p || "simulator" in p || "vbox" in p ||
            h == "goldfish" || h == "ranchu" || "vbox" in h || "genymotion" in maker
    }

    /**
     * Which build sent it: "debug" for a debug build without the debug-route key (agents' and worktree builds), "tester" for a
     * keyed debug build (Roy's phone and the ones he hands testers), else the build type ("profile", "release").
     */
    fun buildFlavor(debug: Boolean, buildType: String, keyed: Boolean): String = when {
        !debug -> buildType.lowercase().ifBlank { "release" }
        keyed -> "tester"
        else -> "debug"
    }
}

/** A stack frame read from an ANR trace. */
data class TraceFrame(val className: String, val method: String, val file: String?, val line: Int)

/**
 * The SDK (posthog-android 3.72.5) has no ANR capture. Android keeps why the app's last processes ended
 * (ApplicationExitInfo, API 30+): at the next start analytics/Errors.kt reports the ANRs it has not reported yet, with the
 * main thread's stack from the trace Android kept.
 */
object ExitRules {
    /** At most this many past ANRs are reported at one start. */
    const val MAX_PER_START = 3

    /**
     * The exits at [timestamps] newer than [watermark] (newest first, at most [MAX_PER_START]) and the watermark to keep. With
     * no watermark yet (the first start with 2.15) nothing is reported and the newest exit, or [now], becomes the watermark.
     */
    fun fresh(timestamps: List<Long>, watermark: Long?, now: Long): Pair<List<Long>, Long> {
        if (watermark == null) return emptyList<Long>() to maxOf(now, timestamps.maxOrNull() ?: now)
        val newer = timestamps.filter { it > watermark }.sortedDescending()
        return newer.take(MAX_PER_START) to maxOf(watermark, newer.maxOrNull() ?: watermark)
    }

    private val FRAME = Regex("^\\s*at\\s+([\\w$.<>]+)\\.([\\w$<>-]+)\\((.*)\\)\\s*$")

    /**
     * The main thread's frames in an ANR trace (the text Android writes: a `"main" prio=5 tid=1 ...` header, then `at
     * class.method(File.kt:12)` lines until a blank line), at most [max]. Empty when [trace] has no main thread.
     */
    fun mainFrames(trace: String, max: Int = 40): List<TraceFrame> {
        val lines = trace.lines()
        val start = lines.indexOfFirst { it.trimStart().startsWith("\"main\"") }
        if (start < 0) return emptyList()
        val out = ArrayList<TraceFrame>()
        for (l in lines.drop(start + 1)) {
            if (l.isBlank()) break
            val m = FRAME.find(l) ?: continue
            val (cls, method, at) = m.destructured
            val file = at.substringBefore(':').takeIf { it.isNotBlank() && it != "Native method" && it != "Unknown Source" }
            val line = when {
                at == "Native method" -> -2
                ':' in at -> at.substringAfterLast(':').toIntOrNull() ?: -1
                else -> -1
            }
            out += TraceFrame(cls, method, file, line)
            if (out.size >= max) break
        }
        return out
    }
}
