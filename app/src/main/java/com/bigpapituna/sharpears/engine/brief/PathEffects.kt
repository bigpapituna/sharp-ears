package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.kid.Pin
import com.bigpapituna.sharpears.engine.kid.Shelf
import com.bigpapituna.sharpears.engine.kid.ShelfSong
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.PlaygroundPage

/**
 * One person's walk as the brief's actions change it (2.4): the pin, focus, pages opened early and the shelf (data/KidStore
 * on the phone), and the lessons' done marks and stars (Prefs and KidStore). A plain map in the tests.
 */
interface PathMemory {
    var pin: Pin?
    var focus: String?
    var early: Set<String>
    var shelf: List<ShelfSong>
    fun done(lessonId: String): Boolean
    fun stars(lessonId: String): Int
    /** Forgets [lessonIds]' done marks and stars (stickers and finales are kept elsewhere and stay). */
    fun forget(lessonIds: Collection<String>)
    /** Puts back what [forget] took: these done marks and best stars. */
    fun restore(done: Set<String>, stars: Map<String, Int>)
    /** The lesson the path suggests next right now, pin aside: what "Set as next" with no lesson pins. */
    fun next(): String?
}

/** Where the actions that open something go (the app's screens; switching to the child first when someone else is open). */
interface PathNavigator {
    /** Opens [lessonId] for the child (Replay for stars; a lesson to practise together, the grown-up sitting in). */
    fun openLesson(lessonId: String)
    /** Opens [songId]; [duet]: the grown-up on the left hand and the child on the right. */
    fun openSong(songId: String, duet: Boolean)
    /** Opens the child's songs to pick from; [duet]: the song picked opens as a duet. */
    fun pickSong(duet: Boolean)
}

/**
 * The seven actions of the brief over a [PathMemory] and a [PathNavigator] (CONTRACT.md, "The action menu"): pure, so each
 * is tested on the JVM (PathEffectsTest); ui/brief/AppEffects.kt gives it the phone's stores, the screens, the settings.
 *
 * - Set as next pins a lesson (none: the path's next) until [Effects.setNext]'s time; the app clears the pin once it is
 *   played. Undo puts the earlier pin back.
 * - Fresh start forgets the page's stars and done marks; undo restores exactly those.
 * - Focus and Open the next page change the pace; undo restores it.
 * - Teacher mode puts the song on top of the shelf with the note; undo restores the shelf.
 * - Replay and Practise together open screens and cannot be undone.
 *
 * [changed] is called after every change and every undo (the open map reads its stores again).
 */
class PathEffects(
    private val memory: PathMemory,
    private val navigator: PathNavigator?,
    private val settings: (SettingId, String) -> Undo?,
    private val songs: () -> List<SongChoice> = { emptyList() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val pages: List<PlaygroundPage> = PLAYGROUND_PAGES,
    private val changed: () -> Unit = {},
) : Effects {
    private fun nav() = navigator ?: throw NotConnected("navigation")
    private fun page(id: String) = pages.firstOrNull { it.id == id } ?: throw IllegalArgumentException("no page $id")

    /** Changes something, tells the app, and gives the undo that puts [restore] back and tells it again. */
    private fun change(apply: () -> Unit, restore: () -> Unit): Undo { apply(); changed(); return Undo { restore(); changed() } }

    override fun setNext(lessonId: String?, untilMs: Long): Undo? {
        val id = lessonId ?: memory.next() ?: throw IllegalStateException("nothing left on the path to pin")
        val before = memory.pin
        return change({ memory.pin = Pin(id, untilMs) }, { memory.pin = before })
    }

    override fun replay(lessonId: String) = nav().openLesson(lessonId)

    override fun practiseTogether(lessonId: String?, songId: String?) = when {
        songId != null -> nav().openSong(songId, duet = true)
        lessonId != null -> nav().openLesson(lessonId)
        else -> nav().pickSong(duet = true)
    }

    override fun freshStart(pageId: String): Undo? {
        val ids = page(pageId).lessonIds
        val done = ids.filter(memory::done).toSet()
        val stars = ids.associateWith(memory::stars).filterValues { it > 0 }
        return change({ memory.forget(ids) }, { memory.restore(done, stars) })
    }

    override fun focus(pageId: String): Undo? {
        page(pageId)
        val before = memory.focus
        return change({ memory.focus = pageId }, { memory.focus = before })
    }

    override fun unlockNext(pageId: String): Undo? {
        page(pageId)
        val before = memory.early
        // Opening the page after this one also lifts a focus that would hide it.
        val focusBefore = memory.focus
        return change({ memory.early = before + pageId; if (focusBefore == pageId) memory.focus = null },
            { memory.early = before; memory.focus = focusBefore })
    }

    override fun changeSetting(setting: SettingId, value: String): Undo? = settings(setting, value)

    override fun assignSong(songId: String, note: String, afterSongId: String?): Undo? {
        val before = memory.shelf
        return change({ memory.shelf = Shelf.add(before, ShelfSong(songId, note.trim().take(BriefActions.MAX_NOTE), clock(), afterSongId)) },
            { memory.shelf = before })
    }

    override fun songChoices(afterSongId: String?): List<SongChoice> = songs().filter { it.id != afterSongId }
}
