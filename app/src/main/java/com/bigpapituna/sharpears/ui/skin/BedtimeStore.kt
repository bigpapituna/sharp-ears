package com.bigpapituna.sharpears.ui.skin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.bigpapituna.sharpears.data.Briefs
import com.bigpapituna.sharpears.data.ProgressFiles
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.ui.family.LocalProfile
import java.time.LocalDate

/**
 * The goodbye's two dates, per profile, in the skin team's own preferences file ("skin", suffixed per profile like
 * every per-person file): the last day the goodnight was shown, and the last day a grown-up let one more lesson
 * through. The integrator adds "skin" to ProgressFiles.NAMES so backup and profile deletion cover it.
 */
class BedtimeStore(context: Context, private val profileId: String) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)

    var goodnightOn: LocalDate?
        get() = day("goodnightOn")
        set(v) { put("goodnightOn", v); if (v != null) Briefs.logFor(profileId) { BriefEvent.BedtimeShown(it) } }

    var oneMoreOn: LocalDate?
        get() = day("oneMoreOn")
        set(v) = put("oneMoreOn", v)

    /** The goal's cheer was said on this day (bedtime or not). */
    var wellDoneOn: LocalDate?
        get() = day("wellDoneOn")
        set(v) = put("wellDoneOn", v)

    /** Bedtime after the goal: a parental control, off unless a grown-up turns it on in Settings > Family. */
    var bedtime: Boolean
        get() = p.getBoolean("bedtime", false)
        set(v) { p.edit { putBoolean("bedtime", v) }; Briefs.settingFor(profileId, "bedtime", v) }

    /**
     * 2.14, "After the goal: wind down"'s second switch, "Close Songs and the Jam too": with [bedtime] on, the goal closes
     * Songs, the Jam and the games for the day as well (engine/skin/Bedtime.songsOpen). Off by default.
     */
    var closeSongs: Boolean
        get() = p.getBoolean("closeSongs", false)
        set(v) { p.edit { putBoolean("closeSongs", v) }; Briefs.settingFor(profileId, "bedtimeSongs", v) }

    /** 2.14, the daily time (engine/skin/TimeCap.kt): minutes on screen a day, 0 off (the default). */
    var capMin: Int
        get() = com.bigpapituna.sharpears.engine.skin.TimeCap.of(p.getInt("capMin", 0))
        set(v) { val m = com.bigpapituna.sharpears.engine.skin.TimeCap.of(v); p.edit { putInt("capMin", m) }; Briefs.settingFor(profileId, "capMin", m) }

    /** The day the daily time's goodbye was shown (once a day; after it the doors say "Tomorrow"). */
    var capShownOn: LocalDate?
        get() = day("capShownOn")
        set(v) { if (v != null && v != capShownOn) com.bigpapituna.sharpears.analytics.Events.timeCapReached(capMin); put("capShownOn", v) }

    /** Grown-up mode was offered once Beginner was done (2.1, integrator): either answer puts the card away. */
    var grownUpOffered: Boolean
        get() = p.getBoolean("grownUpOffered", false)
        set(v) = p.edit { putBoolean("grownUpOffered", v) }

    private fun day(key: String): LocalDate? = p.getLong(key, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
    private fun put(key: String, v: LocalDate?) = p.edit().apply { if (v == null) remove(key) else putLong(key, v.toEpochDay()) }.apply()

    companion object { const val FILE = "skin" }
}

/** The bedtime store of whoever is playing (rebuilt when the profile changes). */
@Composable
fun rememberBedtimeStore(): BedtimeStore {
    val context = LocalContext.current
    val id = LocalProfile.current.id
    return remember(context, id) { BedtimeStore(context.applicationContext, id) }
}
