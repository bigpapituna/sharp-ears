package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.playground.BIG_KID_LESSONS
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_ID
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage

/**
 * The course in the order one person walks it (2.1, Profile.firstUnitId). A kid starts in Pup's Playground and walks
 * straight on into Basics; a reader ("big kid") is pointed at the plan's short list of Playground games
 * ([BIG_KID_LESSONS]) and the rest stay open. A grown-up starts at Basics: the Playground comes after the course as an
 * extra unit, open to tap but never the next lesson (Learn's "Start", Practice today's lesson part).
 *
 * 2.2: the Playground is every page of [pages] (one course level each, engine/playground/Pages.kt), in page order: a
 * kid walks them all before Basics, a grown-up has them all as extras at the end. The big kids' short list applies to
 * a page that names lessons in it; a page it does not name is walked whole.
 */
class CoursePath(
    val firstUnitId: String, val bigKid: Boolean = false, private val course: List<CourseLevel> = COURSE,
    pages: List<PlaygroundPage> = PLAYGROUND_PAGES,
    /** Where the first-run questions placed a grown-up ("Some years": the left-hand scale); null starts at the beginning. */
    private val startAt: () -> String? = { null },
) {
    /** The Playground's levels, in page order. */
    private val playgroundIds: List<String> = (listOf(PLAYGROUND_ID) + pages.map { it.levelId }).distinct()

    /** A Playground level (any page's). */
    fun isPlayground(level: CourseLevel): Boolean = level.id in playgroundIds

    /** Units in the order the Learn path shows them. */
    val levels: List<CourseLevel> = course.firstOrNull { it.id == firstUnitId }?.let { first ->
        val playground = playgroundIds.mapNotNull { id -> course.firstOrNull { it.id == id } }
        val rest = course.filter { !isPlayground(it) && it != first }
        if (isPlayground(first)) listOf(first) + playground.filter { it != first } + rest      // a kid: every page, then the course
        else listOf(first) + rest + playground.filter { it != first }                           // a grown-up: the pages as extras, last
    } ?: course

    /** A unit on the path that is never suggested: the Playground's pages for a grown-up. */
    fun isExtra(level: CourseLevel): Boolean = isPlayground(level) && firstUnitId != PLAYGROUND_ID

    /** Every lesson, in path order, with its unit. */
    val lessons: List<Pair<CourseLevel, CourseLesson>> = levels.flatMap { lv -> lv.lessons.map { lv to it } }

    /** The lessons that count toward "n of m lessons": everything but the extra unit. */
    val counted: List<CourseLesson> = lessons.filter { !isExtra(it.first) }.map { it.second }

    /** Playground levels the big kids' short list names lessons in (the others are walked whole). */
    private val shortListed: Set<String> = levels.filter { isPlayground(it) && it.lessons.any { l -> l.id in BIG_KID_LESSONS } }.map { it.id }.toSet()

    /** Lessons a suggestion may land on: no extra unit, and for a big kid only the short list of a short-listed page. */
    private fun suggestable(level: CourseLevel, lesson: CourseLesson): Boolean =
        !isExtra(level) && !(bigKid && level.id in shortListed && lesson.id !in BIG_KID_LESSONS)

    /**
     * The next lesson to suggest: the first suggestable one not [done]; null when the course is finished. A placement
     * ([startAt]) is where suggestions begin; the lessons before it come round once everything after it is done.
     */
    fun next(done: (String) -> Boolean): CourseLesson? {
        fun first(from: Int) = lessons.drop(from).firstOrNull { (lv, l) -> suggestable(lv, l) && !done(l.id) }?.second
        return first(startAt()?.let(::indexOf)?.coerceAtLeast(0) ?: 0) ?: first(0)
    }

    /**
     * The lesson after [lesson] for "Next lesson": the following one on the path, skipping what is not suggested (a
     * big kid's other games). Inside the extra unit, its next lesson; at the end of the main course, null.
     * With [done], lessons already done are passed over and, past the last one, it is where [next] points (so a lesson
     * replayed for review leads on to the one Learn's Start, Practice today and the quest suggest, not to a done one).
     */
    fun after(lesson: CourseLesson, done: ((String) -> Boolean)? = null): CourseLesson? {
        val i = lessons.indexOfFirst { it.second.id == lesson.id }
        if (i < 0) return null
        val extra = isExtra(lessons[i].first)
        val following = lessons.drop(i + 1).firstOrNull { (lv, l) ->
            done?.invoke(l.id) != true && if (extra) lv == lessons[i].first else suggestable(lv, l)
        }?.second
        return following ?: if (done == null || extra) null else next { it == lesson.id || done(it) }
    }

    /** The unit [lesson] belongs to. */
    fun levelOf(lesson: CourseLesson): CourseLevel? = lessons.firstOrNull { it.second.id == lesson.id }?.first

    /** 1-based number of [lesson] on the path (the extra unit numbers on after the course). */
    fun numberOf(lesson: CourseLesson): Int = lessons.indexOfFirst { it.second.id == lesson.id } + 1

    /** 0-based place of the lesson [id] on the path, or -1 when it is not on it. */
    fun indexOf(id: String): Int = lessons.indexOfFirst { it.second.id == id }

    /** The lesson [id] on the path, or null. */
    fun lesson(id: String): CourseLesson? = lessons.firstOrNull { it.second.id == id }?.second

    companion object {
        /** After this unit the app suggests grown-up mode to a kid's grown-up, once (KIDS-PLAN section 1). */
        const val GROWN_UP_AFTER = "beginner"

        /** Offer grown-up mode now: a kid who finished [GROWN_UP_AFTER] and has not been offered it yet. */
        fun offerGrownUp(kid: Boolean, offered: Boolean, done: (String) -> Boolean, course: List<CourseLevel> = COURSE): Boolean =
            kid && !offered && course.firstOrNull { it.id == GROWN_UP_AFTER }?.lessons?.let { it.isNotEmpty() && it.all { l -> done(l.id) } } == true
    }
}
