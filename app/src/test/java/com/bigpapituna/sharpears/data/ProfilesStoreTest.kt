package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.family.ProfileKind
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

/** SharedPreferences in memory, so the stores run on the JVM. */
private class MemoryPrefs : SharedPreferences {
    val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(k: String, d: String?) = map[k] as? String ?: d
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(k: String, d: MutableSet<String>?) = (map[k] as? Set<String>)?.toMutableSet() ?: d
    override fun getInt(k: String, d: Int) = map[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = map[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = map[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = map[k] as? Boolean ?: d
    override fun contains(k: String) = map.containsKey(k)
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val pending = HashMap<String, Any?>(); val removed = HashSet<String>(); var clear = false
        override fun putString(k: String, v: String?) = apply { pending[k] = v }
        override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v?.toSet() }
        override fun putInt(k: String, v: Int) = apply { pending[k] = v }
        override fun putLong(k: String, v: Long) = apply { pending[k] = v }
        override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
        override fun remove(k: String) = apply { removed += k; pending.remove(k) }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            removed.forEach { map.remove(it) }
            pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
        }
    }
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

/** A phone's preference files in memory, with deletion, like Context does it. */
private class PhoneContext : ContextWrapper(null) {
    val files = LinkedHashMap<String, MemoryPrefs>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = files.getOrPut(name) { MemoryPrefs() }
    override fun deleteSharedPreferences(name: String): Boolean = files.remove(name) != null
}

class ProfilesStoreTest {
    private val phone = PhoneContext()
    private fun profiles() = Profiles(phone, clock = { 1_000L }, log = {})
    private val day = LocalDate.of(2026, 10, 14)

    @Test fun anUpgradedPhoneIsTheOwnersAndKeepsItsFiles() {
        // Roy's progress from before 2.1, in the plain files.
        Prefs(phone, "owner").markLessonDone("find-c")
        val p = profiles()
        assertEquals("owner", p.current.id)
        assertEquals("Roy", p.current.name)
        assertTrue(Prefs(phone, p.current.id).lessonDone("find-c"))
        assertTrue("progress" in phone.files)
        assertFalse(phone.files.keys.any { it.startsWith("progress.") })
    }

    @Test fun eachProfileHasItsOwnProgress() {
        val p = profiles()
        val mia = p.create(Profile.kid("Mia", 1, reader = false))!!
        assertEquals(mia, p.current)
        val miaPrefs = Prefs(phone, mia.id)
        miaPrefs.markLessonDone("find-c")
        miaPrefs.addPractice(day, 120)
        ProgressStore(phone, miaPrefs).record(ProgressEvent.Practice(LocalDateTime.of(day, java.time.LocalTime.NOON), 120))
        PracticeStore(phone, mia.id).putInt("bpm", 92)

        val roy = Prefs(phone, "owner")
        assertFalse(roy.lessonDone("find-c"))
        assertTrue(roy.practice().isEmpty())
        assertEquals(0, ProgressStore(phone, roy).xp)
        assertEquals(70, PracticeStore(phone, "owner").int("bpm", 70))

        assertTrue(Prefs(phone, mia.id).lessonDone("find-c"))                          // read back from its own file
        assertEquals(mapOf(day to 120), ProgressStore(phone, Prefs(phone, mia.id)).practiceLog())
        assertTrue(listOf("progress.p1", "profile.p1", "practice.p1").all { it in phone.files })
    }

    @Test fun theRosterSurvivesARestart() {
        val p = profiles()
        p.create(Profile.kid("Mia", 1, reader = false))
        p.create(Profile.adult("Sam", 2), switch = false)
        val again = profiles()
        assertEquals(listOf("Roy", "Mia", "Sam"), again.all.map { it.name })
        assertEquals("p1", again.current.id)
        assertTrue(again.roster.needsPicker)
    }

    @Test fun deletingRemovesTheFilesAndSwitchesToTheOwner() {
        val p = profiles()
        val mia = p.create(Profile.kid("Mia", 1, reader = false))!!
        Prefs(phone, mia.id).markLessonDone("find-c")
        EarStore(phone, mia.id)
        assertTrue(p.delete(mia.id))
        assertEquals("owner", p.current.id)
        assertEquals("owner", p.currentFlow.value.id)
        assertFalse(phone.files.keys.any { it.endsWith(".p1") })
        assertFalse(p.delete("owner"))
    }

    @Test fun eightAtMostAndGrownUpMode() {
        val p = profiles()
        repeat(7) { assertTrue(p.create(Profile.kid("Kid $it", it, reader = true)) != null) }
        assertTrue(p.isFull)
        assertNull(p.create(Profile.adult("One more", 0)))
        p.setKind("p3", ProfileKind.ADULT)
        assertEquals(listOf(6, 12, 18), p.all.first { it.id == "p3" }.hintSeconds)
    }

    @Test fun progressFilesNamesTheSameFiles() {
        assertEquals(listOf("progress", "mastery", "mic", "profile", "ear", "practice", "theory", "world", "play", "skin", "kid", "brief"),
            ProgressFiles.NAMES.map { ProgressFiles.fileFor(it, "owner") })
        assertEquals(listOf("progress.p2", "mastery.p2", "mic", "profile.p2", "ear.p2", "practice.p2", "theory.p2", "world.p2", "play.p2", "skin.p2", "kid.p2", "brief.p2"),
            ProgressFiles.NAMES.map { ProgressFiles.fileFor(it, "p2") })
    }

    @Test fun theGateTimeIsKept() {
        val p = profiles()
        p.gatePassedAt = 5_000L
        assertEquals(5_000L, profiles().gatePassedAt)
    }
}
