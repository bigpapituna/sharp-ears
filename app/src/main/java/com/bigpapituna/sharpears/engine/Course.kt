package com.bigpapituna.sharpears.engine

/**
 * The Learn course: levels of short lessons. Each lesson is a list of steps: text to read (and hear), and
 * usually an exercise played in the embedded player (listen, play, play from memory, or perform for a score).
 *
 * Exercise notation, one token per note: <name><octave>:<beats>, "r" is a rest, "+" stacks a chord.
 *   "C4:1 E4:1 G4:2 r:1 C4+E4+G4:4"
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
)

/**
 * What a step asks of the player. READ: text only, tap Next. LISTEN: the app plays the phrase, nothing to do.
 * PLAY: wait mode with note names and fingers shown. FROM_MEMORY: wait mode with them hidden.
 * PERFORM: the notes keep moving; the step passes at the exercise's pass mark.
 */
enum class StepKind { READ, LISTEN, PLAY, FROM_MEMORY, PERFORM }

data class Step(val kind: StepKind, val text: String, val exercise: Exercise? = null) {
    val mode get() = when (kind) { StepKind.LISTEN -> Mode.LISTEN; StepKind.PERFORM -> Mode.PERFORM; else -> Mode.WAIT }
    /** Note names and finger numbers are shown on the notes and keys. */
    val labels get() = kind != StepKind.FROM_MEMORY

    /** The step's exercise reached its end with [recap] (null outside Perform mode): is the step complete? */
    fun passed(recap: Recap?): Boolean = when (kind) {
        StepKind.READ -> true
        StepKind.PERFORM -> recap != null && recap.accuracy >= (exercise?.pass ?: 0)
        else -> true
    }
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

fun Exercise.toSong(id: String, title: String): Song {
    val msPerBeat = 60_000 / bpm
    val notes = (parseLine(right, Hand.RIGHT, msPerBeat) + parseLine(left, Hand.LEFT, msPerBeat))
        .sortedWith(compareBy({ it.startMs }, { it.midi }))
    val end = notes.maxOfOrNull { it.startMs + it.durationMs } ?: 0L
    return Song("lesson-$id", title, bpm, beats to 4, notes, groupOnsets(notes), emptyList(), end + 400, preferFlats = flats,
        fingering = fingers.split(" ").mapNotNull { it.toIntOrNull() }.takeIf { it.isNotEmpty() })
}


private fun lesson(id: String, title: String, summary: String, vararg steps: Step, song: String? = null) =
    CourseLesson(id, title, summary, steps.toList(), song)
private fun read(vararg text: String) = Step(StepKind.READ, text.joinToString("\n\n"))
private fun listen(text: String, ex: Exercise) = Step(StepKind.LISTEN, text, ex)
private fun play(text: String, ex: Exercise) = Step(StepKind.PLAY, text, ex)
private fun memory(text: String, ex: Exercise) = Step(StepKind.FROM_MEMORY, text, ex)
private fun perform(text: String, ex: Exercise) = Step(StepKind.PERFORM, text, ex)

private const val LISTEN_FIRST = "Listen first. Watch the notes fall and hear how it goes."
private const val YOUR_TURN = "Your turn. The notes wait for you, with letters and finger numbers to guide you."
private const val FROM_MEMORY = "Once more, from memory. The letters and finger numbers are hidden now."

// Opening phrases of the library songs, so song lessons can be heard and tried before the whole piece.
private val TWINKLE = Exercise(right = "C4:1 C4:1 G4:1 G4:1 A4:1 A4:1 G4:2 F4:1 F4:1 E4:1 E4:1 D4:1 D4:1 C4:2",
    left = "C3:4 F3:2 C3:2 F3:2 C3:2 G2:2 C3:2", bpm = 80.0)
private val MINUET = Exercise(right = "D5:1 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:1 G4:1 G4:1 E5:1 C5:0.5 D5:0.5 E5:0.5 F#5:0.5 G5:1 G4:1 G4:1",
    left = "G3+B3:3 A3:3 B3:3 C4:3", bpm = 96.0, practice = Practice.RIGHT, beats = 3)
private val ELISE = Exercise(right = "E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1.5 r:0.5 C4:0.5 E4:0.5 A4:0.5 B4:1.5 r:0.5 E4:0.5 G#4:0.5 B4:0.5 C5:2",
    bpm = 72.0, practice = Practice.RIGHT)

/**
 * A rhythm lesson: read how to count it, hear it on one key, tap it on middle C in time (Perform, 80%), then the
 * same rhythm as a short tune. Beat lines are on so each beat shows on the lane.
 */
private fun rhythmLesson(id: String, title: String, summary: String, read: String, rhythm: String, tune: String,
                         bpm: Double, beats: Int = 4, count: String): CourseLesson {
    val one = Exercise(right = rhythm, bpm = bpm, practice = Practice.RIGHT, beats = beats, beatLines = true)
    val melody = one.copy(right = tune)
    return lesson(id, title, summary,
        read(read),
        listen("Listen and count along: $count.", one),
        perform("Now tap it on middle C with your thumb, counting out loud. Two bars of clicks count you in. Score 80 percent to pass.", one),
        perform("The same rhythm as a little tune. Keep counting. Score 80 percent to pass.", melody))
}

private val RHYTHM = listOf(
    rhythmLesson("rhythm-quarters", "Steady quarter notes", "One note on every beat, counted in.",
        "A quarter note lasts one beat. Before you play, the app counts you in with two bars of clicks, and the numbers 1, 2, 3, 4 appear above the keys. Start on the next 1.",
        rhythm = "C4:1 C4:1 C4:1 C4:1 C4:1 C4:1 C4:1 C4:1",
        tune = "C4:1 E4:1 G4:1 E4:1 F4:1 D4:1 E4:1 C4:1", bpm = 72.0, count = "1, 2, 3, 4"),
    rhythmLesson("rhythm-halves", "Half and whole notes", "Hold through two beats and four.",
        "A half note lasts two beats and a whole note four. Keep counting while the key stays down, and let it go only on the next note.",
        rhythm = "C4:2 C4:2 C4:4 C4:1 C4:1 C4:2 C4:4",
        tune = "E4:2 D4:2 C4:4 E4:1 F4:1 G4:2 E4:4", bpm = 76.0, count = "1, 2, 3, 4"),
    rhythmLesson("rhythm-eighths", "Eighth notes", "Two notes in one beat: 1 and 2 and.",
        "An eighth note is half a beat. Count the beats with an and between them: 1 and 2 and 3 and 4 and. The second eighth falls on the and.",
        rhythm = "C4:1 C4:0.5 C4:0.5 C4:1 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:0.5 C4:2",
        tune = "C4:1 D4:0.5 E4:0.5 F4:1 E4:0.5 D4:0.5 C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:2", bpm = 66.0, count = "1 and 2 and 3 and 4 and"),
    rhythmLesson("rhythm-dotted", "Dotted quarter and eighth", "Long, short: the lilt of many tunes.",
        "A dot makes a note half as long again: a dotted quarter lasts one and a half beats. An eighth note fills the rest of the second beat. Count 1 and 2 and, and play on 1 and on the and of 2.",
        rhythm = "C4:1.5 C4:0.5 C4:1 C4:1 C4:1.5 C4:0.5 C4:2",
        tune = "E4:1.5 D4:0.5 C4:1 D4:1 E4:1.5 E4:0.5 E4:2", bpm = 72.0, count = "1 and 2 and 3 and 4 and"),
    rhythmLesson("rhythm-waltz", "Three beats: waltz time", "Count 1, 2, 3 with a lean on 1.",
        "In three four time each bar has three beats. Lean a little on beat 1: ONE, two, three. The count-in clicks three to a bar.",
        rhythm = "C4:1 C4:1 C4:1 C4:2 C4:1 C4:1 C4:1 C4:1 C4:3",
        tune = "C4:1 E4:1 G4:1 G4:2 E4:1 F4:1 D4:1 B3:1 C4:3", bpm = 84.0, beats = 3, count = "1, 2, 3"),
    rhythmLesson("rhythm-syncopation", "Simple syncopation", "Notes that land between the beats.",
        "Syncopation puts a note on the and and holds it over the next beat. Keep counting 1 and 2 and evenly; the note comes early, and the beat goes by while you hold it.",
        rhythm = "C4:1 C4:0.5 C4:1 C4:0.5 C4:1 C4:0.5 C4:1 C4:0.5 C4:2",
        tune = "C4:1 D4:0.5 E4:1 D4:0.5 C4:1 E4:0.5 G4:1 E4:0.5 C4:2", bpm = 66.0, count = "1 and 2 and 3 and 4 and"),
)

/**
 * A key lesson: read where the black keys are and how the hand sits, play the scale (one octave up and down,
 * right hand), its three main chords, the scale from memory, then a four-bar tune in the key for a score.
 */
private fun keyLesson(id: String, title: String, summary: String, read: List<String>, scale: String, chords: String, chordNames: String,
                      tune: String, flats: Boolean = false, bpm: Double = 76.0, fingers: String = STANDARD_SCALE): CourseLesson {
    val s = Exercise(right = scale, bpm = bpm, practice = Practice.RIGHT, flats = flats, fingers = fingers)
    return lesson(id, title, summary,
        read(*read.toTypedArray()),
        play("Play the scale up and back down. Watch the finger numbers where the thumb tucks under.", s),
        play("The three main chords of the key: $chordNames. Press each chord's three keys together.",
            Exercise(right = chords, bpm = 60.0, practice = Practice.RIGHT, flats = flats)),
        memory("The scale once more, from memory.", s),
        perform("A short tune in the key, in time. Score 80 percent to pass.", Exercise(right = tune, bpm = 80.0, practice = Practice.RIGHT, flats = flats)))
}

/** Right-hand fingering for a one-octave scale up and down that starts on a white key with the thumb. */
private const val STANDARD_SCALE = "1 2 3 1 2 3 4 5 4 3 2 1 3 2 1"

/** A left-hand pattern: read it, hear both hands, the left hand alone, then both hands under a four-bar tune. */
private fun leftHandLesson(id: String, title: String, summary: String, read: String, left: String, tune: String,
                           bpm: Double = 72.0, beats: Int = 4): CourseLesson {
    val both = Exercise(right = tune, left = left, bpm = bpm, beats = beats)
    return lesson(id, title, summary,
        read(read),
        listen("Listen to the pattern under the tune.", both),
        play("The left hand alone. The notes wait for you.", Exercise(left = left, bpm = bpm, practice = Practice.LEFT, beats = beats)),
        play("Now both hands: the tune on top, the pattern underneath.", both))
}

private val LEFT_HAND_PATTERNS = listOf(
    leftHandLesson("lh-waltz", "Waltz bass", "Root, then the chord twice: oom, pah, pah.",
        "In three four time the left hand plays the chord's root on beat 1, then the other two notes together on beats 2 and 3. Keep beats 2 and 3 light, so the bass note sings.",
        left = "C3:1 E3+G3:1 E3+G3:1 F2:1 A2+C3:1 A2+C3:1 G2:1 B2+D3:1 B2+D3:1 C3:1 E3+G3:1 E3+G3:1",
        tune = "E4:2 G4:1 F4:2 A4:1 D4:2 F4:1 C4:3", bpm = 84.0, beats = 3),
    leftHandLesson("lh-pop", "Root, fifth, octave", "The pop bass that walks up each chord.",
        "Many pop songs carry the left hand up through each chord: the root, the fifth above it, then the root an octave higher. Your hand opens wide, from finger 5 to finger 1.",
        left = "C3:1 G3:1 C4:2 F2:1 C3:1 F3:2 G2:1 D3:1 G3:2 C3:1 G3:1 C4:2",
        tune = "E4:1 G4:1 C5:2 A4:1 F4:1 A4:2 B4:1 G4:1 D5:2 C5:4"),
    leftHandLesson("lh-octaves", "Octaves", "The same note in two places, little finger and thumb.",
        "An octave is the same note eight white keys apart. Stretch the left hand from finger 5 to the thumb and keep that shape as you move from chord to chord. Drop the hand from the wrist.",
        left = "C2+C3:2 C2+C3:2 F2+F3:2 F2+F3:2 G2+G3:2 G2+G3:2 C2+C3:4",
        tune = "G4:2 E4:2 A4:2 F4:2 G4:1 F4:1 D4:2 C4:4"),
    leftHandLesson("lh-tenths", "Broken tenths", "Root, fifth, then the third an octave up.",
        "A tenth is a third plus an octave: too wide to play at once for most hands, so it is broken. Play the root, the fifth, then the chord's third above them, rising.",
        left = "C3:1 G3:1 E4:2 F2:1 C3:1 A3:2 G2:1 D3:1 B3:2 C3:1 G3:1 E4:2",
        tune = "G4:2 C5:2 A4:2 C5:2 B4:2 D5:2 C5:4"),
)

private val KEYS = listOf(
    keyLesson("key-g", "G major", "One sharp: F sharp.",
        listOf("G major uses the white keys except F: every F becomes F sharp, the black key just right of F.",
            "Put your right thumb on G. Play G, A, B with fingers 1, 2, 3, tuck the thumb under to C, then 1, 2, 3, 4, 5 up to G. Finger 4 lands on F sharp."),
        scale = "G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 F#5:1 G5:1 F#5:1 E5:1 D5:1 C5:1 B4:1 A4:1 G4:2",
        chords = "G4+B4+D5:2 G4+C5+E5:2 F#4+A4+D5:2 G4+B4+D5:2", chordNames = "G, C and D, then G again",
        tune = "G4:1 A4:1 B4:1 G4:1 D5:2 B4:2 C5:1 B4:1 A4:1 F#4:1 G4:4"),
    keyLesson("key-f", "F major", "One flat: B flat.",
        listOf("F major uses the white keys except B: every B becomes B flat, the black key just left of B.",
            "Put your right thumb on F. Play F, G, A, B flat with fingers 1, 2, 3, 4, tuck the thumb under to C, then 1, 2, 3, 4 up to F. Finger 4 plays B flat."),
        scale = "F4:1 G4:1 A4:1 Bb4:1 C5:1 D5:1 E5:1 F5:1 E5:1 D5:1 C5:1 Bb4:1 A4:1 G4:1 F4:2",
        chords = "F4+A4+C5:2 F4+Bb4+D5:2 E4+G4+C5:2 F4+A4+C5:2", chordNames = "F, B flat and C, then F again",
        tune = "F4:1 G4:1 A4:1 F4:1 C5:2 A4:2 Bb4:1 A4:1 G4:1 E4:1 F4:4", flats = true,
        fingers = "1 2 3 4 1 2 3 4 3 2 1 4 3 2 1"),
    keyLesson("key-d", "D major", "Two sharps: F sharp and C sharp.",
        listOf("D major has two sharps: F sharp and C sharp. Both are the black keys just right of F and C.",
            "Right thumb on D. Fingers 1, 2, 3 for D, E, F sharp, thumb under to G, then 1, 2, 3, 4, 5 up to D. Finger 3 plays F sharp and finger 4 plays C sharp."),
        scale = "D4:1 E4:1 F#4:1 G4:1 A4:1 B4:1 C#5:1 D5:1 C#5:1 B4:1 A4:1 G4:1 F#4:1 E4:1 D4:2",
        chords = "D4+F#4+A4:2 D4+G4+B4:2 C#4+E4+A4:2 D4+F#4+A4:2", chordNames = "D, G and A, then D again",
        tune = "D4:1 F#4:1 A4:1 F#4:1 G4:1 E4:1 C#4:2 D4:1 E4:1 F#4:1 G4:1 A4:2 D4:2"),
    keyLesson("key-bb", "B flat major", "Two flats: B flat and E flat.",
        listOf("B flat major has two flats: B flat and E flat, the black keys just left of B and E.",
            "This scale starts on a black key, so the thumb does not. Finger 4 plays B flat, then 1, 2, 3 for C, D, E flat, the thumb tucks under to F, and 1, 2, 3, 4 carry on to B flat."),
        scale = "Bb3:1 C4:1 D4:1 Eb4:1 F4:1 G4:1 A4:1 Bb4:1 A4:1 G4:1 F4:1 Eb4:1 D4:1 C4:1 Bb3:2",
        chords = "Bb3+D4+F4:2 Bb3+Eb4+G4:2 A3+C4+F4:2 Bb3+D4+F4:2", chordNames = "B flat, E flat and F, then B flat again",
        tune = "Bb3:1 D4:1 F4:1 D4:1 Eb4:2 C4:2 D4:1 Eb4:1 F4:1 A3:1 Bb3:4", flats = true,
        fingers = "4 1 2 3 1 2 3 4 3 2 1 3 2 1 4"),
    keyLesson("key-em", "E minor", "The minor key with one sharp.",
        listOf("E minor shares its notes with G major: all white keys except F, which becomes F sharp. It starts and ends on E, which gives it a darker sound.",
            "Right thumb on E. Fingers 1, 2, 3 for E, F sharp, G, thumb under to A, then 1, 2, 3, 4, 5 up to E."),
        scale = "E4:1 F#4:1 G4:1 A4:1 B4:1 C5:1 D5:1 E5:1 D5:1 C5:1 B4:1 A4:1 G4:1 F#4:1 E4:2",
        chords = "E4+G4+B4:2 E4+A4+C5:2 D4+F#4+B4:2 E4+G4+B4:2", chordNames = "E minor, A minor and B minor, then E minor again",
        tune = "E4:1 G4:1 B4:1 G4:1 A4:1 G4:1 F#4:2 E4:1 F#4:1 G4:1 A4:1 B4:2 E4:2"),
    keyLesson("key-dm", "D minor", "The minor key with one flat.",
        listOf("D minor shares its notes with F major: all white keys except B, which becomes B flat.",
            "Right thumb on D. Fingers 1, 2, 3 for D, E, F, thumb under to G, then 1, 2, 3, 4, 5 up to D. Finger 3 plays B flat."),
        scale = "D4:1 E4:1 F4:1 G4:1 A4:1 Bb4:1 C5:1 D5:1 C5:1 Bb4:1 A4:1 G4:1 F4:1 E4:1 D4:2",
        chords = "D4+F4+A4:2 D4+G4+Bb4:2 C4+E4+A4:2 D4+F4+A4:2", chordNames = "D minor, G minor and A minor, then D minor again",
        tune = "D4:1 F4:1 A4:2 Bb4:1 A4:1 G4:2 F4:1 E4:1 D4:1 E4:1 D4:4", flats = true),
)

val COURSE: List<CourseLevel> = listOf(
    CourseLevel("basics", "Basics", "Where to sit, where the notes are, and which finger is which.", listOf(
        run {
            val e = Exercise(right = "C4:1 r:1 C4:1 r:1 C4:1 r:1 C4:2", bpm = 60.0, practice = Practice.RIGHT)
            lesson("sit", "Sit, then shape your hand", "Posture and a relaxed, curved hand.",
                read("Sit on the front half of the bench, far enough back that your elbows are a little in front of your body. Your forearms should be level with the keys.",
                    "Let your hand hang loose, then rest it on the keys as if holding a small ball: fingers curved, playing on their tips, thumb resting on its side."),
                listen("Listen to middle C, four times, with a rest between each one.", e),
                play("Play middle C with your right thumb. Let the key come back up between notes.", e))
        },
        run {
            val e = Exercise(right = "r:4 C4:2 C5:2 C6:2", left = "C2:2 C3:2 r:6", bpm = 60.0)
            lesson("find-c", "Find C anywhere", "The black keys show you where every C is.",
                read("Black keys come in groups of two and three. Every C is the white key just left of a group of two.",
                    "Middle C is the C closest to the middle of your piano. Notes are named by letter and by octave: middle C is C4, the next C up is C5."),
                listen("Listen to every C from low to high.", e),
                play("Now play every C from low to high. Use your left hand for the low ones and your right hand for the high ones.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("fingers-right", "Finger numbers", "Thumb is 1, little finger is 5.",
                read("Both hands number their fingers the same way: thumb 1, index 2, middle 3, ring 4, little finger 5.",
                    "Put your right thumb on C and let each finger sit on its own white key, up to G. This is the C position."),
                listen("Listen to five fingers walking up and back down.", e),
                play("Now walk up and back down. The small number on each falling note is the finger to use.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)
            lesson("fingers-left", "The left hand mirrors it", "Little finger on C, thumb on G.",
                read("In the left hand the thumb sits highest. Put your left little finger, finger 5, on the C below middle C, and your thumb on G."),
                listen(LISTEN_FIRST, e),
                play("Walk up and back down with your left hand, one finger per key.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 C4:1 C4:1 C4:1 G4:1 G4:1 G4:1 G4:1 C4:4", bpm = 66.0, practice = Practice.RIGHT)
            lesson("beat", "Keep a steady beat", "Play exactly when the note reaches the line.",
                read("Music moves on a steady beat. Count 1, 2, 3, 4 out loud as the notes fall, and play as each one touches the line."),
                listen("Listen and count along: 1, 2, 3, 4.", e),
                play("Play it once with the notes waiting for you, still counting out loud.", e),
                perform("Now in time. The notes keep moving and you get a score for timing. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", bpm = 66.0, practice = Practice.RIGHT)
            lesson("white-keys", "Name the white keys", "The musical alphabet: A to G, over and over.",
                read("White keys use the letters A to G, then start again. From C they go C, D, E, F, G, A, B, then C again."),
                listen("Listen to one octave, C up to C. Watch the letters on the notes.", e),
                play("Now play up one octave. When you run out of fingers, your thumb tucks under to reach F.", e))
        },
    )),
    CourseLevel("beginner", "Beginner", "First melodies, note lengths, and both hands together.", listOf(
        run {
            val e = Exercise(right = "E4:1 E4:1 F4:1 G4:1 G4:1 F4:1 E4:1 D4:1 C4:1 C4:1 D4:1 E4:1 E4:1.5 D4:0.5 D4:2", bpm = 90.0, practice = Practice.RIGHT)
            lesson("first-melody", "Your first melody", "Ode to Joy, in the C position.",
                read("Keep your right hand in the C position: thumb on C, little finger on G. The whole tune fits under your fingers without moving the hand."),
                listen("Listen to the tune. You probably know it already.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e),
                perform("Now in time, at full speed. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 E4:1 G4:2 G4:2 E4:2 C4:4", bpm = 70.0, practice = Practice.RIGHT)
            lesson("lengths", "Long and short notes", "Quarter, half and whole notes.",
                read("A quarter note lasts one beat, a half note two, a whole note four.",
                    "Longer blocks on the screen are longer notes: keep the key down until the block has passed the line."),
                listen("Listen to short and long notes. Count the beats as each one rings.", e),
                play(YOUR_TURN, e),
                perform("Now in time. Hold each note for its full length. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(left = "C3:4 F3:4 G3:4 C3:4", bpm = 70.0, practice = Practice.LEFT)
            lesson("bass", "Bass notes in the left hand", "Holding one low note under the music.",
                read("Many songs need only one left-hand note at a time: the bass. Play it and hold it for the whole bar.",
                    "These are C, F, G and back to C: the three most common bass notes in the key of C."),
                listen(LISTEN_FIRST, e),
                play("Play the four bass notes with your left hand and hold each one.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = TWINKLE
            lesson("together", "Hands together", "Melody on top, bass underneath.",
                read("Practise each hand alone first, then together. Look ahead: the left hand changes only every two beats."),
                listen("Listen to both hands together.", e),
                play("Right hand alone first. The app plays the left hand for you.", e.copy(practice = Practice.RIGHT)),
                play("Now both hands. The notes wait until both hands have played.", e),
                memory("Both hands once more, from memory.", e))
        },
        run {
            val e = Exercise(right = "D4:1 E4:1 F#4:1 G4:1 A4:1 G4:1 F#4:1 E4:1 D4:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("black-keys", "Sharps and flats", "Black keys sit between the letters.",
                read("A sharp is the next key up, a flat the next key down. The black key between F and G is F sharp, also called G flat.",
                    "This five-finger position starts on D and needs F sharp under your third finger."),
                listen("Listen for the F sharp, the black key under your third finger.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 r:1 E4:1 r:1 G4:1 r:1 C5:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("rests", "Rests", "Silence is part of the rhythm.",
                read("A rest is a beat with no note. Keep counting through it, and lift your finger so the note stops."),
                listen("Listen to the silences between the notes.", e),
                play(YOUR_TURN, e),
                perform("Now in time. Keep counting through the rests. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:1 B4:1 A4:1 G4:1 F4:1 E4:1 D4:1 C4:2", bpm = 70.0, practice = Practice.RIGHT)
            lesson("scale", "Thumb under: the C major scale", "Eight notes with five fingers.",
                read("Play C, D, E with fingers 1, 2, 3, then tuck the thumb under to F and carry on with 1, 2, 3, 4, 5. On the way down, finger 3 crosses over the thumb.",
                    "Keep the wrist level and let the thumb glide under; the hand should not jump."),
                listen("Listen to the scale up and down.", e),
                play("Your turn. Watch the finger numbers where the thumb tucks under.", e),
                memory(FROM_MEMORY, e))
        },
        lesson("first-song", "Your first whole song", "Twinkle Twinkle, hands together.",
            read("Learn a song in three passes: right hand alone, left hand alone, then both. Use wait mode until it feels easy, then Perform mode for a score."),
            listen("Here is how Twinkle Twinkle begins, both hands.", TWINKLE),
            play("Try the opening with your right hand. The app plays the left hand.", TWINKLE.copy(practice = Practice.RIGHT)),
            read("Now open the whole song. Slow it down with the speed button if the notes come too fast."),
            song = "twinkle"),
    )),
    CourseLevel("rhythm", "Rhythm", "Counting: quarters, halves, eighths, dotted notes, waltz time and syncopation.", RHYTHM),
    CourseLevel("intermediate", "Intermediate", "Scales in both hands, chords, inversions and progressions.", listOf(
        run {
            val e = Exercise(left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:1 B3:1 A3:1 G3:1 F3:1 E3:1 D3:1 C3:2", bpm = 70.0, practice = Practice.LEFT)
            lesson("scale-left", "The scale in the left hand", "5, 4, 3, 2, 1, then 3 crosses over.",
                read("Going up, the left hand plays C to G with fingers 5 to 1, then finger 3 crosses over the thumb to A."),
                listen(LISTEN_FIRST, e),
                play("Your turn. Watch for finger 3 crossing over the thumb.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 B4:1 C5:2", left = "C3:1 D3:1 E3:1 F3:1 G3:1 A3:1 B3:1 C4:2", bpm = 60.0)
            lesson("scale-both", "Scales hands together", "Two hands, an octave apart.",
                read("The hands cross their thumbs at different moments, which is the whole challenge. Go slowly enough that every note lines up."),
                listen("Listen to both hands, an octave apart.", e),
                play("Your turn, both hands. The notes wait until both have played.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:2 F4+A4+C5:2 G4+B4+D5:2 C4+E4+G4:2", bpm = 60.0, practice = Practice.RIGHT)
            lesson("triads", "Triads", "Three notes, every other white key.",
                read("A triad stacks three notes a skip apart: C, E, G is C major. Fingers 1, 3 and 5 play it."),
                listen("Listen to C, F, G and C again.", e),
                play("Your turn. Press the three keys at the same moment.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:2 E4+G4+C5:2 G4+C5+E5:2 C5+E5+G5:2", bpm = 60.0, practice = Practice.RIGHT)
            lesson("inversions", "Inversions", "The same chord, a different note on the bottom.",
                read("Move the lowest note of a chord up an octave and you get an inversion. C, E, G becomes E, G, C, then G, C, E.",
                    "Inversions let the hand move less from chord to chord. The Chords tab has every chord in every inversion."),
                listen("Listen: the same C major chord climbing through its inversions.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:2", bpm = 70.0, practice = Practice.LEFT)
            lesson("broken", "Broken chords", "The left hand plays a chord one note at a time.",
                read("This pattern is called Alberti bass: bottom, top, middle, top. It keeps the music moving under a melody."),
                listen(LISTEN_FIRST, e),
                play("Your turn, left hand. Bottom, top, middle, top.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4+E4+G4:4 C4+F4+A4:4 B3+D4+G4:4 C4+E4+G4:4", left = "C3:4 F2:4 G2:4 C3:4", bpm = 70.0)
            lesson("progression", "I, IV, V, I", "The progression behind thousands of songs.",
                read("In the key of C, the I chord is C, IV is F and V is G. The right hand moves to nearby inversions while the left hand plays the root."),
                listen("Listen to the four chords.", e),
                play("Your turn, both hands.", e),
                memory(FROM_MEMORY, e),
                perform("Now in time. Change chords right on the beat. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "A3:1 B3:1 C4:1 D4:1 E4:1 F4:1 G4:1 A4:1 A3+C4+E4:4", bpm = 66.0, practice = Practice.RIGHT)
            lesson("minor", "Minor keys", "Same white keys, starting on A.",
                read("A minor uses the same white keys as C major but starts on A, which gives it a darker sound."),
                listen("Listen to the A minor scale, then the A minor chord.", e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        lesson("expression", "Playing with expression", "Loud and soft, smooth and detached.",
            read("The mic and MIDI hear which notes you play, not how. That part is yours: play softly at the start, let the melody sing over the left hand, and connect notes smoothly unless they have a gap.",
                "On a digital piano the right pedal holds notes after you lift your fingers. Change it when the harmony changes, or the sound blurs."),
            listen("Here is how the Minuet in G begins. Listen to how the melody moves.", MINUET),
            play("Try the melody with your right hand. Make it sing.", MINUET),
            read("Now open the whole Minuet in G and play it with expression."),
            song = "minuet-g"),
    ) + LEFT_HAND_PATTERNS),
    CourseLevel("keys", "Keys", "Major and minor keys with sharps and flats: scales, their three main chords, and a tune in each.", KEYS),
    CourseLevel("advanced", "Advanced", "Longer scales, seventh chords, arpeggios and real repertoire.", listOf(
        run {
            val e = Exercise(right = "C4:0.5 D4:0.5 E4:0.5 F4:0.5 G4:0.5 A4:0.5 B4:0.5 C5:0.5 D5:0.5 E5:0.5 F5:0.5 G5:0.5 A5:0.5 B5:0.5 C6:2",
                bpm = 72.0, practice = Practice.RIGHT)
            lesson("scale-two", "Two-octave scales", "Thumb under twice.",
                read("Two octaves use the fingering 1 2 3, 1 2 3 4, 1 2 3, 1 2 3 4 5. Keep the tempo even across the thumb crossings."),
                listen(LISTEN_FIRST, e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e),
                perform("Now in time, evenly. Score 80 percent to pass.", e))
        },
        run {
            val e = Exercise(right = "D4+F4+A4+C5:4 B3+D4+F4+G4:4 C4+E4+G4+B4:4", left = "D3:4 G2:4 C3:4", bpm = 60.0)
            lesson("sevenths", "Seventh chords", "Four notes: the colour of jazz.",
                read("Add one more skip to a triad and you get a seventh chord. C major 7 is C, E, G, B; D minor 7 is D, F, A, C; G7 is G, B, D, F.",
                    "This is the two, five, one progression. The Chords tab has it in five keys."),
                listen("Listen to D minor 7, G7, and C major 7.", e),
                play("Your turn, both hands.", e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "C4:0.5 E4:0.5 G4:0.5 C5:0.5 E5:0.5 G5:0.5 C6:1 G5:0.5 E5:0.5 C5:0.5 G4:0.5 E4:0.5 C4:2", bpm = 66.0, practice = Practice.RIGHT)
            lesson("arpeggios", "Arpeggios", "A chord spread over two octaves.",
                read("Play the notes of a chord one at a time, up and down. The thumb tucks under after G."),
                listen(LISTEN_FIRST, e),
                play(YOUR_TURN, e),
                memory(FROM_MEMORY, e))
        },
        run {
            val e = Exercise(right = "E4:2 F4:1 G4:1 G4:2 F4:1 E4:1 D4:2 C4:2",
                left = "C3:0.5 G3:0.5 E3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5 B2:0.5 G3:0.5 D3:0.5 G3:0.5 C3:0.5 G3:0.5 E3:0.5 G3:0.5",
                bpm = 72.0)
            lesson("independence", "Hand independence", "A melody over a moving left hand.",
                read("The right hand plays the tune while the left keeps a broken-chord pattern going. Learn the left hand until it runs on its own, then add the melody."),
                listen("Listen to both hands together.", e),
                play("Left hand alone first. The app plays the melody.", e.copy(practice = Practice.LEFT)),
                play("Now both hands.", e),
                memory("Both hands once more, from memory.", e))
        },
        lesson("repertoire", "Learning a real piece", "Für Elise, section by section.",
            read("Big pieces are learned in small loops. Use the Loop button to repeat four bars, start at a slow speed, and play each hand alone before putting them together."),
            listen("Here is how Für Elise begins, right hand.", ELISE),
            play(YOUR_TURN, ELISE),
            memory(FROM_MEMORY, ELISE),
            read("Now open the whole piece. When a section is reliable in wait mode, play it in Perform mode at full speed."),
            song = "fur-elise"),
    )),
)
