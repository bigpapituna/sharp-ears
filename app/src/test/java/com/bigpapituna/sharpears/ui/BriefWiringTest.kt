package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.data.ProgressFiles
import com.bigpapituna.sharpears.engine.play.ConcertFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The parent brief's wiring, read from the sources: the receiver, the share types, the backup, the copy rules in its screens. */
class BriefWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")

    @Test fun theWeeklyReceiverIsRegisteredForAlarmsBootAndTimeChanges() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val block = Regex("(?s)<receiver android:name=\".reminder.BriefReceiver\".*?</receiver>").find(manifest)!!.value
        for (a in listOf("BOOT_COMPLETED", "TIMEZONE_CHANGED", "TIME_SET")) assertTrue(a, "android.intent.action.$a" in block)
        assertTrue("not exported", "android:exported=\"false\"" in block)
        assertTrue("dictation can see the speech service", "android.speech.RecognitionService" in manifest)
        val src = File(main, "reminder/BriefReminder.kt").readText()
        assertTrue("only a system broadcast sets every alarm; the alarm itself posts", src.contains("if (intent.action != null) { BriefReminder.scheduleAll(app); return }"))
        assertTrue(File(main, "MainActivity.kt").readText().contains("BriefReminder.scheduleAll(this)"))
    }

    @Test fun theBriefIsBackedUpAndTheDebugRouteIsThere() {
        assertTrue("brief" in ProgressFiles.NAMES)
        assertTrue(File(main, "MainActivity.kt").readText().contains("route == \"brief\" -> return setContent"))
    }

    @Test fun aPdfAndAPictureShareTogether() {
        assertEquals("application/pdf", ConcertFiles.typeOf("Mia-weekly-brief-2026-10-21.pdf"))
        assertEquals("image/png", ConcertFiles.typeOf("Mia-weekly-brief-2026-10-21.png"))
        assertEquals("*/*", ConcertFiles.mimeType(listOf("a.png", "a.pdf")))
    }

    @Test fun theBriefsOwnSourcesHaveNoDashesOrSeparatorGlyphsInTheirWords() {
        val files = File(main, "ui/brief").listFiles { f -> f.extension == "kt" }.orEmpty().toList() + File(main, "engine/brief").listFiles { f -> f.name in OURS }.orEmpty().toList()
        assertTrue(files.size > 15)
        // Only comments and code may use them; a string literal never does.
        val literal = Regex("\"[^\"\\n]*[\u2014\u2013\u00b7][^\"\\n]*\"")
        for (f in files) for (m in literal.findAll(f.readText())) assertFalse("${f.name}: ${m.value}", true)
    }

    private val OURS = setOf("AskBrief.kt", "BriefActions.kt", "BriefFormat.kt", "BriefSamples.kt", "ExportLayout.kt", "KidCardWords.kt", "WeeklySchedule.kt")
}
