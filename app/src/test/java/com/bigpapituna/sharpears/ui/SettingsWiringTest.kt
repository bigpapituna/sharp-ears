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

    /**
     * A switch acts at once: Spoken guidance off stopped only the next line (the one playing, and the queue, went on);
     * Show wrong keys stayed on, and did nothing, after the noisy-room offer stopped the painting for the session; and a
     * kid's "Reads now" kept the old course path on the kid map and Practice today until a restart.
     */
    @Test fun switchesActAtOnce() {
        fun src(f: String) = File(root, f).readText()
        assertTrue("prefs.voice = it; if (!it) voice?.stop()" in src("ui/SettingsScreen.kt"))
        val mic = src("ui/MicSetupScreen.kt")
        assertTrue("mutableStateOf(mic.paintsWrongKeys)" in mic && "if (it) mic.wrongPaintOff = false" in mic)
        assertTrue("listOf(it.id, it.kind, it.reader, it.level)" in src("MainActivity.kt"))      // 2.6: a new piano level re-plans in place
    }
}
