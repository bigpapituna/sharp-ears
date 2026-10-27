package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.15 (show off): where the tour, "For grown-ups" and the tips are wired, read from the sources. The tips are a grown-up's:
 * the recap's tip is chosen in the player's finish for a profile that is not a kid at all, drawn only in the grown-up branch,
 * and the Practice tip is gated by the same rule; none of the new cards names the pup.
 */
class ShowOffWiringTest {
    private fun src(path: String) = File("src/main/java/com/bigpapituna/sharpears/$path").readText()

    @Test fun theRecapTipIsChosenOncePerRunForAGrownUpAndDrawnOnlyInTheirBranch() {
        val player = src("ui/PlayerScreen.kt")
        val withTip = player.substringAfter("fun RunEnd.withTip()").substringBefore("copy(tip = tip)")
        assertTrue("if (profile.isKid) return this" in withTip)
        assertTrue("prefs.tipShown(tip)" in withTip && "prefs.hardRecaps += 1" in withTip)
        // The library song's two ends and the pop run's end carry it; Loop bar marks Loop used.
        assertTrue(Regex("""\.withTip\(\)""").findAll(player).count() >= 3)
        assertTrue("prefs.tipUsed(com.bigpapituna.sharpears.engine.nav.Tips.Tip.LOOP)" in player.substringAfter("fun loopBar(").substringBefore("setMode"))
        val recap = src("ui/RunRecap.kt")
        assertTrue("e.tip != null && !kid" in recap)
        assertEquals(2, Regex("""TipCard\(e\.tip""").findAll(recap).count())
        // The kid branch (numbers for a kid) draws no tip.
        assertFalse("TipCard" in recap.substringAfter("val numbers:").substringBefore("} else { {"))
    }

    @Test fun thePracticeTipUsesTheSameRulesAndOpensSightReading() {
        val a = src("MainActivity.kt").substringAfter("HomeTab.PRACTICE -> PracticeScreen(").substringBefore("HomeTab.YOU ->")
        assertTrue("Tips.inPractice(current.isKid, current.level, staff, prefs.sightTried(), prefs.tipsSeen" in a)
        assertTrue("prefs.tipShown(it)" in a && "Screen.SightRead(s, " in a)
    }

    @Test fun theTourAndTheParentsTourShareOneDialogWithPicturesAndNoPup() {
        val tour = src("ui/WelcomeTour.kt").substringAfter("fun TourDialog(")
        assertTrue("TourPicture(c.art)" in tour && "ParentsTourDialog" in tour)
        assertFalse("Pup(" in tour)
        assertFalse("Pup" in src("ui/tour/TourArt.kt") || "Pup" in src("ui/TipCard.kt").substringAfter("fun TipCard"))
        // 2.15.0 merge: the Plain look's tour skips the streak card; each card shown sends tour_card_seen by its picture.
        assertTrue("val plain = LocalPlain.current.on" in tour && "Welcome.tour(plain)" in tour)
        assertTrue("Events.tourCardSeen(cards[i].art.name)" in tour)
    }

    @Test fun forGrownUpsShowsOnAGrownUpsHomeAndIsMarkedDoneOnSkipOrEnd() {
        val a = src("MainActivity.kt").substringAfter("Welcome.next(current.isKid").substringBefore("Welcome.Show.NOTHING -> {}")
        assertTrue("parentsTourDue = parentsTourOpen && " in a)
        assertTrue("Show.PARENTS -> com.bigpapituna.sharpears.ui.ParentsTourDialog" in a)
        assertTrue("profiles.parentsTour = com.bigpapituna.sharpears.engine.nav.Welcome.PARENTS_DONE" in a)
        assertTrue("BriefEmail.hasKey" in a)
    }
}
