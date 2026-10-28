package com.bigpapituna.sharpears.afterdark

import com.bigpapituna.sharpears.engine.family.AgeBand
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.lessons.StepPopRules
import com.bigpapituna.sharpears.extras.Moment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.16, the coach voice's rules on the JVM (CLAUDE.md "2.16 coach voice"): the wall (a grown-up only, by the profile's own
 * isKid and bigKid, never the skin; off by default; Encouragement Off and the Plain look silence it), the bank Roy recorded,
 * which bank each moment draws from, Encouragement's waits and caps, the sparing mistake, no repeats, and the mic rule.
 */
class CoachRulesTest {
    private val owner = Profile.owner()
    private val little = Profile.kid("Mia", 1, reader = false, id = "p1")
    private val big = Profile.kid("Leo", 2, reader = true, id = "p2", band = AgeBand.BIG)
    private val root = File("src/afterdark/assets/coach")
    private val voiceDirs = root.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }
    private val bank = CoachBank.parse(File(root, "male_us/lines.json").readText())

    @Test fun theVoicesAreTheFoldersTheBuildHas() {
        val voices = voiceDirs.map { CoachVoice.parse(File(it, "voice.json").readText()) }
        assertEquals(voiceDirs.map { it.name }, voices.map { it.id })
        assertTrue("the three Roy recorded first", listOf("female_us", "male_irish", "male_us").all { v -> voices.any { it.id == v } })
        assertEquals(listOf("male_us", "male_irish", "female_us"), CoachVoice.sorted(voices).map { it.id }.filter { it != "female_au" })
        assertEquals("Liam (American)", voices.first { it.id == "male_us" }.name)
        assertEquals(voices.size, voices.map { it.name }.toSet().size)
        // No file but the voice folders at the top (the first flat layout is gone).
        assertTrue(root.listFiles { f -> f.isFile }!!.isEmpty())
        // Data-driven: an unknown folder sorts last by name.
        assertEquals(listOf("a", "b", "z"), CoachVoice.sorted(listOf(CoachVoice("z", "Zed", 99), CoachVoice("b", "B", 1), CoachVoice("a", "A", 0))).map { it.id })
    }

    @Test fun everyVoiceHasEveryMomentForAWomanAndAMan() {
        var bytes = 0L
        for (dir in voiceDirs) {
            val b = CoachBank.parse(File(dir, "lines.json").readText())
            assertEquals(dir.name, 157, b.lines.size)
            for (m in CoachBank.MOMENTS) for (w in listOf("w", "m")) assertTrue("${dir.name} $m $w", b.of(m, w).isNotEmpty())
            assertEquals(setOf("any", "w", "m"), b.lines.map { it.forWho }.toSet())
            bytes += dir.listFiles { f -> f.name.endsWith(".ogg") }!!.sumOf { it.length() }
            assertEquals(157, dir.listFiles { f -> f.name.endsWith(".ogg") }!!.size)
        }
        // Small: Opus at 24 kb/s, under 2.2 MB a voice.
        assertTrue("$bytes bytes", bytes < 2_200_000L * voiceDirs.size)
    }

    @Test fun theBankHasEveryMomentWithCleanCaptionsAndItsSounds() {
        assertEquals(157, bank.lines.size)
        for (m in CoachBank.MOMENTS) assertTrue(m, bank.of(m).isNotEmpty())
        assertEquals(CoachBank.MOMENTS.toSet(), bank.lines.map { it.moment }.toSet())
        assertEquals("ids are unique", bank.lines.size, bank.lines.map { it.id }.toSet().size)
        val dir = File(root, "male_us")
        for (l in bank.lines) {
            assertFalse("${l.id} keeps a delivery tag: ${l.caption}", '[' in l.caption || ']' in l.caption)
            assertEquals(l.caption.trim(), l.caption)
            assertFalse(l.id, "  " in l.caption)
            assertTrue(l.id, l.caption.length in 8..90)
            assertTrue(l.id, l.ms in 800..8_000)
            assertTrue("${l.id}.ogg", File(dir, "${l.id}.ogg").isFile)
            assertTrue(l.id, l.id.startsWith(l.moment + "_"))
        }
        // Roy's examples are there, captions only (his tags gone), each to the player it is for.
        assertTrue(bank.lines.any { it.caption == "Good girl." && it.forWho == "w" })
        assertTrue(bank.lines.any { it.caption == "Good boy." && it.forWho == "m" })
        assertTrue(bank.lines.any { it.caption == "You're stroking those keys so well." && it.forWho == "any" })
        assertTrue(bank.lines.any { it.caption == "Three stars... that's my girl." && it.forWho == "w" })
    }

    @Test fun aWomanNeverHearsTheMansLinesNorAManTheWomans() {
        for (m in CoachBank.MOMENTS) {
            assertTrue(bank.of(m, "w").none { it.forWho == "m" || "good boy" in it.caption.lowercase() || "my boy" in it.caption.lowercase() })
            assertTrue(bank.of(m, "m").none { it.forWho == "w" || "good girl" in it.caption.lowercase() || "my girl" in it.caption.lowercase() })
            assertTrue("unset: only the lines for anyone", bank.of(m, null).all { it.forWho == "any" })
        }
        val w = CoachBrain(11)
        val m = CoachBrain(12)
        repeat(60) { i ->
            w.pick(Moment.STEP_PASSED, null, i * 1_000L, CheerFrequency.OFTEN, bank, "w")?.let { assertTrue(it.id, it.forWho != "m") }
            m.pick(Moment.STEP_PASSED, null, i * 1_000L, CheerFrequency.OFTEN, bank, "m")?.let { assertTrue(it.id, it.forWho != "w") }
        }
        assertEquals("w", CoachGate.forOf("w"))
        assertEquals("m", CoachGate.forOf("m"))
        assertNull(CoachGate.forOf(null))
        assertNull(CoachGate.forOf("x"))
    }

    @Test fun theVoiceSavedOrTheFirstOrOff() {
        val ids = listOf("male_us", "male_irish", "female_us")
        assertNull(CoachGate.voiceOf(null, ids))
        assertNull(CoachGate.voiceOf("off", ids))
        assertNull(CoachGate.voiceOf("", ids))
        assertEquals("male_irish", CoachGate.voiceOf("male_irish", ids))
        assertEquals("a voice not in this build: the first", "male_us", CoachGate.voiceOf("female_au", ids))
        assertNull("no voice in the build", CoachGate.voiceOf("male_us", emptyList()))
    }

    @Test fun onlyAGrownUpWhoTurnedItOnHearsIt() {
        assertTrue(CoachGate.offered(owner))
        assertTrue(CoachGate.on(owner, voice = "male_us", CheerFrequency.OFTEN, plain = false))
        assertTrue(CoachGate.on(owner, voice = "male_us", CheerFrequency.LESS, plain = false))
        // Off by default (never set) and when set off.
        assertFalse(CoachGate.on(owner, voice = null, CheerFrequency.OFTEN, plain = false))
        assertFalse(CoachGate.on(owner, voice = null, CheerFrequency.OFTEN, plain = false))
        // Encouragement Off and the Plain look silence it, the choice kept.
        assertFalse(CoachGate.on(owner, voice = "male_us", CheerFrequency.OFF, plain = false))
        assertFalse(CoachGate.on(owner, voice = "male_us", CheerFrequency.OFTEN, plain = true))
    }

    @Test fun kidsAndBigKidsNeverSeeOrHearItWhateverIsStored() {
        for (kid in listOf(little, big)) for (f in CheerFrequency.entries) for (plain in listOf(false, true)) for (stored in listOf(null, "off", "male_us", "female_us")) {
            assertFalse("${kid.name} $f $plain $stored", CoachGate.on(kid, CoachGate.voiceOf(stored, listOf("male_us", "female_us")), f, plain))
        }
        assertFalse(CoachGate.offered(little))
        assertFalse(CoachGate.offered(big))
        // The big kid reads the grown-up skin (kidSkin false: ui isKid() is false for him), and is still walled: the profile's own isKid.
        assertFalse(big.kidSkin)
        assertTrue(big.isKid && big.bigKid)
        val gate = File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachRules.kt").readText()
        assertTrue("fun offered(p: Profile): Boolean = p.kind == ProfileKind.ADULT && !p.isKid && !p.bigKid" in gate)
        assertFalse("never the skin", Regex("""\.kidSkin|isKid\(\)""").containsMatchIn(gate))
    }

    @Test fun aGrownUpMadeAKidIsAKidAtOnceAndKeepsTheValueUnused() {
        val nowKid = owner.withKind(ProfileKind.KID)
        assertFalse(CoachGate.on(nowKid, voice = "male_us", CheerFrequency.OFTEN, plain = false))
        assertFalse(CoachGate.offered(nowKid))
        val bigNow = nowKid.withBand(AgeBand.BIG)
        assertFalse(CoachGate.on(bigNow, voice = "male_us", CheerFrequency.OFTEN, plain = false))
        // A grown-up again: the value they set comes back.
        assertTrue(CoachGate.on(bigNow.withKind(ProfileKind.ADULT), voice = "male_us", CheerFrequency.OFTEN, plain = false))
    }

    @Test fun eachMomentDrawsFromItsBank() {
        assertEquals("run20", CoachMoments.bankOf(Moment.RUN, 20, numbered = false))
        assertEquals("run40", CoachMoments.bankOf(Moment.RUN, 40, numbered = false))
        assertEquals("run5", CoachMoments.bankOf(Moment.RUN, 5, numbered = true))
        assertEquals("run", CoachMoments.bankOf(Moment.RUN, 5, numbered = false))
        assertEquals("run10", CoachMoments.bankOf(Moment.RUN, 10, numbered = true))
        assertEquals("run", CoachMoments.bankOf(Moment.RUN, 7, numbered = true))
        assertEquals("run", CoachMoments.bankOf(Moment.RUN, null, numbered = true))
        for (m in listOf(5, 10, 15, 20, 30)) assertEquals("focus$m", CoachMoments.bankOf(Moment.FOCUS, m, numbered = false))
        assertNull(CoachMoments.bankOf(Moment.FOCUS, 25, numbered = false))
        assertNull(CoachMoments.bankOf(Moment.FOCUS, 35, numbered = false))
        for (m in Moment.entries - Moment.RUN - Moment.FOCUS) {
            val b = CoachMoments.bankOf(m, null, numbered = false)
            assertTrue("$m -> $b", b in CoachBank.MOMENTS && bank.of(b!!).isNotEmpty())
        }
        // Every bank Roy recorded is reachable from some moment.
        val reachable = Moment.entries.flatMap { m -> listOf(null, 5, 10, 15, 20, 30, 40).flatMap { c ->
            listOf(true, false).mapNotNull { CoachMoments.bankOf(m, c, it) } } }.toSet()
        assertEquals(CoachBank.MOMENTS.toSet(), reachable)
        assertEquals(Moment.STARS3, Moment.finish(3))
        assertEquals(Moment.STARS2, Moment.finish(2))
        assertEquals(Moment.FINISH, Moment.finish(1))
        assertEquals(Moment.FINISH, Moment.finish(0))
    }

    @Test fun playMomentsKeepEncouragementsWaitAndCap() {
        val b = CoachBrain(1)
        assertNotNull(b.pick(Moment.RUN, 5, 0, CheerFrequency.OFTEN, bank))
        assertNull("15 s between lines", b.pick(Moment.RECOVERY, null, 14_999, CheerFrequency.OFTEN, bank))
        assertNotNull(b.pick(Moment.RECOVERY, null, 15_000, CheerFrequency.OFTEN, bank))
        // Less doubles the wait.
        val less = CoachBrain(2)
        assertNotNull(less.pick(Moment.STEADY, null, 0, CheerFrequency.LESS, bank))
        assertNull(less.pick(Moment.STEADY, null, 29_999, CheerFrequency.LESS, bank))
        assertNotNull(less.pick(Moment.STEADY, null, 30_000, CheerFrequency.LESS, bank))
        // Events have no wait but count toward the minute's cap (four with Often, two with Less).
        val c = CoachBrain(3)
        for (t in 0L..3_000L step 1_000) assertNotNull(c.pick(Moment.START, null, t, CheerFrequency.OFTEN, bank))
        assertNull("four this minute", c.pick(Moment.HALFWAY, null, 20_000, CheerFrequency.OFTEN, bank))
        assertNotNull("a minute after the first", c.pick(Moment.HALFWAY, null, 61_000, CheerFrequency.OFTEN, bank))
        val d = CoachBrain(4)
        assertNotNull(d.pick(Moment.FINISH, null, 0, CheerFrequency.LESS, bank))
        assertNotNull(d.pick(Moment.COMEBACK, null, 1_000, CheerFrequency.LESS, bank))
        assertNull("two this minute with Less", d.pick(Moment.NUDGE, null, 40_000, CheerFrequency.LESS, bank))
    }

    @Test fun encouragementOffMeansNoLineAtAll() {
        val b = CoachBrain(5)
        for (m in Moment.entries) assertNull(m.name, b.pick(m, 5, 100_000L * m.ordinal, CheerFrequency.OFF, bank))
        assertTrue(b.played.isEmpty())
    }

    @Test fun aMistakeOnceAStepAndTheHardPartOnceAStep() {
        val b = CoachBrain(6)
        assertNotNull(b.pick(Moment.MISTAKE, null, 0, CheerFrequency.OFTEN, bank))
        assertNull("once a step", b.pick(Moment.MISTAKE, null, 60_000, CheerFrequency.OFTEN, bank))
        assertNotNull(b.pick(Moment.HARD, null, 120_000, CheerFrequency.OFTEN, bank))
        assertNull(b.pick(Moment.HARD, null, 180_000, CheerFrequency.OFTEN, bank))
        b.stepBegan()
        assertNotNull(b.pick(Moment.MISTAKE, null, 240_000, CheerFrequency.OFTEN, bank))
        assertNotNull(b.pick(Moment.HARD, null, 300_000, CheerFrequency.OFTEN, bank))
        // And the wait applies to it like any play moment.
        b.stepBegan()
        assertNotNull(b.pick(Moment.RUN, 20, 400_000, CheerFrequency.OFTEN, bank))
        assertNull(b.pick(Moment.MISTAKE, null, 405_000, CheerFrequency.OFTEN, bank))
    }

    @Test fun neverOneOfTheLastFewWhileTheBankHasAnother() {
        val b = CoachBrain(7)
        val got = (0 until 40).map { b.pick(Moment.START, null, it * 1_000L, CheerFrequency.OFTEN, bank, "w")!!.id }
        for (i in got.indices) for (j in maxOf(0, i - CoachBrain.RECENT) until i) assertNotEquals("$i and $j", got[i], got[j])
        assertEquals("all ten heard", 10, got.toSet().size)
        // A bank of two plays its lines in turn.
        val f = CoachBrain(8)
        val focus = (0 until 6).map { f.pick(Moment.FOCUS, 5, it * 100_000L, CheerFrequency.OFTEN, bank)!!.id }
        for (i in 1 until focus.size) assertNotEquals(focus[i - 1], focus[i])
        // Each pick is from the moment's own bank.
        val g = CoachBrain(9)
        assertTrue(g.pick(Moment.STARS3, null, 0, CheerFrequency.OFTEN, bank)!!.id.startsWith("stars3_"))
        assertTrue(g.pick(Moment.RUN, 40, 20_000, CheerFrequency.OFTEN, bank)!!.id.startsWith("run40_"))
        assertNull("no lines for 25 minutes", g.pick(Moment.FOCUS, 25, 100_000, CheerFrequency.OFTEN, bank))
    }

    @Test fun theSameSeedTheSameLines() {
        fun run(seed: Long) = CoachBrain(seed).let { b -> (0 until 12).map { b.pick(Moment.entries[it % Moment.entries.size], 10, it * 70_000L, CheerFrequency.OFTEN, bank)?.id } }
        assertEquals(run(42), run(42))
    }

    @Test fun theMicIsDeafForTheWholeLineThroughTheSpeaker() {
        assertEquals(3_000L + StepPopRules.ROOM_TAIL_MS, CoachSound.deafMs(3_000, headphones = false))
        assertEquals(0L, CoachSound.deafMs(3_000, headphones = true))
        assertTrue(CoachSound.deafens(headphones = false))
        assertFalse(CoachSound.deafens(headphones = true))
        // Each slice is renewed before it runs out, so the window never has a gap while the line plays.
        assertTrue(CoachSound.RENEW_MS < CoachSound.SLICE_MS)
        // The words stay up for the line, between the cheer pop's 1.6 s and 6.5 s.
        assertEquals(1_600L, CoachSound.showMs(900))
        assertEquals(4_000L, CoachSound.showMs(4_000))
        assertEquals(CoachSound.MAX_SHOW_MS, CoachSound.showMs(20_000))
        assertTrue(bank.lines.all { CoachSound.showMs(it.ms) >= minOf(it.ms, CoachSound.MAX_SHOW_MS) })
    }

    @Test fun theCourseIsNarratedInTheCoachsVoiceForAGrownUpOnly() {
        assertTrue(CoachGate.narrates(owner, "male_us", plain = false))
        assertFalse("Off", CoachGate.narrates(owner, null, plain = false))
        assertFalse("the Plain look", CoachGate.narrates(owner, "male_us", plain = true))
        for (kid in listOf(little, big)) assertFalse(kid.name, CoachGate.narrates(kid, "male_us", plain = false))
        val files = setOf("abc.ogg", "def_w.ogg", "def_m.ogg", "ghi_w.ogg")
        assertEquals("abc.ogg", CoachGate.narrationOf("abc", "w", files))
        assertEquals("def_w.ogg", CoachGate.narrationOf("def", "w", files))
        assertEquals("def_m.ogg", CoachGate.narrationOf("def", "m", files))
        assertNull("a line only for a woman is not said to a man", CoachGate.narrationOf("ghi", "m", files))
        assertNull("nor when unset", CoachGate.narrationOf("ghi", null, files))
        assertNull("not recorded: the normal voice", CoachGate.narrationOf("zzz", "w", files))
        // Each voice has its course folder (empty until Roy's recordings are converted).
        for (dir in voiceDirs) assertTrue(dir.name, File(dir, "course").isDirectory)
    }
}
