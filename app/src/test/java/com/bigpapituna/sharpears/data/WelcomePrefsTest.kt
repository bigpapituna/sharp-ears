package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.nav.ContinueWith
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

    /** 2.7, Simple mode: kept per profile; unset, the piano level decides; set back to null it decides again. */
    @Test fun simpleModeIsEachPersonsOwn() {
        val phone = FolderContext(tmp.root)
        assertNull(Prefs(phone, "owner").simpleMode)
        Prefs(phone, "owner").simpleMode = false
        Prefs(phone, "p2").simpleMode = true
        assertEquals(false, Prefs(phone, "owner").simpleMode)                   // after a restart
        assertEquals(true, Prefs(phone, "p2").simpleMode)
        assertNull(Prefs(phone, "p3").simpleMode)
        Prefs(phone, "owner").simpleMode = null
        assertNull(Prefs(phone, "owner").simpleMode)
        // Reset progress leaves the choice: Simple only filters what is shown.
        val p = Prefs(phone, "p2"); p.resetProgress()
        assertEquals(true, p.simpleMode)
    }
}
