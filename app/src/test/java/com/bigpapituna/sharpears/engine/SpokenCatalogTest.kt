package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SpokenCatalogTest {
    @Test fun keysIgnoreSpacingAndAreStable() {
        assertEquals(SpokenCatalog.key("Lesson complete."), SpokenCatalog.key("  Lesson   complete. "))
        assertEquals(16, SpokenCatalog.key("x").length)
        assertTrue(SpokenCatalog.key("Lesson complete.") != SpokenCatalog.key("Lesson complete!"))
    }

    /** Also writes build/voice-lines.txt, the list scripts/render_voice.py records: key, text and speed (kid lines slower). */
    @Test fun catalogIsSpeakableAndWritten() {
        val lines = SpokenCatalog.all()
        assertTrue("lesson steps and prompts: ${lines.size}", lines.size > 300)
        lines.forEach { assertTrue("speakable: $it", it.isNotBlank() && it.none { c -> c in "—–·#&/" }) }
        assertEquals("keys are unique", lines.size, lines.map(SpokenCatalog::key).toSet().size)
        val kid = SpokenCatalog.kidLines().toSet()
        assertTrue("every kid line is in the catalog", lines.containsAll(kid))
        File("build/voice-lines.txt").writeText(lines.joinToString("\n") {
            "${SpokenCatalog.key(it)}\t$it\t${if (it in kid) SpokenCatalog.KID_SPEED else 1.0}"
        } + "\n")
    }

    /**
     * 2.2.1 QA: a fixed line a screen speaks is in the catalog, so it is recorded and plays at once in the pup's voice. The
     * octave hint, the noisy-room offer and the kid home's treat jar were missing (made on the phone, or by the platform
     * voice where the model is not installed); a source scan catches a new plain line handed to the voice.
     */
    @Test fun everyFixedLineAScreenSpeaksIsRecorded() {
        val all = SpokenCatalog.all().toSet()
        val kid = SpokenCatalog.kidLines().toSet()
        for (l in com.bigpapituna.sharpears.engine.hints.OctaveHint.LINES) assertTrue(l, l in all)
        assertTrue(com.bigpapituna.sharpears.engine.hints.NoiseWatch.LINE_KID in kid)
        for (l in com.bigpapituna.sharpears.engine.world.TreatJar.KID_LINES) assertTrue(l, l in kid)
        val literal = Regex("""\b(?:speak|speakOverMic|say)\("((?:[^"\\$]|\\.)+)"\s*[,)]""")
        val spoken = File("src/main/java").walkTopDown().filter { it.extension == "kt" && "Demo" !in it.name }
            .flatMap { f -> literal.findAll(f.readText()).map { f.name to it.groupValues[1] } }.toList()
        assertTrue("found the plain lines: ${spoken.size}", spoken.isNotEmpty())
        for ((file, line) in spoken) assertTrue("$file says \"$line\", which is not recorded", SpokenCatalog.normalize(line) in all)
    }

    /** 2.1: the kid skin, the Playground's stages and the play corner are in, and nothing with a name is recorded. */
    @Test fun kidLinesAreListed() {
        val kid = SpokenCatalog.kidLines()
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.skin.SkinLines.fixed()))
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.PlaygroundLines.CALL_OUTS))
        assertTrue("Find a fence!" in kid && "You did it!" in kid)
        // 2.2: the pond's and the concert hall's steps, scenes and call-outs, at kid speed too.
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.PageLines.ALL))
        assertTrue("Your turn: row, row, row your boat." in kid && "Find every D. D is the orange dog, inside the dog house." in kid)
        assertTrue(com.bigpapituna.sharpears.engine.playground.HALL.lessons.flatMap { it.steps }.flatMap { s -> s.scenes.map { it.say } }.all { it in kid })
        assertTrue("no names", kid.none { "Mia" in it || "{name}" in it })
    }
}
