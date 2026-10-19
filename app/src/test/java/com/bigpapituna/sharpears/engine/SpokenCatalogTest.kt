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
    /**
     * 2.2.1 QA: the voice reads words, not symbols. Theory quiz lines went to the voice as "V7 to I" and "the vii chord"
     * (the scenes say "five to one"); the scale-degree game said "Bb is not in Bb major" as written; the chord game's
     * seventh level said "maj7".
     */
    @Test fun theVoiceReadsNotesAndNumeralsAsWords() {
        val symbols = Regex("[♯♭]|\\b[A-G][b#]\\b|maj7|\\b(II|III|IV|VI|VII|ii|iii|iv|vi|vii|V7|V)\\b")
        // A reading step is said scene by scene (each scene's own words); its whole written text is listed but never said.
        val written = COURSE.flatMap { it.lessons }.flatMap { it.steps }.filter { it.scenes.isNotEmpty() }.map { SpokenCatalog.normalize(it.text.replace("\n\n", " ")) }.toSet()
        for (l in SpokenCatalog.all() - written) assertTrue("read as words: \"$l\"", !symbols.containsMatchIn(l))
        // Lines made while playing: a key outside the scale-degree game's key, a wrong shape in the seventh game.
        var outside = 0
        for (seed in 1L..40L) for (midi in 55..79) {
            val g = com.bigpapituna.sharpears.engine.ear.ScaleDegreeGame(2, seed = seed)
            val line = g.input(midi).line ?: continue
            if ("is not in" !in line.text) continue
            outside++
            assertTrue(line.spoken, !symbols.containsMatchIn(line.spoken) && "#" !in line.spoken)
        }
        assertTrue(outside > 20)
        val sevens = ChordGame(GameKind.MAJOR_MINOR, 1, seed = 9)
        val wrong = listOf(60, 63, 66, 69).map { sevens.input(it, 0) }.last().line!!
        assertEquals("Those notes are not a seven or major seven chord. Try again.", wrong.spoken)
    }

    @Test fun kidLinesAreListed() {
        val kid = SpokenCatalog.kidLines()
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.skin.SkinLines.fixed()))
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.PlaygroundLines.CALL_OUTS))
        assertTrue("Find a fence!" in kid && "You did it!" in kid)
        // 2.2: the pond's and the concert hall's steps, scenes and call-outs, at kid speed too.
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.PageLines.ALL))
        assertTrue("Your turn: row, row, row your boat." in kid && "Find every D. D is the orange dog, inside the dog house." in kid)
        assertTrue(com.bigpapituna.sharpears.engine.playground.HALL.lessons.flatMap { it.steps }.flatMap { s -> s.scenes.map { it.say } }.all { it in kid })
        // 2.3: the workshop's and the fair's steps, scenes and stage lines.
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.WorkshopLines.ALL))
        assertTrue(kid.containsAll(com.bigpapituna.sharpears.engine.playground.FairLines.ALL))
        for (level in listOf(com.bigpapituna.sharpears.engine.playground.WORKSHOP, com.bigpapituna.sharpears.engine.playground.FAIR))
            assertTrue(level.id, level.lessons.flatMap { it.steps }.all { s -> SpokenCatalog.normalize(s.text) in kid && s.scenes.all { SpokenCatalog.normalize(it.say) in kid } })
        assertTrue("Finger three!" in kid && "Pop the red balloon!" in kid && "Next stop, the fair!" in kid)
        assertTrue("no names", kid.none { "Mia" in it || "{name}" in it })
    }
}
