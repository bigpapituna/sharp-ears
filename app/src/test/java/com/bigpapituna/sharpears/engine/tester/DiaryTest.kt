package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Hands
import com.bigpapituna.sharpears.engine.brief.RunMode
import com.bigpapituna.sharpears.engine.brief.ToyKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The tester diary: when it goes, and what it says (and never says). */
class DiaryTest {
    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 21)
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = ZonedDateTime.of(d, LocalTime.of(h, m), zone).toInstant().toEpochMilli()

    @Test fun itGoesOnceADayInTheEveningOrTheNextMorning() {
        assertEquals("nothing new: no need to look", DiaryDue.Wait(null), DiarySchedule.decide(at(21), zone, null, null))
        assertEquals("played this afternoon: wait for 20:30", DiaryDue.Wait(at(20, 30)), DiarySchedule.decide(at(15), zone, null, at(14)))
        assertEquals(DiaryDue.Send, DiarySchedule.decide(at(20, 30), zone, null, at(14)))
        assertEquals(DiaryDue.Send, DiarySchedule.decide(at(21, 29), zone, at(20, 31, day.minusDays(1)), at(14)))
        assertEquals("the quiet hours hold it to 07:00", DiaryDue.Wait(at(7, d = day.plusDays(1))), DiarySchedule.decide(at(22), zone, null, at(14)))
        assertEquals("left from yesterday: it goes in the morning", DiaryDue.Send, DiarySchedule.decide(at(7, 5, day.plusDays(1)), zone, at(20, 30, day.minusDays(1)), at(14)))
        assertEquals("sent today: tomorrow evening at the earliest", DiaryDue.Wait(at(20, 30, day.plusDays(1))),
            DiarySchedule.decide(at(20, 45), zone, at(20, 31), at(20, 40)))
    }

    /** The tester log's lines go through the brief log's own format (EventLog) and come back as they went. */
    @Test fun testerLinesSurviveTheLogFormat() {
        val lines = listOf(TesterLog.screen(1, "home:LEARN"), TesterLog.note(2, "lesson:find-c", "home:LEARN", "the key \"C\" did not light"),
            TesterLog.error(3, "song import"), TesterLog.result(4, "email", "NO_KEY", "email: DAILY not sent: NO_KEY"),
            TesterLog.line(5, "mic: permission refused"), TesterLog.kind(6, "kid", "p1"))
        for (e in lines) assertEquals(e, BriefEvent.decode(e.encode()))
    }

    @Test fun theDiarySaysWhatHappenedWithNoNameOrAddress() {
        val from = at(7); val until = at(20, 30)
        val log = listOf(
            TesterLog.kind(at(9), "kid", "p1"),
            TesterLog.screen(at(9), "home:SONGS"),
            TesterLog.screen(at(9, 2), "lesson:find-c"),
            TesterLog.screen(at(9, 10), TesterLog.AWAY),
            TesterLog.screen(at(10), "lesson:find-c"),
            TesterLog.screen(at(10, 5), "home:SONGS"),
            TesterLog.screen(at(10, 6), TesterLog.AWAY),
            TesterLog.error(at(10, 3), "song import"),
            TesterLog.line(at(10, 4), "mic: permission refused"),
            TesterLog.result(at(11), "email", "NO_KEY", "email: DAILY 2026-10-21 for p1 not sent: NO_KEY"),
            TesterLog.note(at(10, 4), "lesson:find-c", "home:SONGS", "the C did not light"),
        )
        val people = listOf(DiaryPerson("kid p1", listOf(
            BriefEvent.LessonStep(at(9, 5), "find-c", 1, "PLAY", tries = 4, letGo = true, hintsUsed = listOf(1, 2), durationMs = 90_000),
            BriefEvent.LessonDone(at(10, 5), "find-c", stars = 2, durationMs = 400_000, first = true),
            BriefEvent.SongRun(at(10, 5), "user-mias-song", RunMode.WAIT, Hands.RIGHT, stars = 1),
            BriefEvent.ToyOpened(at(12), ToyKind.PUP_SAYS, 125_000),
            BriefEvent.LessonStep(at(6), "too-early", 0, "PLAY"),
        )), DiaryPerson("adult owner", emptyList()))
        val text = Diary.text("0f1e2d3c-aaaa-bbbb-cccc-000000000000", "2.5.0 (debug)", from, until, zone, log, people)
        assertTrue(text, text.contains("Device 0f1e2d3c-aaaa-bbbb-cccc-000000000000"))
        assertTrue(text, text.contains("app 2.5.0 (debug)"))
        assertTrue("time on a screen sums its visits; away ends it", text.contains("  lesson:find-c: 13 min, 2"))
        assertTrue(text, text.contains("  home:SONGS: 3 min, 2"))
        assertTrue(text, text.contains("kid p1: find-c step 1 PLAY: 4 tries, let go, hints 1 2, 1 min 30 s"))
        assertTrue(text, text.contains("kid p1: find-c finished, 2 stars, 6 min 40 s, first time"))
        assertTrue(text, text.contains("song user-song wait, 1 stars"))
        assertTrue(text, text.contains("toy pupSays, 2 min 5 s"))
        assertTrue(text, text.contains("error shown: song import"))
        assertTrue(text, text.contains("mic: permission refused"))
        assertTrue(text, text.contains("email NO_KEY: email: DAILY 2026-10-21 for p1 not sent: NO_KEY"))
        assertTrue(text, text.contains("on lesson:find-c (after home:SONGS): the C did not light"))
        assertTrue(text, text.contains("Who played: Wed 21 Oct 09:00 kid p1"))
        assertFalse("before the period", text.contains("too-early"))
        assertFalse(text.contains("mias"))
        assertFalse(text.contains("@"))
    }

    @Test fun anEmptyDayStillReadsAsADiary() {
        val text = Diary.text("id", "2.5.0", at(7), at(20), zone, emptyList(), emptyList())
        assertEquals(6, Regex("\n  none\n").findAll(text).count())
        assertEquals("Sharp Ears test diary Wed 21 Oct, device 0f1e2d3c", Diary.subject("0f1e2d3c-aaaa", at(20, 30), zone))
    }

    @Test fun durationsReadPlainly() {
        assertEquals("45 s", Diary.duration(45_000))
        assertEquals("3 min", Diary.duration(180_000))
        assertEquals("3 min 20 s", Diary.duration(200_000))
        assertEquals("1 h 05 min", Diary.duration(3_900_000))
    }
}
