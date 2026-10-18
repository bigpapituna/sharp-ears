package com.bigpapituna.sharpears.engine.skin

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The animal each white key's letter stands for, for kids who cannot read yet (docs/KIDS-PLAN.md section 3). */
enum class Animal(val word: String) { CAT("cat"), DOG("dog"), ELEPHANT("elephant"), FROG("frog"), GOAT("goat"), ANT("ant"), BEAR("bear") }

/** One of the seven colours: its letter, the colour's name as the pup says it, the colour (ARGB) and its animal. */
data class NoteColour(val letter: Char, val name: String, val argb: Long, val animal: Animal)

/**
 * Boomwhacker / Chroma-Notes colours, the ones many kids' piano books use: C red, D orange, E yellow, F green,
 * G teal, A blue, B purple. Every C is red, whatever the octave.
 *
 * Black keys (decided 2026-10-18): a black key is drawn as **diagonal stripes of its two white neighbours** (C sharp
 * is red and orange stripes), never as a blended colour. A blend of two neighbours is too close to one of them
 * (F sharp would read as a slightly odd green or teal), while stripes say "between these two" at a glance, which is
 * how a child finds a black key anyway. [blend] exists only for places too small for stripes (a 2 dp outline) and
 * TalkBack, which says "between red and orange".
 *
 * The values are tuned for the stage (the dark lane) and the cream keys: bright enough to glow on the lane, and
 * each one gets the ink (dark or light) with the better contrast for a letter on it ([inkFor]).
 */
object NoteColours {
    val WHITE: List<NoteColour> = listOf(
        NoteColour('C', "red", 0xFFE5484DL, Animal.CAT),
        NoteColour('D', "orange", 0xFFF2892BL, Animal.DOG),
        NoteColour('E', "yellow", 0xFFF4C430L, Animal.ELEPHANT),
        NoteColour('F', "green", 0xFF4DB560L, Animal.FROG),
        NoteColour('G', "teal", 0xFF1AAE9FL, Animal.GOAT),
        NoteColour('A', "blue", 0xFF3D85D8L, Animal.ANT),
        NoteColour('B', "purple", 0xFF9563D6L, Animal.BEAR),
    )

    /** Dark ink for letters on light colours, light ink on dark ones (both off-black / off-white, as the app's keys). */
    const val INK_DARK = 0xFF15100CL
    const val INK_LIGHT = 0xFFFFF8F0L

    // Pitch class -> index in WHITE of the white key at or just below it.
    private val BELOW = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)
    private val BLACK = setOf(1, 3, 6, 8, 10)
    private val SHARP_NAMES = mapOf(1 to "C sharp", 3 to "D sharp", 6 to "F sharp", 8 to "G sharp", 10 to "A sharp")
    private val FLAT_NAMES = mapOf(1 to "D flat", 3 to "E flat", 6 to "G flat", 8 to "A flat", 10 to "B flat")

    fun isBlack(midi: Int) = Math.floorMod(midi, 12) in BLACK

    /** The white key's colour entry, or null for a black key. */
    fun white(midi: Int): NoteColour? = if (isBlack(midi)) null else WHITE[BELOW[Math.floorMod(midi, 12)]]

    /** A black key's two neighbours (below, above); null for a white key. */
    fun neighbours(midi: Int): Pair<NoteColour, NoteColour>? {
        if (!isBlack(midi)) return null
        val i = BELOW[Math.floorMod(midi, 12)]
        return WHITE[i] to WHITE[(i + 1) % 7]
    }

    /** The colour of a white key; a black key's stripes as (lower, upper) come from [stripes]. */
    fun colour(midi: Int): Long = white(midi)?.argb ?: blend(midi)

    /** A black key's two stripe colours (the neighbour below, the neighbour above); null for a white key. */
    fun stripes(midi: Int): Pair<Long, Long>? = neighbours(midi)?.let { (a, b) -> a.argb to b.argb }

    /** The midpoint of a black key's neighbours (only where stripes cannot be drawn); a white key's own colour. */
    fun blend(midi: Int): Long {
        val (a, b) = neighbours(midi) ?: return white(midi)!!.argb
        return mix(a.argb, b.argb, 0.5)
    }

    /** The letter shown on a note: "C", "C#" or, with [flats], "Db". */
    fun letter(midi: Int, flats: Boolean = false): String {
        val pc = Math.floorMod(midi, 12)
        if (!isBlack(midi)) return white(midi)!!.letter.toString()
        return if (flats) "${WHITE[(BELOW[pc] + 1) % 7].letter}b" else "${WHITE[BELOW[pc]].letter}#"
    }

    /** The animal for a white key; black keys have none (a pre-reader finds them between two animals). */
    fun animal(midi: Int): Animal? = white(midi)?.animal

    /**
     * What a note or key carries as its label: the letter for readers, the animal for pre-readers on white keys,
     * nothing for a pre-reader's black key (its stripes say where it is).
     */
    fun label(midi: Int, reader: Boolean, flats: Boolean = false): NoteLabel = when {
        reader -> NoteLabel.Letter(letter(midi, flats))
        isBlack(midi) -> NoteLabel.None
        else -> NoteLabel.Creature(animal(midi)!!)
    }

    /**
     * How the pup names a key out loud: "the red cat key" before reading, "the red C key" after; a black key is
     * "the C sharp key" (or "D flat" with [flats]) for readers and "the black key between the cat and the dog" before.
     * No symbols: sharps and flats are said as words, the same rule as every spoken line.
     */
    fun spokenName(midi: Int, reader: Boolean, flats: Boolean = false): String {
        val pc = Math.floorMod(midi, 12)
        white(midi)?.let { w -> return if (reader) "the ${w.name} ${w.letter} key" else "the ${w.name} ${w.animal.word} key" }
        val (a, b) = neighbours(midi)!!
        return if (reader) "the ${(if (flats) FLAT_NAMES else SHARP_NAMES).getValue(pc)} key"
        else "the black key between the ${a.animal.word} and the ${b.animal.word}"
    }

    /** For TalkBack on a key: "red" or "between red and orange". */
    fun colourName(midi: Int): String = white(midi)?.name ?: neighbours(midi)!!.let { (a, b) -> "between ${a.name} and ${b.name}" }

    /** The ink (INK_DARK or INK_LIGHT) with the higher contrast on [argb]. */
    fun inkFor(argb: Long): Long = if (contrast(argb, INK_DARK) >= contrast(argb, INK_LIGHT)) INK_DARK else INK_LIGHT

    /** WCAG contrast ratio between two opaque colours, 1 to 21. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** WCAG relative luminance of an ARGB colour (alpha ignored). */
    fun luminance(argb: Long): Double {
        fun ch(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    /** [a] moved [t] of the way to [b], channel by channel, opaque. */
    fun mix(a: Long, b: Long, t: Double): Long {
        fun ch(c: Long, shift: Int) = (c shr shift) and 0xFF
        fun m(shift: Int) = Math.round(ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t)
        return (0xFFL shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }
}

/** What a note shows: a letter, an animal, or nothing. */
sealed interface NoteLabel {
    data class Letter(val text: String) : NoteLabel
    data class Creature(val animal: Animal) : NoteLabel
    data object None : NoteLabel
}
