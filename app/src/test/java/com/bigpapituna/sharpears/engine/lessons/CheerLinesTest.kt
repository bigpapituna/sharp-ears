package com.bigpapituna.sharpears.engine.lessons

import com.bigpapituna.sharpears.engine.CHEER_TITLES
import com.bigpapituna.sharpears.engine.SpokenCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.13, the cheer pops' words (engine/lessons/CheerLines.kt): enough of them, the copy rules, the two voices (a kid's short
 * and playful, a grown-up's warm and plain), nothing about wrong notes, the runs' numbers, and every line that can be said
 * in the voice catalog.
 */
class CheerLinesTest {
    private val families = CheerTrigger.entries

    @Test fun atLeastTwelveLinesPerFamilyPerAudienceAndTheRunsNumbered() {
        for (kid in listOf(true, false)) {
            for (t in families) {
                val lines = CheerLines.family(t, kid)
                assertTrue("$t kid=$kid: ${lines.size}", lines.size >= CheerLines.MIN_PER_FAMILY)
                assertTrue("$t kid=$kid repeats", lines.size == lines.toSet().size)
            }
            for (n in CheerLines.RUN_MILESTONES) {
                val runs = CheerLines.runNumbered(n, kid)
                assertTrue(runs.size >= 6)
                val said = if (kid) CheerLines.word(n) else "$n"
                runs.forEach { assertTrue(it, it.contains(said, ignoreCase = true)) }
            }
            for (m in CheerLines.FOCUS_MINUTES) CheerLines.focusMinutes(m, kid).forEach { assertTrue(it, it.startsWith(CheerLines.word(m).replaceFirstChar { c -> c.uppercase() })) }
        }
        assertTrue(CheerLines.focusMinutes(25, kid = false).isEmpty())
    }

    @Test fun theCopyRulesHold() {
        val symbols = Regex("[—–·#&/%@*•→+=<>|~^_]| - ")
        val scolding = Regex("\\b(wrong|mistakes?|errors?|fail\\w*|missed|bad|oops)\\b", RegexOption.IGNORE_CASE)
        for (kid in listOf(true, false)) for (l in CheerLines.all(kid)) {
            assertFalse("no dashes or symbols: \"$l\"", symbols.containsMatchIn(l))
            assertFalse("never about wrong notes: \"$l\"", scolding.containsMatchIn(l))
            assertFalse("single spaces: \"$l\"", "  " in l || l != l.trim())
            assertTrue("starts a sentence: \"$l\"", l.first().isUpperCase() || (!kid && l.first().isDigit()))
            if (kid) assertTrue("a kid's line ends with a stop or an exclamation: \"$l\"", l.last() in ".!")
            else assertTrue("a grown-up's line ends with a stop: \"$l\"", l.last() == '.')
        }
    }

    /** A kid's lines: the pup's voice, two to six words (Roy's nudge, "Find the next key, I'm right here.", seven), no digits. */
    @Test fun aKidsLinesAreShortPlayfulAndCarryNoDigits() {
        val nudge = CheerLines.family(CheerTrigger.NUDGE, kid = true).toSet()
        for (l in CheerLines.all(kid = true)) {
            val words = l.split(' ').size
            assertTrue("$words words: \"$l\"", words in 2..(if (l in nudge) 7 else 6))
            assertFalse("no digits: \"$l\"", l.any { it.isDigit() })
        }
        assertTrue(CheerLines.all(kid = true).any { "Woof" in it } && CheerLines.all(kid = true).any { "Paws" in it })
        // Roy's examples are in.
        for (l in listOf("Woof! Great job!", "You're on fire!", "Keep going!", "Paws up! Nice!", "Don't stop, you're doing so well!"))
            assertTrue(l, l in CheerLines.all(kid = true))
    }

    /** A grown-up's lines: warm, plain, never childish or gushy (no exclamation marks, no woofs and paws). */
    @Test fun aGrownUpsLinesAreWarmAndPlain() {
        val childish = Regex("\\b(woof|yay|paws?|tail|superstar|hooray|wow|super)\\b", RegexOption.IGNORE_CASE)
        for (l in CheerLines.all(kid = false)) {
            assertFalse("\"$l\"", '!' in l)
            assertFalse("\"$l\"", childish.containsMatchIn(l))
            assertTrue("short: \"$l\"", l.length <= 40)
        }
        for (l in listOf("Nice and steady.", "That's the rhythm.", "Good hands.", "Keep going, you've got this.", "Don't stop. You're doing really well.",
                "Five minutes of focus. Lovely.", "Take your time.", "Halfway there.", "Nice save.", "Back on track.", "10 in a row."))
            assertTrue(l, l in CheerLines.all(kid = false))
    }

    @Test fun aSaveCelebratesTheSaveAndTheStepPassedLineIsNotTheCheerBarsTitle() {
        for (kid in listOf(true, false)) {
            CheerLines.family(CheerTrigger.RECOVERY, kid).forEach { assertFalse(it, Regex("miss|slip|wrong", RegexOption.IGNORE_CASE).containsMatchIn(it)) }
            // The cheer bar shows one of CHEER_TITLES at the same moment as the step pop's bubble.
            CheerLines.family(CheerTrigger.STEP_PASSED, kid).forEach { assertFalse(it, it in CHEER_TITLES) }
        }
    }

    /** Every line that can be said is recorded: a kid's at the kid speed, a grown-up's at the usual one; the step's are shown only. */
    @Test fun everySpokenLineIsInTheVoiceCatalog() {
        val all = SpokenCatalog.all().toSet()
        val kid = SpokenCatalog.kidLines().toSet()
        for (l in CheerLines.spoken(kid = true)) assertTrue("kid: $l", SpokenCatalog.normalize(l) in kid)
        for (l in CheerLines.spoken(kid = false)) assertTrue("grown-up: $l", SpokenCatalog.normalize(l) in all)
        for (k in listOf(true, false)) for (l in CheerLines.family(CheerTrigger.STEP_PASSED, k)) assertFalse(l, l in CheerLines.spoken(k))
        // A pop's line fits its bubble's two lines in a lesson's header.
        for (k in listOf(true, false)) for (l in CheerLines.all(k)) assertTrue(l, l.length <= 40)
    }
}
