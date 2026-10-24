package com.bigpapituna.sharpears.engine.brief.ai

import com.bigpapituna.sharpears.engine.brief.Fact
import com.bigpapituna.sharpears.engine.brief.FactKind
import com.bigpapituna.sharpears.engine.brief.Findings
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.TimeRange
import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The checks on the way back, one rule at a time: what is kept, what is dropped, and the tidying. */
class AiRepliesTest {
    private val titles = Titles.course { mapOf("gymnopedie-1" to "Gymnopedie No. 1 (opening)", "user-song-live" to "Song [live]", "ode-to-joy" to "Ode to Joy") }

    private fun findings(kind: ProfileKind = ProfileKind.ADULT, lessons: List<String> = emptyList(), songs: List<String> = emptyList()) =
        Findings(kind, Period.WEEK, TimeRange(0, 1), facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 30, 40),
            Fact(FactKind.NEW_LESSONS, lessons.size.toLong(), lessons = lessons), Fact(FactKind.SONG_RUNS, songs.size.toLong(), songs = songs)))

    private fun brief(f: Findings, headline: String, fact: String = "30 minutes of practice."): Checked<com.bigpapituna.sharpears.engine.brief.Brief> =
        AiReplies(f, TitleSet(f, titles, "Mia", emptyList()), "Mia", 5).brief(
            com.bigpapituna.sharpears.engine.MiniJson.write(mapOf("headline" to headline, "facts" to listOf(fact), "praise" to "Well done.")))

    @Test fun aTitleMayHoldADigitOrABracketEvenInTheHeadline() {
        val f = findings(songs = listOf("gymnopedie-1", "user-song-live"))
        assertNotNull(brief(f, "Gymnopedie No. 1 (opening) is coming together.").value)
        assertNotNull(brief(f, "Song [live] is coming together.").value)
        assertNotNull("words for numbers are fine...", brief(f, "Two songs are coming together.").value)
        assertNull("...digits are not", brief(f, "2 songs are coming together.").value)
        assertNull("a bracket outside a title is a placeholder", brief(f, "[child] played well.").value)
    }

    @Test fun otherTitlesAreCaughtAsTheAppWritesThemNotEverydayWords() {
        val f = findings(lessons = listOf("pond-7"))                       // Left paw walks
        assertNotNull("an allowed title holding another title", brief(f, "Left paw walks went well.").value)
        assertNull("Left paw is another lesson", brief(f, "A good week.", "Left paw went well.").value)
        assertNotNull("everyday words in passing", brief(f, "A good week.", "Playing hands together is new.").value)
        assertNull("the lesson by its title", brief(f, "A good week.", "Hands together is next.").value)
        assertNotNull("one-word titles are everyday words", brief(f, "A good week.", "Skips and rests are next.").value)
        assertNotNull("a page title in passing", brief(f, "A good week.", "The left hand walked on the pond.").value)
    }

    @Test fun teacherModeIsTheAppsButATeacherIsNot() {
        val f = findings()
        assertNotNull(brief(f, "A good week.", "Teacher mode put a song on the shelf.").value)
        assertNull(brief(f, "A good week.", "A teacher would help.").value)
        assertNull(brief(f, "A good week.", "Try the book of scales.").value)
        assertNull(brief(f, "A good week.", "Better than other learners.").value)
    }

    @Test fun cleaningTidiesWithoutChangingTheWords() {
        assertEquals("Mia played, then rested.", AiReplies.clean("- **Mia** played " + EM + " then rested."))
        assertEquals("From 5 to 7 days.", AiReplies.clean("From 5" + EN + "7 days."))
        assertEquals("{name} played.", AiReplies.clean("\"{ NAME } played.\""))
        assertEquals("One line, then another.", AiReplies.clean("One line,\nthen another."))
        assertEquals("Ta and ti-ti stays as it is.", AiReplies.clean("Ta and ti-ti stays as it is."))
    }

    @Test fun anAnswerWrappedInJsonOrBulletsIsUnwrapped() {
        val f = findings(ProfileKind.KID)
        val r = AiReplies(f, TitleSet(f, titles, "Mia", emptyList()), "Mia", 5)
        assertEquals("Mia is doing well.", r.answer("{\"answer\": \"{name} is doing well.\"}").value)
        assertEquals("Mia played. Then rested.", r.answer("- {name} played.\n- Then rested.").value)
        assertEquals("Mia played.", r.answer("Answer: {name} played.").value)
        assertTrue(r.answer("Most kids her age do better.").dropped.single().contains("compares"))
    }

    // ---- 2.8.4, QA D4: a fact's numbers are the findings' ----

    private fun facts(f: Findings, vararg facts: String, phone: List<String> = emptyList()) =
        AiReplies(f, TitleSet(f, titles, "Mia", emptyList()), "Mia", 5, standIns = { phone }).brief(
            com.bigpapituna.sharpears.engine.MiniJson.write(mapOf("headline" to "A good week.", "facts" to facts.toList(), "praise" to "Well done.")))

    /** Roy's run in the QA: one song's right hand to the end, 42 notes, in a week of 55 minutes on screen. */
    private val run = Findings(ProfileKind.ADULT, Period.WEEK, TimeRange(0, 1), facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 12, 55),
        Fact(FactKind.SONG_RUNS, 1, songs = listOf("ode-to-joy"))), stats = com.bigpapituna.sharpears.engine.brief.Stats(
        songs = listOf(com.bigpapituna.sharpears.engine.brief.SongStat("ode-to-joy", 1, 3, 42))))

    @Test fun aFactWithANumberTheFindingsDoNotHoldGivesWayToThePhones() {
        val c = facts(run, "You played 12 minutes out of 55 on screen.", "Your right hand played all 57 notes of Ode to Joy without a wrong key.",
            phone = listOf("12 minutes of practice this week.", "Ode to Joy went from start to end."))
        val b = c.value!!
        assertEquals(listOf("You played 12 minutes out of 55 on screen.", "Ode to Joy went from start to end."), b.facts)
        assertEquals("fact 2: a number not in the findings (57), the phone's fact stands in", c.dropped.single())
        assertEquals(com.bigpapituna.sharpears.engine.brief.Brief.Writer.AI, b.writtenBy)
    }

    @Test fun dayCountsTitlesAndTheFindingsOwnNumbersPass() {
        val f = findings(songs = listOf("gymnopedie-1"))
        assertEquals(emptyList<String>(), facts(f, "30 minutes over 3 days, out of 40 on screen.", "Gymnopedie No. 1 (opening) twice in 7 days.").dropped)
        assertTrue(facts(f, "1,500 notes this week.").dropped.first().contains("(1500)"))
        assertEquals(listOf(57L, 8L), AiReplies(f, TitleSet(f, titles, "Mia", emptyList()), "Mia", 5).strangeNumbers("57 notes on 8 days, 30 minutes, 2 songs."))
    }

    @Test fun withNoPhoneFactToSpareTheFactGoesAndWithNoneLeftTheBriefDoes() {
        val c = facts(run, "12 minutes this week.", "All 57 notes, cleanly.")
        assertEquals(listOf("12 minutes this week."), c.value!!.facts)
        assertNull(facts(run, "All 57 notes, cleanly.").value)
        // A phone's fact is not used twice.
        val twice = facts(run, "12 minutes this week.", "All 57 notes.", "And 99 more.", phone = listOf("12 minutes this week.", "Ode to Joy went from start to end."))
        assertEquals(listOf("12 minutes this week.", "Ode to Joy went from start to end."), twice.value!!.facts)
    }
}
