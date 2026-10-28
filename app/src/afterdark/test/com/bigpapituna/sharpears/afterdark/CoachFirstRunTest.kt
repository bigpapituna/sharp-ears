package com.bigpapituna.sharpears.afterdark

import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.17 (sharp-ears-audits/onboarding-audit-2026-10-28.md, M3 and M4): "Just use my name" (the lines for anyone, the course's
 * split lines in the normal voice), "Listening again" after a line, the hold in a profile's first lesson, and Liam a little
 * louder with the notes ducked under him. Kids stay walled whatever is stored.
 */
class CoachFirstRunTest {
    private val root = File("src/afterdark/assets/coach")
    private val voiceDirs = root.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }

    @Test fun justUseMyNameIsAnAnswerThatUsesTheLinesForAnyone() {
        assertEquals("name", CoachGate.NAME)
        assertNull(CoachGate.forOf(CoachGate.NAME))                 // no woman's or man's line
        assertTrue(CoachGate.answered(CoachGate.NAME))
        assertTrue(CoachGate.answered("w")); assertTrue(CoachGate.answered("m"))
        assertFalse(CoachGate.answered(null)); assertFalse(CoachGate.answered("x"))
        assertEquals(listOf("w", "m", "name", "unset", "unset"), listOf("w", "m", "name", null, "x").map(CoachGate::forProperty))
    }

    @Test fun everyVoiceHasALineForAnyoneAtEveryMoment() {
        for (dir in voiceDirs) {
            val bank = CoachBank.parse(File(dir, "lines.json").readText())
            for (m in CoachBank.MOMENTS) {
                val pool = bank.of(m, CoachGate.forOf(CoachGate.NAME))
                assertTrue("${dir.name} $m", pool.isNotEmpty())
                assertTrue("${dir.name} $m", pool.all { it.forWho == CoachBank.ANY })
            }
        }
    }

    @Test fun theCourseSplitLinesFallBackToTheNormalVoiceByName() {
        val files = setOf("k1_w.ogg", "k1_m.ogg", "k2.ogg")
        assertNull(CoachGate.narrationOf("k1", CoachGate.forOf(CoachGate.NAME), files))
        assertEquals("k2.ogg", CoachGate.narrationOf("k2", CoachGate.forOf(CoachGate.NAME), files))
        assertEquals("k1_w.ogg", CoachGate.narrationOf("k1", "w", files))
    }

    @Test fun kidsStayWalledWhateverTheAnswer() {
        assertFalse(CoachGate.offered(Profile.kid("Mia", 1, reader = false, id = "p1")))
        assertFalse(CoachGate.offered(Profile.kid("Leo", 2, reader = true, id = "p2", band = AgeBand.BIG)))
        assertTrue(CoachGate.offered(Profile.owner()))
    }

    @Test fun listeningAgainOnlyAfterALineThatMadeTheListeningMicDeaf() {
        // 2.18: the rule is the Stage's (StageSoundTest drives it through lines); the coach's lines ask for it.
        assertTrue(com.bigpapituna.sharpears.engine.stage.StageRule.listeningCue(deafened = true, listening = true))
        assertFalse(com.bigpapituna.sharpears.engine.stage.StageRule.listeningCue(deafened = false, listening = true))      // headphones: never deaf
        assertFalse(com.bigpapituna.sharpears.engine.stage.StageRule.listeningCue(deafened = true, listening = false))      // nothing was listening
    }

    @Test fun theHostHoldsLinesDucksTheNotesAndCuesTheMic() {
        val host = File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachHost.kt").readText()
        assertTrue("if (!on() || busy || held) return null" in host)
        assertTrue("if (!on() || busy || held) return" in host)
        // 2.18: the duck, the deaf window and "Listening again" are the Stage's, asked as a line starts and ends.
        assertTrue("Stage.lineStarts(p, lengthMs, duck = true, cue = true)" in host)
        assertTrue("Stage.lineEnds(p, StepPopRules.ROOM_TAIL_MS.toLong(), finished)" in host)
        // One clip player for the voice and the coach (audio/ClipPlayer.kt); a line cut short (chime, hush) ends as not finished.
        assertTrue("ClipPlayer(c)" in host && "clips?.stop()" in host)
        assertTrue("ListeningAgain(hidden = visible)" in File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachUi.kt").readText())
        val door = File("src/afterdark/java/com/bigpapituna/sharpears/extras/Extras.kt").readText()
        assertTrue("fun hold(on: Boolean) = CoachHost.hold(on)" in door)
        val lesson = File("src/main/java/com/bigpapituna/sharpears/ui/LessonScreen.kt").readText()
        assertTrue("Extras.hold(FirstRun.holdsExtra(firstLesson, passedSteps))" in lesson)
        assertTrue("!anyKid && com.bigpapituna.sharpears.engine.COURSE" in lesson)      // a kid's first lesson has no extra to hold
    }

    @Test fun liamIsLouderAndHisPeaksStayUnderTheCeiling() {
        val script = File("../scripts/coach_bank.py").readText()
        assertTrue("TARGETS = {\"male_us\": \"I=-20:TP=-2:LRA=11\"}" in script)
        assertTrue("CEILING = -1.5" in script)
    }
}
