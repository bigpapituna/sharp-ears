package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.2.1 QA: the Sound switches reach every screen they describe. Read from the sources (these are composables).
 * App sound off still let the Chords tab's Dictionary, Practice, Progressions and Daily keys, and the Practice tab's
 * scale keys and "Play it", sound; Vibration off still buzzed a chord game's wrong note, and for mic notes too.
 */
class SettingsWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")

    @Test fun appSoundSilencesTheChordsAndPracticeKeys() {
        for (f in listOf("ui/ChordsScreen.kt", "ui/practice/PracticeScreen.kt")) {
            val calls = File(root, f).readLines().filter { "sampler.noteOn(" in it }
            assertTrue(f, calls.isNotEmpty())
            calls.forEach { assertTrue("$f plays past App sound: ${it.trim()}", "sound()" in it) }
        }
    }

    /** A wrong-note buzz answers a key touched on the screen, and only with Settings > Vibration on (debug demos aside). */
    @Test fun aWrongNoteBuzzHonoursTheVibrationSwitch() {
        val files = root.walkTopDown().filter { it.extension == "kt" && !it.name.endsWith("Demo.kt") && "haptics.miss()" in it.readText() }.toList()
        assertTrue(files.size >= 2)
        for (f in files) assertTrue("${f.name} buzzes without the switch", "it.enabled = prefs.haptics" in f.readText())
    }
}
