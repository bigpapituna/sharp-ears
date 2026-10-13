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
