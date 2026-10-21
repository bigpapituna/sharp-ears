package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.MiniJson

/*
 * The parent brief's event log (docs/PARENT-BRIEF-PLAN.md, "What has to be built underneath"): what a person did in the
 * app, one event per line, in their own file (data/EventLog.kt, files/events/<profileId>.log). The body never names the
 * person: the file is theirs, so an event carries only ids (lessons, songs, pages), numbers and enums. Insights.kt reads
 * them back into Findings.
 *
 * Wire format: one JSON object per line, written with engine/MiniJson: "t" the epoch ms, "e" the event's name, then its
 * fields, each left out while it holds its default (0, false, null, empty), so a line stays short:
 *   {"t":1792000000000,"e":"step","lesson":"pond-3","step":2,"kind":"PLAY","tries":3,"letGo":true,"hints":[1,3],"ms":61000}
 * Reading is tolerant both ways: unknown fields are ignored, and a line whose name (or a required field) this version
 * does not know comes back as [BriefEvent.Unknown] with its fields, so a newer app's events survive a backup through an
 * older one and are skipped by Insights. A line that is not JSON at all reads as null.
 */

/** How a run was played: the notes wait, they keep moving (graded in time), or the app played it. */
enum class RunMode { WAIT, PERFORM, LISTEN }

/** The hands a run asked for. */
enum class Hands { RIGHT, LEFT, BOTH }

/** The play corner's toys; on the wire as jam, pupSays and clapAlong. */
enum class ToyKind(val wire: String) {
    JAM("jam"), PUP_SAYS("pupSays"), CLAP_ALONG("clapAlong");

    companion object {
        fun of(s: String?): ToyKind? = entries.firstOrNull { it.wire == s || it.name == s }
    }
}

/** A sticker's rarity (engine/world Rarity, kept separately so the log's words never change with the world's). */
enum class StickerRarity { COMMON, RARE, SHINY }

/**
 * How far hint escalation went in one wait (engine/hints HintPlan): the deepest step reached, one entry per wait that
 * needed a hint. A list [1, 3, 2] is three stuck moments: one ended at the glow, one with the app playing the key, one
 * with the hand pointing.
 */
object HintDepth {
    const val GLOW = 1
    const val POINT = 2
    const val PLAY = 3
}

/**
 * One thing that happened, at [at] (epoch ms, the phone's wall clock). Each subclass names itself on the wire ([name])
 * and writes its fields; see the file comment for the format and CONTRACT.md for who emits what.
 */
sealed class BriefEvent {
    abstract val at: Long
    abstract val name: String
    internal abstract fun body(m: MutableMap<String, Any?>)

    /** One line of the log: compact JSON, no line breaks (MiniJson escapes them inside strings). */
    fun encode(): String = MiniJson.write(LinkedHashMap<String, Any?>().apply { put("t", at); put("e", name); body(this) })

    /** The app came on screen for this person (a launch, a return from another app, a switch to them). */
    data class SessionStarted(override val at: Long) : BriefEvent() {
        override val name get() = "start"
        override fun body(m: MutableMap<String, Any?>) {}
    }

    /**
     * The app went off screen (or another person took over): [durationMs] on screen since [SessionStarted] (the
     * ForegroundClock's time, never time away), and [practiceSec] the practice seconds counted meanwhile (what the daily
     * goal and the paw prints count: time a player, stage or game ran).
     */
    data class SessionEnded(override val at: Long, val durationMs: Long, val practiceSec: Int = 0) : BriefEvent() {
        override val name get() = "end"
        override fun body(m: MutableMap<String, Any?>) { m.opt("ms", durationMs, 0L); m.opt("practice", practiceSec, 0) }
    }

    /**
     * A lesson step was left forward: passed (or let go after three tries), or skipped with Next ([skipped]); or it was
     * left without passing after at least one try, by Back or by closing the lesson ([left]: [tries] are then the tries
     * that did not pass). [kind] is the
     * engine's StepKind name (READ, LISTEN, PLAY, FROM_MEMORY, PERFORM, RAMP, PEDAL, DYNAMICS), [game] the Playground game
     * of its stage (KEY_HUNT, CLAP, ...) or null, [hands] what its exercise asked for. [tries] counts its runs, the passing
     * one included (0 for a step with nothing to play). [hintsUsed] has one [HintDepth] per stuck wait over all tries.
     * [stars] are the passing run's (engine/kid LessonStars), [accuracy] and [timingMeanMs] a Perform run's, [score] a
     * stage's own percent (the drum's beats with a clap, loudness), [notes] the groups played, [wrongKeys] with
     * [nearMisses] (two semitones or closer) and [farMisses] summed over every try, [letGoRounds] of [rounds] a stage
     * let go. [durationMs] is time on screen from the step opening to it ending.
     */
    data class LessonStep(
        override val at: Long, val lessonId: String, val stepIndex: Int, val kind: String,
        val tries: Int = 0, val letGo: Boolean = false, val hintsUsed: List<Int> = emptyList(), val durationMs: Long = 0,
        val game: String? = null, val hands: Hands? = null, val skipped: Boolean = false,
        val stars: Int? = null, val accuracy: Int? = null, val score: Int? = null, val timingMeanMs: Int? = null,
        val notes: Int = 0, val wrongKeys: Int = 0, val nearMisses: Int = 0, val farMisses: Int = 0,
        val letGoRounds: Int = 0, val rounds: Int = 0, val left: Boolean = false,
    ) : BriefEvent() {
        override val name get() = "step"
        override fun body(m: MutableMap<String, Any?>) {
            m["lesson"] = lessonId; m["step"] = stepIndex; m["kind"] = kind
            m.opt("tries", tries, 0); m.opt("letGo", letGo, false); m.opt("hints", hintsUsed); m.opt("ms", durationMs, 0L)
            m.opt("game", game); m.opt("hands", hands?.name); m.opt("skipped", skipped, false)
            m.opt("stars", stars); m.opt("acc", accuracy); m.opt("score", score); m.opt("timing", timingMeanMs)
            m.opt("notes", notes, 0); m.opt("wrong", wrongKeys, 0); m.opt("near", nearMisses, 0); m.opt("far", farMisses, 0)
            m.opt("letGoRounds", letGoRounds, 0); m.opt("rounds", rounds, 0); m.opt("left", left, false)
        }
    }

    /** A lesson's last step ended: its [stars] (1 to 3), [durationMs] on screen, and whether it was the [first] finish ever. */
    data class LessonDone(override val at: Long, val lessonId: String, val stars: Int, val durationMs: Long = 0, val first: Boolean = false) : BriefEvent() {
        override val name get() = "lesson"
        override fun body(m: MutableMap<String, Any?>) {
            m["lesson"] = lessonId; m["stars"] = stars; m.opt("ms", durationMs, 0L); m.opt("first", first, false)
        }
    }

    /**
     * A library song played to its end by the player (not Listen, not an empty run): [mode], [hands], a Perform run's
     * [accuracy] and [timingMeanMs] (its mean offset, negative early), the recap's [stars], [notes] played, [wrongKeys] with
     * [nearMisses] and [farMisses], the [hintsUsed] per stuck wait, [durationMs] of real time, whether it ran [fromTop] and
     * whether it was a [duet].
     */
    data class SongRun(
        override val at: Long, val songId: String, val mode: RunMode, val hands: Hands, val accuracy: Int? = null, val stars: Int,
        val timingMeanMs: Int? = null, val nearMisses: Int = 0, val farMisses: Int = 0,
        val notes: Int = 0, val wrongKeys: Int = 0, val hintsUsed: List<Int> = emptyList(), val durationMs: Long = 0,
        val fromTop: Boolean = true, val duet: Boolean = false,
    ) : BriefEvent() {
        override val name get() = "song"
        override fun body(m: MutableMap<String, Any?>) {
            m["song"] = songId; m["mode"] = mode.name; m["hands"] = hands.name; m.opt("acc", accuracy); m["stars"] = stars
            m.opt("timing", timingMeanMs); m.opt("near", nearMisses, 0); m.opt("far", farMisses, 0)
            m.opt("notes", notes, 0); m.opt("wrong", wrongKeys, 0); m.opt("hints", hintsUsed); m.opt("ms", durationMs, 0L)
            m.opt("fromTop", fromTop, true); m.opt("duet", duet, false)
        }
    }

    /**
     * Wrong keys of one run, aggregated (never one event per key): [playedMidi] was struck [count] times where
     * [expectedMidi] was the nearest note wanted; [slip] when within two semitones. Written for the run's most frequent
     * pairs only (RunTally.TOP_PAIRS), in a lesson ([lessonId]) or a song ([songId]).
     */
    data class WrongKey(
        override val at: Long, val expectedMidi: Int, val playedMidi: Int, val slip: Boolean, val count: Int = 1,
        val lessonId: String? = null, val songId: String? = null,
    ) : BriefEvent() {
        override val name get() = "wrong"
        override fun body(m: MutableMap<String, Any?>) {
            m["expected"] = expectedMidi; m["played"] = playedMidi; m.opt("slip", slip, false); m.opt("count", count, 1)
            m.opt("lesson", lessonId); m.opt("song", songId)
        }
    }

    /** The octave hint ("the right note, one octave up") came [count] times in one run of a lesson step or a song. */
    data class OctaveHint(override val at: Long, val lessonId: String? = null, val songId: String? = null, val count: Int = 1) : BriefEvent() {
        override val name get() = "octave"
        override fun body(m: MutableMap<String, Any?>) { m.opt("lesson", lessonId); m.opt("song", songId); m.opt("count", count, 1) }
    }

    /**
     * The room got in the way: [screen] "player" when the room-noise watchdog offered to stop painting wrong keys, "stage"
     * (or "stage:<GAME>") when a Playground stage heard sounds and no key for eight seconds.
     */
    data class NoisyRoom(override val at: Long, val screen: String) : BriefEvent() {
        override val name get() = "noisy"
        override fun body(m: MutableMap<String, Any?>) { m["screen"] = screen }
    }

    /** A toy of the play corner was open for [durationMs] on screen (written when it closes). */
    data class ToyOpened(override val at: Long, val toy: ToyKind, val durationMs: Long) : BriefEvent() {
        override val name get() = "toy"
        override fun body(m: MutableMap<String, Any?>) { m["toy"] = toy.wire; m.opt("ms", durationMs, 0L) }
    }

    /**
     * One round of an ear or chord game answered: [game] is the ear training kind's id (rhythm, melody, pitch, degree,
     * prog) or "chord:" and the chord game's id (build, hear, mm, int, back); [correct] when it earned its point.
     */
    data class EarRound(override val at: Long, val game: String, val correct: Boolean, val level: Int? = null) : BriefEvent() {
        override val name get() = "ear"
        override fun body(m: MutableMap<String, Any?>) { m["game"] = game; m["correct"] = correct; m.opt("level", level) }
    }

    /** A task of Pup's quest was completed ([task] is the engine/play QuestKind name): a star. */
    data class QuestStar(override val at: Long, val task: String) : BriefEvent() {
        override val name get() = "quest"
        override fun body(m: MutableMap<String, Any?>) { m["task"] = task }
    }

    /** A sticker dropped ([source] "lesson", "song" or "egg"), [isNew] when it filled a gap in the album. */
    data class StickerDrop(override val at: Long, val rarity: StickerRarity, val stickerId: String? = null, val isNew: Boolean = false,
                           val source: String? = null) : BriefEvent() {
        override val name get() = "sticker"
        override fun body(m: MutableMap<String, Any?>) {
            m["rarity"] = rarity.name; m.opt("sticker", stickerId); m.opt("new", isNew, false); m.opt("source", source)
        }
    }

    /** Today's practice reached the daily goal of [goalMin] minutes (once a day). */
    data class GoalReached(override val at: Long, val goalMin: Int = 0) : BriefEvent() {
        override val name get() = "goal"
        override fun body(m: MutableMap<String, Any?>) { m.opt("goal", goalMin, 0) }
    }

    /** The pup said goodnight (bedtime on, the goal reached): lessons sleep until tomorrow. */
    data class BedtimeShown(override val at: Long) : BriefEvent() {
        override val name get() = "bedtime"
        override fun body(m: MutableMap<String, Any?>) {}
    }

    /**
     * A setting changed for this person (or for the phone while they were playing): [key] and its new [value] as text.
     * Keys in use: reader, colourNotes, micOffsetDb, hintSeconds, goalMin, kind, bedtime, wrongFlash, sensitivityDb,
     * keyLabels, noteNames, fingerNumbers, feedbackLight, beatLines, ghostHand, sound, haptics, voice, naturalVoice,
     * reminderOn. Never a name or anything typed.
     */
    data class SettingChanged(override val at: Long, val key: String, val value: String) : BriefEvent() {
        override val name get() = "setting"
        override fun body(m: MutableMap<String, Any?>) { m["key"] = key; m["value"] = value }
    }

    /**
     * A grown-up acted on a brief's recommendation ([actionId] an ActionId name, Findings.kt) with its [params] (lesson,
     * song, page, setting, value; a teacher's note is the only free text and never leaves the phone, see
     * Findings.redacted).
     */
    data class ParentAction(override val at: Long, val actionId: String, val params: Map<String, String> = emptyMap()) : BriefEvent() {
        override val name get() = "action"
        override fun body(m: MutableMap<String, Any?>) { m["action"] = actionId; m.opt("params", params) }
    }

    /** A Playground page's finale played ([pageId], engine/playground PLAYGROUND_PAGES). */
    data class FinalePlayed(override val at: Long, val pageId: String) : BriefEvent() {
        override val name get() = "finale"
        override fun body(m: MutableMap<String, Any?>) { m["page"] = pageId }
    }

    /**
     * A line this version cannot read as one of the above (a newer app's event, or a field it does not understand):
     * kept with its [fields] so it is written back unchanged, and skipped by Insights.
     */
    data class Unknown(override val at: Long, override val name: String, val fields: Map<String, Any?>) : BriefEvent() {
        override fun body(m: MutableMap<String, Any?>) { fields.forEach { (k, v) -> if (k != "t" && k != "e") m[k] = v } }
    }

    companion object {
        /** Reads one line written by [encode]; null for a line that is not an event at all (no JSON object, no time). */
        fun decode(line: String): BriefEvent? {
            val m = runCatching { MiniJson.read(line.trim()) }.getOrNull() as? Map<*, *> ?: return null
            val at = (m["t"] as? Number)?.toLong() ?: return null
            val name = m["e"] as? String ?: return null
            val f = Fields(m)
            return runCatching { read(at, name, f) }.getOrNull()
                ?: Unknown(at, name, m.entries.associate { it.key.toString() to it.value }.filterKeys { it != "t" && it != "e" })
        }

        private fun read(at: Long, name: String, f: Fields): BriefEvent? = when (name) {
            "start" -> SessionStarted(at)
            "end" -> SessionEnded(at, f.long("ms"), f.int("practice"))
            "step" -> LessonStep(at, f.str("lesson")!!, f.int("step"), f.str("kind")!!,
                tries = f.int("tries"), letGo = f.bool("letGo"), hintsUsed = f.ints("hints"), durationMs = f.long("ms"),
                game = f.str("game"), hands = f.str("hands")?.let { h -> Hands.valueOf(h) }, skipped = f.bool("skipped"),
                stars = f.intOrNull("stars"), accuracy = f.intOrNull("acc"), score = f.intOrNull("score"), timingMeanMs = f.intOrNull("timing"),
                notes = f.int("notes"), wrongKeys = f.int("wrong"), nearMisses = f.int("near"), farMisses = f.int("far"),
                letGoRounds = f.int("letGoRounds"), rounds = f.int("rounds"), left = f.bool("left"))
            "lesson" -> LessonDone(at, f.str("lesson")!!, f.intOrNull("stars")!!, f.long("ms"), f.bool("first"))
            "song" -> SongRun(at, f.str("song")!!, RunMode.valueOf(f.str("mode")!!), Hands.valueOf(f.str("hands")!!), f.intOrNull("acc"),
                f.intOrNull("stars")!!, f.intOrNull("timing"), f.int("near"), f.int("far"), f.int("notes"), f.int("wrong"),
                f.ints("hints"), f.long("ms"), f.bool("fromTop", true), f.bool("duet"))
            "wrong" -> WrongKey(at, f.intOrNull("expected")!!, f.intOrNull("played")!!, f.bool("slip"), f.int("count", 1), f.str("lesson"), f.str("song"))
            "octave" -> OctaveHint(at, f.str("lesson"), f.str("song"), f.int("count", 1))
            "noisy" -> NoisyRoom(at, f.str("screen")!!)
            "toy" -> ToyOpened(at, ToyKind.of(f.str("toy"))!!, f.long("ms"))
            "ear" -> EarRound(at, f.str("game")!!, f.bool("correct"), f.intOrNull("level"))
            "quest" -> QuestStar(at, f.str("task")!!)
            "sticker" -> StickerDrop(at, StickerRarity.valueOf(f.str("rarity")!!), f.str("sticker"), f.bool("new"), f.str("source"))
            "goal" -> GoalReached(at, f.int("goal"))
            "bedtime" -> BedtimeShown(at)
            "setting" -> SettingChanged(at, f.str("key")!!, f.str("value")!!)
            "action" -> ParentAction(at, f.str("action")!!, f.strings("params"))
            "finale" -> FinalePlayed(at, f.str("page")!!)
            else -> null
        }
    }
}

/** Puts [v] unless it is [default], null, or an empty list or map: the log leaves defaults out. */
private fun MutableMap<String, Any?>.opt(k: String, v: Any?, default: Any? = null) {
    if (v == null || v == default || (v is Collection<*> && v.isEmpty()) || (v is Map<*, *> && v.isEmpty())) return
    put(k, v)
}

/** Typed reads of a decoded line, each with the default the writer left out. */
private class Fields(private val m: Map<*, *>) {
    fun str(k: String): String? = m[k] as? String
    fun intOrNull(k: String): Int? = (m[k] as? Number)?.toInt()
    fun int(k: String, default: Int = 0): Int = intOrNull(k) ?: default
    fun long(k: String): Long = (m[k] as? Number)?.toLong() ?: 0L
    fun bool(k: String, default: Boolean = false): Boolean = m[k] as? Boolean ?: default
    fun ints(k: String): List<Int> = (m[k] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }.orEmpty()
    fun strings(k: String): Map<String, String> = (m[k] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
}
