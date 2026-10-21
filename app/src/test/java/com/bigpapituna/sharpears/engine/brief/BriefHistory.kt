package com.bigpapituna.sharpears.engine.brief

import java.time.LocalDate
import java.time.ZoneId

/** The tests' time zone and "now": Wednesday 21 October 2026, 8 pm in Ottawa. */
val ZONE: ZoneId = ZoneId.of("America/Toronto")
val TODAY: LocalDate = LocalDate.of(2026, 10, 21)
val NOW: Long = at(TODAY, 20)

fun at(day: LocalDate, hour: Int, minute: Int = 0): Long = day.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

/**
 * A synthetic event history, written the way the app logs it: a session's start, what happened in it (each event 30 s
 * after the last), its end with the time on screen and the practice counted.
 */
class BriefHistory {
    val events = ArrayList<BriefEvent>()

    inner class Session(var t: Long) {
        private fun next(): Long { t += 30_000; return t }
        fun step(lesson: String, index: Int = 1, kind: String = "PLAY", tries: Int = 1, letGo: Boolean = false, hints: List<Int> = emptyList(),
                 stars: Int? = 3, accuracy: Int? = null, score: Int? = null, timing: Int? = null, game: String? = null, hands: Hands? = null,
                 notes: Int = 8, wrong: Int = 0, near: Int = 0, far: Int = 0, skipped: Boolean = false, ms: Long = 60_000) {
            events += BriefEvent.LessonStep(next(), lesson, index, kind, tries, letGo, hints, ms, game, hands, skipped, stars, accuracy, score, timing,
                notes, wrong, near, far)
        }
        fun done(lesson: String, stars: Int = 3, first: Boolean = true) { events += BriefEvent.LessonDone(next(), lesson, stars, 120_000, first) }
        fun song(id: String, stars: Int = 3, mode: RunMode = RunMode.WAIT, hands: Hands = Hands.RIGHT, accuracy: Int? = null, timing: Int? = null,
                 near: Int = 0, far: Int = 0, notes: Int = 20, hints: List<Int> = emptyList()) {
            events += BriefEvent.SongRun(next(), id, mode, hands, accuracy, stars, timing, near, far, notes, near + far, hints, 90_000)
        }
        fun toy(toy: ToyKind, minutes: Int) { events += BriefEvent.ToyOpened(next(), toy, minutes * 60_000L) }
        fun ear(game: String, correct: Boolean) { events += BriefEvent.EarRound(next(), game, correct, 0) }
        fun noisy(screen: String = "player") { events += BriefEvent.NoisyRoom(next(), screen) }
        fun goal(min: Int = 5) { events += BriefEvent.GoalReached(next(), min) }
        fun sticker(r: StickerRarity) { events += BriefEvent.StickerDrop(next(), r, "garden-1", true, "lesson") }
        fun finale(page: String) { events += BriefEvent.FinalePlayed(next(), page) }
        fun quest(task: String) { events += BriefEvent.QuestStar(next(), task) }
        fun setting(key: String, value: String) { events += BriefEvent.SettingChanged(next(), key, value) }
        fun action(id: String, params: Map<String, String>) { events += BriefEvent.ParentAction(next(), id, params) }
        fun bedtime() { events += BriefEvent.BedtimeShown(next()) }
    }

    /** A session on [day] at [hour], [minutes] on screen with [practiceMin] practised (all of it by default). */
    fun session(day: LocalDate, hour: Int, minutes: Int, practiceMin: Int = minutes, minute: Int = 0, end: Boolean = true,
                block: Session.() -> Unit = {}): BriefHistory {
        val start = at(day, hour, minute)
        events += BriefEvent.SessionStarted(start)
        Session(start).block()
        if (end) events += BriefEvent.SessionEnded(start + minutes * 60_000L, minutes * 60_000L, practiceMin * 60)
        return this
    }
}

fun history(block: BriefHistory.() -> Unit) = BriefHistory().apply(block).events
