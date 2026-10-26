package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.ear.WorkoutItem
import com.bigpapituna.sharpears.engine.ear.WorkoutPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.2.1 QA: a count reads right at one, and two screens say the same number. Seen: "Last 12 weeks: 1 days",
 * "Heard C4 1 times", "every 1 bars", "+1 cents", "1 chords to practice", "Nearly clean: 1 notes needed a second
 * try"; a tenth called "an octave up"; the ear workout's card said about 4 minutes and its first screen "about five".
 */
class CopyCountsTest {
    @Test fun oneIsSingular() {
        assertEquals("1 day", plural(1, "day"))
        assertEquals("-1 cent", plural(-1, "cent"))
        assertEquals("0 cents", plural(0, "cent"))
        assertEquals("3 notes", plural(3, "note"))
        assertEquals("Nearly clean: 1 note needed a second try. On to the next step.", feedbackLine(Report(emptyList(), 1, 1, null), perform = false).text)
        val late = feedbackLine(Report(emptyList(), 0, 0, Recap(3, 0, 1, 0, 3)), perform = true).text
        assertTrue(late, "1 note came late or not at all." in late)
    }

    @Test fun aWrongNotePastAnOctaveIsNotCalledAnOctave() {
        val g = IntervalGame(0, seed = 3)
        assertEquals("That was more than an octave up.", g.input(g.first + 16).line!!.text)
        assertEquals("That was an octave up.", IntervalGame(0, seed = 3).let { it.input(it.first + 12).line!!.text })
    }

    @Test fun theWorkoutsCardAndFirstScreenSayTheSameMinutes() {
        val plan = WorkoutPlan(0, listOf(WorkoutItem(com.bigpapituna.sharpears.engine.ear.EarKind.PITCH, 0, 8)))
        assertEquals((plan.estimatedSeconds + 30) / 60, plan.minutes)
        val ui = File("src/main/java/com/bigpapituna/sharpears/ui/ear")
        for (f in listOf("EarTrainingScreen.kt", "WorkoutRun.kt")) assertTrue(f, "plan.minutes" in File(ui, f).readText())
        assertTrue("five minutes" !in File(ui, "WorkoutRun.kt").readText())
    }

    /**
     * A template that puts a count before a plural noun says why it is never one; anything else goes through plural()
     * or times(). The list below are counts that start at two or more (streaks shown from two days, goal minutes from five).
     */
    @Test fun countsBeforePluralNounsAreGuarded() {
        val known = listOf("This week:", "minutes a day", "chords due", "days in a row", "Three games, about", "Three quick games",
            "\"\$m minutes\"", "now \$goal minutes", "of \$goal days", "else -> \"\$n times\"", "every \${if (trainerBars == 1)",
            // 2.14: the parent gate's times tables start at four.
            "\"\$a times \$b\"")
        val count = Regex("""\$(\{[^}]+\}|[a-zA-Z_.]+) (days|bars|cents|times|chords|minutes|notes)\b""")
        val hits = File("src/main/java/com/bigpapituna/sharpears").walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            f.readLines().filter { count.containsMatchIn(it) && "Log." !in it && "log(" !in it && known.none { k -> k in it } }.map { "${f.name}: ${it.trim()}" }
        }.toList()
        // Note counts: Pup says (from two), melodies and rhythms (three or more), progress "3 of 8 notes".
        val notes = hits.filter { Regex("""\$(\{[^}]+\}|[a-zA-Z_.]+) notes\b""").containsMatchIn(it) }
        assertTrue(notes.size < 25)
        assertEquals("unguarded counts:\n" + (hits - notes.toSet()).joinToString("\n"), emptyList<String>(), hits - notes.toSet())
    }
}
