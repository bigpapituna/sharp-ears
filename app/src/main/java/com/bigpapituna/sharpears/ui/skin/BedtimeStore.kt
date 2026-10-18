package com.bigpapituna.sharpears.ui.skin

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.bigpapituna.sharpears.data.ProgressFiles
import com.bigpapituna.sharpears.ui.family.LocalProfile
import java.time.LocalDate

/**
 * The goodbye's two dates, per profile, in the skin team's own preferences file ("skin", suffixed per profile like
 * every per-person file): the last day the goodnight was shown, and the last day a grown-up let one more lesson
 * through. The integrator adds "skin" to ProgressFiles.NAMES so backup and profile deletion cover it.
 */
class BedtimeStore(context: Context, profileId: String) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)

    var goodnightOn: LocalDate?
        get() = day("goodnightOn")
        set(v) = put("goodnightOn", v)

    var oneMoreOn: LocalDate?
        get() = day("oneMoreOn")
        set(v) = put("oneMoreOn", v)

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
