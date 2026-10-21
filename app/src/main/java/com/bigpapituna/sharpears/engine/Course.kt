package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.theory.FingerPress
import com.bigpapituna.sharpears.engine.theory.FingerStep
import com.bigpapituna.sharpears.engine.theory.HandMove
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.MapPass
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.NoteValue
import com.bigpapituna.sharpears.engine.theory.PhraseHands
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.Scene
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.theory.Touch
import com.bigpapituna.sharpears.engine.theory.whiteAtOrAbove
import com.bigpapituna.sharpears.engine.theory.whiteAtOrBelow
import com.bigpapituna.sharpears.engine.playground.Game

/**
 * The Learn course: levels of short lessons. Each lesson is a list of steps: text to read (and hear), and
 * usually an exercise played in the embedded player (listen, play, play from memory, or perform for a score).
 *
 * Exercise notation, one token per note: <name><octave>:<beats>, "r" is a rest, "+" stacks a chord.
 *   "C4:1 E4:1 G4:2 r:1 C4+E4+G4:4"
 * 2.1 adds alternatives, for the Playground's range exercises ([Token.anyOf]): "|" lists keys of which any one will do,
 * and ".." is every key from one to the other. The first key listed is the one the app plays when it demonstrates.
 *   "C#4|D#4:2" (either key of a two-black group), "C6|C#4..C8:2" (any key above middle C; the app plays C6)
 */
data class Exercise(
    val right: String = "",
    val left: String = "",
    val bpm: Double = 80.0,
    val practice: Practice = Practice.BOTH,
    /** Perform-mode accuracy (%) that counts as passing. */
    val pass: Int = 80,
    /** Beats per bar (quarter notes), for the metronome. */
    val beats: Int = 4,
    /** Faint lines at each beat on the lane (rhythm lessons turn them on whatever the setting). */
    val beatLines: Boolean = false,
    /** A flat key: black keys are named as flats on the notes and by the voice. */
    val flats: Boolean = false,
    /** Right-hand fingers by the book, one per note ("1 2 3 1 2 3 4 5"); empty: worked out by [Fingering]. */
    val fingers: String = "",
    /** Left-hand fingers by the book, one per left-hand note ("5 4 3 2 1 3 2 1"); empty: worked out by [Fingering]. */
    val leftFingers: String = "",
    /**
     * Where the first phrase ends, in beats from the start (8.0 = the end of bar 2 in four four). The kid skin plays
     * only this much as a bite ([bite]); null: the exercise is one bite already.
     */
    val phraseEnd: Double? = null,
    /** Black keys spelled against [flats], by pitch class ([Song.spellings]): a scale's or arpeggio's own letters. */
    val spellings: Map<Int, String> = emptyMap(),
)

/**
 * What a step asks of the player. READ: text only, tap Next. LISTEN: the app plays the phrase, nothing to do.
 * PLAY: wait mode with note names and fingers shown. FROM_MEMORY: wait mode with them hidden.
 * PERFORM: the notes keep moving; the step passes at the exercise's pass mark.
 * RAMP: Perform with the tempo ramp: it starts at 50% and rises 10 points after each clean pass (90% or better);
 * the step is done once it reaches 100% (the exercise's bpm is the target tempo).
 * PEDAL (MIDI only): the chords move in time; graded on changing the sustain pedal at each chord ([PedalGrader]).
 * DYNAMICS (MIDI only): wait mode; the phrase twice, soft then loud, graded per note ([DynamicsGrader]).
 */
enum class StepKind { READ, LISTEN, PLAY, FROM_MEMORY, PERFORM, RAMP, PEDAL, DYNAMICS;
    /** Steps that need what only a MIDI keyboard reports (the pedal, how hard a key is struck). */
    val needsMidi get() = this == PEDAL || this == DYNAMICS
}

/**
 * A step of a lesson. A READ step is a short explainer: its [scenes] (2.0.1), each a caption the pup says over an
 * animated picture, in order; [text] is the whole prose (the captions joined), kept for the voice catalog and
 * screen readers. Other steps carry an [exercise] played in the embedded player, and [text] is the instruction.
 */
data class Step(val kind: StepKind, val text: String, val exercise: Exercise? = null, val scenes: List<Scene> = emptyList(),
                /**
                 * A Playground game that draws its own stage instead of the embedded player (2.1, ui/playground): the pup
                 * flying with pitch, the black-key hunt, clapping, soft and loud, the concert. The step's [kind] and
                 * [exercise] still say how it runs, so a screen that does not know the game plays it in the player.
                 */
                val game: Game? = null) {
    val mode get() = when (kind) { StepKind.LISTEN -> Mode.LISTEN; StepKind.PERFORM, StepKind.RAMP, StepKind.PEDAL -> Mode.PERFORM; else -> Mode.WAIT }
    /** Note names and finger numbers are shown on the notes and keys. */
    val labels get() = kind != StepKind.FROM_MEMORY

    /** The step's exercise reached its end with [recap] (null outside Perform mode): is the step complete? */
    fun passed(recap: Recap?): Boolean = when (kind) {
        StepKind.READ -> true
        StepKind.PERFORM -> recap != null && recap.accuracy >= (exercise?.pass ?: 0)
        StepKind.RAMP -> true                    // the player ends a ramp step only once the ramp has reached 100%
        StepKind.PEDAL, StepKind.DYNAMICS -> false   // graded by [passed] with the run's report
        else -> true
    }

    /** The step's run ended with [r]: pedal and dynamics steps are graded on their own score, the rest as above. */
    fun passed(r: Report): Boolean = if (kind.needsMidi) (r.extraScore ?: 0) >= (exercise?.pass ?: 80) else passed(r.recap)
}

data class CourseLesson(
    val id: String,
    val title: String,
    val summary: String,
    val steps: List<Step>,
    /** A library song the lesson's last step sends you to. */
    val songId: String? = null,
)

data class CourseLevel(val id: String, val title: String, val about: String, val lessons: List<CourseLesson>)

private val LETTERS = mapOf('C' to 0, 'D' to 2, 'E' to 4, 'F' to 5, 'G' to 7, 'A' to 9, 'B' to 11)

/** "C4" = 60, "F#3" = 54, "Bb2" = 46. */
fun noteNumber(name: String): Int {
    var pc = LETTERS[name[0]] ?: throw IllegalArgumentException("bad note $name")
    var i = 1
    while (i < name.length && (name[i] == '#' || name[i] == 'b')) { pc += if (name[i] == '#') 1 else -1; i++ }
    return pc + 12 * (name.substring(i).toInt() + 1)
}

/**
 * One token of exercise notation: its [keys] (empty for a rest), where it starts and how long it lasts in beats, and
 * whether the keys are alternatives ([anyOf], any one of them) rather than a chord (all of them). [text] is the token's
 * keys as written, so a token can be written back shortened ([Exercise.bite]).
 */
data class Token(val keys: List<Int>, val startBeat: Double, val beats: Double, val anyOf: Boolean, val text: String) {
    val rest get() = keys.isEmpty()
}

/** "C#4..C8": every key from the first to the second, both included. */
private fun keyRange(spec: String): List<Int> {
    val (a, b) = spec.split("..").map(::noteNumber)
    require(a <= b) { "range runs down: $spec" }
    return (a..b).toList()
}

/** A line of exercise notation as tokens, in order. */
fun parseTokens(spec: String): List<Token> {
    var beat = 0.0
    val out = mutableListOf<Token>()
    for (tok in spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val (names, len) = tok.split(':').let { it[0] to it[1].toDouble() }
        val anyOf = '|' in names || ".." in names
        require(!(anyOf && '+' in names)) { "a token is a chord or alternatives, not both: $tok" }
        val keys = when {
            names == "r" -> emptyList()
            anyOf -> names.split('|').flatMap { if (".." in it) keyRange(it) else listOf(noteNumber(it)) }.distinct()
            else -> names.split('+').map(::noteNumber)
        }
        keys.forEach { require(it in 21..108) { "$tok: ${noteName(it)} is off the piano" } }
        out += Token(keys, beat, len, anyOf, names)
        beat += len
    }
    return out
}

/** One hand's notes from exercise notation, timed at [msPerBeat]. Alternatives give a note per key, all at once. */
fun parseLine(spec: String, hand: Hand, msPerBeat: Double): List<Note> = parseTokens(spec).flatMap { t -> t.notes(hand, msPerBeat) }

private fun Token.notes(hand: Hand, msPerBeat: Double) =
    keys.map { Note(it, (startBeat * msPerBeat).toLong(), (beats * msPerBeat * 0.92).toLong(), hand, 80) }

/**
 * Which hands-apart tip a two-hand song should show, from the hands it has been played through with:
 * "right" (start with the right hand alone), "left" (now the left alone), "together" (both passed alone),
 * or null (one-hand song, already played with both hands, or played through before hands were tracked).
 */
fun handsTip(twoHands: Boolean, done: Set<Practice>, playedThrough: Boolean): String? = when {
    !twoHands || Practice.BOTH in done -> null
    done.isEmpty() && playedThrough -> null
    Practice.RIGHT !in done -> "right"
    Practice.LEFT !in done -> "left"
    else -> "together"
}

/**
 * The exercise as a song for the player. Chords and single notes group by onset as in any song; each token of
 * alternatives becomes its own any-of group ([OnsetGroup.anyOf]) with its demonstration key first, and must not
 * share its moment with any other note (an any-of group is never part of a chord).
 */
fun Exercise.toSong(id: String, title: String): Song {
    val msPerBeat = 60_000 / bpm
    val tokens = parseTokens(right).map { it to Hand.RIGHT } + parseTokens(left).map { it to Hand.LEFT }
    val plain = tokens.filter { !it.first.anyOf }.flatMap { (t, h) -> t.notes(h, msPerBeat) }
    val choices = tokens.filter { it.first.anyOf }.map { (t, h) -> t.notes(h, msPerBeat).let { OnsetGroup(it.first().startMs, it, anyOf = true) } }
    for (c in choices) require((plain.map { it.startMs } + (choices - c).map { it.startMs }).none { kotlin.math.abs(it - c.startMs) <= GROUP_WINDOW_MS }) {
        "$id: alternatives at ${c.startMs} ms share their moment with other notes"
    }
    val notes = (plain + choices.flatMap { it.notes }).sortedWith(compareBy({ it.startMs }, { it.midi }))
    val groups = (groupOnsets(plain) + choices).sortedBy { it.startMs }
    val end = notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
    return Song("lesson-$id", title, bpm, beats to 4, notes, groups, emptyList(), end + 400, preferFlats = flats,
        fingering = fingers.split(" ").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() },
        leftFingering = leftFingers.split(" ").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() }, spellings = spellings)
}

/**
 * The exercise with every finger written out: a hand without book fingers gets the ones [Fingering] works out for the
 * whole exercise. Cut from this, a kid's bite shows the same fingers as the whole line it comes from (worked out on the
 * bite alone, the first phrase of Lightly Row put G under finger 4, the whole line under 5). A hand whose notes do not
 * all get a finger (alternatives, very wide chords) keeps being worked out.
 */
fun Exercise.withFingers(): Exercise {
    if (fingers.isNotBlank() && leftFingers.isNotBlank()) return this
    // Worked out once per exercise: COURSE asks 64 times for 17 exercises (the Playground's bites share their tunes),
    // and building it on the main thread before the first frame took 4 times as long without this.
    val memo = Fingered.memo
    if (memo.size > 256) memo.clear()
    return memo.getOrPut(this) { workOutFingers() }
}
/** An object, not a top-level val: lessons built in other files' static initializers call withFingers in any order. */
private object Fingered { val memo = java.util.concurrent.ConcurrentHashMap<Exercise, Exercise>() }

private fun Exercise.workOutFingers(): Exercise {
    val song = toSong("fingers", "")
    val worked = Fingering.of(song)
    fun of(hand: Hand) = song.notes.filter { it.hand == hand }.map { worked[it] }.takeIf { it.isNotEmpty() && it.all { f -> f != null } }?.joinToString(" ")
    return copy(fingers = fingers.ifBlank { of(Hand.RIGHT) ?: "" }, leftFingers = leftFingers.ifBlank { of(Hand.LEFT) ?: "" })
}

/** "1.5" stays "1.5", "2.0" is written "2". */
private fun beatsText(b: Double) = if (b == b.toLong().toDouble()) b.toLong().toString() else b.toString()

/**
 * The kid bite (2.1, docs/KIDS-PLAN.md section 3): the exercise cut at [Exercise.phraseEnd]. Tokens that start before
 * the cut stay (one that runs past it is shortened to end there), later ones go, in both hands; the fingers of the whole
 * exercise ([withFingers]) are cut to the notes that remain, so the bite and the whole line agree. An exercise without a
 * phrase end is its own bite.
 */
fun Exercise.bite(): Exercise {
    val end = phraseEnd ?: return this
    val whole = withFingers()
    fun cut(spec: String) = parseTokens(spec).filter { it.startBeat < end - 1e-9 }
        .joinToString(" ") { t -> "${t.text}:${beatsText(minOf(t.beats, end - t.startBeat))}" }
    fun keep(fingers: String, line: String) = fingers.split(' ').filter { it.isNotBlank() }.take(parseTokens(line).sumOf { it.keys.size }).joinToString(" ")
    val right = cut(right)
    val left = cut(left)
    return whole.copy(right = right, left = left, phraseEnd = null, fingers = keep(whole.fingers, right), leftFingers = keep(whole.leftFingers, left))
}

private fun lesson(id: String, title: String, summary: String, vararg steps: Step, song: String? = null) =
    CourseLesson(id, title, summary, steps.toList(), song)
/** A reading step: a scene per idea, each a caption the pup says over a picture; the step's text is the captions in order. */
private fun read(vararg scenes: Scene) = Step(StepKind.READ, scenes.joinToString(" ") { it.caption }, scenes = scenes.toList())
private fun listen(text: String, ex: Exercise) = Step(StepKind.LISTEN, text, ex)
private fun play(text: String, ex: Exercise) = Step(StepKind.PLAY, text, ex)
private fun memory(text: String, ex: Exercise) = Step(StepKind.FROM_MEMORY, text, ex)
private fun perform(text: String, ex: Exercise) = Step(StepKind.PERFORM, text, ex)
private fun ramp(text: String, ex: Exercise) = Step(StepKind.RAMP, text, ex)

private const val LISTEN_FIRST = "Listen first. Watch the notes fall and hear how it goes."
private const val YOUR_TURN = "Your turn. The notes wait for you, with letters and finger numbers to guide you."
private const val FROM_MEMORY = "Once more, from memory. The letters and finger numbers are hidden now."

// Opening phrases of the library songs, so song lessons can be heard and tried before the whole piece (Twinkle's is in
// Exercises.kt, shared with the Playground).
private val MINUET = Exercise(right = "D5:1 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:1 G4:1 G4:1 E5:1 C5:0.5 D5:0.5 E5:0.5 F#5:0.5 G5:1 G4:1 G4:1",
    left = "G3+B3:3 A3:3 B3:3 C4:3", bpm = 96.0, practice = Practice.RIGHT, beats = 3)
private val ELISE = Exercise(right = "E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 r:0.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 r:0.5 E4:0.5 G#4:0.5 B4:0.5 C5:2",
    bpm = 72.0, practice = Practice.RIGHT)

/**
 * A rhythm lesson: read how to count it, hear it on one key, tap it on middle C in time (Perform, 80%), then the
 * same rhythm as a short tune. Beat lines are on so each beat shows on the lane.
 */
private fun rhythmLesson(id: String, title: String, summary: String, scenes: List<Scene>, pattern: String, tune: String,
                         bpm: Double, beats: Int = 4, count: String): CourseLesson {
    val one = Exercise(right = pattern, bpm = bpm, practice = Practice.RIGHT, beats = beats, beatLines = true)
    val melody = one.copy(right = tune)
    return lesson(id, title, summary,
        read(*scenes.toTypedArray()),
        listen("Listen and count along: $count.", one),
        perform("Now tap it on middle C with your thumb, counting out loud. Two bars of clicks count you in. Score 80 percent to pass.", one),
        perform("The same rhythm as a little tune. Keep counting. Score 80 percent to pass.", melody))
}

private val RHYTHM = listOf(
    rhythmLesson("rhythm-quarters", "Steady quarter notes", "One note on every beat, counted in.", listOf(
        scene("A quarter note lasts one beat.", rhythm(4, "0:1 1:1 2:1 3:1", bpm = 72)),
        scene("Before you play, the app counts you in with two bars of clicks, and the numbers 1, 2, 3, 4 appear above the keys.",
            lane("C4:1 C4:1 C4:1 C4:1", bpm = 72, countIn = 2), Mood.LISTEN),
        scene("Start on the next 1.", rhythm(4, "4:1 5:1 6:1 7:1", bars = 2, bpm = 72, loops = 1), Mood.POINT)),
        pattern = QUARTERS_PATTERN,
        tune = "C4:1 E4:1 G4:1 E4:1 F4:1 D4:1 E4:1 C4:1", bpm = 72.0, count = "1, 2, 3, 4"),
    rhythmLesson("rhythm-halves", "Half and whole notes", "Hold through two beats and four.", listOf(
        scene("A half note lasts two beats and a whole note four.", rhythm(4, "0:2 2:2 4:4", bars = 2, bpm = 76, loops = 1)),
        scene("Keep counting while the key stays down, and let it go only on the next note.", lane("C4:2 C4:2 C4:4", bpm = 76), Mood.LISTEN)),
        pattern = HALVES_PATTERN,
        tune = "E4:2 D4:2 C4:4 E4:1 F4:1 G4:2 E4:4", bpm = 76.0, count = "1, 2, 3, 4"),
    rhythmLesson("rhythm-eighths", "Eighth notes", "Two notes in one beat: 1 and 2 and.", listOf(
        scene("An eighth note is half a beat.", valueTree(4, bpm = 80)),
        scene("Count the beats with an and between them: 1 and 2 and 3 and 4 and.",
            rhythm(4, "0:0.5 0.5:0.5 1:0.5 1.5:0.5 2:0.5 2.5:0.5 3:0.5 3.5:0.5", sub = 2, bpm = 66), Mood.LISTEN),
        scene("The second eighth falls on the and.", rhythm(4, "0:1 1:0.5 1.5:0.5 2:1 3:0.5 3.5:0.5", sub = 2, bpm = 66), Mood.POINT)),
        pattern = EIGHTHS_PATTERN,
        tune = "C4:1 D4:0.5 E4:0.5 F4:1 E4:0.5 D4:0.5 C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:2", bpm = 66.0, count = "1 and 2 and 3 and 4 and"),
    rhythmLesson("rhythm-dotted", "Dotted quarter and eighth", "Long, short: the lilt of many tunes.", listOf(
        scene("A dot makes a note half as long again: a dotted quarter lasts one and a half beats.",
            staff(sn("C5", NoteValue.QUARTER, "1 beat"), sn("C5", NoteValue.QUARTER, "1.5 beats", dotted = true), sn("C5", NoteValue.EIGHTH, "Half"), bpm = 72)),
        scene("An eighth note fills the rest of the second beat.", rhythm(4, "0:1.5 1.5:0.5 2:1 3:1", sub = 2, bpm = 72)),
        scene("Count 1 and 2 and, and play on 1 and on the and of 2.",
            rhythm(4, "0:1.5 1.5:0.5 2:1 3:1 4:1.5 5.5:0.5 6:2", bars = 2, sub = 2, bpm = 72, loops = 1), Mood.LISTEN)),
        pattern = "C4:1.5 C4:0.5 C4:1 C4:1 C4:1.5 C4:0.5 C4:2",
        tune = "E4:1.5 D4:0.5 C4:1 D4:1 E4:1.5 E4:0.5 E4:2", bpm = 72.0, count = "1 and 2 and 3 and 4 and"),
    rhythmLesson("rhythm-waltz", "Three beats: waltz time", "Count 1, 2, 3 with a lean on 1.", listOf(
        scene("In three four time each bar has three beats.", rhythm(3, "0:1 1:1 2:1", bpm = 84)),
        scene("Lean a little on beat 1: ONE, two, three.",
            dynamics("C4 C4 C4 C4 C4 C4", listOf(108, 58, 58, 108, 58, 58), mapOf(0 to "ONE", 3 to "ONE"), stepMs = 600), Mood.LISTEN,
            say = "Lean a little on beat 1: one, two, three."),
        scene("The count-in clicks three to a bar.", rhythm(3, "", bars = 2, bpm = 84, loops = 1), Mood.POINT)),
        pattern = "C4:1 C4:1 C4:1 C4:2 C4:1 C4:1 C4:1 C4:1 C4:3",
        tune = "C4:1 E4:1 G4:1 G4:2 E4:1 F4:1 D4:1 B3:1 C4:3", bpm = 84.0, beats = 3, count = "1, 2, 3"),
    rhythmLesson("rhythm-syncopation", "Simple syncopation", "Notes that land between the beats.", listOf(
        scene("Syncopation puts a note on the and and holds it over the next beat.", rhythm(4, "0:1 1:0.5 1.5:1 2.5:0.5 3:1", sub = 2, bpm = 66)),
        scene("Keep counting 1 and 2 and evenly. The note comes early, and the beat goes by while you hold it.",
            lane("C4:1 C4:0.5 C4:1 C4:0.5 C4:1", bpm = 60), Mood.LISTEN)),
        pattern = "C4:1 C4:0.5 C4:1 C4:0.5 C4:1 C4:0.5 C4:1 C4:0.5 C4:2",
        tune = "C4:1 D4:0.5 E4:1 D4:0.5 C4:1 E4:0.5 G4:1 E4:0.5 C4:2", bpm = 66.0, count = "1 and 2 and 3 and 4 and"),
)

/**
 * A key lesson: read where the black keys are and how the hand sits, play the scale (one octave up and down,
 * right hand), its three main chords, the scale from memory, then a four-bar tune in the key for a score.
 */
private fun keyLesson(id: String, title: String, summary: String, scenes: List<Scene>, scale: String, chords: String, chordNames: String,
                      tune: String, flats: Boolean = false, bpm: Double = 76.0, fingers: String = STANDARD_SCALE): CourseLesson {
    val s = Exercise(right = scale, bpm = bpm, practice = Practice.RIGHT, flats = flats, fingers = fingers)
    return lesson(id, title, summary,
        read(*scenes.toTypedArray()),
        play("Play the scale up and back down. Watch the finger numbers where the thumb tucks under.", s),
        play("The three main chords of the key: $chordNames. Press each chord's three keys together.",
            Exercise(right = chords, bpm = 60.0, practice = Practice.RIGHT, flats = flats)),
        memory("The scale once more, from memory.", s),
        perform("A short tune in the key, in time. Score 80 percent to pass.", Exercise(right = tune, bpm = 80.0, practice = Practice.RIGHT, flats = flats)))
}

/** Right-hand fingering for a one-octave scale up and down that starts on a white key with the thumb. */
private const val STANDARD_SCALE = "1 2 3 1 2 3 4 5 4 3 2 1 3 2 1"

/** A left-hand pattern: read it, hear both hands, the left hand alone, then both hands under a four-bar tune. */
private fun leftHandLesson(id: String, title: String, summary: String, scenes: List<Scene>, left: String, tune: String,
                           bpm: Double = 72.0, beats: Int = 4, leftFingers: String = ""): CourseLesson {
    val both = Exercise(right = tune, left = left, bpm = bpm, beats = beats, leftFingers = leftFingers)
    return lesson(id, title, summary,
        read(*scenes.toTypedArray()),
        listen("Listen to the pattern under the tune.", both),
        play("The left hand alone. The notes wait for you.", Exercise(left = left, bpm = bpm, practice = Practice.LEFT, beats = beats, leftFingers = leftFingers)),
        play("Now both hands: the tune on top, the pattern underneath.", both))
}

private val PEDAL_AND_DYNAMICS = listOf(
    run {
        val ex = Exercise(right = "C4+E4+G4:4 C4+E4+A4:4 C4+F4+A4:4 B3+D4+G4:4", bpm = 60.0, practice = Practice.RIGHT)
        lesson("pedal", "The sustain pedal", "Lift and press again at every chord change.",
            read(
                scene("The right pedal keeps notes ringing after your fingers leave the keys. Rest your right heel on the floor and press with the ball of the foot.",
                    pedal("C4+E4+G4", foot = true, chordMs = 3600)),
                scene("Change it with each chord: play the new chord, lift the pedal at that moment, and press it straight down again.",
                    pedal("C4+E4+G4 C4+E4+A4 C4+F4+A4 B3+D4+G4"), Mood.POINT),
                scene("The old chord stops, the new one rings on.", pedal("C4+E4+G4 C4+F4+A4", chordMs = 2400), Mood.LISTEN),
                scene("This step needs a MIDI keyboard, which reports the pedal.",
                    midi(pedalSignal("Pedal down"), struck("C4", 80, "C4"), pedalSignal("Pedal up")), Mood.THINK)),
            listen("Listen to the four chords, one bar each.", ex),
            Step(StepKind.PEDAL, "Play each chord and change the pedal with it. The bar under the keys shows when the pedal should be down. Score 80 percent to pass.", ex))
    },
    run {
        val ex = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:2 r:2 C4:1 D4:1 E4:1 F4:1 G4:2 r:2", bpm = 72.0, practice = Practice.RIGHT)
        lesson("dynamics", "Soft and loud", "The same phrase quietly, then strongly.",
            read(
                scene("Loudness comes from the speed of the key, not from pressing harder at the bottom.",
                    dynamics("C4 C4", listOf(36, 112), mapOf(0 to "Slow key", 1 to "Fast key"), stepMs = 1200), Mood.THINK),
                scene("For soft notes, start close to the key and move slowly. For loud notes, drop the weight of the arm.",
                    dynamics("E4 F4 G4 A4 G4 E4 F4 G4 A4 G4", listOf(36, 38, 40, 42, 38, 100, 104, 108, 112, 104), mapOf(0 to "p", 5 to "f"), stepMs = 420)),
                scene("Play the phrase twice: the first time soft, the second time loud.",
                    dynamics("C4 D4 E4 F4 G4 C4 D4 E4 F4 G4", listOf(40, 40, 42, 42, 44, 100, 102, 104, 106, 110), mapOf(0 to "p", 5 to "f"), stepMs = 420), Mood.LISTEN),
                scene("A MIDI keyboard reports how hard each key is struck. The microphone cannot.",
                    midi(struck("C4", 40, "40, soft"), struck("C4", 112, "112, loud"), struck("G4", 76, "76")))),
            listen("Listen: soft, then loud.", ex),
            Step(StepKind.DYNAMICS, "The first five notes soft, under 50 on the keyboard's scale of 127. The next five loud, over 90. Score 80 percent to pass.", ex))
    },
)

private val LEFT_HAND_PATTERNS = listOf(
    leftHandLesson("lh-waltz", "Waltz bass", "Root, then the chord twice: oom, pah, pah.", listOf(
        scene("In three four time the left hand plays the chord's root on beat 1, then the other two notes together on beats 2 and 3.",
            phrase("", "C3:1 E3+G3:1 E3+G3:1 F2:1 A2+C3:1 A2+C3:1 G2:1 B2+D3:1 B2+D3:1", bpm = 84, beats = 3)),
        scene("Keep beats 2 and 3 light, so the bass note sings.",
            dynamics("C3 E3+G3 E3+G3 F2 A2+C3 A2+C3", listOf(100, 48, 48, 100, 48, 48), mapOf(0 to "Strong", 1 to "light", 2 to "light"), stepMs = 560), Mood.LISTEN)),
        left = "C3:1 E3+G3:1 E3+G3:1 F2:1 A2+C3:1 A2+C3:1 G2:1 B2+D3:1 B2+D3:1 C3:1 E3+G3:1 E3+G3:1",
        tune = "E4:2 G4:1 F4:2 A4:1 D4:2 F4:1 C4:3", bpm = 84.0, beats = 3),
    leftHandLesson("lh-pop", "Root, fifth, octave", "The pop bass that walks up each chord.", listOf(
        scene("Many pop songs carry the left hand up through each chord: the root, the fifth above it, then the root an octave higher.",
            keys("A2", "E4", listOf(frame("C3", "Root"), frame("G3", "Fifth"), frame("C4", "Octave")), stepMs = 800, finale = "C3 G3 C4")),
        scene("Your hand opens wide, from finger 5 to finger 1.",
            fingers("", "C4 A3 G3 E3 C3", intro = false, steps = fingered("C3 G3 C4 G3 C3", "5 2 1 2 5", right = false, labels = mapOf(2 to "Wide open"), spread = 1.75f),
                stepMs = 650), Mood.POINT)),
        left = "C3:1 G3:1 C4:2 F2:1 C3:1 F3:2 G2:1 D3:1 G3:2 C3:1 G3:1 C4:2",
        tune = "E4:1 G4:1 C5:2 A4:1 F4:1 A4:2 B4:1 G4:1 D5:2 C5:4", leftFingers = "5 2 1 5 2 1 5 2 1 5 2 1"),
    leftHandLesson("lh-octaves", "Octaves", "The same note in two places, little finger and thumb.", listOf(
        scene("An octave is the same note eight white keys apart.", interval("A2", "E4", "C3", "C4", "Octave")),
        scene("Stretch the left hand from finger 5 to the thumb and keep that shape as you move from chord to chord.",
            fingers("", "C3 B2 A2 G2 C2", intro = false, steps = listOf(
                chordPress("5:C2 1:C3", right = false, spread = 1.75f), chordPress("5:F2 1:F3", right = false, label = "Same shape", spread = 1.75f),
                chordPress("5:G2 1:G3", right = false, spread = 1.75f), chordPress("5:C2 1:C3", right = false, spread = 1.75f)), stepMs = 900)),
        scene("Drop the hand from the wrist.",
            dynamics("C2+C3 C2+C3 F2+F3 F2+F3", listOf(96, 78, 96, 78), mapOf(0 to "Drop", 2 to "Drop"), stepMs = 700), Mood.LISTEN)),
        left = "C2+C3:2 C2+C3:2 F2+F3:2 F2+F3:2 G2+G3:2 G2+G3:2 C2+C3:4",
        tune = "G4:2 E4:2 A4:2 F4:2 G4:1 F4:1 D4:2 C4:4"),
    leftHandLesson("lh-tenths", "Broken tenths", "Root, fifth, then the third an octave up.", listOf(
        scene("A tenth is a third plus an octave: too wide to play at once for most hands, so it is broken.",
            interval("A2", "G4", "C3", "E4", "Tenth"), Mood.THINK),
        scene("Play the root, the fifth, then the chord's third above them, rising.",
            keys("A2", "G4", listOf(frame("C3", "Root"), frame("G3", "Fifth"), frame("E4", "Third")), stepMs = 700, finale = "C3 G3 E4"), Mood.LISTEN)),
        left = "C3:1 G3:1 E4:2 F2:1 C3:1 A3:2 G2:1 D3:1 B3:2 C3:1 G3:1 E4:2",
        tune = "G4:2 C5:2 A4:2 C5:2 B4:2 D5:2 C5:4"),
)

private const val RAMP_TEXT = "Now in time, starting at half speed. Each clean pass, 90 percent or better, goes ten percent faster. Reach full speed to finish."

/** Five fingers up and down twice, both hands an octave apart, in the five-finger position on [root]. */
private fun fiveFinger(id: String, key: String, notes: List<String>, blackKeys: String): CourseLesson {
    fun fingerKeys(octave: Int) = fiveFingerKeys(notes, octave)
    val ex = fiveFingerExercise(notes)
    // The pictures: the right hand's keys for fingers 1 to 5, the left hand's (thumb on the top note), up and back.
    val r = fingerKeys(4); val l = fingerKeys(3)
    val rs = r.joinToString(" "); val ls = l.reversed().joinToString(" ")
    val upDown = (r + r.reversed().drop(1)).joinToString(" "); val lUpDown = (l + l.reversed().drop(1)).joinToString(" ")
    val black = notes.indexOfFirst { it.length > 1 }
    val lo = noteName(whiteAtOrBelow(noteNumber(r[0]) - 1)); val hi = noteName(whiteAtOrAbove(noteNumber(r[4]) + 1))
    val blackScene = if (black < 0) scene(blackKeys, keys(lo, hi, walk(rs), stepMs = 450, finale = rs), Mood.POINT)
        else scene(blackKeys, fingers(rs, ls, intro = false, steps = List(2) {
            FingerStep(listOf(FingerPress(true, black + 1, noteNumber(r[black])), FingerPress(false, 5 - black, noteNumber(l[black]))),
                com.bigpapituna.sharpears.engine.theory.Pitch.parse(r[black]).label)
        }, stepMs = 1000), Mood.POINT)
    return lesson(id, "Five fingers in $key", "Both hands together, one finger per key, target 100 beats a minute.",
        read(
            scene("Put each hand in the five-finger position on $key: thumbs and little fingers on ${notes[0]} and ${notes[4]}, one finger per key.",
                fingers(rs, ls)),
            blackScene,
            scene("Both hands play the same notes an octave apart.",
                fingers(rs, ls, intro = false, steps = together(upDown, "1 2 3 4 5 4 3 2 1", lUpDown, "5 4 3 2 1 2 3 4 5"), stepMs = 430), Mood.LISTEN),
            scene("Keep every finger curved and every note the same loudness.", dynamics(upDown, List(9) { 78 }, mapOf(0 to "Even"), stepMs = 380), Mood.THINK)),
        listen("Listen at full speed.", ex),
        ramp(RAMP_TEXT, ex))
}

private val TECHNIQUE = listOf(
    fiveFinger("five-c", "C", listOf("C", "D", "E", "F", "G"), "All white keys."),
    fiveFinger("five-g", "G", listOf("G", "A", "B", "C", "D"), "All white keys, a step higher on the piano."),
    fiveFinger("five-f", "F", listOf("F", "G", "A", "Bb", "C"), "Finger 4 plays B flat in the right hand, finger 2 in the left."),
    fiveFinger("five-d", "D", listOf("D", "E", "F#", "G", "A"), "Finger 3 plays F sharp in both hands."),
    fiveFinger("five-a", "A", listOf("A", "B", "C#", "D", "E"), "Finger 3 plays C sharp in both hands."),
    run {
        val r = "C4:0.25 E4:0.25 F4:0.25 G4:0.25 A4:0.25 G4:0.25 F4:0.25 E4:0.25 D4:0.25 F4:0.25 G4:0.25 A4:0.25 B4:0.25 A4:0.25 G4:0.25 F4:0.25 " +
            "E4:0.25 G4:0.25 A4:0.25 B4:0.25 C5:0.25 B4:0.25 A4:0.25 G4:0.25 F4:0.25 A4:0.25 B4:0.25 C5:0.25 D5:0.25 C5:0.25 B4:0.25 A4:0.25"
        // Hanon's fingers, as the scene shows them: up to 5 and back down to 2 in every group (the left hand mirrors it).
        val ex = Exercise(right = r, left = r.replace("4:", "3:").replace("5:", "4:"), bpm = 72.0, beats = 2,
            fingers = List(4) { "1 2 3 4 5 4 3 2" }.joinToString(" "), leftFingers = List(4) { "5 4 3 2 1 2 3 4" }.joinToString(" "))
        lesson("hanon-1", "Hanon exercise 1", "The first four bars, both hands, target 72 beats a minute.",
            read(
                scene("Hanon's first exercise climbs one step at a time: a leap of a third from the thumb, then up to finger 5 and back down to finger 2.",
                    fingers(left = "", intro = false, steps = fingered("C4 E4 F4 G4 A4 G4 F4 E4 D4 F4 G4 A4 B4 A4 G4 F4", "1 2 3 4 5 4 3 2 1 2 3 4 5 4 3 2",
                        labels = mapOf(1 to "A third", 8 to "One step up")), stepMs = 360)),
                scene("Both hands play the same notes an octave apart.", phrase(ex, upTo = 4f, bpm = 40), Mood.LISTEN),
                scene("Lift each finger from the knuckle and play every note equally loud. Slow and even beats fast and patchy.",
                    dynamics("C4 E4 F4 G4 A4 G4 F4 E4", List(8) { 76 }, mapOf(0 to "Even"), stepMs = 420), Mood.THINK)),
            listen("Listen to the first four bars at full speed.", ex),
            ramp(RAMP_TEXT, ex))
    },
    run {
        val ex = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:1 B4:1 A4:1 G4:1 F4:1 E4:1 D4:1 C4:2",
            left = "C3:1 B2:1 A2:1 G2:1 F2:1 E2:1 D2:1 C2:1 D2:1 E2:1 F2:1 G2:1 A2:1 B2:1 C3:2", bpm = 80.0)
        lesson("contrary", "Contrary-motion C scale", "The hands move apart and back together, as mirror images.",
            read(
                scene("Both thumbs start on C, an octave apart.", fingers("C4 D4 E4 F4 G4", "C3 B2 A2 G2 F2"), Mood.POINT),
                scene("The right hand goes up while the left goes down, so the hands mirror each other and use the same fingers at the same time.",
                    fingers("C4 D4 E4 F4 G4", "C3 B2 A2 G2 F2", intro = false, steps = together("C4 D4 E4 D4 C4", "1 2 3 2 1", "C3 B2 A2 B2 C3", "1 2 3 2 1"), stepMs = 650)),
                scene("Both thumbs tuck under together after E, and finger 3 crosses over together on the way back.",
                    fingers("C4 D4 E4 F4 G4", "C3 B2 A2 G2 F2", intro = false, steps = together(
                        "C4 D4 E4 F4 G4 A4 B4 C5 B4 A4 G4 F4 E4 D4 C4", "1 2 3 1 2 3 4 5 4 3 2 1 3 2 1",
                        "C3 B2 A2 G2 F2 E2 D2 C2 D2 E2 F2 G2 A2 B2 C3", "1 2 3 1 2 3 4 5 4 3 2 1 3 2 1",
                        labels = mapOf(3 to "Thumbs under", 12 to "3 over")), stepMs = 460))),
            listen("Listen to the hands moving apart and back.", ex),
            play("Both hands, the notes wait for you.", ex),
            ramp(RAMP_TEXT, ex))
    },
)

private val KEYS = listOf(
    keyLesson("key-g", "G major", "One sharp: F sharp.", listOf(
        scene("G major uses the white keys except F: every F becomes F sharp, the black key just right of F.",
            keys("G4", "G5", walk("G4 A4 B4 C5 D5 E5 F#5 G5", mapOf(6 to "F♯")), stepMs = 500)),
        scene("Put your right thumb on G.", fingers("G4 A4 B4 C5 D5", ""), Mood.POINT),
        scene("Play G, A, B with fingers 1, 2, 3, tuck the thumb under to C, then 1, 2, 3, 4, 5 up to G. Finger 4 lands on F sharp.",
            fingers("G4 A4 B4 C5 D5", "", intro = false, steps = fingered("G4 A4 B4 C5 D5 E5 F#5 G5", "1 2 3 1 2 3 4 5", labels = mapOf(3 to "Thumb under", 6 to "F♯")), stepMs = 600))),
        scale = "G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 F#5:1 G5:1 F#5:1 E5:1 D5:1 C5:1 B4:1 A4:1 G4:2",
        chords = "G4+B4+D5:2 G4+C5+E5:2 F#4+A4+D5:2 G4+B4+D5:2", chordNames = "G, C and D, then G again",
        tune = "G4:1 A4:1 B4:1 G4:1 D5:2 B4:2 C5:1 B4:1 A4:1 F#4:1 G4:4"),
    keyLesson("key-f", "F major", "One flat: B flat.", listOf(
        scene("F major uses the white keys except B: every B becomes B flat, the black key just left of B.",
            keys("F4", "F5", walk("F4 G4 A4 Bb4 C5 D5 E5 F5", mapOf(3 to "B♭"), flats = true), stepMs = 500, flats = true)),
        scene("Put your right thumb on F.", fingers("F4 G4 A4 Bb4 C5", ""), Mood.POINT),
        scene("Play F, G, A, B flat with fingers 1, 2, 3, 4, tuck the thumb under to C, then 1, 2, 3, 4 up to F. Finger 4 plays B flat.",
            fingers("F4 G4 A4 Bb4 C5", "", intro = false, steps = fingered("F4 G4 A4 Bb4 C5 D5 E5 F5", "1 2 3 4 1 2 3 4", labels = mapOf(3 to "B♭", 4 to "Thumb under")), stepMs = 600))),
        scale = "F4:1 G4:1 A4:1 Bb4:1 C5:1 D5:1 E5:1 F5:1 E5:1 D5:1 C5:1 Bb4:1 A4:1 G4:1 F4:2",
        chords = "F4+A4+C5:2 F4+Bb4+D5:2 E4+G4+C5:2 F4+A4+C5:2", chordNames = "F, B flat and C, then F again",
        tune = "F4:1 G4:1 A4:1 F4:1 C5:2 A4:2 Bb4:1 A4:1 G4:1 E4:1 F4:4", flats = true,
        fingers = "1 2 3 4 1 2 3 4 3 2 1 4 3 2 1"),
    keyLesson("key-d", "D major", "Two sharps: F sharp and C sharp.", listOf(
        scene("D major has two sharps: F sharp and C sharp.", keys("D4", "D5", walk("D4 E4 F#4 G4 A4 B4 C#5 D5", mapOf(2 to "F♯", 6 to "C♯")), stepMs = 500)),
        scene("Both are the black keys just right of F and C.",
            keys("C4", "D5", listOf(frame("F4", "F", Tint.DIM), frame("F#4", "F♯"), frame("C5", "C", Tint.DIM), frame("C#5", "C♯")), stepMs = 800), Mood.POINT),
        scene("Right thumb on D.", fingers("D4 E4 F#4 G4 A4", "")),
        scene("Fingers 1, 2, 3 for D, E, F sharp, thumb under to G, then 1, 2, 3, 4, 5 up to D. Finger 3 plays F sharp and finger 4 plays C sharp.",
            fingers("D4 E4 F#4 G4 A4", "", intro = false, steps = fingered("D4 E4 F#4 G4 A4 B4 C#5 D5", "1 2 3 1 2 3 4 5", labels = mapOf(2 to "F♯", 3 to "Thumb under", 6 to "C♯")), stepMs = 600))),
        scale = "D4:1 E4:1 F#4:1 G4:1 A4:1 B4:1 C#5:1 D5:1 C#5:1 B4:1 A4:1 G4:1 F#4:1 E4:1 D4:2",
        chords = "D4+F#4+A4:2 D4+G4+B4:2 C#4+E4+A4:2 D4+F#4+A4:2", chordNames = "D, G and A, then D again",
        tune = "D4:1 F#4:1 A4:1 F#4:1 G4:1 E4:1 C#4:2 D4:1 E4:1 F#4:1 G4:1 A4:2 D4:2"),
    keyLesson("key-bb", "B flat major", "Two flats: B flat and E flat.", listOf(
        scene("B flat major has two flats: B flat and E flat, the black keys just left of B and E.",
            keys("A3", "C5", walk("Bb3 C4 D4 Eb4 F4 G4 A4 Bb4", mapOf(0 to "B♭", 3 to "E♭"), flats = true), stepMs = 500, flats = true)),
        scene("This scale starts on a black key, so the thumb does not.",
            fingers("F3 G3 A3 Bb3 C4", "", intro = false, steps = fingered("Bb3 Bb3", "4 4", labels = mapOf(0 to "4 on B♭")), stepMs = 1100), Mood.THINK),
        scene("Finger 4 plays B flat, then 1, 2, 3 for C, D, E flat, the thumb tucks under to F, and 1, 2, 3, 4 carry on to B flat.",
            fingers("F3 G3 A3 Bb3 C4", "", intro = false, steps = fingered("Bb3 C4 D4 Eb4 F4 G4 A4 Bb4", "4 1 2 3 1 2 3 4", labels = mapOf(4 to "Thumb under")), stepMs = 600))),
        scale = "Bb3:1 C4:1 D4:1 Eb4:1 F4:1 G4:1 A4:1 Bb4:1 A4:1 G4:1 F4:1 Eb4:1 D4:1 C4:1 Bb3:2",
        chords = "Bb3+D4+F4:2 Bb3+Eb4+G4:2 A3+C4+F4:2 Bb3+D4+F4:2", chordNames = "B flat, E flat and F, then B flat again",
        tune = "Bb3:1 D4:1 F4:1 D4:1 Eb4:2 C4:2 D4:1 Eb4:1 F4:1 A3:1 Bb3:4", flats = true,
        fingers = "4 1 2 3 1 2 3 4 3 2 1 3 2 1 4"),
    keyLesson("key-em", "E minor", "The minor key with one sharp.", listOf(
        scene("E minor shares its notes with G major: all white keys except F, which becomes F sharp.",
            keys("E4", "E5", walk("E4 F#4 G4 A4 B4 C5 D5 E5", mapOf(1 to "F♯")), stepMs = 500)),
        scene("It starts and ends on E, which gives it a darker sound.",
            chords(chord("G4 B4 D5", "G major", build = false), chord("E4 G4 B4", "E minor", build = false)), Mood.LISTEN),
        scene("Right thumb on E. Fingers 1, 2, 3 for E, F sharp, G, thumb under to A, then 1, 2, 3, 4, 5 up to E.",
            fingers("E4 F#4 G4 A4 B4", "", intro = false, steps = fingered("E4 F#4 G4 A4 B4 C5 D5 E5", "1 2 3 1 2 3 4 5", labels = mapOf(3 to "Thumb under")), stepMs = 600))),
        scale = "E4:1 F#4:1 G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 D5:1 C5:1 B4:1 A4:1 G4:1 F#4:1 E4:2",
        chords = "E4+G4+B4:2 E4+A4+C5:2 D4+F#4+B4:2 E4+G4+B4:2", chordNames = "E minor, A minor and B minor, then E minor again",
        tune = "E4:1 G4:1 B4:1 G4:1 A4:1 G4:1 F#4:2 E4:1 F#4:1 G4:1 A4:1 B4:2 E4:2"),
    keyLesson("key-dm", "D minor", "The minor key with one flat.", listOf(
        scene("D minor shares its notes with F major: all white keys except B, which becomes B flat.",
            keys("D4", "D5", walk("D4 E4 F4 G4 A4 Bb4 C5 D5", mapOf(5 to "B♭"), flats = true), stepMs = 500, flats = true)),
        scene("Right thumb on D. Fingers 1, 2, 3 for D, E, F, thumb under to G, then 1, 2, 3, 4, 5 up to D. Finger 3 plays B flat.",
            fingers("D4 E4 F4 G4 A4", "", intro = false, steps = fingered("D4 E4 F4 G4 A4 Bb4 C5 D5", "1 2 3 1 2 3 4 5", labels = mapOf(3 to "Thumb under", 5 to "B♭")), stepMs = 600))),
        scale = "D4:1 E4:1 F4:1 G4:1 A4:1 Bb4:1 C5:1 D5:1 C5:1 Bb4:1 A4:1 G4:1 F4:1 E4:1 D4:2",
        chords = "D4+F4+A4:2 D4+G4+Bb4:2 C4+E4+A4:2 D4+F4+A4:2", chordNames = "D minor, G minor and A minor, then D minor again",
        tune = "D4:1 F4:1 A4:2 Bb4:1 A4:1 G4:2 F4:1 E4:1 D4:1 E4:1 D4:4", flats = true),
)

/** The last step of a song lesson: the whole song, its sections and the loop, before opening it. */
private fun openSong(phrase: Exercise) = read(
    scene("Now open the whole song.", songMap("1 2 3 4", ex = phrase), Mood.CHEER),
    scene("The sections are marked on the bar at the bottom, so you can loop one at a time.",
        songMap("1 2 3 4", MapPass(0, 3), MapPass(1, 1), MapPass(1, 1), ex = phrase, sectionMs = 1200), Mood.POINT))

/**
 * A song lesson: one idea to read (its scenes preview the opening phrase), the opening phrase heard, the right hand
 * alone, the left hand alone, both hands in time (Perform, 80%), then on to the whole song in the library.
 */
private fun songLesson(id: String, title: String, summary: String, idea: List<Scene>, phrase: Exercise, song: String): CourseLesson = lesson(id, title, summary,
    read(*idea.toTypedArray()),
    listen("Listen to the opening phrase, both hands.", phrase),
    play("Right hand alone. The app plays the left hand. Letters and finger numbers are on the notes.", phrase.copy(practice = Practice.RIGHT)),
    play("Now the left hand alone. The app plays the tune.", phrase.copy(practice = Practice.LEFT)),
    perform("Both hands, in time. The notes keep moving. Score 80 percent to pass.", phrase),
    openSong(phrase),
    song = song)

// Opening phrases (four bars) of the repertoire songs, written in the same notation as scripts/gen_songs.py.
// MARY_X, BUNS_X and the phrases the Playground pages reuse (Row, London Bridge, Yankee Doodle, Old MacDonald, Lightly Row)
// live in Exercises.kt.
private val WEWISH_X = Exercise(right = "G4:1 G4:0.5 A4:0.5 G4:0.5 F#4:0.5 E4:1 E4:1 E4:1 A4:1 A4:0.5 B4:0.5 A4:0.5 G4:0.5 F#4:1 D4:1 D4:1",
    left = "G2:1 B2+D3:2 C3:1 E3+G3:2 D3:1 F#3+A3:2 D3:1 F#3+A3:2", bpm = 108.0, beats = 3)
private val EINE_X = Exercise(right = "G4:1 r:0.5 D4:0.5 G4:1 r:0.5 D4:0.5 G4:0.5 D4:0.5 G4:0.5 B4:0.5 D5:2 C5:1 r:0.5 A4:0.5 C5:1 r:0.5 A4:0.5 C5:0.5 A4:0.5 F#4:0.5 A4:0.5 D5:2",
    left = "G2:2 D3:2 G2:2 D3:2 D3:2 A3:2 D3:2 A3:2", bpm = 100.0)
private val GYMNO_X = Exercise(right = "F#5:3 A5:3 G5:3 F#5:3",
    left = "G2:1 B3+D4+F#4:2 D3:1 A3+C#4+F#4:2 G2:1 B3+D4+F#4:2 D3:1 A3+C#4+F#4:2", bpm = 56.0, beats = 3)
private val TOCCATA_X = Exercise(right = "A5:0.25 G5:0.25 A5:3.5 G5:0.25 F5:0.25 E5:0.25 D5:0.25 C#5:0.25 D5:2.75 " +
    "A4:0.25 G4:0.25 A4:3.5 G4:0.25 F4:0.25 E4:0.25 D4:0.25 C#4:0.25 D4:2.75 C#4+E4+G4+Bb4:4 D4+F4+A4+D5:4",
    left = "r:16 D2+A2:4 D2+A2+D3:4", bpm = 56.0)
private val SCARBOROUGH_X = Exercise(right = "D4:2 D4:1 A4:2 A4:1 E4:1.5 F4:0.5 E4:1 D4:3",
    left = "D3:3 D3:3 C3:3 D3:3", bpm = 84.0, beats = 3)

private val REPERTOIRE_1 = listOf(
    songLesson("rep-mary", "Mary Had a Little Lamb", "Four notes and a steady walk.", listOf(
        scene("Rest your right hand in the C position, thumb on C.", fingers(left = ""), Mood.POINT),
        scene("This tune uses only C, D, E and G, so the hand hardly moves.", phrase(MARY_X), Mood.LISTEN),
        scene("Watch the three E notes in bar two: lift a little between them so each one is heard.",
            touch("E4 E4 E4", Touch.STACCATO, labels = listOf("Lift between"), stepMs = 520))),
        MARY_X, "mary-had-a-little-lamb"),
    songLesson("rep-buns", "Hot Cross Buns", "Quick notes in the middle bar.", listOf(
        scene("Hot Cross Buns has one trick, in bar three: eight quick notes, C four times and D four times.", phrase(BUNS_X), Mood.LISTEN),
        scene("Count one and two and three and four and, so each quick note lasts half a beat.",
            rhythm(4, "0:0.5 0.5:0.5 1:0.5 1.5:0.5 2:0.5 2.5:0.5 3:0.5 3.5:0.5", sub = 2, bpm = 80)),
        scene("Keep the wrist loose and let each finger drop on its own key.",
            fingers(left = "", intro = false, steps = fingered("C4 C4 C4 C4 D4 D4 D4 D4", "1 1 1 1 2 2 2 2"), stepMs = 380), Mood.PLAY)),
        BUNS_X, "hot-cross-buns"),
    songLesson("rep-row", "Row, Row, Row Your Boat", "Long and short notes in threes.", listOf(
        scene("Each bar here has three beats, and the notes come in long and short pairs.", phrase(ROW_X), Mood.LISTEN),
        scene("A long note lasts a beat and a half.", rhythm(3, "0:1.5 1.5:1.5", sub = 2, bpm = 84)),
        scene("Say the words as you play: row, row, row your boat.",
            pop("Row", "row", "row", "your", "boat", notes = listOf("C4", "C4", "C4", "D4", "E4"), stepMs = 640), Mood.PLAY),
        scene("The merrily bars use six quick notes, so keep them light and even.",
            dynamics("C5 C5 C5 G4 G4 G4", List(6) { 58 }, mapOf(0 to "Light"), stepMs = 300))),
        ROW_X, "row-row-row-your-boat"),
    songLesson("rep-london", "London Bridge", "A long-short lilt in two four.", listOf(
        scene("The first two notes of London Bridge are a long-short pair: G for three quarters of a beat, then a quick A.",
            phrase(LONDON_X, upTo = 4f), Mood.LISTEN),
        scene("Say Lon-don with a little skip, then keep the beat steady.", rhythm(2, "0:0.75 0.75:0.25 1:0.5 1.5:0.5", sub = 4, bpm = 80)),
        scene("Count one two, one two.", rhythm(2, "0:1 1:1", bars = 2, bpm = 96), Mood.POINT)),
        LONDON_X, "london-bridge"),
    songLesson("rep-yankee", "Yankee Doodle", "A march with low pickup notes.", listOf(
        scene("Yankee Doodle marches on steady quarter notes.", phrase(YANKEE_X), Mood.LISTEN),
        scene("Bar two ends on the G below middle C, and bar four ends on the B just under it.",
            keys("F3", "F4", listOf(frame("C4", "Middle C"), frame("G3", "Bar two"), frame("B3", "Bar four")), stepMs = 1000), Mood.POINT),
        scene("Let your thumb reach down for those two notes, then come straight back to the C position.",
            fingers(left = "", intro = false, steps = fingered("E4 D4 G3 C4 E4 C4 B3 C4", "3 2 1 1 3 1 1 1", labels = mapOf(2 to "Reach down", 3 to "Back")), stepMs = 560))),
        YANKEE_X, "yankee-doodle"),
    songLesson("rep-macdonald", "Old MacDonald Had a Farm", "A tune that is wider than five fingers.", listOf(
        scene("This tune is a little wider than the five finger position.", phrase(MACDONALD_X), Mood.LISTEN),
        scene("It runs from the G below middle C up to E.", keys("E3", "G4", listOf(frame("G3", "Low G"), frame("E4", "E")), stepMs = 1100)),
        scene("Start with your thumb on that low G, and for E, I, E, I, O slide the hand up so your thumb sits on C.",
            fingers("G3 A3 B3 C4 D4", "", intro = false, steps = fingered("C4 C4 C4 G3 A3 A3 G3 E4 E4 D4 D4 C4", "4 4 4 1 2 2 1 3 3 2 2 1", labels = mapOf(7 to "Slide up")), stepMs = 460))),
        MACDONALD_X, "old-macdonald"),
)

private val REPERTOIRE_2 = listOf(
    songLesson("rep-lightly", "Lightly Row", "Four phrases and one shape.", listOf(
        scene("Lightly Row has four phrases: A, A again, a middle B, then A once more.", songMap("A A B A", ex = LIGHTLY_X)),
        scene("Spot the shape before you play.", phrase(LIGHTLY_X), Mood.LISTEN),
        scene("Phrase A steps down and then climbs, and the middle repeats single notes.",
            keys("B3", "A4", walk("G4 E4 E4 F4 D4 D4 C4 D4 E4 F4 G4 G4 G4", mapOf(5 to "Down", 10 to "Up")), stepMs = 380), Mood.POINT),
        scene("Knowing the plan makes the whole song quicker to learn.", songMap("A A B A", MapPass(0, 3), MapPass(2, 2), ex = LIGHTLY_X, sectionMs = 1200), Mood.CHEER)),
        LIGHTLY_X, "lightly-row"),
    songLesson("rep-wewish", "We Wish You a Merry Christmas", "G major in waltz time.", listOf(
        scene("This carol is in G major, so every F becomes F sharp, the black key just right of F.",
            keys("C4", "B4", listOf(frame("F4", "F", Tint.DIM), frame("F#4", "F♯")), stepMs = 1100), Mood.POINT),
        scene("It moves in three four time too: the bass note on beat one, the chord on beats two and three.",
            phrase("", WEWISH_X.left, bpm = 108, beats = 3, keyFifths = 1)),
        scene("Find the F sharp before you start.", phrase(WEWISH_X, keyFifths = 1), Mood.LISTEN)),
        WEWISH_X, "we-wish-you-a-merry-christmas"),
    songLesson("rep-eine", "Eine kleine Nachtmusik", "Mozart's famous opening.", listOf(
        scene("Mozart opens with a game of jumps: G, then the D below it, again and again, before the notes climb a G chord to the high D.",
            phrase(EINE_X, upTo = 8f, keyFifths = 1), Mood.LISTEN),
        scene("Practise the jump from G down to D until it is calm.", interval("C4", "A4", "D4", "G4", "G down to D")),
        scene("The eighth rests are part of the rhythm, so count straight through them.",
            rhythm(4, "0:1 r1:0.5 1.5:0.5 2:1 r3:0.5 3.5:0.5", sub = 2, bpm = 90), Mood.POINT)),
        EINE_X, "eine-kleine-nachtmusik"),
    songLesson("rep-gymno", "Gymnopedie No. 1", "Slow, soft and spacious.", listOf(
        scene("Satie wants this very slowly and very softly.", dynamics("F#5 A5 G5 F#5", listOf(40, 44, 40, 36), mapOf(0 to "pp"), stepMs = 1000), Mood.LISTEN),
        scene("The left hand rocks between two chords, G major seventh and D major seventh, one bar each.",
            chords(chord("G3 B3 D4 F#4", "Gmaj7", build = false), chord("D4 F#4 A4 C#5", "Dmaj7", build = false), chord("G3 B3 D4 F#4", "Gmaj7", build = false), low = "E3")),
        scene("Let the bass note of every bar ring, keep the chord light, and let the melody float on top.", phrase(GYMNO_X, keyFifths = 2), Mood.LISTEN)),
        GYMNO_X, "gymnopedie-1"),
    songLesson("rep-toccata", "Toccata in D minor", "A famous opening in two echoes.", listOf(
        scene("Bach's most famous opening is only a few notes: A, G, A with a long hold, then a quick run down to D.",
            phrase(TOCCATA_X, upTo = 8f, keyFifths = -1), Mood.LISTEN),
        scene("The same idea comes back an octave lower, then thick chords arrive.", phrase(TOCCATA_X, keyFifths = -1)),
        scene("Take a breath at each hold, as an organist would, and play the quick notes evenly.",
            dynamics("G5 F5 E5 D5 C#5 D5", listOf(80, 80, 80, 80, 80, 90), mapOf(0 to "Even"), stepMs = 260), Mood.THINK)),
        TOCCATA_X, "bach-toccata-opening"),
    songLesson("rep-scarborough", "Scarborough Fair", "An old tune in three four.", listOf(
        scene("Scarborough Fair uses only white keys, starting and ending on D.", keys("C4", "E5", walk("D4 E4 F4 G4 A4 B4 C5 D5", mapOf(0 to "D", 7 to "D")), stepMs = 450)),
        scene("That gives it its old, open sound.", phrase(SCARBOROUGH_X), Mood.LISTEN),
        scene("It is in three four time, so feel one strong beat and two light ones in every bar.",
            dynamics("D4 D4 D4 A4 A4 A4", listOf(104, 56, 56, 104, 56, 56), mapOf(0 to "Strong", 3 to "Strong"), stepMs = 560), Mood.POINT)),
        SCARBOROUGH_X, "scarborough-fair"),
)

private const val SCALE_C = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:1 B4:1 A4:1 G4:1 F4:1 E4:1 D4:1 C4:2"
private const val SCALE_G = "G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 F#5:1 G5:1 F#5:1 E5:1 D5:1 C5:1 B4:1 A4:1 G4:2"
private const val SCALE_F = "F4:1 G4:1 A4:1 Bb4:1 C5:1 D5:1 E5:1 F5:1 E5:1 D5:1 C5:1 Bb4:1 A4:1 G4:1 F4:2"
private const val SCALE_C_LEFT = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:1 B3:1 A3:1 G3:1 F3:1 E3:1 D3:1 C3:2"
private const val SCALE_G_LEFT = "G3:1 A3:1 B3:1 C4:1 D4:1 E4:1 F#4:1 G4:1 F#4:1 E4:1 D4:1 C4:1 B3:1 A3:1 G3:2"
private const val SCALE_F_LEFT = "F3:1 G3:1 A3:1 Bb3:1 C4:1 D4:1 E4:1 F4:1 E4:1 D4:1 C4:1 Bb3:1 A3:1 G3:1 F3:2"
private const val PASS_80 = "Score 80 percent to pass."

private const val LEGATO = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:4"
private const val STACCATO = "C4:0.5 r:0.5 D4:0.5 r:0.5 E4:0.5 r:0.5 F4:0.5 r:0.5 G4:0.5 r:0.5 F4:0.5 r:0.5 E4:0.5 r:0.5 D4:0.5 r:0.5 C4:2 r:2"
private const val TOUCH_MIX = "C4:1 D4:1 E4:1 F4:1 G4:0.5 r:0.5 G4:0.5 r:0.5 G4:0.5 r:0.5 G4:0.5 r:0.5 " +
    "A4:1 G4:1 F4:1 E4:1 D4:0.5 r:0.5 D4:0.5 r:0.5 C4:2"

private val SOFT_LOUD = Exercise(right = "E4:1 F4:1 G4:1 A4:1 G4:2 r:2 E4:1 F4:1 G4:1 A4:1 G4:2 r:2", bpm = 72.0, practice = Practice.RIGHT)
private val PROGRESSION = Exercise(right = "C4+E4+G4:4 B3+D4+G4:4 C4+E4+A4:4 C4+F4+A4:4", left = "C3:4 G2:4 A2:4 F2:4", bpm = 60.0)

private val TECHNIQUE_2 = listOf(
    lesson("t2-scales", "Scales in C, G and F", "Book fingering, both hands, in time.",
        read(
            scene("Three scales, three sets of fingers.",
                chords(chord("C4 E4 G4", "C major", build = false), chord("G4 B4 D5", "G major", build = false), chord("F4 A4 C5", "F major", build = false), holdMs = 1100)),
            scene("In C and G the right hand plays 1 2 3, the thumb tucks under, then 1 2 3 4 5.",
                fingers(left = "", intro = false, steps = fingered("C4 D4 E4 F4 G4 A4 B4 C5", "1 2 3 1 2 3 4 5", labels = mapOf(3 to "Thumb under")), stepMs = 520)),
            scene("F major has a black key under finger 4, B flat.",
                fingers("F4 G4 A4 Bb4 C5", "", intro = false, steps = fingered("F4 G4 A4 Bb4 C5 D5 E5 F5", "1 2 3 4 1 2 3 4", labels = mapOf(3 to "4 on B♭")), stepMs = 560), Mood.POINT),
            scene("Keep the wrist level, move the thumb early, and play every note the same loudness. The finger numbers on screen show the right hand.",
                dynamics("C4 D4 E4 F4 G4 A4 B4 C5", List(8) { 78 }, mapOf(0 to "Even"), stepMs = 420), Mood.THINK)),
        perform("C major, both hands an octave apart, up and back. $PASS_80",
            Exercise(right = SCALE_C, left = SCALE_C_LEFT, bpm = 72.0, fingers = STANDARD_SCALE)),
        perform("G major. Finger 4 plays F sharp on the way up and on the way down. $PASS_80",
            Exercise(right = SCALE_G, left = SCALE_G_LEFT, bpm = 72.0, fingers = STANDARD_SCALE)),
        perform("F major. Finger 4 plays B flat, then the thumb tucks under to C. $PASS_80",
            Exercise(right = SCALE_F, left = SCALE_F_LEFT, bpm = 72.0, flats = true, fingers = "1 2 3 4 1 2 3 4 3 2 1 4 3 2 1"))),
    run {
        // The hand stays still over each chord, as the scene says: bottom, middle, top, middle on 1, 3, 5, 3.
        val broken = Exercise(right = "C4:0.5 E4:0.5 G4:0.5 E4:0.5 C4:0.5 E4:0.5 G4:0.5 E4:0.5 F4:0.5 A4:0.5 C5:0.5 A4:0.5 F4:0.5 A4:0.5 C5:0.5 A4:0.5 " +
            "G4:0.5 B4:0.5 D5:0.5 B4:0.5 G4:0.5 B4:0.5 D5:0.5 B4:0.5 C4:0.5 E4:0.5 G4:0.5 E4:0.5 C4:2",
            left = "C3:4 F3:4 G2:4 C3:4", bpm = 66.0, fingers = List(7) { "1 3 5 3" }.joinToString(" ") + " 1")
        lesson("t2-broken", "Broken chords", "One chord, one note at a time: C, F and G.",
            read(
                scene("A broken chord plays the notes of a chord one after another.", chords(chord("C4 E4 G4", "C major"))),
                scene("Here the hand stays still over each chord: bottom, middle, top, middle, again. See the chord shape first, then let the fingers roll through it.",
                    fingers(left = "", intro = false, steps = fingered("C4 E4 G4 E4 C4 E4 G4 E4", "1 3 5 3 1 3 5 3", labels = mapOf(0 to "Bottom", 1 to "Middle", 2 to "Top")), stepMs = 420)),
                scene("The left hand holds the root. Change the hand shape on the first note of each bar, not in the middle.",
                    phrase(broken, upTo = 8f), Mood.LISTEN)),
            listen("Listen to the chords: C, F, G, then C again.", broken),
            play("Right hand alone, the notes waiting for you. The app plays the left hand.", broken.copy(practice = Practice.RIGHT)),
            perform("Both hands, in time, with even notes. $PASS_80", broken))
    },
    run {
        val both = Exercise(right = "$LEGATO $STACCATO", bpm = 76.0, practice = Practice.RIGHT)
        lesson("t2-touch", "Legato and staccato", "Smooth and detached, side by side.",
            read(
                scene("Legato means smooth: each note starts as the last one ends, with no gap.", touch("C4 D4 E4 F4 G4 F4 E4 D4", Touch.LEGATO)),
                scene("Staccato means short and detached: the key pops back up at once.", touch("C4 D4 E4 F4 G4 F4 E4 D4", Touch.STACCATO), Mood.POINT),
                scene("Both are about what the finger does after it lands. Keep the arm relaxed, and play staccato from the wrist, as if the keys were warm.",
                    touch("C4 D4 E4 F4 G4", Touch.LEGATO, Touch.STACCATO), Mood.THINK)),
            listen("Listen: first smooth, then short.", both),
            play("Smooth first. Keep each key down until the next one is down.", Exercise(right = LEGATO, bpm = 76.0, practice = Practice.RIGHT)),
            play("Now short and light. The rests show where the key should come up.", Exercise(right = STACCATO, bpm = 76.0, practice = Practice.RIGHT)),
            perform("Smooth bars and short bars mixed, in time. $PASS_80", Exercise(right = TOUCH_MIX, bpm = 76.0, practice = Practice.RIGHT)))
    },
    lesson("t2-shaping", "Shaping a phrase", "Rise to the high note, then settle.",
        read(
            scene("A phrase is like a sentence: it rises to a peak and settles.",
                dynamics("C4 D4 E4 F4 G4 A4 G4 F4 E4", listOf(44, 54, 66, 78, 92, 108, 86, 64, 46), mapOf(0 to "p", 5 to "f", 8 to "p"), stepMs = 450)),
            scene("Start softly, grow toward the highest note, and let the last note fade.",
                dynamics("E4 F4 G4 A4 G4", listOf(42, 62, 84, 108, 50), mapOf(0 to "p", 3 to "f"), stepMs = 650), Mood.LISTEN),
            scene("Loudness comes from the speed of the key, so let the weight of your arm travel into it. Never hit.",
                dynamics("A4 A4", listOf(48, 108), mapOf(0 to "Slow key", 1 to "Arm weight"), stepMs = 1200), Mood.THINK)),
        listen("Listen: the phrase soft, then loud.", SOFT_LOUD),
        play("Now play it yourself. Grow toward the A, then ease off.", SOFT_LOUD),
        Step(StepKind.DYNAMICS, "The first five notes soft, under 50 on the keyboard's scale of 127. The next five loud, over 90. $PASS_80", SOFT_LOUD)),
    lesson("t2-pedal-progression", "Pedal on a chord progression", "A fresh pedal at every chord change.",
        read(
            scene("The sustain pedal joins the chords of a progression into one smooth sound.", pedal("C4+E4+G4 B3+D4+G4 C4+E4+A4 C4+F4+A4"), Mood.LISTEN),
            scene("The rule is simple: play the new chord, then lift the pedal and press it straight down again.",
                pedal("C4+E4+G4 B3+D4+G4", chordMs = 2400), Mood.POINT),
            scene("Pedalled late, the old chord blurs into the new one.", pedal("C4+E4+G4 B3+D4+G4 C4+E4+A4", late = true), Mood.THINK),
            scene("Today's four chords are C, G, A minor and F, the pattern behind countless songs. The pedal step needs a MIDI keyboard.",
                chords(chord("C4 E4 G4", "C", build = false), chord("B3 D4 G4", "G", build = false), chord("C4 E4 A4", "A minor", build = false),
                    chord("C4 F4 A4", "F", build = false), holdMs = 1200))),
        listen("Listen to the four chords, one bar each.", PROGRESSION),
        play("Play the chords with both hands. Practise the change of hand first, pedal later.", PROGRESSION),
        Step(StepKind.PEDAL, "Play each chord and change the pedal with it. The bar under the keys shows when the pedal should be down. $PASS_80", PROGRESSION)),
    lesson("t2-sight", "Sight-reading habits", "Look ahead, keep the beat, never stop.",
        read(
            scene("Good sight-readers follow five habits. Check the key and time signature first.", staff(keyFifths = 1, time = 4 to 4), Mood.POINT),
            scene("Scan for the highest and lowest notes.",
                staff(sn("E4"), sn("F4"), sn("G4"), sn("E4"), sn("D4", NoteValue.HALF), sn("C4", NoteValue.HALF, "Lowest"), sn("E4"), sn("G4"),
                    sn("C5", label = "Highest"), sn("G4"), sn("E4", NoteValue.WHOLE), stepMs = 420)),
            scene("Count one bar in before you start. Keep your eyes a beat ahead of your hands.",
                phrase("E4:1 F4:1 G4:1 E4:1 D4:2 C4:2 E4:1 G4:1 C5:1 G4:1 E4:4", bpm = 72, lookAhead = true), Mood.LISTEN),
            scene("And never stop for a wrong note: keep the beat going and fix it next time. The sight-reading drill in Practice gives you endless new lines.",
                lane("C4:1 D4:1 E4:1 G4:1 F4:2 D4:2", bpm = 72), Mood.CHEER)),
        play("Read this line first, then play it. Look at the shape: where does it step, and where does it jump?",
            Exercise(right = "E4:1 F4:1 G4:1 E4:1 D4:2 C4:2 E4:1 G4:1 C5:1 G4:1 E4:4", bpm = 72.0, practice = Practice.RIGHT)),
        perform("A new line, in time. Do not stop for a wrong note. $PASS_80",
            Exercise(right = "C4:1 D4:1 E4:1 G4:1 F4:2 D4:2 E4:1 G4:1 E4:1 C4:1 D4:2 G3:2", bpm = 72.0, practice = Practice.RIGHT)),
        perform("One more, in G with an F sharp. Keep your eyes ahead of your hands. $PASS_80",
            Exercise(right = "G4:1 A4:1 B4:1 D5:1 C5:2 A4:2 B4:1 A4:1 F#4:1 D4:1 G4:4", bpm = 72.0, practice = Practice.RIGHT))),
)

/**
 * The whole course, in order. 2.1: Pup's Playground (engine/playground) comes first, so a kid starts there; a grown-up's
 * path starts at Basics (the integrator orders the path by Profile.firstUnitId). 2.2: its pond and concert hall pages
 * follow the garden, then Basics, the big kids' road. Built on first use: the Playground's
 * lessons call the notation functions in this file while they are made, so building the list with this file's other
 * values would see the Playground half made (null) when the Playground is touched first.
 */
val COURSE: List<CourseLevel> by lazy { com.bigpapituna.sharpears.engine.playground.PLAYGROUND_LEVELS + listOf(
    CourseLevel("basics", "Basics", "Where to sit, where the notes are, and which finger is which.", listOf(
        run {
            val e = Exercise(right = "C4:1 r:1 C4:1 r:1 C4:1 r:1 C4:2", bpm = 60.0, practice = Practice.RIGHT)
            lesson("sit", "Sit, then shape your hand", "Posture and a relaxed, curved hand.",
                read(
                    scene("Sit on the front half of the bench, far enough back that your elbows are a little in front of your body.",
                        posture(PostureCue.EDGE to "Front half", PostureCue.BENCH to "Right distance", PostureCue.BACK to "Tall back", PostureCue.ELBOWS to "Elbows ahead"),
                        Mood.POINT),
                    scene("Your forearms should be level with the keys.", posture(PostureCue.LEVEL to "Level forearms", PostureCue.HANDS to "Hands on the keys")),
                    scene("Let your hand hang loose, then rest it on the keys as if holding a small ball.",
                        hand(HandMove.HANG to "Hang loose", HandMove.BALL to "A small ball", HandMove.CURL to "Curve round it")),
                    scene("Fingers curved, playing on their tips, thumb resting on its side.",
                        hand(HandMove.TIPS to "On the tips", HandMove.THUMB to "Thumb on its side"), Mood.POINT)),
                listen("Listen to middle C, four times, with a rest between each one.", e),
                play("Play middle C with your right thumb. Let the key come back up between notes.", e))
        },
        run {
            val e = FIND_C
            lesson("find-c", "Find C anywhere", "The black keys show you where every C is.",
                read(
                    scene("Black keys come in groups of two and three.",
                        keys("C3", "B5", listOf(frame("C#3 D#3", "Two"), frame("F#3 G#3 A#3", "Three", Tint.BLUE), frame("C#4 D#4", "Two"), frame("F#4 G#4 A#4", "Three", Tint.BLUE)),
                            stepMs = 950, names = KeyNames.NONE), Mood.POINT),
                    scene("Every C is the white key just left of a group of two.",
                        keys("C3", "B5", listOf(frame("C3", "C"), frame("C4", "C"), frame("C5", "C")), stepMs = 900)),
                    scene("Middle C is the C closest to the middle of your piano.", keys("C3", "C6", listOf(frame("C4", "Middle C")), stepMs = 1500)),
                    scene("Notes are named by letter and by octave: middle C is C4, the next C up is C5.",
                        keys("C3", "C6", listOf(frame("C4", "C4", names = mapOf("C4" to "C4")), frame("C5", "C5", names = mapOf("C5" to "C5"))), stepMs = 1300),
                        say = "Notes are named by letter and by octave: middle C is C four, the next C up is C five.")),
                listen("Listen to every C from low to high.", e),
                play("Now play every C from low to high. Use your left hand for the low ones and your right hand for the high ones.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT, phraseEnd = 5.0)
            lesson("fingers-right", "Finger numbers", "Thumb is 1, little finger is 5.",
                read(
                    scene("Both hands number their fingers the same way: thumb 1, index 2, middle 3, ring 4, little finger 5.", fingers(), Mood.POINT),
                    scene("Put your right thumb on C and let each finger sit on its own white key, up to G.",
                        fingers(left = "", intro = false, steps = fingered("C4 D4 E4 F4 G4", "1 2 3 4 5"), stepMs = 650)),
                    scene("This is the C position.", keys("C4", "C5", walk("C4 D4 E4 F4 G4", mapOf(4 to "C position")), stepMs = 420, finale = "C4 E4 G4"), Mood.CHEER)),
                listen("Listen to five fingers walking up and back down.", e),
                play("Now walk up and back down. The small number on each falling note is the finger to use.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = LEFT_HAND_WALK
            lesson("fingers-left", "The left hand mirrors it", "Little finger on C, thumb on G.",
                read(
                    scene("In the left hand the thumb sits highest.", fingers(right = ""), Mood.POINT),
                    scene("Put your left little finger, finger 5, on the C below middle C, and your thumb on G.",
                        fingers(right = "", intro = false, steps = fingered("C3 D3 E3 F3 G3", "5 4 3 2 1", right = false, labels = mapOf(0 to "5 on C", 4 to "Thumb on G")), stepMs = 650))),
                listen(LISTEN_FIRST, e),
                play("Walk up and back down with your left hand, one finger per key.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 C4:1 C4:1 C4:1 G4:1 G4:1 G4:1 G4:1 C4:4", bpm = 66.0, practice = Practice.RIGHT, phraseEnd = 8.0)
            lesson("beat", "Keep a steady beat", "Play exactly when the note reaches the line.",
                read(
                    scene("Music moves on a steady beat.", rhythm(4, "0:1 1:1 2:1 3:1", bpm = 72), Mood.LISTEN),
                    scene("Count 1, 2, 3, 4 out loud as the notes fall, and play as each one touches the line.",
                        lane("C4:1 C4:1 C4:1 C4:1 G4:1 G4:1 G4:1 G4:1", bpm = 66), Mood.POINT)),
                listen("Listen and count along: 1, 2, 3, 4.", e),
                play("Play it once with the notes waiting for you, still counting out loud.", e),
                perform("Now in time. The notes keep moving and you get a score for timing. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", bpm = 66.0, practice = Practice.RIGHT)
            lesson("white-keys", "Name the white keys", "The musical alphabet: A to G, over and over.",
                read(
                    scene("White keys use the letters A to G, then start again.", keys("A3", "C5", walk("A3 B3 C4 D4 E4 F4 G4 A4", mapOf(7 to "Back to A")), stepMs = 520)),
                    scene("From C they go C, D, E, F, G, A, B, then C again.", keys("C4", "C5", walk("C4 D4 E4 F4 G4 A4 B4 C5", mapOf(7 to "C again")), stepMs = 520), Mood.POINT)),
                listen("Listen to one octave, C up to C. Watch the letters on the notes.", e),
                play("Now play up one octave. When you run out of fingers, your thumb tucks under to reach F.", e))
        },
    )),
    CourseLevel("beginner", "Beginner", "First melodies, note lengths, and both hands together.", listOf(
        run {
            val e = ODE_LINE
            lesson("first-melody", "Your first melody", "Ode to Joy, in the C position.",
                read(
                    scene("Keep your right hand in the C position: thumb on C, little finger on G.", fingers(left = ""), Mood.POINT),
                    scene("The whole tune fits under your fingers without moving the hand.",
                        fingers(left = "", intro = false, steps = fingered("E4 E4 F4 G4 G4 F4 E4 D4 C4 C4 D4 E4 E4 D4 D4", "3 3 4 5 5 4 3 2 1 1 2 3 3 2 2"), stepMs = 460),
                        Mood.LISTEN)),
                listen("Listen to the tune. You probably know it already.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e),
                perform("Now in time, at full speed. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 E4:1 G4:2 G4:2 E4:2 C4:4", bpm = 70.0, practice = Practice.RIGHT)
            lesson("lengths", "Long and short notes", "Quarter, half and whole notes.",
                read(
                    scene("A quarter note lasts one beat, a half note two, a whole note four.",
                        staff(sn("C5", NoteValue.QUARTER, "1 beat"), sn("C5", NoteValue.HALF, "2 beats"), sn("C5", NoteValue.WHOLE, "4 beats"), bpm = 70)),
                    scene("Longer blocks on the screen are longer notes: keep the key down until the block has passed the line.",
                        lane("C4:1 E4:1 G4:2 G4:2 E4:2 C4:4", bpm = 70), Mood.POINT)),
                listen("Listen to short and long notes. Count the beats as each one rings.", e),
                play(YOUR_TURN, e),
                perform("Now in time. Hold each note for its full length. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(left = "C3:4 F3:4 G3:4 C3:4", bpm = 70.0, practice = Practice.LEFT)
            lesson("bass", "Bass notes in the left hand", "Holding one low note under the music.",
                read(
                    scene("Many songs need only one left-hand note at a time: the bass.", phrase(TWINKLE, PhraseHands.LEFT, upTo = 8f), Mood.LISTEN),
                    scene("Play it and hold it for the whole bar.", lane("", "C3:4 F3:4", bpm = 70)),
                    scene("These are C, F, G and back to C: the three most common bass notes in the key of C.",
                        keys("C2", "B3", listOf(frame("C3", "C"), frame("F3", "F"), frame("G3", "G"), frame("C3", "C again")), stepMs = 900), Mood.POINT)),
                listen(LISTEN_FIRST, e),
                play("Play the four bass notes with your left hand and hold each one.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = TWINKLE
            lesson("together", "Hands together", "Melody on top, bass underneath.",
                read(
                    scene("Practise each hand alone first, then together.", phrase(TWINKLE, PhraseHands.RIGHT, PhraseHands.LEFT, PhraseHands.BOTH, upTo = 4f)),
                    scene("Look ahead: the left hand changes only every two beats.", phrase(TWINKLE, PhraseHands.LEFT, upTo = 8f, lookAhead = true), Mood.POINT)),
                listen("Listen to both hands together.", e),
                play("Right hand alone first. The app plays the left hand for you.", e.copy(practice = Practice.RIGHT)),
                play("Now both hands. The notes wait until both hands have played.", e),
                memory("Both hands once more, from memory.", e))
        },
        run {
            val e = Exercise(right = "D4:1 E4:1 F#4:1 G4:1 A4:1 G4:1 F#4:1 E4:1 D4:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("black-keys", "Sharps and flats", "Black keys sit between the letters.",
                read(
                    scene("A sharp is the next key up, a flat the next key down.",
                        keys("C4", "B4", listOf(frame("C4", "C"), frame("C#4", "C♯", gap = "up"), frame("E4", "E", clear = true),
                            frame("Eb4", "E♭", Tint.BLUE, names = mapOf("Eb4" to "E♭"), gap = "down")), stepMs = 1000)),
                    scene("The black key between F and G is F sharp, also called G flat.",
                        keys("C4", "B4", listOf(frame("F4", null, Tint.DIM, play = false), frame("G4", null, Tint.DIM, play = false),
                            frame("F#4", "F♯ or G♭", names = mapOf("F#4" to "F♯"))), stepMs = 800), Mood.POINT),
                    scene("This five-finger position starts on D and needs F sharp under your third finger.",
                        fingers("D4 E4 F#4 G4 A4", "", steps = fingered("D4 E4 F#4 G4 A4 G4 F#4 E4 D4", "1 2 3 4 5 4 3 2 1", labels = mapOf(2 to "F♯")), stepMs = 480))),
                listen("Listen for the F sharp, the black key under your third finger.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 r:1 E4:1 r:1 G4:1 r:1 C5:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("rests", "Rests", "Silence is part of the rhythm.",
                read(
                    scene("A rest is a beat with no note.", rhythm(4, "0:1 r1:1 2:1 r3:1", bpm = 70)),
                    scene("Keep counting through it, and lift your finger so the note stops.", lane("C4:1 r:1 E4:1 r:1 G4:1 r:1 C5:2", bpm = 70), Mood.LISTEN)),
                listen("Listen to the silences between the notes.", e),
                play(YOUR_TURN, e),
                perform("Now in time. Keep counting through the rests. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:1 B4:1 A4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT,
                phraseEnd = 8.0)
            lesson("scale", "Thumb under: the C major scale", "Eight notes with five fingers.",
                read(
                    scene("Play C, D, E with fingers 1, 2, 3, then tuck the thumb under to F and carry on with 1, 2, 3, 4, 5.",
                        fingers(left = "", intro = false, steps = fingered("C4 D4 E4 F4 G4 A4 B4 C5", "1 2 3 1 2 3 4 5", labels = mapOf(3 to "Thumb under")), stepMs = 600)),
                    scene("On the way down, finger 3 crosses over the thumb.",
                        fingers("F4 G4 A4 B4 C5", "", intro = false, steps = fingered("C5 B4 A4 G4 F4 E4 D4 C4", "5 4 3 2 1 3 2 1", labels = mapOf(5 to "3 over")), stepMs = 600), Mood.POINT),
                    scene("Keep the wrist level and let the thumb glide under. The hand should not jump.",
                        fingers(left = "", intro = false, steps = fingered("C4 D4 E4 F4 G4", "1 2 3 1 2", labels = mapOf(3 to "Glide")), stepMs = 800), Mood.THINK)),
                listen("Listen to the scale up and down.", e),
                play("Your turn. Watch the finger numbers where the thumb tucks under.", e),
                memory(FROM_MEMORY, e))
        },
        lesson("first-song", "Your first whole song", "Twinkle Twinkle, hands together.",
            read(
                scene("Learn a song in three passes: right hand alone, left hand alone, then both.", phrase(TWINKLE, PhraseHands.RIGHT, PhraseHands.LEFT, PhraseHands.BOTH, upTo = 4f)),
                scene("Use wait mode until it feels easy, then Perform mode for a score.",
                    lane("C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2", "C3:4 F3:2 C3:2", bpm = 80), Mood.POINT)),
            listen("Here is how Twinkle Twinkle begins, both hands.", TWINKLE),
            play("Try the opening with your right hand. The app plays the left hand.", TWINKLE.copy(practice = Practice.RIGHT)),
            read(
                scene("Now open the whole song.", songMap("A B A", ex = TWINKLE), Mood.CHEER),
                scene("Slow it down with the speed button if the notes come too fast.", songMap("A B A", MapPass(0, 0), MapPass(0, 0, 50), ex = TWINKLE), Mood.POINT)),
            song = "twinkle"),
    )),
    CourseLevel("rhythm", "Rhythm", "Counting: quarters, halves, eighths, dotted notes, waltz time and syncopation.", RHYTHM),
    CourseLevel("intermediate", "Intermediate", "Scales in both hands, chords, inversions and progressions.", listOf(
        run {
            val e = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:1 B3:1 A3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)
            lesson("scale-left", "The scale in the left hand", "5, 4, 3, 2, 1, then 3 crosses over.",
                read(
                    scene("Going up, the left hand plays C to G with fingers 5 to 1.",
                        fingers(right = "", intro = false, steps = fingered("C3 D3 E3 F3 G3", "5 4 3 2 1", right = false), stepMs = 600)),
                    scene("Then finger 3 crosses over the thumb to A.",
                        fingers(right = "", intro = false, steps = fingered("E3 F3 G3 A3 B3 C4", "3 2 1 3 2 1", right = false, labels = mapOf(3 to "3 over")), stepMs = 650), Mood.POINT)),
                listen(LISTEN_FIRST, e),
                play("Your turn. Watch for finger 3 crossing over the thumb.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:2", bpm = 60.0)
            lesson("scale-both", "Scales hands together", "Two hands, an octave apart.",
                read(
                    scene("The hands cross their thumbs at different moments, which is the whole challenge.",
                        fingers(intro = false, steps = together("C4 D4 E4 F4 G4 A4 B4 C5", "1 2 3 1 2 3 4 5", "C3 D3 E3 F3 G3 A3 B3 C4", "5 4 3 2 1 3 2 1",
                            labels = mapOf(3 to "Right thumb under", 5 to "Left 3 over")), stepMs = 720), Mood.THINK),
                    scene("Go slowly enough that every note lines up.", lane("C4:1 D4:1 E4:1 F4:1", "C3:1 D3:1 E3:1 F3:1", bpm = 60), Mood.LISTEN)),
                listen("Listen to both hands, an octave apart.", e),
                play("Your turn, both hands. The notes wait until both have played.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:2 F4+A4+C5:2 G4+B4+D5:2 C4+E4+G4:2", bpm = 60.0, practice = Practice.RIGHT,
                fingers = List(4) { "1 3 5" }.joinToString(" "))
            lesson("triads", "Triads", "Three notes, every other white key.",
                read(
                    scene("A triad stacks three notes a skip apart: C, E, G is C major.", chords(chord("C4 E4 G4", "C major", tags = listOf("root", "3rd", "5th")))),
                    scene("Fingers 1, 3 and 5 play it.",
                        fingers(left = "", steps = listOf(chordPress("1:C4 3:E4 5:G4", label = "1 3 5"), chordPress("1:C4 3:E4 5:G4")), stepMs = 900), Mood.POINT)),
                listen("Listen to C, F, G and C again.", e),
                play("Your turn. Press the three keys at the same moment.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:2 E4+G4+C5:2 G4+C5+E5:2 C5+E5+G5:2", bpm = 60.0, practice = Practice.RIGHT)
            lesson("inversions", "Inversions", "The same chord, a different note on the bottom.",
                read(
                    scene("Move the lowest note of a chord up an octave and you get an inversion.",
                        chords(chord("C4 E4 G4", "Root position", build = false), chord("E4 G4 C5", "First inversion"))),
                    scene("C, E, G becomes E, G, C, then G, C, E.",
                        chords(chord("C4 E4 G4", "C E G", build = false), chord("E4 G4 C5", "E G C", build = false), chord("G4 C5 E5", "G C E", build = false), holdMs = 1300), Mood.LISTEN),
                    scene("Inversions let the hand move less from chord to chord. The Chords tab has every chord in every inversion.",
                        chords(chord("C4 E4 G4", "C", build = false), chord("C4 F4 A4", "F", build = false), chord("B3 D4 G4", "G", build = false),
                            chord("C4 E4 G4", "C", build = false), holdMs = 1100), Mood.POINT)),
                listen("Listen: the same C major chord climbing through its inversions.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:2", bpm = 70.0, practice = Practice.LEFT)
            lesson("broken", "Broken chords", "The left hand plays a chord one note at a time.",
                read(
                    scene("This pattern is called Alberti bass: bottom, top, middle, top.",
                        keys("A2", "E4", listOf(frame("C3", "Bottom"), frame("G3", "Top"), frame("E3", "Middle"), frame("G3", "Top")), stepMs = 700)),
                    scene("It keeps the music moving under a melody.",
                        phrase("E4:2 F4:1 G4:1 G4:2 F4:1 E4:1", "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5", bpm = 72),
                        Mood.LISTEN)),
                listen(LISTEN_FIRST, e),
                play("Your turn, left hand. Bottom, top, middle, top.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:4 C4+F4+A4:4 B3+D4+G4:4 C4+E4+G4:4", left = "C3:4 F2:4 G2:4 C3:4", bpm = 70.0)
            lesson("progression", "I, IV, V, I", "The progression behind thousands of songs.",
                read(
                    scene("In the key of C, the I chord is C, IV is F and V is G.",
                        chords(chord("C4 E4 G4", "C", "I", build = false), chord("F4 A4 C5", "F", "IV", build = false), chord("G4 B4 D5", "G", "V", build = false)),
                        say = "In the key of C, the one chord is C, four is F and five is G."),
                    scene("The right hand moves to nearby inversions while the left hand plays the root.",
                        phrase("C4+E4+G4:2 C4+F4+A4:2 B3+D4+G4:2 C4+E4+G4:2", "C3:2 F2:2 G2:2 C3:2", bpm = 70), Mood.LISTEN)),
                listen("Listen to the four chords.", e),
                play("Your turn, both hands.", e),
                memory(FROM_MEMORY, e),
                perform("Now in time. Change chords right on the beat. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "A3:1 B3:1 C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 A3+C4+E4:4", bpm = 66.0, practice = Practice.RIGHT)
            lesson("minor", "Minor keys", "Same white keys, starting on A.",
                read(
                    scene("A minor uses the same white keys as C major but starts on A.",
                        keys("A3", "C5", walk("C4 D4 E4 F4 G4 A4 B4 C5", mapOf(7 to "C major")) + walk("A3 B3 C4 D4 E4 F4 G4 A4", mapOf(7 to "A minor"), clearFirst = true),
                            stepMs = 360)),
                    scene("That gives it a darker sound.", chords(chord("C4 E4 G4", "C major", build = false), chord("A3 C4 E4", "A minor", build = false)), Mood.LISTEN)),
                listen("Listen to the A minor scale, then the A minor chord.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        lesson("expression", "Playing with expression", "Loud and soft, smooth and detached.",
            read(
                scene("The mic and MIDI hear which notes you play, not how. That part is yours.",
                    dynamics("D5 G4 A4 B4 C5 D5 G4 G4", listOf(50, 44, 54, 64, 80, 100, 70, 54), mapOf(0 to "p", 5 to "f", 7 to "p"), stepMs = 480), Mood.THINK),
                scene("Play softly at the start, let the melody sing over the left hand, and connect notes smoothly unless they have a gap.",
                    touch("D5 G4 A4 B4 C5 D5 G4 G4", Touch.LEGATO, labels = listOf("Smooth and singing"), stepMs = 460)),
                scene("On a digital piano the right pedal holds notes after you lift your fingers.", pedal("G3+B3+D4 A3+C4+D4+F#4", foot = true, chordMs = 2600), Mood.LISTEN),
                scene("Change it when the harmony changes, or the sound blurs.", pedal("G3+B3+D4 C4+E4+G4 D4+F#4+A4", late = true), Mood.THINK)),
            listen("Here is how the Minuet in G begins. Listen to how the melody moves.", MINUET),
            play("Try the melody with your right hand. Make it sing.", MINUET),
            read(
                scene("Now open the whole Minuet in G.", phrase(MINUET, keyFifths = 1), Mood.CHEER),
                scene("Play it with expression.", dynamics("D5 G4 A4 B4 C5 D5 G4 G4", listOf(56, 50, 58, 66, 78, 98, 72, 58), mapOf(0 to "p", 5 to "f"), stepMs = 480))),
            song = "minuet-g"),
    ) + LEFT_HAND_PATTERNS + PEDAL_AND_DYNAMICS),
    CourseLevel("keys", "Keys", "Major and minor keys with sharps and flats: scales, their three main chords, and a tune in each.", KEYS),
    CourseLevel("advanced", "Advanced", "Longer scales, seventh chords, arpeggios and real repertoire.", listOf(
        run {
            val e = Exercise(right = "C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:0.5 E5:0.5 F5:0.5 G5:0.5 A5:0.5 B5:0.5 C6:2",
                bpm = 72.0, practice = Practice.RIGHT, fingers = "1 2 3 1 2 3 4 1 2 3 1 2 3 4 5")
            lesson("scale-two", "Two-octave scales", "Thumb under twice.",
                read(
                    scene("Two octaves use the fingering 1 2 3, 1 2 3 4, 1 2 3, 1 2 3 4 5.",
                        fingers(left = "", intro = false, steps = fingered("C4 D4 E4 F4 G4 A4 B4 C5 D5 E5 F5 G5 A5 B5 C6", "1 2 3 1 2 3 4 1 2 3 1 2 3 4 5",
                            labels = mapOf(3 to "Thumb under", 7 to "Thumb under", 10 to "Thumb under")), stepMs = 400)),
                    scene("Keep the tempo even across the thumb crossings.", phrase(e), Mood.LISTEN)),
                listen(LISTEN_FIRST, e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e),
                perform("Now in time, evenly. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "D4+F4+A4+C5:4 B3+D4+F4+G4:4 C4+E4+G4+B4:4", left = "D3:4 G2:4 C3:4", bpm = 60.0)
            lesson("sevenths", "Seventh chords", "Four notes: the colour of jazz.",
                read(
                    scene("Add one more skip to a triad and you get a seventh chord.", chords(chord("C4 E4 G4 B4", "C major 7", tags = listOf("root", "3rd", "5th", "7th")))),
                    scene("C major 7 is C, E, G, B. D minor 7 is D, F, A, C. G7 is G, B, D, F.",
                        chords(chord("C4 E4 G4 B4", "Cmaj7", build = false), chord("D4 F4 A4 C5", "Dm7", build = false), chord("G3 B3 D4 F4", "G7", build = false), low = "E3"),
                        Mood.LISTEN, say = "C major 7 is C, E, G, B. D minor 7 is D, F, A, C. G 7 is G, B, D, F."),
                    scene("This is the two, five, one progression. The Chords tab has it in five keys.", phrase(e), Mood.POINT)),
                listen("Listen to D minor 7, G7, and C major 7.", e),
                play("Your turn, both hands.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:0.5 E4:0.5 G4:0.5 C5:0.5 E5:0.5 G5:0.5 C6:1 G5:0.5 E5:0.5 C5:0.5 G4:0.5 E4:0.5 C4:2", bpm = 66.0, practice = Practice.RIGHT,
                fingers = "1 2 3 1 2 3 5 3 2 1 3 2 1")
            lesson("arpeggios", "Arpeggios", "A chord spread over two octaves.",
                read(
                    scene("Play the notes of a chord one at a time, up and down.", keys("C4", "C6", walk("C4 E4 G4 C5 E5 G5 C6 G5 E5 C5 G4 E4 C4"), stepMs = 330), Mood.LISTEN),
                    scene("The thumb tucks under after G.",
                        fingers(left = "", intro = false, steps = fingered("C4 E4 G4 C5 E5 G5 C6", "1 2 3 1 2 3 5", labels = mapOf(3 to "Thumb under"), spread = 1.6f), stepMs = 560),
                        Mood.POINT)),
                listen(LISTEN_FIRST, e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "E4:2 F4:1 G4:1 G4:2 F4:1 E4:1 D4:2 C4:2",
                left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5",
                bpm = 72.0)
            lesson("independence", "Hand independence", "A melody over a moving left hand.",
                read(
                    scene("The right hand plays the tune while the left keeps a broken-chord pattern going.", phrase(e), Mood.LISTEN),
                    scene("Learn the left hand until it runs on its own, then add the melody.", phrase(e, PhraseHands.LEFT, PhraseHands.BOTH, upTo = 4f), Mood.POINT)),
                listen("Listen to both hands together.", e),
                play("Left hand alone first. The app plays the melody.", e.copy(practice = Practice.LEFT)),
                play("Now both hands.", e),
                memory("Both hands once more, from memory.", e))
        },
        lesson("repertoire", "Learning a real piece", "Für Elise, section by section.",
            read(
                scene("Big pieces are learned in small loops.", songMap("A B A C A", MapPass(0, 4), MapPass(0, 0), MapPass(0, 0), ex = ELISE, sectionMs = 1100)),
                scene("Use the Loop button to repeat four bars, start at a slow speed, and play each hand alone before putting them together.",
                    songMap("A B A C A", MapPass(0, 0, 60), MapPass(0, 0, 80), MapPass(0, 0), ex = ELISE, sectionMs = 1100), Mood.POINT)),
            listen("Here is how Für Elise begins, right hand.", ELISE),
            play(YOUR_TURN, ELISE),
            memory(FROM_MEMORY, ELISE),
            read(
                scene("Now open the whole piece.", phrase(ELISE, upTo = 8f), Mood.CHEER),
                scene("When a section is reliable in wait mode, play it in Perform mode at full speed.",
                    songMap("A B A C A", MapPass(1, 1, 60), MapPass(1, 1), ex = ELISE, sectionMs = 1100))),
            song = "fur-elise"),
    )),
    CourseLevel("technique", "Technique", "Daily drills that build even, independent fingers, played faster each clean pass.", TECHNIQUE),
    CourseLevel("repertoire-1", "Repertoire 1", "Six easy tunes, learned phrase by phrase: hands apart, then together.", REPERTOIRE_1),
    CourseLevel("repertoire-2", "Repertoire 2", "Six medium pieces: new keys, longer phrases and famous openings.", REPERTOIRE_2),
    CourseLevel("technique-2", "Technique 2", "Scales with the book fingering, broken chords, touch, shaping, pedal and sight-reading habits.", TECHNIQUE_2),
) }
