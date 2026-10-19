package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.BUNS_X
import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.chordPress
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.frame
import com.bigpapituna.sharpears.engine.hand
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.theory.HandMove
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.walk

/*
 * Pup's Playground, the treehouse (2.3 part B): twelve lessons on fingers that move. Which finger is which in both
 * hands, walking, the thumb tucking under, the weak fourth and fifth fingers, skips, the hand moving to G and to F (and
 * its first black key), the same tune in the other hand, copying the pup by fingers, a tap game for which finger the
 * painted hand used, and a concert. The same model and rules as the other pages (Playground.kt, Pond.kt, Hall.kt).
 *
 * Every exercise writes out its fingers, so the falling notes, the ghost hand, the hint hand, the scenes' painted hand
 * and the words all say the same finger (FingersAgreeTest, TreeSkyFingersTest). The app hears keys, never fingers:
 * the pup asks for a finger and says which he used, and never says he saw yours.
 */

/** The treehouse's course level: its page (engine/playground/Pages.kt) is [Place.TREEHOUSE], after the workshop. */
const val TREEHOUSE_ID = "playground-treehouse"

private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("treehouse-$n", title, summary, steps.toList())

/** The treehouse's copy games: the painted hand shows each phrase with its fingers. */
private val COPY = StageGame.Copy(Place.TREEHOUSE)
/** The same, by ear after the first round: the pup's words and the sound only. */
private val COPY_BY_EAR = StageGame.Copy(Place.TREEHOUSE, byEar = true)

private fun right(notes: String, fingers: String, bpm: Double = 72.0) = Exercise(right = notes, bpm = bpm, practice = Practice.RIGHT, fingers = fingers)
private fun left(notes: String, fingers: String, bpm: Double = 72.0) = Exercise(left = notes, bpm = bpm, practice = Practice.LEFT, leftFingers = fingers)

// ---- The tunes ------------------------------------------------------------------------------------------------------

/** Up three keys and back with one, two, three (the garden's "Pup's steps", fingers written out). */
private val WALK = right("C4:1 D4:1 E4:2 E4:1 D4:1 C4:2", "1 2 3 3 2 1")
/** The left hand's walk: little finger on C (as the pond's left paw). */
internal val WALK_LEFT = left("C3:1 D3:1 E3:2 E3:1 D3:1 C3:2", "5 4 3 3 4 5")
/** The left thumb's walk, down from G. */
private val THUMB_LEFT = left("G3:1 F3:1 E3:2 E3:1 F3:1 G3:2", "1 2 3 3 2 1")
/** Up with a thumb tuck under to F, and back with three crossing over. */
private val TUCK = right("C4:1 D4:1 E4:1 F4:1 G4:2 G4:1 F4:1 E4:1 D4:1 C4:2", "1 2 3 1 2 2 1 3 2 1", bpm = 66.0)
private val TUCK_UP = right("C4:1 D4:1 E4:1 F4:1 G4:2", "1 2 3 1 2", bpm = 60.0)
/** Fingers four and five, slowly. */
private val PINKY = right("F4:2 G4:2 F4:2 G4:2 F4:2 G4:2 F4:4", "4 5 4 5 4 5 4", bpm = 60.0)
/** A little tune that four and five carry. */
private val PINKY_TUNE = right("G4:1 F4:1 G4:2 G4:1 F4:1 G4:2 E4:1 F4:1 G4:1 F4:1 E4:4", "5 4 5 5 4 5 3 4 5 4 3", bpm = 76.0)
/** Skips: one, three, five. */
private val HOP = right("C4:1 E4:1 G4:2 G4:1 E4:1 C4:2", "1 3 5 5 3 1")
private val HOP_TUNE = right("C4:1 E4:1 G4:2 E4:1 G4:1 C4:2 D4:1 F4:1 E4:1 D4:1 C4:4", "1 3 5 3 5 1 2 4 3 2 1", bpm = 76.0)
/** Five fingers in G, up and back. */
private val FIVE_G = right("G4:1 A4:1 B4:1 C5:1 D5:2 C5:1 B4:1 A4:1 G4:2", "1 2 3 4 5 4 3 2 1")
/** Hot Cross Buns with the thumb on G: the same fingers, new keys. */
private val BUNS_G = right("B4:1 A4:1 G4:2 B4:1 A4:1 G4:2", "3 2 1 3 2 1", bpm = 80.0)
/** Five fingers in F: B flat under finger four. */
private val FIVE_F = right("F4:1 G4:1 A4:1 Bb4:1 C5:2 Bb4:1 A4:1 G4:1 F4:2", "1 2 3 4 5 4 3 2 1").copy(flats = true)
/** One little tune: the right hand high, then the left hand an octave lower (its own fingers). */
private val SAME_RIGHT = right("E4:1 D4:1 C4:1 D4:1 E4:1 E4:1 E4:2", "3 2 1 2 3 3 3", bpm = 80.0)
private val SAME_LEFT = left("E3:1 D3:1 C3:1 D3:1 E3:1 E3:1 E3:2", "3 4 5 4 3 3 3", bpm = 80.0)
private val SAME_BOTH = Exercise(right = "${SAME_RIGHT.right} r:8", left = "r:8 ${SAME_LEFT.left}", bpm = 80.0, fingers = SAME_RIGHT.fingers, leftFingers = SAME_LEFT.leftFingers)
/** Up and back with two, three, four. */
private val TWO_THREE_FOUR = right("D4:1 E4:1 F4:2 F4:1 E4:1 D4:2", "2 3 4 4 3 2")
/** All five in a row, thumb to little finger. */
private val ALL_FIVE = right("C4:1 D4:1 E4:1 F4:1 G4:2", "1 2 3 4 5")
/**
 * Hot Cross Buns with both hands: the tune on top, the left little finger on low C with each "buns", and the thumb on G
 * under "two a penny". Slowly, in time, two stars to pass.
 */
internal val BUNS_BOTH = Exercise(right = BUNS_X.right, left = "r:2 C3:2 r:2 C3:2 C3:2 G3:2 r:2 C3:2", bpm = 66.0, practice = Practice.BOTH, pass = CONCERT_PASS,
    fingers = "3 2 1 3 2 1 1 1 1 1 2 2 2 2 3 2 1", leftFingers = "5 5 5 1 5")
/** Twinkle's start with the band singing the tune: your left hand plays the low notes (little finger on C, two on F). */
internal val TWINKLE_LEFT = TWINKLE.copy(practice = Practice.LEFT, bpm = 72.0, pass = CONCERT_PASS, phraseEnd = null)

/** The treehouse concert (lesson 12), a song a step ([concertPlan]): Hot Cross Buns with both hands, then the encore. */
internal val TREEHOUSE_CONCERT = listOf(ConcertSong("Hot Cross Buns", "buns", BUNS_BOTH), ConcertSong("Twinkle, Twinkle, Little Star", "star", TWINKLE_LEFT))

/**
 * Which finger (lesson 11): a round per key, the painted hand pressing it with its finger; the child taps the number,
 * then plays the key. The exercise is the same keys with the same fingers, for a screen that does not know the game.
 */
private fun whichFinger(rightHand: Boolean, example: Pair<String, Int>, rounds: List<Pair<String, Int>>): Pair<StageGame.Tap, Exercise> {
    fun round(r: Pair<String, Int>): TapRound {
        val key = com.bigpapituna.sharpears.engine.noteNumber(r.first)
        return TapRound(listOf(listOf(TapSound(listOf(key), 900, velocity = 84, finger = r.second, right = rightHand))), TapChoice.finger(r.second), play = key)
    }
    val notes = rounds.joinToString(" ") { "${it.first}:2" }
    val fingers = rounds.joinToString(" ") { "${it.second}" }
    val ex = if (rightHand) right(notes, fingers, bpm = 60.0) else left(notes, fingers, bpm = 60.0)
    return StageGame.Tap(TapKind.FINGER, round(example), rounds.map(::round)) to ex
}

private val WHICH_RIGHT = whichFinger(true, "E4" to 3, listOf("G4" to 5, "C4" to 1, "D4" to 2, "F4" to 4, "E4" to 3))
private val WHICH_LEFT = whichFinger(false, "C3" to 5, listOf("G3" to 1, "E3" to 3, "D3" to 4, "C3" to 5))

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val TREEHOUSE: CourseLevel = CourseLevel(TREEHOUSE_ID, "Pup's Treehouse", "Twelve games up in the tree: fingers that walk, tuck, stretch and hop, in both hands.", listOf(
    lesson(1, "Finger walk", "Your thumb and the next two fingers walk up three keys and back.",
        read(
            scene("Welcome to my treehouse! Every finger has a number. Your thumb is one.", fingers("C4 D4 E4 F4 G4", ""), Mood.CHEER),
            scene("Thumb on C, finger two on D, finger three on E. Walk up: one, two, three.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4", "1 2 3"), stepMs = 750), Mood.POINT),
            scene("Then walk back down: three, two, one.", fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("E4 D4 C4", "3 2 1"), stepMs = 750), Mood.LISTEN)),
        listen("Listen to my fingers walk up the steps and back down.", WALK),
        play("Your turn. Say the numbers as you play: one, two, three.", WALK),
        play("Now I call the fingers and you play them. Listen for the numbers!",
            right("C4:1 D4:1 E4:1 r:2 E4:1 D4:1 C4:1 r:2 C4:1 D4:1 E4:1 D4:1 C4:1", "1 2 3 3 2 1 1 2 3 2 1", bpm = 80.0)).on(COPY),
        memory("Once more, up and back down, all by yourself.", WALK)),

    lesson(2, "Left hand walk", "Your left hand walks three keys too, and its numbers run the other way.",
        read(
            scene("Your left hand has numbers too. Its thumb is one as well.", fingers(right = "", left = "G3 F3 E3 D3 C3"), Mood.POINT),
            scene("So on the left, little finger five sits on C. Walk up: five, four, three.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("C3 D3 E3", "5 4 3", right = false), stepMs = 750)),
            scene("Your thumb walks too. From G it goes down: one, two, three.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("G3 F3 E3", "1 2 3", right = false), stepMs = 750), Mood.LISTEN)),
        listen("Listen to my left paw walk up and back down.", WALK_LEFT),
        play("Your turn, left hand: five, four, three, and back.", WALK_LEFT),
        play("Now the thumb walk: one, two, three goes down from G.", THUMB_LEFT),
        play("Follow my left hand. I say the fingers as it plays.",
            left("C3:1 D3:1 E3:1 r:2 G3:1 F3:1 E3:1 r:2 E3:1 D3:1 C3:1", "5 4 3 1 2 3 3 4 5", bpm = 80.0)).on(COPY)),

    lesson(3, "Thumb tuck", "Tuck your thumb under to climb all the way up to G.",
        read(
            scene("Your thumb can tuck under your hand, like a train into a tunnel.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4 F4", "1 2 3 1", labels = mapOf(3 to "Tuck")), stepMs = 800), Mood.POINT),
            scene("Walk up with one, two, three. Tuck your thumb under to F, then two plays G.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 D4 E4 F4 G4", "1 2 3 1 2"), stepMs = 750)),
            scene("Coming back, finger three crosses over your thumb to E.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("G4 F4 E4 D4 C4", "2 1 3 2 1", labels = mapOf(2 to "Over")), stepMs = 750), Mood.LISTEN)),
        listen("Listen: up the steps with a tuck, and back down.", TUCK),
        play("Your turn, slowly. Tuck your thumb under to F.", TUCK_UP),
        play("Watch my hand tuck, then copy it. Up, then down.",
            right("C4:1 D4:1 E4:1 F4:1 r:2 G4:1 F4:1 E4:1 D4:1 r:2 C4:1 D4:1 E4:1 F4:1 G4:1", "1 2 3 1 2 1 3 2 1 2 3 1 2")).on(COPY),
        memory("Up with a tuck and back down, all by yourself.", TUCK)),

    lesson(4, "Pinky push", "Fingers four and five get strong, slowly, then play a little tune.",
        read(
            scene("Fingers four and five are the quiet ones. Today they get strong!",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("F4 G4 F4 G4", "4 5 4 5"), stepMs = 800), Mood.CHEER),
            scene("Keep your hand round, as if you hold a ball, and press with the tips.",
                hand(HandMove.BALL to "A small ball", HandMove.TIPS to "On the tips"), Mood.POINT),
            scene("Four plays F and five plays G. Slowly: four, five, four, five.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("F4 G4 F4 G4", "4 5 4 5"), stepMs = 1000), Mood.LISTEN)),
        play("Slowly now: four on F, five on G, back and forth.", PINKY),
        play("Follow my hand: four and five take turns with three.",
            right("E4:1 F4:1 G4:1 r:2 G4:1 F4:1 E4:1 r:2 E4:1 G4:1 F4:1 G4:1", "3 4 5 5 4 3 3 5 4 5")).on(COPY),
        listen("Here is a little tune for four and five. Listen first.", PINKY_TUNE),
        play("Your turn: the pinky tune. Four and five do most of the work.", PINKY_TUNE)),

    lesson(5, "Skips", "Fingers one, three and five hop over the keys between.",
        read(
            scene("Some tunes skip. Your fingers hop over a key, like the pup over a puddle.",
                keys("A3", "B4", listOf(frame("C4", "C"), frame("D4", "Hop", Tint.DIM, play = false), frame("E4", "E"), frame("F4", "Hop", Tint.DIM, play = false),
                    frame("G4", "G")), stepMs = 800), Mood.POINT),
            scene("Thumb on C, three on E, five on G. Fingers two and four wait in the air.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 E4 G4", "1 3 5"), stepMs = 800)),
            scene("Hop up and hop back down: one, three, five, then five, three, one.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("C4 E4 G4 G4 E4 C4", "1 3 5 5 3 1"), stepMs = 650), Mood.LISTEN)),
        listen("Listen to my fingers hop up and back down.", HOP),
        play("Hop with me! Watch my hand, then copy the hops.",
            right("C4:1 E4:1 G4:1 r:2 G4:1 E4:1 C4:1 r:2 C4:1 E4:1 G4:1 E4:1 C4:1", "1 3 5 5 3 1 1 3 5 3 1")).on(COPY),
        play("Now a hopping tune, with some steps and some hops.", HOP_TUNE),
        memory("Hop up and back down once more, all by yourself.", HOP)),

    lesson(6, "Five fingers in G", "Move your whole hand up: thumb on G, and five new keys.",
        read(
            scene("Your hand can move house! Lift it up and put your thumb on G.", fingers("G4 A4 B4 C5 D5", ""), Mood.CHEER),
            scene("Now one, two, three, four and five play G, A, B, C and D.",
                fingers("G4 A4 B4 C5 D5", "", intro = false, steps = fingered("G4 A4 B4 C5 D5", "1 2 3 4 5"), stepMs = 650), Mood.POINT),
            scene("Same fingers, new keys. A tune you know sounds higher here.",
                keys("B3", "E5", walk("E4 D4 C4") + walk("B4 A4 G4", clearFirst = true), stepMs = 650), Mood.LISTEN)),
        listen("Listen to five fingers walk up from G and back.", FIVE_G),
        play("Your turn. Thumb on G, one finger for each key.", FIVE_G),
        play("Follow my hand in G. I say the fingers as it plays.",
            right("G4:1 A4:1 B4:1 r:2 D5:1 C5:1 B4:1 r:2 G4:1 B4:1 D5:1 r:2 B4:1 A4:1 G4:1 A4:1", "1 2 3 5 4 3 1 3 5 3 2 1 2")).on(COPY),
        play("A tune you know, in G: hot cross buns, with your thumb on G.", BUNS_G)),

    lesson(7, "Five fingers in F", "Thumb on F, and your fourth finger finds a black key.",
        read(
            scene("Now your thumb moves down to F. Five keys: F, G, A, B flat and C.", fingers("F4 G4 A4 Bb4 C5", ""), Mood.POINT),
            scene("Your fourth finger finds a black key. It is called B flat.",
                fingers("F4 G4 A4 Bb4 C5", "", intro = false, steps = fingered("F4 G4 A4 Bb4", "1 2 3 4", labels = mapOf(3 to "B flat")), stepMs = 800), Mood.CHEER),
            scene("Then five plays C. Up and back: one, two, three, four, five, and home.",
                fingers("F4 G4 A4 Bb4 C5", "", intro = false, steps = fingered("F4 G4 A4 Bb4 C5 Bb4 A4 G4 F4", "1 2 3 4 5 4 3 2 1"), stepMs = 560), Mood.LISTEN)),
        listen("Listen to five fingers in F. Can you hear the black key?", FIVE_F),
        play("Your turn. Finger four reaches up onto the black key.", FIVE_F),
        play("Watch finger four find the black key, then copy me.",
            right("A4:1 Bb4:1 C5:1 r:2 C5:1 Bb4:1 A4:1 r:2 F4:1 G4:1 A4:1 Bb4:1", "3 4 5 5 4 3 1 2 3 4").copy(flats = true)).on(COPY),
        memory("Five fingers in F, all by yourself. Remember the black key!", FIVE_F)),

    lesson(8, "Same tune, other hand", "A little tune in your right hand, then the same tune low down in your left.",
        read(
            scene("This tune walks down three keys and back up. First your right hand plays it.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("E4 D4 C4 D4 E4 E4 E4", "3 2 1 2 3 3 3"), stepMs = 560), Mood.LISTEN),
            scene("Then your left hand plays it lower down, on the keys one octave below.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("E3 D3 C3 D3 E3 E3 E3", "3 4 5 4 3 3 3", right = false), stepMs = 560), Mood.POINT),
            scene("Same tune, other fingers! On the left, little finger five plays C.",
                fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3", intro = false,
                    steps = listOf(chordPress("1:C4", label = "Thumb"), chordPress("5:C3", right = false, label = "Little finger")), stepMs = 1100), Mood.CHEER)),
        listen("Listen to the tune high, then low.", SAME_BOTH),
        play("Your right hand first: three, two, one, two, three.", SAME_RIGHT),
        play("Now your left hand, lower down: three, four, five, four, three.", SAME_LEFT),
        play("Copy me: my right hand plays it high, then my left hand low.",
            Exercise(right = "E4:1 D4:1 C4:1 D4:1 E4:2 r:8", left = "r:8 E3:1 D3:1 C3:1 D3:1 E3:2", bpm = 80.0, fingers = "3 2 1 2 3", leftFingers = "3 4 5 4 3")).on(COPY)),

    lesson(9, "Copy my fingers", "The pup plays a few notes and says the fingers. Copy him!",
        read(
            scene("My turn: I play a few notes and say the fingers. Two, three, four!",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("D4 E4 F4", "2 3 4"), stepMs = 800), Mood.LISTEN),
            scene("Your turn: play them back with the same fingers.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("D4 E4 F4 D4 E4 F4", "2 3 4 2 3 4", labels = mapOf(0 to "Me", 3 to "You")), stepMs = 650),
                Mood.POINT),
            scene("First you see my hand. Then you only listen, and copy by ear!",
                keys("A3", "A4", walk("D4 E4 F4") + walk("D4 E4 F4", clearFirst = true), stepMs = 600, names = KeyNames.NONE), Mood.THINK)),
        play("Watch and listen, then copy me. I say the fingers too.",
            right("D4:1 E4:1 F4:1 r:2 C4:1 E4:1 G4:1 r:2 G4:1 F4:1 E4:1 r:2 E4:1 D4:1 E4:1 C4:1", "2 3 4 1 3 5 5 4 3 3 2 3 1", bpm = 76.0)).on(COPY),
        play("Now just listen. I say the fingers, and you copy me by ear.",
            right("C4:1 D4:1 E4:1 D4:1 r:2 F4:1 E4:1 D4:1 r:2 E4:1 F4:1 G4:1 E4:1 r:2 G4:1 E4:1 C4:1", "1 2 3 2 4 3 2 3 4 5 3 5 3 1", bpm = 76.0))
            .on(COPY_BY_EAR),
        memory("Last one, all by yourself: two, three, four, and back down.", TWO_THREE_FOUR)),

    lesson(10, "Left hand echo", "Copy the pup with your left hand, low down.",
        read(
            scene("Now your left hand plays copy the pup. I call the fingers, low down.", fingers(right = "", left = "G3 F3 E3 D3 C3"), Mood.LISTEN),
            scene("Remember, on the left your thumb sits on G and five sits on C.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("G3 C3", "1 5", right = false, labels = mapOf(0 to "Thumb", 1 to "Five")),
                    stepMs = 1000), Mood.POINT),
            scene("Listen, then play it back with your left hand.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("C3 D3 E3", "5 4 3", right = false), stepMs = 700), Mood.THINK)),
        play("Watch my left hand, then copy it. I say the fingers.",
            left("C3:1 D3:1 E3:1 r:2 G3:1 F3:1 E3:1 r:2 E3:1 D3:1 C3:1 r:2 C3:1 E3:1 G3:1", "5 4 3 1 2 3 3 4 5 5 3 1", bpm = 76.0)).on(COPY),
        play("Now copy me by ear. Listen for the fingers I call.",
            left("G3:1 F3:1 G3:1 E3:1 r:2 C3:1 D3:1 C3:1 r:2 E3:1 F3:1 G3:1 r:2 D3:1 E3:1 D3:1 C3:1", "1 2 1 3 5 4 5 3 2 1 4 3 4 5", bpm = 76.0)).on(COPY_BY_EAR),
        memory("Last one, all by yourself: five, four, three, and back.", WALK_LEFT)),

    lesson(11, "Which finger?", "Watch the painted hand press a key. Which finger did it use?",
        read(
            scene("Watch my hand press a key. Which finger did it use?",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("E4", "3", labels = mapOf(0 to "Which one?")), stepMs = 1400), Mood.THINK),
            scene("Tap its number. One is your thumb, and five is your little finger.", fingers("C4 D4 E4 F4 G4", ""), Mood.POINT),
            scene("Then you play the key yourself, with that same finger.",
                fingers("C4 D4 E4 F4 G4", "", intro = false, steps = fingered("E4", "3"), stepMs = 1400), Mood.PLAY)),
        play("Which finger pressed the key? Tap its number, then play it.", WHICH_RIGHT.second).on(WHICH_RIGHT.first),
        play("Now my left hand. Which finger was it? Tap, then play.", WHICH_LEFT.second).on(WHICH_LEFT.first),
        play("Play all five in a row, from your thumb to your little finger.", ALL_FIVE)),

    lesson(12, "Treehouse concert", "Hot Cross Buns with both hands, then Twinkle with the band.",
        read(
            scene("Tonight the treehouse is a stage, and you are the star!",
                pop("The leaves", "The lanterns", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("First Hot Cross Buns with both hands. Then the band sings Twinkle while your left hand plays.",
                lane(BUNS_BOTH.right, BUNS_BOTH.left, bpm = 66), Mood.POINT)),
        perform("Play Hot Cross Buns with both hands, right to the end!", TREEHOUSE_CONCERT[0].exercise, Game.CONCERT),
        perform("Encore! The band sings Twinkle, and your left hand plays the low notes.", TREEHOUSE_CONCERT[1].exercise, Game.CONCERT),
        read(
            scene("Listen to the whole tree cheer for you!", pop("Bravo", "bravo", "bravo!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700), Mood.CHEER),
            scene("Take a bow, piano player! Your fingers can walk, tuck, stretch and hop.", fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3"), Mood.CHEER))),
))
