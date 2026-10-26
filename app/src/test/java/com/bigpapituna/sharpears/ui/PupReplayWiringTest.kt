package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.14, tap the pup to play it again (Roy), read from the sources as the other wiring tests do: every finish screen where a
 * clip or a beat plays gives his tap a replay, with a sparkle, his voice line the first time only, never blocking the Next
 * button, and as a 150 ms fade under reduced motion.
 */
class PupReplayWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText()

    /** The finish screens: the file, and the part of it that is the screen (so another pup in the file, a cheer bar's, is not one). */
    private val finishScreens = listOf(
        Triple("ui/Celebrate.kt", "fun Celebration(", "/** While more of a scrolling middle"),
        Triple("ui/RunRecap.kt", "fun RunRecap(", "private fun Modifier.slideIn"),
        Triple("ui/arcade/ArcadeFinish.kt", "internal fun ArcadeFinish(", "/** A big pill button"),
        Triple("ui/world/DropReveal.kt", "fun DropRevealOverlay(", "fun RewardExtras("),
        Triple("ui/world/WardrobeReveal.kt", "fun WardrobeReveal(", "\u0000"),
        Triple("ui/Retention.kt", "fun GiftOpening(", "fun KidComebackCard("),
    )

    private fun part(file: String, from: String, to: String): String {
        val s = src(file)
        val a = s.indexOf(from); assertTrue("$file: $from", a >= 0)
        val b = if (to == "\u0000") s.length else s.indexOf(to, a).also { assertTrue("$file: $to", it > a) }
        return s.substring(a, b)
    }

    @Test fun everyFinishScreensPupHasItsTapWiredToAReplay() {
        for ((file, from, to) in finishScreens) {
            val body = part(file, from, to)
            assertTrue("$file draws his replay", "ReplayPup(" in body && "rememberPupReplay(" in body)
            // No other pup in the screen: a bare Pup( would have a tap that does nothing (or none).
            val bare = Regex("""(?<![A-Za-z])Pup\(""").findAll(body).count()
            assertEquals("$file: a pup without a replay", 0, bare)
        }
        // The kid finale: his dance clip, played again from its first frame by a tap.
        val finale = src("ui/kid/Finale.kt")
        assertTrue("PupClip(PupClips.DANCE" in finale && "replay = replays, onTap = {" in finale && "TapSparkle(replays" in finale)
        assertTrue("replay: Int = 0, onTap: (() -> Unit)? = null" in src("ui/pup/PupClip.kt") && "LaunchedEffect(replay)" in src("ui/pup/PupClip.kt"))
    }

    @Test fun theTapReplaysTheClipOrTheBeatSparklesAndSpeaksOnce() {
        val replay = src("ui/pup/PupReplay.kt")
        // The clip again, or the rig's beat when there is none; the cheer after a party.
        assertTrue("ctl.playClip(clip, fallback = PupMood.HAPPY, hold = hold, then = then)" in replay)
        assertTrue("ctl.play(PupMood.HAPPY)" in replay && "ctl.mood = m" in replay)
        // Unlimited: no counter stops it. The line is said once.
        assertFalse("no cap on replays", Regex("""taps\s*(>=|>|<|<=)""").containsMatchIn(replay))
        assertTrue("PupTap.speaks(spoke, voice)" in replay && "spoke = true" in replay)
        // The sparkle only draws (no touch), and under reduced motion it is the 150 ms fade, in place.
        val sparkle = replay.substringAfter("internal fun TapSparkle(")
        assertFalse("pointerInput" in sparkle || "clickable" in sparkle)
        assertTrue("PupMotion.CALM_MS" in sparkle && "if (still)" in sparkle)
        // The clip cue restarts a clip that plays or holds its last frame (no second decode, the held drawable rewound).
        val rig = src("ui/pup/Pup.kt")
        assertTrue("job?.cancelAndJoin()" in rig && "shown?.takeIf { it.name == cue.name }?.drawable" in rig)
        assertTrue("d.stop()" in src("ui/pup/PupClip.kt"))
        // The pup is the only thing the tap takes: the Pup box has the pointer input, the buttons are other composables.
        assertTrue("onTap = replay.onTap" in replay)
    }

    @Test fun theVoiceIsProvidedAtTheRootAndReplaysNeverWaitOnIt() {
        assertTrue("LocalPupVoice provides pupVoice" in src("MainActivity.kt"))
        assertTrue("val pupVoice = remember(voice)" in src("MainActivity.kt"))
        // A kid's line is one of the recorded cheers, so the voice has it; a grown-up's finish says nothing.
        assertTrue("PupTap.line(com.bigpapituna.sharpears.ui.family.profile().kidSkin)" in src("ui/Celebrate.kt"))
    }
}
