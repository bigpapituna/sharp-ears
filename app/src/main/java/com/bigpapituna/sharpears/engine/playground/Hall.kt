package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.HALVES_PATTERN
import com.bigpapituna.sharpears.engine.LIGHTLY_X
import com.bigpapituna.sharpears.engine.ODE_LINE
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.YANKEE_X
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.chordPress
import com.bigpapituna.sharpears.engine.dynamics
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.fiveFingerExercise
import com.bigpapituna.sharpears.engine.frame
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.together
import com.bigpapituna.sharpears.engine.walk

/*
 * Pup's Playground, the concert hall (2.2): the third page and the last before the big kids' road (Basics). Reading
 * letters on the keys and playing with the band: letter hunts, three more songs, both hands, a tune soft and loud, the
 * band at tempo, a longer copy game, hands together, and a recital of three songs the kid picks. Same model as the
 * garden and the pond (Playground.kt, Pond.kt).
 */

/** The concert hall's course level; its page is [PLAYGROUND_PAGES]' third. */
const val HALL_ID = "playground-hall"

private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("hall-$n", title, summary, steps.toList())

/** Any key of pitch class [letter] a child can reach, octaves 2 to 6, the one by middle C first (the app plays it). */
internal fun anyLetter(letter: Char) = "${letter}4|${letter}2|${letter}3|${letter}5|${letter}6"

// ---- The tunes ------------------------------------------------------------------------------------------------------

/** Ode to Joy's first line with the book's fingers in the C position (thumb on C), as "Your first melody" shows them. */
private val ODE = ODE_LINE
/** Lightly Row a little slower, cut after "lightly row, lightly row". */
private val LIGHTLY = LIGHTLY_X.copy(practice = Practice.RIGHT, bpm = 76.0, phraseEnd = 8.0)
/** Yankee Doodle a little slower, cut after "went to town". */
private val YANKEE = YANKEE_X.copy(practice = Practice.RIGHT, bpm = 84.0, phraseEnd = 8.0)
/** Five fingers in C (the Technique unit), slowly, both hands: up to G and home to C once. */
private val FIVE = fiveFingerExercise(listOf("C", "D", "E", "F", "G")).copy(bpm = 60.0, phraseEnd = 9.0)
/** Ode to Joy with the band at 80 (the garden's concert song), two stars to pass. */
private val ODE_BAND = CONCERT_SONGS[2].exercise
/** Ode to Joy's first line over the simplest left hand: low C under bar 1, G under bar 2, and again. */
private val ODE_TOGETHER = ODE.copy(left = "C3:4 G3:4 C3:4 G3:4", practice = Practice.BOTH, bpm = 72.0)
/** Ode to Joy's first phrase once for each round of [TuneLoudness.ROUNDS] (the usual way, softly, loudly, softly), a breath between. */
private val ODE_ROUNDS = ODE_LINE.bite().let { p -> Exercise(right = List(TuneLoudness.ROUNDS.size) { p.right }.joinToString(" r:2 "), bpm = 80.0,
    practice = Practice.RIGHT, fingers = List(TuneLoudness.ROUNDS.size) { p.fingers }.joinToString(" ")) }
/** Ta-a ta-a (the Rhythm unit's halves, its first bar), then ta ta ta and a rest. */
private val TAA_REST = Exercise(right = Bites.cut(Exercise(right = HALVES_PATTERN), 4.0).right + " C4:1 C4:1 C4:1 r:1", bpm = 66.0, practice = Practice.RIGHT,
    beatLines = true)

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val HALL: CourseLevel = CourseLevel(HALL_ID, "Pup's Concert Hall", "Twelve lessons on the big stage: letters on the keys, playing with the band, and your own recital.", listOf(
    lesson(1, "Letters on the keys", "C, D, E, F and G each have a name, a colour and an animal.",
        read(
            scene("Welcome to the concert hall! Every white key has a letter name.", keys("B3", "A4", walk("C4 D4 E4 F4 G4"), stepMs = 650), Mood.CHEER),
            scene("C is a red cat and D an orange dog. E is a yellow elephant, F a green frog, G a teal goat.",
                keys("B3", "A4", walk("C4 D4 E4 F4 G4", mapOf(0 to "Cat", 1 to "Dog", 2 to "Elephant", 3 to "Frog", 4 to "Goat")), stepMs = 900), Mood.POINT),
            scene("Each one comes back again and again, all the way up the piano.",
                keys("C3", "B5", listOf(frame("D3", "D"), frame("D4", "D"), frame("D5", "D")), stepMs = 900), Mood.LISTEN)),
        play("Find every D. D is the orange dog, inside the dog house.", Exercise(right = rounds(anyLetter('D'), anyLetter('D'), anyLetter('D')), bpm = 60.0,
            practice = Practice.RIGHT), Game.KEY_HUNT),
        play("Now E and F. E sits right of the dog house, and F sits left of the fence.",
            Exercise(right = rounds(anyLetter('E'), anyLetter('F'), anyLetter('E'), anyLetter('F')), bpm = 60.0, practice = Practice.RIGHT), Game.KEY_HUNT),
        play("Last game: find the one I call. Listen for its name!",
            Exercise(right = rounds(anyLetter('G'), anyLetter('C'), anyLetter('D'), anyLetter('F'), anyLetter('E')), bpm = 60.0, practice = Practice.RIGHT),
            Game.KEY_HUNT)),

    lesson(2, "Ode to Joy", "A famous tune, with its letters on the notes.",
        read(
            scene("Here is a tune that people sing all over the world.", keys("B3", "A4", walk("E4 E4 F4 G4 G4 F4 E4 D4"), stepMs = 480), Mood.LISTEN),
            scene("It climbs E, F, G, then walks back down to D.", keys("B3", "A4", walk("E4 F4 G4 F4 E4 D4", mapOf(2 to "Top", 5 to "Down")), stepMs = 620), Mood.POINT),
            scene("Read the letter on each note as it falls, and say it as you play.", lane(ODE_LINE.bite().right, bpm = 80), Mood.PLAY)),
        listen("Listen to the start of Ode to Joy.", ODE.bite()),
        play("Your turn. Say each letter as you play it.", ODE.bite()),
        memory("Once more, all by yourself. The letters are hidden now.", ODE.bite()),
        play("Now the whole line. It goes home to C, then rests on D.", ODE.copy(phraseEnd = null))),

    lesson(3, "Lightly Row", "Row lightly across the hall, down and back up.",
        read(
            scene("Lightly Row starts high on G and steps down: G, E, E.", keys("B3", "A4", walk("G4 E4 E4 F4 D4 D4"), stepMs = 520), Mood.LISTEN),
            scene("Then it climbs all five keys: C, D, E, F, G.", keys("B3", "A4", walk("C4 D4 E4 F4 G4", mapOf(4 to "Top")), stepMs = 520), Mood.POINT)),
        listen("Listen to the start of the song.", LIGHTLY.bite()),
        play("Your turn: lightly row, lightly row.", LIGHTLY.bite()),
        memory("Once more, all by yourself. The letters are hidden now.", LIGHTLY.bite()),
        play("Now the whole line, all the way up to G.", LIGHTLY.copy(phraseEnd = null))),

    lesson(4, "Yankee Doodle", "A march with a reach down to low G.",
        read(
            scene("Yankee Doodle is a march! Step, step, step, step.", pop("Yan", "kee", "Doo", "dle", notes = listOf("C4", "C4", "D4", "E4"), stepMs = 600), Mood.CHEER),
            scene("Your thumb reaches down to the G below middle C, then comes back up.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("E4 D4 G3 C4", "3 2 1 1", labels = mapOf(2 to "Reach down", 3 to "Back")), stepMs = 620),
                Mood.POINT)),
        listen("Listen to the start of the march.", YANKEE.bite()),
        play("Your turn: Yankee Doodle went to town.", YANKEE.bite()),
        memory("Once more, all by yourself. The letters are hidden now.", YANKEE.bite()),
        play("Now the whole line. It ends on the B just under middle C.", YANKEE.copy(phraseEnd = null))),

    lesson(5, "Both hands, five fingers", "Both hands walk up to G and home again, slowly.",
        read(
            scene("Both hands sit on C: your right thumb on middle C, your left little finger on the C below.",
                fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3"), Mood.POINT),
            scene("They play the same notes together, one finger each, up to G and back.",
                fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3", intro = false,
                    steps = together("C4 D4 E4 F4 G4 F4 E4 D4 C4", "1 2 3 4 5 4 3 2 1", "C3 D3 E3 F3 G3 F3 E3 D3 C3", "5 4 3 2 1 2 3 4 5"), stepMs = 560),
                Mood.LISTEN),
            scene("Go slowly, so both hands land at the same time.", lane(FIVE.bite().right, FIVE.bite().left, bpm = 60), Mood.THINK)),
        listen("Listen to both hands walk up and back down.", FIVE.bite()),
        play("Your right hand first. The app plays the left hand.", FIVE.bite().copy(practice = Practice.RIGHT)),
        play("Now both hands. The notes wait until both have played.", FIVE.bite()),
        memory("Both hands once more, all by yourself.", FIVE.bite())),

    lesson(6, "Soft song, loud song", "Play Ode to Joy softly, then loudly, and watch the pup.",
        read(
            scene("Remember my nap? Now a whole tune can be soft or loud.",
                dynamics("E4 E4 F4 G4 G4 F4 E4 D4", listOf(36, 34, 34, 32, 32, 30, 30, 28), mapOf(0 to "Soft"), stepMs = 420), Mood.LISTEN),
            scene("For soft, use gentle fingers. For loud, let your arm drop into the keys.",
                dynamics("E4 E4 F4 G4 E4 E4 F4 G4", listOf(34, 34, 34, 34, 112, 112, 112, 112), mapOf(0 to "Soft", 4 to "Loud"), stepMs = 420), Mood.POINT),
            scene("My ears only guess how loud you play, so this is just for fun.",
                dynamics("C4 C4 C4 C4", listOf(40, 104, 40, 104), mapOf(0 to "Soft", 1 to "Loud"), stepMs = 700), Mood.THINK)),
        play("Play the start of Ode to Joy four times: your usual way, softly, loudly, then softly again.", ODE_ROUNDS, Game.SOFT_LOUD_TUNE),
        read(scene("Shh, that was lovely. A soft song puts me to sleep. Goodnight!",
            dynamics("G4 F4 E4 D4 C4", listOf(34, 30, 28, 24, 22), mapOf(0 to "Soft"), stepMs = 800), Mood.LISTEN))),

    lesson(7, "With the band", "Ode to Joy in time with the band.",
        read(
            scene("Tonight the band plays with you. They keep going, so you keep going too.", lane(ODE_BAND.right, ODE_BAND.left, bpm = 80), Mood.LISTEN),
            scene("If you slip, skip that note and catch the next one.", pop("Slip", "Skip", "Catch up!", notes = listOf("E4", "F4", "G4"), stepMs = 800), Mood.POINT)),
        listen("Listen to Ode to Joy with the band.", ODE_BAND),
        play("Practise it once with the notes waiting for you.", ODE_BAND),
        perform("Now in time with the band. Play right to the end!", ODE_BAND)),

    lesson(8, "Ta-a and the rest", "A long note, and a beat of quiet.",
        read(
            scene("Some notes stay longer. Ta-a lasts two beats.", sayRhythm("0:2 2:2", listOf("Ta", "a", "Ta", "a"), 66), Mood.LISTEN),
            scene("A rest is a beat of quiet. Open your hands and whisper shh.", sayRhythm("0:1 1:1 2:1 r3:1", listOf("Ta", "ta", "ta", "shh"), 66), Mood.POINT),
            scene("Say it, then clap it, then play it.", pop("Say it", "Clap it", "Play it!", notes = listOf("C4", "C4+E4", "C4+E4+G4"), stepMs = 800), Mood.CHEER)),
        listen("Listen and say it with me: ta-a, ta-a, then ta, ta, ta, shh.", TAA_REST),
        play("Clap it with my drum: ta-a, ta-a, ta, ta, ta, shh.", TAA_REST, Game.CLAP),
        perform("Now play it on middle C. Hold each ta-a for two beats.", TAA_REST.copy(pass = CONCERT_PASS)),
        perform("The same rhythm as a little tune, down the steps and home. Keep saying it!",
            TAA_REST.copy(right = "E4:2 D4:2 C4:1 D4:1 E4:1 r:1 C4:4", pass = CONCERT_PASS))),

    lesson(9, "Five notes to copy", "The pup plays five notes on stage. Play them back.",
        read(
            scene("On stage, I play five notes. You listen, then play them back.",
                keys("A3", "A4", walk("C4 E4 G4 E4 C4"), stepMs = 560, names = KeyNames.NONE), Mood.LISTEN),
            scene("Watch the lights. Each one shines with a note.",
                keys("A3", "A4", listOf(frame("G4", "Light"), frame("F4", "Light"), frame("E4", "Light"), frame("D4", "Light"), frame("C4", "Light")),
                    stepMs = 600, names = KeyNames.NONE), Mood.POINT),
            scene("If a note slips away, that is fine. I play the tune again, and you go on from there.",
                keys("A3", "A4", walk("G4 F4 E4 D4 C4"), stepMs = 520, names = KeyNames.NONE), Mood.THINK)),
        play("Listen to my five notes, then play them back.",
            Exercise(right = "C4:1 E4:1 G4:1 E4:1 C4:1 r:2 G4:1 F4:1 E4:1 D4:1 C4:1", bpm = 80.0, practice = Practice.RIGHT), Game.ECHO),
        play("Now two tunes you know! Copy all five notes of each.",
            Exercise(right = "E4:1 E4:1 F4:1 G4:1 G4:1 r:2 E4:1 D4:1 C4:1 D4:1 E4:1", bpm = 80.0, practice = Practice.RIGHT,
                fingers = "3 3 4 5 5 3 2 1 2 3"), Game.ECHO)),

    lesson(10, "Hands together", "Ode to Joy on top, a low C and G underneath.",
        read(
            scene("Your left hand joins Ode to Joy with just two notes: C and G.",
                fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3", intro = false,
                    steps = listOf(chordPress("5:C3", right = false, label = "C"), chordPress("1:G3", right = false, label = "G")), stepMs = 1000), Mood.POINT),
            scene("Low C holds under the first bar, and G under the second.", lane(ODE_TOGETHER.bite().right, ODE_TOGETHER.bite().left, bpm = 72), Mood.LISTEN)),
        listen("Listen to both hands together.", ODE_TOGETHER.bite()),
        play("Your left hand alone first: C, then G.", ODE_TOGETHER.bite().copy(practice = Practice.LEFT)),
        play("Now both hands. The notes wait until both have played.", ODE_TOGETHER.bite()),
        memory("Both hands once more, all by yourself.", ODE_TOGETHER.bite())),

    lesson(11, "Pick your three", "Choose three songs for your recital and warm up on each.",
        read(
            scene("Next comes your recital, a concert all of your own!",
                pop("Your songs", "Your band", "Your show!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("Pick three songs from your shelf. Then play the start of each one to warm up.",
                keys("A3", "A4", listOf(frame("C4 E4 G4", "Three songs")), stepMs = 1500, names = KeyNames.NONE), Mood.POINT)),
        play("Pick three songs, then play the start of each one.", Recital.songs(null).first().start, Game.SET_LIST),
        read(scene("What a set list! Next time, you play them for everyone.", pop("Ready", "steady", "show!", notes = listOf("C4", "E4", "G4"), stepMs = 700), Mood.CHEER))),

    lesson(12, "The recital", "Your three songs for everyone, with the band.",
        read(
            scene("The hall is full! All your animal friends came to hear you.",
                pop("The lights", "The band", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("Play your three songs, one after another. If you slip, just keep going.", lane(ODE_BAND.right, ODE_BAND.left, bpm = 80), Mood.POINT)),
        perform("Your first song. Play it right to the end!", Recital.songs(null)[0].concert.exercise, Game.CONCERT),
        perform("Your second song. The audience is listening!", Recital.songs(null)[1].concert.exercise, Game.CONCERT),
        perform("Your last song. Make it sparkle!", Recital.songs(null)[2].concert.exercise, Game.CONCERT),
        read(scene("Bravo! You played a whole recital. Take a bow, piano player!",
            pop("Bravo", "bravo", "bravo!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700), Mood.CHEER))),
))
