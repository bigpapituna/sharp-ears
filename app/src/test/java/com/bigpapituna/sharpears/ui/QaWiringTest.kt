package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The 2.8.4 QA fixes that live in composables and Android code (docs/QA-2.8.3-grownup.md), read from the sources: the
 * player's orientation lock, the rename caret, the recap that stays on its result, the new-person form, one brief at a
 * time and Settings' place. The logic behind them is tested where it is pure (DailySessionTest, ReminderPlanTest, ...).
 */
class QaWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText().replace("\r\n", "\n")

    /** D1: the cover opened in landscape after a song on the inner screen. */
    @Test fun theLandscapeLockIsReleasedInTwoStepsAndOnEveryDisplayChange() {
        val player = src("ui/PlayerScreen.kt")
        assertTrue("LandscapeLock.hold(activity)" in player && "onDispose { LandscapeLock.release(activity) }" in player)
        assertFalse("no plain reset the inner screen can swallow", "requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }" in player)
        val user = player.indexOf("activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER")
        val unspecified = player.indexOf("activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED")
        assertTrue("USER first, then UNSPECIFIED", user in 0 until unspecified)
        val activity = src("MainActivity.kt")
        assertTrue("override fun onConfigurationChanged" in activity)
        assertTrue("if (!com.bigpapituna.sharpears.ui.LandscapeLock.held) com.bigpapituna.sharpears.ui.LandscapeLock.reset(this, \"display changed\")" in activity)
    }

    /** D8: typing in a rename went in front of the old name. */
    @Test fun renameDialogsStartWithTheCaretAfterTheOldName() {
        val v = caretAtEnd("Fur Elise")
        assertEquals("Fur Elise", v.text)
        assertEquals(9, v.selection.start)
        assertEquals(9, v.selection.end)
        assertTrue("caretAtEnd(m.title)" in src("ui/LibraryScreen.kt"))
        assertTrue("caretAtEnd(p.name)" in src("ui/family/FamilySettings.kt"))
        assertTrue("caretAtEnd(draft.scan.title)" in src("ui/MySongDialog.kt"))
    }

    /** D11: an outfit reveal scrolled a grown-up's recap past its stars and XP. */
    @Test fun aGrownUpsRecapStaysOnItsResult() {
        val reveal = src("ui/world/WardrobeReveal.kt")
        assertTrue("val scrollsItself = com.bigpapituna.sharpears.ui.family.LocalProfile.current.isKid" in reveal)
        assertEquals(2, Regex("if \\(scrollsItself\\)").findAll(reveal).count())
        assertTrue("if (short && kid && e.reward != null)" in src("ui/RunRecap.kt"))
    }

    /** D12: the keyboard hid "Who is this for?". */
    @Test fun theNewPersonFormBringsItsQuestionAboveTheKeyboard() {
        val form = src("ui/family/ProfilePicker.kt")
        assertTrue("Column(Modifier.bringIntoViewRequester(askKind)" in form)
        assertTrue("if (named && imeOpen && kind == null)" in form)
        assertTrue(form.indexOf("Modifier.bringIntoViewRequester(askKind)") < form.indexOf("Question(\"Who is this for?\")"))
    }

    /** D13: the notification's brief stacked over an open one. */
    @Test fun oneBriefIsOnScreenAtATime() {
        val entries = src("ui/brief/BriefEntries.kt")
        assertEquals("both brief dialogs take the one slot", 2, Regex("OneBriefAtATime\\(onClose\\)").findAll(entries).count())
    }

    /** D14: Settings came back at the top from Privacy or Licences. */
    @Test fun settingsKeepsItsPlaceWhileItsPagesAreOpen() {
        val activity = src("MainActivity.kt")
        assertTrue("scroll = settingsScroll, showAllState = settingsShowAll" in activity)
        assertEquals("every way in goes through openSettings", listOf("screen = Screen.Settings(back)"),
            Regex("screen = Screen[.]Settings[(][a-z]+[)]").findAll(activity).map { it.value }.toList())
        assertTrue("verticalScroll(scroll)" in src("ui/SettingsScreen.kt"))
    }
}
