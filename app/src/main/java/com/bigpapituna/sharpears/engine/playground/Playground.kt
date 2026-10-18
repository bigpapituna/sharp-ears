package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.BUNS_X
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.FIND_C
import com.bigpapituna.sharpears.engine.MARY_X
import com.bigpapituna.sharpears.engine.ODE_LINE
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.dynamics
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.frame
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.theory.Hit
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.Scene
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.theory.Touch
import com.bigpapituna.sharpears.engine.touch
import com.bigpapituna.sharpears.engine.walk

/*
 * Pup's Playground (2.1, docs/KIDS-PLAN.md section 4): twelve short lessons written as games, the first unit of the
 * course. Kids start here; grown-ups can open it. Each READ step is one to three scenes the pup says over a picture;
 * the games that the embedded player cannot show ([Game]) draw their own stage in ui/playground.
 *
 * Copy: short, warm, second person, sentence case, no em dashes, no percentages, nothing called wrong; pictures carry
 * what letters would (the skin team swaps note names for colours and animals on the keys).
 */

/** The unit's id; Profile.firstUnitId is this for kids. */
const val PLAYGROUND_ID = "playground"

/**
 * A Playground step that draws its own stage (ui/playground's registry) instead of the embedded player. Each still has
 * an ordinary [Step.kind] and exercise, so a screen that does not know the game can play it in the player.
 */
enum class Game {
    /** The pup flies up on high keys and lands on low ones: any-of groups over half the piano, heard by [PitchHeight]. */
    HIGH_LOW,
    /** A big keyboard: the dog houses (two black keys), the fences (three) or the Cs light up as they are found. */
    KEY_HUNT,
    /** Clap with the pup's drum ([ClapCounter]); the exercise's beats and tempo set the drum. */
    CLAP,
    /** Keep the pup asleep with soft notes, wake him with loud ones ([SoftLoudGame]); a toy, it never fails. */
    SOFT_LOUD,
    /** Pick one of [CONCERT_SONGS] and play it for everyone, in time; passes at [CONCERT_PASS]. */
    CONCERT,
}

/** Where a lesson sits in the pup's world (the map and the album pages). */
enum class Place(val title: String) { GARDEN("The garden"), POND("The pond"), CONCERT_HALL("The concert hall") }

/**
 * The Playground's pass mark for playing in time (lesson 7's drum and lesson 12's concert): Perform accuracy in percent.
 * It stands for "two stars": if the skin team's two-star rule lands elsewhere, this follows it.
 */
const val CONCERT_PASS = 60

// ---- Ranges for the any-of exercises ------------------------------------------------------------------------------

/** Every key to the right of middle C, the app's example first. */
private fun high(demo: String) = "$demo|C#4..C8"
/** Every key to the left of middle C. */
private fun low(demo: String) = "$demo|A0..B3"
/** The two-black-key groups (dog houses) and three-black-key groups (fences) in the middle three octaves, the given one first. */
internal const val DOG_HOUSES = "C#4|D#4|C#3|D#3|C#5|D#5"
internal const val FENCES = "F#4|G#4|A#4|F#3|G#3|A#3|F#5|G#5|A#5"
/** Any C a child can reach from the bench. */
internal const val ANY_C = "C4|C2|C3|C5|C6"

private fun rounds(vararg tokens: String, beats: Int = 2) = tokens.joinToString(" ") { "$it:$beats" }

// ---- Builders -------------------------------------------------------------------------------------------------------

private fun read(vararg scenes: Scene) = Step(StepKind.READ, scenes.joinToString(" ") { it.caption }, scenes = scenes.toList())
private fun listen(text: String, ex: Exercise) = Step(StepKind.LISTEN, text, ex)
private fun play(text: String, ex: Exercise, game: Game? = null) = Step(StepKind.PLAY, text, ex, game = game)
private fun memory(text: String, ex: Exercise) = Step(StepKind.FROM_MEMORY, text, ex)
private fun perform(text: String, ex: Exercise, game: Game? = null) = Step(StepKind.PERFORM, text, ex, game = game)
private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("playground-$n", title, summary, steps.toList())

// ---- The tunes --------------------------------------------------------------------------------------------------------

/** Lesson 4: up three steps and back down. */
private val STEPS = Exercise(right = "C4:1 D4:1 E4:2 E4:1 D4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT)
/** Lesson 10: up to G and back, one finger per key; the bite is the climb. */
private val UP_TO_G = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:4 F4:1 E4:1 D4:1 C4:4", bpm = 76.0, practice = Practice.RIGHT, phraseEnd = 8.0)
/** Lesson 7: middle C on every beat, two bars. */
private val DRUM = Exercise(right = List(8) { "C4:1" }.joinToString(" "), bpm = 80.0, practice = Practice.RIGHT, beatLines = true)

/** A song for the concert: its title, the picture on its card, and the exercise (right hand, the band on the left). */
data class ConcertSong(val title: String, val picture: String, val exercise: Exercise)

/** Lesson 12 offers these three, all learned in the Playground, a little slower than in the course, passing at [CONCERT_PASS]. */
val CONCERT_SONGS = listOf(
    ConcertSong("Hot Cross Buns", "buns", BUNS_X.copy(bpm = 72.0, practice = Practice.RIGHT, pass = CONCERT_PASS, phraseEnd = null)),
    ConcertSong("Mary Had a Little Lamb", "lamb", MARY_X.copy(bpm = 80.0, practice = Practice.RIGHT, pass = CONCERT_PASS, phraseEnd = null)),
    ConcertSong("Ode to Joy", "five", ODE_LINE.copy(left = "C3:4 G2:4 C3:4 G2:4", bpm = 80.0, practice = Practice.RIGHT, pass = CONCERT_PASS, phraseEnd = null)),
)

/** Lesson 7's two pictures: the drum's beat, and a clap on each one. */
private fun drumBeat(words: List<String>, bpm: Int = 80) =
    RhythmVisual(4, 4, bpm, (0 until 4).map { Hit(it.toFloat(), 1f) }, counts = words, loops = 2, pitch = 36)

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val PLAYGROUND: CourseLevel = CourseLevel(PLAYGROUND_ID, "Pup's Playground", "Twelve little games: high and low, the black keys, your first songs, and a concert.", listOf(
    lesson(1, "High and low", "Make the pup fly with high keys, and land him with low ones.",
        read(
            scene("Keys on the left sound low, like a big bear. Keys on the right sound high, like a little bird.",
                keys("C3", "C6", listOf(frame("C3", "Low"), frame("G3", "Low"), frame("C4"), frame("G4"), frame("C5", "High", Tint.BLUE),
                    frame("C6", "High", Tint.BLUE)), stepMs = 650, names = KeyNames.NONE), Mood.LISTEN),
            scene("Play a high key and I fly up. Play a low key and I land.",
                keys("C3", "C6", listOf(frame("C6", "Fly up", Tint.BLUE), frame("C3", "Land", clear = true), frame("A5", "Fly up", Tint.BLUE, clear = true),
                    frame("E3", "Land", clear = true)), stepMs = 1000, names = KeyNames.NONE), Mood.POINT)),
        play("Fly me up! Play any key on the high side, three times.",
            Exercise(right = rounds(high("C6"), high("G6"), high("C7")), bpm = 60.0, practice = Practice.RIGHT), Game.HIGH_LOW),
        play("Now bring me down to the grass. Play any key on the low side, three times.",
            Exercise(left = rounds(low("C3"), low("G2"), low("C2")), bpm = 60.0, practice = Practice.LEFT), Game.HIGH_LOW),
        play("Now up and down. A high key to fly, a low key to land.",
            Exercise(right = rounds(high("E6"), "r", high("A6"), "r", high("C7"), "r"), left = rounds("r", low("C3"), "r", low("G2"), "r", low("C2")),
                bpm = 60.0), Game.HIGH_LOW)),

    lesson(2, "Two black, three black", "Find the dog house and the fence in the black keys.",
        read(
            scene("Look at the black keys. They sit in little groups of two and three.",
                keys("C3", "B5", listOf(frame("C#3 D#3", "Two"), frame("F#3 G#3 A#3", "Three", Tint.BLUE), frame("C#4 D#4", "Two"),
                    frame("F#4 G#4 A#4", "Three", Tint.BLUE)), stepMs = 950, names = KeyNames.NONE), Mood.POINT),
            scene("Two black keys make my dog house.",
                keys("C3", "B5", listOf(frame("C#3 D#3", "Dog house"), frame("C#4 D#4", "Dog house"), frame("C#5 D#5", "Dog house")),
                    stepMs = 900, names = KeyNames.NONE)),
            scene("Three black keys make my garden fence.",
                keys("C3", "B5", listOf(frame("F#3 G#3 A#3", "Fence", Tint.BLUE), frame("F#4 G#4 A#4", "Fence", Tint.BLUE),
                    frame("F#5 G#5 A#5", "Fence", Tint.BLUE)), stepMs = 900, names = KeyNames.NONE), Mood.CHEER)),
        play("Find my dog house! Play one of its two black keys.",
            Exercise(right = rounds(DOG_HOUSES, DOG_HOUSES, DOG_HOUSES), bpm = 60.0, practice = Practice.RIGHT), Game.KEY_HUNT),
        play("Now find the fence. Play one of its three black keys.",
            Exercise(right = rounds(FENCES, FENCES, FENCES), bpm = 60.0, practice = Practice.RIGHT), Game.KEY_HUNT),
        play("Dog house or fence? Find the one I point at.",
            Exercise(right = rounds(DOG_HOUSES, FENCES, FENCES, DOG_HOUSES, FENCES, DOG_HOUSES), bpm = 60.0, practice = Practice.RIGHT), Game.KEY_HUNT)),

    lesson(3, "Where C lives", "C lives just to the left of the dog house.",
        read(
            scene("Find a dog house. The white key just to its left is C.",
                keys("C3", "B5", listOf(frame("C#4 D#4", "Dog house", Tint.BLUE), frame("C4", "C")), stepMs = 1300, names = KeyNames.NONE), Mood.POINT),
            scene("Every dog house has a C beside it, from low to high.",
                keys("C2", "C6", walk("C2 C3 C4 C5 C6", mapOf(0 to "C", 4 to "C")), stepMs = 700, finale = "C3 C4 C5"), Mood.LISTEN),
            scene("The C nearest the middle is middle C. It is your home key.",
                keys("C3", "B5", listOf(frame("C4", "Middle C")), stepMs = 1500, names = KeyNames.NONE), Mood.CHEER)),
        play("Find a C. Any C will do!",
            Exercise(right = rounds(ANY_C, ANY_C, ANY_C, ANY_C), bpm = 60.0, practice = Practice.RIGHT), Game.KEY_HUNT),
        play("Now middle C, your home key, three times.", Exercise(right = "C4:1 r:1 C4:1 r:1 C4:2", bpm = 66.0, practice = Practice.RIGHT)),
        listen("Listen to every C, from low to high.", FIND_C),
        play("Now play every C, from low to high. Your left hand takes the low ones.", FIND_C)),

    lesson(4, "Pup's steps", "Walk up and down three white keys with the pup.",
        read(
            scene("Here are three steps going up: C, D and E.", keys("A3", "G4", walk("C4 D4 E4"), stepMs = 750), Mood.POINT),
            scene("Your thumb takes C, the next finger D, and the middle finger E.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4", "1 2 3"), stepMs = 750)),
            scene("Up the steps and back down again.", lane(STEPS.right, bpm = 72), Mood.LISTEN)),
        listen("Listen to me walk up and down.", STEPS),
        play("Your turn. Walk up the steps and back down.", STEPS),
        play("Now start at the top: down the steps and back up.", Exercise(right = "E4:1 D4:1 C4:2 C4:1 D4:1 E4:2", bpm = 72.0, practice = Practice.RIGHT)),
        memory("Once more, up and down, all by yourself. The names are hidden now.", STEPS)),

    lesson(5, "Hot Cross Buns", "Your three steps make a whole song.",
        read(
            scene("This song walks down your three steps.", lane(BUNS_X.bite().right, bpm = 80), Mood.LISTEN),
            scene("Sing it while you play: hot cross buns!", pop("Hot", "cross", "buns", notes = listOf("E4", "D4", "C4"), stepMs = 750), Mood.PLAY)),
        listen("Listen to the start of the song. The band plays along.", BUNS_X.copy(practice = Practice.RIGHT).bite()),
        play("Your turn: hot cross buns, hot cross buns.", BUNS_X.copy(practice = Practice.RIGHT).bite()),
        memory("Once more, all by yourself. The names are hidden now.", BUNS_X.copy(practice = Practice.RIGHT).bite()),
        play("Now the whole song. In the middle the notes go quick: one a penny, two a penny.", BUNS_X.copy(practice = Practice.RIGHT))),

    lesson(6, "Copy the pup", "The pup plays a little tune. Play it back by ear.",
        read(
            scene("I play a few notes. You listen, then play them back.",
                keys("A3", "G4", listOf(frame("E4", "Me"), frame("C4", "Me"), frame("E4", "You", Tint.BLUE, clear = true), frame("C4", "You", Tint.BLUE)),
                    stepMs = 850, names = KeyNames.NONE), Mood.LISTEN),
            scene("We start with two notes, then try three.",
                keys("A3", "G4", walk("E4 C4") + walk("C4 D4 E4", clearFirst = true), stepMs = 650, names = KeyNames.NONE), Mood.POINT),
            scene("I play each tune twice. If it slips away, that is fine: listen again and have another go.",
                keys("A3", "G4", walk("E4 C4") + walk("E4 C4", clearFirst = true), stepMs = 650, names = KeyNames.NONE), Mood.THINK)),
        listen("My turn. Listen to two notes, twice.", Exercise(right = "E4:1 C4:2 r:1 E4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT)),
        memory("Your turn! Play my two notes back.", Exercise(right = "E4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT)),
        listen("My turn again. Three notes this time.", Exercise(right = "C4:1 D4:1 E4:2 r:1 C4:1 D4:1 E4:2", bpm = 72.0, practice = Practice.RIGHT)),
        memory("Your turn! Play all three back.", Exercise(right = "C4:1 D4:1 E4:2", bpm = 72.0, practice = Practice.RIGHT)),
        listen("Last one. Three notes going down. Do you know this one?", Exercise(right = "E4:1 D4:1 C4:2 r:1 E4:1 D4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT)),
        memory("Your turn! Play them back.", Exercise(right = "E4:1 D4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT))),

    lesson(7, "The steady drum", "Clap with the pup's drum, then play C on every beat.",
        read(
            scene("Music has a heartbeat. I keep it on my drum: boom, boom, boom, boom.", drumBeat(listOf("Boom", "boom", "boom", "boom")), Mood.LISTEN),
            scene("Clap once on every boom. Not faster, not slower: just with me.", drumBeat(listOf("Clap", "clap", "clap", "clap")), Mood.PLAY)),
        play("Clap with my drum! One clap on every boom.", DRUM, Game.CLAP),
        play("Now my drum goes slowly. Wait for each boom, then clap.", DRUM.copy(bpm = 60.0), Game.CLAP),
        perform("Now play middle C on every boom. The clicks count you in, and the notes keep moving.", DRUM.copy(pass = CONCERT_PASS))),

    lesson(8, "Mary Had a Little Lamb", "A lamb comes to the pond, on four keys.",
        read(
            scene("Mary's lamb walks on four keys: C, D, E and G.", keys("A3", "A4", walk("C4 D4 E4 G4"), stepMs = 700), Mood.POINT),
            scene("Three E notes come in a row. Lift your finger a little between them.",
                touch("E4 E4 E4", Touch.STACCATO, labels = listOf("Lift between"), stepMs = 520))),
        listen("Listen to the start of the song.", MARY_X.copy(practice = Practice.RIGHT).bite()),
        play("Your turn: Mary had a little lamb.", MARY_X.copy(practice = Practice.RIGHT).bite()),
        memory("Once more, all by yourself. The names are hidden now.", MARY_X.copy(practice = Practice.RIGHT).bite()),
        play("Now the whole line: little lamb, little lamb.", MARY_X.copy(practice = Practice.RIGHT))),

    lesson(9, "Left paw", "Your left hand joins in on a low C.",
        read(
            scene("Your left hand is a paw too. Its little finger plays the C below the middle.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", steps = fingered("C3 C3", "5 5", right = false, labels = mapOf(0 to "Little finger")), stepMs = 800),
                Mood.POINT),
            scene("Play it on every boom, four times.", lane("", "C3:1 C3:1 C3:1 C3:1", bpm = 72), Mood.LISTEN),
            scene("Then hold it down while your right hand plays on top.", lane("E4:1 D4:1 C4:2", "C3:4", bpm = 72))),
        play("Low C with your little finger, four times.", Exercise(left = "C3:1 C3:1 C3:1 C3:1", bpm = 72.0, practice = Practice.LEFT)),
        play("Left paw, right paw: low C, then middle C, back and forth.",
            Exercise(right = "r:1 C4:1 r:1 C4:1 r:1 C4:1 r:1 C4:1", left = "C3:1 r:1 C3:1 r:1 C3:1 r:1 C3:1 r:1", bpm = 72.0)),
        play("Hold the low C while your right hand plays hot cross buns.",
            Exercise(right = "E4:1 D4:1 C4:2 E4:1 D4:1 C4:2", left = "C3:4 C3:4", bpm = 72.0))),

    lesson(10, "Up to G", "Five fingers, five keys, all the way up to G.",
        read(
            scene("Five fingers, five keys: C, D, E, F and G.", fingers("C4 D4 E4 F4 G4", ""), Mood.POINT),
            scene("Climb up to G with one finger on each key, then come back down.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4 F4 G4 F4 E4 D4 C4", "1 2 3 4 5 4 3 2 1"), stepMs = 520), Mood.LISTEN)),
        listen("Listen to me climb to G and back.", UP_TO_G),
        play("Your turn. One finger for each key.", UP_TO_G),
        memory("Once more, all by yourself. The names are hidden now.", UP_TO_G),
        play("Now a real tune that uses all five fingers.", ODE_LINE.bite())),

    lesson(11, "Soft and loud", "Soft notes keep the pup asleep. A loud one wakes him up.",
        read(
            scene("Time for my nap. Soft notes sound like a lullaby.",
                dynamics("C4 E4 G4 E4 C4", listOf(36, 34, 32, 30, 28), mapOf(0 to "Soft"), stepMs = 800), Mood.LISTEN),
            scene("A loud note goes boom and wakes me up!",
                dynamics("C4 C4 C4", listOf(36, 34, 118), mapOf(0 to "Soft", 2 to "Loud"), stepMs = 950), Mood.CHEER),
            scene("I can only guess how loud you play. My ears are not perfect at this, so it is just for fun.",
                dynamics("C4 C4 C4 C4", listOf(40, 104, 40, 104), mapOf(0 to "Soft", 1 to "Loud"), stepMs = 700), Mood.THINK)),
        play("Play middle C. Soft keeps me asleep, loud wakes me up!", SoftLoud.exercise(), Game.SOFT_LOUD),
        read(scene("Shh, that was lovely. Here is my lullaby. Goodnight!", dynamics("G4 E4 D4 C4", listOf(34, 30, 26, 22), mapOf(0 to "Soft"), stepMs = 900), Mood.LISTEN))),

    lesson(12, "Pup's concert", "Play a song for everyone, with the band.",
        read(
            scene("Tonight is my concert, and you are the star!", pop("The lights", "The band", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800),
                Mood.CHEER),
            scene("Pick a song. The notes keep moving, so if you slip, just keep going.", lane(BUNS_X.bite().right, "C3:4 C3:4", bpm = 72), Mood.POINT)),
        perform("Play your song for everyone, right to the end!", CONCERT_SONGS.first().exercise, Game.CONCERT),
        perform("Everyone wants more! Pick a song for an encore.", CONCERT_SONGS[1].exercise, Game.CONCERT),
        read(scene("Listen to them cheer. Take a bow, piano player!", pop("Bravo", "bravo", "bravo!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700),
            Mood.CHEER))),
))

/** Where each Playground lesson sits in the pup's world: four in the garden, four at the pond, four in the concert hall. */
val PLAYGROUND_PLACES: Map<String, Place> = PLAYGROUND.lessons.mapIndexed { i, l -> l.id to Place.entries[i / 4] }.toMap()

/** The plan's short path for big kids (section 4): these five, then Basics. */
val BIG_KID_LESSONS: List<String> = listOf(2, 3, 6, 7, 12).map { "playground-$it" }
