package com.bigpapituna.sharpears.engine.skin

import com.bigpapituna.sharpears.engine.SpokenCatalog
import com.bigpapituna.sharpears.engine.noteNumber
import com.bigpapituna.sharpears.engine.playground.PageLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.2.1 QA: every word that pairs a key with its colour or animal says what [NoteColours] paints. The pages keep their own
 * table for the letter hunts (PageLines.ANIMALS) and the copy names pairs in prose ("C is a red cat", "the red cat key",
 * Settings' "C red, D orange"), so each is checked against the one table the lane, the keys and the badges use.
 */
class ColourWordsTest {
    private val colours = NoteColours.WHITE.map { it.name }
    private val animals = Animal.entries.map { it.word }
    private fun byLetter(letter: Char) = NoteColours.white(noteNumber("${letter}4"))!!

    @Test fun theHuntsTableIsTheSkinsTable() {
        for ((letter, pair) in PageLines.ANIMALS) assertEquals("$letter", byLetter(letter).name to byLetter(letter).animal.word, pair)
    }

    @Test fun everySpokenPairOfColourAndAnimalOrLetterAgrees() {
        val colour = colours.joinToString("|"); val animal = animals.joinToString("|")
        val pairs = Regex("\\b($colour) ($animal)\\b")
        val letterFirst = Regex("\\b([A-G]) (?:is )?an? ($colour) ($animal)\\b")
        val colourLetter = Regex("\\b($colour) ([A-G]) key\\b")
        var checked = 0
        for (line in SpokenCatalog.all() + sourceLiterals()) {
            pairs.findAll(line).forEach { m -> checked++; assertEquals(line, NoteColours.WHITE.first { it.name == m.groupValues[1] }.animal.word, m.groupValues[2]) }
            letterFirst.findAll(line).forEach { m -> assertEquals(line, byLetter(m.groupValues[1][0]).name to byLetter(m.groupValues[1][0]).animal.word, m.groupValues[2] to m.groupValues[3]) }
            colourLetter.findAll(line).forEach { m -> assertEquals(line, byLetter(m.groupValues[2][0]).name, m.groupValues[1]) }
            // Settings and the skin's own words list the table: "C red, D orange, E yellow ...".
            Regex("\\b([A-G]) ($colour)\\b").findAll(line).forEach { m -> assertEquals(line, byLetter(m.groupValues[1][0]).name, m.groupValues[2]) }
        }
        assertTrue("found the pairs: $checked", checked >= 10)
    }

    /** String literals in the app's Kotlin sources (call-outs drawn by ui/ that the voice catalog does not list). */
    private fun sourceLiterals(): List<String> =
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.flatMap { f -> Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(f.readText()).map { it.groupValues[1] } }.toList()
}
