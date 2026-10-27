package com.bigpapituna.sharpears.engine.family

import com.bigpapituna.sharpears.engine.CHEER_TITLES
import com.bigpapituna.sharpears.engine.HIGH_FIVE
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.pup.PupTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.15, the Plain look (engine/family/PlainLook.kt): the rule table (what each element does with the switch on and off, per
 * audience), little kids unaffected, the settings it overrides without rewriting them, the one-time offer and the words.
 */
class PlainLookTest {
    private val little = Audience.LITTLE_KID
    private val big = Audience.BIG_KID
    private val grown = Audience.GROWN_UP

    @Test fun offeredToGrownUpsAndBigKidsOnlyAndALittleKidIgnoresIt() {
        assertTrue(PlainLook.offered(grown)); assertTrue(PlainLook.offered(big)); assertFalse(PlainLook.offered(little))
        assertTrue(PlainLook.on(grown, true)); assertTrue(PlainLook.on(big, true))
        assertFalse(PlainLook.on(little, true))
        for (a in Audience.entries) assertFalse(PlainLook.on(a, false))
        // The profile's own band through Profile.audience: a big kid made little again keeps the stored switch, unused.
        val leo = Profile.kid("Leo", 2, reader = true, id = "p2", band = AgeBand.BIG)
        assertTrue(PlainLook.on(leo.audience, true))
        assertFalse(PlainLook.on(leo.withBand(AgeBand.LITTLE).audience, true))
    }

    /** The table, written out: every element, the three audiences, the switch off and on. */
    @Test fun theRuleTable() {
        val S = PlainShow.SHOWN; val P = PlainShow.PLAIN; val H = PlainShow.HIDDEN; val R = PlainShow.ON_REQUEST
        // part to (little kid, grown-up or big kid with the switch off, grown-up or big kid with it on)
        val table = mapOf(
            PlainPart.PUP to Triple(S, S, H),
            PlainPart.PUP_TAP to Triple(S, S, H),
            PlainPart.PUP_WORDS to Triple(S, S, P),
            PlainPart.CHEER_POPS to Triple(S, S, H),
            PlainPart.ENCOURAGEMENT_SETTING to Triple(S, S, P),
            PlainPart.STEP_POP to Triple(S, S, P),
            PlainPart.STEP_CHIME to Triple(S, S, R),
            PlainPart.FINISH_PARTY to Triple(S, S, H),
            PlainPart.STREAK to Triple(S, S, H),
            PlainPart.STREAK_ENDED to Triple(H, S, H),
            PlainPart.REST_MOON to Triple(S, S, H),
            PlainPart.DAYS_STRIP to Triple(S, S, P),
            PlainPart.REWARD_REVEALS to Triple(S, S, H),
            PlainPart.REWARDS_CHIP to Triple(H, S, H),
            PlainPart.XP_ON_FINISH to Triple(H, S, H),
            PlainPart.LEVEL_UP_SCREEN to Triple(H, S, H),
            PlainPart.LEVEL_ON_YOU to Triple(S, S, S),
            PlainPart.SURPRISE_GIFT to Triple(S, H, H),
            PlainPart.PUP_WORLD_CARD to Triple(S, S, H),
            PlainPart.QUEST to Triple(S, S, H),
            PlainPart.SPOKEN_LINES to Triple(S, S, R),
            PlainPart.PUP_COPY to Triple(S, S, P),
            PlainPart.COMEBACK_CARD to Triple(S, S, P),
            PlainPart.PLAIN_SETTING to Triple(H, S, S),
            PlainPart.PLAIN_OFFER to Triple(H, S, H),
        )
        assertEquals("every part has a row", PlainPart.entries.toSet(), table.keys)
        for ((part, row) in table) {
            val (l, off, on) = row
            // A little kid's column is the same whatever is stored.
            assertEquals("$part little, off", l, PlainLook.rule(part, little, false))
            assertEquals("$part little, stored on (ignored)", l, PlainLook.rule(part, little, true))
            for (a in listOf(grown, big)) {
                assertEquals("$part $a off", off, PlainLook.rule(part, a, false))
                assertEquals("$part $a on", on, PlainLook.rule(part, a, true))
            }
        }
        // XP and level stay (on You); everything the simulation called childish goes.
        assertEquals(PlainShow.SHOWN, PlainLook.rule(PlainPart.LEVEL_ON_YOU, grown, true))
        assertTrue(listOf(PlainPart.PUP, PlainPart.CHEER_POPS, PlainPart.STREAK, PlainPart.REWARD_REVEALS, PlainPart.SURPRISE_GIFT)
            .all { PlainLook.rule(it, big, true) == PlainShow.HIDDEN })
    }

    @Test fun encouragementIsOffInPlainWithoutRewritingTheChoice() {
        for (f in CheerFrequency.entries) {
            assertEquals(CheerFrequency.OFF, PlainLook.cheerFrequency(true, f))
            assertEquals(f, PlainLook.cheerFrequency(false, f))
        }
    }

    @Test fun spokenLinesAreOffByDefaultInPlainAndTheSwitchStillWorks() {
        assertFalse("off by default", PlainLook.voiceOn(plain = true, voice = true, plainVoice = null))
        assertTrue("turned on in Plain", PlainLook.voiceOn(plain = true, voice = false, plainVoice = true))
        assertFalse(PlainLook.voiceOn(plain = true, voice = true, plainVoice = false))
        // Plain off: the usual switch, as it was left.
        assertTrue(PlainLook.voiceOn(plain = false, voice = true, plainVoice = false))
        assertFalse(PlainLook.voiceOn(plain = false, voice = false, plainVoice = true))
    }

    @Test fun theStepChimeNeedsStepSoundInPlain() {
        assertFalse(PlainLook.stepChime(plain = true, sound = true, stepSound = false))
        assertTrue(PlainLook.stepChime(plain = true, sound = true, stepSound = true))
        assertFalse("App sound off is off", PlainLook.stepChime(plain = true, sound = false, stepSound = true))
        assertTrue(PlainLook.stepChime(plain = false, sound = true, stepSound = false))
        assertFalse(PlainLook.stepChime(plain = false, sound = false, stepSound = true))
        assertFalse(PlainLook.party(plain = true, party = true)); assertTrue(PlainLook.party(plain = false, party = true))
    }

    @Test fun theOfferOncePerProfileNeverToALittleKid() {
        val m = PlainLook.Moment.LESSON_FINISHED
        val o = PlainLook.Moment.APP_OPENED
        assertTrue(PlainLook.offerDue(grown, stored = false, offeredBefore = false, moment = m, daysPlayed = 0))
        assertTrue(PlainLook.offerDue(big, stored = false, offeredBefore = false, moment = m, daysPlayed = 1))
        assertFalse("never a little kid", PlainLook.offerDue(little, false, false, m, 30))
        assertFalse("never a little kid", PlainLook.offerDue(little, false, false, o, 30))
        assertFalse("once", PlainLook.offerDue(grown, false, offeredBefore = true, moment = m, daysPlayed = 30))
        assertFalse("already plain", PlainLook.offerDue(grown, stored = true, offeredBefore = false, moment = m, daysPlayed = 30))
        // Opening the app: after seven days played or more.
        assertFalse(PlainLook.offerDue(grown, false, false, o, PlainLook.OFFER_DAYS - 1))
        assertTrue(PlainLook.offerDue(grown, false, false, o, PlainLook.OFFER_DAYS))
        assertTrue(PlainLook.offerDue(big, false, false, o, 40))
    }

    @Test fun theWords() {
        assertEquals("Plain look", PlainLook.TITLE)
        assertEquals("No pup, no cheers, no streak counter. Just you and the piano.", PlainLook.LINE)
        assertEquals("Off in Plain look", PlainLook.ENCOURAGEMENT_OFF)
        assertEquals("Prefer a plainer look? Turn off the pup, cheers and streaks.", PlainLook.OFFER_LINE)
        assertEquals("Try Plain look" to "No thanks", PlainLook.OFFER_YES to PlainLook.OFFER_NO)
        assertEquals("Welcome back. Pick up where you left off: Your first melody.", PlainLook.comeback("Your first melody"))
        assertEquals("Pick up where you left off.", PlainLook.comebackLine(null))
        assertEquals("That's today's playing done, Leo. See you tomorrow.", PlainLook.goodbye("Leo", asleep = false, capped = false))
        assertEquals("That's all for today. See you tomorrow.", PlainLook.goodbye(null, asleep = false, capped = true))
        val all = listOf(PlainLook.TITLE, PlainLook.LINE, PlainLook.ENCOURAGEMENT_OFF, PlainLook.STEP_SOUND, PlainLook.STEP_SOUND_LINE,
            PlainLook.OFFER_LINE, PlainLook.OFFER_YES, PlainLook.OFFER_NO, PlainLook.COMEBACK_TITLE, PlainLook.comebackLine("Twinkle"),
            PlainLook.LESSON_DONE, PlainLook.LET_GO_TITLE, PlainLook.STUCK_TITLE, PlainLook.STUCK_SPOKEN, PlainLook.JAM, PlainLook.PUP_ONLY,
            PlainLook.goodbye("Leo", true, false), PlainLook.goodbye("Leo", false, true), PlainLook.goodbye(null, false, false)) +
            PlainLook.STEP_TITLES + PlainPart.entries.map { it.label }
        for (w in all) {
            assertFalse("no exclamation: $w", '!' in w)
            assertFalse("no dashes: $w", Regex("[–—]| - ").containsMatchIn(w))
            assertFalse("no woofs: $w", "woof" in w.lowercase())
            assertTrue("sentence case: $w", w.first().isUpperCase())
        }
    }

    @Test fun pupVoicedAndExclamationLinesFallBackToThePlainBank() {
        // The cheer bar's titles, the high five, the let-go and the finish have plain lines of their own.
        for (t in CHEER_TITLES) assertTrue(PlainLook.calm(t) in PlainLook.STEP_TITLES)
        assertEquals(PlainLook.STUCK_TITLE, PlainLook.calm("High five!"))
        assertEquals(PlainLook.STUCK_SPOKEN, PlainLook.calm(HIGH_FIVE))
        assertEquals(PlainLook.LET_GO_TITLE, PlainLook.calm("Good try!"))
        assertEquals("Lesson complete.", PlainLook.calm("Lesson complete!"))
        // The rest: the pup's sentences go, a trailing exclamation becomes a full stop.
        assertEquals("Amazing.", PlainLook.calm(PupTap.LINE))
        assertEquals("Nice one.", PlainLook.calm("Woof! Nice one!"))
        assertEquals("That's today's playing done, Leo.", PlainLook.calm("That's today's playing done, Leo. Goodnight from the pup!"))
        assertEquals("New best.", PlainLook.calm("New best!"))
        assertEquals(PlainLook.PUP_ONLY, PlainLook.calm("Woof!"))
        // Only the end: song titles keep their own marks inside a line.
        assertEquals("Next: Oh! Susanna", PlainLook.calm("Next: Oh! Susanna"))
        assertEquals("Last Christmas by Wham! at 80 percent.", PlainLook.calm("Last Christmas by Wham! at 80 percent."))
        assertEquals("Nice and steady.", PlainLook.calm("Nice and steady."))
        assertEquals("", PlainLook.calm(""))
    }

    @Test fun streakLinesInTheBriefsOwnWeek() {
        assertTrue(PlainLook.mentionsStreak("A streak: 4 days running with the goal met."))
        assertTrue(PlainLook.mentionsStreak("Hit the daily goal 5 days in a row."))
        assertTrue(PlainLook.mentionsStreak("Kept a goal streak going."))
        assertFalse(PlainLook.mentionsStreak("Played on six days this month."))
        assertFalse(PlainLook.mentionsStreak("4 min playing of 8 min on screen."))
        assertFalse(PlainLook.mentionsStreak("Eight notes in a row in the good window."))
        assertTrue(PlainLook.mentionsStreak("You have met the goal six days running."))
        // The own week's findings lose the goal streak's praise and nothing else.
        val range = com.bigpapituna.sharpears.engine.brief.TimeRange(0L, 1L)
        val f = com.bigpapituna.sharpears.engine.brief.Findings(ProfileKind.ADULT, com.bigpapituna.sharpears.engine.brief.Period.WEEK, range,
            praise = listOf(com.bigpapituna.sharpears.engine.brief.Praise(com.bigpapituna.sharpears.engine.brief.PraiseKind.GOAL_STREAK, 6),
                com.bigpapituna.sharpears.engine.brief.Praise(com.bigpapituna.sharpears.engine.brief.PraiseKind.CAME_BACK, 4)))
        assertEquals(listOf(com.bigpapituna.sharpears.engine.brief.PraiseKind.CAME_BACK), PlainLook.withoutStreaks(f).praise.map { it.kind })
        assertEquals(f.copy(praise = f.praise.drop(1)), PlainLook.withoutStreaks(f))
    }
}
