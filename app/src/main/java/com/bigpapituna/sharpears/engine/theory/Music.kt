package com.bigpapituna.sharpears.engine.theory

/**
 * Music theory for the Theory Academy: spelled pitches, staff positions, key signatures, scales, intervals,
 * the circle of fifths and triads. Pure Kotlin, tested on the JVM.
 */

private const val LETTERS = "CDEFGAB"
private val NATURAL = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/** A spelled note: [letter] 0 = C .. 6 = B, [accidental] in semitones (-1 flat, +1 sharp), [octave] as in C4 = middle C. */
data class Pitch(val letter: Int, val accidental: Int, val octave: Int) {
    init { require(letter in 0..6 && accidental in -2..2) { "bad pitch $letter $accidental" } }

    val midi get() = 12 * (octave + 1) + NATURAL[letter] + accidental
    /** Letter steps from C0; two pitches a line or space apart on the staff differ by one. */
    val diatonic get() = octave * 7 + letter
    val letterName get() = LETTERS[letter].toString()

    /** "C♯", "B♭", "F" (no octave), drawn on the screen. */
    val label get() = letterName + when (accidental) { 1 -> "♯"; -1 -> "♭"; 2 -> "♯♯"; -2 -> "♭♭"; else -> "" }
    /** "C sharp", "B flat", "F", for captions and the voice. */
    val spoken get() = letterName + when (accidental) { 1 -> " sharp"; -1 -> " flat"; 2 -> " double sharp"; -2 -> " double flat"; else -> "" }
    /** "C#4", "Bb3": the token form [parse] reads. */
    val code get() = letterName + when (accidental) { 1 -> "#"; -1 -> "b"; 2 -> "##"; -2 -> "bb"; else -> "" } + octave

    /** The natural note [steps] letters up (down if negative). */
    fun letterUp(steps: Int): Pitch { val d = diatonic + steps; return Pitch(Math.floorMod(d, 7), 0, Math.floorDiv(d, 7)) }

    companion object {
        /** "C4", "F#3", "Bb2", "Ebb5". */
        fun parse(s: String): Pitch {
            val letter = LETTERS.indexOf(s[0].uppercaseChar())
            require(letter >= 0) { "bad note $s" }
            var i = 1; var acc = 0
            while (i < s.length && (s[i] == '#' || s[i] == 'b')) { acc += if (s[i] == '#') 1 else -1; i++ }
            return Pitch(letter, acc, s.substring(i).toInt())
        }

        /** The usual spelling of [midi]: black keys as sharps, or flats with [flats]. */
        fun of(midi: Int, flats: Boolean = false): Pitch {
            val pc = Math.floorMod(midi, 12); val oct = Math.floorDiv(midi, 12) - 1
            val natural = NATURAL.indexOf(pc)
            if (natural >= 0) return Pitch(natural, 0, oct)
            return if (flats) Pitch(NATURAL.indexOf(pc + 1), -1, oct) else Pitch(NATURAL.indexOf(pc - 1), 1, oct)
        }
    }
}

fun p(s: String) = Pitch.parse(s)

/** The usual name of a key without octave, as drawn: "C♯" or "D♭" with [flats]. */
fun keyLabel(midi: Int, flats: Boolean = false) = Pitch.of(midi, flats).label

enum class Clef(
    /** The pitch on the bottom line. */
    val bottom: Pitch,
) { TREBLE(Pitch(2, 0, 4)), BASS(Pitch(4, 0, 2)) }

/** Where [pitch] sits on a five-line staff: 0 is the bottom line, 1 the space above it, 8 the top line. */
fun staffPosition(pitch: Pitch, clef: Clef) = pitch.diatonic - clef.bottom.diatonic

/** The ledger lines a note at [position] needs (positions of the short lines), nearest the staff first. */
fun ledgerLines(position: Int): List<Int> = when {
    position <= -2 -> (-2 downTo position).filter { it % 2 == 0 }
    position >= 10 -> (10..position).filter { it % 2 == 0 }
    else -> emptyList()
}

/** The notes on the lines (bottom up) and in the spaces, as letters: treble E G B D F and F A C E. */
fun lineNames(clef: Clef) = (0..8 step 2).map { clef.bottom.letterUp(it).letterName }
fun spaceNames(clef: Clef) = (1..7 step 2).map { clef.bottom.letterUp(it).letterName }

// ---- Keys --------------------------------------------------------------------------------------------------------

/** Letters in the order sharps are added (F C G D A E B); flats come in the reverse order. */
val SHARP_ORDER = listOf(3, 0, 4, 1, 5, 2, 6)
val FLAT_ORDER = SHARP_ORDER.reversed()

/** Steps round the circle of fifths from C for each natural letter: F -1, C 0, G 1, D 2, A 3, E 4, B 5. */
private val LETTER_FIFTHS = intArrayOf(0, 2, 4, -1, 1, 3, 5)

/**
 * The key signature of a major key on [tonic], or a minor key with [minor]: positive is that many sharps,
 * negative that many flats. A minor key shares its signature with the major key three half steps up.
 */
fun keyFifths(tonic: Pitch, minor: Boolean = false): Int = LETTER_FIFTHS[tonic.letter] + 7 * tonic.accidental - if (minor) 3 else 0

/** The altered notes of a signature, in the order they are written: 2 gives F sharp, C sharp; -3 gives B, E, A flat. */
fun keySignature(fifths: Int): List<Pitch> = if (fifths >= 0) SHARP_ORDER.take(fifths).map { Pitch(it, 1, 4) }
    else FLAT_ORDER.take(-fifths).map { Pitch(it, -1, 4) }

/** Staff positions of a signature's sharps or flats, in the traditional zigzag (treble sharps start on the top line, F). */
fun keySignaturePositions(fifths: Int, clef: Clef): List<Int> {
    val treble = if (fifths >= 0) listOf(8, 5, 9, 6, 3, 7, 4) else listOf(4, 7, 3, 6, 2, 5, 1)
    val shift = if (clef == Clef.BASS) -2 else 0
    return treble.take(kotlin.math.abs(fifths)).map { it + shift }
}

/** A key: its tonic (the octave does not matter) and mode. */
data class Key(val tonic: Pitch, val minor: Boolean = false) {
    val fifths get() = keyFifths(tonic, minor)
    val label get() = tonic.label + if (minor) " minor" else " major"
    val spoken get() = tonic.spoken + if (minor) " minor" else " major"
    /** The relative key: same signature, the other mode (a minor third away). */
    val relative get() = if (minor) Key(transposeSpelled(tonic, 3, 2), false) else Key(transposeSpelled(tonic, -3, -2), true)
}

/** [p] moved by [semis] half steps and [letters] letters, spelled to match (C up 3 half steps and 2 letters is E flat). */
fun transposeSpelled(p: Pitch, semis: Int, letters: Int): Pitch {
    val target = p.midi + semis
    val plain = p.letterUp(letters)
    return Pitch(plain.letter, target - plain.midi, plain.octave)
}

/** The circle of fifths clockwise from C at the top: each a fifth above the one before; the bottom holds the flat keys. */
val CIRCLE_MAJORS: List<Key> = listOf("C4", "G4", "D4", "A4", "E4", "B4", "F#4", "Db4", "Ab4", "Eb4", "Bb4", "F4").map { Key(p(it)) }
val CIRCLE_MINORS: List<Key> = CIRCLE_MAJORS.map { it.relative }

// ---- Scales ------------------------------------------------------------------------------------------------------

enum class ScaleKind(val label: String, val steps: List<Int>) {
    MAJOR("major", listOf(2, 2, 1, 2, 2, 2, 1)),
    NATURAL_MINOR("natural minor", listOf(2, 1, 2, 2, 1, 2, 2)),
    HARMONIC_MINOR("harmonic minor", listOf(2, 1, 2, 2, 1, 3, 1)),
    MELODIC_MINOR("melodic minor", listOf(2, 1, 2, 2, 2, 2, 1)),
}

/** Eight notes from [tonic] up to its octave, one per letter, spelled so the steps match [kind]. */
fun scale(tonic: Pitch, kind: ScaleKind = ScaleKind.MAJOR): List<Pitch> {
    var semis = 0
    return listOf(tonic) + kind.steps.mapIndexed { i, s -> semis += s; transposeSpelled(tonic, semis, i + 1) }
}

/** "W" for a whole step, "H" for a half step, "W+H" for the harmonic minor's step and a half. */
fun stepLabels(kind: ScaleKind) = kind.steps.map { when (it) { 1 -> "H"; 2 -> "W"; else -> "W+H" } }

// ---- Intervals ---------------------------------------------------------------------------------------------------

enum class Quality(val label: String) { PERFECT("perfect"), MAJOR("major"), MINOR("minor"), AUGMENTED("augmented"), DIMINISHED("diminished") }

data class Interval(val number: Int, val quality: Quality, val semitones: Int) {
    /** "major 3rd", "perfect 5th", "octave", "unison". */
    val name get() = when {
        number == 1 && quality == Quality.PERFECT -> "unison"
        number == 8 && quality == Quality.PERFECT -> "octave"
        else -> "${quality.label} ${ordinal(number)}"
    }
}

fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

/** The interval from [low] up to [high], named by letters (number) and half steps (quality). Up to an octave. */
fun interval(low: Pitch, high: Pitch): Interval {
    val number = high.diatonic - low.diatonic + 1
    require(number in 1..8) { "interval out of range: ${low.code} to ${high.code}" }
    val semis = high.midi - low.midi
    val perfectKind = number in setOf(1, 4, 5, 8)
    val major = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12)[number - 1]     // the major or perfect size
    val q = if (perfectKind) when (semis - major) { 0 -> Quality.PERFECT; 1 -> Quality.AUGMENTED; -1 -> Quality.DIMINISHED; else -> error("odd interval") }
        else when (semis - major) { 0 -> Quality.MAJOR; -1 -> Quality.MINOR; 1 -> Quality.AUGMENTED; -2 -> Quality.DIMINISHED; else -> error("odd interval") }
    return Interval(number, q, semis)
}

/** The common name of an interval of [semis] half steps (0..12); 6 is the tritone. */
fun intervalName(semis: Int): String = listOf("unison", "minor 2nd", "major 2nd", "minor 3rd", "major 3rd", "perfect 4th", "tritone",
    "perfect 5th", "minor 6th", "major 6th", "minor 7th", "major 7th", "octave")[semis]

/** Spoken form: "minor 2nd" becomes "minor second". */
fun spokenOrdinals(s: String) = s.replace("2nd", "second").replace("3rd", "third").replace("4th", "fourth").replace("5th", "fifth")
    .replace("6th", "sixth").replace("7th", "seventh")

/** Consonant intervals sound settled (unison, 3rds, perfect 4th and 5th, 6ths, octave); the rest pull to move on. */
fun consonant(semis: Int) = Math.floorMod(semis, 12) in setOf(0, 3, 4, 5, 7, 8, 9)

// ---- Chords ------------------------------------------------------------------------------------------------------

enum class TriadQuality(val label: String, val third: Int, val fifth: Int) {
    MAJOR("major", 4, 7), MINOR("minor", 3, 7), DIMINISHED("diminished", 3, 6), AUGMENTED("augmented", 4, 8)
}

data class Triad(val numeral: String, val quality: TriadQuality, val notes: List<Pitch>) {
    val root get() = notes[0]
    /** "C", "Dm", "B°". */
    val symbol get() = root.label + when (quality) { TriadQuality.MAJOR -> ""; TriadQuality.MINOR -> "m"; TriadQuality.DIMINISHED -> "°"; TriadQuality.AUGMENTED -> "+" }
    val spoken get() = root.spoken + " " + quality.label
}

/** A triad stacked in thirds on [root]. */
fun triad(root: Pitch, quality: TriadQuality) = listOf(root, transposeSpelled(root, quality.third, 2), transposeSpelled(root, quality.fifth, 4))

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII")

/** The seven triads built on a major scale: I ii iii IV V vi vii° (upper case major, lower case minor, ° diminished). */
fun diatonicTriads(tonic: Pitch): List<Triad> {
    val s = scale(tonic).dropLast(1)
    val ext = s + s.map { Pitch(it.letter, it.accidental, it.octave + 1) }
    return (0..6).map { i ->
        val notes = listOf(ext[i], ext[i + 2], ext[i + 4])
        val third = notes[1].midi - notes[0].midi; val fifth = notes[2].midi - notes[0].midi
        val q = TriadQuality.entries.first { it.third == third && it.fifth == fifth }
        val numeral = when (q) {
            TriadQuality.MAJOR -> ROMAN[i]; TriadQuality.MINOR -> ROMAN[i].lowercase()
            TriadQuality.DIMINISHED -> ROMAN[i].lowercase() + "°"; TriadQuality.AUGMENTED -> ROMAN[i] + "+"
        }
        Triad(numeral, q, notes)
    }
}

/** [notes] (root position, low to high) with the lowest [n] moved up an octave. */
fun invert(notes: List<Pitch>, n: Int): List<Pitch> {
    val out = notes.toMutableList()
    repeat(n) { val low = out.removeAt(0); out += Pitch(low.letter, low.accidental, low.octave + 1) }
    return out
}
