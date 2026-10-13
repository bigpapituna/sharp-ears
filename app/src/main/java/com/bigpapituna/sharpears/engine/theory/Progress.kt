package com.bigpapituna.sharpears.engine.theory

import kotlin.math.roundToInt

/** Where a learner stands in the academy, given which lessons are [done] and each lesson's [best] quiz score (%). */
class TheoryProgress(private val done: (String) -> Boolean, private val best: (String) -> Int? = { null }) {
    /** The first lesson not done yet, in course order. */
    val next: Pair<TheoryUnit, TheoryLesson>? get() = THEORY_LESSONS.firstOrNull { !done(it.second.id) }
    fun done(lesson: TheoryLesson) = done(lesson.id)
    fun unitDone(u: TheoryUnit) = u.lessons.all { done(it.id) }
    fun lessonsDone(u: TheoryUnit) = u.lessons.count { done(it.id) }
    val unitsDone get() = THEORY.count(::unitDone)
    val lessonsDone get() = THEORY_LESSONS.count { done(it.second.id) }
    /** The average best quiz score over a unit's lessons that have one. */
    fun unitScore(u: TheoryUnit): Int? = u.lessons.mapNotNull { best(it.id) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

fun percent(correct: Int, total: Int) = if (total <= 0) 0 else (100.0 * correct / total).roundToInt()

/** The lesson after [lessonId] in course order, if any. */
fun lessonAfter(lessonId: String) = THEORY_LESSONS.indexOfFirst { it.second.id == lessonId }.let { THEORY_LESSONS.getOrNull(it + 1) }
