package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.kid.KidLines
import com.bigpapituna.sharpears.engine.playground.PLAYGROUND_LEVELS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.2.1 QA: the copy rules over every line a kid hears or reads (SpokenCatalog.kidLines: the kid skin, the three
 * Playground pages, their stages, the play corner, the kid home and finale, the treat jar, the noisy-room offer), and
 * the length of every line that lands in the pup's bubble.
 */
class KidCopyTest {
    private val kid = SpokenCatalog.kidLines()
    /** "the red cat key", "the C sharp key": names the pup puts inside his sentences, not sentences of their own. */
    private fun keyName(l: String) = l.startsWith("the ") && l.endsWith(" key") || l.startsWith("the black key between")
    /** Practice today between parts: "Next: a lesson! <the lesson's title>". */
    private fun nextLesson(l: String) = l.startsWith("Next: a lesson! ")

    @Test fun kidLinesAreSentencesWithoutDashesSymbolsOrScoldingWords() {
        assertTrue(kid.size > 500)
        val banned = Regex("[—–·#&/%]| - |\\b(wrong|mistakes?|errors?|fail\\w*|percent)\\b", RegexOption.IGNORE_CASE)
        for (l in kid) {
            assertFalse("no dashes, symbols or scolding: \"$l\"", banned.containsMatchIn(l))
            assertFalse("single spaces: \"$l\"", "  " in l)
            if (keyName(l)) continue
            assertTrue("starts a sentence: \"$l\"", l.first().isUpperCase())
            if (!nextLesson(l)) assertTrue("ends a sentence: \"$l\"", l.last() in ".!?")
        }
    }

    /** Numbers only where a kid counts notes they copied in Pup says ("Yes! 3 notes."), never a "1 notes" (a lesson title may hold one). */
    @Test fun kidLinesCarryNoNumbersButPupSaysCounts() {
        for (l in kid) if (l.any { it.isDigit() } && !nextLesson(l)) {
            assertTrue("a number in a kid line: \"$l\"", Regex("\\b\\d+ notes\\b|copied all \\d+").containsMatchIn(l))
            assertFalse("plural: \"$l\"", Regex("\\b1 notes\\b|all 1\\.").containsMatchIn(l))
        }
    }

    /**
     * Lines made on the spot say no more than is true and look like the rest: the record shelf told a kid "2 records on
     * the shelf: 1 gold, 1 silver."; a three-star kid line said "Every note was right" after a 92 percent Perform run
     * (three stars start at 90); a pre-reader's Jam caption was "The red cat key" where every other caption ends "!".
     */
    @Test fun madeOnTheSpotLinesKeepTheRules() {
        val shelf = com.bigpapituna.sharpears.ui.world.shelfLine(2, "1 gold, 1 silver", kid = true)
        assertFalse(shelf, shelf.any { it.isDigit() })
        assertTrue(com.bigpapituna.sharpears.ui.world.shelfLine(1, "1 gold", kid = false).startsWith("1 record on"))
        for (l in RunWords.KID_LINES[2]) assertFalse(l, Regex("every note|no mistakes|all the notes", RegexOption.IGNORE_CASE).containsMatchIn(l))
        for (m in 60..71) for (reader in listOf(true, false)) com.bigpapituna.sharpears.engine.play.JamWords.note(m, reader).let {
            assertTrue(it.text, it.text.endsWith("!") && it.text.first().isUpperCase())
        }
    }

    /**
     * What lands in the lesson bubble (a scene's caption or a step's instruction) stays within four lines at a 360 dp
     * phone's width (about 30 characters a line beside the 56 dp pup): 110 characters for kids. The kid home's big-word
     * bubble takes 40.
     */
    @Test fun bubbleLinesFitTheirBubble() {
        for (l in PLAYGROUND_LEVELS.flatMap { it.lessons }) for (s in l.steps) {
            if (s.kind == StepKind.READ) s.scenes.forEach { assertTrue("${l.id}: ${it.caption.length} chars: \"${it.caption}\"", it.caption.length <= 110) }
            else assertTrue("${l.id}: ${s.text.length} chars: \"${s.text}\"", s.text.length <= 110)
        }
        val home = listOf(KidLines.LETS_GO, KidLines.THIS_ONE_FIRST, KidLines.ASLEEP_SHORT, KidLines.ROAD) + (0..3).map(KidLines::signpostShort)
        home.forEach { assertTrue("kid home bubble: \"$it\"", it.length <= 40) }
        // Grown-ups' lessons: five lines at most.
        for (l in COURSE.flatMap { it.lessons }) for (s in l.steps) {
            if (s.kind == StepKind.READ) s.scenes.forEach { assertTrue("${l.id}: \"${it.caption}\"", it.caption.length <= 150) }
            else assertTrue("${l.id}: \"${s.text}\"", s.text.length <= 150)
        }
    }
}
