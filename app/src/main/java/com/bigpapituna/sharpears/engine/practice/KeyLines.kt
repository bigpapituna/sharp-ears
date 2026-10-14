package com.bigpapituna.sharpears.engine.practice

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.isBlack

/*
 * The pup's one line about a key on the Scales screen: the key signature (or what makes the scale special) and
 * where the thumbs go, worked out from the same tables the keyboard shows. Note names are words ("E flat"), as
 * everywhere the pup speaks.
 */

private const val LETTERS = "CDEFGAB"
private val LETTER_PC = intArrayOf(0, 2, 4, 5, 7, 9, 11)
private val NUMBER_WORDS = listOf("no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve")

/** 3 as "three"; past twelve, digits. */
fun numberWord(n: Int): String = NUMBER_WORDS.getOrNull(n) ?: n.toString()

/** "Eb" as "E flat", "F#" as "F sharp", "C" as "C". */
fun spokenName(name: String): String =
    name.take(1) + name.drop(1).map { if (it == '#') " sharp" else if (it == 'b') " flat" else "" }.joinToString("")

/**
 * The note [pc] spelled as the letter [letterSteps] above [rootName]'s letter: in E flat major the third step
 * is G, in A harmonic minor the raised seventh is G sharp. A double sharp or flat comes back as the plain name
 * (by [flats]) unless [allowDouble].
 */
fun spell(rootName: String, letterSteps: Int, pc: Int, flats: Boolean, allowDouble: Boolean = true): String {
    val li = Math.floorMod(LETTERS.indexOf(rootName[0]) + letterSteps, 7)
    val acc = Math.floorMod(pc - LETTER_PC[li] + 6, 12) - 6
    val suffix = when (acc) { 0 -> ""; 1 -> " sharp"; -1 -> " flat"; 2 -> " double sharp"; -2 -> " double flat"; else -> null }
    if (suffix == null || (!allowDouble && kotlin.math.abs(acc) == 2)) return spokenName(plainName(pc, flats))
    return LETTERS[li] + suffix
}

private fun plainName(pc: Int, flats: Boolean) =
    (if (flats) listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
     else listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"))[Math.floorMod(pc, 12)]

/** Letter steps from the root for each note of a scale type (pentatonics skip letters; the blues fifth is a flat fifth). */
private fun letterSteps(type: ScaleType): List<Int> = when (type) {
    ScaleType.MAJOR_PENTATONIC -> listOf(0, 1, 2, 4, 5)
    ScaleType.MINOR_PENTATONIC -> listOf(0, 2, 3, 4, 6)
    ScaleType.BLUES -> listOf(0, 2, 3, 4, 4, 6)
    ScaleType.CHROMATIC -> emptyList()
    else -> (0..6).toList()
}

/** The notes of one octave of a scale, spelled for its key ("E flat", "F", "G", ...). Chromatic: plain names. */
fun spelledScale(root: Int, type: ScaleType): List<String> {
    val rootName = scaleRootName(root, type)
    val flats = flatsFor(root, type)
    if (type == ScaleType.CHROMATIC) return type.steps.map { spokenName(plainName(root + it, flats)) }
    return type.steps.zip(letterSteps(type)).map { (s, l) -> spell(rootName, l, root + s, flats) }
}

/** Fifths from C of a major key's pitch class, F sharp as six sharps, D flat as five flats. */
private fun majorFifths(pc: Int) = listOf(0, -5, 2, -3, 4, -1, 6, 1, -4, 3, -2, 5)[Math.floorMod(pc, 12)]

/**
 * The key signature a scale is written with: sharps (positive) or flats (negative). Minor scales and the modes
 * use the signature of their relative major; pentatonics and the blues that of their parent major or minor;
 * the chromatic scale has none.
 */
fun keySignature(root: Int, type: ScaleType): Int {
    if (type == ScaleType.CHROMATIC) return 0
    val major = when (type) {
        ScaleType.DORIAN -> root - 2
        ScaleType.MIXOLYDIAN -> root + 5
        else -> if (type.minorFamily) root + 3 else root
    }
    var f = majorFifths(major)
    val flats = flatsFor(root, type)
    if (flats && f > 0) f -= 12
    if (!flats && f < 0) f += 12
    return f
}

/** "three flats", "one sharp", "no sharps or flats". */
fun signatureWords(fifths: Int): String = when {
    fifths == 0 -> "no sharps or flats"
    fifths > 0 -> "${numberWord(fifths)} sharp${if (fifths == 1) "" else "s"}"
    else -> "${numberWord(-fifths)} flat${if (fifths == -1) "" else "s"}"
}

/** "C", "C and F", "C, F and G". */
fun listWords(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " and " + items.last()
}

private fun capitalized(s: String) = s.replaceFirstChar { it.uppercase() }

/** Where the thumb plays in [notes] fingered [fingers]: the distinct pitch classes, in the order they come. */
internal fun thumbPitches(notes: List<Int>, fingers: List<Int>): List<Int> =
    notes.zip(fingers).filter { it.second == 1 }.map { Math.floorMod(it.first, 12) }.distinct()

/**
 * One or two sentences about the thumbs for [hands] (right, left or both), given each hand's notes and fingers
 * and a namer for pitch classes. Says when the thumb never meets a black key, since that is what makes a key
 * comfortable.
 */
private fun thumbLine(
    hands: Practice, notes: (Hand) -> List<Int>, fingers: (Hand) -> List<Int>, name: (Int) -> String, eachOctave: Boolean,
): String {
    val used = when (hands) { Practice.RIGHT -> listOf(Hand.RIGHT); Practice.LEFT -> listOf(Hand.LEFT); Practice.BOTH -> listOf(Hand.RIGHT, Hand.LEFT) }
    val thumbs = used.associateWith { thumbPitches(notes(it), fingers(it)) }
    val anyBlackKey = used.any { h -> notes(h).any(::isBlack) }
    val thumbOnBlack = thumbs.values.flatten().filter { isBlack(it) }.distinct()
    val tail = if (eachOctave) " each octave" else ""
    return when {
        thumbOnBlack.isNotEmpty() -> "The thumb takes a black key here, ${listWords(thumbOnBlack.map(name))}, so keep the hand forward."
        anyBlackKey && used.size == 1 && thumbs.getValue(used[0]).size == 1 ->
            "The thumb stays on ${name(thumbs.getValue(used[0])[0])}$tail, never a black key."
        anyBlackKey -> "The thumb never lands on a black key here."
        used.size == 1 -> "The ${if (used[0] == Hand.RIGHT) "right" else "left"} thumb plays ${listWords(thumbs.getValue(used[0]).map(name))}$tail."
        else -> "Right thumb on ${listWords(thumbs.getValue(Hand.RIGHT).map(name))}, left thumb on ${listWords(thumbs.getValue(Hand.LEFT).map(name))}."
    }
}

/** The pup's line about [scale] played with [hands]: "E flat major has three flats. The thumb never lands on a black key here." */
fun scaleLine(scale: Scale, hands: Practice): String {
    val type = scale.type
    val title = spokenName(scale.rootName) + " " + type.label.lowercase()
    val spelled = spelledScale(scale.root, type)
    val byPc = type.steps.zip(spelled).associate { (s, n) -> Math.floorMod(scale.root + s, 12) to n }
    val name = { pc: Int -> byPc[pc] ?: spokenName(plainName(pc, scale.flats)) }
    val first = when (type) {
        ScaleType.HARMONIC_MINOR -> "$title is natural minor with the seventh raised to ${spelled[6]}."
        ScaleType.MELODIC_MINOR -> "$title raises its sixth and seventh going up, not coming down."
        ScaleType.MAJOR_PENTATONIC, ScaleType.MINOR_PENTATONIC -> "$title has five notes and no half steps."
        ScaleType.BLUES -> "$title is the minor pentatonic plus one blue note, ${spelled[3]}."
        ScaleType.CHROMATIC -> return "The chromatic scale plays every key in a row. Third finger on the black keys, thumb on the white ones."
        else -> "$title has ${signatureWords(keySignature(scale.root, type))}."
    }
    return first + " " + thumbLine(hands, scale::ascending, scale::fingering, name, eachOctave = false)
}

/** Letter steps of an arpeggio's chord tones (a seventh chord adds the seventh letter). */
private fun arpeggioLetters(type: ArpeggioType) = if (type.intervals.size == 3) listOf(0, 2, 4) else listOf(0, 2, 4, 6)

/** The chord tones of [arp], spelled: "E flat, G and B flat". A diminished seventh's double flat is named plainly. */
fun arpeggioTones(arp: Arpeggio): List<String> = arp.type.intervals.zip(arpeggioLetters(arp.type)).map { (s, l) ->
    spell(arp.rootName, l, arp.root + s, arp.flats, allowDouble = false)
}

/** The pup's line about [arp]: "E flat major is E flat, G and B flat. The thumb stays on G each octave, never a black key." */
fun arpeggioLine(arp: Arpeggio, hands: Practice): String {
    val tones = arpeggioTones(arp)
    val byPc = arp.type.intervals.zip(tones).associate { (s, n) -> Math.floorMod(arp.root + s, 12) to n }
    val name = { pc: Int -> byPc[pc] ?: spokenName(plainName(pc, arp.flats)) }
    val title = spokenName(arp.rootName) + " " + arp.type.label.lowercase()
    return "$title is ${listWords(tones)}. " + thumbLine(hands, arp::ascending, arp::fingering, name, eachOctave = true)
}

/** "E flat major", "F sharp minor": a key's title as words, for headings. */
fun spokenTitle(rootName: String, label: String) = capitalized(spokenName(rootName) + " " + label.lowercase())
