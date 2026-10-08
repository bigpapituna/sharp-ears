package com.bigpapituna.sharpears.engine

/**
 * The Learn course: levels of short lessons, each with an explanation and either an exercise (a small
 * generated song played in the player) or a link to a library song.
 *
 * Exercise notation, one token per note: <name><octave>:<beats>, "r" is a rest, "+" stacks a chord.
 *   "C4:1 E4:1 G4:2 r:1 C4+E4+G4:4"
 */
data class Exercise(
    val right: String = "",
    val left: String = "",
    val bpm: Double = 80.0,
    val mode: Mode = Mode.WAIT,
    val practice: Practice = Practice.BOTH,
    /** Perform-mode accuracy (%) that counts as passing. */
    val pass: Int = 80,
)

data class CourseLesson(
    val id: String,
    val title: String,
    val summary: String,
    val body: List<String>,
    val exercise: Exercise? = null,
    /** A library song this lesson sends you to instead of (or after) its exercise. */
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

/** One hand's notes from exercise notation, timed at [msPerBeat]. */
fun parseLine(spec: String, hand: Hand, msPerBeat: Double): List<Note> {
    var beat = 0.0
    val out = mutableListOf<Note>()
    for (tok in spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val (names, len) = tok.split(':').let { it[0] to it[1].toDouble() }
        if (names != "r") names.split('+').forEach { n ->
            out += Note(noteNumber(n), (beat * msPerBeat).toLong(), (len * msPerBeat * 0.92).toLong(), hand, 80)
        }
        beat += len
    }
    return out
}

fun Exercise.toSong(id: String, title: String): Song {
    val msPerBeat = 60_000 / bpm
    val notes = (parseLine(right, Hand.RIGHT, msPerBeat) + parseLine(left, Hand.LEFT, msPerBeat))
        .sortedWith(compareBy({ it.startMs }, { it.midi }))
    val end = notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
    return Song("lesson-$id", title, bpm, 4 to 4, notes, groupOnsets(notes), emptyList(), end + 400)
}

private fun l(id: String, title: String, summary: String, vararg body: String, exercise: Exercise? = null, song: String? = null) =
    CourseLesson(id, title, summary, body.toList(), exercise, song)

val COURSE: List<CourseLevel> = listOf(
    CourseLevel("basics", "Basics", "Where to sit, where the notes are, and which finger is which.", listOf(
        l("sit", "Sit, then shape your hand", "Posture and a relaxed, curved hand.",
            "Sit on the front half of the bench, far enough back that your elbows are a little in front of your body. Your forearms should be level with the keys.",
            "Let your hand hang loose, then rest it on the keys as if holding a small ball: fingers curved, playing on their tips, thumb resting on its side.",
            "Play middle C with your right thumb. Let the key come back up between notes.",
            exercise = Exercise(right = "C4:1 r:1 C4:1 r:1 C4:1 r:1 C4:2", bpm = 60.0, practice = Practice.RIGHT)),
        l("find-c", "Find C anywhere", "The black keys show you where every C is.",
            "Black keys come in groups of two and three. Every C is the white key just left of a group of two.",
            "Middle C is the C closest to the middle of your piano. Notes are named by letter and by octave: middle C is C4, the next C up is C5.",
            "Play every C from low to high. Use your left hand for the low ones and your right hand for the high ones.",
            exercise = Exercise(right = "r:4 C4:2 C5:2 C6:2", left = "C2:2 C3:2 r:6", bpm = 60.0)),
        l("fingers-right", "Finger numbers", "Thumb is 1, little finger is 5.",
            "Both hands number their fingers the same way: thumb 1, index 2, middle 3, ring 4, little finger 5.",
            "The small number on each falling note is the finger to use. Put your right thumb on C and let each finger sit on its own white key, up to G. This is the C position.",
            exercise = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT)),
        l("fingers-left", "The left hand mirrors it", "Little finger on C, thumb on G.",
            "In the left hand the thumb sits highest. Put your left little finger (5) on the C below middle C and your thumb (1) on G.",
            "Walk up and back down, one finger per key.",
            exercise = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)),
        l("beat", "Keep a steady beat", "Play exactly when the note reaches the line.",
            "Music moves on a steady beat. Count \"1, 2, 3, 4\" out loud as the notes fall, and play as each one touches the line.",
            "This exercise uses Perform mode: the song does not wait for you, and you get a score for timing at the end.",
            exercise = Exercise(right = "C4:1 C4:1 C4:1 C4:1 G4:1 G4:1 G4:1 G4:1 C4:4", bpm = 66.0, mode = Mode.PERFORM, practice = Practice.RIGHT)),
        l("white-keys", "Name the white keys", "The musical alphabet: A to G, over and over.",
            "White keys use the letters A to G, then start again. From C they go C, D, E, F, G, A, B, then C again.",
            "Watch the letters on the notes as you play up one octave. When you run out of fingers, your thumb tucks under to reach F.",
            exercise = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", bpm = 66.0, practice = Practice.RIGHT)),
    )),
    CourseLevel("beginner", "Beginner", "First melodies, note lengths, and both hands together.", listOf(
        l("first-melody", "Your first melody", "Ode to Joy, in the C position.",
            "Keep your right hand in the C position: thumb on C, little finger on G. The whole tune fits under your fingers without moving the hand.",
            "Play it in wait mode first. Once the notes feel familiar, try it again in Perform mode from the player.",
            exercise = Exercise(right = "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:0.5 D4:2", bpm = 90.0, practice = Practice.RIGHT)),
        l("lengths", "Long and short notes", "Quarter, half and whole notes.",
            "A quarter note lasts one beat, a half note two, a whole note four. Longer blocks on the screen are longer notes: keep the key down until the block has passed the line.",
            exercise = Exercise(right = "C4:1 E4:1 G4:2 G4:2 E4:2 C4:4", bpm = 70.0, mode = Mode.PERFORM, practice = Practice.RIGHT)),
        l("bass", "Bass notes in the left hand", "Holding one low note under the music.",
            "Many songs need only one left-hand note at a time: the bass. Play it and hold it for the whole bar.",
            "These are C, F, G and back to C: the three most common bass notes in the key of C.",
            exercise = Exercise(left = "C3:4 F3:4 G3:4 C3:4", bpm = 70.0, practice = Practice.LEFT)),
        l("together", "Hands together", "Melody on top, bass underneath.",
            "Practise each hand alone first (use the L and R buttons in the player), then together. Wait mode holds the song until both hands have played.",
            "Look ahead: the left hand changes only every two beats.",
            exercise = Exercise(right = "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2",
                left = "C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2", bpm = 80.0)),
        l("black-keys", "Sharps and flats", "Black keys sit between the letters.",
            "A sharp (#) is the next key up, a flat (b) the next key down. The black key between F and G is F# (or Gb).",
            "This five-finger position starts on D and needs F# under your third finger.",
            exercise = Exercise(right = "D4:1 E4:1 F#4:1 G4:1 A4:1 G4:1 F#4:1 E4:1 D4:2", bpm = 70.0, practice = Practice.RIGHT)),
        l("rests", "Rests", "Silence is part of the rhythm.",
            "A rest is a beat with no note. Keep counting through it, and lift your finger so the note stops.",
            exercise = Exercise(right = "C4:1 r:1 E4:1 r:1 G4:1 r:1 C5:2", bpm = 70.0, mode = Mode.PERFORM, practice = Practice.RIGHT)),
        l("scale", "Thumb under: the C major scale", "Eight notes with five fingers.",
            "Play C, D, E with fingers 1, 2, 3, then tuck the thumb under to F and carry on with 1, 2, 3, 4, 5. On the way down, finger 3 crosses over the thumb.",
            "Keep the wrist level and let the thumb glide under; the hand should not jump.",
            exercise = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:1 B4:1 A4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT)),
        l("first-song", "Your first whole song", "Twinkle Twinkle, hands together.",
            "Learn it in three passes: right hand alone, left hand alone, then both. Use wait mode until it feels easy, then Perform mode for a score.",
            "Slow it down with the speed button if the notes come too fast.",
            song = "twinkle"),
    )),
    CourseLevel("intermediate", "Intermediate", "Scales in both hands, chords, inversions and progressions.", listOf(
        l("scale-left", "The scale in the left hand", "5, 4, 3, 2, 1, then 3 crosses over.",
            "Going up, the left hand plays C to G with fingers 5 to 1, then finger 3 crosses over the thumb to A.",
            exercise = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:1 B3:1 A3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)),
        l("scale-both", "Scales hands together", "Two hands, an octave apart.",
            "The hands cross their thumbs at different moments, which is the whole challenge. Go slowly enough that every note lines up.",
            exercise = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:2", bpm = 60.0)),
        l("triads", "Triads", "Three notes, every other white key.",
            "A triad stacks three notes a skip apart: C, E, G is C major. Fingers 1, 3 and 5 play it.",
            "Play C, F, G and C again. Press the three keys at the same moment.",
            exercise = Exercise(right = "C4+E4+G4:2 F4+A4+C5:2 G4+B4+D5:2 C4+E4+G4:2", bpm = 60.0, practice = Practice.RIGHT)),
        l("inversions", "Inversions", "The same chord, a different note on the bottom.",
            "Move the lowest note of a chord up an octave and you get an inversion. C-E-G becomes E-G-C, then G-C-E.",
            "Inversions let the hand move less from chord to chord. The Chords tab has every chord in every inversion.",
            exercise = Exercise(right = "C4+E4+G4:2 E4+G4+C5:2 G4+C5+E5:2 C5+E5+G5:2", bpm = 60.0, practice = Practice.RIGHT)),
        l("broken", "Broken chords", "The left hand plays a chord one note at a time.",
            "This pattern is called Alberti bass: bottom, top, middle, top. It keeps the music moving under a melody.",
            exercise = Exercise(left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:2",
                bpm = 70.0, practice = Practice.LEFT)),
        l("progression", "I, IV, V, I", "The progression behind thousands of songs.",
            "In the key of C, the I chord is C, IV is F and V is G. The right hand moves to nearby inversions while the left hand plays the root.",
            exercise = Exercise(right = "C4+E4+G4:4 C4+F4+A4:4 B3+D4+G4:4 C4+E4+G4:4", left = "C3:4 F2:4 G2:4 C3:4", bpm = 70.0)),
        l("minor", "Minor keys", "Same white keys, starting on A.",
            "A minor uses the same white keys as C major but starts on A, which gives it a darker sound. Finish with the A minor chord.",
            exercise = Exercise(right = "A3:1 B3:1 C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 A3+C4+E4:4", bpm = 66.0, practice = Practice.RIGHT)),
        l("expression", "Playing with expression", "Loud and soft, smooth and detached.",
            "The mic and MIDI hear which notes you play, not how. That part is yours: play the opening softly, let the melody sing over the left hand, and connect notes smoothly (legato) unless they have a gap.",
            "On a digital piano the right pedal holds notes after you lift your fingers. Change it when the harmony changes, or the sound blurs.",
            "Try it on the Minuet in G.",
            song = "minuet-g"),
    )),
    CourseLevel("advanced", "Advanced", "Longer scales, seventh chords, arpeggios and real repertoire.", listOf(
        l("scale-two", "Two-octave scales", "Thumb under twice.",
            "Two octaves use the fingering 1 2 3, 1 2 3 4, 1 2 3, 1 2 3 4 5. Keep the tempo even across the thumb crossings.",
            exercise = Exercise(right = "C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:0.5 E5:0.5 F5:0.5 G5:0.5 A5:0.5 B5:0.5 C6:2",
                bpm = 72.0, practice = Practice.RIGHT)),
        l("sevenths", "Seventh chords", "Four notes: the colour of jazz.",
            "Add one more skip to a triad and you get a seventh chord. Cmaj7 is C, E, G, B; Dm7 is D, F, A, C; G7 is G, B, D, F.",
            "This is the ii, V, I progression. The Chords tab has it in five keys.",
            exercise = Exercise(right = "D4+F4+A4+C5:4 B3+D4+F4+G4:4 C4+E4+G4+B4:4", left = "D3:4 G2:4 C3:4", bpm = 60.0)),
        l("arpeggios", "Arpeggios", "A chord spread over two octaves.",
            "Play the notes of a chord one at a time, up and down. The thumb tucks under after G.",
            exercise = Exercise(right = "C4:0.5 E4:0.5 G4:0.5 C5:0.5 E5:0.5 G5:0.5 C6:1 G5:0.5 E5:0.5 C5:0.5 G4:0.5 E4:0.5 C4:2", bpm = 66.0, practice = Practice.RIGHT)),
        l("independence", "Hand independence", "A melody over a moving left hand.",
            "The right hand plays the tune while the left keeps a broken-chord pattern going. Learn the left hand until it runs on its own, then add the melody.",
            exercise = Exercise(right = "E4:2 F4:1 G4:1 G4:2 F4:1 E4:1 D4:2 C4:2",
                left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5",
                bpm = 72.0)),
        l("repertoire", "Learning a real piece", "Für Elise, section by section.",
            "Big pieces are learned in small loops. Use the Loop button to repeat four bars, start at a slow speed, and play each hand alone before putting them together.",
            "When a section is reliable in wait mode, play it in Perform mode at full speed.",
            song = "fur-elise"),
    )),
)
