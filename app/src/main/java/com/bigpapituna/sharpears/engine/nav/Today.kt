package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.SessionPart
import com.bigpapituna.sharpears.engine.SessionPlan
import com.bigpapituna.sharpears.engine.plural

/**
 * The Today tab's card (2.7, the grown-ups' home): today's plan as a short list, the one Start that walks it (Practice today,
 * engine/DailySession.kt), and what the card says once it is done or when nothing is due. Pure, so what the card is made of
 * is tested on the JVM (TodayTest); ui/TodayScreen.kt draws it.
 */
data class TodayRow(val kind: Kind, val title: String, val detail: String) {
    enum class Kind { CHORDS, LESSON, SONG }
}

data class TodayCard(val state: State, val about: String, val rows: List<TodayRow>, val summary: String?) {
    /** PLAN: rows and Start. NOTHING_DUE: an empty day, the songs are the way in. DONE: today's summary and "Again". */
    enum class State { PLAN, NOTHING_DUE, DONE }

    companion object {
        /**
         * [plan] is today's Practice today plan (chords due, the next lesson, the Continue song or its worst bar), in the order
         * Start walks it. [summary] is "Done today: ..." once it ran today: the card then says so, whatever is left.
         */
        fun of(plan: SessionPlan, summary: String?): TodayCard = TodayCard(
            when { summary != null -> State.DONE; plan.isEmpty -> State.NOTHING_DUE; else -> State.PLAN },
            plan.aboutLabel, plan.parts.map(::rowOf), summary)

        /** One part as a row: what it is in a few words, and a quiet line under it. */
        fun rowOf(p: SessionPart): TodayRow = when (p) {
            is SessionPart.Chords -> TodayRow(TodayRow.Kind.CHORDS, plural(p.itemIds.size, "chord") + " due", "From the daily chord review")
            is SessionPart.Lesson -> TodayRow(TodayRow.Kind.LESSON, p.title, "Your next lesson")
            is SessionPart.Loop -> TodayRow(TodayRow.Kind.SONG, p.title, "Bar ${p.bar}, two minutes on a loop")
            is SessionPart.Song -> TodayRow(TodayRow.Kind.SONG, p.title, "With the notes waiting for you")
        }
    }
}

/**
 * What Today's Continue tile opens (2.7): the library song, the song by chords or the lesson played last. Stored as one line
 * ("song:<id>", "pop:<id>", "lesson:<id>", Prefs.lastPlayed); a profile from before 2.7 has only its last song.
 */
sealed interface ContinueWith {
    val id: String
    data class Song(override val id: String) : ContinueWith
    data class PopSong(override val id: String) : ContinueWith
    data class Lesson(override val id: String) : ContinueWith

    companion object {
        fun encode(c: ContinueWith): String = when (c) { is Song -> "song:${c.id}"; is PopSong -> "pop:${c.id}"; is Lesson -> "lesson:${c.id}" }

        fun decode(s: String?): ContinueWith? {
            val id = s?.substringAfter(':', "")?.takeIf { it.isNotEmpty() } ?: return null
            return when (s.substringBefore(':')) { "song" -> Song(id); "pop" -> PopSong(id); "lesson" -> Lesson(id); else -> null }
        }

        /**
         * The tile for [last] when it still [exists] (a deleted song, a lesson gone from the course), else the last library song
         * ([lastSongId], kept since 1.0), else null: the tile then offers an easy first song ("Start here").
         */
        fun of(last: String?, lastSongId: String?, exists: (ContinueWith) -> Boolean): ContinueWith? =
            decode(last)?.takeIf(exists) ?: lastSongId?.let(::Song)?.takeIf(exists)
    }
}
