package com.bigpapituna.sharpears

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.18, Stage sound (CLAUDE.md "2.18 Stage sound and voice"): the app's short sounds and the speaker-and-mic rule live in
 * audio/Stage.kt (logic in engine/stage). A screen asks the Stage; it never builds a SoundPool, reads the headphones or makes the
 * mic deaf by itself, so the rule stays in one place. Both builds (src/main and src/afterdark when present).
 */
class StageWiringTest {
    private val roots = listOf(File("src/main/java"), File("src/afterdark/java"), File("src/noafterdark/java")).filter { it.exists() }
    private fun sources() = roots.flatMap { r -> r.walkTopDown().filter { it.extension == "kt" }.toList() }
    private fun File.rel() = invariantSeparatorsPath.substringAfter("/sharpears/")

    @Test fun onlyTheStageBuildsASoundPool() {
        val users = sources().filter { "SoundPool" in it.readText().lines().filterNot { l -> l.trimStart().startsWith("*") || l.trimStart().startsWith("//") }.joinToString("\n") }
            .map { it.rel() }
        assertTrue("SoundPool outside the Stage: $users", users == listOf("audio/Stage.kt"))
    }

    @Test fun onlyTheStageReadsTheHeadphonesOrMakesTheMicDeaf() {
        for (f in sources()) {
            val rel = f.rel()
            if (rel == "audio/Stage.kt" || rel.startsWith("engine/stage/")) continue
            val code = f.readText().lines().filterNot { l -> l.trimStart().startsWith("*") || l.trimStart().startsWith("//") }.joinToString("\n")
            assertTrue("$rel reads the headphones itself", "headphonesOn(" !in code && "GET_DEVICES_OUTPUTS" !in code)
            assertTrue("$rel makes the mic deaf by itself", Regex("""(?<!Stage)\.deafFor\(""").find(code) == null)
            assertTrue("$rel opens a deaf window by itself", "window.open(" !in code && "deafWhileSpeaking" !in code)
        }
    }

    @Test fun screensAskTheStageForTheRule() {
        val main = File("src/main/java/com/bigpapituna/sharpears")
        fun read(p: String) = File(main, p).readText()
        // The echo of a touched key in every Playground stage and the arcade: one call, the rule inside.
        val taps = main.walkTopDown().filter { it.extension == "kt" }.sumOf { Regex("""audio\.Stage\.tap\(""").findAll(it.readText()).count() }
        assertTrue("Stage.tap call sites: $taps", taps >= 20)
        assertTrue("Stage.echo(prefs.sound, listening = micOn && mode != Mode.LISTEN)" in read("ui/PlayerScreen.kt"))
        assertTrue("audio.Stage.cue(sound())" in read("ui/StepPop.kt"))
        assertTrue("Stage.attach(mic)" in read("MainActivity.kt"))
        // The coach's lines go through the Stage's line windows (the build with it).
        File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachHost.kt").takeIf { it.exists() }?.readText()?.let { host ->
            assertTrue("Stage.lineStarts(" in host && "Stage.lineEnds(" in host)
        }
    }
}
