package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.TWINKLE_LEFT_FINGERS
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.chord
import com.bigpapituna.sharpears.engine.chords
import com.bigpapituna.sharpears.engine.dynamics
import com.bigpapituna.sharpears.engine.frame
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.noteName
import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.rhythm
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.theory.Touch
import com.bigpapituna.sharpears.engine.touch
import com.bigpapituna.sharpears.engine.walk

/*
 * Pup's Playground, the night sky (2.3 part B): twelve lessons on rhythm and listening. A steady beat at three speeds,
 * ta, ti-ti and ta-a, rests, the metronome; then listening games, each a tap game first (the app plays, the child taps
 * the answer) and then the child plays it: up or down, same or different, loud or quiet, fast or slow, long or short,
 * happy or sad; a black-key jam with the band; and Twinkle under the stars. The same model and rules as the other pages.
 */

/** The night sky's course level (its page and place come with the world's part of 2.3: see ui/playground/INTEGRATION-B.md). */
const val NIGHT_SKY_ID = "playground-night"

private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("night-$n", title, summary, steps.toList())

// ---- Sounds for the tap games ---------------------------------------------------------------------------------------

private fun key(name: String) = noteNumber(name)
private fun note(name: String, ms: Int = 700, velocity: Int = 85) = listOf(TapSound(listOf(key(name)), ms, velocity, gapMs = 0))
private fun tune(names: String, ms: Int = 380, gap: Int = 60, velocity: Int = 85) =
    names.split(' ').map { TapSound(listOf(key(it)), ms, velocity, gapMs = gap) }
/** A chord, its notes one by one and then together. */
private fun chordSound(names: String) = names.split(' ').map { TapSound(listOf(key(it)), 260, 78, gapMs = 40) } +
    TapSound(names.split(' ').map(::key), 1500, 84, gapMs = 0)

private fun upDown(a: String, b: String) = TapRound(listOf(note(a), note(b)), if (key(b) > key(a)) TapChoice.UP else TapChoice.DOWN)
private fun sameOrNot(a: String, b: String) = TapRound(listOf(tune(a), tune(b)), if (a == b) TapChoice.SAME else TapChoice.DIFFERENT)
private fun louder(name: String, first: Int, second: Int) =
    TapRound(listOf(note(name, 900, first), note(name, 900, second)), if (first > second) TapChoice.FIRST else TapChoice.SECOND)
private fun pace(names: String, fast: Boolean) = TapRound(listOf(if (fast) tune(names, 170, 25) else tune(names, 620, 90)), if (fast) TapChoice.FAST else TapChoice.SLOW)
private fun length(name: String, long: Boolean) = TapRound(listOf(note(name, if (long) 1900 else 200)), if (long) TapChoice.LONG else TapChoice.SHORT)
private fun mood(names: String, happy: Boolean) = TapRound(listOf(chordSound(names)), if (happy) TapChoice.HAPPY else TapChoice.SAD)

/**
 * A tap game's exercise, for a screen that does not know the game: the sounds it plays, each a beat (a round's last
 * two), a breath between rounds.
 */
private fun tapExercise(rounds: List<TapRound>): Exercise = Exercise(right = rounds.joinToString(" r:1 ") { r ->
    val sounds = r.parts.flatten().map { s -> s.keys.joinToString("+") { noteName(it) } }
    sounds.mapIndexed { i, s -> "$s:${if (i == sounds.lastIndex) 2 else 1}" }.joinToString(" ")
}, bpm = 72.0, practice = Practice.RIGHT)

private fun tap(kind: TapKind, example: TapRound, vararg rounds: TapRound) = StageGame.Tap(kind, example, rounds.toList())
private fun tapStep(text: String, game: StageGame.Tap) = play(text, tapExercise(game.rounds)).on(game)

// ---- The beats and tunes --------------------------------------------------------------------------------------------

/** Middle C on [count] beats, one every [every] beats, at [bpm] (the beat games: a star on each). */
private fun beat(bpm: Double, count: Int = 8, every: Double = 1.0) =
    Exercise(right = List(count) { "C4:${if (every == 1.0) "1" else every.toInt().toString()}" }.joinToString(" "), bpm = bpm, practice = Practice.RIGHT,
        beatLines = true, fingers = List(count) { "1" }.joinToString(" "))
private val DRUM_BEAT = StageGame.Beat(BeatSound.DRUM)
private val CLICK_BEAT = StageGame.Beat(BeatSound.CLICK)

/** Ta, ti-ti, ta-a twice: a walking note, two running ones and a long one. */
private val TA_TITI_TAA = Exercise(right = "C4:1 C4:0.5 C4:0.5 C4:2 C4:1 C4:0.5 C4:0.5 C4:2", bpm = 60.0, practice = Practice.RIGHT, beatLines = true,
    fingers = "1 1 1 1 1 1 1 1")
/** Three claps and a rest, twice; then a rest sooner. */
private val REST_LATE = Exercise(right = "C4:1 C4:1 C4:1 r:1 C4:1 C4:1 C4:1 r:1", bpm = 66.0, practice = Practice.RIGHT, beatLines = true, fingers = "1 1 1 1 1 1")
private val REST_SOON = Exercise(right = "C4:1 r:1 C4:1 r:1 C4:1 C4:1 C4:1 r:1", bpm = 66.0, practice = Practice.RIGHT, beatLines = true, fingers = "1 1 1 1 1")

/** Higher or lower (night 5): the pup's note and the way to go. */
private val PITCH_ROUNDS = listOf(PitchRound(key("E4"), Way.HIGHER), PitchRound(key("A4"), Way.LOWER), PitchRound(key("D4"), Way.HIGHER),
    PitchRound(key("G4"), Way.LOWER), PitchRound(key("C4"), Way.HIGHER), PitchRound(key("B4"), Way.LOWER))

/** Two short tunes to copy back, the second of each pair a note changed (night 6). */
private val SKY_COPY = Exercise(right = "C4:1 D4:1 E4:1 r:2 C4:1 D4:1 G4:1 r:2 E4:1 D4:1 C4:1 r:2 E4:1 D4:1 E4:1", bpm = 80.0, practice = Practice.RIGHT,
    fingers = "1 2 3 1 2 5 3 2 1 3 2 3")

/** Middle C once your usual way, then loud, quiet, loud, quiet (night 7): a note a round. */
private val LOUD_QUIET_ROUNDS: List<Loudness?> = listOf(null, Loudness.LOUD, Loudness.SOFT, Loudness.LOUD, Loudness.SOFT)
private val LOUD_QUIET = Exercise(right = List(LOUD_QUIET_ROUNDS.size) { "C4:2" }.joinToString(" r:2 "), bpm = 60.0, practice = Practice.RIGHT,
    fingers = List(LOUD_QUIET_ROUNDS.size) { "1" }.joinToString(" "))

/** C D E F G your usual way, then fast, then slow (night 8). */
private val FIVE_UP = Exercise(right = List(TempoTune.ROUNDS.size) { "C4:1 D4:1 E4:1 F4:1 G4:1" }.joinToString(" r:2 "), bpm = 76.0, practice = Practice.RIGHT,
    fingers = List(TempoTune.ROUNDS.size) { "1 2 3 4 5" }.joinToString(" "))

/** Long and short notes on middle C: ta-a, ta-a, ti, ti, ta, twice (night 9). */
private val LONG_SHORT = Exercise(right = "C4:2 C4:2 C4:0.5 C4:0.5 C4:1 C4:2 C4:2 C4:0.5 C4:0.5 C4:1", bpm = 66.0, practice = Practice.RIGHT, beatLines = true,
    pass = CONCERT_PASS, fingers = List(10) { "1" }.joinToString(" "))

/** The happy chord three times, a breath between (night 10): thumb, middle finger and little finger, as the pond's chord. */
private val HAPPY_CHORD = Exercise(right = "C4+E4+G4:2 r:2 C4+E4+G4:2 r:2 C4+E4+G4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "1 3 5 1 3 5 1 3 5")

/** Any black key from the dog house above middle C to the fence an octave up, the app's first (the jam, for a screen that does not know it). */
private val ANY_BLACK = "F#4|" + JAM_KEYS.filter { it != 66 }.joinToString("|") { noteName(it) }
private fun jamExercise(bpm: Double, rounds: Int) = Exercise(right = List(rounds) { "$ANY_BLACK:2" }.joinToString(" "), bpm = bpm, practice = Practice.RIGHT)

/** Twinkle's middle (the pond's "up above the world so high"), and the whole star song with the band at 70. */
private const val TWINKLE_MIDDLE = "G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2"
private const val TWINKLE_MIDDLE_LEFT = "C3:2 F3:2 C3:2 G2:2 C3:2 F3:2 C3:2 G2:2"
internal val TWINKLE_ALL = TWINKLE.copy(right = "${TWINKLE.right} $TWINKLE_MIDDLE ${TWINKLE.right}", left = "${TWINKLE.left} $TWINKLE_MIDDLE_LEFT ${TWINKLE.left}",
    bpm = 70.0, practice = Practice.RIGHT, phraseEnd = null, pass = CONCERT_PASS,
    leftFingers = "$TWINKLE_LEFT_FINGERS 5 2 5 5 5 2 5 5 $TWINKLE_LEFT_FINGERS")

// ---- The tap games ---------------------------------------------------------------------------------------------------

private val UP_OR_DOWN = tap(TapKind.UP_DOWN, upDown("C4", "G4"),
    upDown("A4", "D4"), upDown("E4", "C5"), upDown("G4", "C4"), upDown("D4", "A4"), upDown("C5", "F4"), upDown("F4", "B4"))
private val SAME_OR_NOT = tap(TapKind.SAME_DIFFERENT, sameOrNot("C4 D4 E4", "C4 D4 E4"),
    sameOrNot("C4 E4 G4", "C4 E4 G4"), sameOrNot("G4 E4 C4", "G4 F4 C4"), sameOrNot("C4 C4 G4", "C4 C4 G4"), sameOrNot("E4 D4 C4", "E4 D4 E4"),
    sameOrNot("D4 E4 F4 E4", "D4 E4 F4 E4"), sameOrNot("G4 G4 E4", "G4 G4 A4"))
private val LOUDER = tap(TapKind.LOUDER, louder("G4", 30, 115),
    louder("C4", 115, 30), louder("E4", 32, 112), louder("G4", 112, 34), louder("D4", 28, 115), louder("C5", 36, 110), louder("A4", 110, 30))
private val FAST_OR_SLOW = tap(TapKind.FAST_SLOW, pace("C4 D4 E4 F4 G4", fast = true),
    pace("C4 D4 E4", fast = false), pace("G4 F4 E4 D4 C4", fast = true), pace("E4 D4 C4", fast = false), pace("C4 E4 G4 E4 C4", fast = true),
    pace("G4 A4 G4", fast = false), pace("C4 D4 E4 D4 C4", fast = true))
private val LONG_OR_SHORT = tap(TapKind.LONG_SHORT, length("G4", long = true),
    length("C4", long = false), length("E4", long = true), length("A4", long = false), length("D4", long = false), length("F4", long = true), length("C5", long = true))
private val HAPPY_OR_SAD = tap(TapKind.HAPPY_SAD, mood("C4 E4 G4", happy = true),
    mood("C4 Eb4 G4", happy = false), mood("F4 A4 C5", happy = true), mood("A3 C4 E4", happy = false), mood("G3 B3 D4", happy = true),
    mood("D4 F4 A4", happy = false), mood("C4 E4 G4", happy = true))

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val NIGHT_SKY: CourseLevel = CourseLevel(NIGHT_SKY_ID, "Pup's Night Sky",
    "Twelve games under the stars: the beat, rests and the metronome, then listening for up and down, loud and quiet, happy and sad.", listOf(
    lesson(1, "Steady beat", "Clap and play on every beat: slowly, then a bit quicker, then quick.",
        read(
            scene("The stars twinkle in time, like a heartbeat. That is a steady beat.",
                sayRhythm("0:1 1:1 2:1 3:1", listOf("Twin", "kle", "twin", "kle"), 72), Mood.LISTEN),
            scene("A steady beat can be slow, a bit quicker, or quick. It never wobbles.",
                pop("Slow", "Quicker", "Quick!", notes = listOf("C4", "C4", "C4"), stepMs = 900), Mood.POINT)),
        play("Clap on every beat with my drum. Not faster, not slower.", DRUM.copy(bpm = 72.0), Game.CLAP).on(StageGame.NightClap),
        play("Now play middle C on every beat, slowly. Each one lights a star.", beat(60.0)).on(DRUM_BEAT),
        play("A bit quicker now. Keep your C steady with my drum.", beat(80.0)).on(DRUM_BEAT),
        play("Quick! Can your C keep up with the stars?", beat(100.0, count = 12)).on(DRUM_BEAT)),

    lesson(2, "Ta, ti-ti, ta-a", "Say it, clap it, play it: walking notes, running notes and long notes.",
        read(
            scene("Ta walks, ti-ti runs, and ta-a holds on for two beats.",
                sayRhythm("0:1 1:0.5 1.5:0.5 2:2", listOf("Ta", "", "Ti", "ti", "Ta", "", "a", ""), 60, sub = 2), Mood.LISTEN),
            scene("First we say it, then we clap it, then we play it on a key.",
                pop("Say it", "Clap it", "Play it!", notes = listOf("C4", "C4+E4", "C4+E4+G4"), stepMs = 800), Mood.CHEER)),
        listen("Listen and say it with me: ta, ti-ti, ta-a, then once more.", TA_TITI_TAA),
        play("Clap it with my drum: ta, ti-ti, ta-a.", TA_TITI_TAA, Game.CLAP).on(StageGame.NightClap),
        perform("Now play it on middle C. The clicks count you in.", TA_TITI_TAA.copy(pass = CONCERT_PASS)),
        perform("The same words on a little tune: ta, ti-ti, ta-a.",
            Exercise(right = "G4:1 G4:0.5 G4:0.5 E4:2 F4:1 F4:0.5 F4:0.5 D4:2", bpm = 60.0, practice = Practice.RIGHT, beatLines = true, pass = CONCERT_PASS,
                fingers = "5 5 5 3 4 4 4 2"))),

    lesson(3, "Shh, a rest", "A rest is a quiet beat. Clap, then stop and whisper shh.",
        read(
            scene("A rest is a beat of quiet. The stars hold still, and we whisper shh.",
                sayRhythm("0:1 1:1 2:1 r3:1", listOf("Ta", "ta", "ta", "shh"), 66), Mood.LISTEN),
            scene("On a rest, open your hands wide and keep them still.", sayRhythm("0:1 r1:1 2:1 r3:1", listOf("Ta", "shh", "ta", "shh"), 66), Mood.POINT)),
        play("Clap with my drum, and stop on every shh.", REST_LATE, Game.CLAP).on(StageGame.NightClap),
        play("Now the shh comes sooner: ta, shh, ta, shh.", REST_SOON, Game.CLAP).on(StageGame.NightClap),
        perform("Play it on middle C. Lift your hand on every shh.", REST_LATE.copy(pass = CONCERT_PASS)),
        perform("Now a tune with rests in it. Stop on every shh!",
            Exercise(right = "E4:1 D4:1 C4:1 r:1 E4:1 D4:1 C4:1 r:1", bpm = 72.0, practice = Practice.RIGHT, beatLines = true, pass = CONCERT_PASS, fingers = "3 2 1 3 2 1"))),

    lesson(4, "The metronome game", "Play middle C right on the click, and the stars light up.",
        read(
            scene("This is a metronome. It clicks a steady beat, like a tiny clock.",
                sayRhythm("0:1 1:1 2:1 3:1", listOf("Click", "click", "click", "click"), 80), Mood.LISTEN),
            scene("The first click of each bar is a little higher. That is beat one.", rhythm(4, "0:1 1:1 2:1 3:1", bpm = 80), Mood.POINT),
            scene("Play middle C right on each click, and a star lights up!",
                keys("A3", "G4", listOf(frame("C4", "On the click")), stepMs = 1400, names = KeyNames.NONE), Mood.CHEER)),
        play("Play C on every click. Watch the stars light up!", beat(72.0)).on(CLICK_BEAT),
        play("A little quicker now. Can you light even more stars?", beat(88.0, count = 12)).on(CLICK_BEAT),
        play("Now play on every other click: on one and on three.", beat(84.0, count = 6, every = 2.0)).on(CLICK_BEAT),
        perform("Now walk up and back on the clicks: one key for each click.",
            Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:4", bpm = 76.0, practice = Practice.RIGHT, beatLines = true, pass = CONCERT_PASS,
                fingers = "1 2 3 4 5 4 3 2 1"))),

    lesson(5, "Up or down", "Did the tune go up or down? Then play a note higher or lower.",
        read(
            scene("Some notes go up, like a rocket. Some go down, like a falling star.",
                keys("C4", "C5", listOf(frame("D4"), frame("A4", "Up"), frame("G4", clear = true), frame("C4", "Down", Tint.BLUE)), stepMs = 800, names = KeyNames.NONE),
                Mood.LISTEN),
            scene("I play two notes. Tap up if the second is higher, or down if it is lower.",
                keys("C4", "C5", listOf(frame("E4", "First"), frame("B4", "Up", Tint.BLUE)), stepMs = 900, names = KeyNames.NONE), Mood.POINT)),
        tapStep("Listen to my two notes. Did they go up or down? Tap!", UP_OR_DOWN),
        play("Your turn to choose! I play a note, then you play one higher or lower.", higherLowerExercise(PITCH_ROUNDS))
            .on(StageGame.HigherLower(PITCH_ROUNDS))),

    lesson(6, "Same or different", "Two little tunes: are they the same, or different?",
        read(
            scene("Listen to two little tunes. Are they the same, or different?",
                keys("A3", "A4", walk("C4 D4 E4") + walk("C4 D4 E4", clearFirst = true), stepMs = 600, names = KeyNames.NONE), Mood.LISTEN),
            scene("If just one note changes, the tune is different. Listen closely!",
                keys("A3", "A4", walk("C4 D4 E4") + walk("C4 D4 G4", mapOf(2 to "New"), clearFirst = true), stepMs = 600, names = KeyNames.NONE), Mood.THINK)),
        tapStep("Two tunes. Are they the same or different? Tap!", SAME_OR_NOT),
        play("Now copy my tunes on the keys. Listen for the note that changes!", SKY_COPY).on(StageGame.Copy(Backdrop.NIGHT))),

    lesson(7, "Loud or quiet", "Which note was louder? Then play loud like thunder and quiet like a mouse.",
        read(
            scene("Loud sounds are big, like thunder. Quiet sounds are small, like a mouse.",
                dynamics("C4 C4", listOf(112, 30), mapOf(0 to "Loud", 1 to "Quiet"), stepMs = 1000), Mood.LISTEN),
            scene("I play two notes. Tap the one that was louder.", dynamics("G4 G4", listOf(36, 112), mapOf(1 to "Louder"), stepMs = 1000), Mood.POINT),
            scene("My ears only guess how loud you play, so the last game is just for fun.",
                dynamics("C4 C4 C4 C4", listOf(40, 104, 40, 104), mapOf(0 to "Quiet", 1 to "Loud"), stepMs = 700), Mood.THINK)),
        tapStep("Two notes. Which one was louder? Tap it!", LOUDER),
        play("Your turn. Play middle C your usual way, then loud, then quiet.", LOUD_QUIET).on(StageGame.LoudQuiet(LOUD_QUIET_ROUNDS))),

    lesson(8, "Fast or slow", "Was it a rabbit or a turtle? Then play a tune fast, then slow.",
        read(
            scene("Fast music runs like a rabbit. Slow music walks like a turtle.", lane("C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:2 C4:2 D4:2 E4:2", bpm = 72), Mood.LISTEN),
            scene("I play a tune. Tap the rabbit if it was fast, or the turtle if it was slow.",
                pop("Fast", "Slow", notes = listOf("G4", "C4"), stepMs = 1000), Mood.POINT)),
        tapStep("Listen to my tune. Was it fast or slow? Tap!", FAST_OR_SLOW),
        play("Now you! Play up from C to G your usual way, then fast, then slow.", FIVE_UP).on(StageGame.Tempo(TempoTune.ROUNDS))),

    lesson(9, "Long or short", "Was the note long like a yawn, or short like a hop?",
        read(
            scene("Some notes are long, like a big yawn. Some are short, like a hop.", lane("C4:4 C4:0.5 r:0.5 C4:0.5 r:0.5 C4:0.5 r:1.5", bpm = 72), Mood.LISTEN),
            scene("Listen to my note. Tap long if it held on, or short if it hopped away.", touch("C4 E4 G4", Touch.LEGATO, Touch.STACCATO), Mood.POINT)),
        tapStep("Listen to my note. Was it long or short? Tap!", LONG_OR_SHORT),
        perform("Now play long and short notes with me. Hold the long ones!", LONG_SHORT),
        play("In Twinkle, the star note at the end is long. Hold on to it!", TWINKLE.copy(practice = Practice.RIGHT).bite())),

    lesson(10, "Happy or sad", "Does the chord sound sunny or rainy? Then play the happy chord.",
        read(
            scene("Some chords sound happy, like a sunny day. Some sound sad, like rain.",
                chords(chord("C4 E4 G4", "Happy"), chord("C4 Eb4 G4", "Sad"), low = "A3", high = "C5"), Mood.LISTEN),
            scene("Listen to my chord. Tap the sun if it sounds happy, or the cloud if it sounds sad.",
                chords(chord("G3 B3 D4", "Happy"), chord("A3 C4 E4", "Sad"), low = "F3", high = "G4"), Mood.POINT)),
        tapStep("Listen to my chord. Happy or sad? Tap the sun or the cloud!", HAPPY_OR_SAD),
        play("Now play the happy chord, C, E and G together. Make the sun come out!", HAPPY_CHORD).on(StageGame.SkyChord)),

    lesson(11, "Black key tunes", "Make up your own tune on the black keys, and the band plays along.",
        read(
            scene("The black keys are magic. All of them sound good together!",
                keys("C4", "B5", listOf(frame("C#4 D#4", "Two"), frame("F#4 G#4 A#4", "Three", Tint.BLUE), frame("C#5 D#5"), frame("F#5 G#5 A#5", tint = Tint.BLUE)),
                    stepMs = 800, names = KeyNames.NONE), Mood.CHEER),
            scene("Make up your own tune. Every note you play draws a star in the sky.",
                keys("C4", "B5", walk("F#4 G#4 A#4 C#5 A#4 G#4"), stepMs = 450, names = KeyNames.NONE), Mood.POINT)),
        play("Play any black keys you like. The band plays along!", jamExercise(84.0, 8)).on(StageGame.Jam(bars = 8)),
        play("Now a slow, sleepy tune for the moon. Any black keys again.", jamExercise(60.0, 6)).on(StageGame.Jam(bars = 6)),
        read(
            scene("What a tune! You made it up all by yourself.", pop("Your", "own", "tune!", notes = listOf("F#4", "A#4", "C#5"), stepMs = 700), Mood.CHEER),
            scene("The black keys always sound good together. Try a new tune any time!",
                keys("C4", "B5", walk("C#5 A#4 F#4 G#4 A#4"), stepMs = 450, names = KeyNames.NONE), Mood.POINT))),

    lesson(12, "Night sky concert", "Play Twinkle, Twinkle for the stars, with the band.",
        read(
            scene("Tonight the stars came out to hear you play their very own song!",
                pop("The moon", "The stars", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("Play Twinkle with the band. If you slip, just keep going.", lane(TWINKLE.bite().right, "C3:4 F3:2 C3:2", bpm = 72), Mood.POINT)),
        perform("Play Twinkle for the stars, right to the end!", shelfSong("twinkle")!!.concert.exercise)
            .on(StageGame.Concert(Backdrop.NIGHT, "Twinkle, Twinkle, Little Star", "star")),
        perform("The stars want more! Play the whole song, all the way through.", TWINKLE_ALL)
            .on(StageGame.Concert(Backdrop.NIGHT, "Twinkle all the way", "star")),
        read(
            scene("Goodnight, stars! Thank you for listening.", pop("Good", "night", "stars!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700),
                Mood.CHEER),
            scene("Take a bow, piano player! You can hear the beat, the ups and downs, and the stars.",
                keys("C4", "C5", walk("C4 C4 G4 G4 A4 A4 G4"), stepMs = 450, names = KeyNames.NONE), Mood.CHEER))),
))
