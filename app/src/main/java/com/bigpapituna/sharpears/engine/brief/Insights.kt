package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_PAGES
import com.bigpapituna.sharpears.engine.playground.isPlaygroundLesson
import com.bigpapituna.sharpears.engine.playground.pageOf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What the stores know now that the log may not (all optional): the live daily goal (ProgressStore.goalMinutes),
 * whether bedtime is on for this kid (BedtimeStore.bedtime) and whether wrong keys are painted on this phone
 * (MicInput.wrongFlash). Insights only uses them to avoid suggesting a setting that is already on.
 */
data class CurrentSettings(val goalMinutes: Int? = null, val bedtimeOn: Boolean? = null, val wrongKeysShown: Boolean? = null)

/**
 * The insights engine (docs/PARENT-BRIEF-PLAN.md): one person's event log in, [Findings] out. Pure and deterministic:
 * the same events, profile, period, [now] and [zone] always give the same findings, so it is tested on the JVM with
 * synthetic histories (InsightsTest) and every writer and screen can trust it. It compares a person only with their own
 * earlier periods, never with anyone else, and names only lessons, songs and pages that are in the events.
 *
 * Periods: SESSION is the last session at or before [now] (stretches on screen less than [SESSION_GAP_MS] apart are one
 * session; for a note while the session is still open, add the provisional end from data/Briefs.sessionSoFar to
 * [events]); DAY is today from midnight to [now]; WEEK is the seven days ending today. [previousPeriods] earlier periods
 * of the same kind (sessions, days, seven-day blocks) are the baseline for the trends; periods before the person's first
 * event never count.
 */
object Insights {
    /** Two stretches on screen closer than this are one session (a phone call, a look at another app). */
    const val SESSION_GAP_MS = 5 * 60_000L
    /** A trend moves when it changes by this share of its baseline (and by the kind's smallest step). */
    const val TREND_SHARE = 0.15
    /** A step with this many tries is a hard one. */
    const val HARD_TRIES = 3
    /** Kids' reading readiness: the window, the stars, the evidence. */
    const val LETTERS_DAYS = 14L
    const val LETTERS_STARS = 2.5
    const val LETTERS_SAMPLES = 4
    const val LETTERS_DAYS_PLAYED = 3
    const val LETTERS_SPAN_DAYS = 7L
    /** A session counts as toy-heavy from this share of its time in the toys, when it lasted [TOY_SESSION_MIN_MS]. */
    const val TOY_SHARE = 0.6
    const val TOY_SESSION_MIN_MS = 3 * 60_000L
    /** Hint dependence: this many third-step hints, and this share of the stuck moments. */
    const val PLAY_HINTS_MIN = 4
    const val PLAY_HINTS_SHARE = 30
    /** The left hand counts as behind at this many points under the right. */
    const val HAND_GAP = 15
    /** Far misses: this many, and this share of the wrong keys. */
    const val FAR_MIN = 10
    const val FAR_SHARE = 60
    /** A kid's long day: three times the goal on screen, and at least this many minutes. */
    const val LONG_DAY_GOALS = 3
    const val LONG_DAY_MIN = 30
    /** The microphone can be made easier up to this offset (dB); a kid starts at +3. */
    const val MIC_EASIER_MAX_DB = 9.0
    /** Notes never played: this many before they make a fact. */
    const val SKIPPED_MIN = 3
    /** Hesitation: notes measured before its fact and trend count. */
    const val HESITATION_MIN_NOTES = 10
    /** A key the person pauses on: at least this long (ms) in at least this many runs, and twice their usual wait. */
    const val HESITATE_MS = 2_000
    const val HESITATE_RUNS = 3
    /** The left hand lags: both hands timed on this many notes, the left this much slower (ms) and this many times the right. */
    const val LAG_MIN_NOTES = 8
    const val LAG_MS = 500
    const val LAG_RATIO = 1.5
    /** A hand's clean percent is given from this many notes. */
    const val HAND_CLEAN_MIN = 8
    /** At most this many of each list (flags of one kind, candidates, praise). */
    const val MAX_PER_KIND = 3
    const val MAX_CANDIDATES = 5
    const val MAX_PRAISE = 6

    private const val DAY_MS = 86_400_000L

    /**
     * Kids' lessons with letters as their point: the Playground lessons whose words mention letters (the concert hall's
     * letter hunts and the songs read by their letters, hall-1 to hall-4 today). Grown-ups' lessons are not counted.
     */
    val LETTER_LESSONS: Set<String> by lazy {
        COURSE.flatMap { it.lessons }.filter { l -> isPlaygroundLesson(l.id) }.filter { l ->
            (listOf(l.title, l.summary) + l.steps.map { it.text }).any { it.contains("letter", ignoreCase = true) }
        }.map { it.id }.toSet()
    }

    /** Lessons that play a chord (three or more notes struck together somewhere in an exercise). */
    val CHORD_LESSONS: Set<String> by lazy {
        COURSE.flatMap { it.lessons }.filter { l ->
            l.steps.any { s -> s.exercise?.let { e -> listOf(e.right, e.left).any(::hasChord) } == true }
        }.map { it.id }.toSet()
    }

    private fun hasChord(notation: String) =
        notation.split(' ').any { token -> token.substringBefore(':').split('+').size >= 3 }

    /** Lesson id to its course level id (a Playground page's level, Basics, ...). */
    private val LEVEL_OF: Map<String, String> by lazy { COURSE.flatMap { lvl -> lvl.lessons.map { it.id to lvl.id } }.toMap() }

    fun compute(
        events: List<BriefEvent>,
        profile: Profile,
        period: Period,
        now: Long,
        previousPeriods: Int = 4,
        zone: ZoneId = ZoneId.systemDefault(),
        current: CurrentSettings = CurrentSettings(),
    ): Findings {
        // A total order (time, then the line itself), so the findings never depend on the order the events came in.
        val all = events.filter { it !is BriefEvent.Unknown && it.at <= now }.sortedWith(compareBy({ it.at }, { it.encode() }))
        val sessions = Sessions.of(all, now)
        val today = day(now, zone)
        val (win, previous) = when (period) {
            Period.SESSION -> {
                val i = sessions.indexOfLast { it.start <= now }
                if (i < 0) return Findings(profile.kind, period, TimeRange(now, now), reader = profile.reader,
                    goalMinutes = current.goalMinutes ?: profile.dailyGoalMin)
                val s = sessions[i]
                Window.of(s, all, zone) to sessions.subList(maxOf(0, i - previousPeriods), i).reversed().map { Window.of(it, all, zone) }
            }
            Period.DAY -> Window(startOf(today, zone), now + 1, all, sessions, zone) to (1..previousPeriods).map { k ->
                Window(startOf(today.minusDays(k.toLong()), zone), startOf(today.minusDays(k - 1L), zone), all, sessions, zone)
            }
            Period.WEEK -> Window(startOf(today.minusDays(6), zone), now + 1, all, sessions, zone) to (1..previousPeriods).map { k ->
                Window(startOf(today.minusDays(6 + 7L * k), zone), startOf(today.minusDays(6 + 7L * (k - 1)), zone), all, sessions, zone)
            }
        }
        val firstAt = all.firstOrNull()?.at ?: now
        // Earlier periods only count once the person had started (a new kid has no "usual" yet).
        val before = previous.filter { it.to > firstAt }
        val kid = profile.kind == ProfileKind.KID
        val goal = current.goalMinutes ?: all.filterIsInstance<BriefEvent.GoalReached>().lastOrNull { it.goalMin > 0 }?.goalMin ?: profile.dailyGoalMin
        val endMs = minOf(win.to - 1, now)
        val trends = trends(win, before, period)
        val flags = Rules(win, before, all, profile, period, endMs, zone, current, goal).flags()
        return Findings(
            profileKind = profile.kind, period = period, range = TimeRange(win.from, endMs),
            facts = facts(win, period),
            trends = trends,
            flags = flags,
            candidates = candidates(flags, profile, current, win.of<BriefEvent.NoisyRoom>().map { it.screen.substringBefore(':') }.toSet()),
            praise = praise(win, before, all, trends, period, endMs, zone),
            stats = stats(win, before, all, sessions, endMs, zone),
            reader = profile.reader || !kid,
            goalMinutes = goal,
            actions = win.of<BriefEvent.ParentAction>().mapNotNull { a ->
                ActionId.entries.firstOrNull { it.name == a.actionId }?.let { ActionTaken(a.at, it, a.params) }
            },
            settings = win.of<BriefEvent.SettingChanged>().map { SettingChange(it.at, it.key, it.value) },
        )
    }

    /** 2.7: the arcade's rounds by game, the best first (most stars, then most rounds, then the id). */
    private fun gameStats(w: Window): List<GameStat> = w.of<BriefEvent.GameRound>().groupBy { it.game }
        .map { (g, r) -> GameStat(g, r.size, r.maxOf { it.stars }.coerceIn(1, 3)) }
        .sortedWith(compareBy({ -it.bestStars }, { -it.rounds }, { it.game }))

    // ---- facts ----------------------------------------------------------------------------------------------------

    private fun facts(w: Window, period: Period): List<Fact> = buildList {
        if (w.sessions.isNotEmpty() || w.practiceSec > 0) add(Fact(FactKind.PRACTICE_MINUTES, w.practiceMinutes.toLong(), w.minutes.toLong()))
        if (period == Period.WEEK && (w.sessions.isNotEmpty() || w.daysPractised.isNotEmpty())) add(Fact(FactKind.DAYS_PRACTISED, w.daysPractised.size.toLong(), 7))
        w.firstFinishes.takeIf { it.isNotEmpty() }?.let { add(Fact(FactKind.NEW_LESSONS, it.size.toLong(), lessons = it)) }
        w.songsByRuns.takeIf { it.isNotEmpty() }?.let { add(Fact(FactKind.SONG_RUNS, w.runs.size.toLong(), songs = it.take(MAX_PER_KIND))) }
        w.replays.takeIf { it.isNotEmpty() }?.let { r -> add(Fact(FactKind.LESSONS_REPLAYED, r.size.toLong(), lessons = r.map { it.lessonId }.distinct().take(MAX_PER_KIND))) }
        if (w.goalDays.isNotEmpty()) add(Fact(FactKind.GOAL_DAYS, w.goalDays.size.toLong(), if (period == Period.WEEK) 7 else null))
        if (period != Period.SESSION && w.sessions.size > 1) add(Fact(FactKind.SESSIONS, w.sessions.size.toLong()))
        if (period == Period.WEEK && w.sessions.size >= 3 && w.minutes > 0) {
            val (part, minutes) = w.minutesByPart.maxBy { it.value }
            if (minutes * 100 >= w.minutes * 60) add(Fact(FactKind.valueOf("MOSTLY_" + part.name), minutes.toLong(), w.minutes.toLong()))
        }
        if (w.near + w.far >= 5) add(Fact(FactKind.NEAR_MISSES, w.near.toLong(), (w.near + w.far).toLong()))
        if (w.timings.size >= 2) add(Fact(FactKind.TIMING, w.timings.average().roundToInt().toLong(), w.timings.size.toLong()))
        val left = w.handRate(Hands.LEFT); val right = w.handRate(Hands.RIGHT)
        if (left != null && right != null) add(Fact(FactKind.HANDS, left.roundToInt().toLong(), right.roundToInt().toLong()))
        if (w.ear.size >= 3) add(Fact(FactKind.EAR_ROUNDS, w.ear.count { it.correct }.toLong(), w.ear.size.toLong()))
        if (w.claps.isNotEmpty()) add(Fact(FactKind.CLAP, w.claps.average().roundToInt().toLong(), w.claps.size.toLong()))
        if (w.toyMs >= 60_000) add(Fact(FactKind.TOY_MINUTES, (w.toyMs / 60_000), w.minutes.toLong()))
        w.of<BriefEvent.StickerDrop>().takeIf { it.isNotEmpty() }?.let { s -> add(Fact(FactKind.STICKERS, s.size.toLong(), s.count { it.isNew }.toLong())) }
        w.of<BriefEvent.QuestStar>().takeIf { it.isNotEmpty() }?.let { add(Fact(FactKind.QUEST_STARS, it.size.toLong())) }
        gameStats(w).takeIf { it.isNotEmpty() }?.let { g -> add(Fact(FactKind.GAMES, g.sumOf { it.rounds }.toLong(), g.first().bestStars.toLong(), songs = g.map { it.game })) }
        if (w.hints.isNotEmpty()) add(Fact(FactKind.HINTS, w.hints.count { it == HintDepth.PLAY }.toLong(), w.hints.size.toLong()))
        if (w.skippedNotes >= SKIPPED_MIN) add(Fact(FactKind.SKIPPED_NOTES, w.skippedNotes.toLong(), w.notesDue.toLong()))
        if (w.hesitationN >= HESITATION_MIN_NOTES) w.hesitationMean?.let { add(Fact(FactKind.HESITATION, it.roundToInt().toLong(), w.hesitationN.toLong())) }
        w.tempoSpread?.let { add(Fact(FactKind.TEMPO_SPREAD, it.roundToInt().toLong(), w.spreads.size.toLong())) }
    }

    // ---- trends ---------------------------------------------------------------------------------------------------

    /** The smallest change of each kind that counts as a move, and whether up is better. */
    private val STEP = mapOf(
        TrendKind.MINUTES to 5.0, TrendKind.DAYS to 1.0, TrendKind.SESSIONS to 1.0, TrendKind.STARS to 0.25,
        TrendKind.ACCURACY to 5.0, TrendKind.TIMING_MS to 15.0, TrendKind.NEAR_SHARE to 10.0, TrendKind.WRONG_PER_RUN to 1.0,
        TrendKind.LEFT_HAND to 8.0, TrendKind.RIGHT_HAND to 8.0, TrendKind.EAR to 10.0, TrendKind.CLAP to 10.0, TrendKind.PLAY_HINTS to 10.0,
        TrendKind.HESITATION_MS to 150.0, TrendKind.TEMPO_SPREAD to 10.0,
    )
    private val DOWN_IS_GOOD = setOf(TrendKind.TIMING_MS, TrendKind.WRONG_PER_RUN, TrendKind.PLAY_HINTS, TrendKind.HESITATION_MS, TrendKind.TEMPO_SPREAD)

    private fun trends(w: Window, before: List<Window>, period: Period): List<Trend> {
        val out = ArrayList<Trend>()
        for (kind in TrendKind.entries) {
            if (kind == TrendKind.DAYS && period != Period.WEEK) continue
            if (kind == TrendKind.SESSIONS && period == Period.SESSION) continue
            val now = w.value(kind) ?: continue
            val prev = before.mapNotNull { it.value(kind) }
            if (prev.isEmpty()) continue
            val base = prev.average()
            val diff = now - base
            val share = if (base != 0.0) abs(diff) / abs(base) else if (diff != 0.0) 1.0 else 0.0
            val dir = if (abs(diff) >= STEP.getValue(kind) && share >= TREND_SHARE) (if (diff > 0) Direction.UP else Direction.DOWN) else Direction.STEADY
            val good = if (dir == Direction.STEADY) null else (dir == Direction.DOWN) == (kind in DOWN_IS_GOOD)
            out += Trend(kind, round1(now), round1(base), dir, good, prev.size)
        }
        // The ones that moved first, in the kinds' order.
        return out.sortedBy { if (it.direction == Direction.STEADY) 1 else 0 }
    }

    // ---- flags ----------------------------------------------------------------------------------------------------

    private class Rules(
        val w: Window, val before: List<Window>, val all: List<BriefEvent>, val profile: Profile, val period: Period,
        val endMs: Long, val zone: ZoneId, val current: CurrentSettings, val goal: Int,
    ) {
        val kid = profile.kind == ProfileKind.KID
        val stuck = stuckLessons()

        fun flags(): List<Flag> = buildList {
            addAll(stuck.take(MAX_PER_KIND))
            addAll(stuckSongs())
            noisyRoom()?.let(::add)
            hintDependence()?.let(::add)
            if (kid) pageStruggle()?.let(::add)
            if (kid) readyForLetters()?.let(::add)
            if (kid) readyForRoad()?.let(::add)
            cruising()?.let(::add)
            replaying()?.let(::add)
            lowStars()?.let(::add)
            val behind = leftHandBehind()
            behind?.let(::add)
            if (behind == null) leftHandLags()?.let(::add)
            farMisses()?.let(::add)
            hesitatesOn()?.let(::add)
            masteredSong()?.let(::add)
            scattered()?.let(::add)
            toyHeavy()?.let(::add)
            if (kid) longDays()?.let(::add)
            practiceDipped()?.let(::add)
        }

        /** A lesson let go, a step of three tries or more, not finished well after; or replayed only at one star. */
        fun stuckLessons(): List<Flag> = w.lessonIds.mapNotNull { id ->
            val steps = w.steps.filter { it.lessonId == id }
            val dones = w.dones.filter { it.lessonId == id }
            val hard = steps.filter { it.letGo || it.tries >= HARD_TRIES }
            val lastHard = hard.maxOfOrNull { it.at }
            val byHard = lastHard != null && dones.none { it.at >= lastHard && it.stars >= 2 }
            val byReplays = dones.size >= 2 && dones.all { it.stars <= 1 }
            if (!byHard && !byReplays) null
            else Flag(FlagKind.STUCK_LESSON, maxOf(steps.maxOfOrNull { it.tries } ?: 0, dones.size).toLong(),
                hard.count { it.letGo }.toLong(), lessons = listOf(id))
        }.sortedWith(compareBy({ -(it.of ?: 0L) }, { -it.value }, { -w.lastAt(it.lessons.first()) }))

        fun stuckSongs(): List<Flag> = w.runs.groupBy { it.songId }.filter { (_, r) -> r.size >= 3 && r.maxOf { it.stars } < 2 }
            .entries.sortedByDescending { it.value.size }.take(MAX_PER_KIND)
            .map { (id, r) -> Flag(FlagKind.STUCK_SONG, r.size.toLong(), songs = listOf(id)) }

        fun noisyRoom(): Flag? {
            val noisy = w.of<BriefEvent.NoisyRoom>()
            val sessions = w.sessions.count { s -> noisy.any { it.at >= s.start && it.at <= s.end + SESSION_GAP_MS } }
            val enough = when (period) { Period.WEEK -> noisy.size >= 3 || sessions >= 2; else -> noisy.size >= 2 }
            return if (!enough) null else Flag(FlagKind.NOISY_ROOM, noisy.size.toLong(), sessions.toLong())
        }

        fun hintDependence(): Flag? {
            val play = w.hints.count { it == HintDepth.PLAY }
            if (play < PLAY_HINTS_MIN || play * 100 < PLAY_HINTS_SHARE * w.hints.size) return null
            val lessons = w.steps.groupBy { it.lessonId }.mapValues { (_, s) -> s.sumOf { st -> st.hintsUsed.count { it == HintDepth.PLAY } } }
                .filterValues { it > 0 }.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }.take(2)
            val songs = w.runs.groupBy { it.songId }.mapValues { (_, r) -> r.sumOf { run -> run.hintsUsed.count { it == HintDepth.PLAY } } }
                .filterValues { it > 0 }.entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }.take(2)
            return Flag(FlagKind.HINT_DEPENDENCE, play.toLong(), w.hints.size.toLong(), lessons, songs)
        }

        fun pageStruggle(): Flag? {
            val stuckIds = stuck.flatMap { it.lessons }.toSet()
            val weak = w.lessonIds.filter { id -> id in stuckIds || w.dones.filter { it.lessonId == id }.let { d -> d.isNotEmpty() && d.maxOf { it.stars } <= 1 } }
            val byPage = weak.groupBy { pageOf(it)?.id }.filterKeys { it != null }
            val (page, lessons) = byPage.entries.filter { it.value.size >= 3 }.maxByOrNull { it.value.size } ?: return null
            return Flag(FlagKind.PAGE_STRUGGLE, lessons.size.toLong(), lessons = lessons.take(MAX_PER_KIND), page = page)
        }

        /** A pre-reader who played the letter lessons well on several days over the last two weeks. */
        fun readyForLetters(): Flag? {
            if (profile.reader) return null
            val from = endMs - LETTERS_DAYS * DAY_MS
            val recent = all.filter { it.at in from..endMs }
            val steps = recent.filterIsInstance<BriefEvent.LessonStep>().filter { it.lessonId in LETTER_LESSONS && !it.skipped && it.stars != null }
            val dones = recent.filterIsInstance<BriefEvent.LessonDone>().filter { it.lessonId in LETTER_LESSONS }
            if (period == Period.SESSION && w.lessonIds.none { it in LETTER_LESSONS }) return null
            val stars = steps.mapNotNull { it.stars } + dones.map { it.stars }
            if (stars.size < LETTERS_SAMPLES || stars.average() < LETTERS_STARS || steps.any { it.letGo }) return null
            val times = (steps.map { it.at } + dones.map { it.at })
            val days = times.map { day(it, zone) }.toSet()
            if (days.size < LETTERS_DAYS_PLAYED || times.max() - times.min() < LETTERS_SPAN_DAYS * DAY_MS) return null
            val lessons = (steps.map { it.lessonId } + dones.map { it.lessonId }).distinct().take(MAX_PER_KIND)
            return Flag(FlagKind.READY_FOR_LETTERS, (stars.average() * 10).roundToInt().toLong(), stars.size.toLong(), lessons = lessons)
        }

        /** The last Playground page finished (its finale or its last lesson), and no lesson of the road started since. */
        fun readyForRoad(): Flag? {
            val last = PLAYGROUND_PAGES.last()
            val finale = all.lastOrNull { it is BriefEvent.FinalePlayed && it.pageId == last.id }
            val done = all.lastOrNull { it is BriefEvent.LessonDone && it.lessonId == last.lastLessonId }
            val at = listOfNotNull(finale?.at, done?.at).maxOrNull() ?: return null
            if (at < endMs - LETTERS_DAYS * DAY_MS) return null
            val onRoad = all.any { e -> e.at > at && ((e as? BriefEvent.LessonStep)?.lessonId ?: (e as? BriefEvent.LessonDone)?.lessonId)?.let { pageOf(it) == null } == true }
            if (onRoad) return null
            return Flag(FlagKind.READY_FOR_ROAD, lessons = listOfNotNull(done?.let { last.lastLessonId }), page = finale?.let { last.id })
        }

        fun cruising(): Flag? {
            val firsts = w.dones.filter { it.first }
            if (firsts.size < 3 || firsts.map { it.stars }.average() < 2.67) return null
            if (stuck.isNotEmpty() || w.steps.any { it.letGo } || w.hints.any { it == HintDepth.PLAY }) return null
            val lessons = firsts.sortedByDescending { it.at }.map { it.lessonId }.distinct().take(MAX_PER_KIND)
            return Flag(FlagKind.CRUISING, firsts.size.toLong(), lessons = lessons, page = pageOf(lessons.first())?.id)
        }

        fun replaying(): Flag? {
            if (w.replays.size < 3 || w.dones.any { it.first }) return null
            val lessons = w.replays.groupingBy { it.lessonId }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }
            return Flag(FlagKind.REPLAYING_NOT_MOVING_ON, w.replays.size.toLong(), lessons = lessons.take(MAX_PER_KIND))
        }

        fun lowStars(): Flag? {
            val stuckIds = stuck.flatMap { it.lessons }.toSet()
            val low = w.dones.filter { it.first && it.stars <= 1 && it.lessonId !in stuckIds }.map { it.lessonId }.distinct()
            return if (low.isEmpty()) null else Flag(FlagKind.LOW_STARS, low.size.toLong(), lessons = low.take(2))
        }

        fun leftHandBehind(): Flag? {
            val left = w.handRate(Hands.LEFT) ?: return null
            val right = w.handRate(Hands.RIGHT) ?: return null
            if (right - left < HAND_GAP) return null
            val songs = w.runs.filter { it.hands == Hands.LEFT }.map { it.songId }.distinct().take(2)
            val lessons = w.steps.filter { it.hands == Hands.LEFT && w.hitRate(it) != null }.map { it.lessonId }.distinct().take(2)
            return Flag(FlagKind.LEFT_HAND_BEHIND, left.roundToInt().toLong(), right.roundToInt().toLong(), lessons, songs)
        }

        fun farMisses(): Flag? {
            if (profile.colourNotes || w.far < FAR_MIN || w.far * 100 < FAR_SHARE * (w.near + w.far)) return null
            return Flag(FlagKind.FAR_MISSES, w.far.toLong(), (w.near + w.far).toLong())
        }

        /** The left hand finds its keys much more slowly than the right (and its hit rate is not the issue: LEFT_HAND_BEHIND). */
        fun leftHandLags(): Flag? {
            val l = w.hand(left = true); val r = w.hand(left = false)
            if (l.hesN < LAG_MIN_NOTES || r.hesN < LAG_MIN_NOTES) return null
            if (l.hesMs - r.hesMs < LAG_MS || l.hesMs < LAG_RATIO * r.hesMs) return null
            val songs = w.runs.filter { it.detail.left.hesN > 0 }.sortedByDescending { it.detail.left.hesMs }.map { it.songId }.distinct().take(2)
            val lessons = w.steps.filter { it.detail.left.hesN > 0 }.sortedByDescending { it.detail.left.hesMs }.map { it.lessonId }.distinct().take(2)
            return Flag(FlagKind.LEFT_HAND_LAGS, l.hesMs.toLong(), r.hesMs.toLong(), lessons, songs)
        }

        /**
         * One key the person keeps pausing on: its slowest find was [HESITATE_MS] or more in [HESITATE_RUNS] runs or more, and
         * typically twice their usual wait (so a slow day overall flags no key). The most often slow key wins.
         */
        fun hesitatesOn(): Flag? {
            val usual = w.hesitationMean ?: return null
            if (w.hesitationN < HESITATION_MIN_NOTES) return null
            val (key, slow) = w.slowKeys.filter { it.ms >= HESITATE_MS }.groupBy { it.midi }
                .filter { (_, l) -> l.size >= HESITATE_RUNS && l.map { it.ms }.average() >= 2 * usual }
                .entries.maxWithOrNull(compareBy({ it.value.size }, { it.value.map { s -> s.ms }.average() }, { -it.key })) ?: return null
            fun most(ids: List<String>) = ids.groupingBy { it }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key }.take(2)
            return Flag(FlagKind.HESITATES_ON, key.toLong(), slow.map { it.ms }.average().roundToInt().toLong(),
                most(slow.mapNotNull { it.lessonId }), most(slow.mapNotNull { it.songId }))
        }

        fun masteredSong(): Flag? {
            val songs = w.runs.groupBy { it.songId }.filter { (_, r) -> r.count { it.stars >= 3 } >= 3 }.keys.sorted()
            return if (songs.isEmpty()) null else Flag(FlagKind.MASTERED_SONG, songs.size.toLong(), songs = songs.take(2))
        }

        fun scattered(): Flag? {
            val played = w.steps.filter { !it.skipped && it.tries > 0 }
            val levels = played.mapNotNull { LEVEL_OF[it.lessonId] }.distinct()
            if (levels.size < 3) return null
            val stars = w.dones.map { it.stars }
            if (stars.isNotEmpty() && stars.average() >= 2.0) return null
            if (stars.isEmpty() && stuck.isEmpty()) return null
            val latest = played.maxBy { it.at }.lessonId
            return Flag(FlagKind.SCATTERED, levels.size.toLong(), lessons = listOf(latest), page = pageOf(latest)?.id)
        }

        fun toyHeavy(): Flag? {
            val toys = w.of<BriefEvent.ToyOpened>()
            val heavy = w.sessions.count { s ->
                val ms = toys.filter { it.at >= s.start && it.at <= s.end + SESSION_GAP_MS }.sumOf { it.durationMs }
                s.onScreenMs >= TOY_SESSION_MIN_MS && ms >= TOY_SHARE * s.onScreenMs
            }
            val enough = if (period == Period.SESSION) heavy >= 1 else heavy >= 2
            return if (!enough) null else Flag(FlagKind.TOY_HEAVY, heavy.toLong(), w.sessions.size.toLong())
        }

        fun longDays(): Flag? {
            if (current.bedtimeOn == true || w.of<BriefEvent.BedtimeShown>().isNotEmpty()) return null
            val line = maxOf(LONG_DAY_GOALS * goal, LONG_DAY_MIN)
            val long = w.minutesByDay.filterValues { it >= line }
            val enough = if (period == Period.WEEK) long.size >= 2 else long.isNotEmpty()
            return if (!enough) null else Flag(FlagKind.LONG_DAYS, long.size.toLong(), line.toLong())
        }

        fun practiceDipped(): Flag? {
            if (period != Period.WEEK || before.size < 2) return null
            val days = w.daysPractised.size
            val usualDays = before.map { it.daysPractised.size }.average()
            val usualMin = before.map { it.practiceMinutes }.average()
            val dipped = (usualDays >= 2 && days <= usualDays - 2) || (usualMin >= 20 && w.practiceMinutes <= usualMin / 2)
            return if (!dipped) null else Flag(FlagKind.PRACTICE_DIPPED, days.toLong(), usualDays.roundToInt().toLong())
        }
    }

    // ---- candidates -----------------------------------------------------------------------------------------------

    /** [noisy]: where the room got in the way ("player": the noise watchdog; "stage": a stage heard sounds and no key). */
    private fun candidates(flags: List<Flag>, profile: Profile, current: CurrentSettings, noisy: Set<String>): List<Candidate> {
        val kid = profile.kind == ProfileKind.KID
        // Each flag's own candidates, best first; then one from every flag before any flag's second, so the first flags
        // cannot crowd out the rest.
        val perFlag = ArrayList<List<Candidate>>()
        var mine = ArrayList<Candidate>()
        fun add(r: RecommendationKind, a: ActionId, because: FlagKind, vararg p: Pair<String, String?>) {
            mine += Candidate(r, a, p.mapNotNull { (k, v) -> v?.let { k to it } }.toMap(), because)
        }
        fun setting(s: SettingId, value: String, because: FlagKind) = add(RecommendationKind.SETTINGS, ActionId.SETTING, because, "setting" to s.wire, "value" to value)
        for (f in flags) { mine = ArrayList(); when (f.kind) {
            FlagKind.STUCK_LESSON -> {
                add(RecommendationKind.UNSTICK, ActionId.SET_NEXT, f.kind, "lesson" to f.lessons.first())
                add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind, "lesson" to f.lessons.first())
            }
            FlagKind.STUCK_SONG -> add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind, "song" to f.songs.first())
            // Room noise the watchdog caught: stop painting wrong keys. A stage hearing sounds but no key in a room the
            // watchdog never minded: likely soft playing the mic misses, so a little easier (never in a noisy room, where
            // an easier mic hears more of the noise).
            FlagKind.NOISY_ROOM -> {
                if ("player" in noisy && current.wrongKeysShown != false) setting(SettingId.QUIET_ROOM, "true", f.kind)
                if ("player" !in noisy && "stage" in noisy && profile.micOffsetDb + SettingId.MIC_STEP_DB <= MIC_EASIER_MAX_DB)
                    setting(SettingId.MIC_EASIER, fmtDb(profile.micOffsetDb + SettingId.MIC_STEP_DB), f.kind)
            }
            FlagKind.HINT_DEPENDENCE -> add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind,
                "lesson" to f.lessons.firstOrNull(), "song" to f.songs.firstOrNull().takeIf { f.lessons.isEmpty() })
            FlagKind.PAGE_STRUGGLE -> if (kid) add(RecommendationKind.FOCUS, ActionId.FRESH_START, f.kind, "page" to f.page)
            FlagKind.READY_FOR_LETTERS -> if (kid) setting(SettingId.READER, "true", f.kind)
            FlagKind.READY_FOR_ROAD -> if (kid) add(RecommendationKind.NEXT_STEP, ActionId.SET_NEXT, f.kind)
            FlagKind.CRUISING -> {
                val page = f.page?.let { id -> PLAYGROUND_PAGES.firstOrNull { it.id == id } }
                if (kid && page != null && page != PLAYGROUND_PAGES.last()) add(RecommendationKind.NEXT_STEP, ActionId.PACE_UNLOCK, f.kind, "page" to page.id)
            }
            FlagKind.REPLAYING_NOT_MOVING_ON -> add(RecommendationKind.MOVE_ON, ActionId.SET_NEXT, f.kind)
            FlagKind.LOW_STARS -> add(RecommendationKind.POLISH, ActionId.REPLAY_FOR_STARS, f.kind, "lesson" to f.lessons.first())
            FlagKind.LEFT_HAND_BEHIND -> add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind,
                "song" to f.songs.firstOrNull(), "lesson" to f.lessons.firstOrNull().takeIf { f.songs.isEmpty() })
            FlagKind.FAR_MISSES -> if (!profile.colourNotes) setting(SettingId.COLOUR_NOTES, "true", f.kind)
            FlagKind.MASTERED_SONG -> if (kid) add(RecommendationKind.NEW_MUSIC, ActionId.TEACHER_ASSIGN, f.kind, "after" to f.songs.first())
            FlagKind.SCATTERED -> if (kid && f.page != null) add(RecommendationKind.FOCUS, ActionId.PACE_FOCUS, f.kind, "page" to f.page)
            FlagKind.TOY_HEAVY -> add(RecommendationKind.ROUTINE, ActionId.SET_NEXT, f.kind)
            FlagKind.LONG_DAYS -> if (kid && current.bedtimeOn != true) setting(SettingId.BEDTIME, "true", f.kind)
            FlagKind.PRACTICE_DIPPED -> add(RecommendationKind.ROUTINE, ActionId.PRACTISE_TOGETHER, f.kind)
            // Colours make a key easier to find; a grown-up sitting in can point at it.
            FlagKind.HESITATES_ON -> {
                if (!profile.colourNotes) setting(SettingId.COLOUR_NOTES, "true", f.kind)
                add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind,
                    "lesson" to f.lessons.firstOrNull(), "song" to f.songs.firstOrNull().takeIf { f.lessons.isEmpty() })
            }
            FlagKind.LEFT_HAND_LAGS -> add(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, f.kind,
                "song" to f.songs.firstOrNull(), "lesson" to f.lessons.firstOrNull().takeIf { f.songs.isEmpty() })
        }; perFlag += mine }
        val out = LinkedHashMap<Pair<ActionId, Map<String, String>>, Candidate>()
        for (i in 0 until (perFlag.maxOfOrNull { it.size } ?: 0)) for (c in perFlag) c.getOrNull(i)?.let { out.putIfAbsent(it.action to it.params, it) }
        return out.values.take(MAX_CANDIDATES)
    }

    // ---- praise ---------------------------------------------------------------------------------------------------

    private fun praise(w: Window, before: List<Window>, all: List<BriefEvent>, trends: List<Trend>, period: Period, endMs: Long, zone: ZoneId): List<Praise> {
        val out = ArrayList<Praise>()
        w.of<BriefEvent.FinalePlayed>().map { it.pageId }.distinct().forEach { out += Praise(PraiseKind.FINALE, page = it) }
        // The first chord ever: a chord lesson finished for the first time, with no chord lesson finished before it.
        w.dones.firstOrNull { d -> d.first && d.lessonId in CHORD_LESSONS &&
            all.none { it is BriefEvent.LessonDone && it.at < d.at && it.lessonId in CHORD_LESSONS } }
            ?.let { out += Praise(PraiseKind.FIRST_CHORD, lessons = listOf(it.lessonId)) }
        val stuckWithIt = w.lessonIds.filter { id ->
            val lastHard = w.steps.filter { it.lessonId == id && (it.letGo || it.tries >= HARD_TRIES) }.maxOfOrNull { it.at }
            lastHard != null && w.dones.any { it.lessonId == id && it.at >= lastHard && it.stars >= 2 }
        }
        if (stuckWithIt.isNotEmpty()) out += Praise(PraiseKind.STUCK_WITH_IT, stuckWithIt.size.toLong(), lessons = stuckWithIt.take(MAX_PER_KIND))
        val stickers = w.of<BriefEvent.StickerDrop>()
        stickers.count { it.rarity == StickerRarity.SHINY }.takeIf { it > 0 }?.let { out += Praise(PraiseKind.SHINY_STICKER, it.toLong()) }
        if (w.goalDays.isNotEmpty()) goalStreak(all, day(endMs, zone), zone).takeIf { it >= 3 }?.let { out += Praise(PraiseKind.GOAL_STREAK, it.toLong()) }
        w.dones.filter { it.stars >= 3 }.sortedByDescending { it.at }.map { it.lessonId }.distinct().takeIf { it.isNotEmpty() }
            ?.let { out += Praise(PraiseKind.THREE_STARS, it.size.toLong(), lessons = it.take(MAX_PER_KIND)) }
        for (t in trends) if (t.good == true) when (t.kind) {
            TrendKind.TIMING_MS -> out += Praise(PraiseKind.TIMING_IMPROVED, t.now.roundToInt().toLong())
            TrendKind.ACCURACY -> out += Praise(PraiseKind.ACCURACY_IMPROVED, t.now.roundToInt().toLong())
            TrendKind.NEAR_SHARE -> out += Praise(PraiseKind.MISSES_CLOSER, t.now.roundToInt().toLong())
            TrendKind.EAR -> out += Praise(PraiseKind.EAR_IMPROVED, t.now.roundToInt().toLong())
            TrendKind.LEFT_HAND -> out += Praise(PraiseKind.LEFT_HAND_IMPROVED, t.now.roundToInt().toLong())
            TrendKind.TEMPO_SPREAD -> out += Praise(PraiseKind.STEADIER_TEMPO, t.now.roundToInt().toLong())
            TrendKind.HESITATION_MS -> out += Praise(PraiseKind.FINDS_KEYS_FASTER, t.now.roundToInt().toLong())
            else -> {}
        }
        consistent(w, before, all, period, endMs, zone)?.let { out += it }
        w.runs.filter { it.stars >= 3 }.map { it.songId }.distinct().sorted().takeIf { it.isNotEmpty() }
            ?.let { out += Praise(PraiseKind.CLEAN_SONG, it.size.toLong(), songs = it.take(MAX_PER_KIND)) }
        stickers.count { it.rarity == StickerRarity.RARE }.takeIf { it > 0 }?.let { out += Praise(PraiseKind.RARE_STICKER, it.toLong()) }
        w.firstFinishes.takeIf { it.isNotEmpty() }?.let { out += Praise(PraiseKind.NEW_LESSONS, it.size.toLong(), lessons = it.take(MAX_PER_KIND)) }
        w.of<BriefEvent.QuestStar>().groupBy { day(it.at, zone) }.count { it.value.size >= 3 }.takeIf { it > 0 }
            ?.let { out += Praise(PraiseKind.QUEST_DAY, it.toLong()) }
        // 2.9: back after days away (the comeback card showed in the range): the latest return.
        w.of<BriefEvent.Comeback>().lastOrNull()?.takeIf { it.daysAway > 0 }?.let { out += Praise(PraiseKind.CAME_BACK, it.daysAway.toLong()) }
        return out.sortedBy { PRAISE_ORDER.indexOf(it.kind) }.take(MAX_PRAISE)
    }

    /** Days in a row with the goal reached, ending on [end] or the day before it. */
    private fun goalStreak(all: List<BriefEvent>, end: LocalDate, zone: ZoneId): Int {
        val days = all.filterIsInstance<BriefEvent.GoalReached>().map { day(it.at, zone) }.toSet()
        var d = if (end in days) end else end.minusDays(1)
        var n = 0
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    private fun consistent(w: Window, before: List<Window>, all: List<BriefEvent>, period: Period, endMs: Long, zone: ZoneId): Praise? {
        val days = if (period == Period.WEEK) w.daysPractised.size
            else Window(startOf(day(endMs, zone).minusDays(6), zone), endMs + 1, all, Sessions.of(all, endMs), zone).daysPractised.size
        val usual = before.takeIf { period == Period.WEEK && it.isNotEmpty() }?.map { it.daysPractised.size }?.average()
        val good = days >= 4 || (days >= 3 && usual != null && days >= usual + 1)
        return if (good) Praise(PraiseKind.CONSISTENT, days.toLong()) else null
    }

    // ---- stats ----------------------------------------------------------------------------------------------------

    private fun stats(w: Window, before: List<Window>, all: List<BriefEvent>, sessions: List<Session>, endMs: Long, zone: ZoneId): Stats {
        val end = day(endMs, zone)
        // 2.9 habits: every day the log saw practice (as daysPractised counts it) for the days back this month and the longest
        // gap; the strip's rest days from the days its paws fill (practice minutes), so a rest paw always sits between two
        // filled ones.
        val practised = Window(Long.MIN_VALUE, endMs + 1, all, sessions, zone).daysPractised
        val minuteDays = sessions.filter { it.practiceSec > 0 && it.start <= endMs }.map { day(it.start, zone) }.toSet()
        val rest = com.bigpapituna.sharpears.engine.PracticeStats.restDays(minuteDays, end)
        val days = (6 downTo 0).map { k ->
            val d = end.minusDays(k.toLong())
            val dw = Window(startOf(d, zone), startOf(d.plusDays(1), zone), all, sessions, zone)
            DayStat(d.toString(), dw.minutes, dw.practiceMinutes, dw.sessions.size, dw.goalDays.isNotEmpty(), rest = d in rest)
        }
        val habits = Habits.of(practised, end, all.firstOrNull()?.let { day(it.at, zone) })
        val lessons = w.lessonIds.map { id ->
            val steps = w.steps.filter { it.lessonId == id }
            val dones = w.dones.filter { it.lessonId == id }
            val ms = dones.sumOf { it.durationMs }.takeIf { it > 0 } ?: steps.sumOf { it.durationMs }
            LessonStat(id, dones.maxOfOrNull { it.stars }, steps.sumOf { it.tries }, steps.count { it.letGo }, dones.size, (ms / 60_000.0).roundToInt())
        }
        val songs = w.songsByRuns.map { id ->
            val r = w.runs.filter { it.songId == id }
            SongStat(id, r.size, r.maxOf { it.stars }, r.mapNotNull { it.accuracy }.maxOrNull())
        }
        // Four weeks ending with the range's end, oldest first.
        val weeks = (3 downTo 0).map { k -> Window(startOf(end.minusDays(6 + 7L * k), zone), startOf(end.minusDays(7L * k - 1), zone), all, sessions, zone) }
        val skills = SkillKind.entries.map { s -> SkillSeries(s, weeks.map { wk -> wk.value(TrendKind.valueOf(s.name))?.let(::round1) }) }
        val toys = ToyKind.entries.mapNotNull { t ->
            val opened = w.of<BriefEvent.ToyOpened>().filter { it.toy == t }
            if (opened.isEmpty()) null else ToyStat(t, opened.size, (opened.sumOf { it.durationMs } / 60_000.0).roundToInt())
        }
        val parts = DayPart.entries.map { p -> PartStat(p, w.minutesByPart[p] ?: 0, w.sessions.count { partOf(it.start, zone) == p }) }
        val games = gameStats(w)
        val stickers = w.of<BriefEvent.StickerDrop>()
        val totals = Totals(
            minutes = w.minutes, practiceMinutes = w.practiceMinutes, sessions = w.sessions.size, daysPractised = w.daysPractised.size,
            lessonsFinished = w.dones.size, newLessons = w.firstFinishes.size, songRuns = w.runs.size, stickers = stickers.size,
            rareStickers = stickers.count { it.rarity == StickerRarity.RARE }, shinyStickers = stickers.count { it.rarity == StickerRarity.SHINY },
            questStars = w.of<BriefEvent.QuestStar>().size, goalDays = w.goalDays.size,
            earRounds = w.ear.size, earCorrect = w.ear.count { it.correct },
        )
        val wrong = w.of<BriefEvent.WrongKey>()
        val keySlips = wrong.groupBy { it.expectedMidi }
            .map { (k, es) -> KeySlips(k, es.filter { it.slip }.sumOf { it.count }, es.filterNot { it.slip }.sumOf { it.count }) }.sortedBy { it.midi }
        val topSlip = wrong.groupingBy { it.expectedMidi to it.playedMidi }.fold(0) { n, e -> n + e.count }.entries
            .maxWithOrNull(compareBy({ it.value }, { -it.key.first }, { -it.key.second }))?.let { SlipPair(it.key.first, it.key.second, it.value) }
        val hesitation = w.hesitationMean?.let { mean ->
            val waits = w.slowKeys.groupBy { it.midi }.mapValues { (_, l) -> l.map { it.ms }.average() to l.size }
            val worst = waits.entries.filter { it.value.second >= 2 }.ifEmpty { waits.entries }
                .maxWithOrNull(compareBy({ it.value.first }, { -it.key }))
            HesitationStat(mean.roundToInt(), w.hesitationN, worst?.key, worst?.value?.first?.roundToInt())
        }
        fun hand(left: Boolean): HandStat? = w.hand(left).takeIf { it.due > 0 || it.hesN > 0 }?.let { h ->
            HandStat(h.due, if (h.due >= HAND_CLEAN_MIN) (h.clean * 100.0 / h.due).roundToInt() else null, h.hesMs.takeIf { h.hesN > 0 })
        }
        val drum = w.drumSteps
        val ear = w.ear.groupBy { it.game }.map { (g, r) -> EarStat(g, r.size, r.count { it.correct }) }.sortedWith(compareBy({ -it.rounds }, { it.game }))
        fun meanSession(ws: List<Window>): Int? = ws.flatMap { it.sessions }.takeIf { it.isNotEmpty() }?.map { it.onScreenMs }?.average()?.let { (it / 60_000.0).roundToInt() }
        fun min(ms: Long) = (ms / 60_000.0).roundToInt()
        return Stats(days, lessons, songs, skills, toys, parts, w.of<BriefEvent.NoisyRoom>().size, w.hintStats(), totals,
            keySlips = keySlips, topSlip = topSlip, notesDue = w.notesDue, skippedNotes = w.skippedNotes, hesitation = hesitation,
            tempoSpreadMs = w.tempoSpread?.roundToInt(), tempoRuns = w.spreads.size, left = hand(true), right = hand(false),
            hintsBefore = before.firstOrNull()?.takeIf { it.steps.isNotEmpty() || it.runs.isNotEmpty() }?.hintStats(),
            clap = drum.takeIf { it.isNotEmpty() }?.let { d -> ClapStat(d.sumOf { it.notes }, d.sumOf { it.rounds }, d.size) },
            ear = ear, games = games, weeks = weeks.map { it.practiceMinutes },
            sessionMinutes = meanSession(listOf(w)), sessionMinutesBefore = meanSession(before),
            mix = PlayMix(min(w.steps.sumOf { it.durationMs }), min(w.of<BriefEvent.SongRun>().sumOf { it.durationMs }), min(w.toyMs)),
            daysBackThisMonth = habits.daysBackThisMonth, longestGap = habits.longestGap,
        )
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    internal fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    internal fun startOf(d: LocalDate, zone: ZoneId): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    internal fun partOf(ms: Long, zone: ZoneId): DayPart = when (Instant.ofEpochMilli(ms).atZone(zone).hour) {
        in 5..11 -> DayPart.MORNING
        in 12..16 -> DayPart.AFTERNOON
        in 17..20 -> DayPart.EVENING
        else -> DayPart.NIGHT
    }
    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0
    private fun fmtDb(v: Double) = if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else round1(v).toString()
}

/**
 * 2.9 habits, from the days practised: [daysBackThisMonth] (the calendar month of [end], up to it) and [longestGap], the
 * longest run of days without practice in the seven days ending [end]. Days before the person's [first] day are not a gap
 * (a new player has no gap yet), nor is [end] itself while it has no practice (the day is not over).
 */
internal data class Habits(val daysBackThisMonth: Int, val longestGap: Int) {
    companion object {
        fun of(practised: Set<LocalDate>, end: LocalDate, first: LocalDate?): Habits {
            val month = practised.count { it.year == end.year && it.month == end.month && !it.isAfter(end) }
            var run = 0; var longest = 0
            for (k in 6 downTo 0) {
                val d = end.minusDays(k.toLong())
                if (first == null || d.isBefore(first)) continue
                if (d in practised) run = 0
                else if (d != end) { run++; longest = maxOf(longest, run) }
            }
            return Habits(month, longest)
        }
    }
}

/** A run's slow key ([RunDetail.slowKeys]) with the lesson or song it was in. */
internal data class SlowKey(val midi: Int, val ms: Int, val lessonId: String?, val songId: String?)

/** A stretch of the app on screen for one person, merged across gaps under [Insights.SESSION_GAP_MS]. */
internal data class Session(val start: Long, val end: Long, val onScreenMs: Long, val practiceSec: Int, val open: Boolean = false)

internal object Sessions {
    private val ACTIVITY = setOf("step", "lesson", "song", "toy", "ear", "noisy", "wrong", "octave")

    /**
     * Sessions from the log: a start opens one, an end closes it with its time on screen and practice; an end with no
     * start stands for its own duration; activity with no session around it (a log begun mid-session, a crash before the
     * end was written) opens one; a start left open ends at its last event (or [now] when that is recent: the session
     * under way). Then stretches closer than the gap merge.
     */
    fun of(sorted: List<BriefEvent>, now: Long): List<Session> {
        val raw = ArrayList<Session>()
        var openStart = -1L
        var lastSeen = -1L
        fun close(end: Long, onScreen: Long, practice: Int, open: Boolean = false) {
            raw += Session(openStart, maxOf(end, openStart), maxOf(onScreen, 0), practice, open)
            openStart = -1
        }
        for (e in sorted) when (e) {
            is BriefEvent.SessionStarted -> { if (openStart >= 0) close(lastSeen, lastSeen - openStart, 0); openStart = e.at; lastSeen = e.at }
            is BriefEvent.SessionEnded -> {
                if (openStart < 0) { openStart = e.at - e.durationMs }
                close(e.at, if (e.durationMs > 0) e.durationMs else e.at - openStart, e.practiceSec)
            }
            else -> if (e.name in ACTIVITY) {
                if (openStart < 0 && (raw.isEmpty() || e.at > raw.last().end + Insights.SESSION_GAP_MS)) openStart = e.at
                if (openStart >= 0) lastSeen = maxOf(lastSeen, e.at)
            }
        }
        if (openStart >= 0) {
            val end = if (now - lastSeen < Insights.SESSION_GAP_MS) now else lastSeen
            close(end, end - openStart, 0, open = true)
        }
        val merged = ArrayList<Session>()
        for (s in raw.sortedBy { it.start }) {
            val p = merged.lastOrNull()
            if (p != null && s.start - p.end < Insights.SESSION_GAP_MS)
                merged[merged.lastIndex] = Session(p.start, maxOf(p.end, s.end), p.onScreenMs + s.onScreenMs, p.practiceSec + s.practiceSec, s.open)
            else merged += s
        }
        return merged
    }
}

/** The events and sessions of one span, with the measures every rule reads. */
internal class Window(val from: Long, val to: Long, all: List<BriefEvent>, allSessions: List<Session>, val zone: ZoneId,
                      sessionsHere: List<Session>? = null) {
    val events: List<BriefEvent> = all.filter { it.at >= from && it.at < to }
    val sessions: List<Session> = sessionsHere ?: allSessions.filter { it.start >= from && it.start < to }

    inline fun <reified T : BriefEvent> of(): List<T> = events.filterIsInstance<T>()

    val steps by lazy { of<BriefEvent.LessonStep>() }
    val dones by lazy { of<BriefEvent.LessonDone>() }
    val runs by lazy { of<BriefEvent.SongRun>().filter { it.mode != RunMode.LISTEN } }
    val ear by lazy { of<BriefEvent.EarRound>() }
    val replays by lazy { dones.filter { !it.first } }
    /** Lessons finished for the first time ever, latest first. */
    val firstFinishes by lazy { dones.filter { it.first }.sortedByDescending { it.at }.map { it.lessonId }.distinct() }
    /** Every lesson played, in the order first played. */
    val lessonIds by lazy { events.mapNotNull { (it as? BriefEvent.LessonStep)?.lessonId ?: (it as? BriefEvent.LessonDone)?.lessonId }.distinct() }
    val songsByRuns by lazy { runs.groupingBy { it.songId }.eachCount().entries.sortedWith(compareBy({ -it.value }, { it.key })).map { it.key } }

    val onScreenMs by lazy { sessions.sumOf { it.onScreenMs } }
    val practiceSec by lazy { sessions.sumOf { it.practiceSec } }
    val minutes get() = (onScreenMs / 60_000.0).roundToInt()
    val practiceMinutes get() = (practiceSec / 60.0).roundToInt()
    val toyMs by lazy { of<BriefEvent.ToyOpened>().sumOf { it.durationMs } }

    /** Days with practice: a session that counted practice time, or a lesson, song or ear game played. */
    val daysPractised: Set<LocalDate> by lazy {
        (sessions.filter { it.practiceSec > 0 }.map { it.start } +
            steps.filter { it.tries > 0 && !it.skipped }.map { it.at } + dones.map { it.at } + runs.map { it.at } + ear.map { it.at })
            .map { Insights.day(it, zone) }.toSortedSet()
    }
    val goalDays: Set<LocalDate> by lazy { of<BriefEvent.GoalReached>().map { Insights.day(it.at, zone) }.toSortedSet() }
    val minutesByDay: Map<LocalDate, Int> by lazy {
        sessions.groupBy { Insights.day(it.start, zone) }.mapValues { (_, s) -> (s.sumOf { it.onScreenMs } / 60_000.0).roundToInt() }
    }
    val minutesByPart: Map<DayPart, Int> by lazy {
        sessions.groupBy { Insights.partOf(it.start, zone) }.mapValues { (_, s) -> (s.sumOf { it.onScreenMs } / 60_000.0).roundToInt() }
    }

    val near by lazy { steps.sumOf { it.nearMisses } + runs.sumOf { it.nearMisses } }
    val far by lazy { steps.sumOf { it.farMisses } + runs.sumOf { it.farMisses } }
    /** One [HintDepth] per stuck wait; a run with more waits than its list keeps counts them all from its detail. */
    val hints: List<Int> by lazy { steps.flatMap { waits(it.hintsUsed, it.detail) } + runs.flatMap { waits(it.hintsUsed, it.detail) } }
    private fun waits(list: List<Int>, d: RunDetail) = if (d.hintSteps.size == 3) d.hintSteps.flatMapIndexed { i, n -> List(n) { i + 1 } } else list
    fun hintStats() = HintStats(hints.count { it == HintDepth.GLOW }, hints.count { it == HintDepth.POINT }, hints.count { it == HintDepth.PLAY },
        of<BriefEvent.OctaveHint>().sumOf { it.count })

    /** Each played run's note-by-note detail (lesson steps and songs, Listen left out). */
    private val details: List<RunDetail> by lazy { steps.map { it.detail } + runs.map { it.detail } }
    val notesDue by lazy { details.sumOf { it.notesDue } }
    val skippedNotes by lazy { details.sumOf { it.skippedNotes } }
    val hesitationN by lazy { details.sumOf { d -> if (d.hesitationMs != null) d.hesitationN else 0 } }
    /** The mean wait for a key over every note timed, or null with none. */
    val hesitationMean: Double? by lazy {
        if (hesitationN == 0) null else details.sumOf { d -> (d.hesitationMs ?: 0).toDouble() * d.hesitationN } / hesitationN
    }
    /** One hand's notes over the window ([left] or the right). */
    fun hand(left: Boolean): HandNotes {
        val hs = details.map { if (left) it.left else it.right }
        val n = hs.sumOf { it.hesN }
        return HandNotes(hs.sumOf { it.due }, hs.sumOf { it.clean }, if (n == 0) 0 else (hs.sumOf { it.hesMs.toLong() * it.hesN }.toDouble() / n).roundToInt(), n)
    }
    /** Each run's slowest keys, with where. */
    val slowKeys: List<SlowKey> by lazy {
        steps.flatMap { s -> s.detail.slowKeys.map { SlowKey(it.midi, it.ms, s.lessonId, null) } } +
            runs.flatMap { r -> r.detail.slowKeys.map { SlowKey(it.midi, it.ms, null, r.songId) } }
    }
    /** Perform runs' tempo spread (ms) with their weight, the notes graded. */
    val spreads: List<Pair<Int, Int>> by lazy {
        steps.mapNotNull { s -> s.detail.spreadMs?.let { it to maxOf(1, s.notes) } } + runs.mapNotNull { r -> r.detail.spreadMs?.let { it to maxOf(1, r.notes) } }
    }
    val tempoSpread: Double? by lazy { if (spreads.isEmpty()) null else spreads.sumOf { it.first.toDouble() * it.second } / spreads.sumOf { it.second } }
    /** The drum steps that counted their beats (game CLAP). */
    val drumSteps by lazy { steps.filter { it.game == "CLAP" && it.rounds > 0 && !it.skipped } }
    val timings: List<Int> by lazy { (steps.mapNotNull { it.timingMeanMs } + runs.mapNotNull { it.timingMeanMs }).map { abs(it) } }
    val accuracies: List<Int> by lazy { steps.mapNotNull { it.accuracy } + runs.mapNotNull { it.accuracy } }
    val claps: List<Int> by lazy { steps.filter { it.game == "CLAP" }.mapNotNull { it.score } }
    /** Runs that played something: songs, and lesson steps with tries (a step passed or let go). */
    val gradedRuns by lazy { runs.size + steps.count { it.tries > 0 && !it.skipped && it.kind != "LISTEN" && it.kind != "READ" } }
    val wrongKeys by lazy { steps.sumOf { it.wrongKeys } + runs.sumOf { it.wrongKeys } }

    fun lastAt(lessonId: String): Long = events.lastOrNull { (it as? BriefEvent.LessonStep)?.lessonId == lessonId || (it as? BriefEvent.LessonDone)?.lessonId == lessonId }?.at ?: 0

    /** A run's share of right notes: its Perform accuracy, or notes over notes and wrong keys when the notes waited. */
    fun hitRate(accuracy: Int?, notes: Int, wrong: Int): Double? = accuracy?.toDouble() ?: if (notes > 0) notes * 100.0 / (notes + wrong) else null
    fun hitRate(s: BriefEvent.LessonStep) = hitRate(s.accuracy, s.notes, s.wrongKeys)

    /** The mean hit rate of runs for [hands] alone, with two runs or more. */
    fun handRate(hands: Hands): Double? {
        val rates = runs.filter { it.hands == hands }.mapNotNull { hitRate(it.accuracy, it.notes, it.wrongKeys) } +
            steps.filter { it.hands == hands && !it.skipped }.mapNotNull { hitRate(it) }
        return if (rates.size >= 2) rates.average() else null
    }

    /** This window's value for a trend (null without enough to measure). */
    fun value(kind: TrendKind): Double? = when (kind) {
        TrendKind.MINUTES -> practiceMinutes.toDouble()
        TrendKind.DAYS -> daysPractised.size.toDouble()
        TrendKind.SESSIONS -> sessions.size.toDouble()
        TrendKind.STARS -> dones.map { it.stars }.takeIf { it.size >= 2 }?.average()
        TrendKind.ACCURACY -> accuracies.takeIf { it.size >= 2 }?.average()
        TrendKind.TIMING_MS -> timings.takeIf { it.size >= 2 }?.average()
        TrendKind.NEAR_SHARE -> if (near + far >= 5) near * 100.0 / (near + far) else null
        TrendKind.WRONG_PER_RUN -> if (gradedRuns >= 3) wrongKeys.toDouble() / gradedRuns else null
        TrendKind.LEFT_HAND -> handRate(Hands.LEFT)
        TrendKind.RIGHT_HAND -> handRate(Hands.RIGHT)
        TrendKind.EAR -> if (ear.size >= 5) ear.count { it.correct } * 100.0 / ear.size else null
        TrendKind.CLAP -> claps.takeIf { it.size >= 2 }?.average()
        TrendKind.PLAY_HINTS -> if (hints.size >= 4) hints.count { it == HintDepth.PLAY } * 100.0 / hints.size else null
        TrendKind.HESITATION_MS -> hesitationMean?.takeIf { hesitationN >= Insights.HESITATION_MIN_NOTES }
        TrendKind.TEMPO_SPREAD -> tempoSpread?.takeIf { spreads.size >= 2 }
    }

    companion object {
        /** One session as a window: its events from its start to its end, and itself. */
        fun of(s: Session, all: List<BriefEvent>, zone: ZoneId) = Window(s.start, s.end + 1, all, emptyList(), zone, listOf(s))
    }
}
