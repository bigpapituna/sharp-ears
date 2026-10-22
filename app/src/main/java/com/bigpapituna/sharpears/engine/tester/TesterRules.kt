package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.brief.BriefEvent

/*
 * Tester analytics (CLAUDE.md "Tester analytics"): the rules, Android-free and tested on the JVM. analytics/Tester.kt on the
 * phone asks them; the PostHog SDK (only on a build with POSTHOG_KEY) and the tester diary act on what they say.
 *
 * Roy's terms (2026-10-21): one phone-wide switch, "Help Roy test", off by default, behind the parent gate, that Roy turns on
 * when he hands a phone to a tester (he asks the tester, and a child's parent, himself); when it is on every profile on the
 * phone is recorded, kids too, with all text masked, no sound ever, the profile's kind and never its name. The diary is its
 * own switch and goes only to Roy's address. The feature is removed before any public release.
 */

/** One analytics event: its [name] and properties (ids, numbers and words of the app's own; never a name or an address). */
data class TesterEvent(val name: String, val props: Map<String, Any> = emptyMap())

object TesterRules {
    /** A stage counts as stuck from this many tries, or when it was let go. */
    const val STUCK_TRIES = 3

    /**
     * The SDK may be set up: the build carries the SDK and a project key, and the phone's switch is on. Anything else
     * creates nothing (no setup, no thread, no file, no network).
     */
    fun sdkMayStart(sdkCompiled: Boolean, hasKey: Boolean, switchOn: Boolean): Boolean = sdkCompiled && hasKey && switchOn

    /** "Help Roy test" shows on a build with a key, and on Roy's debug builds without one (it then says nothing is sent). */
    fun rowShown(hasKey: Boolean, debuggable: Boolean): Boolean = hasKey || debuggable

    /** "Email Roy a daily diary" shows only on a build with the diary's address. */
    fun diaryShown(diaryTo: String): Boolean = diaryTo.isNotBlank()

    /** What happens on screen is written to the tester log while either switch is on (the diary reads it; nothing else does). */
    fun records(switchOn: Boolean, diaryOn: Boolean, diaryTo: String): Boolean = switchOn || (diaryOn && diaryShown(diaryTo))

    /** An imported song's id is made from its file name, which may hold someone's name: it goes out as "user-song". */
    fun safeId(id: String?): String? = id?.let { if (it.startsWith("user-")) "user-song" else it }

    /**
     * The screen a route names (MainActivity.routeOf: "home:LEARN/lesson:2:find-c"), as one event's name: its last part, a
     * lesson without its step ("lesson:find-c"), an imported song as "song:user-song", a pop song's page without its key,
     * pattern and tempo ("popsong:let-it-be"). Never a name: routes hold only ids.
     */
    fun screenOf(route: String): String {
        val last = route.substringAfterLast('/')
        val parts = last.split(':')
        return when (parts[0]) {
            "lesson" -> "lesson:" + (parts.getOrNull(2) ?: "?")
            "song" -> "song:" + safeId(parts.getOrNull(1) ?: "?")
            "popsong" -> "popsong:" + (parts.getOrNull(1) ?: "?")
            else -> last
        }
    }

    /** The lesson a route is on (its last part), or null. */
    fun lessonOf(route: String): String? = route.substringAfterLast('/').split(':').takeIf { it[0] == "lesson" }?.getOrNull(2)

    /** The route with imported songs' ids replaced, as the screen event's "route" property. */
    fun safeRoute(route: String): String = route.split('/').joinToString("/") { p ->
        if (p.startsWith("song:")) "song:" + safeId(p.removePrefix("song:")) else p
    }

    /** "kid" or "adult": the only thing about a profile that goes out (never its name, never its id). */
    fun profileKind(isKid: Boolean): String = if (isKid) "kid" else "adult"

    /**
     * A screen change from [before] to [after] (routes; [before] null at start): a lesson that was not open before has
     * started. Returns its event, or null.
     */
    fun lessonStarted(before: String?, after: String): TesterEvent? {
        val now = lessonOf(after) ?: return null
        if (before != null && lessonOf(before) == now) return null
        return TesterEvent("lesson_started", mapOf("lesson" to now))
    }

    /**
     * An ai: or email: log line (data/BriefAi.kt, engine/brief/email/EmailSender.kt; written to carry no key, address, name or
     * body) as a result: ("ai" or "email", the outcome in one word), or null for lines that are not a result.
     */
    fun resultOf(line: String): Pair<String, String>? {
        if (line.startsWith("ai: ")) {
            val t = line.removePrefix("ai: ")
            return "ai" to when {
                t.startsWith("reply in ") -> "reply"
                t.startsWith("no reply within") -> "timeout"
                " from the cache" in t -> "cache"
                " not asked: " in t -> "not_asked"
                " reply not usable" in t -> "unusable"
                t.startsWith("the transport failed") -> "failed"
                Regex("^[A-Z_]+( after |: )").containsMatchIn(t) -> t.substringBefore(' ').trimEnd(':')
                else -> return null
            }
        }
        if (line.startsWith("email: ")) {
            val t = line.removePrefix("email: ")
            return "email" to when {
                Regex(" sent \\(").containsMatchIn(t) -> "sent"
                " not sent: " in t -> t.substringAfter(" not sent: ").substringBefore(' ').trimEnd(':', ',').ifBlank { "refused" }
                " failed: " in t -> t.substringAfter(" failed: ").substringBefore(':').trim()
                " failed (" in t -> "failed"
                else -> return null
            }
        }
        return null
    }

    /** [line] safe for the diary: one line, no address, at most [max] characters. */
    fun safeLine(line: String, names: List<String> = emptyList(), max: Int = 160): String {
        var t = line.replace(Regex("\\s+"), " ").trim()
        t = ADDRESS.replace(t, "[address]")
        for (n in names.filter { it.length >= 2 }) t = t.replace(Regex("\\b" + Regex.escape(n) + "\\b", RegexOption.IGNORE_CASE), "[name]")
        return if (t.length > max) t.take(max - 3) + "..." else t
    }

    private val ADDRESS = Regex("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*")
}

/**
 * A brief event (the parent brief's log, engine/brief/Events.kt) as analytics events. Keeps each open lesson's tries and
 * let-gos so its finish carries them. One per process; not thread-safe (Briefs logs from the main thread).
 */
class TesterTally {
    private class Open(var tries: Int = 0, var letGo: Int = 0)
    private val open = HashMap<String, Open>()

    fun eventsFor(e: BriefEvent): List<TesterEvent> = when (e) {
        is BriefEvent.LessonStep -> {
            val o = open.getOrPut(e.lessonId) { Open() }
            o.tries += e.tries; if (e.letGo) o.letGo++
            val step = mapOf("lesson" to e.lessonId, "step" to e.stepIndex, "kind" to e.kind, "tries" to e.tries, "let_go" to e.letGo)
            listOfNotNull(
                TesterEvent("stage_stuck", step).takeIf { e.letGo || e.tries >= TesterRules.STUCK_TRIES },
                TesterEvent("hint_step", step + mapOf("hints" to e.hintsUsed.size, "deepest" to (e.hintsUsed.maxOrNull() ?: 0))).takeIf { e.hintsUsed.isNotEmpty() },
            )
        }
        is BriefEvent.LessonDone -> {
            val o = open.remove(e.lessonId) ?: Open()
            listOf(TesterEvent("lesson_finished", mapOf("lesson" to e.lessonId, "stars" to e.stars, "tries" to o.tries, "let_go" to o.letGo,
                "seconds" to e.durationMs / 1000)))
        }
        is BriefEvent.SongRun -> listOf(TesterEvent("song_run", buildMap {
            put("song", TesterRules.safeId(e.songId)!!); put("mode", e.mode.name.lowercase()); put("stars", e.stars)
            e.accuracy?.let { put("accuracy", it) }; put("duet", e.duet)
        }))
        is BriefEvent.ToyOpened -> listOf(TesterEvent("toy_opened", mapOf("toy" to e.toy.wire, "seconds" to e.durationMs / 1000)))
        else -> emptyList()
    }
}
