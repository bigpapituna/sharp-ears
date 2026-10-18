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

    /** 2.1: the kid skin, the Playground's stages and the play corner are in, and nothing with a name is recorded. */
    @Test fun kidLinesAreListed() {
        val kid = SpokenCatalog.kidLines()
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.skin.SkinLines.fixed()))
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.PlaygroundLines.CALL_OUTS))
        assertTrue("Find a fence!" in kid && "You did it!" in kid)
        assertTrue("no names", kid.none { "Mia" in it || "{name}" in it })
    }
}
