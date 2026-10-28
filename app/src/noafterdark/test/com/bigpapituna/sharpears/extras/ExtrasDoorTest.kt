package com.bigpapituna.sharpears.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.16, the build without the extra (src/noafterdark, CLAUDE.md "2.16 coach voice"): its door takes no moment, adds no
 * What's new line and runs what waits on it at once; nothing of src/afterdark is compiled or packaged in.
 */
class ExtrasDoorTest {
    @Test fun theDoorDoesNothing() {
        assertFalse(Extras.BUILT)
        assertTrue(Extras.WHATS_NEW.isEmpty())
        for (m in Moment.entries) assertNull(m.name, Extras.line(m, 5))
        var ran = false
        Extras.afterLine { ran = true }
        assertTrue(ran)
    }

    @Test fun theBuildTakesNoneOfTheExtra() {
        // This test runs only in the build without it (its source set is chosen with the door's).
        val gradle = File("build.gradle.kts").readText()
        assertTrue("if (afterdark) sourceSets.getByName(\"main\").assets.srcDir(\"src/afterdark/assets\")" in gradle)
        assertEquals(1, Regex("""kotlin\.srcDir\(if \(afterdark\) "src/afterdark/java" else "src/noafterdark/java"\)""").findAll(gradle).count())
    }
}
