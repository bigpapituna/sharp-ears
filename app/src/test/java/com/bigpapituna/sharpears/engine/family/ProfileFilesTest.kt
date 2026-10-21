package com.bigpapituna.sharpears.engine.family

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFilesTest {
    private val names = listOf("progress", "mastery", "mic", "profile", "ear", "practice", "theory")   // ProgressFiles.NAMES

    @Test fun theOwnerKeepsTheFilesFromBefore21() {
        for (n in names) assertEquals(n, ProfileFiles.fileFor(n, "owner"))
    }

    @Test fun everyoneElseGetsASuffixButTheMicIsShared() {
        assertEquals("progress.p3", ProfileFiles.fileFor("progress", "p3"))
        assertEquals("theory.p3", ProfileFiles.fileFor("theory", "p3"))
        assertEquals("mic", ProfileFiles.fileFor("mic", "p3"))
        assertEquals(listOf("progress.p3", "mastery.p3", "profile.p3", "ear.p3", "practice.p3", "theory.p3"),
            ProfileFiles.filesOf("p3", names))
    }

    @Test fun aBackupHoldsEveryProfileAndTheRoster() {
        val files = ProfileFiles.backupFiles(names, listOf("owner", "p1", "p2"))
        assertEquals(1 + 6 * 3 + 1, files.size)
        assertTrue(files.containsAll(listOf("mic", "progress", "profile", "progress.p1", "ear.p2", "profiles")))
        assertFalse("mic.p1" in files)
    }

    @Test fun fileNamesTellWhoseTheyAre() {
        assertEquals("owner", ProfileFiles.profileOf("progress", names))
        assertEquals("p7", ProfileFiles.profileOf("mastery.p7", names))
        assertNull(ProfileFiles.profileOf("mic", names))
        assertNull(ProfileFiles.profileOf("profiles", names))
        assertNull(ProfileFiles.profileOf("mic.p7", names))
        assertNull(ProfileFiles.profileOf("reports", names))
        assertNull(ProfileFiles.profileOf("progress.owner", names))
        assertFalse(ProfileFiles.restorable("reports", names))
        assertTrue(ProfileFiles.restorable("profiles", names))
    }

    @Test fun aNewBackupReplacesEveryProfile() {
        val plan = ProfileFiles.importPlan(setOf("mic", "progress", "progress.p1", "profiles", "reports"), listOf("owner", "p2"), names)
        assertTrue(plan.allProfiles)
        assertEquals(setOf("mic", "progress", "progress.p1", "profiles"), plan.write)
        assertTrue(plan.clear.containsAll(listOf("progress.p2", "theory.p2", "progress.p1", "profiles", "mic")))   // p2 goes, p1 comes
        assertFalse("reports" in plan.clear)
    }

    @Test fun aBackupFrom20IsTheOwnersAlone() {
        val plan = ProfileFiles.importPlan(setOf("progress", "mic", "theory"), listOf("owner", "p1"), names)
        assertFalse(plan.allProfiles)
        assertEquals(setOf("progress", "mic", "theory"), plan.write)
        assertEquals(listOf("progress", "mic", "theory"), plan.clear)        // the other profiles are left alone
    }

    /**
     * Audit (noted in 2.2.1, not fixed then): a 2.0 backup cleared every plain file, so importing one wiped the owner's
     * world (stickers, the dog house), skin, recital and kid stars, which 2.0 never had. It replaces what it holds. (The adult audit
     * found and fixed the same, its aBackupFrom20KeepsTheOwnersNewerFiles; one test kept.)
     */
    @Test fun aBackupFrom20KeepsTheOwnersLaterFiles() {
        val now = names + listOf("world", "play", "skin", "kid")                                           // ProgressFiles.NAMES since 2.2
        val plan = ProfileFiles.importPlan(names.toSet(), listOf("owner", "p1"), now)                    // 2.0.1 wrote all seven
        assertEquals(names, plan.clear)
        assertTrue(listOf("world", "play", "skin", "kid").none { it in plan.clear })
    }
}
