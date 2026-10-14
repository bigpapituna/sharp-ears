package com.bigpapituna.sharpears.engine

/** What a course lesson is mostly about, for its icon on the Learn path. */
enum class LessonKind(val label: String) { READ("Read"), LISTEN("Listen"), PLAY("Play"), PERFORM("Perform"), SONG("Song") }

/**
 * A lesson's kind: a lesson that leads to a library song is a song lesson; otherwise its most demanding step
 * decides (a graded run in time over playing along, over listening, over reading).
 */
val CourseLesson.kind: LessonKind get() = when {
    songId != null -> LessonKind.SONG
    steps.any { it.kind == StepKind.PERFORM || it.kind == StepKind.RAMP || it.kind.needsMidi } -> LessonKind.PERFORM
    steps.any { it.kind == StepKind.PLAY || it.kind == StepKind.FROM_MEMORY } -> LessonKind.PLAY
    steps.any { it.kind == StepKind.LISTEN } -> LessonKind.LISTEN
    else -> LessonKind.READ
}

/** One step's kind on the same scale, for the step icons on a lesson's card. */
val Step.lessonKind: LessonKind get() = when (kind) {
    StepKind.READ -> LessonKind.READ
    StepKind.LISTEN -> LessonKind.LISTEN
    StepKind.PLAY, StepKind.FROM_MEMORY -> LessonKind.PLAY
    StepKind.PERFORM, StepKind.RAMP, StepKind.PEDAL, StepKind.DYNAMICS -> LessonKind.PERFORM
}
