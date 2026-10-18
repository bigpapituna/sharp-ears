package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.family.ProfileFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.2.1 QA: every preference file the app opens per profile is in [ProgressFiles.NAMES], so backup, import and profile
 * deletion cover it; the files opened without a profile are the phone's own (the mic setup, the report token, the
 * roster). Read from the sources, so a new store that forgets to join the list fails here.
 */
class ProgressFilesCoverageTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun everyPerProfileFileIsBackedUpAndDeletedWithItsProfile() {
        val perProfile = mutableSetOf<String>()
        for (f in sources) {
            val text = f.readText()
            Regex("""fileFor\("([a-z]+)"""").findAll(text).forEach { perProfile += it.groupValues[1] }
            // Stores that name their file in a constant: fileFor(FILE, ...) with const val FILE = "kid".
            if (Regex("""fileFor\((?:\w+\.)?FILE\b""").containsMatchIn(text) && !text.contains("PlayStore.FILE"))
                Regex("""const val FILE = "([a-z]+)"""").find(text)?.let { perProfile += it.groupValues[1] }
        }
        assertTrue("found the stores: $perProfile", perProfile.size >= 10)
        for (name in perProfile) assertTrue("\"$name\" is opened per profile but missing from ProgressFiles.NAMES", name in ProgressFiles.NAMES)
        // Every per-profile name is deleted with a profile; only the shared ones stay.
        for (name in ProgressFiles.NAMES - ProfileFiles.SHARED) assertTrue(name, "$name.p4" in ProfileFiles.filesOf("p4", ProgressFiles.NAMES))
    }

    @Test fun theFilesOpenedWithoutAProfileAreThePhones() {
        val phoneWide = sources.flatMap { f -> Regex("""getSharedPreferences\("([a-z]+)"""").findAll(f.readText()).map { it.groupValues[1] }.toList() }.toSet()
        assertEquals(setOf("mic", "reports"), phoneWide)
        assertTrue("the mic setup travels in a backup", "mic" in ProgressFiles.NAMES && "mic" in ProfileFiles.SHARED)
    }
}
