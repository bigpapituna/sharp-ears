package com.bigpapituna.sharpears.data

import android.content.Context
import androidx.core.content.edit
import com.bigpapituna.sharpears.engine.brief.WeeklySlot

/**
 * What the parent brief remembers for one profile, in its own preference file ("brief" for the owner, "brief.<id>" for
 * everyone else; in [ProgressFiles.NAMES], so backup, import and deleting a profile cover it): when the weekly brief goes
 * out ([weekly], default Sunday at 6 pm for a kid, off for a grown-up until they ask), and the session the child's card was
 * last shown for ([cardSession], so it shows once per session). The briefs themselves are not kept: they are written fresh
 * from the event log each time (and held in memory while a screen is open).
 */
class BriefStore(context: Context, val profileId: String) {
    private val p = context.getSharedPreferences(ProgressFiles.fileFor(FILE, profileId), Context.MODE_PRIVATE)

    /** The weekly slot; null when the grown-up has not chosen one ([weeklyOrDefault] gives the default then). */
    var weekly: WeeklySlot?
        get() = WeeklySlot.decode(p.getString(WEEKLY, null))
        set(v) = p.edit { if (v == null) remove(WEEKLY) else putString(WEEKLY, v.encode()) }

    /** The slot to use for a kid or a grown-up who has not chosen: Sunday 6 pm on for a kid, off for a grown-up. */
    fun weeklyOrDefault(kid: Boolean): WeeklySlot = weekly ?: WeeklySlot.DEFAULT.copy(on = kid)

    /** The session start (epoch ms) the child's card was last shown for. */
    var cardSession: Long
        get() = p.getLong(CARD_SESSION, 0L)
        set(v) = p.edit { putLong(CARD_SESSION, v) }

    /** When the weekly notification last went out (epoch ms), so a restart or a second alarm never sends it twice. */
    var lastNotice: Long
        get() = p.getLong(LAST_NOTICE, 0L)
        set(v) = p.edit { putLong(LAST_NOTICE, v) }

    companion object {
        /** The file name; in [ProgressFiles.NAMES]. */
        const val FILE = "brief"
        private const val WEEKLY = "weekly"
        private const val CARD_SESSION = "cardSession"
        private const val LAST_NOTICE = "lastNotice"
    }
}
