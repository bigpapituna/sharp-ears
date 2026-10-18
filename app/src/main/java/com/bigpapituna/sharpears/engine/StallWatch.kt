package com.bigpapituna.sharpears.engine

/**
 * Watches wait mode for stuck moments: the same notes waited on for [thresholdMs] or more while the mic listens.
 * Each one is a likely note-detection miss, worth keeping (with its audio) for review. Pure bookkeeping; the player
 * feeds it, the mic saves what it returns.
 */
class StallWatch(private val thresholdMs: Long = 5_000) {
    /** Who is playing, as a kind only ("kid" or "adult", never a name), for the report header; null leaves it out. */
    var profile: String? = null
    private var startMs = 0L
    private var context = ""
    private var expected = emptyList<String>()
    private val heard = ArrayList<String>()
    var active = false; private set

    /** Waiting began at [nowMs] for [expected] notes; [context] describes where (song, position, mode). */
    fun start(nowMs: Long, expected: List<String>, context: String) {
        active = true; startMs = nowMs; this.expected = expected; this.context = context; heard.clear()
    }

    /** Something the mic decided during the wait ("C3 rise -24.8 dB", "wrong note"). */
    fun heard(nowMs: Long, what: String) {
        if (active && heard.size < 100) heard += "+%.2f s  %s".format((nowMs - startMs) / 1000.0, what)
    }

    /** How long the current wait has lasted at [nowMs] (0 when not waiting). */
    fun waitedMs(nowMs: Long): Long = if (active) nowMs - startMs else 0L

    /**
     * The wait ended at [nowMs] ([outcome]: "played", "left", ...). Returns the report when it was a stuck moment,
     * with how long ago the wait began, so the caller can cut the audio from there.
     */
    fun end(nowMs: Long, outcome: String): Stall? {
        if (!active) return null
        active = false
        val waited = nowMs - startMs
        if (waited < thresholdMs) return null
        val text = buildString {
            append("# sharp ears stuck moment\n")
            profile?.let { append("profile=").append(it).append('\n') }
            append(context).append('\n')
            append("expected=").append(expected.joinToString(",")).append('\n')
            append("waited_ms=").append(waited).append('\n')
            append("outcome=").append(outcome).append('\n')
            append("heard during the wait (time since it began):\n")
            if (heard.isEmpty()) append("  nothing\n") else heard.forEach { append("  ").append(it).append('\n') }
        }
        return Stall(waited, text)
    }

    data class Stall(val waitedMs: Long, val report: String)

    companion object {
        /** The profile kind as reports carry it: "kid" or "adult". Never the name. */
        fun profileKind(kid: Boolean) = if (kid) "kid" else "adult"

        /**
         * [report] with a "profile=kid|adult" line under its "# sharp ears ..." header (or first, with no header),
         * unless it has one already. Every report the mic saves goes through this (stuck moments, flags, answer keys).
         */
        fun tagProfile(report: String, kind: String): String {
            if (Regex("(?m)^profile=").containsMatchIn(report)) return report
            val lines = report.lines()
            val at = lines.indexOfFirst { it.startsWith("# sharp ears") }
            return if (at < 0) "profile=$kind\n$report"
            else (lines.take(at + 1) + "profile=$kind" + lines.drop(at + 1)).joinToString("\n")
        }
    }
}
