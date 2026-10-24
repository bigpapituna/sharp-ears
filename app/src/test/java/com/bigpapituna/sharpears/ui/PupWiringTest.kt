package com.bigpapituna.sharpears.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.10, the living pup: every pup on screen goes through ui/pup (the rig), and the moods reach the screens they belong on
 * (read from the sources, as QaWiringTest does).
 */
class PupWiringTest {
    private val root = File("src/main/java/com/bigpapituna/sharpears")
    private fun src(path: String) = File(root, path).readText()
    private val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    private fun outsidePup(f: File) = !f.invariantSeparatorsPath.contains("/ui/pup/")

    @Test fun noPupDrawableIsDrawnOutsideTheRig() {
        val named = sources.filter { outsidePup(it) && "R.drawable.pup_" in it.readText() }.map { it.name }
        assertEquals("pup drawables named outside ui/pup", emptyList<String>(), named)
        // No bare Image or bitmap of a pose either (Pose.X.res, pose.res, a list of poses) outside ui/pup.
        val bare = Regex("""(painterResource|imageResource)\([^)]*(\bPose\.|\bpose\b|\.pose\b|POSES)""")
        val images = sources.filter { outsidePup(it) && bare.containsMatchIn(it.readText()) }.map { it.name }
        assertEquals("bare pup images outside ui/pup", emptyList<String>(), images)
        // The old ways of drawing him are gone.
        for (f in sources) {
            val s = f.readText()
            for (old in listOf("fun PaintedPup(", "PaintedPup(", "enum class Painted(", "PupPicture(")) assertFalse("${f.name}: $old", old in s)
        }
        assertTrue("fun Pup(" in src("ui/pup/Pup.kt") && "fun rememberPupController(" in src("ui/pup/PupController.kt"))
        assertFalse("ui/Pup.kt keeps only the bubble and buttons", "fun Pup(" in src("ui/Pup.kt") || "enum class Pose" in src("ui/Pup.kt"))
    }

    @Test fun theRigReadsItsTableAndNeverRecomposesTheScreen() {
        val pup = src("ui/pup/Pup.kt")
        // Every number from the engine's table, the motion read in graphicsLayer and draw blocks from remembered Animatables.
        assertTrue("PupMotion.beat(" in pup && "PupMotion.cheer(" in pup && "PupMotion.breath(" in pup && "PupMotion.squint(" in pup && "PupMotion.flick(" in pup)
        assertTrue(".graphicsLayer {" in pup && "drawBehind {" in pup && "remember { Animatable(" in pup)
        assertTrue("PupMotion.clamp(" in pup)
        // Animations off: no idle motion and a beat is one fade of the glow.
        assertTrue("val alive = live && !still" in pup && "calm.animateTo(0f, tween(PupMotion.CALM_MS))" in pup)
        // A pose change is a cross-fade with a pop, never a hard swap.
        assertTrue("fade.animateTo(1f, tween(slowed(PupMotion.SWAP_MS)" in pup && "PupMotion.swapPop(" in pup)
        // He takes no touch unless asked to.
        assertTrue("if (onTap == null) Modifier else Modifier.pointerInput" in pup)
        // engine/pup has no Android in it.
        assertFalse(Regex("^import android", RegexOption.MULTILINE).containsMatchIn(src("engine/pup/PupMotion.kt")))
    }

    @Test fun theMoodsReachTheirScreens() {
        // The kid home: idle, and a wave of jumps while the surprise waits.
        val home = src("ui/kid/KidHome.kt")
        assertTrue("giftReady = gift != null" in home && "PupMood.HAPPY" in home)
        // Lessons: a tilt on a wrong key, a wag on a right one, pointing through a demo, a jump for a step passed.
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("onRightKey = { pupNow.play(PupMood.WAG) }" in lesson && "PupReaction.HEAD_TILT" in lesson)
        assertTrue("PupMood.POINT" in lesson && "reaction == Pose.GREAT_JOB -> PupMood.HAPPY" in lesson)
        // The stages: the same from what they hear (StageFx.hits and misses), pointing at the demo's key.
        val kit = src("ui/playground/PlaygroundKit.kt")
        assertTrue("pup.play(PupMood.WAG)" in kit && "pup.play(PupMood.TILT)" in kit && "Pose.POINTING -> PupMood.POINT" in kit && "-> PupMood.HAPPY" in kit)
        assertTrue(Regex("point = st\\.demoKey").findAll(sources.filter { "/ui/playground/" in it.invariantSeparatorsPath }.joinToString { it.readText() }).count() >= 8)
        // Celebrate and the recap: the jump, then the cheer.
        for (f in listOf("ui/Celebrate.kt", "ui/RunRecap.kt")) {
            val s = src(f)
            assertTrue(f, "pup.play(PupMood.HAPPY)" in s && "pup.mood = PupMood.CHEER" in s)
        }
        // Clap along and the finale cheer at the beat; the finale and the concerts' bow hold the clip slot.
        val clap = src("ui/play/ClapAlongScreen.kt")
        assertTrue("PupMood.CHEER" in clap && "bpm = speed.bpm" in clap && "beatAt = along.beatAt(0)" in clap)
        assertTrue("PupClip(PupClips.DANCE" in src("ui/kid/Finale.kt") && "then = PupMood.CHEER" in src("ui/kid/Finale.kt"))
        for (f in listOf("ui/playground/ConcertStage.kt", "ui/playground/TreeSkyConcertStage.kt")) assertTrue(f, "PupClip(PupClips.WAVE" in src(f))
        // Pup says leans in to listen on the kid's turn.
        assertTrue("Phase.LISTEN -> PupMood.LISTEN" in src("ui/play/PupSaysScreen.kt"))
        // The player's kid mode: a pup in the bar that wags and tilts.
        val player = src("ui/PlayerScreen.kt")
        assertTrue("if (kid) kidPup.play(com.bigpapituna.sharpears.engine.pup.PupMood.TILT)" in player)
        assertTrue("if (kid) kidPup.play(com.bigpapituna.sharpears.engine.pup.PupMood.WAG)" in player && "lead = if (kid && embedded == null)" in player)
        // Bedtime sleeps; the grown-ups' Today idles, subtly.
        assertTrue("Pup(Pose.GOODNIGHT, PupMood.SLEEP" in src("ui/skin/Goodbye.kt"))
        assertTrue("Pup(pose, PupMood.IDLE, size = 48.dp" in src("ui/TodayScreen.kt"))
    }

    /** The clips in assets/pup are looping animated WebPs, one per name the slot knows. */
    @Test fun theClipsAreAnimatedWebps() {
        val dir = File("src/main/assets/pup")
        for (name in listOf("jump", "dance", "wave", "hop")) {
            val f = File(dir, "$name.webp")
            if (!f.exists()) continue                                   // a missing clip is allowed: the slot falls back
            val b = f.readBytes()
            assertEquals("$name RIFF", "RIFF", String(b, 0, 4, Charsets.US_ASCII))
            assertEquals("$name WEBP", "WEBPVP8X", String(b, 8, 8, Charsets.US_ASCII))
            assertTrue("$name animated", (b[20].toInt() and 0x02) != 0)  // VP8X flags: the animation bit
            assertTrue("$name has frames", String(b, Charsets.ISO_8859_1).contains("ANMF"))
            assertTrue("$name is small", b.size < 600_000)
        }
        val clip = src("ui/pup/PupClip.kt")
        for (c in listOf("JUMP = \"jump\"", "DANCE = \"dance\"", "WAVE = \"wave\"", "HOP = \"hop\"")) assertTrue(c, c in clip)
    }

    @Test fun theClipSlotFallsBackToTheRigAndTheDebugRouteIsDebugOnly() {
        val clip = src("ui/pup/PupClip.kt")
        assertTrue("AnimatedImageDrawable" in clip && "\"pup\"" in clip && "pup.play(PupMood.HAPPY)" in clip)
        assertTrue("if (still) ClipState.Missing" in clip)
        val main = src("MainActivity.kt")
        assertTrue("route == \"pup\" -> return setContent { com.bigpapituna.sharpears.ui.pup.PupDebug(intent) { finish() } }" in main)
        assertTrue("val route = intent.getStringExtra(\"screen\")?.takeIf { debuggable }" in main)
        val claude = File("../CLAUDE.md").readText()
        assertTrue("## 2.10 living pup" in claude && "--es screen pup" in claude)
    }
}
