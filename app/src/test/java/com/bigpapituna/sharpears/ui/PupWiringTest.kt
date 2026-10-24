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
        // The kid home: idle; the wave clip on the day's hello and while the surprise waits (decoded ahead).
        val home = src("ui/kid/KidHome.kt")
        assertTrue("giftReady = gift != null" in home && "pup.prepare(PupClips.WAVE)" in home)
        assertTrue("pupNow.playClip(PupClips.WAVE, fallback = PupMood.WAG)" in home && "pupNow.playClip(PupClips.WAVE, fallback = PupMood.HAPPY)" in home)
        // Lessons: a tilt on a wrong key, a wag on a right one, pointing through a demo, a jump for a step passed.
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("onRightKey = { pupNow.play(PupMood.WAG) }" in lesson && "PupReaction.HEAD_TILT" in lesson)
        assertTrue("PupMood.POINT" in lesson && "reaction == Pose.GREAT_JOB -> PupMood.HAPPY" in lesson)
        // The stages: the same from what they hear (StageFx.hits and misses), pointing at the demo's key.
        val kit = src("ui/playground/PlaygroundKit.kt")
        assertTrue("pup.play(PupMood.WAG)" in kit && "pup.play(PupMood.TILT)" in kit && "Pose.POINTING -> PupMood.POINT" in kit && "-> PupMood.HAPPY" in kit)
        assertTrue(Regex("point = st\\.demoKey").findAll(sources.filter { "/ui/playground/" in it.invariantSeparatorsPath }.joinToString { it.readText() }).count() >= 8)
        // Celebrate and the recap: the jump, then the cheer; Celebrate after a lesson and on "Done for today" plays the
        // celebrate clip once, holding its last frame (the rig's jump and cheer without it).
        for (f in listOf("ui/Celebrate.kt", "ui/RunRecap.kt")) {
            val s = src(f)
            assertTrue(f, "pup.play(PupMood.HAPPY)" in s && "pup.mood = PupMood.CHEER" in s)
        }
        assertTrue("pup.playClip(clip, fallback = PupMood.HAPPY, hold = true, then = PupMood.CHEER)" in src("ui/Celebrate.kt"))
        val celebrate = "clip = com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE"
        assertEquals("a kid's and a grown-up's lesson", 2, src("ui/LessonScreen.kt").split(celebrate).size - 1)
        assertEquals("a kid's and a grown-up's Done for today", 2, src("MainActivity.kt").split(celebrate).size - 1)
        // Clap along and the finale cheer at the beat; the finale and the concerts' bow hold the clip slot.
        val clap = src("ui/play/ClapAlongScreen.kt")
        assertTrue("PupMood.CHEER" in clap && "bpm = speed.bpm" in clap && "beatAt = along.beatAt(0)" in clap)
        assertTrue("PupClip(PupClips.DANCE" in src("ui/kid/Finale.kt") && "then = PupMood.CHEER" in src("ui/kid/Finale.kt"))
        for (f in listOf("ui/playground/ConcertStage.kt", "ui/playground/TreeSkyConcertStage.kt")) assertTrue(f, "PupClip(PupClips.DANCE" in src(f))
        // The jump clip on three stars (the arcade, a kid's song), a sticker revealed or placed on the map, the surprise opened.
        assertTrue("clips = stars == 3" in src("ui/arcade/ArcadeFinish.kt") && "clips = kid" in src("ui/RunRecap.kt"))
        assertTrue("Pup(Pose.STICKER, controller = pup, size = 128.dp, clips = true)" in src("ui/world/DropReveal.kt"))
        assertTrue("PupMood.HAPPY, size = 120.dp, idle = false, clips = true" in src("ui/Retention.kt"))
        assertTrue("PupReaction.DANCE" in home && "bounce = BounceSize.SMALL, clips = true" in home)
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

    /** The nine layered poses: every layer's picture is in the app, every rig has a body and a head, the limits hold. */
    @Test fun theLayeredPosesAreWhole() {
        val kt = src("ui/pup/PupLayers.kt")
        val rigs = Regex("""Pose\.(\w+) to PupRig\((\d+), (\d+), headMax = ([\d.]+)f, earMax = ([\d.]+)f, tailMax = ([\d.]+)f""").findAll(kt).toList()
        assertEquals(9, rigs.size)
        for (r in rigs) for (i in 4..6) assertTrue(r.value, r.groupValues[i].toFloat() <= com.bigpapituna.sharpears.engine.pup.PupMotion.PART_MAX_DEG)
        val res = File("src/main/res/drawable-nodpi")
        val files = Regex("""R\.drawable\.(pup_\w+)""").findAll(kt).map { it.groupValues[1] }.toList()
        for (f in files) assertTrue(f, File(res, "$f.webp").exists())
        for (r in rigs) {
            val pose = r.groupValues[1].lowercase()
            for (part in listOf("body", "head", "left_ear", "right_ear", "tail")) assertTrue("$pose $part", "pup_${pose}_$part" in files)
        }
        // Eyes only where they are open, and those poses blink with them.
        val eyes = files.filter { it.endsWith("_eyes") }.map { it.removePrefix("pup_").removeSuffix("_eyes") }.toSet()
        assertEquals(setOf("standing", "welcome", "pointing", "head_tilt", "wear_base"), eyes)
        val art = src("ui/pup/PupArt.kt")
        for (e in listOf("DANCING", "LISTENING", "GREAT_JOB", "SLEEPING")) assertTrue(e, Regex("$e\\(R\\.drawable\\.pup_\\w+, eyesOpen = false\\)").containsMatchIn(art))
        // Small: the parts add under 600 KB.
        assertTrue(files.sumOf { File(res, "$it.webp").length() } < 600_000)
        val pup = src("ui/pup/Pup.kt")
        assertTrue("drawRig(" in pup && "PupMotion.eyesBlink(" in pup && "PupMotion.tailWag(" in pup && "tiltHead.value" in pup && "headLean" in pup)
    }

    /** Little-endian unsigned integer of [n] bytes at [at]. */
    private fun le(b: ByteArray, at: Int, n: Int) = (0 until n).sumOf { (b[at + it].toInt() and 0xff) shl (8 * it) }

    /**
     * The five Veo clips in assets/pup (and nothing else: no hop, no bow): animated WebPs on one 318 x 400 canvas, looping, at
     * 12 fps, each under 600 KB; jump and sleep scaled up 1.15 to match the others' heads; celebrate played once and held.
     */
    @Test fun theClipsAreAnimatedWebps() {
        val dir = File("src/main/assets/pup")
        val clip = src("ui/pup/PupClip.kt")
        val names = listOf("jump", "dance", "wave", "sleep", "celebrate")
        assertEquals(names.map { "$it.webp" }.toSet(), dir.list()!!.toSet())
        for (name in names) {
            val b = File(dir, "$name.webp").readBytes()
            assertEquals("$name RIFF", "RIFF", String(b, 0, 4, Charsets.US_ASCII))
            assertEquals("$name WEBP", "WEBPVP8X", String(b, 8, 8, Charsets.US_ASCII))
            assertTrue("$name animated", (b[20].toInt() and 0x02) != 0)  // VP8X flags: the animation bit
            assertTrue("$name alpha", (b[20].toInt() and 0x10) != 0)     // and the alpha bit
            assertEquals("$name width", 318, le(b, 24, 3) + 1)
            assertEquals("$name height", 400, le(b, 27, 3) + 1)
            assertTrue("$name is small", b.size < 600_000)
            assertTrue("$name is one of the slot's", "= \"$name\"" in clip)
            // Chunks after VP8X: ANIM (loop count 0, forever) and one ANMF per frame, each 83 or 84 ms.
            var at = 30
            var loops = -1
            val ms = mutableListOf<Int>()
            while (at + 8 <= b.size) {
                val id = String(b, at, 4, Charsets.US_ASCII)
                val len = le(b, at + 4, 4)
                if (id == "ANIM") loops = le(b, at + 12, 2)
                if (id == "ANMF") ms += le(b, at + 8 + 12, 3)
                at += 8 + len + (len and 1)
            }
            assertEquals("$name loops forever in the file", 0, loops)
            assertTrue("$name frames ${ms.size}", ms.size in 20..60)
            assertTrue("$name 12 fps $ms", ms.all { it in 80..90 })
        }
        for (c in listOf("JUMP = \"jump\"", "DANCE = \"dance\"", "WAVE = \"wave\"", "SLEEP = \"sleep\"", "CELEBRATE = \"celebrate\"")) assertTrue(c, c in clip)
        assertTrue(dir.listFiles()!!.sumOf { it.length() } < 2_400_000)
        // Heads that match: the jump and the sleep were drawn about 15% small; the canvas maps to the box as the stills do.
        assertTrue("JUMP_SCALE = 1.15f" in clip && "SLEEP_SCALE = 1.15f" in clip && "JUMP -> JUMP_SCALE; SLEEP -> SLEEP_SCALE" in clip)
        assertTrue("const val FEET = 380f" in clip && "const val STAND_H = 306f" in clip)
        // Celebrate plays once and stays on its last frame (its loop's wrap jumps from a laugh to a calm sit).
        assertTrue("if (hold) { kept = true; return true }" in clip && "d.repeatCount = if (once) 0 else AnimatedImageDrawable.REPEAT_INFINITE" in clip)
        // A clip never takes a touch.
        assertTrue("isClickable = false" in clip)
        // The goodnight screen curls him up with the sleep clip, scaled to the others.
        assertTrue("PupClip(PupClips.SLEEP" in src("ui/skin/Goodbye.kt") && "scale = PupClips.SLEEP_SCALE" in src("ui/skin/Goodbye.kt"))
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
