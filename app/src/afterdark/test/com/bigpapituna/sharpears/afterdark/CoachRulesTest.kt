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
        // 2.16.1: the four Roy recorded (Rory re-recorded in a new Irish voice, Sienna added), in the script's order.
        assertEquals(listOf("male_us", "male_irish", "female_us", "female_au"), CoachVoice.sorted(voices).map { it.id })
        assertEquals("Liam (American)", voices.first { it.id == "male_us" }.name)
        assertEquals("Sienna (Australian)", voices.first { it.id == "female_au" }.name)
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
        for (i in got.indices) for (j in maxOf(0, i - com.bigpapituna.sharpears.engine.praise.PraiseBags.carry(10)) until i) assertNotEquals("$i and $j", got[i], got[j])
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
        // Each voice has its course folder (Liam's filled in 2.16.2; an empty one falls back to the normal voice).
        for (dir in voiceDirs) assertTrue(dir.name, File(dir, "course").isDirectory)
    }

    /**
     * 2.16.2: Liam narrates the course (scripts/coach_bank.py from tts-bakeoff/elevenlabs/course_male_us). Every file is a
     * grown-up line still in the spoken catalog (no orphans: re-run the script after copy changes, it drops them), named as
     * NaturalVoice's hook asks (<SpokenCatalog key>[_w|_m].ogg), a woman's and a man's variant in pairs and never beside a
     * line for anyone.
     */
    @Test fun theCourseFilesAreTheCatalogsGrownUpLines() {
        val titles = Regex("\"title\": \"([^\"]+)\",\\s*\"composer\"").findAll(File("src/main/assets/songs/index.json").readText())
            .map { it.groupValues[1] }.toList()
        val kid = com.bigpapituna.sharpears.engine.SpokenCatalog.kidLines().toSet()
        val grown = com.bigpapituna.sharpears.engine.SpokenCatalog.all(titles).filter { it !in kid }
            .map(com.bigpapituna.sharpears.engine.SpokenCatalog::key).toSet()
        for (dir in voiceDirs) {
            val stems = File(dir, "course").listFiles { f -> f.name.endsWith(".ogg") }!!.map { it.name.removeSuffix(".ogg") }
            val byKey = stems.groupBy { it.substringBefore('_') }
            for ((key, variants) in byKey) {
                assertTrue("${dir.name}: $key is a grown-up line in the catalog", key in grown)
                assertTrue("${dir.name}: $key has $variants", variants == listOf(key) || variants.sorted() == listOf("${key}_m", "${key}_w"))
            }
            // 2.18, bug 2: Liam narrates every grown-up line (a gap would switch to the pup's recording mid-lesson): re-run
            // coach_bank.py and record the missing ones before a copy change ships. Kid lines never have his narration.
            if (dir.name == "male_us") assertTrue("Liam narrates every grown-up line; missing: ${(grown - byKey.keys).take(10)} of ${(grown - byKey.keys).size}",
                (grown - byKey.keys).isEmpty())
        }
    }

    /**
     * 2.18, bug 1: at a lesson's finish Liam narrates "Clean." and "Lesson complete... good girl" (more than the old 8 s wait),
     * then the stars line plays: at most one coach line after it (one waits at a time), none once the page moves on (hush).
     */
    @Test fun theStarsLineWaitsOutLiamsNarrationAndPlaysAfterIt() {
        val host = File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachHost.kt").readText()
        assertTrue("ExtraTurn(startAt = now(), waitMs = CoachSound.waitMs(overlay = true, inTurn = false), outlastsTheVoice = true)" in host)
        assertEquals(CoachSound.WAIT_MS, CoachSound.waitMs(overlay = true, inTurn = false))
        assertTrue("overlay = false, ExtraTurn(startAt = now() + afterMs, waitMs = CoachSound.SHOWN_WAIT_MS, outlastsTheVoice = false)" in host)
        assertTrue("if (!on() || busy || held) return" in host)                       // one line waits at a time
        val turn = com.bigpapituna.sharpears.engine.voice.ExtraTurn(0, CoachSound.WAIT_MS, outlastsTheVoice = true)
        var t = 0L
        while (t < 12_500) { assertEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.WAIT, turn.next(t, voiceTalking = true, blocked = false)); t += 50 }
        assertEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.GO, turn.next(t, voiceTalking = false, blocked = false))
        // The lesson page and the song recap hush it as they go.
        assertTrue("onDispose { com.bigpapituna.sharpears.extras.Extras.hush() }" in File("src/main/java/com/bigpapituna/sharpears/ui/RunRecap.kt").readText())
    }

    /**
     * 2.18.3 (Roy: "some keys are not being played when it's asking me to play the keys"): a coach line in the middle of a try
     * made the mic deaf for its whole length (3 to 6 s; on the emulator a mistake line plus the octave hint dropped 7 injected
     * notes, a recovery line 3). Now no line starts while the mic listens through the phone's speaker: a bubble's line leaves
     * the bubble to the pup, an overlay line asked in the middle of a run is dropped after a moment, one asked as a try ends
     * (the mic stops within a frame or two) plays once the mic stops. Headphones or the mic off: as before.
     */
    @Test fun noCoachLineStartsWhileTheMicListensOnTheSpeaker() {
        assertFalse(CoachSound.mayStart(micListening = true, headphones = false))
        assertTrue(CoachSound.mayStart(micListening = true, headphones = true))
        assertTrue(CoachSound.mayStart(micListening = false, headphones = false))
        val wait = CoachSound.waitMs(overlay = true, inTurn = true)
        assertEquals(CoachSound.SHOWN_WAIT_MS, wait)
        val next = com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.values()
        // The middle of a run: the mic keeps listening, the line never starts and is dropped once its short wait is spent.
        val mid = com.bigpapituna.sharpears.engine.voice.ExtraTurn(0, wait, outlastsTheVoice = false)
        var t = 0L
        var got = next[0]
        while (t <= 3_000) {
            got = mid.next(t, voiceTalking = false, blocked = !CoachSound.mayStart(micListening = true, headphones = false))
            assertNotEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.GO, got)
            if (got == com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.DROP) break
            t += 50
        }
        assertEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.DROP, got)
        assertTrue(t <= wait + 50)
        // The end of a try: asked while the mic still listens, it plays as soon as the mic has stopped.
        val end = com.bigpapituna.sharpears.engine.voice.ExtraTurn(0, wait, outlastsTheVoice = false)
        assertEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.WAIT, end.next(0, voiceTalking = false, blocked = !CoachSound.mayStart(true, false)))
        assertEquals(com.bigpapituna.sharpears.engine.voice.ExtraTurn.Next.GO, end.next(100, voiceTalking = false, blocked = !CoachSound.mayStart(false, false)))
        // The host asks the rule for a bubble's line, an overlay line and each turn.
        val host = File("src/afterdark/java/com/bigpapituna/sharpears/afterdark/CoachHost.kt").readText()
        assertTrue("if (afterMs == 0L && !mayStartNow()) { holdReaction(m, count); return null }" in host)
        assertTrue("if (!mayStartNow()) return holdReaction(m, null)" in host)
        assertTrue("playing != null || (!p.atPause && !mayStartNow())" in host)
        assertTrue("CoachSound.mayStart(mic?.listening == true, Stage.headphones())" in host)
    }

    // 2.19.0: a reaction asked mid-try is held (not dropped) and said at the next pause, or in place of the step end's line.
    @Test fun aReactionMidTryIsHeldForThePauseAndExpires() {
        val hold = CoachHold<String>()
        assertEquals(CoachHold.Next.NONE, hold.next(0, 0).first)
        assertTrue(hold.offer("mistake", CoachHold.rank(Moment.MISTAKE), 1_000))
        // The player keeps playing: no pause yet, the mic is never made deaf mid-run.
        assertEquals(CoachHold.Next.WAIT, hold.next(1_800, lastKeyMs = 1_700).first)
        assertEquals(CoachHold.Next.WAIT, hold.next(3_000, lastKeyMs = 2_900).first)
        // A run line says less than the mistake: not held over it. A recovery says as much and is newer: it replaces it.
        assertFalse(hold.offer("run", CoachHold.rank(Moment.RUN), 3_100))
        assertTrue(hold.offer("recovery", CoachHold.rank(Moment.RECOVERY), 3_200))
        assertEquals("recovery", hold.item)
        // Quiet for 1.5 s after the last key: said now, once.
        assertEquals(CoachHold.Next.WAIT, hold.next(4_300, lastKeyMs = 3_000).first)
        assertEquals(CoachHold.Next.SPEAK to "recovery", hold.next(4_700, lastKeyMs = 3_000))
        assertEquals(CoachHold.Next.NONE, hold.next(4_800, 3_000).first)
        // Never a pause within 8 s: stale, dropped.
        hold.offer("nudge", CoachHold.rank(Moment.NUDGE), 10_000)
        var t = 10_000L
        var got = CoachHold.Next.WAIT
        while (got == CoachHold.Next.WAIT) { t += 100; got = hold.next(t, lastKeyMs = t - 50).first }
        assertEquals(CoachHold.Next.STALE, got)
        assertTrue(t - 10_000 in CoachHold.STALE_MS..CoachHold.STALE_MS + 100)
        // A stale one never blocks a new one, even of a lower rank.
        hold.offer("hard", CoachHold.rank(Moment.HARD), 20_000)
        assertTrue(hold.offer("run", CoachHold.rank(Moment.RUN), 29_000))
        // The step's end takes a fresh held reaction in place of its own line; a failed try's mistake takes one that says as much.
        hold.offer("run10", CoachHold.rank(Moment.RUN), 30_000)
        assertEquals("run10", hold.claim(CoachHold.rank(Moment.STEP_PASSED), 31_000))
        assertNull(hold.claim(CoachHold.rank(Moment.STEP_PASSED), 31_000))
        hold.offer("run10", CoachHold.rank(Moment.RUN), 32_000)
        assertNull("a run says less than the failed try's mistake line", hold.claim(CoachHold.rank(Moment.MISTAKE), 32_500))
        hold.offer("mistake", CoachHold.rank(Moment.MISTAKE), 40_000)
        assertNull("stale by the step's end", hold.claim(CoachHold.rank(Moment.STEP_PASSED), 48_500))
    }
}
