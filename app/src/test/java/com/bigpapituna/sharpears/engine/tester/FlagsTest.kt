package com.bigpapituna.sharpears.engine.tester

import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.15, the feature flags (engine/tester/Flags.kt): each read in one place, any value PostHog may send turned into the
 * setting, and the safe local default whenever there is none (offline, Help Roy test off, a keyless build) or it is odd.
 */
class FlagsTest {
    @Test fun thePlainLookOfferIsYesUnlessTheFlagSaysNo() {
        assertTrue("no value: the default", Flags.plainLookOffer(null))
        assertEquals(Flags.PLAIN_LOOK_OFFER_DEFAULT, Flags.plainLookOffer(null))
        assertFalse(Flags.plainLookOffer(false))
        assertTrue(Flags.plainLookOffer(true))
        assertFalse("an experiment's control group", Flags.plainLookOffer("control"))
        assertTrue(Flags.plainLookOffer("test"))
        assertTrue("an odd value: the default", Flags.plainLookOffer("purple"))
        assertTrue(Flags.plainLookOffer(42))
    }

    @Test fun encouragementStartsOftenUnlessTheFlagNamesAnother() {
        assertEquals(CheerFrequency.OFTEN, Flags.cheerFrequencyDefault(null))
        assertEquals(CheerFrequency.LESS, Flags.cheerFrequencyDefault("less"))
        assertEquals(CheerFrequency.OFF, Flags.cheerFrequencyDefault(" OFF "))
        assertEquals(CheerFrequency.OFTEN, Flags.cheerFrequencyDefault("often"))
        assertEquals("a boolean flag by mistake", CheerFrequency.OFTEN, Flags.cheerFrequencyDefault(true))
        assertEquals(CheerFrequency.OFTEN, Flags.cheerFrequencyDefault("sometimes"))
        assertEquals(CheerFrequency.DEFAULT, Flags.CHEER_FREQUENCY_FALLBACK)
    }

    /** The flags are read in one place only: analytics/Events.kt's AppFlags asks the door, and the settings ask AppFlags. */
    @Test fun theFlagsAreReadInOnePlace() {
        val main = File("src/main/java/com/bigpapituna/sharpears")
        val readers = main.walkTopDown().filter { it.extension == "kt" && "TesterSdk.flag(" in it.readText() }.map { it.name }.toSet()
        assertEquals(setOf("Tester.kt"), readers)
        val asks = main.walkTopDown().filter { it.extension == "kt" && "Tester.flag(" in it.readText().replace("TesterSdk.flag(", "") }.map { it.name }.toSet()
        assertEquals(setOf("Events.kt"), asks)
        assertTrue(File(main, "data/Prefs.kt").readText().contains("?: com.bigpapituna.sharpears.analytics.AppFlags.cheerFrequencyDefault"))
    }
}
