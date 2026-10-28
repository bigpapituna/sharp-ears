package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.11, the step pop, read from the sources (these are composables) as PupWiringTest does: it fires from the one place a
 * lesson step's try ends, the chime follows App sound and the mic rule, the pup's line waits for it, nothing in it takes a
 * touch or holds up the page, all four pictures are drawn, and the debug route goes through the gate.
 */
class StepPopWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    /** The source with plain line ends (a Windows checkout has CRLF), so a match may span lines. */
    private fun src(path: String) = File(root, path).readText().replace("\r\n", "\n")

    @Test fun aLessonPopsFromTheEndOfAStepsTryOnly() {
        val lesson = src("ui/LessonScreen.kt")
        val ended = lesson.substringAfter("fun exerciseEnded(").substringBefore("\n    }\n")
        // Every step's try (the player's onFinished and the Playground stage's onEnded) ends in exerciseEnded, which pops.
        assertTrue("onEnded = { exerciseEnded(step, it) }" in lesson && "onFinished = { exerciseEnded(step, it) }" in lesson)
        assertTrue("StepPopRules.outcome(listen = s.kind == StepKind.LISTEN, judged = judged, letGo = letGo, last = last," in ended)
        assertTrue("StepPopRules.mostlyLetGo(report.letGoRounds, report.rounds) || StepPopRules.mostlyLetGo(tally?.letGos ?: 0, report.groups)" in ended)
        // The picture is rolled per lesson step, and the pup's beat goes with it (none with animations off).
        // 2.13: a step passed hands the cheer pops' line to the pop (its bubble), null with Encouragement off.
        assertTrue("val pop = if (outcome == StepOutcome.POP) pops.fire(streak, StepPopRules.seedOf(lesson.id, index), line = cheerPops.stepPassed()) else null" in ended)
        assertTrue("StepPopRules.pupBeat(pop.variant)" in ended && "else if (still) PupMood.IDLE" in ended)
        assertEquals("one pop in the lesson", 1, Regex("""pops\.fire\(""").findAll(lesson).count())
        // The pup's line and the high five wait for the chime; a page left early says them first.
        assertTrue("line?.let { l -> pops.afterChime { com.bigpapituna.sharpears.extras.Extras.afterLine { voice.speak(l.spoken) } } }" in ended)   // 2.16: and after the extra's line
        assertTrue("pops.afterChime { com.bigpapituna.sharpears.extras.Extras.afterLine { voice.speak(com.bigpapituna.sharpears.engine.HIGH_FIVE) } }" in ended)
        assertTrue("fun go(to: Int) { pops.flush();" in lesson && "fun complete() {\n        pops.flush()" in lesson)
        // A new lesson starts a new streak; a graded step skipped starts the climb again.
        assertTrue("key = lesson.id" in lesson && "pops.streak.skipped()" in lesson)
        // The pop sits over the step and under the cheer bar (drawn before it), in the part of the step the bar leaves in view.
        assertTrue(lesson.indexOf("StepPop(pops.cue") in 1 until lesson.indexOf("CheerBar(cheer, title, line"))
        assertTrue("onSizeChanged { barPx.intValue = it.height }" in lesson)
        // The next page is not held up: Continue goes straight on, as before.
        assertTrue("if (cheer == Cheer.GOOD) complete() else" in lesson)
    }

    @Test fun theChimeFollowsAppSoundAndTheMicRule() {
        val pop = src("ui/StepPop.kt")
        assertTrue("StepPopRules.chimeMode(micListening = mic.listening, headphones = mic.headphonesOn(), soundOn = sound())" in pop)
        assertTrue("sound = { deps.prefs.sound }" in src("ui/LessonScreen.kt"))
        // The mic is deaf before the first note strikes, for the chime and the room's ring.
        val fire = pop.substringAfter("fun fire(streak: Int").substringBefore("\n    }\n")
        assertTrue(fire.indexOf("mic.deafFor(StepPopRules.DEAF_MS.toLong())") in 0 until fire.indexOf("playChime("))
        assertTrue("if (mode != ChimeMode.SKIP)" in fire)
        // The chime does not depend on the picture: the same family, climbing with the streak.
        assertTrue("StepPopRules.chime(tonic, streak)" in fire)
        // The log line QA reads, and the tester event (streak only).
        assertTrue("Log.d(TAG, \"step pop: streak \$streak, chime \$mode, \${variant.name.lowercase()}\")" in fire && "Tester.stepPop(streak)" in fire)
        assertTrue("StepPopRules.variant(streak, cue?.variant, seed)" in fire)
        // The chime's notes are always let go, also when the screen goes mid-chime.
        assertTrue("} finally {\n        notes.forEach { sampler.noteOff(it.midi) }" in pop)
        // No new sound files: the piano samples.
        assertFalse("R.raw." in pop)
    }

    @Test fun thePicturesTakeNoTouchDrawFromOneClockAndAreAllFour() {
        val pop = src("ui/StepPop.kt")
        for (touch in listOf("pointerInput", "clickable", "detectTap", "onClick")) assertFalse(touch, touch in pop)
        val composable = pop.substringAfter("fun StepPop(").substringBefore("/** BURST:")
        assertTrue("reducedMotion()" in composable && "withFrameNanos" in composable && "Canvas(modifier.graphicsLayer())" in composable)
        // Animations off: the same single check fade for every picture, before any of them is drawn.
        assertTrue(composable.indexOf("if (still) { drawCheckDisc(box.center, 1f, StepPopMotion.calmAlpha(ms), 1f); return@Canvas }") in 0 until
            composable.indexOf("when (c0.variant)"))
        for (v in listOf("BURST -> burst(", "CONFETTI -> confetti(", "RAINBOW -> rainbow(", "PAW -> paw(")) assertTrue(v, v in composable)
        // Colours from the design system only.
        assertTrue("listOf(Accent, PupGold, Good, LeftHandColor)" in pop)
        assertFalse(Regex("""Color\(0x""").containsMatchIn(pop))
        assertFalse("Color.White" in pop || "Color.Black" in pop)
    }

    @Test fun theDebugRouteGoesThroughTheGateAndReadsNoExtras() {
        val main = src("MainActivity.kt")
        assertTrue("route == \"steppop\" -> return setContent { com.bigpapituna.sharpears.ui.StepPopDebug(sampler, mic, prefs) { finish() } }" in main)
        val debug = src("ui/StepPopDebug.kt")
        assertFalse(Regex("""get(String|Boolean|Int|Long|Float)Extra\(""").containsMatchIn(debug))
        assertTrue("listOf(1, 3, 6)" in debug && "for (v in PopVariant.entries) OutlinedButton(onClick = { pop(v) })" in debug)
        assertTrue("telemetry = false" in debug && "pops.fire(streak.intValue" in debug)
        val claude = File("../CLAUDE.md").readText()
        assertTrue("## 2.11 step pop" in claude && "--es screen steppop" in claude)
    }
}
