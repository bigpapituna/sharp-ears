package com.bigpapituna.sharpears.ui

import com.bigpapituna.sharpears.engine.nav.TodayCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.14 depth, the parts that live in composables and Android code, read from the sources (the rules behind them are pure
 * and tested in PerformTimingTest, MidiOffsetTest, SightStaffTest, NextChallengeTest and LevelLadderTest).
 */
class DepthWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(f: String) = File(root, f).readText().replace("\r\n", "\n")

    @Test fun theRecapPlacesTheTimingPanelForEveryPerformRun() {
        val recap = src("ui/RunRecap.kt")
        assertTrue("a kid's strip", "TimingPanel(e.timing, r, kid = true" in recap)
        assertTrue("a grown-up's panel", "TimingPanel(e.timing, r, kid = false" in recap)
        val player = src("ui/PlayerScreen.kt")
        // The library song's, the generated song's (a lesson phrase, a progression) and the pop run's recaps all carry it.
        assertEquals(3, Regex("""\.timed\(transport, speed, midiRun\)""").findAll(player).count())
        val panel = src("ui/player/TimingPanel.kt")
        assertTrue("the rule is shown", "SongStars.ruleLines(recap)" in panel)
        assertTrue("no numbers for a kid", "if (!kid)" in panel && "face(b.verdict)" in panel)
    }

    @Test fun theMidiOffsetReachesPerformScoringAlone() {
        val player = src("ui/PlayerScreen.kt")
        assertTrue("transport.noteInput(midi, latencyMs - sinceTick + if (transport.mode == Mode.PERFORM) midiOffsetMs else 0.0)" in player)
        assertTrue("only MIDI notes carry it", "midiOffsetMs = com.bigpapituna.sharpears.engine.MidiOffset.latencyMs(midi.offsetMs)" in player)
        // The recording keeps the note as it came.
        assertTrue("recorder.on(midi, transport.positionMs - latencyMs * transport.speed, velocity)" in player)
        val devices = src("audio/MidiDevices.kt")
        assertTrue("kept for the phone", "context.getSharedPreferences(\"mic\", Context.MODE_PRIVATE)" in devices)
        val settings = src("ui/SettingsScreen.kt")
        assertTrue("under Microphone", settings.indexOf("Section(\"Microphone\")") < settings.indexOf("midiTiming?.let { Sub(\"MIDI keyboard\"); it() }"))
        assertTrue("midiTiming = { com.bigpapituna.sharpears.ui.MidiTimingSetting(midi" in src("MainActivity.kt"))
    }

    @Test fun theLiveTickIsDrawnUnderTheHitLine() {
        val player = src("ui/PlayerScreen.kt")
        assertTrue("lights.hit(midi, handColor(h), grade, tickMs = off)" in player)
        val lights = src("ui/HitLights.kt")
        assertTrue("drawTicks(keys, hitY, low, high, now)" in lights)
        assertFalse("never a word", Regex("""fun tick\([^)]*\)[^}]*word\(""").containsMatchIn(lights))
    }

    @Test fun sightReadOpensItsLevelsAndTheChallenge() {
        val main = src("MainActivity.kt")
        // 2.14 merge: a big kid opens the staff levels on the grown-ups' rule (SightLevels bigKid).
        assertTrue("open = com.bigpapituna.sharpears.engine.SightLevels.open(current.isKid, current.reader, current.level, layout, bigKid = current.bigKid)" in main)
        assertTrue("sightStaff = com.bigpapituna.sharpears.engine.SightLevels.staffOpen(" in main)
        assertTrue("is SessionPart.Challenge -> when (val c = part.item)" in main)
        assertTrue("val challenge = if (profile.isKid) null else nextChallenge(next)" in main)
        assertTrue("no easy song beside a challenge", "null -> if (plan.parts.any { it is SessionPart.Challenge }) null" in main)
        assertFalse("the old two-level clamp is gone", "coerceIn(1, 2)" in src("ui/SightReadScreen.kt"))
    }

    @Test fun todayNeverOffersAnEasySongBesideAChallenge() {
        // 2.14 merge: polish's name in the hello ("Hi there." with none), levels-ages' song by chords, depth's challenge first.
        assertEquals("Hi there. I'm your coach. There's a challenge at your level in today's plan.",
            greeting(TodayCard.State.PLAN, null, fresh = true, hour = 10, challenge = true))
        assertEquals("Hi, Sam. I'm your coach. There's a challenge at your level in today's plan.",
            greeting(TodayCard.State.PLAN, null, fresh = true, hour = 10, name = "Sam", easy = false, challenge = true))
        assertEquals("Hi there. I'm your coach. Let's start with an easy song.", greeting(TodayCard.State.PLAN, null, fresh = true, hour = 10))
        assertEquals("Hi there. I'm your coach. Let's start with a song by chords.", greeting(TodayCard.State.PLAN, null, fresh = true, hour = 10, easy = false))
    }

    @Test fun youAndTheRecapReadTheStoresLevel() {
        assertTrue("val level = store.level" in src("ui/profile/ProfileScreen.kt"))
        assertEquals(2, Regex("""xpBarFor\(it, p\.xp, p\.level\)""").findAll(src("ui/PlayerScreen.kt")).count())
    }
}
