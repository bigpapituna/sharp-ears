package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.kid.Pin
import com.bigpapituna.sharpears.engine.kid.Shelf
import com.bigpapituna.sharpears.engine.kid.ShelfSong
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven actions of the parent brief over a child's walk (2.4 integration, engine/brief/PathEffects.kt): one test per
 * effect, each with its undo, through [BriefActions] as the action cards run them.
 */
class PathEffectsTest {
    private val now = 1_792_000_000_000L
    private val pond = PLAYGROUND_PAGES.first { it.id == "pond" }

    /** A child's walk in memory. */
    private class Memory(var nextId: String? = "pond-5") : PathMemory {
        override var pin: Pin? = null
        override var focus: String? = null
        override var early: Set<String> = emptySet()
        override var shelf: List<ShelfSong> = emptyList()
        val done = HashSet<String>()
        val stars = HashMap<String, Int>()
        override fun done(lessonId: String) = lessonId in done
        override fun stars(lessonId: String) = stars[lessonId] ?: 0
        override fun forget(lessonIds: Collection<String>) { done.removeAll(lessonIds.toSet()); lessonIds.forEach { stars.remove(it) } }
        override fun restore(done: Set<String>, stars: Map<String, Int>) { this.done += done; this.stars += stars }
        override fun next() = nextId
    }

    /** Where the screens would have gone. */
    private class Screens : PathNavigator {
        val opened = ArrayList<String>()
        override fun openLesson(lessonId: String) { opened += "lesson $lessonId" }
        override fun openSong(songId: String, duet: Boolean) { opened += "song $songId" + if (duet) " duet" else "" }
        override fun pickSong(duet: Boolean) { opened += "pick" + if (duet) " duet" else "" }
    }

    private val memory = Memory()
    private val screens = Screens()
    private var changes = 0
    private val recorded = ArrayList<Pair<String, Map<String, String>>>()
    private val effects = PathEffects(memory, screens, settings = { _, _ -> null },
        songs = { listOf(SongChoice("twinkle", "Twinkle"), SongChoice("ode-to-joy", "Ode to Joy")) }, clock = { now }, changed = { changes++ })
    private val actions = BriefActions(effects, IdTitles, "Mia", record = { id, p -> recorded += id to p }, clock = { now })

    private fun perform(c: Candidate, input: ActionInput = ActionInput()): Performed {
        val o = actions.perform(c, input)
        assertTrue("$o", o is Outcome.Done)
        return (o as Outcome.Done).performed
    }

    @Test fun setAsNextPinsTheLessonForADayAndUndoTakesThePinAway() {
        memory.pin = Pin("garden-2", now - 1)                       // an old pin, past its day
        val done = perform(Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-7"), FlagKind.STUCK_LESSON))
        assertEquals(Pin("pond-7", now + BriefActions.DAY_MS), memory.pin)
        assertTrue(memory.pin!!.activeAt(now) && !memory.pin!!.activeAt(now + BriefActions.DAY_MS))
        assertEquals(1, changes)
        assertTrue(done.undo())
        assertEquals("the earlier pin is back as it was", Pin("garden-2", now - 1), memory.pin)
        assertEquals(2, changes)
        done.commit()
        assertTrue("an undone action is never recorded", recorded.isEmpty())
    }

    @Test fun setAsNextWithNoLessonPinsThePathsNextOne() {
        val done = perform(Candidate(RecommendationKind.MOVE_ON, ActionId.SET_NEXT, emptyMap(), FlagKind.REPLAYING_NOT_MOVING_ON))
        assertEquals("pond-5", memory.pin?.lessonId)
        done.commit()
        assertEquals(listOf(ActionId.SET_NEXT.name to emptyMap<String, String>()), recorded)
    }

    @Test fun setAsNextWithNothingLeftOnThePathFailsAndChangesNothing() {
        memory.nextId = null
        val o = actions.perform(Candidate(RecommendationKind.NEXT_STEP, ActionId.SET_NEXT, emptyMap(), FlagKind.READY_FOR_ROAD))
        assertTrue(o is Outcome.Failed)
        assertNull(memory.pin)
        assertEquals(0, changes)
    }

    @Test fun replayForStarsOpensTheLessonAndIsRecordedAtOnce() {
        val done = perform(Candidate(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, mapOf("lesson" to "pond-3"), FlagKind.LOW_STARS))
        assertEquals(listOf("lesson pond-3"), screens.opened)
        assertTrue(!done.reversible && done.committed)
        assertEquals(1, recorded.size)
    }

    @Test fun practiseTogetherOpensTheSongAsADuetTheLessonOrTheSongPick() {
        perform(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("song" to "twinkle"), FlagKind.LEFT_HAND_BEHIND))
        perform(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("lesson" to "pond-5"), FlagKind.STUCK_LESSON))
        perform(Candidate(RecommendationKind.ROUTINE, ActionId.PRACTISE_TOGETHER, emptyMap(), FlagKind.PRACTICE_DIPPED))
        assertEquals(listOf("song twinkle duet", "lesson pond-5", "pick duet"), screens.opened)
    }

    @Test fun withoutTheAppsScreensTheOpeningActionsSayNotConnected() {
        val bare = BriefActions(PathEffects(memory, navigator = null, settings = { _, _ -> null }), IdTitles, "Mia", record = { _, _ -> })
        val o = bare.perform(Candidate(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, mapOf("lesson" to "pond-3"), FlagKind.LOW_STARS))
        assertEquals(Outcome.Failed("This one is not connected in this version yet. Nothing was changed."), o)
    }

    @Test fun freshStartForgetsThePagesStarsAndDoneMarksAndUndoPutsExactlyThoseBack() {
        memory.done += listOf("pond-1", "pond-2", "pond-3", "garden-12")
        memory.stars += mapOf("pond-1" to 3, "pond-2" to 1, "pond-3" to 2, "garden-12" to 3)
        val done = perform(Candidate(RecommendationKind.FOCUS, ActionId.FRESH_START, mapOf("page" to "pond"), FlagKind.PAGE_STRUGGLE))
        assertTrue(pond.lessonIds.none(memory::done))
        assertTrue(pond.lessonIds.all { memory.stars(it) == 0 })
        assertTrue("another page is untouched", memory.done("garden-12") && memory.stars("garden-12") == 3)
        assertTrue(done.undo())
        assertEquals(setOf("pond-1", "pond-2", "pond-3", "garden-12"), memory.done)
        assertEquals(mapOf("pond-1" to 3, "pond-2" to 1, "pond-3" to 2, "garden-12" to 3), memory.stars)
    }

    @Test fun focusHidesLaterPagesUntilUndone() {
        memory.focus = "garden"
        val done = perform(Candidate(RecommendationKind.FOCUS, ActionId.PACE_FOCUS, mapOf("page" to "pond"), FlagKind.SCATTERED))
        assertEquals("pond", memory.focus)
        done.undo()
        assertEquals("garden", memory.focus)
    }

    @Test fun anUnknownPageFailsWithoutAChange() {
        val o = actions.perform(Candidate(RecommendationKind.FOCUS, ActionId.PACE_FOCUS, mapOf("page" to "moon"), FlagKind.SCATTERED))
        assertTrue(o is Outcome.Failed)
        assertNull(memory.focus)
        assertEquals(0, changes)
    }

    @Test fun openTheNextPageEarlyAddsThePageAndLiftsAFocusOnIt() {
        memory.early = setOf("garden")
        memory.focus = "pond"
        val done = perform(Candidate(RecommendationKind.NEXT_STEP, ActionId.PACE_UNLOCK, mapOf("page" to "pond"), FlagKind.CRUISING))
        assertEquals(setOf("garden", "pond"), memory.early)
        assertNull("focus on the same page would hide the page just opened", memory.focus)
        done.undo()
        assertEquals(setOf("garden"), memory.early)
        assertEquals("pond", memory.focus)
    }

    @Test fun teacherModePutsTheSongOnTopOfTheShelfWithTheNoteAndUndoRestoresTheShelf() {
        val old = listOf(ShelfSong("twinkle", "Old note", now - 5_000))
        memory.shelf = old
        assertEquals("the mastered song is not offered", listOf("twinkle"), effects.songChoices("ode-to-joy").map { it.id })
        val done = perform(Candidate(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "hot-cross-buns"), FlagKind.MASTERED_SONG),
            ActionInput(song = "ode-to-joy", note = "  Try the first line slowly.  "))
        assertEquals(ShelfSong("ode-to-joy", "Try the first line slowly.", now, after = "hot-cross-buns"), memory.shelf.first())
        assertEquals(2, memory.shelf.size)
        done.undo()
        assertEquals(old, memory.shelf)
    }

    @Test fun teacherModeWithoutASongPickedFailsWithoutAChange() {
        val o = actions.perform(Candidate(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, mapOf("after" to "twinkle"), FlagKind.MASTERED_SONG))
        assertEquals(Outcome.Failed("Pick a song first."), o)
        assertTrue(memory.shelf.isEmpty())
    }

    @Test fun aSettingGoesToTheSettingsWithItsValue() {
        val asked = ArrayList<Pair<SettingId, String>>()
        val e = PathEffects(memory, screens, settings = { s, v -> asked += s to v; Undo { asked += s to "undo" } })
        val p = (BriefActions(e, IdTitles, "Mia", record = { _, _ -> }).perform(
            Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "reader", "value" to "true"), FlagKind.READY_FOR_LETTERS)) as Outcome.Done).performed
        p.undo()
        assertEquals(listOf(SettingId.READER to "true", SettingId.READER to "undo"), asked)
    }

    @Test fun theShelfKeepsTheNewestThreeOncePerSongAndSurvivesItsJson() {
        var shelf = emptyList<ShelfSong>()
        listOf("a", "b", "c", "a", "d").forEachIndexed { i, id -> shelf = Shelf.add(shelf, ShelfSong(id, "note $i", now + i)) }
        assertEquals(listOf("d", "a", "c"), shelf.map { it.songId })
        assertEquals("the newer note for the same song", "note 3", shelf[1].note)
        val tricky = listOf(ShelfSong("e", "with \"quotes\", commas\nand lines", now, after = "x"), shelf[0], shelf[1])
        assertEquals(tricky, Shelf.decode(Shelf.encode(tricky)))
        assertTrue(Shelf.decode("not json").isEmpty())
        assertTrue(Shelf.decode(null).isEmpty())
        assertEquals(listOf("ok"), Shelf.decode("""[{"note":"no song"},{"song":"ok"}]""").map { it.songId })
    }
}
