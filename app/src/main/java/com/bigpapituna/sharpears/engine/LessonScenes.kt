package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.theory.ArticulationVisual
import com.bigpapituna.sharpears.engine.theory.ChordStep
import com.bigpapituna.sharpears.engine.theory.ChordVisual
import com.bigpapituna.sharpears.engine.theory.Clef
import com.bigpapituna.sharpears.engine.theory.DynamicsVisual
import com.bigpapituna.sharpears.engine.theory.FingerNumbersVisual
import com.bigpapituna.sharpears.engine.theory.FingerPress
import com.bigpapituna.sharpears.engine.theory.FingerStep
import com.bigpapituna.sharpears.engine.theory.HandMove
import com.bigpapituna.sharpears.engine.theory.HandShapeVisual
import com.bigpapituna.sharpears.engine.theory.HandStep
import com.bigpapituna.sharpears.engine.theory.Hit
import com.bigpapituna.sharpears.engine.theory.IntervalPair
import com.bigpapituna.sharpears.engine.theory.IntervalVisual
import com.bigpapituna.sharpears.engine.theory.KeyFrame
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.KeysVisual
import com.bigpapituna.sharpears.engine.theory.LaneVisual
import com.bigpapituna.sharpears.engine.theory.MapPass
import com.bigpapituna.sharpears.engine.theory.MidiSignal
import com.bigpapituna.sharpears.engine.theory.MidiVisual
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.PedalVisual
import com.bigpapituna.sharpears.engine.theory.PhraseHands
import com.bigpapituna.sharpears.engine.theory.PhraseVisual
import com.bigpapituna.sharpears.engine.theory.Pitch
import com.bigpapituna.sharpears.engine.theory.PopVisual
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.PostureStep
import com.bigpapituna.sharpears.engine.theory.PostureVisual
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.Scene
import com.bigpapituna.sharpears.engine.theory.SongMapVisual
import com.bigpapituna.sharpears.engine.theory.StaffNote
import com.bigpapituna.sharpears.engine.theory.StaffVisual
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.theory.Touch

/*
 * Builders for the course's reading steps (2.0.1). Every READ step is a short explainer like a Theory lesson: one
 * scene per idea, each a caption the pup says (the step's own sentences) over an animated picture. Course.kt
 * authors the scenes with these; the pictures are engine/theory Visuals, drawn by ui/theory.
 */

internal fun scene(caption: String, visual: com.bigpapituna.sharpears.engine.theory.Visual, mood: Mood = Mood.EXPLAIN, say: String = caption) =
    Scene(caption, visual, mood, say)

private fun m(s: String) = Pitch.parse(s).midi
private fun ms(s: String) = s.split(' ').filter { it.isNotBlank() }.map(::m)

// ---- Keys --------------------------------------------------------------------------------------------------------

/** One frame of lit keys ("C4 E4"), with a label over them, names written on keys, or a step label ([gap]). */
internal fun frame(notes: String, label: String? = null, tint: Tint = Tint.ACCENT, names: Map<String, String> = emptyMap(), gap: String? = null,
                   clear: Boolean = false, play: Boolean = true) = KeyFrame(ms(notes), label, gap, play, tint, names.mapKeys { m(it.key) }, clear)

/** One key per frame along [notes], labelled from [labels] by index; black keys in blue. */
internal fun walk(notes: String, labels: Map<Int, String> = emptyMap(), clearFirst: Boolean = false, flats: Boolean = false) = ms(notes).mapIndexed { i, n ->
    val p = Pitch.of(n, flats)
    KeyFrame(listOf(n), labels[i], null, true, if (p.accidental != 0) Tint.BLUE else Tint.ACCENT,
        if (p.accidental != 0) mapOf(n to p.label) else emptyMap(), clear = i == 0 && clearFirst)
}

internal fun keys(low: String, high: String, frames: List<KeyFrame>, stepMs: Int = 800, names: KeyNames = KeyNames.LIT, flats: Boolean = false, finale: String = "") =
    KeysVisual(m(low), m(high), frames, stepMs, names, flats, ms(finale))

internal fun interval(low: String, high: String, a: String, b: String, name: String, note: String? = null) =
    IntervalVisual(m(low), m(high), listOf(IntervalPair(Pitch.parse(a), Pitch.parse(b), name, note)), countSteps = false)

// ---- The body ----------------------------------------------------------------------------------------------------

internal fun posture(vararg steps: Pair<PostureCue, String>, stepMs: Int = 1500) = PostureVisual(steps.map { PostureStep(it.first, it.second) }, stepMs)

internal fun hand(vararg steps: Pair<HandMove, String>, keys: String = "C4 D4 E4 F4 G4", stepMs: Int = 1600) =
    HandShapeVisual(steps.map { HandStep(it.first, it.second) }, ms(keys), stepMs)

/**
 * Hands over the keys: [right] and [left] are the keys under fingers 1 to 5 ("" hides that hand). With [intro] the
 * finger numbers pop on first; [steps] then play in turn.
 */
internal fun fingers(right: String = "C4 D4 E4 F4 G4", left: String = "G3 F3 E3 D3 C3", intro: Boolean = true, steps: List<FingerStep> = emptyList(),
                     stepMs: Int = 520, popMs: Int = 600) = FingerNumbersVisual(ms(right), ms(left), intro, steps, popMs, stepMs)

/** One hand playing [notes] with [fingers] ("1 2 3 1"), a note per step; [labels] by note index. */
internal fun fingered(notes: String, fingers: String, right: Boolean = true, labels: Map<Int, String> = emptyMap(), spread: Float = 1f): List<FingerStep> {
    val n = ms(notes); val f = fingers.split(' ').map { it.toInt() }
    require(n.size == f.size) { "one finger per note: $notes / $fingers" }
    return n.indices.map { i -> FingerStep(listOf(FingerPress(right, f[i], n[i])), labels[i], spread) }
}

/** Both hands together, a note each per step: the right hand's [rNotes] with [rFingers] over the left's. */
internal fun together(rNotes: String, rFingers: String, lNotes: String, lFingers: String, labels: Map<Int, String> = emptyMap(), spread: Float = 1f): List<FingerStep> {
    val r = fingered(rNotes, rFingers, true); val l = fingered(lNotes, lFingers, false)
    require(r.size == l.size) { "hands differ in length" }
    return r.indices.map { i -> FingerStep(r[i].presses + l[i].presses, labels[i], spread) }
}

/** Several fingers of one hand at once: "1:C4 3:E4 5:G4". */
internal fun chordPress(spec: String, right: Boolean = true, label: String? = null, spread: Float = 1f) =
    FingerStep(spec.split(' ').map { t -> t.split(':').let { FingerPress(right, it[0].toInt(), m(it[1])) } }, label, spread)

internal fun pedal(chords: String, names: List<String> = emptyList(), late: Boolean = false, foot: Boolean = false, chordMs: Int = 1900) =
    PedalVisual(chords.split(' ').map { c -> c.split('+').map(::m) }, names, chordMs, late = late, foot = foot)

/** Notes or chords ("C4 D4 E4+G4") each at its loudness, with marks under some of them (by index). */
internal fun dynamics(notes: String, levels: List<Int>, marks: Map<Int, String> = emptyMap(), stepMs: Int = 520) =
    DynamicsVisual(notes.split(' ').map { c -> c.split('+').map(::m) }, levels, marks, stepMs)

internal fun touch(notes: String, vararg touches: Touch, labels: List<String> = emptyList(), stepMs: Int = 430) =
    ArticulationVisual(ms(notes), touches.toList().ifEmpty { listOf(Touch.LEGATO, Touch.STACCATO) }, stepMs, labels)

internal fun midi(vararg signals: MidiSignal, stepMs: Int = 1100) = MidiVisual(signals.toList(), stepMs)
internal fun struck(note: String, velocity: Int, label: String) = MidiSignal(m(note), velocity, label)
internal fun pedalSignal(label: String) = MidiSignal(null, 0, label)

// ---- Music on the page -------------------------------------------------------------------------------------------

/** A phrase from an exercise, on the staff and keys. */
internal fun phrase(ex: Exercise, vararg passes: PhraseHands, upTo: Float = 0f, keyFifths: Int = 0, lookAhead: Boolean = false, bpm: Int = ex.bpm.toInt()) =
    PhraseVisual(ex.right, ex.left, bpm, ex.beats, keyFifths, passes.toList().ifEmpty { listOf(PhraseHands.BOTH) }, upTo, lookAhead)

internal fun phrase(right: String, left: String = "", bpm: Int = 80, beats: Int = 4, vararg passes: PhraseHands, keyFifths: Int = 0, upTo: Float = 0f,
                    lookAhead: Boolean = false) = PhraseVisual(right, left, bpm, beats, keyFifths, passes.toList().ifEmpty { listOf(PhraseHands.BOTH) }, upTo, lookAhead)

internal fun valueTree(rows: Int, bpm: Int = 80) = com.bigpapituna.sharpears.engine.theory.ValueTreeVisual(rows, bpm)

internal fun lane(right: String, left: String = "", bpm: Int = 72, beats: Int = 4, countIn: Int = 0) = LaneVisual(right, left, bpm, beats, countIn)

/** "0:1 1:1 r2:2" as start:length in beats ("r" marks a rest), counted [sub] to a beat. */
internal fun rhythm(top: Int, hits: String, bars: Int = 1, sub: Int = 1, bpm: Int = 80, loops: Int = 2, counts: Boolean = true,
                    strong: Set<Int> = setOf(0), pitch: Int = 72, clicks: Boolean = true): RhythmVisual {
    val h = hits.split(' ').filter { it.isNotBlank() }.map { t ->
        val rest = t.startsWith("r")
        val (a, b) = t.removePrefix("r").split(':')
        Hit(a.toFloat(), b.toFloat(), rest)
    }
    val words = if (!counts) emptyList() else (1..top * bars).flatMap { b ->
        val n = (b - 1) % top + 1
        when (sub) { 1 -> listOf("$n"); 2 -> listOf("$n", "&"); else -> listOf("$n", "e", "&", "a") }
    }
    return RhythmVisual(top, 4, bpm, h, bars, sub, words, loops, pitch, strong, clicks = clicks)
}

internal fun sn(s: String, v: NoteValue = NoteValue.QUARTER, label: String? = null, dotted: Boolean = false) =
    StaffNote(s.split('+').map(Pitch::parse), v, dotted, false, false, label)
internal fun restNote(v: NoteValue, label: String? = null) = StaffNote(emptyList(), v, rest = true, label = label)
internal fun staff(vararg notes: StaffNote, bpm: Int = 0, time: Pair<Int, Int>? = null, keyFifths: Int = 0, stepMs: Int = 750, drawClef: Boolean = true,
                   barlines: List<Int> = emptyList(), clef: Clef = Clef.TREBLE) =
    StaffVisual(clef, notes.toList(), drawClef, keyFifths, time, bpm = bpm, stepMs = stepMs, barlines = barlines)

internal fun chord(notes: String, name: String, numeral: String? = null, build: Boolean = true, tags: List<String> = emptyList()) =
    ChordStep(notes.split(' ').map(Pitch::parse), name, numeral, build, tags)
internal fun chords(vararg c: ChordStep, low: String = "G3", high: String = "C6", noteMs: Int = 650, holdMs: Int = 1400) =
    ChordVisual(c.toList(), m(low), m(high), noteMs, holdMs)

internal fun pop(vararg items: String, notes: List<String> = emptyList(), stepMs: Int = 650, small: String? = null) =
    PopVisual(items.toList(), stepMs, notes.map { n -> n.split('+').filter { it.isNotBlank() }.map(::m) }, small)

/** The song's section bar: [sections] ("A A B A"), the playhead's [passes], the tune from [ex]'s right hand. */
internal fun songMap(sections: String, vararg passes: MapPass, ex: Exercise? = null, sectionMs: Int = 1400): SongMapVisual {
    val s = sections.split(' ')
    val melody = ex?.right?.split(' ')?.filter { it.isNotBlank() && !it.startsWith("r") }?.map { m(it.substringBefore(':').substringBefore('+')) } ?: emptyList()
    return SongMapVisual(s, passes.toList().ifEmpty { listOf(MapPass(0, s.lastIndex)) }, melody, sectionMs)
}
