package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.kid.KidPages
import com.bigpapituna.sharpears.engine.playground.BIG_KID_LESSONS
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage
import com.bigpapituna.sharpears.engine.playground.isPlaygroundLevel

/*
 * Placement (2.6). Roy: "when creating a profile, ask what their current piano level is, so we don't have to show them
 * all the introductory stuff if necessary." A new profile says how much piano it has played (a grown-up for themselves, a
 * parent for a kid), and the course starts there: the units or pages before it are marked "done, skipped", which are not
 * attempted and never earn a star, and stay open to play. Everything here is pure; data/Prefs.kt keeps the marks.
 */

/**
 * How much piano someone has played. Grown-ups answer all four; a parent answers for a kid with the first three ([kidLine]
 * is null for [A_LOT]). Stored by name in the profile ([Profile.level]).
 */
enum class Level(
    /** The grown-up's answer, short. */
    val label: String,
    /** The grown-up's answer, honest: what "Never" and the rest mean. */
    val adultLine: String,
    /** A parent's answer for a kid; null: not offered for kids. */
    val kidLine: String?,
) {
    NEVER("Never", "I'm starting from the very first key", "First time at a piano"),
    A_LITTLE("A little", "I know some keys and can play a simple tune", "Has played a bit"),
    SOME("Some", "I play with both hands and read a bit", "Has had lessons"),
    A_LOT("A lot", "I'm here for songs and technique", null);

    /** "Never: I'm starting from the very first key". */
    val adultText: String get() = "$label: $adultLine"

    /** The level as a kid's course knows it: [A_LOT] is not offered to a parent, and counts as [SOME]. */
    val forKid: Level get() = if (this == A_LOT) SOME else this

    companion object {
        /** What a grown-up chooses between. */
        val adult: List<Level> = entries
        /** What a parent chooses between for a kid. */
        val kid: List<Level> = entries.filter { it.kidLine != null }

        /** The level named [name] (as stored); anything else, or nothing, is [NEVER]. */
        fun parse(name: String?): Level = entries.firstOrNull { it.name == name } ?: NEVER
        fun options(kind: ProfileKind): List<Level> = if (kind == ProfileKind.KID) kid else adult
    }
}

/** What the home shows first after a grown-up is placed: the course (Learn), or the song library for someone who is here for songs. */
enum class HomeFirst { COURSE, SONGS }

/**
 * Where [level] starts [kind]. [startUnitId] and [startLessonId] are the first unit and lesson to open; [skipLessonIds]
 * are marked "done, skipped" (not attempted: no stars, no XP, no event for the brief), in course order;
 * [skipUnitIds] are the units (a kid's pages) wholly inside them; [comeBackLessonIds] are lessons behind the start that stay
 * as they were, open to play and still to do (the concert hall's recital for a kid placed at the workshop);
 * [learnOpensAt] is the unit the Learn path scrolls to; [homeFirst] what the home shows first. [startTitle] and [openTitles] name
 * the start and what stays open ("Beginner", ["Basics"]), for the screens' words.
 */
data class PlacementPlan(
    val level: Level,
    val kind: ProfileKind,
    val startUnitId: String,
    val startLessonId: String,
    val skipLessonIds: List<String>,
    val skipUnitIds: List<String>,
    val comeBackLessonIds: List<String>,
    val homeFirst: HomeFirst,
    val learnOpensAt: String,
    val startTitle: String,
    val openTitles: List<String>,
) {
    /** Nothing is skipped: the plan for [Level.NEVER], where the course starts at its beginning. */
    val isFullCourse: Boolean get() = skipLessonIds.isEmpty()

    /** "Begin at Beginner. Basics stays open." / "Begin at Basics." (a kid: "Begin at the pond. The garden stays open.") */
    val line: String get() = "Begin at $startTitle." + openLine().let { if (it.isEmpty()) "" else " $it" }

    /** "Basics stays open." / "Basics and Beginner stay open." / "" */
    fun openLine(): String = when (openTitles.size) {
        0 -> ""
        1 -> "${openTitles[0]} stays open."
        else -> "${openTitles.dropLast(1).joinToString(", ")} and ${openTitles.last()} stay open."
    }
}

object Placement {
    /** The unit a grown-up of each level starts at (the main course; a Playground page is only ever an extra for them). */
    private val ADULT_START = mapOf(Level.NEVER to "basics", Level.A_LITTLE to "beginner", Level.SOME to "intermediate", Level.A_LOT to "keys")

    /** The page a kid of each level starts at: the garden, the pond, the workshop (the garden, pond and hall are skipped). */
    private val KID_START = mapOf(Level.NEVER to "garden", Level.A_LITTLE to "pond", Level.SOME to "workshop")

    /**
     * The first unit and lesson to open for [level], what is marked "done, skipped" and what the home shows first.
     * Kids (answered by a parent): first time at a piano starts in the garden; has played a bit at the pond (the garden
     * skipped); has had lessons at the workshop (the garden, the pond and the concert hall skipped; the hall's recital stays
     * as a "come back" stone). Grown-ups: never starts at Basics, a little at Beginner (Basics skipped), some at
     * Intermediate (Basics, Beginner and Rhythm skipped), a lot at Keys (everything before it skipped, Learn opens at Keys and
     * the home shows the song library first). A [reader] kid on a page the big kids' short list names lessons in starts at
     * the first of them.
     */
    fun startFor(level: Level, kind: ProfileKind, reader: Boolean = true, course: List<CourseLevel> = COURSE,
                 pages: List<PlaygroundPage> = PLAYGROUND_PAGES): PlacementPlan =
        if (kind == ProfileKind.KID) forKid(level.forKid, reader, course, pages) else forAdult(level, course)

    private fun forAdult(level: Level, course: List<CourseLevel>): PlacementPlan {
        val main = course.filter { !isPlaygroundLevel(it.id) }
        val start = main.firstOrNull { it.id == ADULT_START.getValue(level) } ?: main.first()
        val before = main.takeWhile { it.id != start.id }
        return PlacementPlan(
            level, ProfileKind.ADULT, start.id, start.lessons.first().id,
            skipLessonIds = before.flatMap { u -> u.lessons.map { it.id } },
            skipUnitIds = before.map { it.id },
            comeBackLessonIds = emptyList(),
            homeFirst = if (level == Level.A_LOT) HomeFirst.SONGS else HomeFirst.COURSE,
            learnOpensAt = start.id,
            startTitle = start.title,
            openTitles = before.map { it.title },
        )
    }

    private fun forKid(level: Level, reader: Boolean, course: List<CourseLevel>, pages: List<PlaygroundPage>): PlacementPlan {
        val startPage = pages.firstOrNull { it.id == KID_START.getValue(level) } ?: pages.first()
        val atPage = pages.indexOf(startPage)
        val before = pages.take(atPage)
        // The concert hall's recital is the one lesson of a skipped page that stays: a stone to come back to.
        val comeBack = if (level == Level.SOME) pages.firstOrNull { it.id == "hall" }?.takeIf { it in before }?.lastLessonId?.let { listOf(it) }.orEmpty() else emptyList()
        val skip = before.flatMap { it.lessonIds }.filter { it !in comeBack }
        val shortList = reader && startPage.lessonIds.any { it in BIG_KID_LESSONS }
        val first = startPage.lessonIds.firstOrNull { !shortList || it in BIG_KID_LESSONS } ?: startPage.lessonIds.first()
        return PlacementPlan(
            level, ProfileKind.KID, startPage.levelId, first,
            skipLessonIds = skip,
            skipUnitIds = before.filter { page -> page.lessonIds.all { it in skip } }.map { it.levelId },
            comeBackLessonIds = comeBack,
            homeFirst = HomeFirst.COURSE,
            learnOpensAt = startPage.levelId,
            startTitle = KidPages.inSentence(startPage),
            openTitles = before.map { KidPages.inSentence(it).replaceFirstChar { c -> c.uppercase() } },
        )
    }

    /** The id the check's first-lesson rewards are kept under (its stars, the XP event, the world's sticker): not a course lesson. */
    const val CHECK_ID = "placement-check"

    private val plans = HashMap<Triple<Level, ProfileKind, Boolean>, PlacementPlan>()

    /** [startFor] on the app's own course, remembered (the path asks on every look). */
    fun planFor(level: Level, kind: ProfileKind, reader: Boolean = true): PlacementPlan = synchronized(plans) {
        plans.getOrPut(Triple(if (kind == ProfileKind.KID) level.forKid else level, kind, reader)) { startFor(level, kind, reader) }
    }

    /** The plan of [who]'s level. */
    fun planOf(who: Profile): PlacementPlan = planFor(who.level, who.kind, who.reader)

    /**
     * The lesson suggestions begin at for [who] ([com.bigpapituna.sharpears.engine.CoursePath]'s startAt): their plan's start,
     * or null for a first time (the course's own beginning, and the older "Have you played before?" answer if it was given).
     */
    fun startLessonId(who: Profile): String? = who.level.takeIf { it != Level.NEVER }?.let { planOf(who).startLessonId }

    /**
     * What applying [plan] to someone's marks changes: [Reconcile.skip] the plan's lessons that are neither played nor
     * skipped yet (marked "done, skipped"), and [Reconcile.reopen] lessons skipped before that this plan no longer skips (a
     * lower level chosen later). Played lessons, stars and finales are never in either list: a plan never undoes progress.
     * [done] says a lesson is done (played or skipped), [skipped] which are skipped marks.
     */
    fun reconcile(plan: PlacementPlan, done: (String) -> Boolean, skipped: Set<String>): Reconcile {
        val wanted = plan.skipLessonIds.toSet()
        return Reconcile(skip = plan.skipLessonIds.filter { !done(it) }, reopen = skipped.filter { it !in wanted }.sorted())
    }

    data class Reconcile(val skip: List<String>, val reopen: List<String>) {
        val changes: Boolean get() = skip.isNotEmpty() || reopen.isNotEmpty()
    }
}
