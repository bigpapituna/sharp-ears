package com.bigpapituna.sharpears.data

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.progress.Achievement
import com.bigpapituna.sharpears.engine.progress.Achievements
import com.bigpapituna.sharpears.engine.progress.DailyGoal
import com.bigpapituna.sharpears.engine.progress.Experience
import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import com.bigpapituna.sharpears.engine.progress.Progression
import com.bigpapituna.sharpears.engine.progress.Snapshot
import com.bigpapituna.sharpears.engine.progress.StreakResult
import com.bigpapituna.sharpears.engine.progress.Streaks
import com.bigpapituna.sharpears.engine.progress.WeeklyGoal
import com.bigpapituna.sharpears.engine.progress.Xp
import java.time.LocalDate

/** What one [ProgressStore.record] did: the XP it earned, a level gained, achievements unlocked. */
data class RecordResult(val xpGained: Int, val levelUp: LevelUp?, val unlocked: List<Achievement>) {
    companion object { val NONE = RecordResult(0, null, emptyList()) }
}

/**
 * XP, level, achievements, goals and onboarding answers, in their own SharedPreferences file ("profile"; Prefs keeps
 * "progress"). Report what the learner does with [record]; it adds XP, finds new achievements and a level gained,
 * and queues them in [toasts] and [levelUp] for the overlays at the root of the app.
 *
 * Song, lesson and game facts that Prefs already holds (songs completed, best scores, lessons done) are read from
 * [prefs] through its public API when a [Snapshot] is built, so nothing is stored twice. Prefs forgets practice days
 * after 60 days; this store keeps its own log for [KEEP_DAYS] days so the 12-week heatmap and the streak have room.
 * [songIds] says which library songs exist (set it when the library is loaded).
 */
class ProgressStore(context: Context, private val prefs: Prefs, var songIds: () -> List<String> = { emptyList() }) {
    /** The same profile as [prefs]: "profile" for the owner, "profile.<id>" for everyone else. */
    private val p = context.getSharedPreferences(ProgressFiles.fileFor("profile", prefs.profileId), Context.MODE_PRIVATE)

    /** Bumps on every write, so screens that read the store recompose. */
    val version = mutableIntStateOf(0)

    private val xpState = mutableIntStateOf(p.getInt("xp", 0))
    val xp: Int get() = xpState.intValue
    val level: Int get() = Progression.levelFor(xp)

    /** Unlocked achievements and the day each came. */
    val unlocked = mutableStateMapOf<String, LocalDate>().also { m ->
        p.all.forEach { (k, v) -> if (k.startsWith("ach:") && v is String) runCatching { m[k.removePrefix("ach:")] = LocalDate.parse(v) } }
    }

    /** A level gained and not yet celebrated; [LevelUpOverlay][com.bigpapituna.sharpears.ui.profile.LevelUpOverlay] shows it. */
    val levelUp = mutableStateOf<LevelUp?>(null)

    /** Achievements waiting for a toast, oldest first. */
    val toasts = mutableStateListOf<Achievement>()

    // ---- settings and answers ----

    private val goalState = mutableIntStateOf(p.getInt("goalMinutes", DailyGoal.DEFAULT))
    var goalMinutes: Int
        get() = goalState.intValue
        set(v) { if (v != goalState.intValue) Briefs.settingFor(prefs.profileId, "goalMin", v); goalState.intValue = v; edit { putInt("goalMinutes", v) } }

    private val weeklyState = mutableIntStateOf(p.getInt("weeklyDays", WeeklyGoal.DEFAULT))
    var weeklyDays: Int
        get() = weeklyState.intValue
        set(v) { weeklyState.intValue = v; edit { putInt("weeklyDays", v) } }

    private val onboardingState = mutableStateOf(p.getBoolean("onboardingDone", false))
    /** The first-run flow has been finished (or skipped). Gate the flow on this. */
    var onboardingDone: Boolean
        get() = onboardingState.value
        set(v) { onboardingState.value = v; edit { putBoolean("onboardingDone", v) } }

    private val experienceState = mutableIntStateOf(p.getInt("experience", -1))
    /** "Have you played before?": 0 never, 1 a little, 2 some years, -1 not asked. */
    var experience: Experience?
        get() = Experience.fromOrdinal(experienceState.intValue)
        set(v) { experienceState.intValue = v?.ordinal ?: -1; edit { putInt("experience", v?.ordinal ?: -1) } }

    private val micState = mutableStateOf(p.getBoolean("useMic", false))
    /** The learner said they will play an acoustic piano into the microphone (false: the on-screen or a MIDI keyboard). */
    var useMic: Boolean
        get() = micState.value
        set(v) { micState.value = v; edit { putBoolean("useMic", v) } }

    // ---- practice log ----

    /** Seconds practised per day: this store's log joined with Prefs (the larger of the two for each day). */
    fun practiceLog(): Map<LocalDate, Int> {
        val own = p.all.mapNotNull { (k, v) ->
            if (!k.startsWith("day:") || v !is Int) null else runCatching { LocalDate.parse(k.removePrefix("day:")) to v }.getOrNull()
        }.toMap()
        val merged = own.toMutableMap()
        prefs.practice().forEach { (d, s) -> if (s > (merged[d] ?: 0)) merged[d] = s }
        return merged
    }

    fun secondsToday(today: LocalDate = LocalDate.now()): Int = practiceLog()[today] ?: 0

    /** Every second of practice this store has counted (the brief's sessions take the difference across a session). */
    val practiceSecondsTotal: Long get() = p.getLong("totalSeconds", 0)

    /** Settings > Reset progress clears practice time: this log too, or the goal ring, streak and calendar keep it. */
    fun clearPracticeLog() = edit { p.all.keys.filter { it.startsWith("day:") }.forEach(::remove) }

    /** "3 of 10 min today", for the home greeting. */
    fun goalLine(today: LocalDate = LocalDate.now()): String = DailyGoal.line(secondsToday(today), goalMinutes)

    fun streak(today: LocalDate = LocalDate.now()): StreakResult =
        Streaks.compute(practiceLog().filterValues { it > 0 }.keys, today)

    // ---- the snapshot ----

    fun snapshot(today: LocalDate = LocalDate.now()): Snapshot {
        val log = practiceLog()
        val days = log.filterValues { it > 0 }.keys
        val streak = Streaks.compute(days, today)
        val ids = songIds()
        val progress = ids.map { prefs.progress(it) }
        val lessons = COURSE.flatMap { it.lessons }
        val ownLessons = strings("lessons")
        fun lessonDone(id: String) = id in ownLessons || prefs.lessonDone(id)
        val completedSongs = ids.filterIndexed { i, _ -> progress[i].completed }.toSet() + strings("songs")
        return Snapshot(
            today = today,
            totalSeconds = maxOf(p.getLong("totalSeconds", 0), log.values.sumOf { it.toLong() }),
            bestDaySeconds = log.values.maxOrNull() ?: 0,
            nightOwl = p.getBoolean("nightOwl", false),
            earlyBird = p.getBoolean("earlyBird", false),
            comeback = p.getBoolean("comeback", false),
            daysGoalMet = p.getInt("daysGoalMet", 0),
            weeksGoalMet = WeeklyGoal.weeksMet(days, weeklyDays),
            level = level,
            streak = streak.streak,
            longestStreak = maxOf(streak.longest, p.getInt("longestStreak", 0)),
            freezesUsed = maxOf(streak.freezesUsed, p.getInt("freezesUsed", 0)),
            lessonsDone = lessons.count { lessonDone(it.id) },
            lessonsTotal = lessons.size,
            unitsComplete = COURSE.filter { u -> u.lessons.isNotEmpty() && u.lessons.all { lessonDone(it.id) } }.map { it.id }.toSet(),
            songsCompleted = completedSongs.size,
            performRuns = p.getInt("performRuns", 0),
            bestPerform = maxOf(p.getInt("bestPerform", -1), progress.maxOfOrNull { it.bestAccuracy } ?: -1),
            songsPolished = progress.count { it.bestAccuracy >= 90 },
            newBests = p.getInt("newBests", 0),
            dailySessions = p.getInt("dailySessions", 0),
            chordRounds = p.getInt("chordRounds", 0),
            gamesCompleted = p.getInt("gamesCompleted", 0),
            perfectGames = p.getInt("perfectGames", 0),
            rootsPlayed = strings("roots").mapNotNull { it.toIntOrNull() }.toSet(),
            earRounds = p.getInt("earRounds", 0),
            earCorrect = p.getInt("earCorrect", 0),
            theoryLessons = strings("theoryLessons").size,
            theoryUnits = strings("theoryUnits").size,
            sightReadRuns = p.getInt("sightReadRuns", 0),
            featuresUsed = strings("features"),
        )
    }

    // ---- recording ----

    /**
     * Counts [event]: adds its XP (and the daily-goal bonus when a practice event completes the day's goal), then looks for
     * a level gained and new achievements. Call it after Prefs has saved the same fact (markCompleted, saveScore,
     * markLessonDone), because the snapshot reads Prefs too.
     */
    fun record(event: ProgressEvent): RecordResult {
        var gain = Progression.xpFor(event)
        val features = mutableSetOf<String>()
        when (event) {
            is ProgressEvent.Practice -> gain += practice(event)
            ProgressEvent.LessonStepPassed -> features += "lesson"
            is ProgressEvent.LessonComplete -> {
                val first = addTo("lessons", event.lessonId)
                gain = Progression.xpFor(event, first)
                features += "lesson"
            }
            is ProgressEvent.SongRun -> {
                addTo("songs", event.songId)
                if (event.accuracy != null) {
                    bump("performRuns")
                    if (event.accuracy > p.getInt("bestPerform", -1)) edit { putInt("bestPerform", event.accuracy) }
                }
                if (event.newBest) bump("newBests")
                features += "song"
            }
            is ProgressEvent.ChordRound -> {
                bump("chordRounds")
                event.root?.let { addTo("roots", Math.floorMod(it, 12).toString()) }
                features += "chords"
            }
            is ProgressEvent.GameComplete -> {
                bump("gamesCompleted")
                if (event.rounds > 0 && event.score >= event.rounds) bump("perfectGames")
                features += "chords"
            }
            is ProgressEvent.TheoryLesson -> {
                gain = Progression.xpFor(event, addTo("theoryLessons", event.lessonId))
                features += "theory"
            }
            is ProgressEvent.TheoryUnit -> {
                gain = Progression.xpFor(event, addTo("theoryUnits", event.unitId))
                features += "theory"
            }
            is ProgressEvent.EarRound -> {
                bump("earRounds")
                if (event.correct) bump("earCorrect")
                features += "ear"
            }
            is ProgressEvent.SightRead -> { bump("sightReadRuns"); features += "sight" }
            is ProgressEvent.DailySession -> { bump("dailySessions"); features += "daily" }
            is ProgressEvent.FeatureUsed -> features += event.name
        }
        features.forEach { addTo("features", it) }
        return settle(gain)
    }

    /** Practice time: the log, time-of-day and comeback flags, and the daily goal bonus. Returns the bonus XP. */
    private fun practice(e: ProgressEvent.Practice): Int {
        if (e.seconds <= 0) return 0
        val day = e.at.toLocalDate()
        val before = p.getLong("totalSeconds", 0)      // XP counts this store's own total, so Prefs seconds are never paid twice
        val ownDay = p.getInt("day:$day", 0) + e.seconds
        val last = p.getString("lastPractice", null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        // The newest KEEP_DAYS practice days stay (counted, not dated): one practice with the clock set ahead must not
        // drop every real day before it.
        val stale = p.all.keys.filter { it.startsWith("day:") }
            .mapNotNull { k -> runCatching { LocalDate.parse(k.removePrefix("day:")) }.getOrNull()?.let { k to it } }
            .sortedByDescending { it.second }.drop(KEEP_DAYS.toInt()).map { it.first }
        edit {
            putLong("totalSeconds", before + e.seconds)
            putInt("day:$day", ownDay)
            putString("lastPractice", maxOf(last ?: day, day).toString())
            if (Achievements.isNightOwlHour(e.at.hour)) putBoolean("nightOwl", true)
            if (Achievements.isEarlyBirdHour(e.at.hour)) putBoolean("earlyBird", true)
            if (Streaks.isComeback(last, day)) putBoolean("comeback", true)
            stale.forEach { remove(it) }
        }
        // The streak and the freezes are worked out from the log; keep the best numbers so a pruned log cannot lower them.
        val s = streak(day)
        edit {
            if (s.longest > p.getInt("longestStreak", 0)) putInt("longestStreak", s.longest)
            if (s.freezesUsed > p.getInt("freezesUsed", 0)) putInt("freezesUsed", s.freezesUsed)
        }
        var bonus = Progression.practiceXp(before, before + e.seconds)
        // Prefs may already hold the same seconds (it is written first), so take the larger, not the sum.
        val dayTotal = maxOf(ownDay, prefs.practice()[day] ?: 0)
        if (DailyGoal.met(dayTotal, goalMinutes) && p.getString("goalPaid", null) != day.toString()) {
            edit { putString("goalPaid", day.toString()); putInt("daysGoalMet", p.getInt("daysGoalMet", 0) + 1) }
            bonus += Xp.DAILY_GOAL_MET
            Briefs.logFor(prefs.profileId) { com.bigpapituna.sharpears.engine.brief.BriefEvent.GoalReached(it, goalMinutes) }
        }
        return bonus
    }

    /** Adds [gain] XP, finds the level change and the new achievements, and queues them. */
    private fun settle(gain: Int): RecordResult {
        val before = xp
        if (gain > 0) { xpState.intValue = before + gain; edit { putInt("xp", before + gain) } } else version.intValue++
        val up = Progression.levelUp(before, gain)
        val fresh = Achievements.newlyUnlocked(snapshot(), unlocked.keys)
        if (fresh.isNotEmpty()) {
            val today = LocalDate.now()
            edit { fresh.forEach { putString("ach:${it.id}", today.toString()) } }
            fresh.forEach { unlocked[it.id] = today }
            toasts.addAll(fresh)
        }
        if (up != null) levelUp.value = levelUp.value?.let { LevelUp(it.from, up.to) } ?: up
        return RecordResult(gain, up, fresh)
    }

    /** The overlay was shown. */
    fun consumeLevelUp() { levelUp.value = null }

    /** The oldest toast was shown. */
    fun popToast() { if (toasts.isNotEmpty()) toasts.removeAt(0) }

    // ---- first run ----

    /**
     * Credits what the learner did before this store existed (practice kept by Prefs, lessons and songs done) as XP,
     * once, without a level-up moment or toasts. Call it after [songIds] is set.
     */
    fun catchUp() {
        if (p.getBoolean("caughtUp", false)) return
        val lessons = COURSE.flatMap { it.lessons }.filter { prefs.lessonDone(it.id) }
        val songs = songIds().filter { prefs.progress(it).completed }
        val seconds = prefs.practice().values.sumOf { it.toLong() }
        val xpBefore = xp
        val gain = Progression.practiceXp(0, seconds) + lessons.size * Xp.LESSON_COMPLETE + songs.size * Xp.SONG_RUN
        edit {
            putBoolean("caughtUp", true)
            putInt("xp", xpBefore + gain)
            putLong("totalSeconds", maxOf(p.getLong("totalSeconds", 0), seconds))
            prefs.practice().keys.maxOrNull()?.let { putString("lastPractice", it.toString()) }
            putStringSet("lessons", strings("lessons") + lessons.map { it.id })
            putStringSet("songs", strings("songs") + songs)
        }
        xpState.intValue = xpBefore + gain
        // Achievements already earned are marked quietly.
        val today = LocalDate.now()
        val fresh = Achievements.newlyUnlocked(snapshot(), unlocked.keys)
        edit { fresh.forEach { putString("ach:${it.id}", today.toString()) } }
        fresh.forEach { unlocked[it.id] = today }
    }

    // ---- helpers ----

    private fun strings(key: String): Set<String> = p.getStringSet(key, null)?.toSet() ?: emptySet()

    /** Adds [value] to the set at [key]; true when it was new. */
    private fun addTo(key: String, value: String): Boolean {
        val now = strings(key)
        if (value in now) return false
        edit { putStringSet(key, now + value) }
        return true
    }

    private fun bump(key: String) = edit { putInt(key, p.getInt(key, 0) + 1) }

    private inline fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        p.edit().apply(block).apply()
        version.intValue++
    }

    companion object {
        /** Days of practice kept in this store's own log. */
        const val KEEP_DAYS = 400L
    }
}
