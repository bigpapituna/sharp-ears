package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionPlan
import java.time.LocalDate

/** The kinds of quest task. Each knows which [QuestEvent] moves it on (see [QuestTask.after]). */
enum class QuestKind {
    /** Play [QuestTask.target] songs to the end (any songs, the same one twice counts twice). */
    PLAY_SONGS,
    /** Copy the pup's pattern [QuestTask.target] times (Pup says or melody memory rounds). */
    COPY_PUP,
    /**
     * Play every C from C2 to C6 (five of them, any order), or finish the find-c lesson. On a touch keyboard, every C it
     * has is all the Cs there are (the Jam's keys are C4 to C5: two of them; QA 2.8.3).
     */
    FIND_CS,
    /** Answer the day's due chords ([QuestTask.target] of them). */
    DUE_CHORDS,
    /** Finish the lesson [QuestTask.ref] (or any lesson when ref is null). */
    LESSON,
    /** Loop the trouble bar of the song [QuestTask.ref]. */
    TROUBLE_BAR,
    /** Play the song [QuestTask.ref] to the end. */
    SONG,
    /** 2.7: play a round of the arcade game [QuestTask.ref] to its end (engine/arcade ArcadeGame's id). */
    PLAY_GAME,
}

/**
 * Something that happened, as the quest hears it. The integrator sends these to `PlayStore.recordQuest` from the
 * places listed in ui/play/INTEGRATION.md; the Play screens send their own.
 */
sealed interface QuestEvent {
    /** A song run reached its end, in any mode. */
    data class SongFinished(val songId: String) : QuestEvent
    /** A Pup says pattern (or a melody memory round) copied right. */
    data class PupCopied(val length: Int) : QuestEvent
    /**
     * A note was heard (the Jam, a lesson, a song): only Cs matter, for Find all the Cs. [touchKeys]: the keys on the screen
     * it was touched on (null: a piano heard by the mic, or MIDI, which has every C).
     */
    data class NotePlayed(val midi: Int, val touchKeys: IntRange? = null) : QuestEvent
    /** A due chord from the daily review was answered. */
    data class ChordReviewed(val itemId: String) : QuestEvent
    /** A course lesson was finished. */
    data class LessonDone(val lessonId: String) : QuestEvent
    /** The trouble-bar loop of a song was played (the session's Loop part ran). */
    data class LoopDone(val songId: String, val bar: Int) : QuestEvent
    /** 2.7: a round of the arcade game [gameId] was played to its end. */
    data class GameRound(val gameId: String) : QuestEvent
}

/**
 * One task of the day: what the card says ([title], spoken the same), how much is needed ([target]), how much is
 * done, and for tasks that count different things (the Cs, the chords) which ones were [seen]. Each has its own star.
 */
data class QuestTask(
    val kind: QuestKind, val title: String, val target: Int, val ref: String? = null,
    val done: Int = 0, val seen: Set<String> = emptySet(),
) {
    val complete get() = done >= target
    /** 0..1 for the card's little bar. */
    val fraction get() = (done.toFloat() / target).coerceIn(0f, 1f)

    /** This task after [e]; the same task when [e] does not concern it. */
    fun after(e: QuestEvent): QuestTask {
        if (complete) return this
        return when (kind) {
            QuestKind.PLAY_SONGS -> if (e is QuestEvent.SongFinished) bump() else this
            QuestKind.COPY_PUP -> if (e is QuestEvent.PupCopied) bump() else this
            QuestKind.FIND_CS -> when {
                e is QuestEvent.LessonDone && e.lessonId == FIND_C_LESSON -> copy(done = target, seen = seen + ALL_CS.map { it.toString() })
                e is QuestEvent.NotePlayed && e.midi in ALL_CS -> distinct(e.midi.toString()).let { t ->
                    // A touch keyboard with only some of the Cs (the Jam's C4 and C5): finding each one there is all of them.
                    val there = e.touchKeys?.let { keys -> ALL_CS.filter { it in keys } }.orEmpty()
                    if (there.isNotEmpty() && there.all { it.toString() in t.seen }) t.copy(done = target) else t
                }
                else -> this
            }
            QuestKind.DUE_CHORDS -> if (e is QuestEvent.ChordReviewed) distinct(e.itemId) else this
            QuestKind.LESSON -> if (e is QuestEvent.LessonDone && (ref == null || ref == e.lessonId)) bump() else this
            QuestKind.TROUBLE_BAR -> if (e is QuestEvent.LoopDone && e.songId == ref) bump() else this
            QuestKind.SONG -> if (e is QuestEvent.SongFinished && e.songId == ref) bump() else this
            QuestKind.PLAY_GAME -> if (e is QuestEvent.GameRound && (ref == null || e.gameId == ref)) bump() else this
        }
    }

    private fun bump() = copy(done = (done + 1).coerceAtMost(target))
    private fun distinct(key: String) = if (key in seen) this else copy(seen = seen + key, done = (done + 1).coerceAtMost(target))

    companion object {
        /** The Cs of Find all the Cs: C2 to C6, the Cs of the find-c lesson. */
        val ALL_CS = listOf(36, 48, 60, 72, 84)
        const val FIND_C_LESSON = "find-c"
    }
}

/**
 * Pup's quest: three tiny tasks a day, each with its own star. Kids always get the same three ("Play two songs",
 * "Copy the pup three times", "Find all the Cs"); grown-ups get theirs from today's "Practice today" plan (the due
 * chords, the next lesson, the current song's trouble bar or the song itself), topped up to three with playing two
 * songs, copying the pup and finishing any lesson. A quest lasts its day: [today] keeps a stored quest of the same
 * day and kind, and makes a new one otherwise. No streaks and nothing lost: a day's quest that is not finished
 * just ends.
 */
class Quest(val day: LocalDate, val kid: Boolean, tasks: List<QuestTask>) {
    var tasks: List<QuestTask> = tasks; private set

    val stars get() = tasks.count { it.complete }
    val allDone get() = tasks.isNotEmpty() && tasks.all { it.complete }

    /** Moves the tasks on with [event]; returns the tasks this event completed (each is a star to celebrate). */
    fun record(event: QuestEvent): List<QuestTask> {
        val before = tasks
        tasks = tasks.map { it.after(event) }
        return tasks.filterIndexed { i, t -> t.complete && !before[i].complete }
    }

    fun encode(): String = MiniJson.write(mapOf(
        "day" to day.toString(), "kid" to kid,
        "tasks" to tasks.map { t ->
            mapOf("kind" to t.kind.name, "title" to t.title, "target" to t.target, "ref" to t.ref, "done" to t.done, "seen" to t.seen.sorted())
        },
    ))

    companion object {
        const val SIZE = 3

        /**
         * The kids' three. 2.7: with a [game] (the arcade's pick for the day: its id and its task's words, engine/arcade
         * ArcadeQuest) the middle one is that game instead of copying the pup.
         */
        fun kidTasks(game: Pair<String, String>? = null) = listOf(
            QuestTask(QuestKind.PLAY_SONGS, "Play two songs", 2),
            game?.let { (id, title) -> QuestTask(QuestKind.PLAY_GAME, title, 1, id) } ?: QuestTask(QuestKind.COPY_PUP, "Copy the pup three times", 3),
            QuestTask(QuestKind.FIND_CS, "Find all the Cs", QuestTask.ALL_CS.size),
        )

        /** A grown-up's three, from [plan]: one task per part in the plan's order, then the top-ups. */
        fun adultTasks(plan: SessionPlan?): List<QuestTask> {
            val fromPlan = plan?.parts.orEmpty().map { part ->
                when (part) {
                    is SessionPart.Chords -> part.itemIds.size.let { n ->
                        QuestTask(QuestKind.DUE_CHORDS, if (n == 1) "Review the chord that is due" else "Review $n due chords", n)
                    }
                    is SessionPart.Lesson -> QuestTask(QuestKind.LESSON, "Finish the lesson ${part.title}", 1, part.lessonId)
                    is SessionPart.Loop -> QuestTask(QuestKind.TROUBLE_BAR, "Smooth out bar ${part.bar} of ${part.title}", 1, part.songId)
                    is SessionPart.Song -> QuestTask(QuestKind.SONG, "Play ${part.title} to the end", 1, part.songId)
                    // 2.14: a challenge song counts as the song task; a drill has no quest of its own (the top-ups fill in).
                    is SessionPart.Challenge -> (part.item as? com.bigpapituna.sharpears.engine.nav.Challenge.Perform)?.let {
                        QuestTask(QuestKind.SONG, "Play ${it.title} to the end", 1, it.songId)
                    }
                }
            }.filterNotNull()
            val topUps = listOf(
                QuestTask(QuestKind.PLAY_SONGS, "Play two songs to the end", 2),
                QuestTask(QuestKind.COPY_PUP, "Copy the pup three times", 3),
                QuestTask(QuestKind.LESSON, "Finish any lesson", 1),
            ).filter { t -> fromPlan.none { it.kind == t.kind } }
            return (fromPlan + topUps).take(SIZE)
        }

        fun forDay(day: LocalDate, kid: Boolean, plan: SessionPlan?, game: Pair<String, String>? = null): Quest =
            Quest(day, kid, if (kid) kidTasks(game) else adultTasks(plan))

        /** Today's quest: [stored] when it is today's and of the same kind (kid or grown-up), else a new one ([game]: a kid's game task). */
        fun today(stored: Quest?, day: LocalDate, kid: Boolean, plan: SessionPlan?, game: Pair<String, String>? = null): Quest =
            stored?.takeIf { it.day == day && it.kid == kid } ?: forDay(day, kid, plan, game)

        /** Reads [encode]'s text; null for anything it cannot read (a new quest is made instead). */
        fun decode(text: String?): Quest? = text?.let {
            runCatching {
                val o = MiniJson.read(it) as Map<*, *>
                val tasks = (o["tasks"] as List<*>).map { t ->
                    t as Map<*, *>
                    QuestTask(
                        QuestKind.valueOf(t["kind"] as String), t["title"] as String, (t["target"] as Number).toInt(),
                        t["ref"] as String?, (t["done"] as Number).toInt(), (t["seen"] as List<*>).map { s -> s.toString() }.toSet(),
                    )
                }
                Quest(LocalDate.parse(o["day"] as String), o["kid"] as Boolean, tasks)
            }.getOrNull()
        }
    }
}
