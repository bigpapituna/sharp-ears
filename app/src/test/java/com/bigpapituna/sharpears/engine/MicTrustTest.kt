package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicTrustTest {
    /** Every combination of the facts (keeping implies a build that can keep). */
    private val all: List<MicFacts> = buildList {
        for (bits in 0 until (1 shl 7)) {
            fun b(i: Int) = bits and (1 shl i) != 0
            val f = MicFacts(b(0), b(1), b(2), b(3), b(4), b(5), b(6))
            if (f.keepsRecordings && !f.canKeepRecordings) continue
            add(f)
        }
    }

    private fun words(f: MicFacts) = MicTrust.card(f).all + " " + MicTrust.privacy(f).joinToString(" ") { it.heading + " " + it.text }

    @Test fun aBuildThatKeepsRecordingsNeverSaysNothingIsKept() {
        for (f in all.filter { it.keeps }) {
            val w = words(f)
            for (claim in listOf("None of it is kept", "gone straight away", "discarded", "Nothing is kept", "nothing is recorded"))
                assertFalse("$f says \"$claim\"", w.contains(claim, ignoreCase = true))
            assertTrue("$f: the card states the exception", MicTrust.card(f).all.contains(MicTrust.KEEP_LINE))
            assertTrue("$f: the page states it", MicTrust.privacy(f)[0].text.contains(MicTrust.KEEP_LINE))
        }
    }

    @Test fun aBuildThatDoesNotKeepNeverMentionsTheException() {
        for (f in all.filter { !it.keeps }) {
            assertFalse("$f", words(f).contains(MicTrust.KEEP_LINE))
            assertFalse("$f", words(f).contains("keeps the last four"))
        }
        val plain = MicTrust.soundWords(MicFacts())
        assertEquals("It works out the notes on this phone as you play, and the sound is gone straight away. None of it is kept. " +
            "None of it is sent anywhere.", plain)
    }

    @Test fun theTesterSwitchOnlyWhereTheBuildHasIt() {
        for (f in all) {
            val card = MicTrust.card(f).all
            assertEquals("$f", f.testerDashboard, card.contains("Help Roy test"))
            assertEquals("$f", f.testerDashboard, MicTrust.leaving(f).any { it.startsWith("Help Roy test") })
            assertEquals("$f", f.testerDiary, MicTrust.leaving(f).any { it.contains("daily diary") })
            // Whatever the build, the card never says the tester sends sound.
            if (f.testerDashboard) assertTrue(card.contains("Never any sound."))
        }
    }

    @Test fun nothingIsSentIsSaidOnlyWhenNoSoundCanLeave() {
        for (f in all) {
            val says = MicTrust.soundWords(f).contains("None of it is sent anywhere")
            assertEquals("$f", !f.sendsReviewClips && !f.keeps, says)
            if (f.sendsReviewClips) assertTrue("$f", MicTrust.soundWords(f).contains("Send them for review is on"))
        }
    }

    @Test fun whatLeavesListsOnlyWhatThisBuildHas() {
        val none = MicTrust.privacy(MicFacts())[2]
        assertEquals(MicTrust.LEAVES, none.heading)
        assertTrue(none.text.startsWith("Nothing about you or your playing leaves this phone."))
        assertTrue("YouTube is always named", none.text.contains("YouTube"))
        for (f in all) {
            val l = MicTrust.leaving(f)
            assertEquals(f.aiBrief, l.any { "AI" in it })
            assertEquals(f.emailBrief, l.any { "by email" in it })
            assertEquals(f.canKeepRecordings, l.any { "Send them for review" in it })
            assertTrue("no names, no sound to the AI", l.filter { "AI" in it }.all { "no names" in it && "no sound" in it })
            val text = MicTrust.privacy(f)[2].text
            assertEquals(l.isEmpty(), text.startsWith("Nothing about you"))
        }
    }

    @Test fun threeHeadingsInOrderEachUnderOneCoverScreen() {
        for (f in all) {
            val p = MicTrust.privacy(f)
            assertEquals(listOf("What the app listens to", "What stays on this phone", "What leaves this phone"), p.map { it.heading })
            // The Fold's cover screen (443 dp wide, about 990 dp tall) holds about 30 lines of body text: 150 words is half of it.
            for (s in p) assertTrue("$f ${s.heading}: ${s.text.split(' ').size} words", s.text.split(' ').size <= 150)
        }
        for (f in all) assertTrue("the card: ${MicTrust.card(f).all.split(' ').size} words", MicTrust.card(f).all.split(' ').size <= 200)
    }

    @Test fun theCardSaysWhatThe215BriefAsks() {
        val c = MicTrust.card(MicFacts())
        assertEquals("How the microphone works", c.title)
        assertEquals(listOf("What it listens for", "What happens to the sound", "Kids' profiles", "Playing without the mic"), c.sections.map { it.heading })
        assertTrue(c.sections[0].text.startsWith("The notes you play, so it can check them"))
        assertTrue(c.sections[0].text.contains("never in the background"))
        assertTrue(c.sections[2].text.contains("same rule"))
        assertTrue(c.sections[3].text.contains("keys on the screen") && c.sections[3].text.contains("MIDI keyboard"))
        assertEquals("Not now", c.notNow)
        assertEquals("Allow the microphone", c.allow)
        assertEquals("This test build keeps the last four practice recordings on this phone to tune the listening. Turn this off in Settings, Sound.",
            MicTrust.KEEP_LINE)
        assertTrue(MicTrust.card(MicFacts(), child = "Mia").lead.startsWith("Mia's games can listen"))
    }

    @Test fun copyRules() {
        for (f in all) {
            val w = words(f) + " " + MicTrust.leaving(f).joinToString(" ")
            assertFalse("no em or en dash: $f", w.contains(Char(0x2014)) || w.contains(Char(0x2013)) || w.contains(" - "))
            assertFalse("no middle dot", w.contains(Char(0x00B7)))
            // Sentence case: no heading has a capital after its first word, but for names and the app's own labels.
            for (h in MicTrust.privacy(f).map { it.heading } + MicTrust.card(f).sections.map { it.heading })
                assertTrue(h, h.split(' ').drop(1).none { it.first().isUpperCase() })
        }
    }
}
