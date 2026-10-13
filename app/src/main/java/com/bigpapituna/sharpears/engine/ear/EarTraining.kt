package com.bigpapituna.sharpears.engine.ear

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.GameLine
import com.bigpapituna.sharpears.engine.Lit
import com.bigpapituna.sharpears.engine.rootName
import com.bigpapituna.sharpears.engine.spokenPitch

/*
 * Ear and rhythm training: the five games of Sharp Ears 2.0 (Learn > Ear training). Pure Kotlin, no Android.
 * Same shape as the Chords > Play games (engine/EarGames.kt): ten rounds, a seeded random, a prompt, an answer
 * check that returns a ChordGame.Answer, score and streak, and a line that says what it was.
 * Levels are 0..2 in code (the screens call them Level 1 to 3).
 */

/** The five games. [secondsPerRound] is a rough figure the daily workout uses to fit about five minutes. */
enum class EarKind(val id: String, val title: String, val howTo: String, val levels: List<String>, val secondsPerRound: Int) {
    RHYTHM("rhythm", "Rhythm tap",
        "Listen to a rhythm, then tap it on the pad after a one bar count in. Tap on the notes, stay silent on the rests.",
        listOf("Quarters and eighths", "Dotted notes and 3/4", "Offbeats and 6/8"), 14),
    MELODY("melody", "Melody memory",
        "A short melody plays and grows by one note every round. Play it back from the start. A wrong note starts the try over.",
        listOf("Five notes, C major", "An octave, other keys", "Wide range, minor keys"), 20),
    PITCH("pitch", "Perfect pitch",
        "One note sounds. Name it with the buttons, or find it on the keys, in any octave.",
        listOf("White keys", "All twelve notes", "Two octaves"), 9),
    DEGREE("degree", "Scale degrees",
        "The app sets a key with its chord and scale, then plays one note. Name its step in the scale: do re mi, or 1 to 7.",
        listOf("C major, do to sol", "Major keys", "Major and minor keys"), 14),
    PROGRESSION("prog", "Progressions",
        "A chord progression plays. Pick which one it is from the numerals, I for the home chord, V for the one that pulls back to it.",
        listOf("Three common ones", "Six in major", "Major and minor keys"), 12),
    ;

    fun howToFor(level: Int): String = howTo
    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

/** What a game asks of the player. */
enum class Answering { KEYS, CHOICES, KEYS_AND_CHOICES, TAPS }

/** One button in a choice game. [id] is what [EarTrainer.choose] takes. [sub] is a small second line. */
data class Choice(val id: Int, val label: String, val sub: String? = null, val spoken: String = label)

/** One sounding step of a prompt: [notes] struck together, held [holdMs], then [gapMs] of silence. */
data class Step(val notes: List<Int>, val holdMs: Int = 600, val gapMs: Int = 60, val velocity: Int = 85)

/** One timed sound of a rhythm: a click (accented or not) or a note, [atMs] after the start of its phase. */
data class Cue(val atMs: Long, val click: Boolean = false, val accent: Boolean = false, val note: Int? = null, val holdMs: Int = 0)

/**
 * What the generic game screen needs from any of the five games: rounds and scores, the prompt, what the app plays,
 * what the keys show, what the mic listens for, and the player's answers. Rhythm tap also takes a list of taps
 * (see [RhythmTapGame.grade]); the other four are answered by keys, choice buttons or both.
 */
interface EarTrainer {
    val kind: EarKind
    val level: Int
    val rounds: Int
    val round: Int
    val score: Int
    val streak: Int
    val bestStreak: Int
    val finished: Boolean
    val roundOver: Boolean
    val answering: Answering
    /** The game plays the prompt again by itself after a wrong note (melody memory). */
    val replayOnWrong: Boolean get() = false
    fun prompt(): GameLine
    /** Played once at the start of a round and on "Hear the key" (the key's chord and scale); usually empty. */
    fun intro(): List<Step> = emptyList()
    /** The question: played after the intro, and again on "Hear again". */
    fun toPlay(): List<Step>
    fun choices(): List<Choice> = emptyList()
    /** The choice that is right this round, once the round could be over (for colouring the buttons). */
    fun answerId(): Int? = null
    fun lit(): Map<Int, Lit>
    fun micNotes(): Set<Int>
    /** The matcher's groups (notes that ring together). */
    fun micGroups(): List<Set<Int>>
    fun input(midi: Int): ChordGame.Answer
    fun choose(id: Int): ChordGame.Answer
    fun skip()
    fun next()
    /** The round's answer, for the log. */
    fun describe(): String
}

fun newTrainer(kind: EarKind, level: Int, seed: Long = System.nanoTime(), rounds: Int = 10): EarTrainer = when (kind) {
    EarKind.RHYTHM -> RhythmTapGame(level, seed, rounds)
    EarKind.MELODY -> MelodyMemoryGame(level, seed, rounds)
    EarKind.PITCH -> PerfectPitchGame(level, seed, rounds)
    EarKind.DEGREE -> ScaleDegreeGame(level, seed, rounds)
    EarKind.PROGRESSION -> ProgressionGame(level, seed, rounds)
}

/** Round bookkeeping shared by the five games. Subclasses call [newRound] from their own init. */
abstract class TrainerBase(final override val kind: EarKind, final override val level: Int, final override val rounds: Int) : EarTrainer {
    final override var round = 0; private set
    final override var score = 0; private set
    final override var streak = 0; private set
    final override var bestStreak = 0; private set
    final override var roundOver = false; private set
    /** Wrong answers this round. */
    var wrongs = 0; protected set
    override val finished get() = round >= rounds

    protected fun endRound(clean: Boolean, line: GameLine): ChordGame.Answer {
        roundOver = true
        if (clean) { score++; streak++; bestStreak = maxOf(bestStreak, streak) } else streak = 0
        return ChordGame.Answer(ChordGame.Outcome.DONE, line, clean)
    }

    protected fun ignored() = ChordGame.Answer(ChordGame.Outcome.IGNORED, null)
    protected fun wrong(line: GameLine) = ChordGame.Answer(ChordGame.Outcome.WRONG, line)
    protected fun found(line: GameLine? = null) = ChordGame.Answer(ChordGame.Outcome.FOUND, line)

    override fun input(midi: Int): ChordGame.Answer = ignored()
    override fun choose(id: Int): ChordGame.Answer = ignored()

    /** Gives up on this round (no point) and moves on. */
    override fun skip() { if (!roundOver) { roundOver = true; streak = 0 }; next() }

    override fun next() {
        if (!roundOver || finished) return
        round++
        if (!finished) { roundOver = false; wrongs = 0; newRound() }
    }

    protected abstract fun newRound()
}

// Shared music bits -----------------------------------------------------------------------------------------

val MAJOR_SCALE = listOf(0, 2, 4, 5, 7, 9, 11)
/** Natural minor. */
val MINOR_SCALE = listOf(0, 2, 3, 5, 7, 8, 10)

fun keyName(pc: Int, minor: Boolean) = rootName(pc) + if (minor) " minor" else " major"
fun keySpoken(pc: Int, minor: Boolean) = spokenPitch(pc) + if (minor) " minor" else " major"

/** Where a key's tonic sits: from middle C up to F sharp, then the octave below (G3 to B3), so the scale stays in reach. */
fun tonicMidi(pc: Int) = if (pc <= 6) 60 + pc else 48 + pc

/** Scale notes of a key between [from] and [to] (MIDI, both included), ascending. */
fun scaleNotes(tonicPc: Int, minor: Boolean, from: Int, to: Int): List<Int> {
    val scale = if (minor) MINOR_SCALE else MAJOR_SCALE
    return (from..to).filter { n -> ((n - tonicPc) % 12 + 12) % 12 in scale }
}

/** The tonic triad of a key as MIDI notes, from [tonicMidi]. */
fun tonicTriad(pc: Int, minor: Boolean): List<Int> = tonicMidi(pc).let { t -> listOf(t, t + if (minor) 3 else 4, t + 7) }

private val SOLFEGE_MAJOR = listOf("do", "re", "mi", "fa", "sol", "la", "ti")
private val SOLFEGE_MINOR = listOf("do", "re", "me", "fa", "sol", "le", "te")
private val SOLFEGE_SPOKEN = mapOf("do" to "doh", "re" to "ray", "mi" to "mee", "fa" to "fah", "sol" to "sole", "la" to "lah", "ti" to "tee",
    "me" to "may", "le" to "lay", "te" to "tay")

/** The solfege syllable of scale step [degree] (0 = do); a minor key lowers the third, sixth and seventh. */
fun solfege(degree: Int, minor: Boolean) = (if (minor) SOLFEGE_MINOR else SOLFEGE_MAJOR)[degree]
fun solfegeSpoken(syllable: String) = SOLFEGE_SPOKEN[syllable] ?: syllable
fun ordinal(n: Int) = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh")[n - 1]

/** How a pitch class reads on a button: white keys plainly, black keys with both spellings. */
fun pitchLabel(pc: Int) = when (((pc % 12) + 12) % 12) {
    1 -> "C#/Db"; 3 -> "D#/Eb"; 6 -> "F#/Gb"; 8 -> "G#/Ab"; 10 -> "A#/Bb"
    else -> rootName(pc)
}

fun pitchSpoken(pc: Int) = when (((pc % 12) + 12) % 12) {
    1 -> "C sharp"; 3 -> "D sharp"; 6 -> "F sharp"; 8 -> "G sharp"; 10 -> "A sharp"
    else -> spokenPitch(pc)
}

/** A memory aid for finding a note on the keyboard by the groups of black keys. */
fun keyHint(pc: Int) = when (((pc % 12) + 12) % 12) {
    0 -> "C is the white key just left of the pair of black keys."
    1 -> "C sharp is the first black key of the pair."
    2 -> "D is the white key in the middle of the pair of black keys."
    3 -> "D sharp is the second black key of the pair."
    4 -> "E is the white key just right of the pair of black keys."
    5 -> "F is the white key just left of the group of three black keys."
    6 -> "F sharp is the first black key of the three."
    7 -> "G is the white key between the first and second black keys of the three."
    8 -> "G sharp is the middle black key of the three."
    9 -> "A is the white key between the second and third black keys of the three."
    10 -> "A sharp is the last black key of the three."
    else -> "B is the white key just right of the group of three black keys."
}

/** A round with the same pick as [prev] feels like a repeat; this draws until it differs (when there is a choice). */
internal fun <T> java.util.Random.pickDifferent(items: List<T>, prev: T?): T {
    var x: T
    do { x = items[nextInt(items.size)] } while (x == prev && items.size > 1)
    return x
}

// Progress events --------------------------------------------------------------------------------------------

/** What the Ear training screen reports to the app (XP, goals, streaks), see ui/ear/INTEGRATION.md. */
sealed interface EarEvent {
    /** One round finished; [clean] is true when it earned its point. */
    data class Round(val kind: EarKind, val level: Int, val clean: Boolean) : EarEvent
    /** A whole game finished (not the workout's games, those report [Workout]). */
    data class GameComplete(val kind: EarKind, val level: Int, val score: Int, val rounds: Int, val bestStreak: Int, val newBest: Boolean, val seconds: Int = 0) : EarEvent
    /** The daily workout finished: [games] games, [score] of [rounds] rounds clean, in about [seconds]. */
    data class Workout(val games: Int, val score: Int, val rounds: Int, val bestStreak: Int, val seconds: Int) : EarEvent
}
