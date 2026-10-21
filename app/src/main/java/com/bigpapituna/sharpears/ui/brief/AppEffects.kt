package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.KidStore
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.data.SongRepository
import com.bigpapituna.sharpears.engine.CoursePath
import com.bigpapituna.sharpears.engine.brief.Effects
import com.bigpapituna.sharpears.engine.brief.NotConnected
import com.bigpapituna.sharpears.engine.brief.PathEffects
import com.bigpapituna.sharpears.engine.brief.PathMemory
import com.bigpapituna.sharpears.engine.brief.PathNavigator
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.SongChoice
import com.bigpapituna.sharpears.engine.brief.Undo
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.kid.KidMap
import com.bigpapituna.sharpears.engine.kid.Pace
import com.bigpapituna.sharpears.engine.kid.Pin
import com.bigpapituna.sharpears.engine.kid.ShelfSong
import com.bigpapituna.sharpears.ui.skin.BedtimeStore

/**
 * Where the brief's actions go in the app (MainActivity provides it as [LocalBriefNav]): screens to open for a person
 * (the app switches to them first when someone else is open), and a word that a person's walk changed under the open
 * screens (the kid map and Learn read their stores again). Calls may come from any thread.
 */
interface BriefNav {
    /** Opens [lessonId] for [who]. */
    fun openLesson(who: Profile, lessonId: String)
    /** Opens [songId] for [who]; [duet]: with a grown-up on the left hand (a grown-up's own week: the duet picker). */
    fun openSong(who: Profile, songId: String, duet: Boolean)
    /** Opens [who]'s songs to pick one; [duet]: the one picked opens as a duet. */
    fun pickSong(who: Profile, duet: Boolean)
    /** [who]'s path, pace or shelf changed (an action or its undo). */
    fun changed(who: Profile)
}

/** The activity's [BriefNav]; null (the debug demo, previews) leaves the opening actions "not connected". */
val LocalBriefNav = staticCompositionLocalOf<BriefNav?> { null }

/**
 * What the action cards do on this phone for [who] (ui/brief/INTEGRATION.md): the seven actions of [PathEffects] over
 * [who]'s own stores (KidStore's pin, pace and shelf, Prefs' done marks, KidStore's stars), the screens through [nav], and
 * the five SETTING actions on the stores Settings > Family and Microphone setup write, each with an Undo that puts the old
 * value back.
 */
class AppEffects(
    private val context: Context, private val profiles: Profiles?, private val who: Profile, private val nav: BriefNav? = null,
) : Effects by PathEffects(
    memory = StoreMemory(context.applicationContext, who),
    navigator = nav?.let { n -> object : PathNavigator {
        override fun openLesson(lessonId: String) = n.openLesson(who, lessonId)
        override fun openSong(songId: String, duet: Boolean) = n.openSong(who, songId, duet)
        override fun pickSong(duet: Boolean) = n.pickSong(who, duet)
    } },
    settings = { s, v -> changeSetting(context.applicationContext, profiles, who, s, v) },
    songs = { teacherSongs(context.applicationContext) },
    changed = { nav?.changed(who) },
)

/** [who]'s walk on this phone: their KidStore and Prefs (the same files the app reads as them). */
private class StoreMemory(private val app: Context, private val who: Profile) : PathMemory {
    private val kid = KidStore(app, who.id)
    private val prefs = Prefs(app, who.id)
    override var pin: Pin? by kid::pin
    override var focus: String? by kid::focus
    override var early: Set<String> by kid::early
    override var shelf: List<ShelfSong> by kid::shelf
    override fun done(lessonId: String) = prefs.lessonDone(lessonId)
    override fun stars(lessonId: String) = kid.stars(lessonId)
    override fun forget(lessonIds: Collection<String>) { lessonIds.forEach(prefs::forgetLessonDone); kid.forgetStars(lessonIds) }
    override fun restore(done: Set<String>, stars: Map<String, Int>) { done.forEach(prefs::markLessonDone); kid.restoreStars(stars) }
    override fun next(): String? {
        val path = CoursePath(who.firstUnitId, bigKid = who.isKid && who.reader,
            startAt = { runCatching { ProgressStore(app, prefs).experience?.startLessonId }.getOrNull() })
        return if (who.isKid) KidMap.next(path, prefs::lessonDone, kid.finales(), pace = kid.pace().copy(pin = null), stars = kid::stars)?.id
        else path.next(prefs::lessonDone)?.id
    }
}

/** Songs to offer in Teacher mode: the app's own, the easy ones first (a kid's next song), then the rest by difficulty. */
private fun teacherSongs(app: Context): List<SongChoice> {
    val order = listOf("easy", "medium", "hard")
    return runCatching { SongRepository(app).all() }.getOrDefault(emptyList())
        .filter { !it.user && it.difficulty in order }
        .sortedBy { order.indexOf(it.difficulty) }
        .map { SongChoice(it.id, it.title) }
}

/** The five one-tap settings, each with an Undo that puts the old value back. */
private fun changeSetting(context: Context, profiles: Profiles?, who: Profile, setting: SettingId, value: String): Undo? {
    val on = value != "false"
    val p = profiles ?: throw NotConnected("profiles")
    val before = p.all.firstOrNull { it.id == who.id } ?: who
    return when (setting) {
        SettingId.READER -> { p.update(who.id) { it.copy(reader = on) }; Undo { p.update(who.id) { it.copy(reader = before.reader) } } }
        SettingId.COLOUR_NOTES -> { p.update(who.id) { it.copy(colourNotes = on) }; Undo { p.update(who.id) { it.copy(colourNotes = before.colourNotes) } } }
        SettingId.MIC_EASIER -> {
            val to = value.toDoubleOrNull() ?: (before.micOffsetDb + SettingId.MIC_STEP_DB)
            p.update(who.id) { it.copy(micOffsetDb = to) }
            Undo { p.update(who.id) { it.copy(micOffsetDb = before.micOffsetDb) } }
        }
        SettingId.BEDTIME -> {
            val store = BedtimeStore(context, who.id)
            val was = store.bedtime
            store.bedtime = on
            Undo { store.bedtime = was }
        }
        SettingId.QUIET_ROOM -> {
            // The microphone setup is the phone's ("mic", MicInput.wrongFlash); quietRoom stops painting wrong keys.
            val mic = context.getSharedPreferences("mic", Context.MODE_PRIVATE)
            val was = mic.getBoolean("wrongFlash", true)
            mic.edit().putBoolean("wrongFlash", false).apply()
            Briefs.settingFor(who.id, "wrongFlash", false)
            Undo { mic.edit().putBoolean("wrongFlash", was).apply(); Briefs.settingFor(who.id, "wrongFlash", was) }
        }
    }
}
