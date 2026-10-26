package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.MiniJson
import com.bigpapituna.sharpears.engine.family.ProfileKind

/*
 * THE CONTRACT between the insights engine (Insights.kt) and everything that writes or shows a brief: the AI writer,
 * the template writer, the child's writer and the screens (docs/PARENT-BRIEF-PLAN.md). Findings hold numbers, enums and
 * ids only (lessons, songs, pages), never prose and never a name: a writer turns them into words, inserting the child's
 * name and the lessons' titles on the phone. CONTRACT.md in this folder explains every kind with an example.
 *
 * Stable on the wire: the enum names below are what encode() writes and what the AI is shown, so rename nothing; add new
 * kinds at the end. decode() drops entries whose kind it does not know, so an older reader survives a newer engine.
 */

/** Which brief: right after a session, today, or the last seven days. */
enum class Period { SESSION, DAY, WEEK }

/** [fromMs] to [toMs] (epoch ms), the span a brief covers; [toMs] is the moment it was computed for a DAY or WEEK. */
data class TimeRange(val fromMs: Long, val toMs: Long)

enum class Direction { UP, DOWN, STEADY }

/** Morning 05:00 to 12:00, afternoon to 17:00, evening to 21:00, night the rest (the phone's time zone). */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/** A number worth saying, for the one-minute read (the first three) and the full report. See CONTRACT.md for each. */
enum class FactKind {
    /** [Fact.value] minutes practised (what the goal counts), [Fact.of] minutes the app was on screen. */
    PRACTICE_MINUTES,
    /** [Fact.value] days with any practice, [Fact.of] days in the range. */
    DAYS_PRACTISED,
    /** [Fact.value] sessions (stretches on screen with less than five minutes away between them). */
    SESSIONS,
    /** [Fact.value] days the daily goal was reached, [Fact.of] days in the range. */
    GOAL_DAYS,
    /** Most practice in one part of the day: [Fact.value] minutes in it, [Fact.of] all minutes. */
    MOSTLY_MORNING, MOSTLY_AFTERNOON, MOSTLY_EVENING, MOSTLY_NIGHT,
    /** [Fact.value] lessons finished for the first time; [Fact.lessons] those lessons. */
    NEW_LESSONS,
    /** [Fact.value] lessons finished again (replays); [Fact.lessons] those lessons. */
    LESSONS_REPLAYED,
    /** [Fact.value] songs played to the end; [Fact.songs] the songs, most played first. */
    SONG_RUNS,
    /** [Fact.value] wrong keys that were next door (two semitones or closer), [Fact.of] all wrong keys. */
    NEAR_MISSES,
    /** [Fact.value] mean timing in ms off the beat (absolute) over Perform runs, [Fact.of] the runs. */
    TIMING,
    /** [Fact.value] the left hand's hit rate in percent, [Fact.of] the right hand's (runs for one hand only). */
    HANDS,
    /** [Fact.value] ear and chord game rounds answered right, [Fact.of] rounds answered. */
    EAR_ROUNDS,
    /** [Fact.value] mean percent of the drum's beats with a clap, [Fact.of] drum steps played. */
    CLAP,
    /** [Fact.value] minutes in the toys (Jam, Pup says, Clap along), [Fact.of] minutes on screen. */
    TOY_MINUTES,
    /** [Fact.value] stickers dropped, [Fact.of] of them new to the album. */
    STICKERS,
    /** [Fact.value] Pup's quest stars. */
    QUEST_STARS,
    /**
     * 2.7: [Fact.value] arcade game rounds played, [Fact.of] the best stars of the best game; [Fact.songs] carries the games'
     * ids (engine/arcade ArcadeGame), the best first (most stars, then most rounds).
     */
    GAMES,
    /** [Fact.value] stuck moments where the app played the key (the third hint), [Fact.of] stuck moments with any hint. */
    HINTS,
    /** [Fact.value] notes that came due and were never played (let go, missed in Perform), [Fact.of] notes that came due. */
    SKIPPED_NOTES,
    /** [Fact.value] the mean ms from a note coming due to its key being played (wait mode), [Fact.of] notes measured. */
    HESITATION,
    /** [Fact.value] tempo steadiness: the mean spread of Perform runs' offsets from the beat in ms (lower is steadier), [Fact.of] the runs. */
    TEMPO_SPREAD,
}

/** A change against the person's OWN earlier periods (never anyone else). See [Trend]. */
enum class TrendKind {
    /** Practice minutes per period. Up is good. */
    MINUTES,
    /** Days practised per period (WEEK only). Up is good. */
    DAYS,
    /** Sessions per period. Up is good. */
    SESSIONS,
    /** Mean lesson stars (1 to 3). Up is good. */
    STARS,
    /** Mean Perform accuracy, percent. Up is good. */
    ACCURACY,
    /** Mean timing off the beat, ms (absolute). Down is good. */
    TIMING_MS,
    /** Share of wrong keys that were next door, percent. Up is good (the misses are getting closer). */
    NEAR_SHARE,
    /** Wrong keys per graded run. Down is good. */
    WRONG_PER_RUN,
    /** Left-hand hit rate, percent. Up is good. */
    LEFT_HAND,
    /** Right-hand hit rate, percent. Up is good. */
    RIGHT_HAND,
    /** Ear and chord game rounds right, percent. Up is good. */
    EAR,
    /** Drum steps' beats with a clap, percent. Up is good. */
    CLAP,
    /** Share of stuck moments that needed the app to play the key, percent. Down is good. */
    PLAY_HINTS,
    /** Mean ms from a note coming due to its key being played (wait mode). Down is good. */
    HESITATION_MS,
    /** Spread of Perform runs' offsets from the beat, ms. Down is good (a steadier beat). */
    TEMPO_SPREAD,
}

/** Something to act on or explain gently. Every flag is paired with why it is normal by the writer (CONTRACT.md). */
enum class FlagKind {
    /** A lesson let go after three tries, a step that took three or more, or a lesson replayed with one star each time. [Flag.lessons]. */
    STUCK_LESSON,
    /** A song played three or more times without reaching two stars. [Flag.songs]. */
    STUCK_SONG,
    /** Finished lessons again three or more times and started nothing new. [Flag.lessons] the replays. */
    REPLAYING_NOT_MOVING_ON,
    /** Kids who do not read yet: two weeks of letter lessons played well. Suggests reader mode. */
    READY_FOR_LETTERS,
    /** Kids: the last Playground page is done; the big kids' road is next. */
    READY_FOR_ROAD,
    /** Lessons finished first time with nearly three stars and no hard moments: ready for more. */
    CRUISING,
    /** Lessons from three or more levels or pages with few stars: jumping around. */
    SCATTERED,
    /** Kids: three or more lessons of one Playground page stuck or at one star. [Flag.page]. */
    PAGE_STRUGGLE,
    /** Most of the time on screen went to the toys, in two or more sessions. */
    TOY_HEAVY,
    /** The room got in the way repeatedly: noise the watchdog offered to ignore, or a stage hearing sounds and no key. */
    NOISY_ROOM,
    /** The app had to play the key (the third hint) often. [Flag.lessons] / [Flag.songs] where it happened most. */
    HINT_DEPENDENCE,
    /** The left hand's hit rate well under the right hand's. */
    LEFT_HAND_BEHIND,
    /** Most wrong keys were far from the note wanted (finding the keys), colour notes off. */
    FAR_MISSES,
    /** Kids: very long days at the piano (three times the goal or more) with bedtime off. */
    LONG_DAYS,
    /** A song played three or more times at three stars: ready for a new one. [Flag.songs]. */
    MASTERED_SONG,
    /** Lessons finished first time with one star (not stuck): stars to be won back. [Flag.lessons]. */
    LOW_STARS,
    /** Fewer days or minutes than the person's own usual (WEEK). */
    PRACTICE_DIPPED,
    /**
     * One key the person keeps pausing on: [Flag.value] the key (a MIDI note number, 60 is middle C), [Flag.of] its typical
     * wait in ms; [Flag.lessons] / [Flag.songs] where it happened most.
     */
    HESITATES_ON,
    /** The left hand takes noticeably longer to find its keys than the right: [Flag.value] its mean wait in ms, [Flag.of] the right's. */
    LEFT_HAND_LAGS,
}

/** Something to celebrate; the writer picks one for the short read. */
enum class PraiseKind {
    /** A Playground page's finale played. [Praise.page]. */
    FINALE,
    /** A lesson with a chord finished for the first time ever. [Praise.lessons]. */
    FIRST_CHORD,
    /** A lesson that took three tries or a let go, finished later with two stars or more. [Praise.lessons]. */
    STUCK_WITH_IT,
    /** [Praise.value] shiny stickers. */
    SHINY_STICKER,
    /** [Praise.value] days in a row reaching the daily goal (three or more), up to the range's end. */
    GOAL_STREAK,
    /** [Praise.value] lessons finished with three stars. [Praise.lessons]. */
    THREE_STARS,
    /** Timing closer to the beat than before. [Praise.value] ms now. */
    TIMING_IMPROVED,
    /** Perform accuracy up on before. [Praise.value] percent now. */
    ACCURACY_IMPROVED,
    /** Wrong keys closer to the right one than before. [Praise.value] percent next door now. */
    MISSES_CLOSER,
    /** Ear games better than before. [Praise.value] percent now. */
    EAR_IMPROVED,
    /** The left hand better than before. [Praise.value] percent now. */
    LEFT_HAND_IMPROVED,
    /** [Praise.value] days practised (four or more in a week, or more than usual). */
    CONSISTENT,
    /** [Praise.value] songs played with three stars. [Praise.songs]. */
    CLEAN_SONG,
    /** [Praise.value] rare stickers. */
    RARE_STICKER,
    /** [Praise.value] new lessons finished. [Praise.lessons]. */
    NEW_LESSONS,
    /** [Praise.value] days with all three quest stars. */
    QUEST_DAY,
    /** The beat steadier than before in Perform. [Praise.value] the spread in ms now. */
    STEADIER_TEMPO,
    /** Keys found faster than before. [Praise.value] the mean wait in ms now. */
    FINDS_KEYS_FASTER,
    /** 2.9: back at the piano after [Praise.value] full days away (the comeback card showed). Said gladly, never as a lapse. */
    CAME_BACK,
}

/**
 * Praise in order of specialness (the one-minute read takes the first): the enum's order, with the kinds added later in
 * their place (the enum itself only ever grows at the end, so its wire names stay put).
 */
val PRAISE_ORDER: List<PraiseKind> = listOf(
    PraiseKind.FINALE, PraiseKind.FIRST_CHORD, PraiseKind.STUCK_WITH_IT, PraiseKind.CAME_BACK, PraiseKind.SHINY_STICKER, PraiseKind.GOAL_STREAK,
    PraiseKind.THREE_STARS, PraiseKind.TIMING_IMPROVED, PraiseKind.STEADIER_TEMPO, PraiseKind.ACCURACY_IMPROVED,
    PraiseKind.MISSES_CLOSER, PraiseKind.FINDS_KEYS_FASTER, PraiseKind.EAR_IMPROVED, PraiseKind.LEFT_HAND_IMPROVED,
    PraiseKind.CONSISTENT, PraiseKind.CLEAN_SONG, PraiseKind.RARE_STICKER, PraiseKind.NEW_LESSONS, PraiseKind.QUEST_DAY,
)

/** What a recommendation is for; the writer's heading. */
enum class RecommendationKind { UNSTICK, MOVE_ON, POLISH, TOGETHER, ROUTINE, SETTINGS, NEXT_STEP, FOCUS, NEW_MUSIC }

/**
 * The fixed action menu (docs/PARENT-BRIEF-PLAN.md, the seven actions): a recommendation picks one by id, and the
 * screens run it behind the parent gate. Params (all optional strings): "lesson", "song", "page", "setting", "value",
 * "after" (TEACHER_ASSIGN: the song mastered). CONTRACT.md has what each does with them.
 */
enum class ActionId {
    /** Pin a lesson as the next stone for a day ("lesson"; none = the path's next lesson). */
    SET_NEXT,
    /** Replay a lesson for more stars ("lesson"). */
    REPLAY_FOR_STARS,
    /** A duet with the grown-up on the left hand ("song" or "lesson"; none = the grown-up picks). */
    PRACTISE_TOGETHER,
    /** Restart a Playground page, two confirmations ("page"). */
    FRESH_START,
    /** Focus mode: later pages hidden for now ("page" = the one to stay on). */
    PACE_FOCUS,
    /** Open the next page early ("page" = the current one). */
    PACE_UNLOCK,
    /** One-tap settings ("setting" = reader | bedtime | colourNotes | micEasier | quietRoom, "value"). */
    SETTING,
    /** Put a song on the child's shelf with a note ("after" = a song they mastered; the parent picks). */
    TEACHER_ASSIGN,
}

/** The settings SETTING may change, by their wire names. */
enum class SettingId(val wire: String) {
    /** The kid reads: letters instead of animals on the keys (Profile.reader). */
    READER("reader"),
    /** Goodnight after the daily goal (BedtimeStore.bedtime). */
    BEDTIME("bedtime"),
    /** Colour notes on the lane and keys (Profile.colourNotes). */
    COLOUR_NOTES("colourNotes"),
    /** The microphone a little easier: Profile.micOffsetDb up by MIC_STEP_DB (3 dB). */
    MIC_EASIER("micEasier"),
    /** A noisy room: stop painting wrong keys on this phone (MicInput.wrongFlash off). */
    QUIET_ROOM("quietRoom");

    companion object {
        fun of(s: String?): SettingId? = entries.firstOrNull { it.wire == s }
        const val MIC_STEP_DB = 3.0
    }
}

/** A number worth saying: [value] (and [of], what it is out of), with the lessons and songs it is about. */
data class Fact(val kind: FactKind, val value: Long, val of: Long? = null,
                val lessons: List<String> = emptyList(), val songs: List<String> = emptyList())

/**
 * [now] this period against [before], the mean of up to `previousPeriods` earlier periods that had data ([periods] of
 * them). [direction] is the plain change (15 percent or more, and more than the kind's smallest step), [good] whether
 * that change is an improvement (null when STEADY).
 */
data class Trend(val kind: TrendKind, val now: Double, val before: Double, val direction: Direction, val good: Boolean?, val periods: Int)

/** Something to act on: [value] and [of] as the kind says, with the lessons, songs or page it is about. */
data class Flag(val kind: FlagKind, val value: Long = 0, val of: Long? = null,
                val lessons: List<String> = emptyList(), val songs: List<String> = emptyList(), val page: String? = null)

/** Something to celebrate, shaped like a flag. */
data class Praise(val kind: PraiseKind, val value: Long = 0, val of: Long? = null,
                  val lessons: List<String> = emptyList(), val songs: List<String> = emptyList(), val page: String? = null)

/** A recommendation: what it is for, the action from the menu with its params, and the flag it answers ([because]). */
data class Candidate(val recommendation: RecommendationKind, val action: ActionId, val params: Map<String, String> = emptyMap(),
                     val because: FlagKind? = null)

/** A grown-up's action in the range (recorded in the next brief, as the plan promises). */
data class ActionTaken(val at: Long, val action: ActionId, val params: Map<String, String> = emptyMap())

/** A setting changed in the range ([key] and [value] as BriefEvent.SettingChanged has them). */
data class SettingChange(val at: Long, val key: String, val value: String)

/**
 * 2.14, "what they can now do": a lesson finished with two stars or more ([lessonId]), or a song played to its end with two
 * stars or more ([songId], with the [hands] its best run used), in the four weeks up to the range's end. Ids only; the phone
 * says them (engine/brief/CanDoWords.kt).
 */
data class CanDo(val lessonId: String? = null, val songId: String? = null, val hands: Hands? = null)

/**
 * One day of the seven-day strip: minutes on screen and practised, sessions, and whether the goal was reached. [rest]
 * (2.9): a missed day the streak kept as its rest day (engine/PracticeStats.restDays), drawn as a sleeping paw.
 */
data class DayStat(val date: String, val minutes: Int, val practiceMinutes: Int, val sessions: Int, val goal: Boolean, val rest: Boolean = false)

/** A lesson played in the range: its best stars there (null when not finished), tries, steps let go, finishes, minutes. */
data class LessonStat(val lessonId: String, val stars: Int?, val tries: Int, val letGo: Int, val finishes: Int, val minutes: Int)

/** A song played to its end in the range: runs, best stars, best Perform accuracy (null with none). */
data class SongStat(val songId: String, val runs: Int, val bestStars: Int, val bestAccuracy: Int?)

/** The skills the full report draws as small lines. */
enum class SkillKind { STARS, ACCURACY, TIMING_MS, NEAR_SHARE, LEFT_HAND, RIGHT_HAND, EAR, CLAP, PLAY_HINTS }

/** One skill over the last four weeks ending with the range, oldest first; null for a week with nothing to measure. */
data class SkillSeries(val kind: SkillKind, val points: List<Double?>)

data class ToyStat(val toy: ToyKind, val opens: Int, val minutes: Int)

/** 2.7: an arcade game's rounds in the range and its best stars ([game] engine/arcade ArcadeGame's id). */
data class GameStat(val game: String, val rounds: Int, val bestStars: Int)

data class PartStat(val part: DayPart, val minutes: Int, val sessions: Int)

/** Hint escalation in the range: stuck moments that ended at each step, and octave hints. */
data class HintStats(val glow: Int = 0, val point: Int = 0, val play: Int = 0, val octave: Int = 0)

/** One key of the slip map: wrong keys struck when [midi] was the note wanted, [near] (two semitones or closer) and [far]. */
data class KeySlips(val midi: Int, val near: Int, val far: Int) { val total: Int get() = near + far }

/** A slip: [played] struck when [expected] was wanted, [count] times. */
data class SlipPair(val expected: Int, val played: Int, val count: Int)

/**
 * How long keys took to find (wait mode): the mean [meanMs] over [notes]; [worstKey] the key with the longest typical wait
 * (the mean of its slowest find in each run, keys seen in two runs or more preferred) and that wait, [worstMs].
 */
data class HesitationStat(val meanMs: Int, val notes: Int, val worstKey: Int? = null, val worstMs: Int? = null)

/**
 * One hand: [notes] that came due, [clean] the percent of them played with no wrong key charged to them (not let go, not
 * missed; null under Insights.HAND_CLEAN_MIN notes), and its mean wait for a key [hesitationMs] (null with none).
 */
data class HandStat(val notes: Int, val clean: Int?, val hesitationMs: Int?)

/** The drum steps (game CLAP): [onBeat] beats with a clap of [beats], over [steps] steps. */
data class ClapStat(val onBeat: Int, val beats: Int, val steps: Int)

/** Ear and chord game rounds of one [game] (BriefEvent.EarRound's id: rhythm, pitch, ..., chord:build ...). */
data class EarStat(val game: String, val rounds: Int, val correct: Int)

/** Minutes on screen by where they went: lesson steps, songs played (Listen included), the toys. */
data class PlayMix(val lessons: Int = 0, val songs: Int = 0, val toys: Int = 0)

/** The range's totals. */
data class Totals(
    val minutes: Int = 0, val practiceMinutes: Int = 0, val sessions: Int = 0, val daysPractised: Int = 0,
    val lessonsFinished: Int = 0, val newLessons: Int = 0, val songRuns: Int = 0, val stickers: Int = 0,
    val rareStickers: Int = 0, val shinyStickers: Int = 0, val questStars: Int = 0, val goalDays: Int = 0,
    val earRounds: Int = 0, val earCorrect: Int = 0,
)

/**
 * The numbers the full report shows: the seven-day strip ([days], ending on the range's last day), every lesson with
 * stars and tries, the songs, skills over four weeks, the toy mix, time of day, noisy-room events and hints.
 */
data class Stats(
    val days: List<DayStat> = emptyList(),
    val lessons: List<LessonStat> = emptyList(),
    val songs: List<SongStat> = emptyList(),
    val skills: List<SkillSeries> = emptyList(),
    val toys: List<ToyStat> = emptyList(),
    val timeOfDay: List<PartStat> = emptyList(),
    val noisyRoom: Int = 0,
    val hints: HintStats = HintStats(),
    val totals: Totals = Totals(),
    /** Every key wanted that drew a wrong key, low to high (the slip map). */
    val keySlips: List<KeySlips> = emptyList(),
    /** The most common slip, or null with none. */
    val topSlip: SlipPair? = null,
    /** Notes that came due in lesson steps and songs, and those never played (let go, missed in Perform). */
    val notesDue: Int = 0,
    val skippedNotes: Int = 0,
    /** How long keys took to find; null when nothing was waited for. */
    val hesitation: HesitationStat? = null,
    /** Tempo steadiness: the mean spread of Perform runs' beat offsets in ms over [tempoRuns] runs; null with none. */
    val tempoSpreadMs: Int? = null,
    val tempoRuns: Int = 0,
    /** Each hand's notes; null for a hand with no notes due. */
    val left: HandStat? = null,
    val right: HandStat? = null,
    /** The period before's hints (last week for a WEEK), null when nothing was played in it. */
    val hintsBefore: HintStats? = null,
    /** The drum steps' claps on the beat; null with no drum step that counted its beats. */
    val clap: ClapStat? = null,
    /** Ear and chord game rounds by game, most played first. */
    val ear: List<EarStat> = emptyList(),
    /** 2.7: the arcade's games played, the best first. */
    val games: List<GameStat> = emptyList(),
    /** Practice minutes in each of the four weeks ending with the range, oldest first. */
    val weeks: List<Int> = emptyList(),
    /** The mean session's minutes on screen, in the range and in the periods before (null without sessions). */
    val sessionMinutes: Int? = null,
    val sessionMinutesBefore: Int? = null,
    /** Where the minutes on screen went. */
    val mix: PlayMix = PlayMix(),
    /** 2.9 habits: days with practice in the calendar month of the range's end, up to that day. */
    val daysBackThisMonth: Int = 0,
    /**
     * 2.9 habits: the longest run of days in a row with no practice in the seven-day strip (days before the person's first
     * event, and the last day while it is still unplayed, left out). "Back 5 of the last 7, the longest gap 2 days."
     */
    val longestGap: Int = 0,
)

/**
 * Everything a brief may say about one person for one [period] over [range], computed by Insights.compute. Lists are in
 * order of importance (the short read takes the first three facts, the first two or three candidates, the first praise).
 * [reader] matters for kids (letters or animals), [goalMinutes] is the daily goal when known.
 */
data class Findings(
    val profileKind: ProfileKind,
    val period: Period,
    val range: TimeRange,
    val facts: List<Fact> = emptyList(),
    val trends: List<Trend> = emptyList(),
    val flags: List<Flag> = emptyList(),
    val candidates: List<Candidate> = emptyList(),
    val praise: List<Praise> = emptyList(),
    val stats: Stats = Stats(),
    val reader: Boolean = true,
    val goalMinutes: Int? = null,
    val actions: List<ActionTaken> = emptyList(),
    val settings: List<SettingChange> = emptyList(),
    /** 2.14: what the person can now do, the latest first (at most three). */
    val canDo: List<CanDo> = emptyList(),
) {
    /** Nothing happened in the range (no session, no event): writers say so kindly, or skip the brief. */
    val isEmpty: Boolean get() = stats.totals.sessions == 0 && facts.isEmpty() && flags.isEmpty() && praise.isEmpty()

    /** Every lesson id the findings mention (facts, flags, praise, candidates, stats). */
    fun lessonIds(): Set<String> = buildSet {
        facts.forEach { addAll(it.lessons) }; flags.forEach { addAll(it.lessons) }; praise.forEach { addAll(it.lessons) }
        candidates.forEach { c -> c.params["lesson"]?.let(::add) }; actions.forEach { a -> a.params["lesson"]?.let(::add) }
        stats.lessons.forEach { add(it.lessonId) }
        canDo.forEach { c -> c.lessonId?.let(::add) }
    }

    /** Every song id the findings mention. */
    fun songIds(): Set<String> = buildSet {
        facts.forEach { addAll(it.songs) }; flags.forEach { addAll(it.songs) }; praise.forEach { addAll(it.songs) }
        candidates.forEach { c -> c.params["song"]?.let(::add); c.params["after"]?.let(::add) }
        actions.forEach { a -> a.params["song"]?.let(::add) }
        stats.songs.forEach { add(it.songId) }
        canDo.forEach { c -> c.songId?.let(::add) }
    }

    /**
     * What the AI writer may send: the same findings with nothing typed by a person and nothing that is not an id, a
     * number or an enum. Findings never hold a name; this also drops every action or setting param outside [SAFE_KEYS],
     * every value that does not look like an id ([ID]) (a teacher's note, say), and ids that are not id-shaped.
     */
    fun redacted(): Findings {
        fun ids(l: List<String>) = l.filter(ID::matches)
        fun params(p: Map<String, String>) = p.filter { (k, v) -> k in SAFE_KEYS && ID.matches(v) }
        return copy(
            facts = facts.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs)) },
            flags = flags.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs), page = it.page?.takeIf(ID::matches)) },
            praise = praise.map { it.copy(lessons = ids(it.lessons), songs = ids(it.songs), page = it.page?.takeIf(ID::matches)) },
            candidates = candidates.map { it.copy(params = params(it.params)) },
            actions = actions.map { it.copy(params = params(it.params)) },
            settings = settings.filter { ID.matches(it.key) && ID.matches(it.value) },
            stats = stats.copy(lessons = stats.lessons.filter { ID.matches(it.lessonId) }, songs = stats.songs.filter { ID.matches(it.songId) },
                ear = stats.ear.filter { ID.matches(it.game) }),
            canDo = canDo.filter { c -> listOfNotNull(c.lessonId, c.songId).all(ID::matches) },
        )
    }

    /** One JSON document (MiniJson), stable names; [decode] reads it back. */
    fun encode(): String = MiniJson.write(FindingsCodec.write(this))

    companion object {
        /** Param keys a writer may see. */
        val SAFE_KEYS = setOf("lesson", "song", "page", "setting", "value", "after")
        /** An id, an enum's wire name, a number or a boolean: letters, digits, dot, colon, underscore and dash, no spaces. */
        val ID = Regex("[A-Za-z0-9._:-]{1,64}")

        /** Reads what [encode] wrote; entries with a kind this version does not know are dropped. Null when it is not findings. */
        fun decode(text: String): Findings? = runCatching { FindingsCodec.read(MiniJson.read(text) as Map<*, *>) }.getOrNull()
    }
}

/** The JSON shape of [Findings]: plain maps and lists, enum names as strings, absent fields default. */
internal object FindingsCodec {
    fun write(f: Findings): Map<String, Any?> = linkedMapOf<String, Any?>(
        "profileKind" to f.profileKind.name, "period" to f.period.name,
        "range" to linkedMapOf("from" to f.range.fromMs, "to" to f.range.toMs),
        "reader" to f.reader, "goalMinutes" to f.goalMinutes,
        "facts" to f.facts.map { linkedMapOf("kind" to it.kind.name, "value" to it.value, "of" to it.of, "lessons" to it.lessons, "songs" to it.songs) },
        "trends" to f.trends.map { linkedMapOf("kind" to it.kind.name, "now" to it.now, "before" to it.before, "direction" to it.direction.name, "good" to it.good, "periods" to it.periods) },
        "flags" to f.flags.map { linkedMapOf("kind" to it.kind.name, "value" to it.value, "of" to it.of, "lessons" to it.lessons, "songs" to it.songs, "page" to it.page) },
        "candidates" to f.candidates.map { linkedMapOf("recommendation" to it.recommendation.name, "action" to it.action.name, "params" to it.params, "because" to it.because?.name) },
        "praise" to f.praise.map { linkedMapOf("kind" to it.kind.name, "value" to it.value, "of" to it.of, "lessons" to it.lessons, "songs" to it.songs, "page" to it.page) },
        "actions" to f.actions.map { linkedMapOf("at" to it.at, "action" to it.action.name, "params" to it.params) },
        "settings" to f.settings.map { linkedMapOf("at" to it.at, "key" to it.key, "value" to it.value) },
        "stats" to stats(f.stats),
    ).apply {
        // 2.14: written only when there is something, so findings without it read exactly as before.
        if (f.canDo.isNotEmpty()) put("canDo", f.canDo.map { linkedMapOf("lesson" to it.lessonId, "song" to it.songId, "hands" to it.hands?.name) })
    }

    private fun stats(s: Stats): Map<String, Any?> = linkedMapOf(
        "days" to s.days.map { linkedMapOf("date" to it.date, "minutes" to it.minutes, "practiceMinutes" to it.practiceMinutes, "sessions" to it.sessions, "goal" to it.goal, "rest" to it.rest) },
        "lessons" to s.lessons.map { linkedMapOf("lesson" to it.lessonId, "stars" to it.stars, "tries" to it.tries, "letGo" to it.letGo, "finishes" to it.finishes, "minutes" to it.minutes) },
        "songs" to s.songs.map { linkedMapOf("song" to it.songId, "runs" to it.runs, "bestStars" to it.bestStars, "bestAccuracy" to it.bestAccuracy) },
        "skills" to s.skills.map { linkedMapOf("kind" to it.kind.name, "points" to it.points) },
        "toys" to s.toys.map { linkedMapOf("toy" to it.toy.wire, "opens" to it.opens, "minutes" to it.minutes) },
        "timeOfDay" to s.timeOfDay.map { linkedMapOf("part" to it.part.name, "minutes" to it.minutes, "sessions" to it.sessions) },
        "noisyRoom" to s.noisyRoom,
        "hints" to hints(s.hints),
        "totals" to s.totals.let { t -> linkedMapOf(
            "minutes" to t.minutes, "practiceMinutes" to t.practiceMinutes, "sessions" to t.sessions, "daysPractised" to t.daysPractised,
            "lessonsFinished" to t.lessonsFinished, "newLessons" to t.newLessons, "songRuns" to t.songRuns, "stickers" to t.stickers,
            "rareStickers" to t.rareStickers, "shinyStickers" to t.shinyStickers, "questStars" to t.questStars, "goalDays" to t.goalDays,
            "earRounds" to t.earRounds, "earCorrect" to t.earCorrect) },
        "keySlips" to s.keySlips.map { linkedMapOf("key" to it.midi, "near" to it.near, "far" to it.far) },
        "topSlip" to s.topSlip?.let { linkedMapOf("expected" to it.expected, "played" to it.played, "count" to it.count) },
        "notesDue" to s.notesDue, "skippedNotes" to s.skippedNotes,
        "hesitation" to s.hesitation?.let { linkedMapOf("meanMs" to it.meanMs, "notes" to it.notes, "worstKey" to it.worstKey, "worstMs" to it.worstMs) },
        "tempoSpreadMs" to s.tempoSpreadMs, "tempoRuns" to s.tempoRuns,
        "left" to s.left?.let(::hand), "right" to s.right?.let(::hand),
        "hintsBefore" to s.hintsBefore?.let(::hints),
        "clap" to s.clap?.let { linkedMapOf("onBeat" to it.onBeat, "beats" to it.beats, "steps" to it.steps) },
        "ear" to s.ear.map { linkedMapOf("game" to it.game, "rounds" to it.rounds, "correct" to it.correct) },
        "games" to s.games.map { linkedMapOf("game" to it.game, "rounds" to it.rounds, "bestStars" to it.bestStars) },
        "weeks" to s.weeks,
        "sessionMinutes" to s.sessionMinutes, "sessionMinutesBefore" to s.sessionMinutesBefore,
        "mix" to linkedMapOf("lessons" to s.mix.lessons, "songs" to s.mix.songs, "toys" to s.mix.toys),
        "daysBackThisMonth" to s.daysBackThisMonth, "longestGap" to s.longestGap,
    )

    private fun hints(h: HintStats) = linkedMapOf("glow" to h.glow, "point" to h.point, "play" to h.play, "octave" to h.octave)
    private fun hand(h: HandStat) = linkedMapOf("notes" to h.notes, "clean" to h.clean, "hesitationMs" to h.hesitationMs)

    fun read(m: Map<*, *>): Findings {
        val range = m["range"] as? Map<*, *>
        return Findings(
            profileKind = enumOf<ProfileKind>(m["profileKind"]) ?: ProfileKind.ADULT,
            period = enumOf<Period>(m["period"])!!,
            range = TimeRange(long(range?.get("from")), long(range?.get("to"))),
            facts = list(m["facts"]).mapNotNull { e -> enumOf<FactKind>(e["kind"])?.let { Fact(it, long(e["value"]), longOrNull(e["of"]), strs(e["lessons"]), strs(e["songs"])) } },
            trends = list(m["trends"]).mapNotNull { e ->
                val k = enumOf<TrendKind>(e["kind"]) ?: return@mapNotNull null
                Trend(k, dbl(e["now"]), dbl(e["before"]), enumOf<Direction>(e["direction"]) ?: Direction.STEADY, e["good"] as? Boolean, int(e["periods"]))
            },
            flags = list(m["flags"]).mapNotNull { e ->
                enumOf<FlagKind>(e["kind"])?.let { Flag(it, long(e["value"]), longOrNull(e["of"]), strs(e["lessons"]), strs(e["songs"]), e["page"] as? String) }
            },
            candidates = list(m["candidates"]).mapNotNull { e ->
                val r = enumOf<RecommendationKind>(e["recommendation"]) ?: return@mapNotNull null
                val a = enumOf<ActionId>(e["action"]) ?: return@mapNotNull null
                Candidate(r, a, strMap(e["params"]), enumOf<FlagKind>(e["because"]))
            },
            praise = list(m["praise"]).mapNotNull { e ->
                enumOf<PraiseKind>(e["kind"])?.let { Praise(it, long(e["value"]), longOrNull(e["of"]), strs(e["lessons"]), strs(e["songs"]), e["page"] as? String) }
            },
            stats = (m["stats"] as? Map<*, *>)?.let(::readStats) ?: Stats(),
            reader = m["reader"] as? Boolean ?: true,
            goalMinutes = (m["goalMinutes"] as? Number)?.toInt(),
            actions = list(m["actions"]).mapNotNull { e -> enumOf<ActionId>(e["action"])?.let { ActionTaken(long(e["at"]), it, strMap(e["params"])) } },
            settings = list(m["settings"]).mapNotNull { e ->
                val k = e["key"] as? String ?: return@mapNotNull null
                SettingChange(long(e["at"]), k, e["value"]?.toString() ?: return@mapNotNull null)
            },
            canDo = list(m["canDo"]).mapNotNull { e ->
                val lesson = e["lesson"] as? String; val song = e["song"] as? String
                if (lesson == null && song == null) null else CanDo(lesson, song, enumOf<Hands>(e["hands"]))
            },
        )
    }

    private fun readStats(m: Map<*, *>): Stats = Stats(
        days = list(m["days"]).mapNotNull { e ->
            DayStat(e["date"] as? String ?: return@mapNotNull null, int(e["minutes"]), int(e["practiceMinutes"]), int(e["sessions"]), e["goal"] == true, e["rest"] == true)
        },
        lessons = list(m["lessons"]).mapNotNull { e ->
            LessonStat(e["lesson"] as? String ?: return@mapNotNull null, intOrNull(e["stars"]), int(e["tries"]), int(e["letGo"]), int(e["finishes"]), int(e["minutes"]))
        },
        songs = list(m["songs"]).mapNotNull { e ->
            SongStat(e["song"] as? String ?: return@mapNotNull null, int(e["runs"]), int(e["bestStars"]), intOrNull(e["bestAccuracy"]))
        },
        skills = list(m["skills"]).mapNotNull { e ->
            enumOf<SkillKind>(e["kind"])?.let { k -> SkillSeries(k, (e["points"] as? List<*>).orEmpty().map { (it as? Number)?.toDouble() }) }
        },
        toys = list(m["toys"]).mapNotNull { e -> ToyKind.of(e["toy"] as? String)?.let { ToyStat(it, int(e["opens"]), int(e["minutes"])) } },
        timeOfDay = list(m["timeOfDay"]).mapNotNull { e -> enumOf<DayPart>(e["part"])?.let { PartStat(it, int(e["minutes"]), int(e["sessions"])) } },
        noisyRoom = int(m["noisyRoom"]),
        hints = (m["hints"] as? Map<*, *>)?.let(::readHints) ?: HintStats(),
        totals = (m["totals"] as? Map<*, *>)?.let { t -> Totals(
            int(t["minutes"]), int(t["practiceMinutes"]), int(t["sessions"]), int(t["daysPractised"]), int(t["lessonsFinished"]),
            int(t["newLessons"]), int(t["songRuns"]), int(t["stickers"]), int(t["rareStickers"]), int(t["shinyStickers"]),
            int(t["questStars"]), int(t["goalDays"]), int(t["earRounds"]), int(t["earCorrect"])) } ?: Totals(),
        keySlips = list(m["keySlips"]).mapNotNull { e -> intOrNull(e["key"])?.let { KeySlips(it, int(e["near"]), int(e["far"])) } },
        topSlip = (m["topSlip"] as? Map<*, *>)?.let { e ->
            val x = intOrNull(e["expected"]); val p = intOrNull(e["played"])
            if (x == null || p == null) null else SlipPair(x, p, int(e["count"]))
        },
        notesDue = int(m["notesDue"]), skippedNotes = int(m["skippedNotes"]),
        hesitation = (m["hesitation"] as? Map<*, *>)?.let { e -> HesitationStat(int(e["meanMs"]), int(e["notes"]), intOrNull(e["worstKey"]), intOrNull(e["worstMs"])) },
        tempoSpreadMs = intOrNull(m["tempoSpreadMs"]), tempoRuns = int(m["tempoRuns"]),
        left = (m["left"] as? Map<*, *>)?.let(::readHand), right = (m["right"] as? Map<*, *>)?.let(::readHand),
        hintsBefore = (m["hintsBefore"] as? Map<*, *>)?.let(::readHints),
        clap = (m["clap"] as? Map<*, *>)?.let { e -> ClapStat(int(e["onBeat"]), int(e["beats"]), int(e["steps"])) },
        ear = list(m["ear"]).mapNotNull { e -> (e["game"] as? String)?.let { EarStat(it, int(e["rounds"]), int(e["correct"])) } },
        games = list(m["games"]).mapNotNull { e -> (e["game"] as? String)?.let { GameStat(it, int(e["rounds"]), int(e["bestStars"])) } },
        weeks = (m["weeks"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() },
        sessionMinutes = intOrNull(m["sessionMinutes"]), sessionMinutesBefore = intOrNull(m["sessionMinutesBefore"]),
        mix = (m["mix"] as? Map<*, *>)?.let { e -> PlayMix(int(e["lessons"]), int(e["songs"]), int(e["toys"])) } ?: PlayMix(),
        daysBackThisMonth = int(m["daysBackThisMonth"]), longestGap = int(m["longestGap"]),
    )

    private fun readHints(m: Map<*, *>) = HintStats(int(m["glow"]), int(m["point"]), int(m["play"]), int(m["octave"]))
    private fun readHand(m: Map<*, *>) = HandStat(int(m["notes"]), intOrNull(m["clean"]), intOrNull(m["hesitationMs"]))

    private inline fun <reified E : Enum<E>> enumOf(v: Any?): E? = (v as? String)?.let { s -> enumValues<E>().firstOrNull { it.name == s } }
    private fun list(v: Any?): List<Map<*, *>> = (v as? List<*>).orEmpty().filterIsInstance<Map<*, *>>()
    private fun strs(v: Any?): List<String> = (v as? List<*>).orEmpty().filterIsInstance<String>()
    private fun strMap(v: Any?): Map<String, String> = (v as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
    private fun long(v: Any?): Long = (v as? Number)?.toLong() ?: 0L
    private fun longOrNull(v: Any?): Long? = (v as? Number)?.toLong()
    private fun int(v: Any?): Int = (v as? Number)?.toInt() ?: 0
    private fun intOrNull(v: Any?): Int? = (v as? Number)?.toInt()
    private fun dbl(v: Any?): Double = (v as? Number)?.toDouble() ?: 0.0
}
