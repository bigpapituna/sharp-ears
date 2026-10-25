package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.13, the cheer pops, read from the sources (composables) as StepPopWiringTest does: what feeds the rules (the lesson, its
 * embedded player, its stages, a song played on its own), where a pop is drawn (a lesson's header, a song's lane band, never
 * the keys or the instruction), the mic and voice rules, no touches, the setting in every layout, the step pop's bubble, the
 * tester event and the debug route through the gate.
 */
class CheerPopWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText().replace("\r\n", "\n")

    @Test fun aLessonFeedsOneSetOfRulesAndDrawsThePopOverItsHeader() {
        val lesson = src("ui/LessonScreen.kt")
        assertEquals(1, Regex("""rememberCheerPops\(""").findAll(lesson).count())
        assertTrue("key = lesson.id" in lesson.substringAfter("rememberCheerPops(").substringBefore("\n    cheerPops.chiming"))
        // Its embedded player and its Playground stages hear through the same rules; each step and each try starts them again.
        assertTrue("LocalCheers provides cheerPops" in lesson)
        assertTrue("LaunchedEffect(index, run) { cheerPops.stepBegan(\"\${lesson.id}-\${index + 1}\") }" in lesson)
        // A try that ends takes the pop away; a step passed hands its line to the step pop.
        val ended = lesson.substringAfter("fun exerciseEnded(").substringBefore("\n    }\n")
        assertTrue(ended.indexOf("cheerPops.clear()") in 0 until ended.indexOf("pops.fire("))
        assertTrue("line = cheerPops.stepPassed()" in ended)
        assertTrue("fun go(to: Int) { pops.flush(); cheerPops.clear();" in lesson && "fun complete() {\n        pops.flush()\n        cheerPops.clear()" in lesson)
        // Drawn over the header (the title and the dots), measured, never over the instruction; under the cheer bar.
        assertTrue("Column(Modifier.fillMaxWidth().onGloballyPositioned { headerBox.value = it.boundsInRoot() })" in lesson)
        assertTrue("CheerPop(cheerPops.cue,\n        Modifier.inRect { headerBox.value?.translate(" in lesson)
        assertTrue(lesson.indexOf("CheerPop(cheerPops.cue") in 1 until lesson.indexOf("CheerBar(cheer, title, line"))
        assertTrue("StepPopLine(pops.cue?.seq, pops.cue?.line, area = popArea" in lesson)
        // The sparkle never on top of the step pop's chime.
        assertTrue("cheerPops.chiming = { pops.chiming }" in lesson && "val chiming: Boolean get() = chimeJob?.isActive == true" in src("ui/StepPop.kt"))
    }

    @Test fun thePlayerAndTheStagesFeedTheRules() {
        val player = src("ui/PlayerScreen.kt")
        // A lesson's own rules, or a song's own when it is played on its own.
        assertTrue("val cheers = com.bigpapituna.sharpears.ui.pup.LocalCheers.current ?: if (embedded == null) com.bigpapituna.sharpears.ui.pup.rememberCheerPops(" in player)
        // Kept out of the body's locals (PlayerVars): one feed object.
        assertTrue("    val cheer = CheerFeed()" in player.substringAfter("private class PlayerVars("))
        val struck = player.substringAfter("fun keyStruck(").substringBefore("\n    fun setSpeed(")
        assertTrue("cheer.struck(cheers, transport, song, result, grade == HitLights.PERFECT, embedded != null, hintStep == HintStep.PLAY)" in struck)
        // Every wrong key shown (touch, MIDI and the mic's named ones) breaks the run, from the one place they are shown.
        assertTrue("cheer.wrong(cheers, transport, song, embedded != null)" in player.substringAfter("fun showWrongKey(").substringBefore("\n    }\n"))
        assertTrue("cheer.began(cheers, song)" in player.substringAfter("fun play() {").substringBefore("\n    }\n"))
        assertTrue("cheer.frame(cheers, transport, song, now / 1_000_000, hintStep == HintStep.PLAY, embedded != null)" in player)
        // A song's pop sits in the lane's top band (never the hit line or the notes being read), a lesson's over its header.
        val lane = player.substringAfter("fun Lane(modifier: Modifier)").substringBefore("    @Composable\n    fun Keys(")
        assertTrue("if (embedded == null && cheers != null)" in lane && "CheerPlace.laneHeightDp(maxHeight.value)" in lane && "CheerPlace.laneSides(" in lane)
        val feed = src("ui/CheerFeed.kt")
        // A chord counts once (a wait group done), a Perform note with its timing; misses from the transport's trouble log.
        assertTrue("t.mode == Mode.WAIT && result == InputResult.GROUP_DONE" in feed && "t.mode == Mode.PERFORM && result == InputResult.CORRECT" in feed)
        assertTrue("t.troubles.missedAt" in feed && "busy = t.mode == Mode.LISTEN || hinting" in feed)
        assertTrue("tailMs = if (embedded) minOf(2 * bar, (end - from) * 0.25) else 2 * bar" in feed)
        val stage = src("ui/playground/PlaygroundKit.kt")
        assertTrue("val cheers = com.bigpapituna.sharpears.ui.pup.LocalCheers.current" in stage)
        val found = stage.substringAfter("is Answer.Found -> {").substringBefore("is Answer.Miss ->")
        assertTrue(found.indexOf("cheers?.right(round, null,") in 0 until found.indexOf("foundLine(run, a.key)"))
        assertTrue("cheers?.wrong(run.done)" in stage && "betweenNotes = run.phase != RoundCoach.Phase.LISTEN" in stage)
    }

    @Test fun thePopTakesNoTouchesFollowsTheMicRuleAndLogsItsLine() {
        val pop = src("ui/pup/CheerPop.kt")
        for (touch in listOf("pointerInput", "clickable", "detectTap", "onClick", "onTap =")) assertFalse(touch, touch in pop)
        assertTrue("reducedMotion()" in pop && "graphicsLayer" in pop && "FxSlow.factor" in pop)
        assertTrue("if (still) { alpha = p.coerceIn(0f, 1f); return@graphicsLayer }" in pop)
        for (e in listOf("CheerEntry.POP_UP ->", "CheerEntry.PEEK && c.fromTop ->", "entry == CheerEntry.PEEK ->", "c.fromTop -> translationY", "else -> translationX"))
            assertTrue(e, e in pop)
        val show = pop.substringAfter("private fun show(").substringBefore("\n    }\n")
        assertTrue("CheerRules.voiceMode(prefs.voice && CheerRules.aloud(kid, prefs.cheerAloud), listening, phones, s.betweenNotes)" in show)
        assertTrue("CheerRules.soundMode(prefs.sound, chiming(), listening, phones, s.betweenNotes)" in show)
        assertTrue("ChimeMode.PLAY -> voice.speak(c.line)" in show && "ChimeMode.PLAY_DEAF -> voice.speakOverMic(c.line)" in show)
        assertTrue(show.indexOf("mic.deafFor(CheerRules.SPARKLE_DEAF_MS.toLong())") in 0 until show.indexOf("playSparkle("))
        assertTrue("Log.d(TAG, \"cheer: \${c.trigger.wire} \$audience \\\"\${c.line}\\\" voice=\$said\")" in show)
        assertTrue("if (telemetry) Tester.cheerPop(c.trigger, kid)" in show)
        assertTrue("} finally {\n        notes.forEach { sampler.noteOff(it.midi) }" in pop)
        // The design system's colours only (the accent and the pup's gold, the bubble in the theme's container).
        assertFalse(Regex("""Color\(0x""").containsMatchIn(pop))
        assertFalse("Color.White" in pop || "Color.Black" in pop)
        assertFalse("R.raw." in pop)
    }

    @Test fun encouragementShowsInEveryLayoutWithOftenLessAndOff() {
        val settings = src("ui/SettingsScreen.kt")
        assertTrue("if (shows(Simple.SettingsPart.ENCOURAGEMENT)) {" in settings && "Sub(\"Encouragement\")" in settings)
        assertTrue("prefs.cheerFrequency = f; com.bigpapituna.sharpears.data.Briefs.setting(\"cheer\", f.wire)" in settings)
        assertTrue("Toggle(\"Say it aloud\"" in settings)
        val line = settings.substringAfter("internal const val ENCOURAGEMENT_LINE = ").substringBefore("\n\n")
        assertFalse(Regex("[—–·]| - ").containsMatchIn(line))
        assertTrue("Less" in line && "Off" in line)
        val simple = src("engine/nav/Simple.kt")
        assertTrue("SettingsPart.ENCOURAGEMENT" in simple.substringAfter("val SETTINGS_ESSENTIALS").substringBefore("\n\n"))
        val prefs = src("data/Prefs.kt")
        assertTrue("CheerFrequency.parse(p.getString(\"cheer\", null))" in prefs && "p.contains(\"cheerAloud\")" in prefs)
    }

    @Test fun theDebugRouteGoesThroughTheGateAndReadsNoExtras() {
        val main = src("MainActivity.kt")
        assertTrue("route == \"cheerpop\" -> return setContent { com.bigpapituna.sharpears.ui.pup.CheerPopDebug(sampler, mic, voice, prefs) { finish() } }" in main)
        val debug = src("ui/pup/CheerPopDebug.kt")
        assertFalse(Regex("""get(String|Boolean|Int|Long|Float)Extra\(""").containsMatchIn(debug))
        assertTrue("telemetry = false" in debug && "cheers.debugFire(t, entry.value" in debug && "for (t in CheerTrigger.entries)" in debug)
        assertTrue("for (e in CheerEntry.entries)" in debug && "Text(\"Kid\")" in debug && "Text(\"Grown-up\")" in debug)
        val claude = File("../CLAUDE.md").readText()
        assertTrue("## 2.13 cheer pops" in claude && "--es screen cheerpop" in claude)
    }
}
