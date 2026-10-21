package com.bigpapituna.sharpears.engine.playground

/*
 * Clips for tuning (2.1.1): the stages and the Jam were tuned on synthetic audio. Roy's next session with the kids
 * should leave real audio behind: a stage keeps the moment it was stuck (sounds arriving, no key recognised for a
 * while), the Jam one stretch of play per session. Both go through MicInput.saveStall (files/stalls, the 250 MB cap,
 * the Reporter); these classes only decide when, so the rules are tested on the JVM. Reports name the stage and the
 * profile kind (kid or adult), never a name.
 */

/**
 * A stage stuck with sound arriving: [quietMs] or more since the round began or a key was last recognised, with at
 * least [minSounds] sounds heard in that stretch (a quiet kid is not a detection problem). One clip per stretch: the
 * next can only come after a key is recognised or a new round begins. [check] returns the clip's report text.
 */
class StageClipWatch(val quietMs: Long = 8_000, val minSounds: Int = 3) {
    private var since = Long.MIN_VALUE
    private val heard = ArrayList<String>()
    private var saved = false
    var expected: List<String> = emptyList(); private set

    /** A round began at [nowMs] listening for [expected] (note names), or a key was recognised: a new stretch. */
    fun reset(nowMs: Long, expected: List<String> = this.expected) { since = nowMs; heard.clear(); saved = false; this.expected = expected }

    /** Something heard at [nowMs] that was not the round's key ("noise", "named E4", "pitch 63.2"). */
    fun sound(nowMs: Long, what: String) {
        if (since == Long.MIN_VALUE || saved) return
        if (heard.size < 60) heard += "+%.2f s  %s".format(java.util.Locale.ROOT, (nowMs - since) / 1000.0, what)
    }

    /** Sounds counted in the stretch so far. */
    val sounds get() = heard.size

    /**
     * The clip to save at [nowMs], once per stretch: [Clip.fromMs] is where the stretch began, so the audio from just
     * before it to now shows the moments the stage did not recognise. [where] says which lesson, step and stage;
     * [profile] is "kid" or "adult".
     */
    fun check(nowMs: Long, where: String, profile: String): Clip? {
        if (saved || since == Long.MIN_VALUE || nowMs - since < quietMs || heard.size < minSounds) return null
        saved = true
        val text = buildString {
            append("# sharp ears stuck moment\n")
            append("profile=").append(profile).append('\n')
            append(where).append('\n')
            append("expected=").append(expected.joinToString(",")).append('\n')
            append("waited_ms=").append(nowMs - since).append('\n')
            append("outcome=stage stuck: sounds but no key recognised\n")
            append("heard during the wait (time since it began):\n")
            heard.forEach { append("  ").append(it).append('\n') }
        }
        return Clip(since, nowMs, text)
    }

    data class Clip(val fromMs: Long, val toMs: Long, val report: String)
}

/**
 * The Jam keeps one clip a session: once [minSounds] sounds have been heard, the [clipMs] that hold most of them. It
 * waits until the first counted sound is [clipMs] old (so the clip is full of play), or saves what there is when the
 * Jam closes ([closing]). Sounds the app made itself must not be counted (the caller leaves out its own whoosh).
 */
class JamClipWatch(val minSounds: Int = 10, val clipMs: Long = 20_000) {
    private val times = ArrayList<Long>()
    var saved = false; private set

    fun sound(nowMs: Long) { if (!saved) times += nowMs }

    val sounds get() = times.size

    /** Whether to save the last [clipMs] now: enough sounds, and the first of them [clipMs] old, or the Jam [closing]. */
    fun check(nowMs: Long, closing: Boolean = false): Boolean {
        if (saved || times.size < minSounds) return false
        if (!closing && nowMs - times.first() < clipMs) return false
        saved = true
        return true
    }

    /** The clip's report: [where] is the screen, [profile] "kid" or "adult". */
    fun report(nowMs: Long, where: String, profile: String): String = buildString {
        append("# sharp ears jam clip\n")
        append("profile=").append(profile).append('\n')
        append(where).append('\n')
        append("sounds=").append(times.size).append(" in the session, ").append(times.count { nowMs - it <= clipMs }).append(" in the clip\n")
    }
}
