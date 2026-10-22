package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.email.EmailSchedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The tester log's lines (files/tester/diary.log on the phone, the parent brief's EventLog format): what happened on screen
 * while "Help Roy test" or the diary is on. Written as [BriefEvent.Unknown] with names of their own ("t_..."), so the log's
 * reader and writer are the brief's and these never reach a brief (they live in their own file). Only words of the app's own,
 * ids and numbers; a note's text is the tester's own, kept to one line with addresses and the family's names taken out.
 */
object TesterLog {
    const val SCREEN = "t_screen"
    const val NOTE = "t_note"
    const val ERROR = "t_error"
    const val RESULT = "t_result"
    const val LINE = "t_log"
    const val KIND = "t_kind"
    /** The screen name of the app leaving the screen (onStop): it ends the time on the screen before. */
    const val AWAY = "away"

    fun screen(at: Long, screen: String) = BriefEvent.Unknown(at, SCREEN, mapOf("s" to screen))
    fun note(at: Long, screen: String, before: String?, text: String) =
        BriefEvent.Unknown(at, NOTE, buildMap { put("s", screen); before?.let { put("before", it) }; put("text", text) })
    fun error(at: Long, where: String) = BriefEvent.Unknown(at, ERROR, mapOf("where" to where))
    fun result(at: Long, kind: String, outcome: String, line: String) = BriefEvent.Unknown(at, RESULT, mapOf("kind" to kind, "out" to outcome, "line" to line))
    fun line(at: Long, line: String) = BriefEvent.Unknown(at, LINE, mapOf("line" to line))
    /** Who is playing changed: their kind and profile id (p1, owner), never the name. */
    fun kind(at: Long, kind: String, profileId: String) = BriefEvent.Unknown(at, KIND, mapOf("kind" to kind, "id" to profileId))
}

/** When the diary goes: [Send] now, or wait until [Wait.atMs] (null: nothing to send, no need to look again). */
sealed interface DiaryDue {
    data object Send : DiaryDue
    data class Wait(val atMs: Long?) : DiaryDue
}

object DiarySchedule {
    /**
     * The diary goes once a day, in the evening (from EmailSchedule.DAY_CUTOFF, 20:30, until the quiet hours at 21:30), with
     * everything since the last one; what is left from an earlier day goes the next morning (07:00 on). Never in the quiet
     * hours, never twice on one day, never with nothing new ([oldestNews] null: no line since the last diary).
     */
    fun decide(now: Long, zone: ZoneId, lastSent: Long?, oldestNews: Long?): DiaryDue {
        if (oldestNews == null) return DiaryDue.Wait(null)
        if (EmailSchedule.quiet(now, zone)) return DiaryDue.Wait(EmailSchedule.afterQuiet(now, zone))
        val today = EmailSchedule.date(now, zone)
        val evening = EmailSchedule.at(today, EmailSchedule.DAY_CUTOFF, zone)
        if (lastSent != null && EmailSchedule.date(lastSent, zone) == today) return DiaryDue.Wait(EmailSchedule.at(today.plusDays(1), EmailSchedule.DAY_CUTOFF, zone))
        if (now >= evening || EmailSchedule.date(oldestNews, zone) < today) return DiaryDue.Send
        return DiaryDue.Wait(evening)
    }
}

/** One person on the phone in the diary: [label] is their kind and profile id ("kid p1"), never a name. */
data class DiaryPerson(val label: String, val events: List<BriefEvent>)

/**
 * The tester diary: a plain-text email to Roy, built from the tester log and each profile's brief events since the last one.
 * Pure: the phone gathers the inputs (analytics/TesterDiary.kt).
 */
object Diary {
    /** A screen held longer than this without another line counts this long (an app killed without leaving the screen). */
    const val SCREEN_CAP_MS = 60 * 60_000L

    fun subject(deviceId: String, now: Long, zone: ZoneId): String =
        "Sharp Ears test diary ${DATE.format(Instant.ofEpochMilli(now).atZone(zone))}, device ${deviceId.take(8)}"

    fun text(deviceId: String, appVersion: String, from: Long, until: Long, zone: ZoneId, log: List<BriefEvent>, people: List<DiaryPerson>): String {
        val t = log.filterIsInstance<BriefEvent.Unknown>().filter { it.at in from until until }.sortedBy { it.at }
        fun clock(ms: Long) = CLOCK.format(Instant.ofEpochMilli(ms).atZone(zone))
        fun s(e: BriefEvent.Unknown, k: String) = e.fields[k]?.toString().orEmpty()
        val out = StringBuilder()
        fun line(text: String = "") { out.append(text).append('\n') }
        line("Sharp Ears test diary")
        line("Device $deviceId (a random id made on the phone), app $appVersion")
        line("From ${clock(from)} to ${clock(until)}")

        // Screens: time on each, from one screen line to the next (away ends it).
        val screens = t.filter { it.name == TesterLog.SCREEN }
        val time = LinkedHashMap<String, Long>(); val visits = LinkedHashMap<String, Int>()
        screens.forEachIndexed { i, e ->
            val name = s(e, "s")
            if (name == TesterLog.AWAY) return@forEachIndexed
            val end = screens.getOrNull(i + 1)?.at ?: until
            time[name] = (time[name] ?: 0) + (end - e.at).coerceIn(0, SCREEN_CAP_MS)
            visits[name] = (visits[name] ?: 0) + 1
        }
        line(); line("Screens (time on each, visits)")
        if (time.isEmpty()) line("  none")
        time.entries.sortedByDescending { it.value }.forEach { (k, v) -> line("  $k: ${duration(v)}, ${visits[k]}") }
        val kinds = t.filter { it.name == TesterLog.KIND }
        if (kinds.isNotEmpty()) line("  Who played: " + kinds.joinToString(", ") { "${clock(it.at)} ${s(it, "kind")} ${s(it, "id")}" })

        line(); line("Lessons and stages")
        var any = false
        for (p in people) for (e in p.events.filter { it.at in from until until }.sortedBy { it.at }) {
            val text = when (e) {
                is BriefEvent.LessonStep -> "${e.lessonId} step ${e.stepIndex} ${e.kind}" + (e.game?.let { " $it" } ?: "") + ": ${e.tries} tries" +
                    (if (e.letGo) ", let go" else "") + (if (e.skipped) ", skipped" else "") +
                    (if (e.hintsUsed.isNotEmpty()) ", hints ${e.hintsUsed.joinToString(" ")}" else "") +
                    (e.stars?.let { ", $it stars" } ?: "") + ", ${duration(e.durationMs)}"
                is BriefEvent.LessonDone -> "${e.lessonId} finished, ${e.stars} stars, ${duration(e.durationMs)}" + if (e.first) ", first time" else ""
                else -> null
            } ?: continue
            any = true; line("  ${clock(e.at)} ${p.label}: $text")
        }
        if (!any) line("  none")

        line(); line("Songs and toys")
        any = false
        for (p in people) for (e in p.events.filter { it.at in from until until }.sortedBy { it.at }) {
            val text = when (e) {
                is BriefEvent.SongRun -> "song ${TesterRules.safeId(e.songId)} ${e.mode.name.lowercase()}" + (e.accuracy?.let { " $it%" } ?: "") +
                    ", ${e.stars} stars" + (if (e.duet) ", duet" else "")
                is BriefEvent.ToyOpened -> "toy ${e.toy.wire}, ${duration(e.durationMs)}"
                is BriefEvent.SessionEnded -> "session ended, ${duration(e.durationMs)} on screen, ${e.practiceSec / 60} min practice"
                else -> null
            } ?: continue
            any = true; line("  ${clock(e.at)} ${p.label}: $text")
        }
        if (!any) line("  none")

        line(); line("Refusals and errors")
        val errors = t.filter { it.name == TesterLog.ERROR || it.name == TesterLog.LINE }
        if (errors.isEmpty()) line("  none")
        errors.forEach { line("  ${clock(it.at)} " + if (it.name == TesterLog.ERROR) "error shown: ${s(it, "where")}" else s(it, "line")) }

        line(); line("AI and email results")
        val results = t.filter { it.name == TesterLog.RESULT }
        if (results.isEmpty()) line("  none")
        results.forEach { line("  ${clock(it.at)} ${s(it, "kind")} ${s(it, "out")}: ${s(it, "line")}") }

        line(); line("Something felt off")
        val notes = t.filter { it.name == TesterLog.NOTE }
        if (notes.isEmpty()) line("  none")
        notes.forEach { n -> line("  ${clock(n.at)} on ${s(n, "s")}" + (n.fields["before"]?.let { " (after $it)" } ?: "") + ": " + s(n, "text")) }
        return out.toString()
    }

    /** "45 s", "3 min 20 s", "1 h 05 min". */
    fun duration(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        return when {
            sec < 60 -> "$sec s"
            sec < 3600 -> "${sec / 60} min" + if (sec % 60 != 0L) " ${sec % 60} s" else ""
            else -> "${sec / 3600} h " + String.format(Locale.ROOT, "%02d min", (sec % 3600) / 60)
        }
    }

    private val CLOCK = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ROOT)
    private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ROOT)
}
