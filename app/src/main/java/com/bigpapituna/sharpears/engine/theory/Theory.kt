package com.bigpapituna.sharpears.engine.theory

/**
 * The Theory Academy as data: units of lessons; a lesson is an animated explainer (scenes) and then a short quiz.
 * A scene is a caption the pup says and a [Visual]: what is drawn and played, as a pure function of time. The UI
 * draws a visual at any moment from its timing here and plays its [Visual.cues]; the end state is what is drawn
 * at [Visual.durationMs] (and what a quiz shows as a still picture).
 */
data class TheoryUnit(val id: String, val number: Int, val title: String, val about: String, val lessons: List<TheoryLesson>)

data class TheoryLesson(val id: String, val title: String, val summary: String, val scenes: List<Scene>, val quiz: List<Question>)

/** How the pup looks while he says a scene's caption. */
enum class Mood { EXPLAIN, POINT, LISTEN, PLAY, CHEER, THINK }

/** One beat of an explainer: [caption] in the pup's bubble (and [say] spoken), over an animated [visual]. */
data class Scene(val caption: String, val visual: Visual, val mood: Mood = Mood.EXPLAIN, val say: String = caption)

/** A sound at [atMs]: [notes] held for [holdMs], or a metronome [click]. */
data class Cue(val atMs: Int, val notes: List<Int> = emptyList(), val holdMs: Int = 600, val click: Click? = null, val velocity: Int = 80)

enum class Click { BEAT, ACCENT }

sealed interface Visual {
    /** How long the animation runs, in ms; at this time and after, the end state is drawn. */
    val durationMs: Int
    /** The sounds, in time order. */
    val cues: List<Cue>
}

/** How far through a [length] ms move that started at [start] we are at [t]: 0 before, 1 after. */
fun progress(t: Int, start: Int, length: Int): Float = if (length <= 0) (if (t >= start) 1f else 0f) else ((t - start).toFloat() / length).coerceIn(0f, 1f)

// ---- Keyboard ----------------------------------------------------------------------------------------------------

/** Colours a lit key can take: the accent, the left-hand blue (a second voice), green (right), or grey (context). */
enum class Tint { ACCENT, BLUE, GREEN, DIM }

/** Which key names are written on the keys. */
enum class KeyNames { NONE, LIT, WHITES, C_ONLY }

/**
 * One moment of a keyboard scene: [notes] light (and sound with [play]). [gap] labels the step from the previous
 * frame's top note to this one's lowest ("W", "H"); [label] is a line drawn above the keys; [names] override the
 * text on a key (to show both names of a black key). [clear] turns off what earlier frames lit.
 */
data class KeyFrame(
    val notes: List<Int>, val label: String? = null, val gap: String? = null, val play: Boolean = true,
    val tint: Tint = Tint.ACCENT, val names: Map<Int, String> = emptyMap(), val clear: Boolean = false,
)

/** Keys light up one frame at a time, every [stepMs]; with [finale] the lit notes then sound together. */
data class KeysVisual(
    val low: Int, val high: Int, val frames: List<KeyFrame>, val stepMs: Int = 700, val names: KeyNames = KeyNames.LIT,
    val flats: Boolean = false, val finale: List<Int> = emptyList(), val leadMs: Int = 500,
) : Visual {
    fun frameAt(i: Int) = leadMs + i * stepMs
    val finaleAt get() = frameAt(frames.size) + 150
    override val durationMs get() = if (finale.isEmpty()) frameAt(frames.size) + 200 else finaleAt + 1500
    override val cues get() = frames.mapIndexedNotNull { i, f -> if (f.play && f.notes.isNotEmpty()) Cue(frameAt(i), f.notes, (stepMs * 0.92f).toInt()) else null } +
        listOfNotNull(finale.takeIf { it.isNotEmpty() }?.let { Cue(finaleAt, it, 1400) })
    /** Frames shown at [t]: the index of the newest frame lit, or -1 before the first. */
    fun current(t: Int) = if (t < leadMs) -1 else minOf(frames.lastIndex, (t - leadMs) / stepMs)
}

// ---- Staff -------------------------------------------------------------------------------------------------------

enum class NoteValue(val beats: Float, val label: String) {
    WHOLE(4f, "whole"), HALF(2f, "half"), QUARTER(1f, "quarter"), EIGHTH(0.5f, "eighth"), SIXTEENTH(0.25f, "sixteenth")
}

/** What the staff spells out around the clef: line numbers, line letters (E G B D F) or space letters (F A C E). */
enum class Marks { NONE, LINE_NUMBERS, LINE_NAMES, SPACE_NAMES }

/**
 * A note (or chord, or rest) on the staff. [tie] ties it to the next note (one sound, held); [label] is written
 * under it. On a grand staff, [staff] picks the stave (default: middle C and up in the treble).
 */
data class StaffNote(
    val pitches: List<Pitch>, val value: NoteValue = NoteValue.QUARTER, val dotted: Boolean = false, val rest: Boolean = false,
    val tie: Boolean = false, val label: String? = null, val staff: Clef? = null,
) {
    val beats get() = value.beats * if (dotted) 1.5f else 1f
}

/**
 * A staff that draws itself: the five lines sweep in, the clef is traced stroke by stroke, then [marks], the key
 * signature ([keyFifths]) and time signature pop in, and the notes slide in from the right one by one, each
 * sounding as it lands. With [bpm] the notes land in rhythm (their values heard); otherwise every [stepMs].
 * [grand]: a treble and a bass staff joined, with middle C between them.
 */
data class StaffVisual(
    val clef: Clef, val notes: List<StaffNote> = emptyList(), val drawClef: Boolean = true, val keyFifths: Int = 0,
    val time: Pair<Int, Int>? = null, val marks: Marks = Marks.NONE, val bpm: Int = 0, val stepMs: Int = 750,
    val grand: Boolean = false, val play: Boolean = true, val names: Boolean = false, val barlines: List<Int> = emptyList(),
) : Visual {
    val linesMs get() = 600
    val clefStart get() = linesMs
    val clefMs get() = if (drawClef) 1100 else 0
    val marksStart get() = clefStart + clefMs
    val marksCount get() = when (marks) { Marks.NONE -> 0; Marks.LINE_NUMBERS, Marks.LINE_NAMES -> 5; Marks.SPACE_NAMES -> 4 }
    val markMs get() = 420
    val keyStart get() = marksStart + marksCount * markMs + if (marksCount > 0) 300 else 0
    val keyMs get() = 260
    val timeStart get() = keyStart + kotlin.math.abs(keyFifths) * keyMs
    val notesStart get() = timeStart + (if (time != null) 500 else 0) + 450
    private val beatMs get() = if (bpm > 0) 60_000f / bpm else 0f

    /** When note [i] lands. */
    fun arrival(i: Int): Int = if (bpm > 0) notesStart + (notes.take(i).sumOf { it.beats.toDouble() } * beatMs).toInt() else notesStart + i * stepMs
    /** How long a landed note sounds. */
    private fun hold(i: Int): Int {
        if (bpm <= 0) return (stepMs * 0.9f).toInt()
        var beats = notes[i].beats
        var j = i
        while (notes[j].tie && j + 1 < notes.size) { j++; beats += notes[j].beats }
        return (beats * beatMs * 0.94f).toInt()
    }
    /** How far note [i] has slid in at [t] (0 off to the right, 1 landed). */
    fun slide(i: Int, t: Int) = progress(t, arrival(i) - SLIDE_MS, SLIDE_MS)

    override val durationMs get() = if (notes.isEmpty()) notesStart else arrival(notes.lastIndex) + maxOf(hold(notes.lastIndex), 500) + 250
    override val cues get() = if (!play) emptyList() else notes.mapIndexedNotNull { i, n ->
        val tiedFromBefore = i > 0 && notes[i - 1].tie
        if (n.rest || tiedFromBefore) null else Cue(arrival(i), n.pitches.map { it.midi }, hold(i))
    }

    companion object { const val SLIDE_MS = 420 }
}

// ---- Rhythm ------------------------------------------------------------------------------------------------------

/** A note or rest on the rhythm grid, in counted beats from the start of the pattern. */
data class Hit(val start: Float, val length: Float, val rest: Boolean = false)

/**
 * A rhythm grid: the bar's beats as columns, notes as bars of their length, a cursor sweeping across at [bpm] for
 * [loops] times. Each beat clicks (the [strong] ones accented) and each note sounds [pitch] as the cursor reaches it.
 * [counts] are the words under the grid, one per subdivision ([sub] per beat), lit as the cursor passes.
 * [top]/[bottom] is the time signature shown; [term] a tempo marking shown beside it.
 */
data class RhythmVisual(
    val top: Int, val bottom: Int, val bpm: Int, val hits: List<Hit>, val bars: Int = 1, val sub: Int = 1,
    val counts: List<String> = emptyList(), val loops: Int = 2, val pitch: Int = 72, val strong: Set<Int> = setOf(0),
    val term: String? = null, val clicks: Boolean = true,
) : Visual {
    val leadMs get() = 700
    val beatMs get() = 60_000f / bpm
    val beats get() = top * bars
    val loopMs get() = (beats * beatMs).toInt()
    override val durationMs get() = leadMs + loops * loopMs + 300
    /** The cursor in beats from the pattern start, or null before it starts and after it ends. */
    fun cursor(t: Int): Float? = if (t < leadMs || t >= leadMs + loops * loopMs) null else ((t - leadMs) / beatMs) % beats
    override val cues get() = (0 until loops).flatMap { loop ->
        val base = leadMs + loop * loopMs
        val ticks = if (clicks) (0 until beats).map { b -> Cue((base + b * beatMs).toInt(), click = if (b % top in strong) Click.ACCENT else Click.BEAT) } else emptyList()
        val notes = hits.filter { !it.rest }.map { h -> Cue((base + h.start * beatMs).toInt(), listOf(pitch), (h.length * beatMs * 0.85f).toInt(), velocity = 90) }
        ticks + notes
    }.sortedBy { it.atMs }
}

// ---- Intervals ---------------------------------------------------------------------------------------------------

data class IntervalPair(val low: Pitch, val high: Pitch, val name: String, val note: String? = null)

/**
 * Two keys and an arc between them: the low key sounds, the arc stretches up to the high key counting half steps
 * as it goes, the high key sounds, then both together. One [IntervalPair] after another, [segmentMs] apart.
 */
data class IntervalVisual(val low: Int, val high: Int, val pairs: List<IntervalPair>, val countSteps: Boolean = true, val segmentMs: Int = 3800) : Visual {
    val leadMs get() = 400
    fun segmentAt(i: Int) = leadMs + i * segmentMs
    val stretchMs get() = 1200
    val highAt get() = 1350
    val togetherAt get() = 2300
    override val durationMs get() = segmentAt(pairs.size) - 200
    override val cues get() = pairs.flatMapIndexed { i, pr ->
        val s = segmentAt(i)
        listOf(Cue(s, listOf(pr.low.midi), 1200), Cue(s + highAt, listOf(pr.high.midi), 900), Cue(s + togetherAt, listOf(pr.low.midi, pr.high.midi), 1300))
    }
    /** The pair on screen at [t]. */
    fun current(t: Int) = ((t - leadMs) / segmentMs).coerceIn(0, pairs.lastIndex)
}

// ---- Circle of fifths --------------------------------------------------------------------------------------------

/**
 * The circle of fifths. With [build] the twelve keys appear one by one clockwise from C, each tonic sounding;
 * then the wheel turns to each of [stops] (indexes into [CIRCLE_MAJORS]) in turn, the key's signature in the
 * middle and its chord sounding. [minors] shows the relative minors on an inner ring.
 */
data class CircleVisual(val stops: List<Int>, val build: Boolean = false, val minors: Boolean = false, val stepMs: Int = 2400, val play: Boolean = true) : Visual {
    val wedgeMs get() = 230
    val buildMs get() = if (build) 12 * wedgeMs + 400 else 300
    val turnMs get() = 1000
    fun stopAt(i: Int) = buildMs + i * stepMs
    override val durationMs get() = stopAt(stops.size) - stepMs + turnMs + 1400
    override val cues: List<Cue> get() {
        if (!play) return emptyList()
        val wedges = if (build) (0 until 12).map { i -> Cue(i * wedgeMs + 200, listOf(tonicMidi(i)), 500, velocity = 70) } else emptyList()
        val chords = stops.mapIndexed { i, k ->
            val notes = if (minors) triad(CIRCLE_MINORS[k].tonic, TriadQuality.MINOR) else triad(CIRCLE_MAJORS[k].tonic, TriadQuality.MAJOR)
            val shift = 60 + Math.floorMod(notes[0].midi, 12) - notes[0].midi       // root between C4 and B4
            Cue(stopAt(i) + turnMs, notes.map { it.midi + shift }, 1300)
        }
        return (wedges + chords).sortedBy { it.atMs }
    }
    /** Where the wheel points at [t], in steps clockwise from C (fractional while turning); turns the short way. */
    fun angle(t: Int): Float {
        var at = 0f
        stops.forEachIndexed { i, k ->
            val start = stopAt(i)
            if (t < start) return at
            var d = (k - at) % 12f
            if (d > 6f) d -= 12f
            if (d < -6f) d += 12f
            val e = progress(t, start, turnMs)
            val eased = e * e * (3 - 2 * e)
            at += d * eased
            if (e < 1f) return at
        }
        return at
    }
    /** The stop the wheel rests on (or turns toward) at [t], or -1 before the first. */
    fun currentStop(t: Int) = stops.indices.lastOrNull { t >= stopAt(it) } ?: -1

    companion object {
        /** The tonic of the [i]-th circle key between C4 and B4. */
        fun tonicMidi(i: Int) = 60 + Math.floorMod(CIRCLE_MAJORS[i].tonic.midi, 12)
    }
}

// ---- Chords ------------------------------------------------------------------------------------------------------

/** A chord in a [ChordVisual]: with [build] its notes stack a third at a time; [tags] name each note ("root", "3rd"). */
data class ChordStep(val notes: List<Pitch>, val name: String, val numeral: String? = null, val build: Boolean = true, val tags: List<String> = emptyList())

/**
 * Chords on a staff above a keyboard. A built chord stacks one note at a time (each slides up into place and
 * sounds), then rings together; an unbuilt one appears whole. One chord after another.
 */
data class ChordVisual(val chords: List<ChordStep>, val low: Int = 48, val high: Int = 83, val noteMs: Int = 700, val holdMs: Int = 1500, val clef: Clef = Clef.TREBLE) : Visual {
    val leadMs get() = 450
    fun length(c: ChordStep) = (if (c.build) c.notes.size * noteMs + 250 else 0) + holdMs + 250
    fun chordAt(i: Int) = leadMs + chords.take(i).sumOf { length(it) }
    /** When the [n]-th note of chord [i] arrives (all at once for an unbuilt chord). */
    fun noteAt(i: Int, n: Int) = chordAt(i) + if (chords[i].build) n * noteMs else 0
    fun togetherAt(i: Int) = chordAt(i) + if (chords[i].build) chords[i].notes.size * noteMs + 250 else 0
    override val durationMs get() = chordAt(chords.size)
    override val cues get() = chords.flatMapIndexed { i, c ->
        (if (c.build) c.notes.mapIndexed { n, pt -> Cue(noteAt(i, n), listOf(pt.midi), noteMs - 60) } else emptyList()) +
            Cue(togetherAt(i), c.notes.map { it.midi }, holdMs)
    }
    fun current(t: Int) = chords.indices.lastOrNull { t >= chordAt(it) } ?: 0
}

// ---- Note values -------------------------------------------------------------------------------------------------

/**
 * The note value tree: a whole note, then two halves under it, four quarters, eight eighths, sixteen sixteenths,
 * one row at a time; each row plays its notes across one bar at [bpm]. With [rests], the same tree in rests,
 * with only the beat clicking (a rest is counted silence).
 */
data class ValueTreeVisual(val rows: Int = 5, val bpm: Int = 96, val rests: Boolean = false, val pitch: Int = 72) : Visual {
    val leadMs get() = 400
    val beatMs get() = 60_000f / bpm
    val rowMs get() = (4 * beatMs).toInt() + 500
    fun rowAt(r: Int) = leadMs + r * rowMs
    override val durationMs get() = rowAt(rows) - 200
    override val cues get() = (0 until rows).flatMap { r ->
        val n = 1 shl r
        val each = 4f / n * beatMs
        if (rests) (0 until 4).map { b -> Cue((rowAt(r) + b * beatMs).toInt(), click = if (b == 0) Click.ACCENT else Click.BEAT) }
        else (0 until n).map { k -> Cue((rowAt(r) + k * each).toInt(), listOf(pitch), (each * 0.85f).toInt(), velocity = 85) }
    }.sortedBy { it.atMs }
}

// ---- Words -------------------------------------------------------------------------------------------------------

/** Big words that pop in one by one (a formula, a mnemonic), each with its note from [notes] if given. */
data class PopVisual(val items: List<String>, val stepMs: Int = 650, val notes: List<List<Int>> = emptyList(), val small: String? = null) : Visual {
    val leadMs get() = 350
    fun itemAt(i: Int) = leadMs + i * stepMs
    override val durationMs get() = itemAt(items.size) + 300
    override val cues get() = notes.mapIndexed { i, n -> Cue(itemAt(i), n, (stepMs * 0.9f).toInt()) }.filter { it.notes.isNotEmpty() }
}

// ---- Quiz --------------------------------------------------------------------------------------------------------

sealed interface Question {
    val prompt: String
    /** One sentence on why the answer is right, shown after the answer either way. */
    val explain: String
}

/** Tap one of [options]; [show] is a still picture (a visual at its end state) to read from. */
data class Choice(override val prompt: String, val options: List<String>, val answer: Int, override val explain: String, val show: Visual? = null) : Question

/** Play [target] on the keyboard ([low] to [high], white keys at both ends); with [anyOctave] any key of that name counts. */
data class PlayIt(
    override val prompt: String, val target: Int, override val explain: String, val anyOctave: Boolean = true,
    val low: Int = 60, val high: Int = 83, val show: Visual? = null, val flats: Boolean = false,
) : Question {
    fun accepts(midi: Int) = if (anyOctave) Math.floorMod(midi - target, 12) == 0 else midi == target
}

/** Listen to [clips] (each a group of notes, played in turn) and pick from [options]. */
data class Listen(override val prompt: String, val clips: List<List<Int>>, val options: List<String>, val answer: Int, override val explain: String, val gapMs: Int = 750) : Question

/** All the notes a quiz needs, for tests: every question has exactly one right answer. */
fun Question.rightAnswers(): Int = when (this) {
    is Choice -> if (answer in options.indices && options.toSet().size == options.size) 1 else 0
    is Listen -> if (answer in options.indices && options.toSet().size == options.size) 1 else 0
    is PlayIt -> if (anyOctave) 1 else (low..high).count { accepts(it) }
}

/**
 * A theory line as the voice should read it (integration): "4/4" is said "4 4", sharps and flats by name, "C♯/D♭"
 * as "C sharp or D flat", "&" as "and". The screen keeps the written form; SpokenCatalog records this one.
 */
fun spokenTheory(text: String): String = text
    .replace(Regex("(\\d+)/(\\d+)"), "$1 $2")
    .replace(Regex("([A-G])[#♯]"), "$1 sharp").replace(Regex("([A-G])♭"), "$1 flat")
    .replace("♯", " sharp").replace("♭", " flat")
    .replace("/", " or ").replace("&", " and ")
    .replace(Regex("\\s+"), " ").trim()

private val NUMERAL_WORDS = mapOf("I" to "one", "II" to "two", "III" to "three", "IV" to "four", "V" to "five", "VI" to "six", "VII" to "seven")

/**
 * A theory quiz line or a lesson's title as the voice reads it: [spokenTheory], and Roman numerals as the scenes say
 * them ("V7 to I" is "five seven to one", "the vii chord" "the seven chord"). Not for other lines: there "I" is the pup.
 */
fun spokenNumerals(text: String): String = spokenTheory(text.replace(Regex("\\b(VII|VI|IV|V|III|II|I|vii|vi|iv|v|iii|ii)(7)?\\b")) { m ->
    NUMERAL_WORDS.getValue(m.groupValues[1].uppercase()) + if (m.groupValues[2].isNotEmpty()) " seven" else ""
})

// ---- Lessons (2.0.1): the body at the piano, touch and phrases ---------------------------------------------------
// The Learn course's reading steps are short explainers too (engine/Course.kt Step.scenes). These visuals show what
// words alone did before: how to sit, the hand's shape, finger numbers, the pedal, loud and soft, smooth and short,
// and a song's opening phrase.

/** A soft rising chime for the moment a picture settles into place, so a still picture is heard as well as seen. */
private val CHIME = listOf(72, 76, 79, 84, 79, 76)

/**
 * A part of the body at the piano that a [PostureVisual] puts right. 2.3 (the workshop): CLOSE, the bench pushed in too
 * close (knees under the keys, elbows squashed); FEET, the feet tucked under the bench.
 */
enum class PostureCue { BENCH, EDGE, BACK, ELBOWS, LEVEL, HANDS, CLOSE, FEET }

data class PostureStep(val cue: PostureCue, val label: String)

/**
 * A figure seated at the piano, side view. Each step moves one part from a poor position into a good one (the
 * bench slides to the right distance, the back straightens, the elbows settle a little ahead of the body, a level
 * line appears under the forearms, the hands rest on the keys) and pops its label. Parts not listed start right.
 */
data class PostureVisual(val steps: List<PostureStep>, val stepMs: Int = 1500) : Visual {
    val leadMs get() = 600
    val moveMs get() = 800
    fun stepAt(i: Int) = leadMs + i * stepMs
    override val durationMs get() = stepAt(steps.size) + 300
    override val cues get() = steps.mapIndexed { i, s ->
        if (s.cue == PostureCue.HANDS) Cue(stepAt(i) + moveMs, listOf(60, 64, 67), 900, velocity = 60)
        else Cue(stepAt(i) + moveMs - 120, listOf(CHIME[i % CHIME.size]), 500, velocity = 50)
    }
    /** How far [cue] has moved into place at [t]: 0 the poor position, 1 right. */
    fun settled(cue: PostureCue, t: Int): Float = steps.indexOfFirst { it.cue == cue }.let { if (it < 0) 1f else progress(t, stepAt(it), moveMs) }
}

/** 2.3: what the forearm, wrist and hand do at the keys in an [ArmVisual]. */
enum class ArmMove {
    /** The wrist level with the forearm and the knuckles: a bridge. */
    LEVEL,
    /** The wrist sagging below the keys. */
    LOW,
    /** The wrist poking up above the knuckles. */
    HIGH,
    /** The arm lifts and falls into the key with its weight: a big, round sound. */
    HEAVY,
    /** The fingertip sinks gently into the key: a soft sound. */
    SOFT,
}

data class ArmStep(val move: ArmMove, val label: String)

/**
 * 2.3 (the workshop): a forearm, wrist and hand at a key, seen from the side, in the soft realistic look of the posture
 * figure (ui/theory/SoftFigure.kt softArm). Each step moves into its pose over [moveMs] and pops its label: the wrist
 * level like a bridge (a dashed line from the arm to the knuckles), sagging, poking up; the arm's weight falling into
 * the key (loud) or the fingertip sinking softly (soft). The key sounds as the finger lands.
 */
data class ArmVisual(val steps: List<ArmStep>, val key: Int = 60, val stepMs: Int = 1700) : Visual {
    val leadMs get() = 500
    val moveMs get() = 700
    fun stepAt(i: Int) = leadMs + i * stepMs
    /** When the finger lands in step [i] (a heavy arm falls a little later, after its lift). */
    fun landAt(i: Int) = stepAt(i) + if (steps[i].move == ArmMove.HEAVY) moveMs + 250 else moveMs
    override val durationMs get() = stepAt(steps.size) + 300
    override val cues get() = steps.mapIndexedNotNull { i, s ->
        when (s.move) {
            ArmMove.HEAVY -> Cue(landAt(i), listOf(key), 1000, velocity = 112)
            ArmMove.SOFT -> Cue(landAt(i), listOf(key), 1000, velocity = 34)
            ArmMove.LEVEL -> Cue(landAt(i), listOf(key), 800, velocity = 68)
            else -> null
        }
    }
    /** The step showing at [t], or -1 before the first. */
    fun current(t: Int) = steps.indices.lastOrNull { t >= stepAt(it) } ?: -1
}

/** A move of the hand in a [HandShapeVisual]. */
enum class HandMove { HANG, BALL, CURL, TIPS, THUMB }

data class HandStep(val move: HandMove, val label: String)

/**
 * A right hand over the keys, from above. HANG: it dangles loose, then floats down over the keys. BALL: a faint
 * ball appears under the palm. CURL: the fingers curl round it (and the ball fades). TIPS: the fingertips land on
 * [keys], one per finger, and sound. THUMB: the thumb turns onto its side and plays its key. Moves not listed that
 * come before the listed ones (in that order) start done; those after them do not happen. [keys] are the keys
 * under fingers 1 to 5.
 */
data class HandShapeVisual(val steps: List<HandStep>, val keys: List<Int> = listOf(60, 62, 64, 65, 67), val stepMs: Int = 1600) : Visual {
    val leadMs get() = 500
    val moveMs get() = 850
    fun stepAt(i: Int) = leadMs + i * stepMs
    override val durationMs get() = stepAt(steps.size) + 300
    private val thumbMoves get() = steps.any { it.move == HandMove.THUMB }
    override val cues get() = steps.flatMapIndexed { i, s ->
        val land = stepAt(i) + moveMs - 150
        when (s.move) {
            HandMove.TIPS -> (if (thumbMoves) 1..4 else 0..4).map { k -> Cue(land + k * 80, listOf(keys[k]), 700, velocity = 62) }
            HandMove.THUMB -> listOf(Cue(land, listOf(keys[0]), 800, velocity = 62))
            else -> listOf(Cue(land, listOf(CHIME[i % CHIME.size]), 500, velocity = 48))
        }
    }.sortedBy { it.atMs }
    fun has(move: HandMove) = steps.any { it.move == move }
    /** How far [move] has gone at [t]: 0 not started, 1 done. A move not listed is done if it comes before the last listed one. */
    fun done(move: HandMove, t: Int): Float = steps.indexOfFirst { it.move == move }.let {
        if (it >= 0) progress(t, stepAt(it), moveMs) else if (move.ordinal < steps.maxOf { s -> s.move.ordinal }) 1f else 0f
    }
}

/** One finger pressing [key]: the right hand's or the left's, finger 1 (thumb) to 5. */
data class FingerPress(val right: Boolean, val finger: Int, val key: Int)

/**
 * A moment of a [FingerNumbersVisual]: these fingers press together. [label] pops over them; [spread] is how far
 * apart the fingers sit, in white keys (1 is one finger per key; an octave stretch is about 1.75).
 */
data class FingerStep(val presses: List<FingerPress>, val label: String? = null, val spread: Float = 1f)

/**
 * Both hands over the keys from above, the left one mirrored. With [intro] the numbers 1 to 5 pop onto the fingers
 * in order, each finger pressing and sounding its key ([rightKeys], [leftKeys]: fingers 1 to 5; empty hides that
 * hand). Then [steps] play one every [stepMs]: the hand glides so the finger is over its key (the thumb tucking
 * under, a finger crossing over) and the finger presses, its number lit.
 */
data class FingerNumbersVisual(
    val rightKeys: List<Int> = listOf(60, 62, 64, 65, 67), val leftKeys: List<Int> = listOf(55, 53, 52, 50, 48),
    val intro: Boolean = true, val steps: List<FingerStep> = emptyList(), val popMs: Int = 600, val stepMs: Int = 520,
) : Visual {
    val leadMs get() = 450
    /** When finger [f] (1..5) gets its number in the intro. */
    fun popAt(f: Int) = leadMs + (f - 1) * popMs
    val stepsStart get() = if (intro) popAt(6) + 250 else leadMs + 400
    fun stepAt(i: Int) = stepsStart + i * stepMs
    override val durationMs get() = if (steps.isEmpty()) popAt(6) + 300 else stepAt(steps.size) + 450
    override val cues get() = (if (intro) (1..5).mapNotNull { f ->
        listOfNotNull(rightKeys.getOrNull(f - 1), leftKeys.getOrNull(f - 1)).takeIf { it.isNotEmpty() }?.let { Cue(popAt(f), it, (popMs * 0.85f).toInt(), velocity = 70) }
    } else emptyList()) + steps.mapIndexedNotNull { i, s -> s.presses.map { it.key }.takeIf { it.isNotEmpty() }?.let { Cue(stepAt(i), it, (stepMs * 0.9f).toInt(), velocity = 72) } }
    private val allKeys get() = rightKeys + leftKeys + steps.flatMap { s -> s.presses.map { it.key } }
    val low get() = whiteAtOrBelow(allKeys.min() - 2)
    val high get() = whiteAtOrAbove(allKeys.max() + 2)
}

private val BLACK = setOf(1, 3, 6, 8, 10)
fun whiteAtOrBelow(m: Int) = if (Math.floorMod(m, 12) in BLACK) m - 1 else m
fun whiteAtOrAbove(m: Int) = if (Math.floorMod(m, 12) in BLACK) m + 1 else m

/**
 * The sustain pedal under a run of chords. A keyboard shows each chord (the hands leave the keys while it rings
 * on), a lane below draws the pedal line as it happens, and a foot on the pedal presses and lifts. The pedal lifts
 * as each new chord sounds and goes down [downAfterMs] later (an arrow marks "down after the chord"); with [late]
 * it changes half a bar late and the old chord blurs into the new one. [foot] draws the foot larger, labelled.
 */
data class PedalVisual(
    val chords: List<List<Int>>, val names: List<String> = emptyList(), val chordMs: Int = 1900, val downAfterMs: Int = 380,
    val late: Boolean = false, val foot: Boolean = false,
) : Visual {
    val leadMs get() = 700
    fun chordAt(i: Int) = leadMs + i * chordMs
    /** When the fingers leave the chord's keys (the pedal holds the sound on). */
    fun releaseAt(i: Int) = chordAt(i) + minOf(700, chordMs / 2)
    /** When the pedal comes up before chord [i] rings on (i >= 1), and when it goes down again. */
    fun liftAt(i: Int) = if (i == 0) chordAt(0) else chordAt(i) + if (late) chordMs / 2 else 0
    fun downAt(i: Int) = liftAt(i) + if (late && i > 0) 180 else downAfterMs
    override val durationMs get() = chordAt(chords.size) + 900
    override val cues get() = chords.mapIndexed { i, c ->
        val end = if (i + 1 < chords.size) liftAt(i + 1) + 60 else durationMs - 200
        Cue(chordAt(i), c, end - chordAt(i), velocity = 72)
    }
    /** How far down the pedal is at [t]: 0 up, 1 down. */
    fun pedal(t: Int): Float {
        if (chords.isEmpty() || t < downAt(0)) return 0f
        if (t > durationMs - 500) return 1f - progress(t, durationMs - 500, 250)
        val i = chords.indices.last { t >= liftAt(it) }
        if (i == 0) return progress(t, downAt(0), 160)
        val u = t - liftAt(i)
        return when {
            u < 110 -> 1f - u / 110f
            t < downAt(i) -> 0f
            else -> progress(t, downAt(i), 160)
        }
    }
    /** The chord showing at [t], or -1 before the first. */
    fun current(t: Int) = chords.indices.lastOrNull { t >= chordAt(it) } ?: -1
    val low get() = whiteAtOrBelow(chords.flatten().min() - 2)
    val high get() = whiteAtOrAbove(chords.flatten().max() + 2)
}

/**
 * Loud and soft: a short phrase plays, each note (or chord) at its level (MIDI velocity, 1 to 127). A dot per
 * note grows with its loudness and a curve under the phrase traces the swell as it plays; [marks] (p, f, or a
 * word) pop under the notes they belong to.
 */
data class DynamicsVisual(val notes: List<List<Int>>, val levels: List<Int>, val marks: Map<Int, String> = emptyMap(), val stepMs: Int = 520) : Visual {
    init { require(notes.size == levels.size) { "one level per note" } }
    val leadMs get() = 500
    fun noteAt(i: Int) = leadMs + i * stepMs
    override val durationMs get() = noteAt(notes.size) + 700
    override val cues get() = notes.mapIndexed { i, n -> Cue(noteAt(i), n, (stepMs * 0.92f).toInt(), velocity = levels[i].coerceIn(1, 127)) }
}

/** Smooth or short. */
enum class Touch(val label: String) { LEGATO("Legato, smooth"), STACCATO("Staccato, short") }

/**
 * The same notes played in each of [touches] in turn: legato under a slur that draws itself, the keys held until
 * the next one goes down; staccato with a dot over each short note, the key popping straight back up.
 */
data class ArticulationVisual(val notes: List<Int>, val touches: List<Touch> = listOf(Touch.LEGATO, Touch.STACCATO), val stepMs: Int = 430,
                              val labels: List<String> = emptyList()) : Visual {
    val leadMs get() = 500
    val passMs get() = notes.size * stepMs + 800
    fun passAt(k: Int) = leadMs + k * passMs
    fun noteAt(k: Int, i: Int) = passAt(k) + i * stepMs
    fun hold(touch: Touch) = if (touch == Touch.LEGATO) stepMs + 30 else (stepMs * 0.32f).toInt()
    fun label(k: Int) = labels.getOrNull(k) ?: touches[k].label
    override val durationMs get() = passAt(touches.size) - 300
    override val cues get() = touches.flatMapIndexed { k, touch -> notes.mapIndexed { i, n -> Cue(noteAt(k, i), listOf(n), hold(touch), velocity = 74) } }
    val low get() = whiteAtOrBelow(notes.min() - 2)
    val high get() = whiteAtOrAbove(notes.max() + 2)
}

/** A note or chord of a phrase (a rest when [pitches] is empty): [start] and [length] in beats; [left] for the left hand. */
data class PhraseNote(val pitches: List<Pitch>, val start: Float, val length: Float, val left: Boolean) {
    val rest get() = pitches.isEmpty()
}

/** The notes of one hand from the course's exercise notation ("C4:1 r:1 C4+E4+G4:2"; "r" is a rest). */
fun phraseNotes(spec: String, left: Boolean): List<PhraseNote> {
    var beat = 0f
    val out = mutableListOf<PhraseNote>()
    for (tok in spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val (names, len) = tok.split(':').let { it[0] to it[1].toFloat() }
        out += PhraseNote(if (names == "r") emptyList() else names.split('+').map(Pitch::parse), beat, len, left)
        beat += len
    }
    return out
}

/** Which hand a pass of a [PhraseVisual] is about (the other is drawn faint and heard softly). */
enum class PhraseHands(val label: String) { BOTH("Hands together"), RIGHT("Right hand"), LEFT("Left hand") }

/**
 * A phrase in the course's notation shown on a staff (a grand staff when it has both hands), with a keyboard under
 * it. The staff draws itself, then the notes slide in and sound in rhythm at [bpm], their keys lighting. Each of
 * [passes] plays the phrase once more, the hand it names in front. [upTo] keeps the first beats only (0: all).
 * With [lookAhead] a soft band runs one beat ahead of the notes being played: where the eyes should be.
 */
data class PhraseVisual(
    val right: String, val left: String = "", val bpm: Int = 80, val beats: Int = 4, val keyFifths: Int = 0,
    val passes: List<PhraseHands> = listOf(PhraseHands.BOTH), val upTo: Float = 0f, val lookAhead: Boolean = false,
) : Visual {
    val notes: List<PhraseNote> by lazy {
        (phraseNotes(right, false) + phraseNotes(left, true)).filter { upTo <= 0f || it.start < upTo - 0.001f }.sortedBy { it.start }
    }
    /** The phrase's length in beats. */
    val span: Float get() = if (upTo > 0f) upTo else notes.maxOfOrNull { it.start + it.length } ?: 0f
    val hasRight get() = notes.any { !it.left && !it.rest }
    val hasLeft get() = notes.any { it.left && !it.rest }
    private val sounding get() = notes.filter { !it.rest }
    val beatMs get() = 60_000f / bpm
    val drawMs get() = 1300
    fun passAt(k: Int) = drawMs + 300 + k * ((span * beatMs).toInt() + 700)
    /** When [beat] of pass [k] is played. */
    fun at(k: Int, beat: Float) = passAt(k) + (beat * beatMs).toInt()
    override val durationMs get() = passAt(passes.size) - 700 + 500
    override val cues get() = passes.flatMapIndexed { k, hands ->
        notes.filter { !it.rest }.map { n ->
            val front = hands == PhraseHands.BOTH || (hands == PhraseHands.LEFT) == n.left
            val hold = (minOf(n.length, (span - n.start).coerceAtLeast(0.25f)) * beatMs * 0.92f).toInt()
            Cue(at(k, n.start), n.pitches.map { it.midi }, hold, velocity = if (front) 80 else 38)
        }
    }.sortedBy { it.atMs }
    /** The pass playing at [t] and the beat it has reached, or null between passes. */
    fun cursor(t: Int): Pair<Int, Float>? = passes.indices.lastOrNull { t >= passAt(it) }?.let { k ->
        val b = (t - passAt(k)) / beatMs
        if (b > span) null else k to b
    }
    val low get() = whiteAtOrBelow(sounding.minOf { n -> n.pitches.minOf { it.midi } } - 2)
    val high get() = whiteAtOrAbove(sounding.maxOf { n -> n.pitches.maxOf { it.midi } } + 2)
}

/**
 * Falling notes, as in the player: the blocks fall onto the keys and sound as they touch the line, long blocks
 * holding the key down until they have passed. A big count (1, 2, 3, 4) follows the beat, with [countIn] bars of
 * clicks first.
 */
data class LaneVisual(val right: String, val left: String = "", val bpm: Int = 72, val beats: Int = 4, val countIn: Int = 0, val clicks: Boolean = true) : Visual {
    val notes: List<PhraseNote> by lazy { (phraseNotes(right, false) + phraseNotes(left, true)).filter { !it.rest }.sortedBy { it.start } }
    val beatMs get() = 60_000f / bpm
    /** How many beats of falling the lane shows above the line. */
    val fallBeats get() = 2.5f
    val startAt get() = (fallBeats * beatMs).toInt() + 400 + (countIn * beats * beatMs).toInt()
    /** Beats from the first note: negative during the count-in. */
    fun beatAt(t: Int) = (t - startAt) / beatMs
    fun hitAt(beat: Float) = startAt + (beat * beatMs).toInt()
    val total get() = notes.maxOfOrNull { it.start + it.length }?.let { kotlin.math.ceil(it / beats) * beats } ?: beats.toFloat()
    override val durationMs get() = hitAt(total) + 400
    override val cues get() = (notes.map { Cue(hitAt(it.start), it.pitches.map { p -> p.midi }, (it.length * beatMs * 0.92f).toInt(), velocity = 82) } +
        if (clicks) (-countIn * beats until total.toInt()).map { b -> Cue(hitAt(b.toFloat()), click = if (Math.floorMod(b, beats) == 0) Click.ACCENT else Click.BEAT) } else emptyList())
        .filter { it.atMs >= 0 }.sortedBy { it.atMs }
    val low get() = whiteAtOrBelow(notes.minOf { n -> n.pitches.minOf { it.midi } } - 3)
    val high get() = whiteAtOrAbove(notes.maxOf { n -> n.pitches.maxOf { it.midi } } + 3)
}

/** One run of the playhead over sections [from]..[to] of a [SongMapVisual], at [speed] percent. */
data class MapPass(val from: Int, val to: Int, val speed: Int = 100)

/**
 * A whole song as the player's section bar: labelled [sections], a playhead running along it and the song's tune
 * ([melody], shared out over the sections) heard as it goes. A pass over fewer sections than all shows the loop
 * bracket around them; a pass under 100 percent shows the speed.
 */
data class SongMapVisual(val sections: List<String>, val passes: List<MapPass>, val melody: List<Int> = emptyList(), val sectionMs: Int = 1500) : Visual {
    val leadMs get() = 600
    val gapMs get() = 600
    fun length(p: MapPass) = (p.to - p.from + 1) * sectionMs * 100 / p.speed
    fun passAt(k: Int) = leadMs + passes.take(k).sumOf { length(it) + gapMs }
    override val durationMs get() = passAt(passes.size) - gapMs + 400
    /** The tune heard over section [s]: the melody shared out over the sections, wrapping round. */
    fun chunk(s: Int): List<Int> {
        if (melody.isEmpty()) return emptyList()
        val per = maxOf(1, melody.size / sections.size)
        val from = Math.floorMod(s, sections.size) * per % melody.size
        return melody.subList(from, minOf(melody.size, from + per))
    }
    override val cues get() = passes.flatMapIndexed { k, p ->
        val secMs = sectionMs * 100 / p.speed
        (p.from..p.to).flatMap { s ->
            val notes = chunk(s)
            val each = secMs / maxOf(1, notes.size)
            notes.mapIndexed { j, n -> Cue(passAt(k) + (s - p.from) * secMs + j * each, listOf(n), (each * 0.9f).toInt(), velocity = 70) }
        }
    }
    /** The pass under way at [t] and where the playhead is, in sections from the start of the song (null between passes). */
    fun playhead(t: Int): Pair<Int, Float>? = passes.indices.lastOrNull { t >= passAt(it) }?.let { k ->
        val p = passes[k]
        val f = (t - passAt(k)).toFloat() / (sectionMs * 100 / p.speed)
        if (f > p.to - p.from + 1) null else k to p.from + f
    }
}

/** A message a keyboard sends over its cable: a key at a velocity, or the pedal (null [note]). */
data class MidiSignal(val note: Int?, val velocity: Int = 80, val label: String)

/**
 * A digital piano plugged into the phone by a USB cable: the cable draws itself and its plug slides home, then
 * each signal sounds (a key struck at its velocity, or the pedal going down) and runs along the cable as a pulse,
 * its label popping up by the phone.
 */
data class MidiVisual(val signals: List<MidiSignal>, val stepMs: Int = 1100) : Visual {
    val leadMs get() = 300
    val plugMs get() = 1300
    fun signalAt(i: Int) = leadMs + plugMs + 300 + i * stepMs
    /** How long a pulse takes to run from the piano to the phone. */
    val travelMs get() = 450
    override val durationMs get() = signalAt(signals.size) + 500
    override val cues get() = signals.mapIndexedNotNull { i, s -> s.note?.let { Cue(signalAt(i), listOf(it), 800, velocity = s.velocity.coerceIn(1, 127)) } }
}
