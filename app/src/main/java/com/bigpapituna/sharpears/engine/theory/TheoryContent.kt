package com.bigpapituna.sharpears.engine.theory

import com.bigpapituna.sharpears.engine.theory.NoteValue.EIGHTH
import com.bigpapituna.sharpears.engine.theory.NoteValue.HALF
import com.bigpapituna.sharpears.engine.theory.NoteValue.QUARTER
import com.bigpapituna.sharpears.engine.theory.NoteValue.SIXTEENTH
import com.bigpapituna.sharpears.engine.theory.NoteValue.WHOLE

/*
 * Every word of the Theory Academy. Captions are what the pup says (shown in his bubble and, with the voice on,
 * spoken; [Scene.say] when the spoken form differs). House rules: sentence case, no dashes, note names in words
 * in captions ("F sharp"), symbols only on the pictures.
 */

// ---- Little builders ---------------------------------------------------------------------------------------------

private fun m(s: String) = p(s).midi
private fun ms(s: String) = s.split(' ').filter { it.isNotBlank() }.map(::m)

private fun kf(s: String, label: String? = null, gap: String? = null, tint: Tint = Tint.ACCENT, names: Map<String, String> = emptyMap(),
               clear: Boolean = false, play: Boolean = true) = KeyFrame(ms(s), label, gap, play, tint, names.mapKeys { m(it.key) }, clear)

/** One key per frame, up a run of notes, with optional step labels between them. */
private fun run(s: String, gaps: List<String> = emptyList(), tints: Map<Int, Tint> = emptyMap(), labels: Map<Int, String> = emptyMap(), clearFirst: Boolean = false) =
    ms(s).mapIndexed { i, n -> KeyFrame(listOf(n), labels[i], if (i == 0) null else gaps.getOrNull(i - 1), true, tints[i] ?: Tint.ACCENT, clear = i == 0 && clearFirst) }

private fun keys(low: String, high: String, frames: List<KeyFrame>, stepMs: Int = 700, names: KeyNames = KeyNames.LIT, flats: Boolean = false, finale: String = "") =
    KeysVisual(m(low), m(high), frames, stepMs, names, flats, ms(finale))

/** A scale up the keyboard: every note lit in turn, with W and H between them when [gaps]; altered notes in blue. */
private fun scaleFrames(tonic: String, kind: ScaleKind = ScaleKind.MAJOR, gaps: Boolean = true, clearFirst: Boolean = false, label: String? = null): List<KeyFrame> {
    val s = scale(p(tonic), kind)
    val steps = stepLabels(kind)
    return s.mapIndexed { i, pt ->
        KeyFrame(listOf(pt.midi), if (i == s.lastIndex) label else null, if (gaps && i > 0) steps[i - 1] else null,
            tint = if (pt.accidental != 0) Tint.BLUE else Tint.ACCENT, names = if (pt.accidental != 0) mapOf(pt.midi to pt.label) else emptyMap(),
            clear = i == 0 && clearFirst)
    }
}

private fun sn(s: String, v: NoteValue = QUARTER, label: String? = null, dotted: Boolean = false, tie: Boolean = false, staff: Clef? = null) =
    StaffNote(s.split('+').map(::p), v, dotted, false, tie, label, staff)
private fun rest(v: NoteValue, label: String? = null, dotted: Boolean = false) = StaffNote(emptyList(), v, dotted, rest = true, label = label)
private fun row(s: String, v: NoteValue = QUARTER, names: Boolean = false) = s.split(' ').map { sn(it, v, if (names) p(it.substringBefore('+')).letterName else null) }

private fun treble(notes: List<StaffNote> = emptyList(), drawClef: Boolean = true, keyFifths: Int = 0, time: Pair<Int, Int>? = null, marks: Marks = Marks.NONE,
                   bpm: Int = 0, stepMs: Int = 750, names: Boolean = false, barlines: List<Int> = emptyList()) =
    StaffVisual(Clef.TREBLE, notes, drawClef, keyFifths, time, marks, bpm, stepMs, false, true, names, barlines)
private fun bass(notes: List<StaffNote> = emptyList(), drawClef: Boolean = true, marks: Marks = Marks.NONE, stepMs: Int = 750, names: Boolean = false) =
    StaffVisual(Clef.BASS, notes, drawClef, marks = marks, stepMs = stepMs, names = names)
private fun grand(notes: List<StaffNote>, stepMs: Int = 700, names: Boolean = false) = StaffVisual(Clef.TREBLE, notes, true, grand = true, stepMs = stepMs, names = names)

/** A still note on the staff for a quiz picture. */
private fun still(clef: Clef, s: String, v: NoteValue = WHOLE, keyFifths: Int = 0, dotted: Boolean = false) =
    StaffVisual(clef, listOf(sn(s, v, dotted = dotted)), keyFifths = keyFifths, play = false)
private fun stillRest(v: NoteValue) = StaffVisual(Clef.TREBLE, listOf(rest(v)), play = false)
private fun stillKeys(lit: String, low: String = "C4", high: String = "C5", names: KeyNames = KeyNames.NONE) =
    KeysVisual(m(low), m(high), listOf(KeyFrame(ms(lit), play = false)), names = names)

private fun pair(a: String, b: String, note: String? = null, name: String? = null) = IntervalPair(p(a), p(b), name ?: interval(p(a), p(b)).name, note)
private fun intervals(low: String, high: String, vararg pairs: IntervalPair, count: Boolean = true) = IntervalVisual(m(low), m(high), pairs.toList(), count)

private fun chord(s: String, name: String, numeral: String? = null, build: Boolean = true, tags: List<String> = emptyList()) =
    ChordStep(s.split(' ').map(::p), name, numeral, build, tags)
private fun chords(vararg c: ChordStep, low: String = "G3", high: String = "C6", noteMs: Int = 700, holdMs: Int = 1500) =
    ChordVisual(c.toList(), m(low), m(high), noteMs, holdMs)

/** "0:1 1:1 2:2" as start:length in beats; an "r" before a start makes it a rest. */
private fun hits(spec: String) = spec.split(' ').map { t ->
    val rest = t.startsWith("r")
    val (a, b) = t.removePrefix("r").split(':')
    Hit(a.toFloat(), b.toFloat(), rest)
}
private fun even(n: Int, len: Float) = (0 until n).map { Hit(it * len, len) }

private fun counts(sub: Int, beats: Int): List<String> = (1..beats).flatMap { b ->
    when (sub) { 1 -> listOf("$b"); 2 -> listOf("$b", "&"); else -> listOf("$b", "e", "&", "a") }
}

private fun scene(caption: String, visual: Visual, mood: Mood = Mood.EXPLAIN, say: String = caption) = Scene(caption, visual, mood, say)
private fun lesson(id: String, title: String, summary: String, scenes: List<Scene>, quiz: List<Question>) = TheoryLesson(id, title, summary, scenes, quiz)

private fun choice(prompt: String, options: List<String>, answer: Int, explain: String, show: Visual? = null) = Choice(prompt, options, answer, explain, show)
private fun play(prompt: String, target: String, explain: String, low: String = "C4", high: String = "C5", anyOctave: Boolean = true, show: Visual? = null, flats: Boolean = false) =
    PlayIt(prompt, m(target), explain, anyOctave, m(low), m(high), show, flats)
private fun listen(prompt: String, clips: List<String>, options: List<String>, answer: Int, explain: String, gapMs: Int = 750) =
    Listen(prompt, clips.map { c -> c.split('+').map(::m) }, options, answer, explain, gapMs)
private fun scaleClips(tonic: String, kind: ScaleKind) = scale(p(tonic), kind).map { it.code }

// ---- Unit 1: the keyboard ----------------------------------------------------------------------------------------

private val unitKeyboard = TheoryUnit("keys", 1, "The keyboard", "Octaves, the black key pattern, finding C, sharps and flats.", listOf(
    lesson("keys-pattern", "The black key pattern", "Groups of two and three, and seven letters that repeat.", listOf(
        scene("A piano has 88 keys, but only a handful of names to learn. Let's look closer.",
            keys("C3", "C6", run("C3 D3 E3 F3 G3 A3 B3 C4 D4 E4 F4 G4 A4 B4 C5 D5 E5 F5 G5 A5 B5 C6"), stepMs = 140, names = KeyNames.NONE), Mood.POINT,
            say = "A piano has eighty eight keys, but only a handful of names to learn. Let's look closer."),
        scene("The black keys come in groups of two and three, over and over.",
            keys("C4", "B5", listOf(kf("C#4 D#4", "Two"), kf("F#4 G#4 A#4", "Three", tint = Tint.BLUE), kf("C#5 D#5", "Two"), kf("F#5 G#5 A#5", "Three", tint = Tint.BLUE)),
                stepMs = 1100, names = KeyNames.NONE)),
        scene("That pattern is your map. Find a group of two and you always know where you are.",
            keys("C3", "B5", listOf(kf("C#3 D#3"), kf("C#4 D#4"), kf("C#5 D#5", "Same pattern, every octave")), stepMs = 900, names = KeyNames.NONE), Mood.POINT),
        scene("The white keys use seven letters, A B C D E F G. Then the letters start again.",
            keys("C4", "C6", run("A4 B4 C5 D5 E5 F5 G5 A5", labels = mapOf(7 to "Back to A")), stepMs = 600)),
    ), listOf(
        choice("How many letters name the white keys?", listOf("5", "7", "8", "12"), 1, "Seven letters, A to G, then they repeat."),
        choice("The black keys come in groups of", listOf("two and three", "three and four", "one and two"), 0, "Two, then three, all the way up the piano."),
        choice("Which letter comes after G?", listOf("H", "A", "C"), 1, "After G the alphabet starts again at A.", show = stillKeys("G4", names = KeyNames.LIT)),
        play("Play the lower key of a group of two black keys.", "C#4", "That key is C sharp, just right of C.", low = "C4", high = "B5"),
    )),
    lesson("keys-c", "Finding C", "C sits left of the two black keys. Middle C, and the octave.", listOf(
        scene("C is the white key just left of every group of two black keys.",
            keys("C3", "B5", listOf(kf("C3", "C"), kf("C4", "C"), kf("C5", "C")), stepMs = 900, names = KeyNames.LIT), Mood.POINT),
        scene("The C nearest the middle of the piano is middle C. We call it C4.",
            keys("C3", "B5", listOf(kf("C4", "Middle C", names = mapOf("C4" to "C4"))), stepMs = 1500)),
        scene("From C the white keys go C D E F G A B, and then C again, one octave up.",
            keys("C4", "C5", run("C4 D4 E4 F4 G4 A4 B4 C5", labels = mapOf(7 to "One octave")), stepMs = 550)),
        scene("D sits between the two black keys. F is just left of the three.",
            keys("C4", "B4", listOf(kf("D4", "D"), kf("F4", "F", tint = Tint.BLUE)), stepMs = 1300)),
        scene("Notes an octave apart share a name and blend into one sound.",
            keys("C3", "C6", listOf(kf("C3"), kf("C4"), kf("C5"), kf("C6", "Octaves")), stepMs = 700, finale = "C3 C4 C5 C6"), Mood.LISTEN),
    ), listOf(
        play("Play any C.", "C4", "C is just left of the two black keys.", low = "C3", high = "B4"),
        play("Play middle C.", "C4", "Middle C is the C nearest the middle of the keyboard.", low = "C3", high = "C5", anyOctave = false),
        play("Play an F.", "F4", "F is just left of the group of three black keys.", low = "C4", high = "B5"),
        choice("Which note is lit?", listOf("C", "D", "E", "F"), 2, "E is just right of the two black keys.", show = stillKeys("E4")),
        choice("From one C up to the next C is called", listOf("a step", "an octave", "a chord"), 1, "Eight white keys from C to C make an octave."),
    )),
    lesson("keys-sharps", "Sharps and flats", "A sharp goes one key up, a flat one key down.", listOf(
        scene("A sharp raises a note to the very next key on the right.",
            keys("C4", "B4", listOf(kf("C4", "C"), kf("C#4", "C♯", gap = "up")), stepMs = 1300, names = KeyNames.LIT)),
        scene("A flat lowers a note to the very next key on the left.",
            keys("C4", "B4", listOf(kf("E4", "E"), kf("Eb4", "E♭", tint = Tint.BLUE)), stepMs = 1300, flats = true)),
        scene("Black keys take their names from their white neighbours. This one is just above F, so it's F sharp.",
            keys("C4", "B4", listOf(kf("F4"), kf("F#4", "F♯"), kf("G4", play = false, tint = Tint.DIM)), stepMs = 1100)),
        scene("A sharp or flat can even land on a white key. E sharp is the same key as F.",
            keys("C4", "B4", listOf(kf("E4", "E"), kf("F4", "E♯ is F", names = mapOf("F4" to "E♯"), tint = Tint.BLUE)), stepMs = 1300), Mood.THINK),
    ), listOf(
        play("Play F sharp.", "F#4", "F sharp is the black key just right of F."),
        play("Play B flat.", "Bb4", "B flat is the black key just left of B.", flats = true),
        choice("Which name fits this key?", listOf("C sharp", "B flat", "D sharp"), 0, "It is one key above C, so it is C sharp.", show = stillKeys("C#4")),
        choice("A flat makes a note", listOf("higher", "lower", "longer"), 1, "A flat moves down to the next key on the left."),
        choice("Which key is one step to the right of E?", listOf("F", "F sharp", "E flat"), 0, "There is no black key between E and F, so the next key up is F."),
    )),
    lesson("keys-enharmonic", "Two names, one key", "Enharmonic notes: C sharp is D flat.", listOf(
        scene("Every black key has two names. This one is C sharp and also D flat.",
            keys("C4", "B4", listOf(kf("C4", tint = Tint.DIM), kf("D4", tint = Tint.DIM), kf("C#4", "C♯ = D♭", names = mapOf("C#4" to "C♯/D♭"))), stepMs = 1000)),
        scene("Names that sound the same are called enharmonic. Same key, same sound.",
            keys("C4", "B4", listOf(kf("F#4", "F♯", names = mapOf("F#4" to "F♯")), kf("F#4", "G♭", names = mapOf("F#4" to "G♭"), tint = Tint.BLUE)), stepMs = 1500), Mood.LISTEN),
        scene("Every black key works the same way.",
            PopVisual(listOf("C♯ = D♭", "D♯ = E♭", "F♯ = G♭", "G♯ = A♭", "A♯ = B♭"), 800, listOf(ms("C#4"), ms("D#4"), ms("F#4"), ms("G#4"), ms("A#4")))),
        scene("Which name to use depends on the key you're in. You'll see why in the scales unit.",
            keys("C4", "B4", listOf(kf("A#4", "In B♭ major it's B♭", names = mapOf("A#4" to "B♭"))), stepMs = 1500), Mood.THINK),
    ), listOf(
        choice("D flat is the same key as", listOf("C sharp", "D sharp", "E flat"), 0, "The black key between C and D has both names."),
        choice("Another name for G sharp is", listOf("A flat", "G flat", "A sharp"), 0, "The key just above G is the key just below A."),
        play("Play E flat.", "Eb4", "E flat is the same key as D sharp.", flats = true),
        choice("Two names for the same key are called", listOf("enharmonic", "chromatic", "octaves"), 0, "Enharmonic names sound the same and look different."),
    )),
))

// ---- Unit 2: reading music ---------------------------------------------------------------------------------------

private val unitReading = TheoryUnit("read", 2, "Reading music", "The staff, both clefs, ledger lines, note values, rests, dots and ties.", listOf(
    lesson("read-staff", "The staff and treble clef", "Five lines, four spaces, and the clef that names them.", listOf(
        scene("Music is written on a staff: five lines and the four spaces between them.",
            treble(drawClef = false, marks = Marks.LINE_NUMBERS)),
        scene("This is the treble clef. Its curl circles the second line, which makes that line G.",
            treble(listOf(sn("G4", WHOLE, "G"))), Mood.POINT),
        scene("Higher on the staff means higher in pitch. Each line and space is the next letter.",
            treble(row("E4 F4 G4 A4 B4 C5 D5 E5 F5"), stepMs = 480, names = true)),
        scene("The lines, bottom to top, are E G B D F. Every good boy does fine.",
            treble(row("E4 G4 B4 D5 F5", WHOLE), drawClef = false, marks = Marks.LINE_NAMES, stepMs = 550)),
        scene("The spaces spell FACE, from the bottom up.",
            treble(row("F4 A4 C5 E5", WHOLE), drawClef = false, marks = Marks.SPACE_NAMES, stepMs = 600), say = "The spaces spell face, from the bottom up."),
    ), listOf(
        choice("Name this note.", listOf("E", "G", "B", "D"), 1, "The second line in the treble clef is G.", show = still(Clef.TREBLE, "G4")),
        choice("Name this note.", listOf("F", "E", "D", "A"), 0, "The top line is F: every good boy does fine.", show = still(Clef.TREBLE, "F5")),
        choice("Name this note.", listOf("A", "C", "E", "F"), 1, "The third space spells the C in FACE.", show = still(Clef.TREBLE, "C5")),
        choice("How many lines does a staff have?", listOf("4", "5", "6"), 1, "Five lines, with four spaces between them."),
        play("Play this note.", "E4", "The bottom line of the treble staff is the E above middle C.", low = "C4", high = "C6", anyOctave = false, show = still(Clef.TREBLE, "E4")),
    )),
    lesson("read-bass", "The bass clef", "Low notes, the F clef, and its lines and spaces.", listOf(
        scene("Low notes use the bass clef. Its two dots sit around the fourth line, F.",
            bass(listOf(sn("F3", WHOLE, "F"))), Mood.POINT),
        scene("The bass lines are G B D F A. Good boys do fine always.",
            bass(row("G2 B2 D3 F3 A3", WHOLE), drawClef = false, marks = Marks.LINE_NAMES, stepMs = 550)),
        scene("The bass spaces are A C E G. All cows eat grass.",
            bass(row("A2 C3 E3 G3", WHOLE), drawClef = false, marks = Marks.SPACE_NAMES, stepMs = 600)),
        scene("Usually the left hand reads the bass clef and the right hand reads the treble.",
            grand(listOf(sn("C3", HALF, staff = Clef.BASS), sn("G3", HALF, staff = Clef.BASS), sn("E4", HALF), sn("G4", HALF), sn("C5", HALF)), stepMs = 650), Mood.PLAY),
    ), listOf(
        choice("Name this note.", listOf("F", "A", "D"), 0, "The bass clef's dots sit around F, the fourth line.", show = still(Clef.BASS, "F3")),
        choice("Name this note.", listOf("G", "E", "B"), 0, "The bottom line of the bass staff is G: good boys do fine always.", show = still(Clef.BASS, "G2")),
        choice("Name this note.", listOf("C", "A", "E"), 0, "The second space is the C in all cows eat grass.", show = still(Clef.BASS, "C3")),
        choice("Which clef is used for low notes?", listOf("Treble clef", "Bass clef"), 1, "The bass clef holds the low notes, usually for the left hand."),
        play("Play this note.", "A2", "The bottom space of the bass staff is A.", low = "C2", high = "B3", anyOctave = false, show = still(Clef.BASS, "A2")),
    )),
    lesson("read-ledger", "Ledger lines and middle C", "Notes beyond the staff, and the grand staff.", listOf(
        scene("Notes can climb above the staff. Short ledger lines extend it.",
            treble(row("G5 A5 B5 C6", names = true), stepMs = 650)),
        scene("Middle C sits on one ledger line just below the treble staff.",
            treble(listOf(sn("C4", WHOLE, "Middle C")))),
        scene("In the bass clef, the very same middle C sits one ledger line above.",
            bass(listOf(sn("C4", WHOLE, "Middle C")))),
        scene("Join the two and you get the grand staff. Middle C sits right between them.",
            grand(listOf(sn("G3", staff = Clef.BASS), sn("A3", staff = Clef.BASS), sn("B3", staff = Clef.BASS), sn("C4", label = "C"), sn("D4"), sn("E4"), sn("F4")), stepMs = 520, names = true), Mood.POINT),
    ), listOf(
        choice("Name this note.", listOf("C", "A", "E"), 0, "One ledger line below the treble staff is middle C.", show = still(Clef.TREBLE, "C4")),
        choice("Name this note.", listOf("A", "F", "C"), 0, "One ledger line above the treble staff is A.", show = still(Clef.TREBLE, "A5")),
        choice("Name this note.", listOf("E", "G", "C"), 0, "One ledger line below the bass staff is E.", show = still(Clef.BASS, "E2")),
        play("Play this note.", "C4", "That is middle C.", low = "C3", high = "C5", anyOctave = false, show = still(Clef.TREBLE, "C4")),
        choice("The short lines above or below the staff are called", listOf("ledger lines", "bar lines", "ties"), 0, "Ledger lines carry notes beyond the five lines."),
    )),
    lesson("read-values", "Note values", "Whole, half, quarter, eighth and sixteenth notes.", listOf(
        scene("A whole note lasts four beats. Two half notes fill the same time, and so do four quarters.",
            ValueTreeVisual(rows = 3), Mood.LISTEN),
        scene("Hollow heads are long notes, filled heads are short. Stems and flags halve the time.",
            treble(listOf(sn("C5", WHOLE, "4"), sn("C5", HALF, "2"), sn("C5", QUARTER, "1"), sn("C5", EIGHTH, "½")), bpm = 100)),
        scene("An eighth note has one flag and lasts half a beat. A sixteenth has two flags.",
            treble(listOf(sn("A4", QUARTER, "1"), sn("A4", EIGHTH, "½"), sn("A4", EIGHTH, "½"), sn("A4", SIXTEENTH, "¼"), sn("A4", SIXTEENTH, "¼"), sn("A4", SIXTEENTH, "¼"), sn("A4", SIXTEENTH, "¼")), bpm = 76)),
        scene("Here is the whole family: one whole note down to sixteen sixteenths, all in the same time.",
            ValueTreeVisual(rows = 5, bpm = 104), Mood.LISTEN),
    ), listOf(
        choice("How many beats does this note last?", listOf("1", "2", "4"), 1, "A half note is hollow with a stem and lasts two beats.", show = still(Clef.TREBLE, "B4", HALF)),
        choice("How many quarter notes fill a whole note?", listOf("2", "4", "8"), 1, "A whole note lasts four beats, one for each quarter."),
        choice("What is this note?", listOf("Quarter note", "Eighth note", "Sixteenth note"), 1, "One flag makes an eighth note.", show = still(Clef.TREBLE, "A4", EIGHTH)),
        choice("Which note lasts longest?", listOf("Whole note", "Half note", "Quarter note", "Eighth note"), 0, "The whole note lasts four beats, the longest of these."),
        choice("How many eighth notes fit in one beat?", listOf("1", "2", "4"), 1, "An eighth note lasts half a beat, so two fit in one."),
    )),
    lesson("read-rests", "Rests, dots and ties", "Counted silence, and two ways to make notes longer.", listOf(
        scene("A rest is counted silence. Every note value has a rest of the same length.",
            ValueTreeVisual(rows = 4, bpm = 96, rests = true)),
        scene("Rests keep the beat going. Count through them, don't skip them.",
            treble(listOf(sn("C5"), rest(QUARTER), sn("E5"), rest(QUARTER), sn("G5", HALF), rest(HALF)), time = 4 to 4, bpm = 92, barlines = listOf(3)), Mood.LISTEN),
        scene("A dot adds half the note's value again. A dotted half note lasts three beats.",
            treble(listOf(sn("C5", HALF, "3", dotted = true), sn("C5", QUARTER, "1"), sn("D5", QUARTER, "1½", dotted = true), sn("D5", EIGHTH, "½")), bpm = 84)),
        scene("A tie joins two notes of the same pitch into one longer sound.",
            treble(listOf(sn("E5", HALF, "2", tie = true), sn("E5", QUARTER, "+1"), sn("D5", QUARTER, "1")), bpm = 84), Mood.LISTEN),
    ), listOf(
        choice("How many beats does this note last?", listOf("2", "3", "4"), 1, "The dot adds half of two beats, so three.", show = still(Clef.TREBLE, "B4", HALF, dotted = true)),
        choice("What does a tie do?", listOf("Joins two notes into one sound", "Makes a note louder", "Repeats the note"), 0, "Tied notes are played once and held for both values."),
        choice("What is this?", listOf("Quarter rest", "Half rest", "Eighth note"), 0, "The zigzag is a quarter rest: one beat of silence.", show = stillRest(QUARTER)),
        choice("A dotted quarter note lasts", listOf("1 beat", "1 and a half beats", "2 beats"), 1, "One beat plus half of one."),
        choice("Which rest is this?", listOf("Whole rest", "Half rest", "Quarter rest"), 0, "The whole rest hangs from a line. The half rest sits on top of one.", show = stillRest(WHOLE)),
    )),
))

// ---- Unit 3: rhythm and meter ------------------------------------------------------------------------------------

private val unitRhythm = TheoryUnit("rhythm", 3, "Rhythm and meter", "Beats, bars, time signatures, counting and tempo.", listOf(
    lesson("rhythm-beat", "Feel the beat", "The steady pulse, and how notes sit on it.", listOf(
        scene("The beat is the steady pulse you tap your foot to. Count along: 1, 2, 3, 4.",
            RhythmVisual(4, 4, 92, even(4, 1f), counts = counts(1, 4)), Mood.LISTEN, say = "The beat is the steady pulse you tap your foot to. Count along: one, two, three, four."),
        scene("Notes sit on the beat. Some last one beat, some last two or more.",
            RhythmVisual(4, 4, 92, hits("0:1 1:1 2:2"), counts = counts(1, 4))),
        scene("Beats are grouped into bars, also called measures. Beat 1 of each bar is the strongest.",
            RhythmVisual(4, 4, 100, even(8, 1f), bars = 2, counts = counts(1, 4) + counts(1, 4), loops = 1), Mood.POINT, say = "Beats are grouped into bars, also called measures. Beat one of each bar is the strongest."),
    ), listOf(
        choice("What is the beat?", listOf("The steady pulse of the music", "The loudest note", "The first note of a song"), 0, "The beat is the pulse you can tap along to."),
        choice("Which beat of a bar is usually the strongest?", listOf("The first", "The last", "The second"), 0, "Beat 1, the downbeat, gets the most weight."),
        choice("Another name for a bar is", listOf("a measure", "a beat", "a rest"), 0, "Bar and measure mean the same thing."),
    )),
    lesson("rhythm-time", "Time signatures", "4/4, 3/4 and 6/8, and what the numbers mean.", listOf(
        scene("The time signature comes after the clef. The top number tells you how many beats are in each bar.",
            treble(row("C5 D5 E5 F5 G5 F5 E5 D5"), time = 4 to 4, bpm = 100, barlines = listOf(3)), Mood.POINT),
        scene("4/4 means four quarter note beats in every bar. It's so common it's called common time.",
            RhythmVisual(4, 4, 96, hits("0:1 1:1 2:1 3:1"), counts = counts(1, 4)), say = "Four four means four quarter note beats in every bar. It's so common it's called common time."),
        scene("3/4 has three beats per bar: ONE two three, ONE two three. That's the waltz.",
            RhythmVisual(3, 4, 120, hits("0:1 1:1 2:1"), bars = 2, counts = counts(1, 3) + counts(1, 3), loops = 2, pitch = 67), Mood.LISTEN,
            say = "Three four has three beats per bar: one two three, one two three. That's the waltz."),
        scene("6/8 has six eighth notes per bar, felt as two groups of three.",
            RhythmVisual(6, 8, 200, hits("0:2 2:1 3:2 5:1"), counts = counts(1, 6), strong = setOf(0, 3), loops = 3), Mood.LISTEN,
            say = "Six eight has six eighth notes per bar, felt as two groups of three."),
    ), listOf(
        choice("How many beats are in each bar?", listOf("2", "3", "4"), 1, "The top number of the time signature is 3.",
            show = StaffVisual(Clef.TREBLE, listOf(sn("C5", HALF, dotted = true)), time = 3 to 4, play = false)),
        choice("The top number of a time signature tells you", listOf("the beats in each bar", "how fast to play", "how loud to play"), 0, "Top: how many beats. Bottom: which note value is one beat."),
        choice("Which time signature fits a waltz?", listOf("3/4", "4/4", "2/4"), 0, "A waltz swings in three: ONE two three."),
        choice("6/8 is usually felt as", listOf("two groups of three", "three groups of two", "six equal beats"), 0, "Beats 1 and 4 get the weight: ONE two three FOUR five six."),
    )),
    lesson("rhythm-count", "Counting and subdivisions", "1 and 2 and, and 1 e and a.", listOf(
        scene("Count quarter notes with one number per beat: 1, 2, 3, 4.",
            RhythmVisual(4, 4, 84, even(4, 1f), counts = counts(1, 4)), Mood.LISTEN, say = "Count quarter notes with one number per beat: one, two, three, four."),
        scene("Eighth notes split each beat in two. Count them: 1 and 2 and 3 and 4 and.",
            RhythmVisual(4, 4, 80, even(8, 0.5f), sub = 2, counts = counts(2, 4)), Mood.LISTEN,
            say = "Eighth notes split each beat in two. Count them: one and two and three and four and."),
        scene("Sixteenths split each beat in four: 1 e and a, 2 e and a.",
            RhythmVisual(4, 4, 60, even(16, 0.25f), sub = 4, counts = counts(4, 4), loops = 1), Mood.LISTEN,
            say = "Sixteenths split each beat in four: one e and a, two e and a."),
        scene("Mix them and keep counting. The beat never changes, only how you split it.",
            RhythmVisual(4, 4, 84, hits("0:1 1:0.5 1.5:0.5 2:1 3:0.5 3.5:0.5"), sub = 2, counts = counts(2, 4))),
    ), listOf(
        choice("How do you count eighth notes?", listOf("1 and 2 and", "1 2 3 4", "1 e and a"), 0, "Each beat splits in two: the number, then and."),
        choice("How many sixteenth notes fit in one beat?", listOf("2", "4", "8"), 1, "Sixteenths split a quarter note beat in four."),
        choice("In 1 e and a, which part lands on the beat?", listOf("1", "e", "and", "a"), 0, "The number is always on the beat."),
        choice("When you count eighths, the beat", listOf("stays the same", "gets twice as fast", "gets slower"), 0, "The pulse never changes. You just fit more notes into it."),
    )),
    lesson("rhythm-tempo", "Tempo", "Beats per minute, and the Italian words for speed.", listOf(
        scene("Tempo is the speed of the beat, counted in beats per minute, or bpm.",
            RhythmVisual(4, 4, 60, even(4, 1f), counts = counts(1, 4), term = "60 bpm"), say = "Tempo is the speed of the beat, counted in beats per minute, or B P M."),
        scene("Largo is slow and broad, around 50 beats per minute.",
            RhythmVisual(4, 4, 50, hits("0:2 2:2"), counts = counts(1, 4), loops = 1, term = "Largo"), Mood.LISTEN),
        scene("Andante is a walking pace, around 80.",
            RhythmVisual(4, 4, 80, even(4, 1f), counts = counts(1, 4), term = "Andante"), Mood.LISTEN),
        scene("Allegro is quick and bright, around 130.",
            RhythmVisual(4, 4, 132, hits("0:1 1:0.5 1.5:0.5 2:1 3:1"), counts = counts(1, 4), loops = 3, term = "Allegro"), Mood.PLAY),
        scene("Presto is very fast. Composers write these words above the staff.",
            RhythmVisual(4, 4, 176, even(8, 0.5f), sub = 2, counts = counts(2, 4), loops = 3, term = "Presto"), Mood.CHEER),
    ), listOf(
        choice("Which marking is the slowest?", listOf("Largo", "Andante", "Allegro", "Presto"), 0, "Largo is slow and broad."),
        choice("bpm stands for", listOf("beats per minute", "bars per measure", "beats per measure"), 0, "Sixty bpm is one beat every second."),
        choice("Andante means", listOf("at a walking pace", "very fast", "getting louder"), 0, "Andante comes from the Italian for walking."),
        choice("Which tempo is fastest?", listOf("Presto", "Allegro", "Andante"), 0, "Presto is the fastest of these."),
    )),
))

// ---- Unit 4: intervals -------------------------------------------------------------------------------------------

private val unitIntervals = TheoryUnit("intervals", 4, "Intervals", "Half and whole steps, interval names, how they sound.", listOf(
    lesson("int-steps", "Half steps and whole steps", "The smallest moves on the keyboard.", listOf(
        scene("A half step is the smallest move: to the very next key, black or white.",
            keys("C4", "B4", listOf(kf("C4"), kf("C#4", gap = "H"), kf("E4", clear = true), kf("F4", gap = "H")), stepMs = 1000)),
        scene("Two half steps make a whole step. You skip one key in between.",
            keys("C4", "B4", listOf(kf("C4"), kf("D4", gap = "W"), kf("E4", clear = true), kf("F#4", gap = "W")), stepMs = 1000)),
        scene("E to F and B to C are half steps with no black key between them.",
            keys("C4", "C5", listOf(kf("E4"), kf("F4", gap = "H"), kf("B4"), kf("C5", gap = "H")), stepMs = 1000), Mood.POINT),
        scene("Count half steps key by key. C up to E is four of them.",
            intervals("C4", "B4", pair("C4", "E4", name = "4 half steps"))),
    ), listOf(
        choice("From E to F is a", listOf("half step", "whole step"), 0, "There is no key between E and F."),
        choice("From C to D is a", listOf("half step", "whole step"), 1, "C sharp sits between them, so it is two half steps."),
        play("Play the note a half step above E.", "F4", "E to F is a half step: no black key between."),
        play("Play the note a whole step above F.", "G4", "F to G skips F sharp: a whole step."),
        choice("How many half steps make a whole step?", listOf("1", "2", "3"), 1, "Two half steps make one whole step."),
    )),
    lesson("int-names", "Naming intervals", "Count the letters: 2nds, 3rds, 4ths, 5ths and octaves.", listOf(
        scene("An interval is the distance between two notes. Count the letters, including both ends.",
            intervals("C4", "B4", pair("C4", "D4", name = "2nd"), pair("C4", "E4", name = "3rd"), count = false), Mood.LISTEN),
        scene("C up to F is a 4th, and C up to G is a 5th.",
            intervals("C4", "B4", pair("C4", "F4", name = "4th"), pair("C4", "G4", name = "5th"), count = false), Mood.LISTEN),
        scene("Eight letters from C to the next C make an octave.",
            intervals("C4", "C5", pair("C4", "C5", name = "Octave"), count = false), Mood.LISTEN),
        scene("On the staff, a 3rd goes line to line or space to space. A 5th skips one more.",
            treble(listOf(sn("E4+G4", HALF, "3rd"), sn("F4+A4", HALF, "3rd"), sn("G4+D5", HALF, "5th"), sn("A4+E5", HALF, "5th")), stepMs = 900)),
    ), listOf(
        choice("C up to A is a", listOf("5th", "6th", "7th"), 1, "C D E F G A: six letters.", show = stillKeys("C4 A4", names = KeyNames.LIT)),
        choice("D up to F is a", listOf("2nd", "3rd", "4th"), 1, "D E F: three letters."),
        choice("What interval is this?", listOf("2nd", "3rd", "4th"), 1, "Line to line, skipping one letter: a 3rd.",
            show = StaffVisual(Clef.TREBLE, listOf(sn("G4+B4", WHOLE)), play = false)),
        choice("E up to B is a", listOf("4th", "5th", "6th"), 1, "E F G A B: five letters."),
        play("Play the note a 5th above C.", "G4", "C D E F G: five letters up is G."),
    )),
    lesson("int-quality", "Major, minor, perfect", "The quality of an interval, by half steps.", listOf(
        scene("Seconds, thirds, sixths and sevenths come in two sizes: major and minor.",
            intervals("C4", "B4", pair("C4", "E4"), pair("C4", "Eb4")), Mood.LISTEN),
        scene("A minor interval is one half step smaller than the major one. Hear the colour change.",
            intervals("C4", "B4", pair("C4", "A4"), pair("C4", "Ab4")), Mood.LISTEN),
        scene("Unisons, 4ths, 5ths and octaves are perfect. They sound open and pure.",
            intervals("C4", "C5", pair("C4", "F4"), pair("C4", "G4")), Mood.LISTEN),
        scene("Six half steps sit right in the middle: the tritone. Hear how it wants to move.",
            intervals("C4", "B4", pair("C4", "F#4", name = "Tritone")), Mood.THINK),
    ), listOf(
        listen("Major 3rd or minor 3rd?", listOf("C4", "E4", "C4+E4"), listOf("Major 3rd", "Minor 3rd"), 0, "Four half steps: a bright major 3rd."),
        listen("Major 3rd or minor 3rd?", listOf("A3", "C4", "A3+C4"), listOf("Major 3rd", "Minor 3rd"), 1, "Three half steps: a darker minor 3rd."),
        choice("How many half steps are in a perfect 5th?", listOf("5", "7", "8"), 1, "C up to G is seven half steps."),
        choice("A minor 3rd has", listOf("3 half steps", "4 half steps", "5 half steps"), 0, "Minor 3rd, three half steps. Major 3rd, four."),
        choice("Which interval is perfect?", listOf("4th", "3rd", "6th"), 0, "Unisons, 4ths, 5ths and octaves are the perfect ones."),
    )),
    lesson("int-sound", "How intervals sound", "Consonance, dissonance, and songs to remember them.", listOf(
        scene("Each interval has its own colour. A perfect 5th sounds open and strong.",
            intervals("C4", "B4", pair("C4", "G4")), Mood.LISTEN),
        scene("Thirds and sixths sound sweet. They're consonant: settled, at rest.",
            intervals("C4", "B4", pair("C4", "E4", "Consonant"), pair("C4", "A4", "Consonant")), Mood.LISTEN),
        scene("Seconds and sevenths rub against each other. They're dissonant: they want to move.",
            intervals("C4", "B4", pair("C4", "D4", "Dissonant"), pair("C4", "B4", "Dissonant")), Mood.THINK),
        scene("Dissonance isn't wrong. It builds tension, and the music then lets it go.",
            keys("A3", "B4", listOf(kf("B3 F4", "Tension", tint = Tint.BLUE), kf("C4 E4", "Release", clear = true)), stepMs = 1700)),
        scene("Songs help you hear intervals. Twinkle Twinkle leaps up a perfect 5th.",
            keys("C4", "B4", run("C4 C4 G4 G4 A4 A4 G4", labels = mapOf(2 to "Up a 5th")), stepMs = 480), Mood.CHEER),
    ), listOf(
        listen("Which sounds more settled?", listOf("C4+G4", "C4+C#4"), listOf("The first", "The second"), 0, "The perfect 5th is consonant. The half step clashes.", gapMs = 1200),
        listen("Which note is higher?", listOf("G4", "D4"), listOf("The first", "The second"), 0, "The first note, G, is higher than D."),
        listen("Which interval is this?", listOf("C4", "G4", "C4+G4"), listOf("Perfect 5th", "Major 3rd", "Octave"), 0, "Open and strong: the perfect 5th, like Twinkle Twinkle."),
        choice("Which interval is dissonant?", listOf("Major 2nd", "Perfect 5th", "Major 3rd"), 0, "Seconds rub against each other and want to move."),
        listen("Which interval is this?", listOf("C4", "C5", "C4+C5"), listOf("Octave", "Perfect 4th", "Minor 3rd"), 0, "The same note, higher: an octave."),
    )),
))

// ---- Unit 5: scales and keys -------------------------------------------------------------------------------------

private val unitScales = TheoryUnit("scales", 5, "Scales and keys", "Major and minor scales, key signatures, the circle of fifths.", listOf(
    lesson("sc-major", "The major scale", "Whole, whole, half, whole, whole, whole, half.", listOf(
        scene("A major scale climbs eight notes, from one note up to the same letter an octave higher.",
            keys("C4", "C5", scaleFrames("C4", gaps = false), stepMs = 450), Mood.LISTEN),
        scene("Its secret is the step pattern: whole, whole, half, whole, whole, whole, half.",
            keys("C4", "C5", scaleFrames("C4"), stepMs = 850), Mood.POINT),
        scene("Start on G and follow the same pattern. To fit it, F becomes F sharp.",
            keys("C4", "C6", scaleFrames("G4"), stepMs = 800)),
        scene("On D the pattern needs two sharps: F sharp and C sharp.",
            keys("C4", "C6", scaleFrames("D4"), stepMs = 650), Mood.LISTEN),
    ), listOf(
        choice("What is the step pattern of a major scale?", listOf("W W H W W W H", "W H W W H W W", "W W W H W W H"), 0, "Whole, whole, half, whole, whole, whole, half."),
        play("G major has one black key. Play it.", "F#4", "G major has F sharp to keep the pattern."),
        choice("Which notes are sharp in D major?", listOf("F and C", "F only", "C and G"), 0, "D major has two sharps, F sharp and C sharp."),
        choice("In C major, E to F is a", listOf("half step", "whole step"), 0, "Steps 3 to 4 are a half step.", show = stillKeys("E4 F4", names = KeyNames.LIT)),
        play("Play the 5th note of the C major scale.", "G4", "C D E F G: the 5th note is G."),
    )),
    lesson("sc-minor", "Minor scales", "Natural, harmonic and melodic minor.", listOf(
        scene("The natural minor scale has its own pattern: whole, half, whole, whole, half, whole, whole.",
            keys("C4", "C6", scaleFrames("A4", ScaleKind.NATURAL_MINOR), stepMs = 800)),
        scene("Same first note, different colour. C major sounds bright, C minor sounds darker.",
            keys("C4", "C5", scaleFrames("C4", gaps = false, label = "Major") + scaleFrames("C4", ScaleKind.NATURAL_MINOR, gaps = false, clearFirst = true, label = "Minor"), stepMs = 380, flats = true), Mood.LISTEN),
        scene("Harmonic minor raises the 7th note. The leap of a step and a half sounds exotic.",
            keys("C4", "C6", scaleFrames("A4", ScaleKind.HARMONIC_MINOR), stepMs = 750), Mood.LISTEN),
        scene("Melodic minor raises the 6th and 7th on the way up, and comes down as natural minor.",
            keys("C4", "C6", scaleFrames("A4", ScaleKind.MELODIC_MINOR, gaps = false) + run("G5 F5 E5 D5 C5 B4 A4", clearFirst = true), stepMs = 450)),
    ), listOf(
        choice("Which note is raised in harmonic minor?", listOf("The 7th", "The 3rd", "The 5th"), 0, "Raising the 7th leads back home to the first note."),
        listen("Major or minor?", scaleClips("C4", ScaleKind.NATURAL_MINOR), listOf("Major", "Minor"), 1, "The lowered 3rd makes it minor.", gapMs = 330),
        listen("Major or minor?", scaleClips("C4", ScaleKind.MAJOR), listOf("Major", "Minor"), 0, "Bright from the 3rd note on: major.", gapMs = 330),
        play("A harmonic minor has one black key. Play it.", "G#4", "The raised 7th of A minor is G sharp.", low = "A3", high = "A5"),
        choice("A natural minor uses", listOf("only white keys", "two black keys", "only black keys"), 0, "A minor shares its notes with C major."),
    )),
    lesson("sc-keysig", "Key signatures", "Sharps and flats written once, at the start.", listOf(
        scene("G major always has F sharp, so it's written once at the start of each line: the key signature.",
            treble(row("G4 A4 B4 C5 D5 E5 F#5 G5"), keyFifths = 1, stepMs = 450)),
        scene("Sharps always arrive in the same order: F C G D A E B. Father Charles goes down and ends battle.",
            treble(keyFifths = 7), Mood.POINT),
        scene("Flats come in the reverse order: B E A D G C F. Battle ends and down goes Charles' father.",
            treble(keyFifths = -7), Mood.POINT),
        scene("A trick for sharps: the key is a half step above the last sharp. Three sharps end on G sharp, so A major.",
            treble(listOf(sn("A4", WHOLE, "A major")), keyFifths = 3), Mood.THINK),
        scene("A trick for flats: the second to last flat names the key. Two flats: B flat major.",
            treble(listOf(sn("Bb4", WHOLE, "B♭ major")), keyFifths = -2), Mood.THINK),
    ), listOf(
        choice("Which major key has this signature?", listOf("D major", "G major", "A major"), 0, "Two sharps: the last is C sharp, a half step below D.", show = treble(keyFifths = 2).copy(play = false)),
        choice("Which major key has this signature?", listOf("F major", "B flat major", "C major"), 0, "One flat, B flat, is F major.", show = treble(keyFifths = -1).copy(play = false)),
        choice("What is the first sharp in a key signature?", listOf("F sharp", "C sharp", "G sharp"), 0, "Father Charles: F comes first."),
        choice("What is the first flat in a key signature?", listOf("B flat", "E flat", "F flat"), 0, "Battle ends: B comes first."),
        choice("Which major key has this signature?", listOf("E major", "A major", "B major"), 0, "Four sharps end on D sharp, a half step below E.", show = treble(keyFifths = 4).copy(play = false)),
        choice("Which major key has this signature?", listOf("E flat major", "A flat major", "B flat major"), 0, "Three flats: the second to last is E flat.", show = treble(keyFifths = -3).copy(play = false)),
    )),
    lesson("sc-circle", "The circle of fifths", "Every key, one sharp or flat apart.", listOf(
        scene("Put the keys in a circle, each a 5th above the last, and you get the circle of fifths.",
            CircleVisual(listOf(0), build = true), Mood.LISTEN),
        scene("Going clockwise adds one sharp at a time: G has one, D has two, A has three.",
            CircleVisual(listOf(1, 2, 3))),
        scene("Going the other way adds flats: F has one, B flat two, E flat three.",
            CircleVisual(listOf(11, 10, 9))),
        scene("At the bottom the two sides meet. F sharp major and G flat major sound the same.",
            CircleVisual(listOf(6)), Mood.THINK),
    ), listOf(
        choice("How many sharps does D major have?", listOf("1", "2", "3"), 1, "Two steps clockwise from C: two sharps.", show = CircleVisual(listOf(2), play = false)),
        choice("Clockwise, each key on the circle is a", listOf("5th above the last", "4th above the last", "3rd above the last"), 0, "That is why it is called the circle of fifths."),
        choice("Which key comes after A, clockwise?", listOf("E", "D", "B"), 0, "A up a 5th is E."),
        choice("Which major key has one flat?", listOf("F major", "B flat major", "G major"), 0, "One step counterclockwise from C is F."),
        choice("How many flats does E flat major have?", listOf("2", "3", "4"), 1, "Three steps counterclockwise from C: three flats.", show = CircleVisual(listOf(9), play = false)),
    )),
    lesson("sc-relative", "Relative keys", "Major and minor keys that share a signature.", listOf(
        scene("Every major key has a relative minor with the same key signature. C major and A minor share no sharps or flats.",
            CircleVisual(listOf(0), minors = true), Mood.POINT),
        scene("To find it, count down three half steps from the major key's first note.",
            keys("C4", "C5", listOf(kf("C5", "C major"), kf("B4", names = mapOf("B4" to "1"), play = true), kf("Bb4", names = mapOf("Bb4" to "2")), kf("A4", "A minor", tint = Tint.BLUE, names = mapOf("A4" to "3"))), stepMs = 800)),
        scene("G major and E minor share one sharp. Same notes, a different home.",
            CircleVisual(listOf(1), minors = true), Mood.LISTEN),
        scene("Relative keys are close friends. Songs often move between them.",
            chords(chord("C4 E4 G4", "C", "I", build = false), chord("C4 E4 A4", "Am", "vi", build = false), chord("C4 F4 A4", "F", "IV", build = false), chord("B3 D4 G4", "G", "V", build = false), holdMs = 1100), Mood.LISTEN),
    ), listOf(
        choice("What is the relative minor of C major?", listOf("A minor", "E minor", "D minor"), 0, "Three half steps down from C is A."),
        choice("What is the relative minor of G major?", listOf("E minor", "B minor", "D minor"), 0, "Three half steps down from G is E."),
        choice("Relative keys share the same", listOf("key signature", "first note", "chords in the same order"), 0, "Same sharps or flats, a different home note."),
        choice("What is the relative major of D minor?", listOf("F major", "B flat major", "A major"), 0, "Three half steps up from D is F."),
        choice("Which minor key shares A major's three sharps?", listOf("F sharp minor", "C sharp minor", "E minor"), 0, "Three half steps down from A is F sharp.", show = CircleVisual(listOf(3), minors = true, play = false)),
    )),
))

// ---- Unit 6: chords and harmony ----------------------------------------------------------------------------------

private val cTriads = diatonicTriads(p("C4"))

private val unitChords = TheoryUnit("chords", 6, "Chords and harmony", "Triads, inversions, sevenths, progressions and cadences.", listOf(
    lesson("ch-triads", "Building triads", "Three notes stacked in thirds.", listOf(
        scene("A triad is three notes stacked in thirds: a root, a 3rd and a 5th.",
            chords(chord("C4 E4 G4", "C major", tags = listOf("Root", "3rd", "5th")))),
        scene("Build one on every note of the C major scale, using only white keys.",
            ChordVisual(cTriads.map { ChordStep(it.notes, it.symbol, it.numeral, build = false) }, m("C4"), m("C6"), holdMs = 900), Mood.LISTEN),
        scene("Each gets a Roman numeral for its step of the scale. Capitals are major, small letters minor.",
            PopVisual(cTriads.map { it.numeral }, 700, cTriads.map { t -> t.notes.map { it.midi } }),
            say = "Each gets a Roman numeral for its step of the scale, one to seven. Capitals are major, small letters minor."),
    ), listOf(
        choice("A triad is built from", listOf("stacked 3rds", "stacked 2nds", "stacked 5ths"), 0, "Root, skip a letter, 3rd, skip a letter, 5th."),
        choice("The three notes of a triad are the", listOf("root, 3rd and 5th", "root, 2nd and 4th", "bass, middle and top"), 0, "Named by their distance from the root."),
        choice("Name this chord.", listOf("C major", "E minor", "G major"), 0, "C, E and G: the C major triad.", show = chords(chord("C4 E4 G4", "", build = false))),
        play("Play the 3rd of a C major triad.", "E4", "C E G: the 3rd is E."),
        play("Play the 5th of an F major triad.", "C5", "F A C: the 5th is C.", low = "F4", high = "F5"),
    )),
    lesson("ch-quality", "Major, minor, diminished", "What makes a triad bright, dark or tense.", listOf(
        scene("A major triad: a major 3rd with a minor 3rd on top. Bright and stable.",
            chords(chord("C4 E4 G4", "C major", tags = listOf("Root", "Major 3rd", "5th"))), Mood.LISTEN),
        scene("A minor triad flips them: minor 3rd below, major 3rd on top. Softer and darker.",
            chords(chord("C4 Eb4 G4", "C minor", tags = listOf("Root", "Minor 3rd", "5th"))), Mood.LISTEN),
        scene("A diminished triad stacks two minor 3rds. Tense and restless.",
            chords(chord("B3 D4 F4", "B diminished", tags = listOf("Root", "Minor 3rd", "Flat 5th"))), Mood.THINK),
        scene("In a major key: I, IV and V are major; ii, iii and vi are minor; vii is diminished.",
            ChordVisual(cTriads.map { ChordStep(it.notes, it.symbol, it.numeral, build = false) }, m("C4"), m("C6"), holdMs = 800),
            say = "In a major key: one, four and five are major; two, three and six are minor; seven is diminished."),
    ), listOf(
        listen("Major or minor?", listOf("C4+E4+G4"), listOf("Major", "Minor"), 0, "Major 3rd on the bottom: bright, a major chord."),
        listen("Major or minor?", listOf("A3+C4+E4"), listOf("Major", "Minor"), 1, "Minor 3rd on the bottom: A minor."),
        listen("Which chord sounds tense?", listOf("B3+D4+F4", "C4+E4+G4"), listOf("The first", "The second"), 0, "The first was B diminished, two minor 3rds.", gapMs = 1300),
        choice("A minor triad has which 3rd on the bottom?", listOf("Minor 3rd", "Major 3rd"), 0, "Minor 3rd, then a major 3rd on top."),
        choice("Which triad in C major is diminished?", listOf("The one on B", "The one on D", "The one on G"), 0, "B D F: the vii chord."),
    )),
    lesson("ch-inversions", "Inversions", "Same chord, the notes in a new order.", listOf(
        scene("Root position has the root at the bottom: C, E, G.",
            chords(chord("C4 E4 G4", "Root position", tags = listOf("Root", "3rd", "5th")))),
        scene("Move the root up an octave and you get first inversion: E, G, C.",
            chords(chord("E4 G4 C5", "First inversion", tags = listOf("3rd", "5th", "Root")))),
        scene("Move the 3rd up too: second inversion, G, C, E. Same chord, new shape.",
            chords(chord("G4 C5 E5", "Second inversion", tags = listOf("5th", "Root", "3rd")))),
        scene("Inversions let your hand barely move from chord to chord.",
            chords(chord("C4 E4 G4", "C", "I", build = false), chord("C4 F4 A4", "F", "IV", build = false), chord("B3 D4 G4", "G", "V", build = false), chord("C4 E4 G4", "C", "I", build = false), holdMs = 1200), Mood.PLAY),
    ), listOf(
        choice("Which note is at the bottom in first inversion?", listOf("The 3rd", "The root", "The 5th"), 0, "The root moved up, so the 3rd is lowest."),
        choice("Which inversion of C major is this?", listOf("Root position", "First inversion", "Second inversion"), 2, "G is at the bottom: the 5th, so second inversion.",
            show = StaffVisual(Clef.TREBLE, listOf(sn("G4+C5+E5", WHOLE)), play = false)),
        choice("Which inversion of C major is this?", listOf("Root position", "First inversion", "Second inversion"), 1, "E is at the bottom: the 3rd, so first inversion.",
            show = StaffVisual(Clef.TREBLE, listOf(sn("E4+G4+C5", WHOLE)), play = false)),
        choice("Does inverting a chord change its name?", listOf("No, the notes are the same", "Yes, it becomes a new chord"), 0, "C E G in any order is still C major."),
    )),
    lesson("ch-sevenths", "Seventh chords", "One more 3rd on top.", listOf(
        scene("Add one more 3rd on top of a triad and you get a seventh chord.",
            chords(chord("G3 B3 D4 F4", "G7", tags = listOf("Root", "3rd", "5th", "7th")), low = "C3")),
        scene("G7, the dominant 7th, pulls hard toward home, C.",
            chords(chord("G3 B3 D4 F4", "G7", "V7", build = false), chord("C4 E4 G4", "C", "I", build = false), low = "C3", holdMs = 1500), Mood.LISTEN),
        scene("A major 7th sounds dreamy: C, E, G, B.",
            chords(chord("C4 E4 G4 B4", "Cmaj7", tags = listOf("Root", "3rd", "5th", "Major 7th"))), Mood.LISTEN),
        scene("A minor 7th sounds smooth and mellow: A, C, E, G.",
            chords(chord("A3 C4 E4 G4", "Am7", tags = listOf("Root", "Minor 3rd", "5th", "7th")), low = "C3"), Mood.LISTEN),
    ), listOf(
        choice("How many notes are in a seventh chord?", listOf("3", "4", "5"), 1, "A triad plus one more 3rd."),
        choice("G B D F is called", listOf("G7", "G major 7", "G minor 7"), 0, "A major triad with a minor 7th on top: the dominant 7th."),
        listen("Which chord has four notes?", listOf("C4+E4+G4", "C4+E4+G4+B4"), listOf("The first", "The second"), 1, "The second added B: C major 7.", gapMs = 1300),
        choice("A seventh chord adds a note that is", listOf("a 7th above the root", "a 6th above the root", "a 9th above the root"), 0, "Root, 3rd, 5th, 7th."),
    )),
    lesson("ch-progressions", "Progressions and cadences", "I IV V I, the pop four, and how phrases end.", listOf(
        scene("Chords move in progressions. The classic: I, IV, V, then home to I.",
            chords(chord("C4 E4 G4", "C", "I", build = false), chord("C4 F4 A4", "F", "IV", build = false), chord("B3 D4 G4", "G", "V", build = false), chord("C4 E4 G4", "C", "I", build = false), holdMs = 1200),
            Mood.LISTEN, say = "Chords move in progressions. The classic: one, four, five, then home to one."),
        scene("Add vi and you get the pop progression, I V vi IV, heard in countless songs.",
            chords(chord("C4 E4 G4", "C", "I", build = false), chord("B3 D4 G4", "G", "V", build = false), chord("C4 E4 A4", "Am", "vi", build = false), chord("C4 F4 A4", "F", "IV", build = false), holdMs = 1200),
            Mood.CHEER, say = "Add six and you get the pop progression, one five six four, heard in countless songs."),
        scene("A cadence ends a phrase. V to I is the perfect cadence: it sounds finished.",
            chords(chord("G3 B3 D4 F4", "G7", "V", build = false), chord("C4 E4 G4", "C", "I", build = false), low = "C3", holdMs = 1600), say = "A cadence ends a phrase. Five to one is the perfect cadence: it sounds finished."),
        scene("IV to I is the plagal cadence, the gentle amen at the end of a hymn.",
            chords(chord("C4 F4 A4", "F", "IV", build = false), chord("C4 E4 G4", "C", "I", build = false), holdMs = 1600), Mood.LISTEN, say = "Four to one is the plagal cadence, the gentle amen at the end of a hymn."),
        scene("Stopping on V is a half cadence. It sounds like a question waiting for an answer.",
            chords(chord("C4 E4 G4", "C", "I", build = false), chord("B3 D4 G4", "G", "V", build = false), holdMs = 1600), Mood.THINK, say = "Stopping on five is a half cadence. It sounds like a question waiting for an answer."),
    ), listOf(
        choice("Which cadence sounds most finished?", listOf("V to I", "I to V", "IV to V"), 0, "The perfect cadence, V to I, lands at home."),
        listen("Does this sound finished?", listOf("G3+B3+D4+F4", "C4+E4+G4"), listOf("Yes, it ends at home", "No, it ends on a question"), 0, "V7 to I: the perfect cadence.", gapMs = 1300),
        listen("Finished, or a question?", listOf("C4+E4+G4", "B3+D4+G4"), listOf("Finished", "A question"), 1, "Ending on V leaves it hanging: a half cadence.", gapMs = 1300),
        choice("In C major, which chord is V?", listOf("G major", "F major", "A minor"), 0, "C D E F G: the 5th note is G."),
        choice("The plagal cadence goes", listOf("IV to I", "V to I", "I to V"), 0, "The amen cadence: IV to I."),
        play("Play the root of the IV chord in C major.", "F4", "The 4th note of C major is F."),
    )),
))

/** The Theory Academy, in order. */
val THEORY: List<TheoryUnit> = listOf(unitKeyboard, unitReading, unitRhythm, unitIntervals, unitScales, unitChords)

/** Every lesson in order with its unit. */
val THEORY_LESSONS: List<Pair<TheoryUnit, TheoryLesson>> = THEORY.flatMap { u -> u.lessons.map { u to it } }

fun theoryLesson(id: String) = THEORY_LESSONS.firstOrNull { it.second.id == id }
