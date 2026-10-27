package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.nav.ContinueWith
import com.bigpapituna.sharpears.engine.nav.Layout
import com.bigpapituna.sharpears.engine.nav.Simple
import com.bigpapituna.sharpears.engine.nav.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 2.7: the tour, What's new, the microphone banner and Continue are kept per profile (Prefs), so one person walking the tour
 * or seeing What's new leaves everyone else's to come, and a restart keeps them.
 */
class WelcomePrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun theTourAndWhatsNewAreOncePerProfile() {
        val phone = FolderContext(tmp.root)
        val roy = Prefs(phone, "owner")
        assertFalse(roy.tourDone); assertNull(roy.whatsNewSeen)
        assertEquals(Welcome.Show.WHATS_NEW, Welcome.next(false, roy.tourDone, roy.whatsNewSeen, "2.7.0"))
        roy.whatsNewSeen = "2.7.0"
        roy.tourDone = true
        val again = Prefs(phone, "owner")                        // after a restart
        assertEquals(Welcome.Show.NOTHING, Welcome.next(false, again.tourDone, again.whatsNewSeen, "2.7.0"))
        val dad = Prefs(phone, "p2")                             // a second grown-up on the same phone still gets both
        assertEquals(Welcome.Show.WHATS_NEW, Welcome.next(false, dad.tourDone, dad.whatsNewSeen, "2.7.0"))
    }

    @Test fun theMicBannerAndContinueAreTheProfilesOwn() {
        val phone = FolderContext(tmp.root)
        Prefs(phone, "owner").micBannerClosed = true
        Prefs(phone, "owner").lastPlayed = ContinueWith.encode(ContinueWith.Lesson("rests"))
        assertTrue(Prefs(phone, "owner").micBannerClosed)
        assertFalse(Prefs(phone, "p1").micBannerClosed)
        assertEquals(ContinueWith.Lesson("rests"), ContinueWith.decode(Prefs(phone, "owner").lastPlayed))
        assertNull(Prefs(phone, "p1").lastPlayed)
    }

    /** Reset progress forgets where Continue stood (it is a resume), and keeps the tour and What's new as seen. */
    @Test fun resetForgetsContinueNotTheTour() {
        val p = Prefs(FolderContext(tmp.root), "owner")
        p.lastPlayed = "song:twinkle"; p.tourDone = true; p.whatsNewSeen = "2.7.0"
        p.resetProgress()
        assertNull(p.lastPlayed)
        assertTrue(p.tourDone)
        assertEquals("2.7.0", p.whatsNewSeen)
    }

    /** 2.8, the layout: kept per profile; unset, the launch asks (and the piano level decides meanwhile); null forgets it. */
    @Test fun theLayoutIsEachPersonsOwn() {
        val phone = FolderContext(tmp.root)
        assertNull(Prefs(phone, "owner").layout)
        Prefs(phone, "owner").layout = Layout.ADVANCED
        Prefs(phone, "p2").layout = Layout.SIMPLE
        assertEquals(Layout.ADVANCED, Prefs(phone, "owner").layout)              // after a restart
        assertEquals(Layout.SIMPLE, Prefs(phone, "p2").layout)
        assertNull(Prefs(phone, "p3").layout)
        assertTrue(Simple.asks(kid = false, chosen = Prefs(phone, "p3").layout))
        Prefs(phone, "owner").layout = null
        assertNull(Prefs(phone, "owner").layout)
        // Reset progress leaves the choice: a layout only filters what is shown.
        val p = Prefs(phone, "p2"); p.resetProgress()
        assertEquals(Layout.SIMPLE, p.layout)
    }

    /** 2.7's Simple switch, as a 2.7 phone kept it: on reads as Simple, off as Full, and neither is asked again. */
    @Test fun theSimpleSwitchCarriesOverAndIsNeverAskedAgain() {
        val phone = FolderContext(tmp.root)
        fun file(id: String) = phone.getSharedPreferences(ProgressFiles.fileFor("progress", id), android.content.Context.MODE_PRIVATE)
        file("owner").edit().putBoolean("simpleMode", true).apply()
        file("p2").edit().putBoolean("simpleMode", false).apply()
        assertEquals(Layout.SIMPLE, Prefs(phone, "owner").layout)
        assertEquals(Layout.FULL, Prefs(phone, "p2").layout)
        assertFalse(Simple.asks(kid = false, chosen = Prefs(phone, "owner").layout))
        assertFalse(Simple.asks(kid = false, chosen = Prefs(phone, "p2").layout))
        // A new choice replaces the old switch for good; forgetting the choice (the debug "unset") forgets it too.
        Prefs(phone, "owner").layout = Layout.ADVANCED
        assertEquals(Layout.ADVANCED, Prefs(phone, "owner").layout)
        assertFalse(file("owner").contains("simpleMode"))
        Prefs(phone, "p2").layout = null
        assertNull(Prefs(phone, "p2").layout)
        assertTrue(Simple.asks(kid = false, chosen = Prefs(phone, "p2").layout))
    }

    /** 2.15: the tips are each person's own, are kept through a restart and through Reset progress, and a used feature counts as seen. */
    @Test fun theTipsAreEachProfilesOwnAndSurviveAReset() {
        val phone = FolderContext(tmp.root)
        val a = Prefs(phone, "owner")
        assertTrue(a.tipsSeen.isEmpty()); assertEquals(0, a.hardRecaps)
        com.bigpapituna.sharpears.engine.nav.TipSession.reset()
        a.tipShown(com.bigpapituna.sharpears.engine.nav.Tips.Tip.TIMING)
        assertTrue(com.bigpapituna.sharpears.engine.nav.TipSession.shown)
        a.tipUsed(com.bigpapituna.sharpears.engine.nav.Tips.Tip.LOOP)
        a.hardRecaps = 2
        a.resetProgress()
        val again = Prefs(phone, "owner")
        assertEquals(setOf("timing", "loop"), again.tipsSeen); assertEquals(2, again.hardRecaps)
        assertTrue(Prefs(phone, "p2").tipsSeen.isEmpty())
        again.resetTips()
        assertTrue(Prefs(phone, "owner").tipsSeen.isEmpty())
        com.bigpapituna.sharpears.engine.nav.TipSession.reset()
    }

    /** A sight-reading level played at any level (the old pieces or a staff drill) means the Practice tip has no job. */
    @Test fun sightTriedFollowsAnyLevelPlayed() {
        val p = Prefs(FolderContext(tmp.root), "owner")
        assertFalse(p.sightTried())
        p.saveSightDrillBest(4, com.bigpapituna.sharpears.engine.DrillScore(right = 12, wrong = 2))
        assertTrue(p.sightTried())
    }
}
