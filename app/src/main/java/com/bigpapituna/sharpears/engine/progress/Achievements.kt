package com.bigpapituna.sharpears.engine.progress

import java.time.LocalDate

/**
 * Everything the achievements can look at, gathered by `ProgressStore.snapshot` from the progress store and Prefs.
 * Plain data, so every condition is tested without a phone.
 */
data class Snapshot(
    val today: LocalDate = LocalDate.of(2026, 1, 1),
    // Practice
    val totalSeconds: Long = 0,
    val bestDaySeconds: Int = 0,
    val nightOwl: Boolean = false,        // practised after 22:00
    val earlyBird: Boolean = false,       // practised before 07:00
    val comeback: Boolean = false,        // practised again after a week or more away
    val daysGoalMet: Int = 0,
    val weeksGoalMet: Int = 0,
    val level: Int = 1,
    // Streaks
    val streak: Int = 0,
    val longestStreak: Int = 0,
    val freezesUsed: Int = 0,
    // Lessons
    val lessonsDone: Int = 0,
    val lessonsTotal: Int = 0,
    /** Course unit ids with every lesson done. */
    val unitsComplete: Set<String> = emptySet(),
    // Songs
    val songsCompleted: Int = 0,
    val performRuns: Int = 0,
    /** Highest Perform score across all songs, or -1. */
    val bestPerform: Int = -1,
    /** Songs with a best score of 90 or more. */
    val songsPolished: Int = 0,
    val newBests: Int = 0,
    val dailySessions: Int = 0,
    // Chords and ear
    val chordRounds: Int = 0,
    val gamesCompleted: Int = 0,
    val perfectGames: Int = 0,
    /** Pitch classes (0..11) of chords played in the games. */
    val rootsPlayed: Set<Int> = emptySet(),
    val earRounds: Int = 0,
    val earCorrect: Int = 0,
    // Theory
    val theoryLessons: Int = 0,
    val theoryUnits: Int = 0,
    // Explorer
    val sightReadRuns: Int = 0,
    val featuresUsed: Set<String> = emptySet(),
) {
    val totalMinutes: Int get() = (totalSeconds / 60).toInt()
}

enum class AchievementCategory(val label: String) {
    PRACTICE("Practice"), SONGS("Songs"), LESSONS("Lessons"), CHORDS("Chords"),
    EAR("Ear"), THEORY("Theory"), STREAKS("Streaks"), EXPLORER("Explorer"),
}

/**
 * The picture on an achievement: an app drawable ("ic_piano") or one of the pup's poses ("STREAK"). The UI maps
 * these names; they are strings so this file stays free of Android.
 */
data class AchievementIcon(val name: String, val isPose: Boolean) {
    companion object {
        fun drawable(name: String) = AchievementIcon(name, false)
        fun pose(name: String) = AchievementIcon(name, true)
    }
}

/**
 * One achievement. It is earned when [measure] reaches [target]; the pair also gives the "42 of 100" shown on a
 * locked one. Yes or no conditions measure 0 or 1 against a target of 1.
 */
class Achievement(
    val id: String,
    val title: String,
    val description: String,
    /** What a locked one says it needs ("Practise 100 minutes"). */
    val hint: String,
    val category: AchievementCategory,
    val icon: AchievementIcon,
    val target: Int = 1,
    val measure: (Snapshot) -> Int,
) {
    fun unlocked(s: Snapshot) = measure(s) >= target
    /** 0..1 toward the target, for a progress bar. */
    fun fraction(s: Snapshot) = (measure(s).toFloat() / target).coerceIn(0f, 1f)
}

object Achievements {
    private fun flag(b: Boolean) = if (b) 1 else 0
    private fun drawable(n: String) = AchievementIcon.drawable(n)
    private fun pose(n: String) = AchievementIcon.pose(n)

    private fun a(id: String, title: String, description: String, hint: String, cat: AchievementCategory, icon: AchievementIcon,
                  target: Int = 1, measure: (Snapshot) -> Int) = Achievement(id, title, description, hint, cat, icon, target, measure)

    val ALL: List<Achievement> = listOf(
        // Practice
        a("first-note", "First note", "You played your first note. Every pianist started here.", "Practise for a moment",
            AchievementCategory.PRACTICE, pose("WELCOME")) { flag(it.totalSeconds > 0) },
        a("minutes-100", "100 minutes", "A hundred minutes at the keys.", "Practise 100 minutes in total",
            AchievementCategory.PRACTICE, drawable("ic_piano"), 100) { it.totalMinutes },
        a("minutes-500", "500 minutes", "Eight hours and twenty minutes of music.", "Practise 500 minutes in total",
            AchievementCategory.PRACTICE, drawable("ic_piano"), 500) { it.totalMinutes },
        a("minutes-1000", "1,000 minutes", "Over sixteen hours of practice. Your hands know the way.", "Practise 1,000 minutes in total",
            AchievementCategory.PRACTICE, pose("LEVEL_UP"), 1000) { it.totalMinutes },
        a("marathon", "Marathon", "Thirty minutes of practice in a single day.", "Practise 30 minutes in one day",
            AchievementCategory.PRACTICE, pose("PRACTICING"), 30) { it.bestDaySeconds / 60 },
        a("night-owl", "Night owl", "You practised after 10 pm.", "Practise after 10 pm",
            AchievementCategory.PRACTICE, pose("RESTING")) { flag(it.nightOwl) },
        a("early-bird", "Early bird", "You practised before 7 am.", "Practise before 7 am",
            AchievementCategory.PRACTICE, pose("GET_READY")) { flag(it.earlyBird) },
        a("comeback", "Comeback", "You came back after a week or more away. That takes heart.", "Return after a week away",
            AchievementCategory.PRACTICE, pose("GREAT_JOB")) { flag(it.comeback) },
        a("goal-1", "Goal met", "You reached your daily goal.", "Reach your daily goal",
            AchievementCategory.PRACTICE, drawable("ic_flag")) { it.daysGoalMet },
        a("goal-10", "Goal getter", "You reached your daily goal on 10 days.", "Reach your daily goal on 10 days",
            AchievementCategory.PRACTICE, drawable("ic_flag"), 10) { it.daysGoalMet },
        a("level-10", "Steady hands", "You reached level 10.", "Reach level 10",
            AchievementCategory.PRACTICE, pose("LEVEL_UP"), 10) { it.level },
        a("level-20", "Sight reader", "You reached level 20.", "Reach level 20",
            AchievementCategory.PRACTICE, pose("LEVEL_UP"), 20) { it.level },

        // Streaks
        a("streak-3", "Three in a row", "Three days of practice in a row.", "Practise 3 days in a row",
            AchievementCategory.STREAKS, pose("STREAK"), 3) { it.longestStreak },
        a("streak-7", "One week streak", "Seven days in a row. You earned a streak freeze.", "Practise 7 days in a row",
            AchievementCategory.STREAKS, pose("STREAK"), 7) { it.longestStreak },
        a("streak-14", "Two week streak", "Fourteen days in a row.", "Practise 14 days in a row",
            AchievementCategory.STREAKS, pose("STREAK"), 14) { it.longestStreak },
        a("streak-30", "Thirty day streak", "A whole month, every day.", "Practise 30 days in a row",
            AchievementCategory.STREAKS, pose("STREAK"), 30) { it.longestStreak },
        a("streak-100", "One hundred days", "A hundred days in a row. That is a habit now.", "Practise 100 days in a row",
            AchievementCategory.STREAKS, pose("LEVEL_UP"), 100) { it.longestStreak },
        a("freeze-used", "Saved by the freeze", "A streak freeze covered a missed day.", "Let a streak freeze save your streak",
            AchievementCategory.STREAKS, pose("RESTING")) { it.freezesUsed },
        a("week-goal", "Week well played", "You met your weekly goal.", "Meet your weekly goal",
            AchievementCategory.STREAKS, drawable("ic_repeat")) { it.weeksGoalMet },
        a("week-goal-4", "A good month", "You met your weekly goal in 4 weeks.", "Meet your weekly goal in 4 weeks",
            AchievementCategory.STREAKS, drawable("ic_repeat"), 4) { it.weeksGoalMet },

        // Songs
        a("song-1", "First song", "You played a song all the way through.", "Play a song to the end",
            AchievementCategory.SONGS, drawable("ic_library_music")) { it.songsCompleted },
        a("song-10", "Ten songs", "Ten songs played through.", "Play 10 songs to the end",
            AchievementCategory.SONGS, drawable("ic_library_music"), 10) { it.songsCompleted },
        a("song-25", "Repertoire", "Twenty five songs played through.", "Play 25 songs to the end",
            AchievementCategory.SONGS, pose("LEVEL_UP"), 25) { it.songsCompleted },
        a("perform-1", "Stage debut", "You finished a Perform run.", "Finish a song in Perform mode",
            AchievementCategory.SONGS, pose("PRACTICING")) { it.performRuns },
        a("perform-80", "Solid run", "A Perform run of 80 percent or better.", "Score 80 percent in Perform mode",
            AchievementCategory.SONGS, pose("GREAT_JOB"), 80) { it.bestPerform.coerceAtLeast(0) },
        a("perform-95", "Nearly flawless", "A Perform run of 95 percent or better.", "Score 95 percent in Perform mode",
            AchievementCategory.SONGS, pose("LEVEL_UP"), 95) { it.bestPerform.coerceAtLeast(0) },
        a("polished-5", "Polished", "Five songs with a best score of 90 percent or more.", "Score 90 percent on 5 songs",
            AchievementCategory.SONGS, pose("GREAT_JOB"), 5) { it.songsPolished },
        a("records-10", "Record breaker", "Ten new personal bests.", "Set 10 new personal bests",
            AchievementCategory.SONGS, drawable("ic_speed"), 10) { it.newBests },

        // Lessons
        a("lesson-1", "First lesson", "You finished a lesson.", "Finish a lesson",
            AchievementCategory.LESSONS, drawable("ic_school")) { it.lessonsDone },
        a("lessons-10", "Ten lessons", "Ten lessons done.", "Finish 10 lessons",
            AchievementCategory.LESSONS, drawable("ic_school"), 10) { it.lessonsDone },
        a("lessons-25", "Twenty five lessons", "Twenty five lessons done.", "Finish 25 lessons",
            AchievementCategory.LESSONS, drawable("ic_school"), 25) { it.lessonsDone },
        a("basics-done", "All the basics", "Every lesson in Basics is done.", "Finish every Basics lesson",
            AchievementCategory.LESSONS, pose("LEARNING")) { flag("basics" in it.unitsComplete) },
        a("beginner-done", "Beginner graduate", "Every lesson in Beginner is done.", "Finish every Beginner lesson",
            AchievementCategory.LESSONS, pose("GREAT_JOB")) { flag("beginner" in it.unitsComplete) },
        a("intermediate-done", "Intermediate graduate", "Every lesson in Intermediate is done.", "Finish every Intermediate lesson",
            AchievementCategory.LESSONS, pose("GREAT_JOB")) { flag("intermediate" in it.unitsComplete) },
        a("course-done", "Course complete", "Every lesson in the course is done.", "Finish every lesson in the course",
            AchievementCategory.LESSONS, pose("LEVEL_UP")) { flag(it.lessonsTotal > 0 && it.lessonsDone >= it.lessonsTotal) },

        // Chords
        a("chord-1", "Chord curious", "You answered your first chord game round.", "Play a chord game round",
            AchievementCategory.CHORDS, drawable("ic_piano")) { it.chordRounds },
        a("game-1", "Game on", "You finished a chord game.", "Finish a chord game",
            AchievementCategory.CHORDS, pose("PRACTICING")) { it.gamesCompleted },
        a("game-10", "Chord regular", "Ten chord games finished.", "Finish 10 chord games",
            AchievementCategory.CHORDS, pose("PRACTICING"), 10) { it.gamesCompleted },
        a("game-perfect", "Ten out of ten", "A chord game with every round right.", "Score a perfect chord game",
            AchievementCategory.CHORDS, pose("GREAT_JOB")) { it.perfectGames },
        a("every-key", "Every key", "You played a chord in all 12 keys.", "Play a chord in all 12 keys",
            AchievementCategory.CHORDS, drawable("ic_piano"), 12) { it.rootsPlayed.count { r -> r in 0..11 } },

        // Ear
        a("ear-1", "First listen", "You answered an ear training round.", "Play an ear training round",
            AchievementCategory.EAR, drawable("ic_ear")) { it.earRounds },
        a("ear-50", "Good ears", "Fifty ear training rounds.", "Play 50 ear training rounds",
            AchievementCategory.EAR, drawable("ic_ear"), 50) { it.earRounds },
        a("ear-250", "Sharp ears", "Two hundred and fifty ear training rounds.", "Play 250 ear training rounds",
            AchievementCategory.EAR, pose("LISTENING"), 250) { it.earRounds },
        a("ear-100-right", "Right on pitch", "A hundred ear training answers right.", "Answer 100 ear rounds correctly",
            AchievementCategory.EAR, pose("LISTENING"), 100) { it.earCorrect },

        // Theory
        a("theory-1", "Theory begins", "You finished a theory lesson.", "Finish a theory lesson",
            AchievementCategory.THEORY, drawable("ic_school")) { it.theoryLessons },
        a("theory-10", "Ten theory lessons", "Ten theory lessons done.", "Finish 10 theory lessons",
            AchievementCategory.THEORY, drawable("ic_school"), 10) { it.theoryLessons },
        a("theory-unit-1", "Unit complete", "You finished a whole theory unit.", "Finish a theory unit",
            AchievementCategory.THEORY, pose("LEARNING")) { it.theoryUnits },
        a("theory-unit-6", "Theory graduate", "All six theory units done.", "Finish all 6 theory units",
            AchievementCategory.THEORY, pose("LEVEL_UP"), 6) { it.theoryUnits },

        // Explorer
        a("sight-1", "First sight read", "You sight-read new music.", "Finish a sight-reading run",
            AchievementCategory.EXPLORER, drawable("ic_school")) { it.sightReadRuns },
        a("mic-on", "Mic on", "You played with the microphone listening.", "Play a song with the microphone",
            AchievementCategory.EXPLORER, drawable("ic_mic")) { flag("mic" in it.featuresUsed) },
        a("import-midi", "Your own music", "You imported a MIDI file.", "Import a MIDI file",
            AchievementCategory.EXPLORER, drawable("ic_library_music")) { flag("import" in it.featuresUsed) },
        a("daily-1", "Practice today", "You finished a Practice today session.", "Finish a Practice today session",
            AchievementCategory.EXPLORER, pose("GREAT_JOB")) { it.dailySessions },
        a("daily-10", "Daily habit", "Ten Practice today sessions.", "Finish 10 Practice today sessions",
            AchievementCategory.EXPLORER, pose("STREAK"), 10) { it.dailySessions },
        a("explorer", "Explorer", "You tried six different parts of the app.", "Try 6 different features",
            AchievementCategory.EXPLORER, drawable("ic_flag"), 6) { it.featuresUsed.size },
    )

    private val byId = ALL.associateBy { it.id }
    fun byId(id: String): Achievement? = byId[id]

    fun byCategory(): Map<AchievementCategory, List<Achievement>> =
        AchievementCategory.entries.associateWith { c -> ALL.filter { it.category == c } }.filterValues { it.isNotEmpty() }

    /** Achievements unlocked by [s] that are not in [already], in list order. */
    fun newlyUnlocked(s: Snapshot, already: Set<String>): List<Achievement> = ALL.filter { it.id !in already && it.unlocked(s) }

    /** Practising at this hour earns the time-of-day flags. */
    fun isNightOwlHour(hour: Int) = hour >= 22
    fun isEarlyBirdHour(hour: Int) = hour < 7
}
