package com.bigpapituna.sharpears.extras

import com.bigpapituna.sharpears.engine.nav.Welcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.16, the build-time wall around the coach voice (CLAUDE.md "2.16 coach voice"), from the sources, in both builds: its code,
 * captions and sounds live only in src/afterdark; src/main knows only the door (extras/Extras.kt, one in each of src/afterdark
 * and src/noafterdark with the same calls) and the moments' names; the build takes it only with AFTERDARK=true, and the
 * What's new of a build without it has nothing about it. The packaged APK is checked by checkNoAfterdark<Type> in
 * app/build.gradle.kts.
 */
class AfterdarkWallTest {
    private val main = File("src/main")
    private val afterdark = File("src/afterdark")
    private val none = File("src/noafterdark")

    @Test fun nothingOfItIsInMain() {
        val texts = main.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "json", "txt", "md") }.toList()
        for (f in texts) {
            val t = f.readText()
            assertFalse("${f.path} names the afterdark package", "sharpears.afterdark" in t)
        }
        assertFalse(File(main, "assets/coach").exists())
        assertTrue("no Ogg in main but the voice lines and the piano samples", File(main, "assets").walkTopDown()
            .filter { it.extension == "ogg" }.all { it.parentFile.name == "voice" || it.parentFile.name == "samples" })
        // No caption of the bank anywhere in main (the bank sits in src/afterdark on every checkout, built or not).
        val banks = File(afterdark, "assets/coach").listFiles { f -> f.isDirectory }!!.map { File(it, "lines.json") }
        assertTrue(banks.size >= 3 && banks.all { it.isFile })
        val captions = banks.flatMap { b -> Regex(""""caption": "([^"]+)"""").findAll(b.readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertTrue("${captions.size} captions", captions.size >= 150)
        val code = texts.filter { it.extension != "md" }.map { it.readText() }
        for (c in captions) assertTrue("\"$c\" is in main", code.none { c in it })
    }

    @Test fun theTwoDoorsHaveTheSameCalls() {
        fun calls(f: File) = Regex("""^\s+(?:@Composable )?(?:fun|val|const val) (\w+)""", RegexOption.MULTILINE).findAll(f.readText()).map { it.groupValues[1] }.toSet()
        val withIt = calls(File(afterdark, "java/com/bigpapituna/sharpears/extras/Extras.kt"))
        val without = calls(File(none, "java/com/bigpapituna/sharpears/extras/Extras.kt"))
        assertEquals(without, withIt)
        assertTrue(setOf("BUILT", "WHATS_NEW", "open", "close", "line", "say", "stepBegan", "afterLine", "chime", "hush", "narration", "SettingsPart", "Overlay").all { it in without })
        // The empty door carries no word of it.
        val empty = File(none, "java/com/bigpapituna/sharpears/extras/Extras.kt").readText()
        assertFalse(Regex("coach|flirt|adult", RegexOption.IGNORE_CASE).containsMatchIn(empty))
        assertTrue(none.walkTopDown().none { it.isDirectory && it.name == "assets" })
    }

    @Test fun theBuildTakesItOnlyWithTheKey() {
        val gradle = File("build.gradle.kts").readText()
        assertTrue("""    "false" -> false""" in gradle)
        assertTrue("""    "true" -> true""" in gradle)
        assertTrue("""    else -> secret("AFTERDARK").trim().equals("true", ignoreCase = true)""" in gradle)
        assertTrue("""sourceSets.getByName("main").kotlin.srcDir(if (afterdark) "src/afterdark/java" else "src/noafterdark/java")""" in gradle)
        assertTrue("""if (afterdark) sourceSets.getByName("main").assets.srcDir("src/afterdark/assets")""" in gradle)
        assertTrue("""if (!afterdark) tasks.matching { it.name == "package${'$'}cap" }.configureEach { finalizedBy(check) }""" in gradle)
        // The example secrets file names the key, off.
        assertTrue(Regex("""(?m)^AFTERDARK=\s*$""").containsMatchIn(File("../secrets.properties.example").readText()))
    }

    @Test fun whatsNewWithoutItSaysNothingAboutIt() {
        assertNull("2.16.0 has no line of the app's own", Welcome.WHATS_NEW["2.16.0"])
        assertEquals(Welcome.WHATS_NEW, Welcome.merged(emptyMap()))
        val merged = Welcome.merged(mapOf("2.16.0" to listOf("A."), "2.15.0" to listOf("B.")))
        assertEquals(listOf("A."), merged["2.16.0"])
        assertEquals(Welcome.WHATS_NEW["2.15.0"]!! + "B.", merged["2.15.0"])
        for (lines in Welcome.WHATS_NEW.values) for (l in lines) assertFalse(l, Regex("coach|flirt", RegexOption.IGNORE_CASE).containsMatchIn(l))
    }

    @Test fun theMomentsAreNamesOnly() {
        val moment = File(main, "java/com/bigpapituna/sharpears/extras/Moment.kt").readText()
        assertFalse(Regex("coach|flirt|girl", RegexOption.IGNORE_CASE).containsMatchIn(moment))
        for (t in com.bigpapituna.sharpears.engine.lessons.CheerTrigger.entries) assertEquals(t.name, Moment.of(t).name)
    }
}
