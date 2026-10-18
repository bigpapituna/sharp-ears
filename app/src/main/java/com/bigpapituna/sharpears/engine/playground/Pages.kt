package com.bigpapituna.sharpears.engine.playground

/**
 * A page of Pup's Playground: one place in the pup's world, one course level, twelve lessons in order. The kid's
 * path runs page by page ([PLAYGROUND_PAGES]), then on to the big kids' road (Basics). The world draws one scene per
 * page and a stone per lesson; the finale plays when a page's last lesson is done.
 */
data class PlaygroundPage(val id: String, val levelId: String, val place: Place, val title: String, val lessonIds: List<String>) {
    val lastLessonId get() = lessonIds.last()
}

/** In path order. The garden is the first twelve; the pond and the concert hall pages follow. */
val PLAYGROUND_PAGES: List<PlaygroundPage> = listOf(
    PlaygroundPage("garden", PLAYGROUND_ID, Place.GARDEN, "The garden", PLAYGROUND.lessons.map { it.id }),
)

fun pageOf(lessonId: String): PlaygroundPage? = PLAYGROUND_PAGES.firstOrNull { lessonId in it.lessonIds }

/** True for every lesson of every Playground page. */
fun isPlaygroundLesson(lessonId: String) = pageOf(lessonId) != null
