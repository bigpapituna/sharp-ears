package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLevel

/**
 * A page of Pup's Playground: one place in the pup's world, one course level, twelve lessons in order. The kid's
 * path runs page by page ([PLAYGROUND_PAGES]), then on to the big kids' road (Basics). The world draws one scene per
 * page and a stone per lesson; the finale plays when a page's last lesson is done.
 */
data class PlaygroundPage(val id: String, val levelId: String, val place: Place, val title: String, val lessonIds: List<String>) {
    val lastLessonId get() = lessonIds.last()
}

private fun page(id: String, level: CourseLevel, place: Place, title: String) = PlaygroundPage(id, level.id, place, title, level.lessons.map { it.id }) to level

/**
 * Every page with its course level, in path order: the one list to change when a page is added (2.3: the workshop, the
 * treehouse, the night sky, then the fair, the last before the big kids' road). [PLAYGROUND_PAGES],
 * [PLAYGROUND_LEVELS] and the course's start (COURSE) all follow it.
 */
private val PAGES: List<Pair<PlaygroundPage, CourseLevel>> = listOf(
    page("garden", PLAYGROUND, Place.GARDEN, "The garden"),
    page("pond", POND, Place.POND, "The pond"),
    page("hall", HALL, Place.CONCERT_HALL, "The concert hall"),
    page("workshop", WORKSHOP, Place.WORKSHOP, "The workshop"),
    page("treehouse", TREEHOUSE, Place.TREEHOUSE, "The treehouse"),
    page("nightsky", NIGHT_SKY, Place.NIGHT_SKY, "The night sky"),
    page("fair", FAIR, Place.FAIR, "The fair"),
)

/** In path order. The garden is the first twelve; the pond, the concert hall, the workshop, the treehouse, the night sky and the fair follow. */
val PLAYGROUND_PAGES: List<PlaygroundPage> = PAGES.map { it.first }

/** The pages' course levels, in path order (first in COURSE, before Basics). */
val PLAYGROUND_LEVELS: List<CourseLevel> get() = PAGES.map { it.second }

/** True for the level of a Playground page (any of the seven). */
fun isPlaygroundLevel(levelId: String) = PLAYGROUND_PAGES.any { it.levelId == levelId }

fun pageOf(lessonId: String): PlaygroundPage? = PLAYGROUND_PAGES.firstOrNull { lessonId in it.lessonIds }

/** True for every lesson of every Playground page. */
fun isPlaygroundLesson(lessonId: String) = pageOf(lessonId) != null
