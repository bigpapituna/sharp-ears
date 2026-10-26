package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.14 parents, read from the sources: the minutes ledger's checkpoints and flushes, every kid door the daily time and wind
 * down close, the gate's new closings and Lock now on every grown-ups' screen, Family today and the brief's first lines.
 */
class ParentsWiringTest {
    private val main = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(main, path).readText()
    private val activity by lazy { src("MainActivity.kt") }

    @Test fun theOpenSessionIsCheckpointedAndEveryPracticeCounterFlushesFirst() {
        assertTrue("every 30 s while on screen", Regex("""repeatOnLifecycle\(androidx\.lifecycle\.Lifecycle\.State\.STARTED\) \{\s*while \(true\) \{ kotlinx\.coroutines\.delay\(Briefs\.CHECKPOINT_MS\); Briefs\.checkpoint\(\) \}""").containsMatchIn(activity))
        val pause = Regex("(?s)override fun onPause\\(\\) \\{(.*?)\\n    \\}").find(activity)!!.groupValues[1]
        assertTrue("and on every pause", "Briefs.checkpoint()" in pause)
        for (f in listOf("ui/PlayerScreen.kt", "ui/playground/PlaygroundStage.kt", "ui/arcade/ArcadeGameScreen.kt", "MainActivity.kt"))
            assertTrue("$f flushes its practice before a checkpoint and an end", "Briefs.addFlusher(f)" in src(f) && "Briefs.removeFlusher(f)" in src(f))
        assertTrue("the email job's start closes a killed session too", "Briefs.init(app)" in src("reminder/EmailJob.kt"))
    }

    @Test fun everyKidDoorAsksTheDay() {
        // The screens that play: what opens after the day closed ends at once, what runs ends at its boundary or the grace.
        val doors = Regex("(?s)val kidDoor = if \\(!profile\\.isKid\\) null else when \\(screen\\) \\{(.*?)\\n\\s*else -> null").find(activity)!!.groupValues[1]
        for (s in listOf("Screen.Lesson", "Screen.Player", "Screen.PopSong", "Screen.KidChords", "Screen.Play", "Screen.Arcade", "Screen.ArcadeRound"))
            assertTrue("$s has a door", Regex("""\b${Regex.escape(s)}\b""").containsMatchIn(doors))
        assertTrue("the lesson's next step is the boundary", "if (profile.isKid && capSince >= 0) closedForToday(\"a lesson step ended\")" in activity)
        assertTrue("a song's end is the boundary", "if (ranToEnd && capSince >= 0) { kotlinx.coroutines.delay(4_000L); closedForToday(\"a song ended\") }" in activity)
        assertTrue("a stone", "onStone = { st -> if (capSince >= 0) { capGoodbye = true; sayTomorrow() }" in activity)
        assertTrue("the Play sheet's tiles", "playClosed = !com.bigpapituna.sharpears.engine.skin.KidDoors.open(com.bigpapituna.sharpears.engine.skin.KidDoor.TOYS, kidDay)" in activity)
        assertTrue("the Songs tiles", "closed = profile.isKid && !com.bigpapituna.sharpears.engine.skin.KidDoors.open(com.bigpapituna.sharpears.engine.skin.KidDoor.SONGS, kidDay)" in activity)
        assertTrue("the goodbye offers only the goodbye", "songsOpen = !songsAsleep" in activity && "capped = true" in activity)
        assertTrue("the lesson's own goodbyes too", src("ui/LessonScreen.kt").split("songsOpen = songsOpen").size == 3)
        assertTrue(src("ui/kid/KidPlaySheet.kt").contains("KidDoors.TOMORROW") && src("ui/kid/KidSongs.kt").contains("KidDoors.TOMORROW"))
    }

    @Test fun theGateClosesOnSwitchesAndAfterAwayAndLocksFromEveryGrownUpsScreen() {
        assertTrue("every switch, not a recreate", "ParentGateRules.closesOnOpen(openId, profile.id, recreated)" in activity)
        val start = Regex("(?s)override fun onStart\\(\\) \\{(.*?)\\n    \\}").find(activity)!!.groupValues[1]
        val stop = Regex("(?s)override fun onStop\\(\\) \\{(.*?)\\n    \\}").find(activity)!!.groupValues[1]
        assertTrue("gate.back()" in start && "gate.away()" in stop)
        for ((f, n) in listOf("ui/kid/KidHome.kt" to 1, "ui/family/FamilyDialog.kt" to 1, "ui/brief/ParentBriefScreen.kt" to 1, "MainActivity.kt" to 2))
            assertTrue("$f: Lock now", src(f).split("LockNowRow(").size - 1 >= n)
        val gate = src("ui/family/ParentGate.kt")
        assertTrue("the question is rolled", "ParentGateRules.nextQuestion()" in gate && "nextSum" !in gate)
        // A corner left open (or a kid's Settings) closes with the gate, and its rows ask again once the two minutes ran out.
        assertTrue("if (wasOpen) closings++" in gate)
        assertTrue(activity.contains("LaunchedEffect(gate.closings) { if (gate.closings != closings0) { corner = false; familyOpen = false; briefOpen = false } }"))
        assertTrue(activity.split("if (profile.isKid) LaunchedEffect(gate.closings) { if (gate.closings != closings0) screen = s.back }").size == 3)
        for (row in listOf("onSettings = { gate.guard {", "onMicSetup = { gate.guard {", "onSwitch = { id -> gate.guard {")) assertTrue(row, row in activity)
    }

    @Test fun familyTodayAndTheBriefsFirstLines() {
        assertTrue("family?.invoke()" in src("ui/TodayScreen.kt"))
        assertTrue("FamilyTodayCard(kids, onOpen" in activity)
        val page = src("ui/brief/BriefPage.kt")
        assertTrue("CanDoWords.line(f.canDo, env.titles)" in page && "CanDoWords.minutesLine(f.stats.totals.practiceMinutes, f.stats.totals.minutes)" in page)
        assertTrue("the daily time is in the corner and in Family", "KidTimeControls(" in src("ui/kid/KidHome.kt") && "KidTimeControls(" in src("ui/family/FamilySettings.kt"))
    }
}
