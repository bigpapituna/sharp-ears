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

    private fun src(path: String) = File(main, path).readText().replace("\r\n", "\n")

    /** The body of the lambda whose `{` is at [open]. */
    private fun lambdaAt(text: String, open: Int): String {
        var depth = 0
        for (i in open until text.length) when (text[i]) {
            '{' -> depth++
            '}' -> { depth--; if (depth == 0) return text.substring(open + 1, i) }
        }
        error("unbalanced braces after $open")
    }

    /** The index just past the `)` that closes the `(` at [open]. */
    private fun argsEnd(text: String, open: Int): Int {
        var depth = 0
        for (i in open until text.length) when (text[i]) {
            '(' -> depth++
            ')' -> { depth--; if (depth == 0) return i + 1 }
        }
        error("unbalanced parentheses after $open")
    }

    /**
     * QA 2.8.3 defect 1: Show Mum opens a child's session note after the hold alone (reading it is the point), and its Send,
     * Ask and Refresh reached the share sheet and the AI with no sum. Every action in the brief a kid can reach now asks the
     * real gate itself. Read from the sources: each button and action callback in the brief's screens goes through the gate,
     * apart from the ones that only read or move around (tabs, the preview's Short or Full, the sheet's draft, Back).
     */
    @Test fun everyActionInTheKidReachableBriefAsksTheGate() {
        val files = listOf("ui/brief/ParentBriefScreen.kt", "ui/brief/BriefPage.kt", "ui/brief/AskBox.kt").associateWith(::src)
        val reading = listOf(Regex("""^\s*onPick\("""), Regex("""^\s*full = (true|false)\s*$"""), Regex("""^\s*controller\.update\("""), Regex("""^\s*send\(\)\s*$"""))
        val opens = Regex("""onClick = \{|\.clickable\([^()]*\) \{|\bon(Refresh|Send|Press|Undo|Retry|Confirm|Ask|Change|Save) = \{""")
        var checked = 0
        for ((name, text) in files) for (m in opens.findAll(text)) {
            val body = Regex("""^\s*[\w, ]+ ->""").replaceFirst(lambdaAt(text, m.range.last), "")     // without its parameters
            checked++
            if (reading.any { it.containsMatchIn(body) }) continue
            assertTrue("$name: ${m.value} ${body.trim().take(120)} does not ask the gate", "guardBrief" in body || "guard {" in body || "guard(" in body)
        }
        assertTrue("found the brief's buttons: $checked", checked >= 14)
        // The rule itself: a child's brief always asks (unless the gate is open), a grown-up's own week while a kid plays.
        assertTrue(src("ui/brief/BriefPage.kt").contains("fun ParentGateController.guardBrief(about: Profile, action: () -> Unit) = if (about.isKid) guard(action) else guardForKids(action)"))
        // Ask: typed, a starter or the microphone, all through the guard; the page hands it the brief's gate.
        val ask = files.getValue("ui/brief/AskBox.kt")
        assertTrue(lambdaAt(ask, ask.indexOf("{", ask.indexOf("fun send()"))).contains("guard {"))
        assertTrue(ask.contains("onClick = { guard(::toggleMic) }"))
        assertTrue(files.getValue("ui/brief/BriefPage.kt").contains("guard = { a -> gate.guardBrief(page.profile, a) }"))
        // Send: the share sheet only from inside the guard; the sheets step aside while the gate shows (their window is on top).
        val screen = files.getValue("ui/brief/ParentBriefScreen.kt")
        val sendButton = screen.substringAfter("ChunkyButton(enabled = !sending").let { lambdaAt(it, it.indexOf("onClick = {") + "onClick = ".length) }
        assertTrue(sendButton.trimStart().startsWith("gate.guardBrief(who) {") && "Share.files(" in sendButton)
        assertTrue(screen.contains("if (gate.pending != null) return\n    ModalBottomSheet("))
        assertTrue(files.getValue("ui/brief/BriefPage.kt").contains("if (gate.pending == null) page.cards.entries"))
        // Show Mum: the hold to read, the gate's own host in its window, and the gate shut when it closes.
        val mum = src("ui/brief/KidBriefCard.kt").substringAfter("fun ShowMumDialog(").substringBefore("\n}\n")
        assertTrue(mum.contains("if (!passed) LightGate(onPass = { passed = true }, onCancel = close)"))
        assertTrue(mum.contains("ParentGateHost()"))
        assertTrue(mum.contains("val close = { if (profile.isKid) gate.close(); onClose() }"))
    }

    /** QA 2.8.3 defect 3: a kid's song recap is a Dialog; its Send to someone asked a gate drawn under it. The gate is hosted inside. */
    @Test fun theSongRecapDrawsTheGateInItsOwnWindow() {
        val player = src("ui/PlayerScreen.kt")
        val at = player.indexOf("RunRecap(r, speed")
        val dialog = player.lastIndexOf("Dialog(onDismissRequest", at)
        val content = lambdaAt(player, player.indexOf("{", argsEnd(player, dialog + "Dialog".length)))
        assertTrue("the recap is in the dialog", "RunRecap(r, speed" in content)
        assertTrue("so is the gate", "ParentGateHost(" in content)
        assertTrue("a gate left asked goes with the recap", "if (gate.pending != null) gate.cancel()" in content)
        assertTrue("its button asks the gate", src("ui/play/ShareConcert.kt").contains("onClick = { gate.guardForKids {"))
    }

    /** QA 2.8.3 defect 2: the launch picker opened the grown-ups' app, or made a new profile, for anyone who tapped. */
    @Test fun theLaunchPickerAsksTheGateForGrownUpsAndAddSomeone() {
        val picker = src("ui/family/ProfilePicker.kt")
        assertTrue(picker.contains("ParentGateRules.pickerAsks(roster.profiles, picked)) gate.guard(action) else action()"))
        assertTrue(picker.contains("onClick = { asked(p) { onPick(p.id) } }"))
        assertTrue(picker.contains("onClick = { asked(null) { adding = true } }"))
        val main = src("MainActivity.kt")
        val showPicker = main.substringAfter("private fun showPicker()").substringBefore("private fun openApp(")
        assertTrue("the picker draws the gate", showPicker.contains("ParentGateHost(gate)"))
    }

    /** QA 2.8.3 defect 10: no weekly alarm on a phone that refused notifications; the setting says so. */
    @Test fun theWeeklyAlarmNeedsNotifications() {
        assertTrue(src("reminder/BriefReminder.kt").contains("if (!WeeklyPlan.notifies(slot, Reminder.canNotify(context)))"))
        val weekly = src("ui/brief/WeeklyTime.kt")
        assertTrue(weekly.contains("WeeklyPlan.stateLabel(slot, canNotify)"))
        assertTrue(weekly.contains("if (slot.on && !canNotify) NotificationsOffNote()"))
        assertTrue(weekly.contains("Settings.ACTION_APP_NOTIFICATION_SETTINGS"))
    }
}
