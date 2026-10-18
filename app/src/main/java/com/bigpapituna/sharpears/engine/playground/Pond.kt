package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.CourseLesson
import com.bigpapituna.sharpears.engine.CourseLevel
import com.bigpapituna.sharpears.engine.EIGHTHS_PATTERN
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.LEFT_HAND_WALK
import com.bigpapituna.sharpears.engine.LONDON_X
import com.bigpapituna.sharpears.engine.MACDONALD_X
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.QUARTERS_PATTERN
import com.bigpapituna.sharpears.engine.ROW_X
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.TWINKLE_LEFT_FINGERS
import com.bigpapituna.sharpears.engine.bite
import com.bigpapituna.sharpears.engine.chord
import com.bigpapituna.sharpears.engine.chordPress
import com.bigpapituna.sharpears.engine.chords
import com.bigpapituna.sharpears.engine.fingered
import com.bigpapituna.sharpears.engine.fingers
import com.bigpapituna.sharpears.engine.frame
import com.bigpapituna.sharpears.engine.keys
import com.bigpapituna.sharpears.engine.lane
import com.bigpapituna.sharpears.engine.phrase
import com.bigpapituna.sharpears.engine.pop
import com.bigpapituna.sharpears.engine.scene
import com.bigpapituna.sharpears.engine.skin.Bites
import com.bigpapituna.sharpears.engine.songMap
import com.bigpapituna.sharpears.engine.theory.Hit
import com.bigpapituna.sharpears.engine.theory.KeyNames
import com.bigpapituna.sharpears.engine.theory.Mood
import com.bigpapituna.sharpears.engine.theory.PhraseHands
import com.bigpapituna.sharpears.engine.theory.RhythmVisual
import com.bigpapituna.sharpears.engine.theory.Tint
import com.bigpapituna.sharpears.engine.walk

/*
 * Pup's Playground, the pond (2.2, docs/KIDS-PLAN.md section 4 and the 2.2 pages): twelve more lessons after the
 * garden, songs with words and both hands beginning. The same model and quality as the garden (Playground.kt): READ
 * steps of one to three scenes the pup says, games on their own stages (ui/playground), the course's songs reused and
 * cut to their first phrase (Exercise.phraseEnd, bite()) rather than written again.
 */

/** The pond's course level; its page is [PLAYGROUND_PAGES]' second. */
const val POND_ID = "playground-pond"

private fun lesson(n: Int, title: String, summary: String, vararg steps: com.bigpapituna.sharpears.engine.Step) =
    CourseLesson("pond-$n", title, summary, steps.toList())

/** A rhythm said in words ("Ta", "ti"), one per counted slot, for a scene: [hits] as start:length in beats, "r" a rest. */
internal fun sayRhythm(hits: String, words: List<String>, bpm: Int, sub: Int = 1, bars: Int = 1, loops: Int = 2, top: Int = 4) = RhythmVisual(
    top, 4, bpm, hits.split(' ').filter { it.isNotBlank() }.map { t ->
        val (a, b) = t.removePrefix("r").split(':'); Hit(a.toFloat(), b.toFloat(), t.startsWith("r"))
    }, bars, sub, words, loops, pitch = 60)

// ---- The tunes ------------------------------------------------------------------------------------------------------

/** Twinkle's start, right hand (the band plays the left). */
private val TWINKLE_RIGHT = TWINKLE.copy(practice = Practice.RIGHT)
/** Row, Row, Row Your Boat a little slower, cut after "row your boat" (two bars of three). */
private val ROW = ROW_X.copy(practice = Practice.RIGHT, bpm = 72.0, phraseEnd = 6.0)
/** Old MacDonald a little slower, cut after "had a farm". */
private val FARM = MACDONALD_X.copy(practice = Practice.RIGHT, bpm = 80.0, phraseEnd = 8.0)
/** London Bridge a little slower, cut after "falling down" (two bars of two). */
private val BRIDGE = LONDON_X.copy(practice = Practice.RIGHT, bpm = 72.0, phraseEnd = 4.0)
/** The garden's three steps, an octave down in the left hand: the little finger on C, one finger a key, as the scene shows. */
private val LEFT_STEPS = Exercise(left = STEPS.right.replace("4:", "3:"), bpm = 72.0, practice = Practice.LEFT, leftFingers = "5 4 3 3 4 5")
/** Twinkle's middle: up above the world so high, like a diamond in the sky. */
private const val TWINKLE_B = "G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2 G4:1 G4:1 F4:1 F4:1 E4:1 E4:1 D4:2"
private const val TWINKLE_B_LEFT = "C3:2 F3:2 C3:2 G2:2 C3:2 F3:2 C3:2 G2:2"
/** The whole star song, A B A, right hand at 70 with the band on the left (the A section's left fingers, the B's alike). */
private val TWINKLE_WHOLE = TWINKLE.copy(right = "${TWINKLE.right} $TWINKLE_B ${TWINKLE.right}", left = "${TWINKLE.left} $TWINKLE_B_LEFT ${TWINKLE.left}",
    bpm = 70.0, practice = Practice.RIGHT, phraseEnd = null,
    leftFingers = "$TWINKLE_LEFT_FINGERS 5 2 5 5 5 2 5 5 $TWINKLE_LEFT_FINGERS")
/** Ta, ti-ti: the eighths pattern of the Rhythm unit, its first bar, twice. */
private val TA_TITI = Bites.cut(Exercise(right = EIGHTHS_PATTERN), 4.0).right.let { bar -> Exercise(right = "$bar $bar", bpm = 60.0, practice = Practice.RIGHT, beatLines = true) }
/** Ta ta ta ta (the quarters pattern's first bar), then ta ti-ti ta ti-ti, to listen to. */
private val TA_THEN_TITI = Exercise(right = Bites.cut(Exercise(right = QUARTERS_PATTERN), 4.0).right + " " + Bites.cut(Exercise(right = EIGHTHS_PATTERN), 4.0).right,
    bpm = 60.0, practice = Practice.RIGHT, beatLines = true)
/** The C chord, three times, a breath between. */
private val CHORDS = Exercise(right = "C4+E4+G4:2 r:2 C4+E4+G4:2 r:2 C4+E4+G4:2", bpm = 60.0, practice = Practice.RIGHT)

// ---- The twelve lessons -----------------------------------------------------------------------------------------------

val POND: CourseLevel = CourseLevel(POND_ID, "Pup's Pond", "Twelve more games by the water: songs with words, a first chord, and both hands together.", listOf(
    lesson(1, "Twinkle, Twinkle, Little Star", "A song about a star, one colour at a time.",
        read(
            scene("Welcome to my pond! The frogs want a song about a star.",
                pop("Twin", "kle", "twin", "kle", "lit", "tle", "star!", notes = listOf("C4", "C4", "G4", "G4", "A4", "A4", "G4"), stepMs = 560), Mood.CHEER),
            scene("It starts on red C, jumps up to G, steps to A and comes back to G.",
                keys("A3", "C5", walk("C4 G4 A4 G4"), stepMs = 850), Mood.POINT),
            scene("Then it walks down, one step at a time, all the way home to C.",
                keys("A3", "C5", walk("F4 E4 D4 C4", mapOf(3 to "Home")), stepMs = 750), Mood.LISTEN)),
        listen("Listen to the start of the song. The band plays along.", TWINKLE_RIGHT.bite()),
        play("Your turn: twinkle, twinkle, little star.", TWINKLE_RIGHT.bite()),
        memory("Once more, all by yourself. The names are hidden now.", TWINKLE_RIGHT.bite()),
        play("Now the next part: how I wonder what you are.", TWINKLE_RIGHT.copy(phraseEnd = null))),

    lesson(2, "Row, Row, Row Your Boat", "Row the boat across the pond, long and short.",
        read(
            scene("Hop in my boat! Say the words as you play: row, row, row your boat.",
                pop("Row", "row", "row", "your", "boat", notes = listOf("C4", "C4", "C4", "D4", "E4"), stepMs = 640), Mood.PLAY),
            scene("The first two rows are long, like big pulls on the oars. Then the words go quicker.",
                sayRhythm("0:1.5 1.5:1.5 3:1 4:0.5 4.5:1.5", listOf("Row", "", "", "row", "", "", "row", "", "your", "boat", "", ""), 72, sub = 2, bars = 2, top = 3), Mood.LISTEN),
            scene("It climbs three steps you know: C, D and E.", keys("A3", "G4", walk("C4 D4 E4"), stepMs = 750), Mood.POINT)),
        listen("Listen to the start of the song. The band rocks the boat.", ROW.bite()),
        play("Your turn: row, row, row your boat.", ROW.bite()),
        memory("Once more, all by yourself. The names are hidden now.", ROW.bite()),
        play("Now the next part: gently down the stream.", ROW.copy(phraseEnd = null))),

    lesson(3, "Copy the frogs", "The frogs sing four notes. Sing them back on the keys.",
        read(
            scene("The frogs in my pond love to sing. Each frog sings one note.",
                keys("A3", "A4", listOf(frame("C4", "Frog"), frame("D4", "Frog"), frame("E4", "Frog"), frame("C4", "Frog")), stepMs = 700, names = KeyNames.NONE),
                Mood.LISTEN),
            scene("They sing four notes. You listen, then play the same four back.",
                keys("A3", "A4", walk("C4 D4 E4 C4") + listOf(frame("C4", "You", Tint.BLUE, clear = true), frame("D4", "You", Tint.BLUE), frame("E4", "You", Tint.BLUE),
                    frame("C4", "You", Tint.BLUE)), stepMs = 560, names = KeyNames.NONE), Mood.POINT),
            scene("If a note slips away, that is fine. The frogs sing it again.",
                keys("A3", "A4", walk("E4 D4 C4 E4"), stepMs = 560, names = KeyNames.NONE), Mood.THINK)),
        play("Listen to the frogs, then play their four notes back.",
            Exercise(right = "C4:1 D4:1 E4:1 C4:1 r:2 E4:1 D4:1 C4:1 E4:1 r:2 G4:1 E4:1 G4:1 E4:1", bpm = 80.0, practice = Practice.RIGHT), Game.ECHO),
        play("These frogs sing bits of songs you know. Can you copy them?",
            Exercise(right = "E4:1 D4:1 C4:1 D4:1 r:2 C4:1 C4:1 G4:1 G4:1", bpm = 80.0, practice = Practice.RIGHT), Game.ECHO)),

    lesson(4, "Drum on a lily pad", "Clap with the pup's drum, slowly and then quicker.",
        read(
            scene("I found a lily pad that sounds like a drum! Boom, boom, boom, boom.", drumBeat(listOf("Boom", "boom", "boom", "boom"), bpm = 60), Mood.LISTEN),
            scene("Clap once on every boom. First slowly, then a little quicker.", drumBeat(listOf("Clap", "clap", "clap", "clap"), bpm = 90), Mood.PLAY)),
        play("My drum goes slowly first. Clap on every boom.", DRUM.copy(bpm = 60.0), Game.CLAP),
        play("Now my drum goes quicker! Clap on every boom.", DRUM.copy(bpm = 90.0), Game.CLAP),
        perform("Now play middle C on every boom, nice and slowly. The clicks count you in.", DRUM.copy(bpm = 60.0, pass = CONCERT_PASS))),

    lesson(5, "Ta and ti-ti", "Say it, clap it, play it: walking notes and running notes.",
        read(
            scene("Some notes walk, one on every beat: ta, ta, ta, ta.",
                sayRhythm("0:1 1:1 2:1 3:1", listOf("Ta", "ta", "ta", "ta"), 60), Mood.LISTEN),
            scene("Some notes run, two in one beat: ta, ti-ti, ta, ti-ti.",
                sayRhythm("0:1 1:0.5 1.5:0.5 2:1 3:0.5 3.5:0.5", listOf("Ta", "", "Ti", "ti", "Ta", "", "Ti", "ti"), 60, sub = 2), Mood.LISTEN),
            scene("Say it first, then clap it, then play it.", pop("Say it", "Clap it", "Play it!", notes = listOf("C4", "C4+E4", "C4+E4+G4"), stepMs = 800), Mood.CHEER)),
        listen("Listen and say it with me: ta, ta, ta, ta, then ta, ti-ti, ta, ti-ti.", TA_THEN_TITI),
        play("Clap it with my drum: ta, ti-ti, ta, ti-ti.", TA_TITI, Game.CLAP),
        perform("Now play it on middle C: ta, ti-ti, ta, ti-ti. The clicks count you in.", TA_TITI.copy(pass = CONCERT_PASS))),

    lesson(6, "Old MacDonald Had a Farm", "A farm song by the pond, starting with a dip to low G.",
        read(
            scene("Old MacDonald had a farm, and his farm had a pond like mine!",
                pop("Old", "Mac", "Don", "ald", "had", "a", "farm", notes = listOf("C4", "C4", "C4", "G3", "A3", "A3", "G3"), stepMs = 520), Mood.CHEER),
            scene("The song starts on C, then dips down to the G below it.",
                keys("E3", "G4", listOf(frame("C4", "C"), frame("G3", "Low G"), frame("A3", "A"), frame("G3", "G")), stepMs = 900), Mood.POINT),
            scene("Put your thumb on that low G, so your fingers reach up to C.",
                fingers("G3 A3 B3 C4 D4", "", intro = false, steps = fingered("C4 C4 C4 G3 A3 A3 G3", "4 4 4 1 2 2 1", labels = mapOf(3 to "Thumb")), stepMs = 480))),
        listen("Listen to the start of the song.", FARM.bite()),
        play("Your turn: Old MacDonald had a farm.", FARM.bite()),
        memory("Once more, all by yourself. The names are hidden now.", FARM.bite()),
        play("Now the whole line, all the way to E, I, E, I, O. Slide your hand up for it.", FARM.copy(phraseEnd = null))),

    lesson(7, "Left paw walks", "Your left hand walks up three steps and back, down low.",
        read(
            scene("Your left hand is my left paw. Its little finger sits on the C below the middle.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", steps = fingered("C3", "5", right = false, labels = mapOf(0 to "Little finger")), stepMs = 800),
                Mood.POINT),
            scene("Then it walks up: C, D, E, one finger for each key.",
                fingers(right = "", left = "G3 F3 E3 D3 C3", intro = false, steps = fingered("C3 D3 E3", "5 4 3", right = false), stepMs = 700)),
            scene("Up three steps and back down, like a frog on stepping stones.", lane("", LEFT_STEPS.left, bpm = 72), Mood.LISTEN)),
        listen("Listen to my left paw walk up and back down.", LEFT_STEPS),
        play("Your turn, left hand. Up the steps and back down.", LEFT_STEPS),
        memory("Once more, all by yourself. The names are hidden now.", LEFT_STEPS),
        play("Now a longer walk for your left paw, up to G and back.", LEFT_HAND_WALK)),

    lesson(8, "Both paws", "Your left hand holds low C while your right hand walks.",
        read(
            scene("Both paws play together now! Your left little finger holds low C.",
                fingers("C4 D4 E4 F4 G4", "G3 F3 E3 D3 C3", intro = false, steps = listOf(chordPress("5:C3", right = false, label = "Hold it")), stepMs = 1400),
                Mood.POINT),
            scene("While it holds, your right hand walks up: C, D, E.", lane("C4:1 D4:1 E4:2", "C3:4", bpm = 66), Mood.LISTEN),
            scene("Then Twinkle with both paws. Your left hand plays one low note at a time.",
                phrase(TWINKLE, PhraseHands.BOTH, upTo = 8f), Mood.LISTEN)),
        play("Hold low C with your left hand. Your right hand walks up and back down.",
            Exercise(right = "C4:1 D4:1 E4:2 E4:1 D4:1 C4:2", left = "C3:4 C3:4", bpm = 66.0, leftFingers = "5 5")),
        listen("Listen to the start of Twinkle with both paws.", TWINKLE.bite()),
        play("Your turn, both paws. The notes wait until both hands have played.", TWINKLE.bite()),
        memory("Both paws once more, all by yourself.", TWINKLE.bite())),

    lesson(9, "Your first chord", "Three keys at once: C, E and G. The pup sits on it.",
        read(
            scene("Press three keys at once and they ring together. That is a chord!", chords(chord("C4 E4 G4", "C chord")), Mood.LISTEN),
            scene("Skip a key each time: C, then E, then G.",
                keys("A3", "B4", listOf(frame("C4", "C"), frame("D4", "Skip", Tint.DIM, play = false), frame("E4", "E"), frame("F4", "Skip", Tint.DIM, play = false),
                    frame("G4", "G"), frame("C4 E4 G4", "Chord", clear = true)), stepMs = 800), Mood.POINT),
            scene("Your thumb, middle finger and little finger press together. Then I sit on your chord like a cushion.",
                fingers(left = "", intro = false, steps = listOf(chordPress("1:C4 3:E4 5:G4", label = "Together"), chordPress("1:C4 3:E4 5:G4")), stepMs = 1100),
                Mood.CHEER)),
        listen("Listen to the chord ring.", CHORDS),
        play("Your turn! Press all three keys at once, and I will sit on your chord.", CHORDS, Game.CHORD),
        play("Now walk down, and finish with your chord.", Exercise(right = "E4:1 D4:1 C4:2 E4:1 D4:1 C4+E4+G4:2", bpm = 66.0, practice = Practice.RIGHT))),

    lesson(10, "London Bridge", "A bridge over the pond, with a skip in its step.",
        read(
            scene("London Bridge crosses my pond. Sing it with me!",
                pop("Lon", "don", "bridge", "is", "fall", "ing", "down", notes = listOf("G4", "A4", "G4", "F4", "E4", "F4", "G4"), stepMs = 500), Mood.CHEER),
            scene("Lon is long and don is quick, like a little skip.",
                sayRhythm("0:0.75 0.75:0.25 1:0.5 1.5:0.5", listOf("Lon", "", "", "don", "bridge", "", "is", ""), 60, sub = 4, top = 2), Mood.LISTEN),
            scene("It starts on G, steps down to E, then climbs back up to G.", keys("D4", "A4", walk("G4 A4 G4 F4 E4 F4 G4"), stepMs = 520), Mood.POINT)),
        listen("Listen to the start of the song.", BRIDGE.bite()),
        play("Your turn: London Bridge is falling down.", BRIDGE.bite()),
        memory("Once more, all by yourself. The names are hidden now.", BRIDGE.bite()),
        play("Now the whole line: falling down, falling down.", BRIDGE.copy(phraseEnd = null))),

    lesson(11, "Twinkle all the way", "The whole star song, with the band.",
        read(
            scene("You know how Twinkle starts. Now you play the whole song!", songMap("A B A", ex = TWINKLE_WHOLE, sectionMs = 1300), Mood.CHEER),
            scene("The middle walks down from G twice: up above the world so high.",
                keys("B3", "A4", walk("G4 G4 F4 F4 E4 E4 D4", mapOf(0 to "Up above")), stepMs = 520), Mood.POINT),
            scene("Then the start comes back, just as you know it.", songMap("A B A", com.bigpapituna.sharpears.engine.theory.MapPass(2, 2), ex = TWINKLE_WHOLE,
                sectionMs = 1300), Mood.LISTEN)),
        listen("Listen to the whole song. The band plays the left hand.", TWINKLE_WHOLE),
        play("Your turn for the middle: up above the world so high.", Exercise(right = TWINKLE_B, left = TWINKLE_B_LEFT, bpm = 70.0, practice = Practice.RIGHT)),
        perform("Now the whole song with the band, in time. If you slip, just keep going!", TWINKLE_WHOLE.copy(pass = CONCERT_PASS))),

    lesson(12, "Pond concert", "Play a song for the frogs and the ducks.",
        read(
            scene("The frogs and the ducks are here for my pond concert. You are the star!",
                pop("The frogs", "The ducks", "And you!", notes = listOf("C4+E4+G4", "F4+A4+C5", "G4+B4+D5"), stepMs = 800), Mood.CHEER),
            scene("Pick a song. The notes keep moving, so if you slip, just keep going.", lane(TWINKLE.bite().right, "C3:4 F3:2 C3:2", bpm = 72), Mood.POINT)),
        perform("Play your song for the pond, right to the end!", POND_SONGS.first().exercise, Game.CONCERT),
        perform("The frogs want more! Pick a song for an encore.", POND_SONGS[1].exercise, Game.CONCERT),
        read(scene("Ribbit, quack, hooray! Take a bow, piano player!", pop("Ribbit", "Quack", "Hooray!", notes = listOf("C4+E4+G4", "E4+G4+C5", "G4+C5+E5"), stepMs = 700),
            Mood.CHEER))),
))
