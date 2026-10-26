package com.bigpapituna.sharpears.engine.kid

import com.bigpapituna.sharpears.engine.chords.PopFind
import com.bigpapituna.sharpears.engine.chords.PopSong
import com.bigpapituna.sharpears.engine.family.Audience
import com.bigpapituna.sharpears.engine.family.Level

/**
 * "Skip the garden" (2.14). A reader whose grown-up said "No" lessons starts in the garden; one who breezes through it (three
 * stones at three stars in a row) is offered the pond, once: the garden's other stones are then passed by (placement's marks:
 * done for the path, open to play, no stars), as if the grown-up had answered "A few". Pure (KidLevelsTest).
 */
object GardenSkip {
    /** Stones at three stars in a row that make the offer. */
    const val IN_A_ROW = 3

    /** Where the skip starts them: the pond, as "A few" lessons does. */
    val LEVEL_AFTER: Level = Level.A_LITTLE

    /**
     * The offer shows for a little kid who reads, placed at the garden ([level] NEVER), while the garden still has a stone not
     * played, when the last [IN_A_ROW] garden stones played (in path order, [garden]) all have three stars, and they never said
     * "Not now" ([declined]).
     */
    fun offered(audience: Audience, reader: Boolean, level: Level, garden: List<String>, played: (String) -> Boolean,
                stars: (String) -> Int, declined: Boolean): Boolean {
        if (audience != Audience.LITTLE_KID || !reader || level != Level.NEVER || declined) return false
        val walked = garden.filter(played)
        if (walked.size == garden.size) return false
        return walked.size >= IN_A_ROW && walked.takeLast(IN_A_ROW).all { stars(it) >= 3 }
    }

    const val TITLE = "Too easy?"
    const val LINE = "Three stones, three stars each. Skip the garden and start at the pond. The garden stays open."
    const val SKIP = "Skip the garden"
    const val LATER = "Not now"
}

/**
 * Songs by chords for a little kid who reads (2.14): after the nursery songs on their Songs, three packs of the grown-ups'
 * charts (Pop, Greatest hits, Rock), each opening the kid's chord list for that style (colour blocks with letters, no
 * numbers). Pure (KidLevelsTest).
 */
object KidPop {
    /** The packs, in order. */
    val CHIPS: List<PopFind.Chip> = listOf(PopFind.Chip.POP, PopFind.Chip.GREATEST_HITS, PopFind.Chip.ROCK)

    /**
     * Charts left out of a little kid's packs by their title or what the song is about (only chords are ever shown, but the
     * title is): a prison shooting, a song about dying, an innuendo. The grown-ups' list keeps them.
     */
    val NOT_FOR_LITTLE_KIDS: Set<String> = setOf("folsom-prison-blues", "knockin-on-heavens-door", "watermelon-sugar")

    /** A little kid who reads gets the packs; a pre-reader keeps the colour songs and the kids' chord songs. */
    fun shows(audience: Audience, reader: Boolean): Boolean = audience == Audience.LITTLE_KID && reader

    /** [chip]'s songs for the pack (or the kids' style, as before 2.14, for any other chip), easiest first. */
    fun songs(all: List<PopSong>, chip: PopFind.Chip?): List<PopSong> =
        if (chip == null || chip !in CHIPS) all.filter { PopSong.Style.KIDS in it.styles }.sortedWith(PopFind.easiest)
        else PopFind.under(all, chip).filter { it.id !in NOT_FOR_LITTLE_KIDS }.sortedWith(PopFind.easiest)

    /** The pack's title on its tile and its list. */
    fun title(chip: PopFind.Chip?): String = if (chip == null || chip !in CHIPS) "Songs by chords" else chip.label
}
