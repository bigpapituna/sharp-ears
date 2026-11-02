package com.bigpapituna.sharpears.engine.nav

import com.bigpapituna.sharpears.engine.nav.LearnTabs.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 2.18.3: Learn in four tabs (Road, Theory, Ear training, Sight reading), shown to whoever had the side rows before. */
class LearnTabsTest {
    @Test fun fourTabsRoadFirst() {
        assertEquals(listOf("Road", "Theory", "Ear training", "Sight reading"), Tab.entries.map { it.label })
        assertEquals(listOf("road", "theory", "ear", "sight"), Tab.entries.map { it.wire })
    }

    @Test fun grownUpsHaveAllFourExceptInSimple() {
        assertEquals(Tab.entries, LearnTabs.tabs(kid = false, reader = true, layout = Layout.FULL))
        assertEquals(Tab.entries, LearnTabs.tabs(kid = false, reader = false, layout = Layout.ADVANCED))
        // Simple keeps the road; its "Show more" widens the layout (Simple.shown) and brings the rest.
        assertEquals(listOf(Tab.ROAD), LearnTabs.tabs(kid = false, reader = true, layout = Layout.SIMPLE))
        assertEquals(Tab.entries, LearnTabs.tabs(kid = false, reader = true, layout = Simple.shown(Layout.SIMPLE, more = true)))
    }

    @Test fun aKidWhoCannotReadHasTheRoadAlone() {
        assertEquals(listOf(Tab.ROAD), LearnTabs.tabs(kid = true, reader = false, layout = Layout.FULL))
        assertEquals(Tab.entries, LearnTabs.tabs(kid = true, reader = true, layout = Layout.FULL))
    }

    @Test fun theSameRuleAsTheOldSideRows() {
        for (kid in listOf(true, false)) for (reader in listOf(true, false)) for (l in Layout.entries)
            assertEquals("$kid $reader $l", (!kid || reader) && Simple.learnSideRows(l), LearnTabs.tabs(kid, reader, l).size == 4)
    }

    @Test fun pickKeepsTheLastTabWhenItIsShownElseTheRoad() {
        assertEquals(Tab.EAR, LearnTabs.pick(Tab.EAR, Tab.entries))
        assertEquals(Tab.ROAD, LearnTabs.pick(Tab.EAR, listOf(Tab.ROAD)))
        assertEquals(Tab.ROAD, LearnTabs.pick(null, Tab.entries))
    }

    @Test fun parseReadsTheWireTheNameAndTheLabel() {
        assertEquals(Tab.SIGHT, LearnTabs.parse("sight"))
        assertEquals(Tab.SIGHT, LearnTabs.parse("Sight reading"))
        assertEquals(Tab.EAR, LearnTabs.parse(" EAR "))
        assertEquals(Tab.ROAD, LearnTabs.parse("road"))
        assertNull(LearnTabs.parse("chords"))
        assertNull(LearnTabs.parse(null))
    }
}
