package com.bigpapituna.sharpears.analytics

import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.tester.AppEvents
import com.bigpapituna.sharpears.engine.tester.Flags
import java.util.concurrent.ConcurrentHashMap

/**
 * 2.15, the app's own events, sent from the screens (the taxonomy and its builders: engine/tester/AppEvents.kt; the table
 * Roy reads: analytics/EVENTS.md). One line at each place, through [Tester.track]: nothing goes out unless Help Roy test is
 * on, on a build with the PostHog key. The events made from the brief's own log (lesson steps, songs, chord charts, the
 * arcade, a session's length and practice, the layout, the daily time, the piano level) come from TesterTally instead.
 */
object Events {
    fun sightDrillDone(level: Int, score: Int, staff: Boolean) = Tester.track(AppEvents.sightDrillDone(level, score, if (staff) "staff" else "piece"))
    fun dailyPlanStarted(parts: Int) = Tester.track(AppEvents.dailyPlanStarted(parts))
    fun dailyPlanFinished(done: Int, of: Int, practiceSec: Long, complete: Boolean) = Tester.track(AppEvents.dailyPlanFinished(done, of, practiceSec, complete))
    fun onboardingStep(step: Int, completed: Boolean) = Tester.track(AppEvents.onboardingStep(step, completed))
    fun bigKidMade() = Tester.track(AppEvents.bigKidMade())
    fun familyCardOpened() = Tester.track(AppEvents.familyCardOpened())
    fun briefEmailSetUp(yes: Boolean) = Tester.track(AppEvents.briefEmailSetUp(yes))
    fun timeCapReached(minutes: Int) = Tester.track(AppEvents.timeCapReached(minutes))
    fun gateOpened() = Tester.track(AppEvents.gateOpened())
    fun gateFailed() = Tester.track(AppEvents.gateFailed())
    fun tourCardSeen(which: String) = Tester.track(AppEvents.tourCardSeen(which))
    fun micPermission(granted: Boolean) = Tester.track(AppEvents.micPermission(granted))
    /** A library song the app played through for the listener (Listen mode keeps no run, so the brief has no event). */
    fun songListened(songId: String) = Tester.track(AppEvents.songPlayed(songId, "listen", stars = null, accuracy = null))
    /** Hear the band on a chord chart's page. */
    fun chartHeard(chart: String) = Tester.track(AppEvents.chartHeard(chart))
}

/**
 * 2.15, the feature flags Roy runs experiments with (engine/tester/Flags.kt has the names, the defaults and the reading).
 * Read from PostHog only while the dashboard records; a value is kept for the rest of the profile's visit once it came
 * ([Tester.profile] forgets them), and until one comes the safe local default stands.
 */
object AppFlags {
    private val values = ConcurrentHashMap<String, Any>()

    private fun raw(key: String): Any? = values[key] ?: Tester.flag(key)?.also { values[key] = it }

    /** The plain-mode branch's one-time Plain look offer shows (default yes). */
    val plainLookOffer: Boolean get() = Flags.plainLookOffer(raw(Flags.PLAIN_LOOK_OFFER))

    /** Encouragement for a profile that never chose (default Often): data/Prefs.cheerFrequency. */
    val cheerFrequencyDefault: CheerFrequency get() = Flags.cheerFrequencyDefault(raw(Flags.CHEER_FREQUENCY_DEFAULT))

    /** Another profile opened: its flags are read afresh. */
    internal fun forget() = values.clear()
}
