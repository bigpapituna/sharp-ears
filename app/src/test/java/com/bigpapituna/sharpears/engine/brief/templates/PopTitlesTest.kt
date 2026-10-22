package com.bigpapituna.sharpears.engine.brief.templates

import com.bigpapituna.sharpears.engine.brief.Brief
import com.bigpapituna.sharpears.engine.brief.Insights
import com.bigpapituna.sharpears.engine.brief.NOW
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.brief.ZONE
import com.bigpapituna.sharpears.engine.brief.ai.Titles
import com.bigpapituna.sharpears.engine.brief.history
import com.bigpapituna.sharpears.engine.chords.POP_SONGS
import com.bigpapituna.sharpears.engine.chords.PopPlay
import com.bigpapituna.sharpears.engine.family.Placement
import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 2.6.0: a pop song's runs are logged under "pop-<id>" (songs by chords) and "Let's hear you play" keeps its first-lesson
 * reward under "placement-check". Neither is a library song or a course lesson, so the brief's title lookups answer them
 * here: the brief names the song, and never shows an id.
 */
class PopTitlesTest {
    private val pop = POP_SONGS.first()
    private val runId = PopPlay.runId(pop)
    private val noLibrary = CourseTitles.lookup { null }

    @Test fun aPopSongsRunHasItsTitleInEveryLookup() {
        assertEquals(pop.title, noLibrary(runId))
        assertEquals(pop.title, Titles.course().song(runId))
        assertEquals("", noLibrary("pop-no-such-song"))
        // A library song with the same id would keep its own title.
        assertEquals("Mine", CourseTitles.lookup { if (it == runId) "Mine" else null }(runId))
    }

    @Test fun thePlacementCheckHasATitleAsALesson() {
        assertEquals(Placement.CHECK_TITLE, noLibrary(Placement.CHECK_ID))
        assertEquals(Placement.CHECK_TITLE, Titles.course().lesson(Placement.CHECK_ID))
    }

    @Test fun aWeekOfAPopSongNamesItAndNeverItsId() {
        val d = { day: Int -> LocalDate.of(2026, 10, day) }
        val week = history {
            session(d(16), 20, 15) { song(runId, 2, accuracy = 60, timing = 50) }
            session(d(18), 20, 15) { song(runId, 2, accuracy = 70, timing = 40) }
            session(d(20), 20, 15) { song(runId, 3, accuracy = 86, timing = 30) }
        }
        val findings = Insights.compute(week, Profile.owner(), Period.WEEK, NOW, zone = ZONE)
        assertTrue(runId in findings.songIds())
        val text = render(TemplateWriter(noLibrary, seed = 7, clock = { NOW }).write(findings, "Roy"))
        assertTrue(text, pop.title in text)
        assertTrue(text, runId !in text)
    }

    private fun render(b: Brief): String = (listOf(b.headline, b.praise) + b.facts + b.notes + b.recommendations.map { it.text } +
        b.sections.flatMap { listOf(it.title, it.body) }).joinToString("\n")
}
