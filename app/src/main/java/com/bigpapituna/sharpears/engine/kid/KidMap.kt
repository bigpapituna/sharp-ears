package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.world.Place

/** How a stone looks: walked (with its stars), the one to play next, one left behind (open to play), or one ahead (asleep). */
enum class StoneState { DONE, NEXT, OPEN, LATER }

/** One lesson's stone on the kid's map: its state and its best stars (0 before the first finish). */
data class Stone(val lessonId: String, val title: String, val state: StoneState, val stars: Int, val skipped: Boolean = false)

/** A Playground page on the map: its scene's place and a stone per lesson, in order. */
data class MapPage(val page: PlaygroundPage, val place: Place, val stones: List<Stone>) {
    val walked: Boolean get() = stones.isNotEmpty() && stones.all { it.state == StoneState.DONE }
}

/** A stretch of the big kids' road: one course unit (Basics, Beginner, ...) and its stones. */
data class RoadUnit(val levelId: String, val title: String, val stones: List<Stone>)

/**
 * The whole map a kid sees (2.2): every Playground page in order, then the big kids' road, open once the kid's next
 * stone is past the Playground. [next] is the lesson the pup stands at (null when there is nothing left to suggest),
 * [nextPage] the index of its page (-1 on the road or without one).
 */
data class KidMapModel(val pages: List<MapPage>, val roadOpen: Boolean, val road: List<RoadUnit>, val next: String?, val nextPage: Int) {
    val stones: List<Stone> get() = pages.flatMap { it.stones } + road.flatMap { it.stones }
    fun stone(lessonId: String): Stone? = stones.firstOrNull { it.lessonId == lessonId }
}

/**
 * The kid's walk (2.2): the Playground pages ([PLAYGROUND_PAGES]) one after another, then the big kids' road (the rest
 * of the course, Basics first, still with the kid skin). Pure; ui/kid/KidMap.kt draws it.
 */
object KidMap {
    /**
     * The stone the pup stands at: the path's next lesson ([CoursePath.next], so a big kid's short list holds), where
     * a page whose finale has played counts as walked. Its gaps stay open to play, but the pup has moved on to the
     * next page's first stone or, after the last page, to the big kids' road.
     *
     * A grown-up's [pace] (2.4, the parent brief's actions) comes first: a pinned lesson is the next stone (unless focus
     * hides its page); a page opened early counts as walked; under focus the pup never goes past the focus page: when the
     * walk is beyond it, he stands on its first lesson not done, else on its stone with the fewest [stars].
     */
    fun next(path: CoursePath, done: (String) -> Boolean, finales: Set<String>, pages: List<PlaygroundPage> = PLAYGROUND_PAGES,
             pace: Pace = Pace.NONE, stars: (String) -> Int = { 0 }): CourseLesson? {
        val end = focusEnd(path, pages, pace.focus)
        pace.pin?.let(path::lesson)?.takeIf { end == null || path.indexOf(it.id) <= end }?.let { return it }
        val walk = walk(path, done, finales, pages, pace.early)
        if (end == null || (walk != null && path.indexOf(walk.id) <= end)) return walk
        val onPage = pages.first { it.id == pace.focus }.lessonIds.mapNotNull(path::lesson)
        return onPage.firstOrNull { !done(it.id) } ?: onPage.minByOrNull { stars(it.id) }
    }

    /** The path's next with pages whose finale played, or that a grown-up opened past early, counted as walked. */
    private fun walk(path: CoursePath, done: (String) -> Boolean, finales: Set<String>, pages: List<PlaygroundPage>, early: Set<String>): CourseLesson? =
        path.next { id -> done(id) || KidPages.pageOf(id, pages)?.id?.let { it in finales || it in early } == true }

    /** The path index of the focus page's last lesson (pages after it are hidden), or null without focus. */
    private fun focusEnd(path: CoursePath, pages: List<PlaygroundPage>, focus: String?): Int? =
        focus?.let { f -> pages.firstOrNull { it.id == f } }?.lessonIds?.maxOfOrNull { path.indexOf(it) }?.takeIf { it >= 0 }

    /**
     * The map for a kid on [path]: [done] says which lessons are finished, [stars] their best stars (0 for none),
     * [finales] the pages whose finale has played. A page lesson missing from the course (a page still being written)
     * is left off; a page may hold fewer stones than its scene's twelve.
     *
     * With a [pace]: the pup stands on the pinned stone (walked or not); stones up to the walk's next stay open; under
     * focus the pages after the focus page and the road are not drawn.
     */
    fun build(path: CoursePath, done: (String) -> Boolean, stars: (String) -> Int, finales: Set<String>,
              pages: List<PlaygroundPage> = PLAYGROUND_PAGES, pace: Pace = Pace.NONE,
              /** Lessons placement skipped (2.6): done for the walk, but shown as stones passed by: open, no stars, never a walked page. */
              skipped: (String) -> Boolean = { false }): KidMapModel {
        val next = next(path, done, finales, pages, pace, stars)
        val walk = walk(path, done, finales, pages, pace.early)
        val nextAt = next?.let { path.indexOf(it.id) } ?: -1
        // Stones up to the walk's next stay open (it among them), and those before the pup's stone: a pin or focus closes none.
        val walkAt = walk?.let { path.indexOf(it.id) } ?: Int.MAX_VALUE
        val end = focusEnd(path, pages, pace.focus)
        fun stone(l: CourseLesson): Stone {
            val at = path.indexOf(l.id)
            val state = when {
                l.id == next?.id && (l.id == pace.pin || end != null) -> StoneState.NEXT      // pinned or focused: the pup stands here even when walked
                skipped(l.id) -> StoneState.OPEN
                done(l.id) -> StoneState.DONE
                l.id == next?.id -> StoneState.NEXT
                next == null || at < nextAt || at <= walkAt -> StoneState.OPEN
                else -> StoneState.LATER
            }
            val passedBy = skipped(l.id)
            return Stone(l.id, l.title, state, if (done(l.id) && !passedBy) stars(l.id).coerceIn(0, 3) else 0, passedBy)
        }
        val shown = if (end == null) pages else pages.take(pages.indexOfFirst { it.id == pace.focus } + 1)
        val mapPages = shown.map { page -> MapPage(page, KidPages.worldPlace(page), page.lessonIds.mapNotNull { id -> path.lesson(id)?.let(::stone) }) }
        val roadOpen = end == null && (walk == null || KidPages.pageOf(walk.id, pages) == null)
        // The road reaches the unit of the walk's next stone, or of a pinned road lesson further on.
        val roadTo = if (walk == null) null else listOfNotNull(walk, next).maxBy { path.indexOf(it.id) }
        val road = if (!roadOpen) emptyList() else roadUnits(path, roadTo).map { lv -> RoadUnit(lv.id, lv.title, lv.lessons.map(::stone)) }
        return KidMapModel(mapPages, roadOpen, road, next?.id, next?.let { n -> shown.indexOfFirst { n.id in it.lessonIds } } ?: -1)
    }

    /**
     * The road's units as far as the kid has come: every unit after the Playground up to and including the one the
     * next stone is in (all of them when the course is finished), so the road grows a unit at a time.
     */
    fun roadUnits(path: CoursePath, next: CourseLesson?): List<CourseLevel> {
        val road = path.levels.filter { !path.isPlayground(it) && !path.isExtra(it) }
        if (next == null) return road
        val upTo = road.indexOfFirst { lv -> lv.lessons.any { it.id == next.id } }
        return if (upTo < 0) road.take(1) else road.take(upTo + 1)
    }
}

/**
 * The finale (2.2): when a page's last lesson ([PlaygroundPage.lastLessonId]) is finished for the first time, the
 * whole screen celebrates the page, then the path walks on to the next page's first stone or, after the last page, to
 * the big kids' road. Pure; ui/kid/Finale.kt shows it and data/KidStore.kt remembers which finales have played.
 */
object Finale {
    /** The page whose finale finishing [lessonId] plays now, or null: its last lesson, a finale not played before ([seen]). */
    fun trigger(lessonId: String, seen: Set<String>, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): PlaygroundPage? =
        pages.firstOrNull { it.lessonIds.isNotEmpty() && it.lastLessonId == lessonId && it.id !in seen }

    /**
     * A finale owed and never shown: a page whose last lesson is [done] but whose finale has not played ([seen]). It
     * plays as the kid leaves that lesson; when the app was closed or killed in between, the kid home plays it the next
     * time it opens (before, it was lost for good: the map had already walked on past the page).
     */
    fun owed(done: (String) -> Boolean, seen: Set<String>, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): PlaygroundPage? =
        pages.firstOrNull { it.lessonIds.isNotEmpty() && it.id !in seen && done(it.lastLessonId) }

    /** Where the path goes after [page]'s finale. */
    sealed interface After {
        /** The next page, its first stone. */
        data class NextPage(val page: PlaygroundPage) : After
        /** The big kids' road: the pup says so, and Basics' first lesson is the next stone. */
        data object Road : After
    }

    fun after(page: PlaygroundPage, pages: List<PlaygroundPage> = PLAYGROUND_PAGES): After {
        val i = pages.indexOfFirst { it.id == page.id }
        return pages.getOrNull(i + 1)?.takeIf { i >= 0 && it.lessonIds.isNotEmpty() }?.let { After.NextPage(it) } ?: After.Road
    }
}
