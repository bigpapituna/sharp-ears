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

    /** Also writes build/voice-lines.txt, the list scripts/render_voice.py records. */
    @Test fun catalogIsSpeakableAndWritten() {
        val lines = SpokenCatalog.all()
        assertTrue("lesson steps and prompts: ${lines.size}", lines.size > 300)
        lines.forEach { assertTrue("speakable: $it", it.isNotBlank() && it.none { c -> c in "—–·#&/" }) }
        assertEquals("keys are unique", lines.size, lines.map(SpokenCatalog::key).toSet().size)
        File("build/voice-lines.txt").writeText(lines.joinToString("\n") { "${SpokenCatalog.key(it)}\t$it" } + "\n")
    }
}
