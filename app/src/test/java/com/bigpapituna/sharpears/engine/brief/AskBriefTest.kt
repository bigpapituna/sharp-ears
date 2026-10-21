package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AskBriefTest {
    @Test fun aQuestionIsCleanedAndABlankOneIsRefused() {
        assertEquals("Why is it hard?", AskRules.clean("  Why   is it\n hard?  "))
        assertNull(AskRules.clean("   \n "))
        assertEquals(AskRules.MAX_QUESTION, AskRules.clean("a".repeat(900))!!.length)
        assertNull(AskHistory().ask("  "))
    }

    @Test fun theHistoryKeepsPairsInOrderAndAnswersTheRightOne() {
        val (h1, a) = AskHistory().ask("First?")!!
        assertTrue(h1.busy)
        assertFalse(h1.answered(a, "One.").busy)
        val (h2, b) = h1.answered(a, "One.").ask("Second?")!!
        val h = h2.failed(b)
        assertEquals(listOf("First?", "Second?"), h.exchanges.map { it.question })
        assertEquals("One.", h.exchanges[0].answer)
        assertTrue(h.exchanges[1].failed && h.exchanges[1].answer == null)
        assertFalse(h.busy)
    }

    @Test fun anAnswerOutOfOrderLandsOnItsOwnQuestion() {
        val (h1, a) = AskHistory().ask("One")!!
        val (h2, b) = h1.ask("Two")!!
        val h = h2.answered(b, "Two answer").answered(a, "One answer")
        assertEquals(listOf("One answer", "Two answer"), h.exchanges.map { it.answer })
    }

    @Test fun anEmptyAnswerCountsAsFailed() {
        val (h, id) = AskHistory().ask("Q")!!
        assertTrue(h.answered(id, "  ").exchanges.single().failed)
    }

    @Test fun onlyTheLastEightAreKept() {
        var h = AskHistory()
        repeat(12) { i -> h = h.ask("Q$i")!!.first }
        assertEquals(AskHistory.MAX, h.exchanges.size)
        assertEquals("Q4", h.exchanges.first().question)
        assertEquals("Q11", h.exchanges.last().question)
        assertEquals("ids never repeat", h.exchanges.size, h.exchanges.map { it.id }.distinct().size)
    }

    @Test fun theChildsNameNeverGoesWithTheQuestion() {
        assertEquals("Why is my child stuck on the pond?", AskRules.forWriter("Why is Mia stuck on the pond?", "Mia", ProfileKind.KID))
        assertEquals("Is my child's playing normal? my child!", AskRules.forWriter("Is mia's playing normal? MIA!", "Mia", ProfileKind.KID))
        assertEquals("Why am I late?", AskRules.forWriter("Why am I late?", "Mia", ProfileKind.KID))
        assertEquals("Is Miami fun?", AskRules.forWriter("Is Miami fun?", "Mia", ProfileKind.KID))
        assertEquals("Why is the learner slow?", AskRules.forWriter("Why is Roy slow?", "Roy", ProfileKind.ADULT))
        assertEquals("unchanged", AskRules.forWriter("unchanged", "", ProfileKind.KID))
    }

    @Test fun startersFollowTheFlagsAndNeverNameAnyone() {
        val none = AskRules.starters(BriefSamples.empty(Period.WEEK, 0L, java.time.ZoneOffset.UTC))
        assertEquals(listOf("What went well?", "What should we do next?", "How much should they practise?"), none)
        val week = AskRules.starters(BriefSamples.kid(Period.WEEK, 1_800_000_000_000L, java.time.ZoneOffset.UTC))
        assertEquals("Why is one thing taking so long?", week.first())
        assertEquals(3, week.size)
        assertTrue((none + week).none { "Mia" in it })
    }

    @Test fun aGrownUpsOwnWeekAsksInTheFirstPerson() {
        val mine = AskRules.starters(BriefSamples.adult(Period.WEEK, 1_800_000_000_000L, java.time.ZoneOffset.UTC))
        assertTrue(mine.toString(), mine.none { " they " in " $it " || " we " in " $it " })
        assertTrue(mine.toString(), "What should I do next?" in mine || "How much should I practise?" in mine)
    }

    @Test fun dictationTextJoinsWithOneSpaceAndCapitalisesASentenceStart() {
        assertEquals("Why is it hard", DictationText.append("", "why is it hard"))
        assertEquals("Why is it hard", DictationText.append("   ", " why is it hard "))
        assertEquals("Hello there", DictationText.append("Hello", "there"))
        assertEquals("Fine. Next one", DictationText.append("Fine.", "next one"))
        assertEquals("kept", DictationText.append("kept", "   "))
    }

    @Test fun everyRecogniserErrorHasKindWordsOrNone() {
        assertNull("stopped by us", DictationText.errorMessage(5))
        for (code in 1..15) if (code != 5) {
            val m = DictationText.errorMessage(code)!!
            assertTrue("$code: $m", m.first().isUpperCase() && m.last() == '.' && !m.contains('—'))
        }
        assertTrue(DictationText.errorMessage(9)!!.contains("Allow the microphone"))
    }
}
