package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.playground.BIG_KID_LESSONS
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID

/**
 * The course in the order one person walks it (2.1, Profile.firstUnitId). A kid starts in Pup's Playground and walks
 * straight on into Basics; a reader ("big kid") is pointed at the plan's short list of Playground games
 * ([BIG_KID_LESSONS]) and the rest stay open. A grown-up starts at Basics: the Playground comes after the course as an
 * extra unit, open to tap but never the next lesson (Learn's "Start", Practice today's lesson part).
 */
class CoursePath(val firstUnitId: String, val bigKid: Boolean = false, private val course: List<CourseLevel> = COURSE) {
    /** Units in the order the Learn path shows them. */
    val levels: List<CourseLevel> = course.firstOrNull { it.id == firstUnitId }
        ?.let { first -> listOf(first) + course.filter { it.id != firstUnitId && !isExtra(it) } + course.filter { it != first && isExtra(it) } }
        ?: course

    /** A unit on the path that is never suggested: the Playground for a grown-up. */
    fun isExtra(level: CourseLevel): Boolean = level.id == PLAYGROUND_ID && firstUnitId != PLAYGROUND_ID

    /** Every lesson, in path order, with its unit. */
    val lessons: List<Pair<CourseLevel, CourseLesson>> = levels.flatMap { lv -> lv.lessons.map { lv to it } }

    /** The lessons that count toward "n of m lessons": everything but the extra unit. */
    val counted: List<CourseLesson> = lessons.filter { !isExtra(it.first) }.map { it.second }

    /** Lessons a suggestion may land on: no extra unit, and for a big kid only the short list of Playground games. */
    private fun suggestable(level: CourseLevel, lesson: CourseLesson): Boolean =
        !isExtra(level) && !(bigKid && level.id == PLAYGROUND_ID && lesson.id !in BIG_KID_LESSONS)

    /** The next lesson to suggest: the first suggestable one not [done]; null when the course is finished. */
    fun next(done: (String) -> Boolean): CourseLesson? = lessons.firstOrNull { (lv, l) -> suggestable(lv, l) && !done(l.id) }?.second

    /**
     * The lesson after [lesson] for "Next lesson": the following one on the path, skipping what is not suggested (a
     * big kid's other games). Inside the extra unit, its next lesson; at the end of the main course, null.
     */
    fun after(lesson: CourseLesson): CourseLesson? {
        val i = lessons.indexOfFirst { it.second.id == lesson.id }
        if (i < 0) return null
        val extra = isExtra(lessons[i].first)
        return lessons.drop(i + 1).firstOrNull { (lv, l) -> if (extra) lv == lessons[i].first else suggestable(lv, l) }?.second
    }

    /** The unit [lesson] belongs to. */
    fun levelOf(lesson: CourseLesson): CourseLevel? = lessons.firstOrNull { it.second.id == lesson.id }?.first

    /** 1-based number of [lesson] on the path (the extra unit numbers on after the course). */
    fun numberOf(lesson: CourseLesson): Int = lessons.indexOfFirst { it.second.id == lesson.id } + 1
}
