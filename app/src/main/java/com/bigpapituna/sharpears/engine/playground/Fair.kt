package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.arm
import com.bigpapituna.sharpears.engine.dynamics
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.theory.ArmMove
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.walk
import com.bigpapituna.sharpears.engine.withFingers

/*
 * Pup's Playground, the fair (2.3): a short page of four reward games, the last before the big kids' road (after the
 * night sky). Each is a game of three rounds with a star a round: pop the balloons the pup
 * calls, spin the carousel with smooth runs up to G and back, ring the strongman's bell with a heavy arm, and play a song
 * in the photo booth for a photo card to send. Same rules as every page: a demo first, three tries and it goes by, every
 * sound answered, strict keys.
 */

/** The fair's course level; its page is the last of [PLAYGROUND_PAGES]. */
const val FAIR_ID = "playground-fair"

private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("fair-$n", title, summary, steps.toList())
private fun game(text: String, ex: Exercise, g: Game) = Step(StepKind.PLAY, text, ex, game = g)

/** The C position's fingers for [line]: thumb on C, one finger a key up to G. */
private fun cFingers(line: String) = parseTokens(line).flatMap { it.keys }.joinToString(" ") { "${C_POSITION.getValue(it)}" }
private val C_POSITION = mapOf(60 to 1, 62 to 2, 64 to 3, 65 to 4, 67 to 5)

/** Rounds of balloons, a bunch each, a breath between: the keys of the C position. */
private fun balloons(vararg bunches: String) = bunches.joinToString(" r:2 ") { b -> b.split(' ').joinToString(" ") { "$it:1" } }.let {
    Exercise(right = it, bpm = 60.0, practice = Practice.RIGHT, fingers = cFingers(it))
}
private val BALLOONS_ONE = balloons("E4 C4 G4", "D4 F4 E4", "C4 G4 D4 F4")
private val BALLOONS_TWO = balloons("G4 E4 C4 D4", "F4 D4 G4 E4", "C4 E4 G4 F4 D4")
/** Three runs up to G and back, a breath between. */
private const val RUN = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:2"
private val CAROUSEL_RUNS = Exercise(right = List(3) { RUN }.joinToString(" r:2 "), bpm = 76.0, practice = Practice.RIGHT,
    fingers = List(3) { "1 2 3 4 5 4 3 2 1" }.joinToString(" "))
/** Three rounds of three swings each, on middle C (any key near it will do: [StrongmanJudge]). */
private val SWINGS = Exercise(right = List(3) { "C4:1 C4:1 C4:2" }.joinToString(" r:2 "), bpm = 60.0, practice = Practice.RIGHT,
    fingers = List(9) { "1" }.joinToString(" "))

/** The photo booth's three photos: [song]'s start, three times, a breath between (its melody, with the fingers it shows on the shelf). */
fun photoExercise(song: ShelfSong): Exercise {
    val s = song.start.withFingers()
    return Exercise(right = List(PHOTOS) { s.right }.joinToString(" r:2 "), bpm = s.bpm, practice = Practice.RIGHT, beats = s.beats,
        fingers = List(PHOTOS) { s.fingers }.joinToString(" "))
}

/** Photos on the strip. */
const val PHOTOS = 3

// ---- The four games ---------------------------------------------------------------------------------------------------

val FAIR: CourseLevel = CourseLevel(FAIR_ID, "Pup's Fair", "Four games at the fair: balloons, the carousel, the strongman and the photo booth.", listOf(
    lesson(1, "Balloon pop", "Pop each balloon by playing its key.",
        read(
            scene("Welcome to the fair! The balloons are the colours of your keys.",
                pop("Red", "Yellow", "Teal", notes = listOf("C4", "E4", "G4"), stepMs = 700), Mood.CHEER),
            scene("I call a balloon. Play its key, and pop!", keys("B3", "A4", walk("C4 E4 G4"), stepMs = 700), Mood.POINT)),
        game("Pop the balloons I call! Play each one's key.", BALLOONS_ONE, Game.BALLOONS),
        game("More balloons! Listen for each one, then pop it.", BALLOONS_TWO, Game.BALLOONS)),

    lesson(2, "The carousel", "Play up to G and back, and the carousel spins faster.",
        read(
            scene("The carousel goes round when you play: up to G and back down.", keys("B3", "A4", walk("C4 D4 E4 F4 G4 F4 E4 D4 C4"), stepMs = 420), Mood.POINT),
            scene("One finger for each key. Every smooth run spins it a little faster!",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4 F4 G4 F4 E4 D4 C4", "1 2 3 4 5 4 3 2 1"), stepMs = 420), Mood.CHEER)),
        game("Play up to G and back down. Each smooth run spins the carousel faster!", CAROUSEL_RUNS, Game.CAROUSEL)),

    lesson(3, "The strongman", "Play a big, heavy note and ring the bell.",
        read(
            scene("Ring the strongman's bell! A heavy arm sends the puck flying up.", arm(ArmMove.HEAVY to "Heavy arm"), Mood.CHEER),
            scene("My ears guess how big each sound is. Every note sends the puck up!",
                dynamics("C4 C4 C4", listOf(50, 80, 118), mapOf(0 to "Up", 2 to "Ding"), stepMs = 800), Mood.THINK)),
        game("Three swings! Let your arm fall into middle C.", SWINGS, Game.STRONGMAN),
        game("Again! Can you ring the bell with a heavy arm?", SWINGS, Game.STRONGMAN)),

    lesson(4, "The photo booth", "Play a song you know, and take home a photo card.",
        read(
            scene("Smile! The photo booth takes a picture each time you play your song.",
                pop("Click", "Flash", "Smile!", notes = listOf("C5", "E5", "G5"), stepMs = 700), Mood.CHEER),
            scene("Pick a song you know. Play its start three times, one for each photo.",
                keys("A3", "A4", walk("C4 E4 G4"), stepMs = 600, names = KeyNames.NONE), Mood.POINT)),
        game("Pick a song, then play its start for each photo. Big smile!", photoExercise(SHELF.first()), Game.PHOTO),
        read(scene("What a photo strip! A grown-up can send it to someone you love.",
            pop("Snap", "Snap", "Snap!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700), Mood.CHEER))),
))

// ---- The games' rules -------------------------------------------------------------------------------------------------

/**
 * The strongman: any key the matcher confirmed or named is a swing (the round's next note: the game is how big, not
 * which key). How high the puck goes is a rough guess from the strike's rise ([height]), as soft and loud is.
 */
object StrongmanJudge {
    fun judge(sound: StageSound, expected: Int?): Answer {
        if (sound.by != HeardBy.MATCHER && sound.by != HeardBy.NAMED) return Answer.Heard
        if (sound.riseDb == null || sound.midi == null) return Answer.Heard
        return Answer.Found(expected ?: return Answer.Heard)
    }

    /** How high a strike that rose [riseDb] sends the puck, 0.1 to 1 (the bell): from a whisper to a loud note ([SoftLoud]'s lines, moved by [offsetDb]). */
    fun height(riseDb: Double, offsetDb: Double = 0.0): Float {
        val low = SoftLoud.SOFT_AT_MOST_DB - 6.0 - offsetDb
        val high = SoftLoud.LOUD_AT_LEAST_DB - offsetDb
        return (0.1 + 0.9 * (riseDb - low) / (high - low)).coerceIn(0.1, 1.0).toFloat()
    }

    /** The bell rings at this height. */
    const val BELL = 1f
}

/** The carousel: how fast it turns (turns a second) after [smooth] smooth runs. */
fun carouselSpeed(smooth: Int): Float = 0.12f + 0.16f * smooth.coerceIn(0, 3)

/** What the fair's stages say (part of [PlaygroundLines.CALL_OUTS], so the voice records them at kid speed). */
object FairLines {
    /** A balloon's colour (pre-readers) or letter (readers): "the red balloon", "the C balloon". */
    private fun name(midi: Int, reader: Boolean): String {
        val letter = noteName(midi).first()
        return if (reader) "$letter" else PageLines.ANIMALS[letter]?.first ?: "next"
    }

    /** The call for a round's first balloon. */
    fun balloon(midi: Int, reader: Boolean): String = "Pop the ${name(midi, reader)} balloon!"

    /** The call for the next balloon of a bunch. */
    fun next(midi: Int, reader: Boolean): String = if (reader) "Now ${name(midi, true)}!" else "Now the ${name(midi, false)} one!"

    const val ALL_POPPED = "Every balloon popped!"
    val POPPED = listOf("Pop!", "Bang!", "Pop, pop!")
    fun balloonMiss(reader: Boolean) = if (reader) "Look at the letter, and find its key." else "Look at the colour, and find its key."

    val SPIN = listOf("Faster!", "Round and round!", "Whee, faster still!")
    const val SPIN_SAME = "Round we go! A smooth run spins it faster."
    const val CAROUSEL_DONE = "What a ride!"
    const val CAROUSEL_MISS = "Up to G and back. Which key comes next?"

    /** What the pup cheers at the puck's height: every height gets a cheer. */
    fun height(h: Float): String = when {
        h >= StrongmanJudge.BELL -> "Ding! You rang the bell!"
        h >= 0.7f -> "So strong! Nearly to the bell!"
        h >= 0.4f -> "Strong!"
        else -> "Up it goes!"
    }
    const val STRONG_DONE = "What a strong pianist!"

    const val PICK_SONG = "Pick a song for your photos!"
    val SNAP = listOf("Smile!", "Say cheese!", "Big finish!")
    const val SNAPPED = "Click! What a photo!"
    const val PLAYBACK = "Here is your song, played back!"
    const val CARD = "Your photo card is ready!"

    val ALL: List<String> = buildList {
        for (k in listOf(60, 62, 64, 65, 67)) for (reader in listOf(true, false)) { add(balloon(k, reader)); add(next(k, reader)) }
        add(ALL_POPPED); addAll(POPPED); add(balloonMiss(true)); add(balloonMiss(false))
        addAll(SPIN); add(SPIN_SAME); add(CAROUSEL_DONE); add(CAROUSEL_MISS)
        listOf(0.2f, 0.5f, 0.8f, 1f).forEach { add(height(it)) }; add(STRONG_DONE)
        add(PICK_SONG); addAll(SNAP); add(SNAPPED); add(PLAYBACK); add(CARD)
    }.distinct()
}
