package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract's own rules: it travels as JSON both ways, a newer engine's kinds are dropped, and the redacted view. */
class FindingsTest {
    private val full = Findings(
        ProfileKind.KID, Period.WEEK, TimeRange(1_000, 2_000),
        facts = listOf(Fact(FactKind.PRACTICE_MINUTES, 42, 50), Fact(FactKind.NEW_LESSONS, 2, lessons = listOf("pond-1", "pond-2"))),
        trends = listOf(Trend(TrendKind.MINUTES, 42.0, 30.5, Direction.UP, true, 3), Trend(TrendKind.EAR, 70.0, 68.0, Direction.STEADY, null, 2)),
        flags = listOf(Flag(FlagKind.STUCK_LESSON, 3, 1, lessons = listOf("pond-3")), Flag(FlagKind.PAGE_STRUGGLE, 3, page = "pond")),
        candidates = listOf(Candidate(RecommendationKind.UNSTICK, ActionId.SET_NEXT, mapOf("lesson" to "pond-3"), FlagKind.STUCK_LESSON),
            Candidate(RecommendationKind.SETTINGS, ActionId.SETTING, mapOf("setting" to "reader", "value" to "true"))),
        praise = listOf(Praise(PraiseKind.FINALE, page = "garden"), Praise(PraiseKind.CLEAN_SONG, 1, songs = listOf("twinkle"))),
        stats = Stats(
            days = listOf(DayStat("2026-10-21", 12, 10, 1, true)),
            lessons = listOf(LessonStat("pond-3", null, 3, 1, 0, 4)),
            songs = listOf(SongStat("twinkle", 2, 3, 91)),
            skills = listOf(SkillSeries(SkillKind.STARS, listOf(null, 2.5, 3.0, null))),
            toys = listOf(ToyStat(ToyKind.JAM, 2, 9)),
            timeOfDay = listOf(PartStat(DayPart.EVENING, 12, 1)),
            noisyRoom = 2, hints = HintStats(1, 2, 3, 1), totals = Totals(minutes = 12, sessions = 1, earRounds = 6, earCorrect = 4),
            keySlips = listOf(KeySlips(62, 6, 1), KeySlips(67, 0, 2)), topSlip = SlipPair(62, 64, 5), notesDue = 412, skippedNotes = 4,
            hesitation = HesitationStat(1_100, 380, 67, 2_400), tempoSpreadMs = 38, tempoRuns = 3,
            left = HandStat(40, 88, 1_500), right = HandStat(372, 97, null), hintsBefore = HintStats(0, 9, 4, 0),
            clap = ClapStat(14, 16, 1), ear = listOf(EarStat("pitch", 6, 5)), weeks = listOf(19, 22, 38, 78),
            sessionMinutes = 13, sessionMinutesBefore = 9, mix = PlayMix(55, 16, 7),
        ),
        reader = false, goalMinutes = 5,
        actions = listOf(ActionTaken(1_500, ActionId.TEACHER_ASSIGN, mapOf("song" to "ode-to-joy", "note" to "Mia, try the left hand slowly!"))),
        settings = listOf(SettingChange(1_600, "bedtime", "true"), SettingChange(1_700, "reader", "a note with spaces")),
    )

    @Test fun findingsTravelAsJsonAndComeBackEqual() {
        assertEquals(full, Findings.decode(full.encode()))
        val empty = Findings(ProfileKind.ADULT, Period.SESSION, TimeRange(5, 5))
        assertEquals(empty, Findings.decode(empty.encode()))
        assertTrue(empty.isEmpty)
        assertFalse(full.isEmpty)
    }

    @Test fun kindsANewerEngineAddsAreDroppedNotFatal() {
        val text = full.encode()
            .replace("\"kind\":\"STUCK_LESSON\"", "\"kind\":\"STUCK_ON_THE_MOON\"")
            .replace("\"action\":\"SET_NEXT\"", "\"action\":\"TELEPORT\"")
        val back = Findings.decode(text)!!
        assertEquals(listOf(FlagKind.PAGE_STRUGGLE), back.flags.map { it.kind })
        assertEquals(listOf(ActionId.SETTING), back.candidates.map { it.action })
        assertNull(Findings.decode("not json"))
        assertNull(Findings.decode("{\"facts\":[]}"))             // no period: not findings
    }

    @Test fun theRedactedViewKeepsIdsNumbersAndEnumsOnly() {
        val r = full.redacted()
        assertEquals(mapOf("song" to "ode-to-joy"), r.actions.single().params)
        assertEquals(listOf(SettingChange(1_600, "bedtime", "true")), r.settings)
        assertEquals(full.facts, r.facts); assertEquals(full.flags, r.flags); assertEquals(full.candidates, r.candidates)
        assertEquals(full.stats, r.stats); assertEquals(full.trends, r.trends)
        val text = r.encode()
        assertFalse(text.contains("Mia") || text.contains("slowly") || text.contains("spaces"))
        // Ids that are not id-shaped are dropped too.
        val odd = full.copy(flags = listOf(Flag(FlagKind.STUCK_SONG, 3, songs = listOf("my song.mid with spaces", "twinkle"))),
            candidates = listOf(Candidate(RecommendationKind.TOGETHER, ActionId.PRACTISE_TOGETHER, mapOf("song" to "twinkle", "why" to "because")))).redacted()
        assertEquals(listOf("twinkle"), odd.flags.single().songs)
        assertEquals(mapOf("song" to "twinkle"), odd.candidates.single().params)
    }

    @Test fun theIdsTheFindingsMention() {
        assertEquals(setOf("pond-1", "pond-2", "pond-3"), full.lessonIds())
        assertEquals(setOf("twinkle", "ode-to-joy"), full.songIds())
    }

    @Test fun theActionMenuAndSettingsAreTheFixedOnes() {
        assertEquals(listOf("SET_NEXT", "REPLAY_FOR_STARS", "PRACTISE_TOGETHER", "FRESH_START", "PACE_FOCUS", "PACE_UNLOCK", "SETTING", "TEACHER_ASSIGN"),
            ActionId.entries.map { it.name })
        assertEquals(listOf("reader", "bedtime", "colourNotes", "micEasier", "quietRoom"), SettingId.entries.map { it.wire })
        assertEquals(SettingId.MIC_EASIER, SettingId.of("micEasier"))
    }
}
