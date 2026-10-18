package com.bigpapituna.sharpears.engine.playground

import com.bigpapituna.sharpears.engine.BUNS_X
import com.bigpapituna.sharpears.engine.Exercise
import com.bigpapituna.sharpears.engine.LIGHTLY_X
import com.bigpapituna.sharpears.engine.LONDON_X
import com.bigpapituna.sharpears.engine.MACDONALD_X
import com.bigpapituna.sharpears.engine.MARY_X
import com.bigpapituna.sharpears.engine.ODE_LINE
import com.bigpapituna.sharpears.engine.Practice
import com.bigpapituna.sharpears.engine.ROW_X
import com.bigpapituna.sharpears.engine.Step
import com.bigpapituna.sharpears.engine.TWINKLE
import com.bigpapituna.sharpears.engine.YANKEE_X
import com.bigpapituna.sharpears.engine.bite

/*
 * Songs for an audience (2.2): every song a kid learns on the Playground's pages sits on their shelf, with the start
 * they warm up on and the version they play with the band. The pond's concert offers three of them; the concert hall's
 * recital plays the three the kid picked in "Pick your three" (stored per profile, Recital.parse/format).
 */

/** A song the band plays with you (right hand, the band on the left), slower than in the course, passing at [CONCERT_PASS]. */
private fun band(ex: Exercise, bpm: Double) = ex.copy(bpm = bpm, practice = Practice.RIGHT, pass = CONCERT_PASS, phraseEnd = null)

/** The start of a song as a warm up: its first phrase, right hand, the notes waiting. */
private fun start(ex: Exercise, phraseEnd: Double?, bpm: Double = ex.bpm) = ex.copy(bpm = bpm, practice = Practice.RIGHT, phraseEnd = phraseEnd).bite()

/**
 * A song on the shelf: [id] (stored in a kid's recital), its [title] and [picture] (the cards draw one per picture),
 * the [start] played in "Pick your three", and the [concert] version played with the band.
 */
data class ShelfSong(val id: String, val title: String, val picture: String, val start: Exercise, val concert: ConcertSong)

private fun shelf(id: String, title: String, picture: String, start: Exercise, concert: Exercise) =
    ShelfSong(id, title, picture, start, ConcertSong(title, picture, concert))

/** Every song learned on the Playground's three pages, in the order they are learned. */
val SHELF: List<ShelfSong> = listOf(
    shelf("buns", "Hot Cross Buns", "buns", BUNS_X.copy(practice = Practice.RIGHT).bite(), CONCERT_SONGS[0].exercise),
    shelf("lamb", "Mary Had a Little Lamb", "lamb", MARY_X.copy(practice = Practice.RIGHT).bite(), CONCERT_SONGS[1].exercise),
    shelf("twinkle", "Twinkle, Twinkle, Little Star", "star", start(TWINKLE, 8.0, 80.0), band(TWINKLE, 72.0)),
    shelf("row", "Row, Row, Row Your Boat", "boat", start(ROW_X, 6.0, 72.0), band(ROW_X, 72.0)),
    shelf("farm", "Old MacDonald Had a Farm", "farm", start(MACDONALD_X, 8.0, 80.0), band(MACDONALD_X, 80.0)),
    shelf("bridge", "London Bridge", "bridge", start(LONDON_X, 4.0, 72.0), band(LONDON_X, 76.0)),
    shelf("ode", "Ode to Joy", "five", ODE_LINE.bite(), CONCERT_SONGS[2].exercise),
    shelf("lightly", "Lightly Row", "waves", start(LIGHTLY_X, 8.0, 76.0), band(LIGHTLY_X, 80.0)),
    shelf("yankee", "Yankee Doodle", "hat", start(YANKEE_X, 8.0, 84.0), band(YANKEE_X, 84.0)),
)

fun shelfSong(id: String): ShelfSong? = SHELF.firstOrNull { it.id == id }

/** The pond's concert (lesson 12): pick one of the three songs learned there. */
val POND_SONGS: List<ConcertSong> = listOf("twinkle", "row", "farm").map { shelfSong(it)!!.concert }

/** The recital (the concert hall's last lesson): the three songs a kid picked, kept per profile as their ids. */
object Recital {
    const val SIZE = 3
    /** When nothing was picked yet (a debug route, a skipped lesson): three songs every page teaches. */
    val DEFAULT = listOf("twinkle", "farm", "ode")

    /** The kid's [ids] as three distinct shelf songs: unknown ids dropped, the rest filled from [DEFAULT]. */
    fun songs(ids: List<String>?): List<ShelfSong> =
        ((ids.orEmpty() + DEFAULT).distinct().mapNotNull(::shelfSong)).take(SIZE)

    fun format(ids: List<String>): String = ids.joinToString(",")
    fun parse(text: String?): List<String> = text.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * How a lesson's concert runs: in [place] (the stage's frame and audience), with [songs] to pick from ([pick]) or, for
 * the recital, the one song this step plays.
 */
data class ConcertPlan(val place: Place, val songs: List<ConcertSong>, val pick: Boolean)

/**
 * The concert for [step] of lesson [lessonId]: the garden's and the pond's let the kid pick; the concert hall's recital
 * plays the kid's [recital] songs (ids, see [Recital]) one per step, in order.
 */
fun concertPlan(lessonId: String, step: Step, recital: List<String>? = null): ConcertPlan {
    val page = pageOf(lessonId)
    return when (page?.place) {
        Place.POND -> ConcertPlan(Place.POND, POND_SONGS, pick = true)
        Place.CONCERT_HALL -> {
            val lesson = HALL.lessons.first { it.id == lessonId }
            val nth = lesson.steps.filter { it.game == Game.CONCERT }.indexOfFirst { it.text == step.text }.coerceAtLeast(0)
            ConcertPlan(Place.CONCERT_HALL, listOf(Recital.songs(recital)[nth % Recital.SIZE].concert), pick = false)
        }
        else -> ConcertPlan(Place.GARDEN, CONCERT_SONGS, pick = true)
    }
}
