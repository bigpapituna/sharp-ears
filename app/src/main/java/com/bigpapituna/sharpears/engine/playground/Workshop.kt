package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.LEFT_HAND_WALK
import com.bigpapituna.sharpears.engine.MARY_X
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.StepKind
import com.bigpapituna.sharpears.engine.arm
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.chordPress
import com.bigpapituna.sharpears.engine.dynamics
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.hand
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.parseTokens
import com.bigpapituna.sharpears.engine.pedal
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.posture
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.theory.ArmMove
import com.bigpapituna.sharpears.engine.theory.FingerPress
import com.bigpapituna.sharpears.engine.theory.FingerStep
import com.bigpapituna.sharpears.engine.theory.HandMove
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.PostureCue
import com.bigpapituna.sharpears.engine.theory.Touch
import com.bigpapituna.sharpears.engine.touch

/*
 * Pup's Playground, the workshop (2.3, part A): twelve lessons on how to use a piano, after the concert hall. Roy asked
 * for lessons "teaching them which finger should be used, showing them basic ways to use the piano, and teaching them
 * how to place their hands on the piano". How to sit, a round hand, the finger numbers, each hand's home and the middle C
 * position, a level wrist, the arm's weight, short and long notes, the pedal, the hands taking turns, and a concert.
 *
 * The garden's model and rules (Playground.kt): READ steps of one to three scenes the pup says (the pictures are the soft
 * figure at the bench, the painted hand, the arm at the key and the foot on the pedal), a demo before every round, three
 * tries and it goes by, strict keys. The app hears keys, never fingers: the copy asks for a finger and says it listens
 * for the key. Every exercise names its fingers (book fingers), and every finger a scene shows is the one the notes show
 * (FingersAgreeTest).
 */

/** The workshop's course level; its page is [PLAYGROUND_PAGES]' fourth. */
const val WORKSHOP_ID = "playground-workshop"

private fun lesson(n: Int, title: String, summary: String, vararg steps: Step) = CourseLesson("workshop-$n", title, summary, steps.toList())

private fun pick(text: String) = Step(StepKind.PLAY, text, PICK_EXERCISE, game = Game.PICK)
private fun handGame(text: String, ex: Exercise) = Step(StepKind.PLAY, text, ex, game = Game.HAND)

/** What a pick step plays in a screen that does not know the game: middle C, with the thumb. */
private val PICK_EXERCISE = Exercise(right = "C4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "1")

// ---- The tunes, each with its fingers --------------------------------------------------------------------------------

/** Lesson 1: one long middle C, sitting well; then three, a rest between. */
private val ONE_C = Exercise(right = "C4:4", bpm = 60.0, practice = Practice.RIGHT, fingers = "1")
private val THREE_CS = Exercise(right = "C4:1 r:1 C4:1 r:1 C4:2", bpm = 66.0, practice = Practice.RIGHT, fingers = "1 1 1")
/** Lesson 2: the bubble hand plays C with the thumb, then D and E with the next fingers, then up and back. */
private val BUBBLE_C = Exercise(right = "C4:2 C4:2 C4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "1 1 1")
private val BUBBLE_DE = Exercise(right = "D4:2 E4:2 D4:2 E4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "2 3 2 3")
private val BUBBLE_CDE = Exercise(right = "C4:1 D4:1 E4:2 D4:1 C4:3", bpm = 66.0, practice = Practice.RIGHT, fingers = "1 2 3 2 1")
/** Lesson 3: the pup calls a finger; the key under it in the right hand's home (no finger twice running). */
private val CALL_ONE = Exercise(right = "E4:2 C4:2 G4:2 D4:2 F4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "3 1 5 2 4")
private val CALL_TWO = Exercise(right = "D4:2 F4:2 C4:2 G4:2 E4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "2 4 1 5 3")
/** Lesson 4: each finger on its key, then up the hill and back down. */
private val HOME_KEYS = Exercise(right = "C4:2 D4:2 E4:2 F4:2 G4:2", bpm = 60.0, practice = Practice.RIGHT, fingers = "1 2 3 4 5")
internal val UP_AND_DOWN = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:2 F4:1 E4:1 D4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT,
    fingers = "1 2 3 4 5 4 3 2 1")
/** Lesson 5: the left hand's fingers on their keys, the little finger on low C. The walk is [LEFT_HAND_WALK]. */
private val LEFT_HOME_KEYS = Exercise(left = "C3:2 D3:2 E3:2 F3:2 G3:2", bpm = 60.0, practice = Practice.LEFT, leftFingers = "5 4 3 2 1")
/**
 * Lesson 6, the middle C position: both thumbs on middle C, the left little finger on F below it, the right one on G
 * above. The hands take turns, walking out from the middle: left C, right C, left B, right D, and on to the little fingers.
 */
private val THUMBS = Exercise(right = "r:1 C4:1 r:1 D4:1 r:1 E4:1 r:1 F4:1 r:1 G4:1", left = "C4:1 r:1 B3:1 r:1 A3:1 r:1 G3:1 r:1 F3:1 r:1",
    bpm = 66.0, fingers = "1 2 3 4 5", leftFingers = "1 2 3 4 5")
/** Lesson 7: a slow walk up to G and back, keeping the wrist level. */
private val BRIDGE_WALK = Exercise(right = "C4:2 D4:2 E4:2 F4:2 G4:4 F4:2 E4:2 D4:2 C4:4", bpm = 88.0, practice = Practice.RIGHT,
    fingers = "1 2 3 4 5 4 3 2 1")
/** Lesson 8: middle C your usual way, then heavy, soft, three times ([SoftLoud.ARM_ROUNDS]); then C, E and G with the arm's weight. */
private val ARM_NOTES = SoftLoud.exercise(SoftLoud.ARM_ROUNDS)
private val HEAVY_NOTES = Exercise(right = "C4:2 E4:2 G4:4", bpm = 66.0, practice = Practice.RIGHT, fingers = "1 3 5")
/** Lesson 9: C, E, G and back, short and bouncing with a little rest after each; then long and smooth. */
private val SHORT_TUNE = Exercise(right = "C4:0.5 r:0.5 E4:0.5 r:0.5 G4:0.5 r:0.5 E4:0.5 r:0.5 C4:0.5 r:1.5", bpm = 72.0, practice = Practice.RIGHT,
    beatLines = true, fingers = "1 3 5 3 1")
private val LONG_TUNE = Exercise(right = "C4:1 E4:1 G4:1 E4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT, beatLines = true, fingers = "1 3 5 3 1")
/** Lesson 10: C, E and G up, then down: two little tunes the pup plays back with the pedal. */
private val PEDAL_TUNES = Exercise(right = "C4:1 E4:1 G4:2 r:2 G4:1 E4:1 C4:2", bpm = 72.0, practice = Practice.RIGHT, fingers = "1 3 5 5 3 1")
/** Lesson 11: left says C D E, right answers C D E; then left E D C, right E D C. Both hands in the C position. */
private val TURNS = Exercise(right = "r:4 C4:1 D4:1 E4:2 r:4 E4:1 D4:1 C4:2", left = "C3:1 D3:1 E3:2 r:4 E3:1 D3:1 C3:2 r:4", bpm = 72.0,
    fingers = "1 2 3 3 2 1", leftFingers = "5 4 3 3 4 5")
/**
 * Lesson 12, the workshop's concert: Mary Had a Little Lamb with the thumb on C (three two one two three...), the band on
 * the left, at a kid's tempo, two stars to pass. The practice run before it is cut for kids at "little lamb".
 */
internal val WORKSHOP_MARY = MARY_X.copy(bpm = 80.0, practice = Practice.RIGHT, pass = CONCERT_PASS, phraseEnd = null,
    fingers = "3 2 1 2 3 3 3 2 2 2 3 5 5")

// ---- Pictures used more than once ------------------------------------------------------------------------------------

private val RIGHT_HOME = "C4 D4 E4 F4 G4"
private val LEFT_HOME = "G3 F3 E3 D3 C3"
/** The middle C position's left hand, fingers 1 to 5: thumb on middle C, little finger on F below it. */
private val LEFT_MIDDLE_C = "C4 B3 A3 G3 F3"

/** One finger of one hand pressing a key, a step of a finger picture. */
private fun press(right: Boolean, finger: Int, key: String, label: String? = null) = FingerStep(listOf(FingerPress(right, finger, noteNumber(key))), label)

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val WORKSHOP: CourseLevel = CourseLevel(WORKSHOP_ID, "Pup's Workshop", "Twelve lessons on how to play a piano: sitting, round hands, finger numbers, both hands, the pedal and a concert.", listOf(
    lesson(1, "Sit like a pianist", "Sit tall, not too close, feet flat. Then play a C.",
        read(
            scene("Pianists sit tall, as if a string pulls up the top of your head.", posture(PostureCue.BACK to "Sit tall"), Mood.POINT),
            scene("Not too close! Your elbows need room, just in front of your body.",
                posture(PostureCue.CLOSE to "Room to move", PostureCue.ELBOWS to "Elbows in front")),
            scene("Your feet rest flat on the floor. If they do not reach, put a box under them.", posture(PostureCue.FEET to "Feet flat"), Mood.CHEER)),
        pick("Which one sits like a pianist? Tap the one sitting just right!"),
        play("Now sit just right, like the one you picked. Play one long middle C with your thumb.", ONE_C),
        play("Still sitting tall? Play middle C three times, with a rest between.", THREE_CS),
        read(scene("That is how pianists sit, every time. Check it before you play!", posture(PostureCue.BACK to "Tall", PostureCue.HANDS to "Ready"), Mood.CHEER))),

    lesson(2, "The bubble hand", "Round fingers, as if a bubble sits under your hand.",
        read(
            scene("Let your hand hang loose. Now hold a bubble under it, gently, so it does not pop.",
                hand(HandMove.HANG to "Hang loose", HandMove.BALL to "A bubble"), Mood.POINT),
            scene("Your fingers curve round the bubble and land on their tips.", hand(HandMove.CURL to "Round fingers", HandMove.TIPS to "On the tips")),
            scene("Flat fingers would squash the bubble. Round fingers keep it safe.", hand(HandMove.TIPS to "Round and safe", HandMove.THUMB to "Thumb on its side"),
                Mood.CHEER)),
        handGame("Keep your bubble hand. Play middle C with your thumb, three times.", BUBBLE_C),
        handGame("Now D with the next finger, and E with your middle finger.", BUBBLE_DE),
        play("Up and back with your bubble hand: C, D, E, D, C.", BUBBLE_CDE)),

    lesson(3, "Finger numbers", "Thumbs are one, little fingers five, on both hands.",
        read(
            scene("Your fingers have numbers. Your thumb is one, and your little finger is five.", fingers(RIGHT_HOME, ""), Mood.POINT),
            scene("Your other hand counts the same way. Its thumb is one too.", fingers("", LEFT_HOME)),
            scene("Wiggle each finger as I say its number: one, two, three, four, five!", fingers(), Mood.CHEER)),
        handGame("I call a finger. Play the key under it! I hear the key, and you pick the finger.", CALL_ONE),
        handGame("Five more! Listen for the number, then find its key.", CALL_TWO)),

    lesson(4, "Right hand home", "Thumb on middle C, one finger on each key up to G.",
        read(
            scene("Your right hand has a home. Your thumb sits on middle C.", fingers(RIGHT_HOME, ""), Mood.POINT),
            scene("Each finger gets its own key: C, D, E, F and G, thumb to little finger.",
                fingers(RIGHT_HOME, "", intro = false, steps = fingered("C4 D4 E4 F4 G4", "1 2 3 4 5"), stepMs = 650)),
            scene("Walk up the hill, one to five, and back down, five to one.", lane(UP_AND_DOWN.right, bpm = 72), Mood.LISTEN)),
        handGame("Put each finger on its key. Play them one at a time, thumb first.", HOME_KEYS),
        listen("Listen to me walk up the hill and back down.", UP_AND_DOWN),
        play("Your turn. Each note shows the finger that plays it.", UP_AND_DOWN),
        memory("Once more, all by yourself. The numbers are hidden now.", UP_AND_DOWN)),

    lesson(5, "Left hand home", "Little finger on the C below middle C, up to G with your thumb.",
        read(
            scene("Your left hand has a home too. Its little finger sits on the C below middle C.", fingers("", LEFT_HOME), Mood.POINT),
            scene("Going up, your left fingers count down: five, four, three, two, one.",
                fingers("", LEFT_HOME, intro = false, steps = fingered("C3 D3 E3 F3 G3", "5 4 3 2 1", right = false), stepMs = 650), Mood.LISTEN)),
        handGame("Put each left finger on its key. Little finger first, on low C.", LEFT_HOME_KEYS),
        listen("Listen to my left hand walk up to G and back.", LEFT_HAND_WALK),
        play("Your turn, left hand. Each note shows its finger.", LEFT_HAND_WALK),
        memory("Once more, all by yourself. The numbers are hidden now.", LEFT_HAND_WALK)),

    lesson(6, "Thumbs meet", "Both thumbs share middle C. Your hands take turns.",
        read(
            scene("A new home for both hands: your two thumbs share middle C!", fingers(RIGHT_HOME, LEFT_MIDDLE_C), Mood.CHEER),
            scene("Your left little finger stretches down to F. Your right little finger reaches up to G.",
                fingers(RIGHT_HOME, LEFT_MIDDLE_C, intro = false, steps = listOf(press(false, 5, "F3", "Left five"), press(true, 5, "G4", "Right five")), stepMs = 1100),
                Mood.POINT),
            scene("Your hands take turns, out from the middle: left thumb, right thumb, and on.",
                fingers(RIGHT_HOME, LEFT_MIDDLE_C, intro = false, steps = listOf(press(false, 1, "C4", "Left"), press(true, 1, "C4", "Right"),
                    press(false, 2, "B3"), press(true, 2, "D4"), press(false, 3, "A3"), press(true, 3, "E4")), stepMs = 650), Mood.LISTEN)),
        listen("Listen to the hands take turns, out from middle C.", THUMBS),
        play("Your turn. Left, right, left, right, out from the middle.", THUMBS),
        memory("Once more, all by yourself.", THUMBS)),

    lesson(7, "Wrist like a bridge", "Keep your wrist level, like a bridge for a little car.",
        read(
            scene("Your wrist is a bridge from your arm to your hand. Keep it level.", arm(ArmMove.LEVEL to "A level bridge"), Mood.POINT),
            scene("Not sagging down, not poking up. Level, so a little car could drive across.",
                arm(ArmMove.LOW to "Sagging", ArmMove.HIGH to "Poking up", ArmMove.LEVEL to "Level"))),
        pick("Which wrist is a bridge? Tap the level one!"),
        listen("Listen to a slow walk up to G and back. Keep the bridge level all the way.", BRIDGE_WALK),
        play("Your turn, nice and slowly. Keep your wrist level, like a bridge.", BRIDGE_WALK)),

    lesson(8, "Heavy arm, soft arm", "Let your arm's weight make a big sound. Sink in gently for a soft one.",
        read(
            scene("A big sound comes from the weight of your arm, not from pushing hard.", arm(ArmMove.HEAVY to "Heavy arm"), Mood.POINT),
            scene("For a soft sound, your fingertip sinks in gently, like into a pillow.", arm(ArmMove.SOFT to "Soft arm")),
            scene("My ears only guess how loud you play, so this is just for fun.",
                dynamics("C4 C4 C4 C4", listOf(40, 104, 40, 104), mapOf(0 to "Soft", 1 to "Loud"), stepMs = 700), Mood.THINK)),
        play("Play middle C your usual way first. Then a heavy arm to wake me, a soft one to let me sleep.", ARM_NOTES, Game.SOFT_LOUD),
        play("Now let your arm's weight play C, E and G, one big round note each.", HEAVY_NOTES),
        read(scene("Heavy arm, soft arm. Now you know where a big sound comes from!", arm(ArmMove.HEAVY to "Heavy", ArmMove.SOFT to "Soft"), Mood.CHEER))),

    lesson(9, "Short and long", "Bounce off the keys for short notes. Stay down for long ones.",
        read(
            scene("Short notes bounce off the keys, like a ball. Long notes stay down, smooth as honey.",
                touch("C4 E4 G4 E4 C4", Touch.STACCATO, Touch.LEGATO, labels = listOf("Short, bouncing", "Long, smooth"))),
            scene("I listen for when each note starts. Short or long is up to your fingers!",
                touch("C4 C4 C4", Touch.STACCATO, Touch.LEGATO, labels = listOf("Short", "Long"), stepMs = 520), Mood.THINK)),
        pick("Listen. Was that note short or long? Tap the picture!"),
        listen("Listen to a bouncing tune: short notes, with little rests.", SHORT_TUNE),
        perform("Your turn. Bounce off each key, in time with the clicks.", SHORT_TUNE.copy(pass = CONCERT_PASS)),
        perform("Now long and smooth. Hold each key down until the next one.", LONG_TUNE.copy(pass = CONCERT_PASS))),

    lesson(10, "The pedal", "The pedal by your right foot makes the notes ring on.",
        read(
            scene("The pedal by your right foot makes notes keep ringing after your fingers lift.", pedal("C4+E4+G4", foot = true, chordMs = 3600), Mood.LISTEN),
            scene("Press it down and the sound rings on. Lift it and the sound stops.", pedal("C4+E4+G4 G3+B3+D4", chordMs = 2200), Mood.POINT)),
        pick("Listen. Was the pedal down? Tap the foot you hear!"),
        Step(StepKind.PLAY, "Play C, E and G. Then I play it back with the pedal down.", PEDAL_TUNES, game = Game.PEDAL),
        read(scene("Real pianos and some keyboards have this pedal. Ask a grown-up to show you yours!", pedal("C4+E4+G4", foot = true, chordMs = 3000),
            Mood.CHEER))),

    lesson(11, "Two hands take turns", "Your left hand says something, and your right hand answers.",
        read(
            scene("Left little finger on low C. Right thumb on middle C.", fingers(RIGHT_HOME, LEFT_HOME), Mood.POINT),
            scene("Left says, right answers: three notes low, then the same three up high.",
                fingers(RIGHT_HOME, LEFT_HOME, intro = false, steps = fingered("C3 D3 E3", "5 4 3", right = false) + fingered("C4 D4 E4", "1 2 3"), stepMs = 650),
                Mood.LISTEN)),
        listen("Listen: the left hand says it, the right hand answers.", TURNS),
        play("Your turn. Left hand first, then the right hand answers.", TURNS),
        memory("Once more, all by yourself.", TURNS)),

    lesson(12, "The workshop concert", "Mary Had a Little Lamb with the band, every finger in place.",
        read(
            scene("The piano is built! Tonight it plays its first song, and you are the pianist.",
                pop("The piano", "The band", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("Thumb on C. Watch each finger: three, two, one, two, three, three, three.",
                fingers(RIGHT_HOME, "", intro = false, steps = fingered("E4 D4 C4 D4 E4 E4 E4", "3 2 1 2 3 3 3"), stepMs = 560), Mood.POINT)),
        listen("Listen to the start of the song, with the band.", WORKSHOP_MARY.copy(phraseEnd = 8.0).bite()),
        play("Your turn, with the fingers on the notes: Mary had a little lamb.", WORKSHOP_MARY.copy(phraseEnd = 8.0)),
        perform("Now with the band, for everyone. Play right to the end!", WORKSHOP_MARY, Game.CONCERT),
        read(scene("Bravo! The workshop's piano sings, and you played it. Take a bow!",
            pop("Bravo", "bravo", "bravo!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700), Mood.CHEER))),
))

// ---- What the stages need ---------------------------------------------------------------------------------------------

/** How the hand stage looks in a lesson: a bubble under the palm, the finger numbers called, or the hand at home. */
enum class HandLook { HOME, BUBBLE, NUMBERS }

fun handLook(lessonId: String): HandLook = when (lessonId) {
    "workshop-2" -> HandLook.BUBBLE
    "workshop-3" -> HandLook.NUMBERS
    else -> HandLook.HOME
}

/** The finger that plays each note of [ex], by its book fingers (right hand, then left), in time order. */
fun bookFingers(ex: Exercise): List<Pair<Int, Int>> {
    fun of(line: String, fingers: String): List<Pair<Int, Pair<Double, Int>>> {
        val f = fingers.split(' ').mapNotNull { it.toIntOrNull() }
        var i = 0
        return parseTokens(line).flatMap { t -> t.keys.map { k -> k to (t.startBeat to (f.getOrNull(i++) ?: 0)) } }
    }
    return (of(ex.right, ex.fingers) + of(ex.left, ex.leftFingers)).sortedBy { it.second.first }.map { it.first to it.second.second }
}

/**
 * The hand's home for [ex]: the key under each finger, 1 to 5, from its book fingers, a white key a finger for those it
 * does not play (the right hand's fingers go up the keys, the left hand's down).
 */
fun handHome(ex: Exercise, right: Boolean): IntArray {
    val known = bookFingers(ex).filter { it.second in 1..5 }.associate { it.second to it.first }
    val anchor = known.entries.firstOrNull() ?: return if (right) intArrayOf(60, 62, 64, 65, 67) else intArrayOf(55, 53, 52, 50, 48)
    val whites = (21..108).filter { !com.bigpapituna.sharpears.engine.isBlack(it) }
    val at = whites.indexOf(anchor.value).coerceAtLeast(0)
    return IntArray(5) { i -> known[i + 1] ?: whites[(at + (if (right) 1 else -1) * (i + 1 - anchor.key)).coerceIn(0, whites.lastIndex)] }
}

/** The pick rounds of a pick step, by its instruction. */
fun pickRounds(step: Step): List<PickRound> = PICKS[step.text] ?: SIT_ROUNDS

private val SIT_ROUNDS = listOf(
    PickRound("Which one sits tall?", listOf(Pick.SIT_SLOUCH, Pick.SIT_TALL), Pick.SIT_TALL),
    PickRound("Which one has room for the elbows?", listOf(Pick.SIT_TALL, Pick.SIT_CLOSE), Pick.SIT_TALL),
    PickRound("Which one has flat feet?", listOf(Pick.SIT_FEET, Pick.SIT_TALL), Pick.SIT_TALL),
    PickRound("Which one sits just right?", listOf(Pick.SIT_CLOSE, Pick.SIT_TALL, Pick.SIT_SLOUCH), Pick.SIT_TALL),
)
private val WRIST_ROUNDS = listOf(
    PickRound("Which wrist is a level bridge?", listOf(Pick.WRIST_LOW, Pick.WRIST_LEVEL), Pick.WRIST_LEVEL),
    PickRound("Which wrist is a level bridge?", listOf(Pick.WRIST_LEVEL, Pick.WRIST_HIGH), Pick.WRIST_LEVEL),
    PickRound("Which wrist could a little car drive over?", listOf(Pick.WRIST_HIGH, Pick.WRIST_LOW, Pick.WRIST_LEVEL), Pick.WRIST_LEVEL),
)
private val SHORT_LONG_ROUNDS = listOf(
    PickRound("Listen. Short or long?", listOf(Pick.SHORT, Pick.LONG), Pick.SHORT, PickSound.SHORT),
    PickRound("Listen. Short or long?", listOf(Pick.SHORT, Pick.LONG), Pick.LONG, PickSound.LONG),
    PickRound("Listen. Short or long?", listOf(Pick.LONG, Pick.SHORT), Pick.LONG, PickSound.LONG),
    PickRound("Listen. Short or long?", listOf(Pick.LONG, Pick.SHORT), Pick.SHORT, PickSound.SHORT),
)
private val PEDAL_ROUNDS = listOf(
    PickRound("Listen. Pedal down or up?", listOf(Pick.PEDAL_UP, Pick.PEDAL_DOWN), Pick.PEDAL_DOWN, PickSound.PEDAL),
    PickRound("Listen. Pedal down or up?", listOf(Pick.PEDAL_UP, Pick.PEDAL_DOWN), Pick.PEDAL_UP, PickSound.DRY),
    PickRound("Listen. Pedal down or up?", listOf(Pick.PEDAL_DOWN, Pick.PEDAL_UP), Pick.PEDAL_UP, PickSound.DRY),
    PickRound("Listen. Pedal down or up?", listOf(Pick.PEDAL_DOWN, Pick.PEDAL_UP), Pick.PEDAL_DOWN, PickSound.PEDAL),
)

/** Each pick step's rounds, by its instruction (the lessons above). */
private val PICKS: Map<String, List<PickRound>> by lazy {
    val steps = WORKSHOP.lessons.flatMap { it.steps }.filter { it.game == Game.PICK }.map { it.text }
    mapOf(steps[0] to SIT_ROUNDS, steps[1] to WRIST_ROUNDS, steps[2] to SHORT_LONG_ROUNDS, steps[3] to PEDAL_ROUNDS)
}

/** What the workshop's stages say, listed for the voice catalog (part of [PlaygroundLines.CALL_OUTS]). */
object WorkshopLines {
    /** The hand stage's call in the finger numbers lesson: the round's finger. */
    fun fingerCall(finger: Int): String = when (finger) {
        1 -> "Finger one, your thumb!"
        2 -> "Finger two!"
        3 -> "Finger three!"
        4 -> "Finger four!"
        else -> "Finger five, your little finger!"
    }

    val BUBBLE_FOUND = listOf("Round and safe!", "The bubble did not pop!", "Lovely round fingers!")
    const val BUBBLE_DONE = "Your bubble is still safe!"
    const val BUBBLE_MISS = "Keep your bubble hand, and find the key that shines."
    val NUMBER_FOUND = listOf("That's the one!", "You found it!", "Yes!")
    const val NUMBER_MISS = "Look where that finger points."
    const val HOME_DONE = "Every finger found its key!"
    const val HOME_MISS = "Look where the finger points."

    // The pedal stage.
    const val DRY = "First, without the pedal."
    const val WET = "Now with the pedal down!"
    const val REPLAY = "Here is your tune with the pedal!"
    const val PEDAL_DONE = "Ring, ring! That is the pedal."

    val ALL: List<String> by lazy {
        buildList {
            (1..5).forEach { add(fingerCall(it)) }
            addAll(BUBBLE_FOUND); add(BUBBLE_DONE); add(BUBBLE_MISS); addAll(NUMBER_FOUND); add(NUMBER_MISS); add(HOME_DONE); add(HOME_MISS)
            add(DRY); add(WET); add(REPLAY); add(PEDAL_DONE)
            for (rounds in PICKS.values) addAll(PickLines.all(rounds))
        }.distinct()
    }
}
