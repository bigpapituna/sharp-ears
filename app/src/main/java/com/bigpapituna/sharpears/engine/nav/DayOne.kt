package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.Chord
import com.bigpapituna.sharpears.engine.ChordType
import com.bigpapituna.sharpears.engine.family.Level

/**
 * Day one follows placement (2.14). The persona walk: Dana (never played) was asked on her first night to "Play C between C3
 * and G4, any order" before her first lesson; Sam and Marc (some, a lot) got "Find a C", a microphone they never chose and
 * Twinkle as their easy first song, and Sam stayed "Newcomer". The rules, one per question; Today, the plan, the check and
 * the chord screen ask them. Pure (DayOneTest).
 */
object DayOne {
    /** A beginner's plan starts its chord review only after this many course lessons were played (placement skips not counted). */
    const val CHORDS_AFTER_LESSONS = 2

    /** Never and a little: the course comes first and the words stay plain. */
    fun beginner(level: Level): Boolean = level == Level.NEVER || level == Level.A_LITTLE

    /** Today's plan opens on the next lesson (then the chords, then a song) for a beginner; chords first for the rest. */
    fun lessonFirst(level: Level): Boolean = beginner(level)

    /** Whether the plan may hold the chord review: always for some and a lot, after [CHORDS_AFTER_LESSONS] lessons for a beginner. */
    fun chordsOpen(level: Level, lessonsPlayed: Int): Boolean = !beginner(level) || lessonsPlayed >= CHORDS_AFTER_LESSONS

    /** The chords due today as the plan takes them: none until [chordsOpen]. */
    fun chordsFor(level: Level, lessonsPlayed: Int, due: List<String>): List<String> = if (chordsOpen(level, lessonsPlayed)) due else emptyList()

    /**
     * The easy first song (Twinkle: "Start here", "Let's start with an easy song", the plan's song before any was played) for
     * a beginner only. Some and a lot get a song by chords as their first song and no library song in the plan until they
     * play one.
     */
    fun easySongDefault(level: Level): Boolean = beginner(level)

    /** Today's microphone banner: only for someone who said they play into the microphone, until it is allowed or put away. */
    fun micBanner(useMic: Boolean, micAllowed: Boolean, closed: Boolean): Boolean = useMic && !micAllowed && !closed

    /** "Let's hear you play" asks for the microphone up front unless a grown-up chose the screen's keys or MIDI (a kid always listens). */
    fun checkAsksMic(useMic: Boolean, kid: Boolean): Boolean = useMic || kid

    /** The chord review in plain words ("Play a C chord", its keys lit): for a beginner. */
    fun plainChords(level: Level): Boolean = beginner(level)
}

/**
 * A chord asked for in plain words (2.14): "Play a C chord", "Play an A minor chord", and the keys named as letters that the
 * screen lights ("C, E and G are lit. Any octave counts."). Never octave numbers or "any order". Spoken and shown alike.
 */
object ChordWords {
    /** "C", "F sharp", "B flat": the root as words. */
    fun root(c: Chord): String = c.name.removeSuffix(c.type.suffix).let(::letters)

    /** "C", "A minor", "D 7": the chord as words. */
    fun name(c: Chord): String = root(c) + when (c.type) {
        ChordType.MAJOR -> ""
        ChordType.MINOR -> " minor"
        else -> " " + c.type.label
    }

    /** "a" or "an", by how the first letter sounds: an A, an E, an F. */
    fun article(c: Chord): String = if (root(c).first() in "AEF") "an" else "a"

    /** "Play a C chord", "Play an A minor chord". */
    fun ask(c: Chord): String = "Play ${article(c)} ${name(c)} chord"

    /** "C, E and G are lit. Any octave counts." */
    fun keysLine(c: Chord): String {
        val tones = c.voicing(0).map { letters(c.toneName(it)) }
        return tones.dropLast(1).joinToString(", ") + " and " + tones.last() + " are lit. Any octave counts."
    }

    /** "F#" to "F sharp", "Bb" to "B flat". */
    private fun letters(note: String): String = when {
        note.length == 2 && note[1] == '#' -> "${note[0]} sharp"
        note.length == 2 && note[1] == 'b' -> "${note[0]} flat"
        else -> note
    }
}
