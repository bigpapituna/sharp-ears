package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
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
        // 2.6: a new piano level re-plans in place; 2.14: a new age band reopens the app (the map or the tabs).
        assertTrue("listOf(it.id, it.kind, it.reader, it.level, it.band)" in src("MainActivity.kt"))
    }

    /**
     * 2.7: Settings under six headers in this order, Testing last, and no switch lost or renamed on the way: each still writes
     * its own preference and tells the brief its own key (Briefs.setting), and Show wrong keys in Playing is Microphone
     * setup's switch (the same MicInput.wrongFlash and the same way back from the noisy-room offer).
     */
    @Test fun settingsAreGroupedAndKeepEverySwitch() {
        val s = File(root, "ui/SettingsScreen.kt").readText()
        val order = listOf("Family", "Playing", "Briefs and email", "Microphone", "About", "Testing").map { s.indexOf("Section(\"$it\")") }
        assertTrue("every group is there: $order", order.all { it >= 0 })
        assertEquals(order.sorted(), order)
        for (key in listOf("keyLabels", "noteNames", "fingerNumbers", "feedbackLight", "beatLines", "ghostHand", "sound", "haptics", "voice", "naturalVoice", "reminderOn", "wrongFlash"))
            assertTrue("Settings lost the $key switch", "Briefs.setting(\"$key\"" in s)
        for (pref in listOf("prefs.keyLabels = k", "prefs.noteNames = it", "prefs.fingerNumbers = it", "prefs.feedbackLight = it", "prefs.beatLines = it",
            "prefs.ghostHand = it", "prefs.sound = it", "prefs.haptics = it", "prefs.naturalVoice = it", "prefs.reminderOn = on", "it.copy(colourNotes = on)"))
            assertTrue("Settings no longer writes $pref", pref in s)
        assertTrue("mutableStateOf(mic.paintsWrongKeys)" in s && "mic.wrongFlash = it; if (it) mic.wrongPaintOff = false" in s)
        // The family's own switches (AI briefs, Testing) moved to their groups; the kids' corner's Family keeps them.
        assertTrue("FamilySettings(profiles, extras = false)" in File(root, "MainActivity.kt").readText())
        // 2.8: Briefs and email is the email form, with who writes the brief at its bottom.
        assertTrue("com.bigpapituna.sharpears.ui.brief.email.BriefsAndEmail(kids)" in s)
        assertTrue("BriefAiSettings(gate)" in File(root, "ui/brief/email/BriefsAndEmail.kt").readText())
    }

    /**
     * 2.8: one email form. The address field lives in BriefsAndEmail only; Family's child has no "Email the brief"; the parent
     * brief's Email button opens the same composable in a dialog (SimpleTest checks that Simple shows the section).
     */
    @Test fun theBriefByEmailHasOnePlace() {
        val files = root.walkTopDown().filter { it.extension == "kt" }.toList()
        assertEquals(listOf("BriefsAndEmail.kt"), files.filter { "label = { Text(\"Your email address\") }" in it.readText() }.map { it.name })
        assertEquals(emptyList<String>(), files.filter { "Text(\"Email the brief\")" in it.readText() }.map { it.name })
        assertTrue("EmailDialog(children.filter { it.isKid }" in File(root, "ui/brief/ParentBriefScreen.kt").readText())
        val dialog = File(root, "ui/brief/email/EmailPreview.kt").readText()
        assertTrue("Gated(onCancel = onClose) { BriefsAndEmailPage(kids, onBack = onClose) }" in dialog)
        assertTrue("BriefsAndEmail(kids)" in File(root, "ui/brief/email/BriefsAndEmail.kt").readText())
    }
}
