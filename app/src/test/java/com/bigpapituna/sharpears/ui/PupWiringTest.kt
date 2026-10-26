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
        // Lessons: a tilt on a wrong key, a wag on a right one, pointing through a demo, a jump for a step passed (2.11: the
        // step pop's mini hop when another step follows, none with animations off).
        val lesson = src("ui/LessonScreen.kt")
        assertTrue("onRightKey = { pupNow.play(PupMood.WAG) }" in lesson && "PupReaction.HEAD_TILT" in lesson)
        assertTrue("PupMood.POINT" in lesson && "reaction == Pose.GREAT_JOB -> passBeat" in lesson)
        assertTrue("passBeat = if (pop == null) PupMood.HAPPY else if (still) PupMood.IDLE else StepPopRules.pupBeat(pop.variant)" in lesson)
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
        // 2.14: a lesson finished with a graded step skipped gets no celebrate clip (the rig's wag; ui/Celebrate.kt FinishStars).
        val celebrate = "clip = if (neutral) null else com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE"
        assertEquals("a kid's and a grown-up's lesson", 2, src("ui/LessonScreen.kt").split(celebrate).size - 1)
        assertEquals("a kid's and a grown-up's Done for today", 2, src("MainActivity.kt").split("clip = com.bigpapituna.sharpears.ui.pup.PupClips.CELEBRATE").size - 1)
        // Clap along and the finale cheer at the beat; the finale and the concerts' bow hold the clip slot.
        val clap = src("ui/play/ClapAlongScreen.kt")
        assertTrue("PupMood.CHEER" in clap && "bpm = speed.bpm" in clap && "beatAt = along.beatAt(0)" in clap)
        assertTrue("PupClip(PupClips.DANCE" in src("ui/kid/Finale.kt") && "then = PupMood.CHEER" in src("ui/kid/Finale.kt"))
        // 2.12: the concerts' bow is the bow clip once when assets/pup/bow.webp ships, else the dance as before.
        for (f in listOf("ui/playground/ConcertStage.kt", "ui/playground/TreeSkyConcertStage.kt")) assertTrue(f, "PupBow(size = " in src(f) && "PupClips.DANCE" !in src(f))
        assertTrue("PupClip(if (bow) PupClips.BOW else PupClips.DANCE, modifier, size, fallback, then)" in src("ui/pup/PupClip.kt"))
        assertTrue("remember { PupClips.has(context, PupClips.BOW) }" in src("ui/pup/PupClip.kt"))
        // The jump clip on three stars (the arcade, a kid's song), a sticker revealed or placed on the map, the surprise opened.
        assertTrue("clips = stars == 3" in src("ui/arcade/ArcadeFinish.kt") && "clips = kid" in src("ui/RunRecap.kt"))
        assertTrue("ReplayPup(Pose.STICKER, replay, pup, 128.dp, clips = true, idle = true)" in src("ui/world/DropReveal.kt"))
        assertTrue("Pose.GREAT_JOB, replay, giftPup, 120.dp, mood = PupMood.HAPPY, clips = true" in src("ui/Retention.kt"))
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
     * The clips in assets/pup (2.12: Gemini jump, wave and sleep, the Flow celebrate, the Veo dance; nothing else, and the bow
     * only once it ships): animated WebPs on one 318 x 400 canvas, loop count 0 in the file, at 12 fps, each under 600 KB;
     * only the sleep scaled up (1.15) to match the others' heads; jump, celebrate and bow played once.
     */
    @Test fun theClipsAreAnimatedWebps() {
        val dir = File("src/main/assets/pup")
        val clip = src("ui/pup/PupClip.kt")
        val required = listOf("jump", "dance", "wave", "sleep", "celebrate")
        val present = dir.list()!!.toSet()
        assertTrue("required clips $present", required.all { "$it.webp" in present })
        assertTrue("only the slot's clips in assets/pup: $present", present.all { it.removeSuffix(".webp") in required + "bow" && it.endsWith(".webp") })
        for (name in present.map { it.removeSuffix(".webp") }) {
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
        for (c in listOf("JUMP = \"jump\"", "DANCE = \"dance\"", "WAVE = \"wave\"", "SLEEP = \"sleep\"", "CELEBRATE = \"celebrate\"", "BOW = \"bow\"")) assertTrue(c, c in clip)
        assertTrue(dir.listFiles()!!.sumOf { it.length() } < 2_400_000)
        // Heads that match (2.12): the jump, wave and celebrate are full size (1); the lying sleep, width-limited, scales 1.15;
        // the canvas maps to the box as the stills do. The jump (its wrap pops) and the celebrate (its ends differ) never loop.
        assertTrue("JUMP to Spec(oneShot = true)," in clip && "DANCE to Spec()," in clip && "WAVE to Spec()," in clip)
        assertTrue("SLEEP to Spec(scale = 1.15f)," in clip && "CELEBRATE to Spec(oneShot = true)," in clip)
        assertTrue("BOW to Spec(oneShot = true, optional = true)," in clip)
        assertTrue("once: Boolean = PupClips.oneShot(name)" in clip && "_SCALE = 1.15f" !in clip)
        assertTrue("const val FEET = 380f" in clip && "const val STAND_H = 306f" in clip)
        // Celebrate plays once and stays on its last frame (its loop's wrap jumps from a laugh to a calm sit).
        assertTrue("if (hold) { kept = true; return true }" in clip && "d.repeatCount = if (once) 0 else AnimatedImageDrawable.REPEAT_INFINITE" in clip)
        // A clip never takes a touch.
        assertTrue("isClickable = false" in clip)
        // The goodnight screen curls him up with the sleep clip, scaled to the others.
        assertTrue("PupClip(PupClips.SLEEP" in src("ui/skin/Goodbye.kt") && "scale = PupClips.SLEEP_SCALE" in src("ui/skin/Goodbye.kt"))
    }

    /**
     * 2.12: every clip PupClips names (each `const val X = "name"` but the folder) is in assets/pup or marked optional in its
     * table, and every name has a row there; nothing optional is required by a screen without a fallback (the bow is the
     * dance until it ships).
     */
    @Test fun everyNamedClipShipsOrIsMarkedOptional() {
        val clip = src("ui/pup/PupClip.kt")
        val body = clip.replace("\r\n", "\n").substringAfter("object PupClips {").substringBefore("\n}\n")
        val named = Regex("""const val (\w+) = "(\w+)"""").findAll(body).map { it.groupValues[1] to it.groupValues[2] }
            .filter { it.first != "DIR" }.toList()
        assertTrue("clip names found: $named", named.size >= 6)
        val dir = File("src/main/assets/pup")
        val optional = Regex("""(\w+) to Spec\([^)]*optional = true""").findAll(body).map { it.groupValues[1] }.toSet()
        assertEquals(setOf("BOW"), optional)
        for ((id, name) in named) {
            assertTrue("$id has a row in SPECS", Regex("""\b$id to Spec\(""").containsMatchIn(body))
            assertTrue("$name.webp is in assets/pup or $id is optional", File(dir, "$name.webp").exists() || id in optional)
        }
        // And the code the app runs agrees with the source.
        val specs = com.bigpapituna.sharpears.ui.pup.PupClips.SPECS
        assertEquals(named.map { it.second }.toSet(), specs.keys)
        for ((name, spec) in specs) assertTrue("$name ships or is optional", File(dir, "$name.webp").exists() || spec.optional)
        assertEquals(1f, com.bigpapituna.sharpears.ui.pup.PupClips.scale("jump"))
        assertEquals(1.15f, com.bigpapituna.sharpears.ui.pup.PupClips.scale("sleep"))
        assertTrue(listOf("jump", "celebrate", "bow").all { com.bigpapituna.sharpears.ui.pup.PupClips.oneShot(it) })
        assertFalse(listOf("dance", "wave", "sleep").any { com.bigpapituna.sharpears.ui.pup.PupClips.oneShot(it) })
    }

    /** 2.12: the jump never loops (its wrap pops): the rig plays it once, the debug screen too, and no PupClip loops it. */
    @Test fun theJumpIsNeverLooped() {
        assertTrue("playClip(context, PupClips.JUMP, 1, clipNow, clipFade" in src("ui/pup/Pup.kt"))
        assertTrue("PupClip(c, size = size.dp, fallback = pose)" in src("ui/pup/PupDebug.kt"))
        for (f in sources) {
            val s = f.readText()
            assertFalse(f.name, Regex("""PupClip\(PupClips\.JUMP[^)]*once = false""").containsMatchIn(s))
            assertFalse(f.name, Regex("""playClip\(PupClips\.JUMP, (loops = )?[2-9]""").containsMatchIn(s))
        }
    }

    @Test fun theClipSlotFallsBackToTheRigAndTheDebugRouteIsDebugOnly() {
        val clip = src("ui/pup/PupClip.kt")
        assertTrue("AnimatedImageDrawable" in clip && "\"pup\"" in clip && "pup.play(PupMood.HAPPY)" in clip)
        assertTrue("if (still) ClipState.Missing" in clip)
        val main = src("MainActivity.kt")
        assertTrue("route == \"pup\" -> return setContent { com.bigpapituna.sharpears.ui.pup.PupDebug(intent) { finish() } }" in main)
        assertTrue("val route = intent.getStringExtra(\"screen\")?.takeIf { routes }" in main)
        val claude = File("../CLAUDE.md").readText()
        assertTrue("## 2.10 living pup" in claude && "--es screen pup" in claude)
    }
}
