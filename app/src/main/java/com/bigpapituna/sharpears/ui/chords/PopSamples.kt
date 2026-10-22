package com.bigpapituna.sharpears.ui.chords

import com.bigpapituna.sharpears.engine.ChordType.MAJOR
import com.bigpapituna.sharpears.engine.ChordType.MINOR
import com.bigpapituna.sharpears.engine.chords.PopBar
import com.bigpapituna.sharpears.engine.chords.PopChord
import com.bigpapituna.sharpears.engine.chords.PopSection
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.chords.PopSong.Confidence
import com.bigpapituna.sharpears.engine.chords.PopSong.Pattern
import com.bigpapituna.sharpears.engine.chords.PopSong.Style

/*
 * ===================================================================================================================
 * SAMPLE SONGS FOR THE INTEGRATOR TO DELETE.
 *
 * Six songs so the screens in this folder can be built and shown before the real list (engine/chords/PopSongs.kt,
 * `POP_SONGS`, written by popc) exists on this branch. Every screen takes its list as a parameter; the debug route and
 * the Chords tab use [POP_SAMPLES] only until the integrator swaps in POP_SONGS (see INTEGRATION.md). Delete this file
 * then.
 *
 * Chords, keys, tempos and section names only. No melody, no lyric, no lyric fragment anywhere, notes included.
 * Confidence is honest: only the two that the author is sure of say CHECKED.
 * ===================================================================================================================
 */

private const val C = 0
private const val D = 2
private const val E = 4
private const val F = 5
private const val FS = 6
private const val G = 7
private const val A = 9

private fun maj(r: Int) = PopChord(r, MAJOR)
private fun min(r: Int) = PopChord(r, MINOR)
private fun bar(c: PopChord) = PopBar(c)
private fun bar(a: PopChord, b: PopChord) = PopBar(a, b)

val POP_SAMPLES: List<PopSong> = listOf(
    PopSong(
        id = "sample-let-it-be", title = "Let It Be", artist = "The Beatles", year = 1970,
        styles = listOf(Style.GREATEST_HITS, Style.BALLAD), key = C, minor = false, bpm = 72,
        sections = listOf(
            PopSection("Verse", listOf(bar(maj(C)), bar(maj(G)), bar(min(A)), bar(maj(F)), bar(maj(C)), bar(maj(G)), bar(maj(F)), bar(maj(C)))),
            PopSection("Chorus", listOf(bar(min(A)), bar(maj(G)), bar(maj(F)), bar(maj(C)), bar(maj(C)), bar(maj(G)), bar(maj(F)), bar(maj(C)))),
        ),
        pattern = Pattern.BALLAD_ARPEGGIO, difficulty = 2, confidence = Confidence.CHECKED,
        note = "Four chords, a bar each. Take it slowly and let the right hand roll them.",
    ),
    PopSong(
        id = "sample-stand-by-me", title = "Stand By Me", artist = "Ben E. King", year = 1961,
        styles = listOf(Style.GREATEST_HITS, Style.SOUL), key = A, minor = false, bpm = 118,
        sections = listOf(
            PopSection("Intro", listOf(bar(maj(A)), bar(maj(A)))),
            PopSection("Verse", listOf(bar(maj(A)), bar(maj(A)), bar(min(FS)), bar(min(FS)), bar(maj(D)), bar(maj(E)), bar(maj(A)), bar(maj(A)))),
            PopSection("Chorus", listOf(bar(maj(A)), bar(maj(A)), bar(min(FS)), bar(min(FS)), bar(maj(D), maj(E)), bar(maj(A)))),
        ),
        pattern = Pattern.SHUFFLE, difficulty = 2, confidence = Confidence.CHECKED,
        note = "The famous four: one, six, four, five. The bass leads, so keep the left hand steady.",
    ),
    PopSong(
        id = "sample-knockin", title = "Knockin' on Heaven's Door", artist = "Bob Dylan", year = 1973,
        styles = listOf(Style.ROCK, Style.COUNTRY), key = G, minor = false, bpm = 68,
        sections = listOf(
            PopSection("Verse", listOf(bar(maj(G)), bar(maj(D)), bar(min(A)), bar(min(A)), bar(maj(G)), bar(maj(D)), bar(maj(C)), bar(maj(C)))),
            PopSection("Chorus", listOf(bar(maj(G)), bar(maj(D)), bar(min(A)), bar(min(A)), bar(maj(G)), bar(maj(D)), bar(maj(C)), bar(maj(C)))),
        ),
        pattern = Pattern.HALVES, difficulty = 1, confidence = Confidence.LIKELY,
        note = "Three chords going round, and the minor one stays for two bars.",
    ),
    PopSong(
        id = "sample-someone-like-you", title = "Someone Like You", artist = "Adele", year = 2011,
        styles = listOf(Style.POP, Style.BALLAD), key = A, minor = false, bpm = 67,
        sections = listOf(
            PopSection("Verse", listOf(bar(maj(A)), bar(maj(E)), bar(min(FS)), bar(maj(D)), bar(maj(A)), bar(maj(E)), bar(min(FS)), bar(maj(D)))),
            PopSection("Chorus", listOf(bar(maj(D)), bar(maj(A)), bar(maj(E)), bar(min(FS)), bar(maj(D)), bar(maj(A)), bar(maj(E)), bar(maj(E)))),
        ),
        pattern = Pattern.BALLAD_ARPEGGIO, difficulty = 2, confidence = Confidence.SKETCH,
        note = "A sketch of the shape: the verse's four chords go round. Check the real chart.",
    ),
    PopSong(
        id = "sample-twist-and-shout", title = "Twist and Shout", artist = "The Isley Brothers", year = 1962,
        styles = listOf(Style.ROCK, Style.GREATEST_HITS), key = D, minor = false, bpm = 126,
        sections = listOf(
            PopSection("Verse", listOf(bar(maj(D)), bar(maj(G)), bar(maj(A)), bar(maj(A)), bar(maj(D)), bar(maj(G)), bar(maj(A)), bar(maj(A)))),
            PopSection("Chorus", listOf(bar(maj(D)), bar(maj(D)), bar(maj(G)), bar(maj(G)), bar(maj(A)), bar(maj(A)), bar(maj(D), maj(A)), bar(maj(D)))),
        ),
        pattern = Pattern.EIGHTHS_ROCK, difficulty = 3, confidence = Confidence.LIKELY,
        note = "Only one, four and five. All the work is in the drive.",
    ),
    PopSong(
        id = "sample-twinkle", title = "Twinkle, Twinkle, Little Star", artist = "Traditional", year = 1806,
        styles = listOf(Style.KIDS), key = C, minor = false, bpm = 88,
        sections = listOf(
            PopSection("Verse", listOf(bar(maj(C)), bar(maj(F), maj(C)), bar(maj(F), maj(C)), bar(maj(G), maj(C)))),
            PopSection("Chorus", listOf(bar(maj(F), maj(C)), bar(maj(F), maj(C)), bar(maj(F), maj(C)), bar(maj(G), maj(C)))),
        ),
        pattern = Pattern.HALVES, difficulty = 1, confidence = Confidence.CHECKED,
        note = "Only C, F and G, the first three chords everybody learns.",
    ),
)
