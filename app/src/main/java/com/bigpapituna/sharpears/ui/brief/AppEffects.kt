package com.bigpapituna.sharpears.ui.brief

import android.content.Context
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.Profiles
import com.bigpapituna.sharpears.engine.brief.Effects
import com.bigpapituna.sharpears.engine.brief.NotConnected
import com.bigpapituna.sharpears.engine.brief.SettingId
import com.bigpapituna.sharpears.engine.brief.Undo
import com.bigpapituna.sharpears.ui.skin.BedtimeStore

/**
 * What the action cards do on this phone for one profile: the five SETTING actions are real here (the same stores
 * Settings > Family and Microphone setup write, with an Undo that puts the old value back). The rest of the menu needs
 * wiring only the integrator can do (the kid map's next stone, page pacing, the shelf, the duet and lesson navigation), so
 * those throw [NotConnected] and the card says so; ui/brief/INTEGRATION.md lists each one with where it goes.
 */
class AppEffects(private val context: Context, private val profiles: Profiles?, private val who: com.bigpapituna.sharpears.engine.family.Profile) : Effects {
    override fun setNext(lessonId: String?, untilMs: Long): Undo? = throw NotConnected("setNext")
    override fun replay(lessonId: String) = throw NotConnected("replay")
    override fun practiseTogether(lessonId: String?, songId: String?) = throw NotConnected("practiseTogether")
    override fun freshStart(pageId: String): Undo? = throw NotConnected("freshStart")
    override fun focus(pageId: String): Undo? = throw NotConnected("focus")
    override fun unlockNext(pageId: String): Undo? = throw NotConnected("unlockNext")
    override fun assignSong(songId: String, note: String, afterSongId: String?): Undo? = throw NotConnected("assignSong")

    override fun changeSetting(setting: SettingId, value: String): Undo? {
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
}
