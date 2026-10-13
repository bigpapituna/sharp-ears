package com.bigpapituna.sharpears.engine.progress

import java.time.LocalDateTime

/**
 * Something the learner did that earns XP or counts toward an achievement. The app reports each one to
 * `ProgressStore.record`; nothing here knows about Android.
 */
sealed class ProgressEvent {
    /** [seconds] of practice that ended at [at] (the transport played that long). */
    data class Practice(val at: LocalDateTime, val seconds: Int) : ProgressEvent()

    /** One lesson step passed (a Perform step at its pass mark, or a play-through step). */
    data object LessonStepPassed : ProgressEvent()

    /** The last step of a course lesson. XP counts the first time only, so replaying a lesson does not farm it. */
    data class LessonComplete(val lessonId: String) : ProgressEvent()

    /**
     * A song reached its end. [accuracy] is the Perform score (0..100) or null for a Wait or Listen run;
     * [newBest] is true when it beat the saved best; [firstCompletion] when the song had never been completed.
     */
    data class SongRun(val songId: String, val accuracy: Int?, val newBest: Boolean, val firstCompletion: Boolean) : ProgressEvent()

    /** One chord game round answered; [root] is the chord's pitch class (0..11, C = 0) when known. */
    data class ChordRound(val root: Int? = null) : ProgressEvent()

    /** A chord game of [rounds] rounds finished with [score] right. */
    data class GameComplete(val game: String, val level: Int, val score: Int, val rounds: Int = 10) : ProgressEvent()

    /** A theory lesson finished (first time only earns XP). */
    data class TheoryLesson(val lessonId: String) : ProgressEvent()

    /** All lessons of a theory unit finished (first time only earns XP). */
    data class TheoryUnit(val unitId: String) : ProgressEvent()

    /** One ear-training round answered. */
    data class EarRound(val correct: Boolean = true) : ProgressEvent()

    /** A sight-reading run finished. */
    data class SightRead(val level: Int = 1) : ProgressEvent()

    /** The "Practice today" session finished with [parts] parts done. */
    data class DailySession(val parts: Int = 1) : ProgressEvent()

    /** The learner used a feature for the first time ("mic", "import", "tuner", "metronome", "theory", ...). */
    data class FeatureUsed(val name: String) : ProgressEvent()
}
