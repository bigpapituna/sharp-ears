package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.brief.BriefEvent

/*
 * 2.15, the app's event taxonomy for Roy's tester dashboard (PostHog). analytics/EVENTS.md is the table Roy reads;
 * analytics/Events.kt sends the ones made on screen; TesterTally makes the rest from the brief's own events. Everything goes
 * out only while Help Roy test is on, on a build with the PostHog key (analytics/Tester.kt).
 *
 * Privacy first: a property is an id of the app's own (a lesson, a bundled song, a game), a number, a bucket or one of a few
 * words. Never a name, an address, a title, typed text, a file or a path ([denied] keeps those words out of every key, and
 * AppEventsTest checks the whole catalog). A song of the person's own goes out as "my_song", never its id (a My songs id is
 * made from its title). Who is playing goes with every event as the super property `profile_kind` (kid, big_kid or adult),
 * with `layout`, `plain_look` (once the plain-mode branch sets it), `app_version`, `is_emulator` and `build_flavor`.
 */
object AppEvents {
    // ---- super properties: sent with every event (registered once the dashboard records) ----
    const val PROFILE_KIND = "profile_kind"
    const val LAYOUT = "layout"
    const val PLAIN_LOOK = "plain_look"
    const val APP_VERSION = "app_version"
    const val IS_EMULATOR = "is_emulator"
    const val BUILD_FLAVOR = "build_flavor"
    /** 2.17: how the person open plays now: mic, midi or touch (engine/nav/FirstRun.inputOf). */
    const val INPUT_MODE = "input_mode"
    val SUPER = listOf(PROFILE_KIND, LAYOUT, PLAIN_LOOK, APP_VERSION, IS_EMULATOR, BUILD_FLAVOR, INPUT_MODE)

    // ---- 2.15 events ----
    const val APP_OPEN = "app_open"
    const val SESSION_LENGTH = "session_length"
    const val PRACTICE_SESSION = "practice_session"
    const val LESSON_STEP_PASSED = "lesson_step_passed"
    const val SONG_PLAYED = "song_played"
    const val CHORD_CHART_PLAYED = "chord_chart_played"
    const val ARCADE_GAME_PLAYED = "arcade_game_played"
    const val SIGHT_DRILL_DONE = "sight_drill_done"
    const val DAILY_PLAN_STARTED = "daily_plan_started"
    const val DAILY_PLAN_FINISHED = "daily_plan_finished"
    const val ONBOARDING_STEP = "onboarding_step"
    const val PLACEMENT_RESULT = "placement_result"
    const val LAYOUT_CHOSEN = "layout_chosen"
    const val BIG_KID_MADE = "big_kid_made"
    const val FAMILY_CARD_OPENED = "family_card_opened"
    const val BRIEF_EMAIL_SET_UP = "brief_email_set_up"
    const val TIME_CAP_SET = "time_cap_set"
    const val TIME_CAP_REACHED = "time_cap_reached"
    const val GATE_OPENED = "gate_opened"
    const val GATE_FAILED = "gate_failed"
    const val TOUR_CARD_SEEN = "tour_card_seen"
    const val MIC_PERMISSION = "mic_permission"
    /** mic-trust's could-not-hear hint (TesterRules.MIC_NOT_HEARD): one per hint shown, the cause only. */
    const val MIC_NOT_HEARD = TesterRules.MIC_NOT_HEARD
    // ---- 2.17 onboarding (engine/nav/FirstRun.kt) ----
    /** The profile's first right note, once: how it was played and how long after the profile first opened. */
    const val FIRST_NOTE = "first_note"
    /** The first playing step's question answered: the piano through the mic, or the screen's keys. */
    const val INPUT_CHOSEN = "input_chosen"
    /** The nudge under the first finished lesson answered: granted (on), denied (Android said no) or not_now. */
    const val NOTIFICATION_ASK = "notification_ask"

    /** A song of the person's own, as every event names it. */
    const val MY_SONG = "my_song"

    /**
     * Every event the app sends and its properties (besides the super properties). The 2.6 to 2.13 events keep their names;
     * `song_run` became `song_played` and `chord_chart_played` in 2.15. `mic_not_heard` (mic-trust) is made by TesterTally
     * from the brief's MicNotHeard, logged once per could-not-hear hint ([micNotHeard]). `$screen` and `$exception` are PostHog's own names.
     */
    val CATALOG: Map<String, List<String>> = linkedMapOf(
        // PostHog's own, with the app's properties
        "\$screen" to listOf("route"),
        "\$exception" to listOf(ErrorReport.WHERE, ErrorReport.HANDLED, ErrorReport.IS_ERROR_TEST, "kind", "status", "problem", "code", "reason", "importance", "clip"),
        // 2.6, tester analytics
        "lesson_started" to listOf("lesson"),
        "lesson_finished" to listOf("lesson", "stars", "tries", "let_go", "seconds"),
        "stage_stuck" to listOf("lesson", "step", "kind", "tries", "let_go"),
        "hint_step" to listOf("lesson", "step", "kind", "tries", "let_go", "hints", "deepest"),
        "toy_opened" to listOf("toy", "seconds"),
        "profile_kind" to listOf("kind"),
        "brief_opened" to listOf("period", "kind"),
        "email_sent_result" to listOf("result"),
        "ai_brief_result" to listOf("result"),
        "error_shown" to listOf("where", "screen"),
        "felt_off" to listOf("screen"),
        TesterRules.DEFAULT_ON_EVENT to emptyList(),
        // 2.9, retention; 2.11 and 2.13, the pops
        Retention.COMEBACK_SHOWN to listOf(PROFILE_KIND),
        Retention.COMEBACK_PLAYED to listOf(PROFILE_KIND),
        Retention.REST_DAY_USED to listOf(PROFILE_KIND),
        Retention.SURPRISE_WRAPPED to listOf(PROFILE_KIND),
        Retention.SURPRISE_OPENED to listOf(PROFILE_KIND),
        StepPopEvent.NAME to listOf(StepPopEvent.STREAK),
        CheerPopEvent.NAME to listOf(CheerPopEvent.TRIGGER, CheerPopEvent.KIND),
        // 2.15
        APP_OPEN to listOf("cold"),
        SESSION_LENGTH to listOf("minutes_bucket"),
        PRACTICE_SESSION to listOf("minutes_bucket", "parts"),
        LESSON_STEP_PASSED to listOf("lesson", "step", "kind", "stars", "tries", "let_go"),
        SONG_PLAYED to listOf("song", "mode", "stars", "accuracy_bucket", "duet"),
        CHORD_CHART_PLAYED to listOf("chart", "mode", "stars"),
        ARCADE_GAME_PLAYED to listOf("game", "level", "stars"),
        SIGHT_DRILL_DONE to listOf("level", "score", "kind"),
        DAILY_PLAN_STARTED to listOf("parts"),
        DAILY_PLAN_FINISHED to listOf("parts", "of", "minutes_bucket", "complete"),
        ONBOARDING_STEP to listOf("step", "result", "who", "level", "goal"),
        PLACEMENT_RESULT to listOf("level"),
        LAYOUT_CHOSEN to listOf("layout"),
        BIG_KID_MADE to emptyList(),
        FAMILY_CARD_OPENED to emptyList(),
        BRIEF_EMAIL_SET_UP to listOf("set_up"),
        TIME_CAP_SET to listOf("minutes"),
        TIME_CAP_REACHED to listOf("minutes"),
        GATE_OPENED to emptyList(),
        GATE_FAILED to emptyList(),
        TOUR_CARD_SEEN to listOf("which"),
        MIC_PERMISSION to listOf("result"),
        MIC_NOT_HEARD to listOf("cause"),
        // 2.17
        FIRST_NOTE to listOf("input", "seconds_since_first_open"),
        INPUT_CHOSEN to listOf("answer"),
        NOTIFICATION_ASK to listOf("result"),
    )

    // ---- the privacy rule ----

    /** Words no property key may hold (whole, or as a part between underscores): Roy's deny-list. */
    val DENIED_WORDS = setOf("name", "email", "title", "text", "file", "path")

    /** Denied words run together, as a key might spell them. */
    private val DENIED_COMPOUNDS = setOf("filename", "filepath", "pathname", "username", "nickname", "firstname", "lastname", "fullname",
        "kidname", "childname", "songtitle", "emailaddress", "subtitle")

    /** True when [key] holds a denied word as one of its parts ("name", "song_title", "fileName", "email_address", "username"). */
    fun denied(key: String): Boolean = key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
        .split('_', '-', '.', ' ').filter { it.isNotEmpty() }.any { it in DENIED_WORDS || it in DENIED_COMPOUNDS }

    // ---- who is playing ----

    /** The profile's kind as every event carries it: "kid" (a little kid, 4 to 7), "big_kid" (8 to 13) or "adult". */
    fun profileKind(kid: Boolean, bigKid: Boolean): String = when { !kid -> "adult"; bigKid -> "big_kid"; else -> "kid" }

    /**
     * An event's own `profile_kind` ([kind], made from a kid flag: "kid" or "adult") agreeing with who is playing ([current],
     * the super property): a big kid's events say "big_kid" too. Anything else stays as it is.
     */
    fun refineKind(kind: Any?, current: String?): Any? = if (kind == "kid" && current == "big_kid") "big_kid" else kind

    // ---- buckets ----

    /** Minutes as a bucket: "0", "1-4", "5-9", "10-19", "20-29", "30-59" or "60+". */
    fun minutesBucket(seconds: Long): String {
        val m = seconds.coerceAtLeast(0) / 60
        return when { m < 1 -> "0"; m < 5 -> "1-4"; m < 10 -> "5-9"; m < 20 -> "10-19"; m < 30 -> "20-29"; m < 60 -> "30-59"; else -> "60+" }
    }

    /** An accuracy percent as a bucket: "0-49", "50-69", "70-84", "85-94" or "95-100". */
    fun accuracyBucket(percent: Int): String = when {
        percent < 50 -> "0-49"; percent < 70 -> "50-69"; percent < 85 -> "70-84"; percent < 95 -> "85-94"; else -> "95-100"
    }

    // ---- ids ----

    private val MY_SONG_ID = Regex("[a-z0-9-]*-[0-9a-f]{6}")

    /**
     * A song's id as it goes out: a song of the person's own (imported, "user-...", or added to My songs, its title's letters
     * and a six-digit hash) is [MY_SONG]; a bundled song keeps its id.
     */
    fun songId(id: String): String = if (id.startsWith("user-") || MY_SONG_ID.matches(id)) MY_SONG else id

    /** A pop run's id ("pop-<song>", engine/chords/PopPlay.RUN_PREFIX) is a chord chart. */
    fun isChart(id: String): Boolean = id.startsWith("pop-")

    // ---- the events ----

    fun appOpen(cold: Boolean) = TesterEvent(APP_OPEN, mapOf("cold" to cold))

    fun sessionLength(durationMs: Long) = TesterEvent(SESSION_LENGTH, mapOf("minutes_bucket" to minutesBucket(durationMs / 1000)))

    fun practiceSession(practiceSec: Int, parts: Int) =
        TesterEvent(PRACTICE_SESSION, mapOf("minutes_bucket" to minutesBucket(practiceSec.toLong()), "parts" to parts))

    /** A lesson step passed (or let go after three tries); null for one skipped with Next or left part way. */
    fun lessonStepPassed(e: BriefEvent.LessonStep): TesterEvent? = if (e.skipped || e.left) null else TesterEvent(LESSON_STEP_PASSED, buildMap {
        put("lesson", e.lessonId); put("step", e.stepIndex); put("kind", e.kind.lowercase()); put("tries", e.tries); put("let_go", e.letGo)
        e.stars?.let { put("stars", it) }
    })

    /** A run played to its end: a chord chart's play-along ([CHORD_CHART_PLAYED]) or a song ([SONG_PLAYED]). */
    fun songRun(e: BriefEvent.SongRun): TesterEvent = if (isChart(e.songId))
        TesterEvent(CHORD_CHART_PLAYED, mapOf("chart" to e.songId.removePrefix("pop-"), "mode" to e.mode.name.lowercase(), "stars" to e.stars))
    else songPlayed(e.songId, e.mode.name.lowercase(), e.stars, e.accuracy, e.duet)

    fun songPlayed(id: String, mode: String, stars: Int?, accuracy: Int?, duet: Boolean = false) = TesterEvent(SONG_PLAYED, buildMap {
        put("song", songId(id)); put("mode", mode); stars?.let { put("stars", it) }
        accuracy?.let { put("accuracy_bucket", accuracyBucket(it)) }; if (duet) put("duet", true)
    })

    /** Hear the band on a chord chart's page (the app plays it). */
    fun chartHeard(chart: String) = TesterEvent(CHORD_CHART_PLAYED, mapOf("chart" to chart.removePrefix("pop-"), "mode" to "listen"))

    fun arcadeGamePlayed(e: BriefEvent.GameRound) = TesterEvent(ARCADE_GAME_PLAYED, mapOf("game" to e.game, "level" to e.level, "stars" to e.stars))

    /** A sight-reading run ended: [kind] "staff" (the note-reading drill, [score] the notes right) or "piece" ([score] its points). */
    fun sightDrillDone(level: Int, score: Int, kind: String) = TesterEvent(SIGHT_DRILL_DONE, mapOf("level" to level, "score" to score, "kind" to kind))

    fun dailyPlanStarted(parts: Int) = TesterEvent(DAILY_PLAN_STARTED, mapOf("parts" to parts))

    /** Practice today ended: [done] of [of] parts, [practiceSec] the practice it added, [complete] when the plan is done for today. */
    fun dailyPlanFinished(done: Int, of: Int, practiceSec: Long, complete: Boolean) =
        TesterEvent(DAILY_PLAN_FINISHED, mapOf("parts" to done, "of" to of, "minutes_bucket" to minutesBucket(practiceSec), "complete" to complete))

    /** The onboarding's cards, in order (OnboardingScreen's steps; 2.17 dropped "habit", the reminder and how you play). */
    val ONBOARDING_STEPS = listOf("welcome", "experience", "goal")

    /**
     * An onboarding card left forward ([completed]) or with Skip, with the answer it holds (2.17): the welcome card's [who]
     * ("me" or "child", when it was asked), the experience card's [level] (never, a_little, some, a_lot), the goal card's
     * [goal] (minutes a day).
     */
    fun onboardingStep(step: Int, completed: Boolean, who: String? = null, level: String? = null, goal: Int? = null) = TesterEvent(ONBOARDING_STEP, buildMap {
        put("step", ONBOARDING_STEPS.getOrNull(step) ?: "step_$step"); put("result", if (completed) "completed" else "skipped")
        who?.let { put("who", it) }; level?.let { put("level", it.lowercase()) }; goal?.let { put("goal", it) }
    })

    /** 2.17: the profile's first right note ([input] mic, midi or touch), [seconds] after it first opened. */
    fun firstNote(input: String, seconds: Long) = TesterEvent(FIRST_NOTE, mapOf("input" to input, "seconds_since_first_open" to seconds))

    /** 2.17: the first playing step's question: "mic" (Use my piano) or "touch" (Use screen keys). */
    fun inputChosen(mic: Boolean) = TesterEvent(INPUT_CHOSEN, mapOf("answer" to if (mic) "mic" else "touch"))

    /** 2.17: the nudge's answer: granted, denied or not_now. */
    fun notificationAsk(result: String) = TesterEvent(NOTIFICATION_ASK, mapOf("result" to result))

    fun bigKidMade() = TesterEvent(BIG_KID_MADE)
    fun familyCardOpened() = TesterEvent(FAMILY_CARD_OPENED)
    fun briefEmailSetUp(yes: Boolean) = TesterEvent(BRIEF_EMAIL_SET_UP, mapOf("set_up" to if (yes) "yes" else "no"))
    fun timeCapReached(minutes: Int) = TesterEvent(TIME_CAP_REACHED, mapOf("minutes" to minutes))
    fun gateOpened() = TesterEvent(GATE_OPENED)
    fun gateFailed() = TesterEvent(GATE_FAILED)
    fun tourCardSeen(which: String) = TesterEvent(TOUR_CARD_SEEN, mapOf("which" to which.lowercase()))
    fun micPermission(granted: Boolean) = TesterEvent(MIC_PERMISSION, mapOf("result" to if (granted) "granted" else "denied"))

    /** The could-not-hear hint showed (the brief's MicNotHeard, one per hint): its cause, "noise" or "silence", nothing else. */
    fun micNotHeard(e: BriefEvent.MicNotHeard) = TesterEvent(MIC_NOT_HEARD, mapOf("cause" to e.cause.wire))

    /**
     * A setting the brief logged (Briefs.setting / settingFor) that the dashboard tracks: the layout chosen, a kid's daily time
     * set (0 is off), a piano level set (the onboarding's answer, the placement check, Family > Piano level). Else null.
     */
    fun fromSetting(e: BriefEvent.SettingChanged): TesterEvent? = when (e.key) {
        "layout" -> TesterEvent(LAYOUT_CHOSEN, mapOf("layout" to e.value.lowercase()))
        "capMin" -> e.value.toIntOrNull()?.let { TesterEvent(TIME_CAP_SET, mapOf("minutes" to it)) }
        "level" -> TesterEvent(PLACEMENT_RESULT, mapOf("level" to e.value.lowercase()))
        else -> null
    }

    /** What kind of play a brief event is, for a session's `parts`; null for anything that is not play. */
    fun partOf(e: BriefEvent): String? = when (e) {
        is BriefEvent.LessonStep, is BriefEvent.LessonDone -> "lesson"
        is BriefEvent.SongRun -> if (isChart(e.songId)) "chart" else "song"
        is BriefEvent.GameRound -> "arcade"
        is BriefEvent.EarRound -> "ear"
        is BriefEvent.ToyOpened -> "toy"
        else -> null
    }
}
