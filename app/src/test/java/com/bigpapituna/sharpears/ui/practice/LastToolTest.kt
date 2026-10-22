package com.bigpapituna.sharpears.ui.practice

import com.bigpapituna.sharpears.data.FolderContext
import com.bigpapituna.sharpears.data.PracticeStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 2.7: the Practice tab opens on the last tab or tool used, kept per profile in its own store, and the metronome and the
 * tuner live under Tools beside Scales and Warm-ups.
 */
class LastToolTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun aFirstVisitOpensOnScales() {
        assertEquals(PracticeTab.SCALES, PracticeStore(FolderContext(tmp.root), "owner").lastTool())
    }

    @Test fun theLastToolIsKeptPerProfileAndSurvivesARestart() {
        val phone = FolderContext(tmp.root)
        PracticeStore(phone, "owner").putEnum(LAST_TOOL, PracticeTab.TUNER)
        PracticeStore(phone, "p1").putEnum(LAST_TOOL, PracticeTab.WARMUPS)
        assertEquals(PracticeTab.TUNER, PracticeStore(phone, "owner").lastTool())      // a new store, as after a restart
        assertEquals(PracticeTab.WARMUPS, PracticeStore(phone, "p1").lastTool())
        assertEquals(PracticeTab.SCALES, PracticeStore(phone, "p2").lastTool())
    }

    @Test fun anUnknownValueFromAnotherVersionOpensOnScales() {
        val phone = FolderContext(tmp.root)
        PracticeStore(phone, "owner").putString(LAST_TOOL, "CHORDS")
        assertEquals(PracticeTab.SCALES, PracticeStore(phone, "owner").lastTool())
    }

    @Test fun scalesAndWarmUpsAreTheMainTabsAndToolsHoldsTheMetronomeAndTuner() {
        assertEquals(listOf("Scales", "Warm-ups", "Tools"), PracticeSection.entries.map { it.label })
        assertEquals(setOf(PracticeTab.METRONOME, PracticeTab.TUNER), PracticeTab.entries.filter { it.section == PracticeSection.TOOLS }.toSet())
        assertEquals(PracticeSection.SCALES, PracticeTab.ARPEGGIOS.section)
        assertEquals(PracticeSection.WARMUPS, PracticeTab.WARMUPS.section)
    }
}
