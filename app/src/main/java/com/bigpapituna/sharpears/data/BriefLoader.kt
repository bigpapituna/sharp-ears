package com.bigpapituna.sharpears.data

import android.content.Context
import com.bigpapituna.sharpears.engine.brief.CurrentSettings
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Insights
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.ui.skin.BedtimeStore

/**
 * Findings for one person from their event log (engine/brief/INTEGRATION.md): everything kept, plus the open session for a
 * session note, with the settings the rules look at (the daily goal, bedtime, whether wrong keys are painted). Reads files:
 * call it off the main thread.
 */
object BriefLoader {
    fun findings(context: Context, profile: Profile, period: Period, now: Long = System.currentTimeMillis()): Findings {
        val app = context.applicationContext
        // The open session's provisional end is stamped [now], the findings' own clock (one stamped later is left out).
        val events = Briefs.read(profile.id) + listOfNotNull(Briefs.sessionSoFar(profile.id, at = now))
        val current = CurrentSettings(
            goalMinutes = runCatching { ProgressStore(app, Prefs(app, profile.id)).goalMinutes }.getOrNull(),
            bedtimeOn = runCatching { BedtimeStore(app, profile.id).bedtime }.getOrNull(),
            // The microphone setup is the phone's, not a profile's (file "mic", MicInput.wrongFlash, default on).
            wrongKeysShown = runCatching { app.getSharedPreferences("mic", Context.MODE_PRIVATE).getBoolean("wrongFlash", true) }.getOrNull(),
        )
        return Insights.compute(events, profile, period, now, current = current)
    }
}
