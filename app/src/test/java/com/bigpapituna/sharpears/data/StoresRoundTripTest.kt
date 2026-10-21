package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.bigpapituna.sharpears.engine.ear.EarKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SharedPreferences in memory, shared by every store opened on the same [FileContext]. */
private class FilePrefs : SharedPreferences {
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

private class FileContext : ContextWrapper(null) {
    val files = HashMap<String, FilePrefs>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = files.getOrPut(name) { FilePrefs() }
}

/**
 * Audit (docs/AUDIT-ENGINE.md): the Practice tab's and ear training's stores give back every field they wrote, read by a
 * fresh store on the same file, per profile, across the year's end. (The stores that log through android.util.Log are
 * Android-bound; their encodings are pure and tested with the engine.)
 */
class StoresRoundTripTest {
    @Test fun earTrainingComesBackAsItWent() {
        val phone = FileContext()
        val day = java.time.LocalDate.of(2026, 12, 31).toEpochDay()
        EarStore(phone, "p2").apply {
            assertTrue(saveBest(EarKind.DEGREE, 2, 8)); assertFalse(saveBest(EarKind.DEGREE, 2, 7))
            played(EarKind.DEGREE, day); played(EarKind.DEGREE, day + 1)
            recordWorkout(13, 20, day); recordWorkout(15, 20, day + 1)
        }
        val back = EarStore(phone, "p2")
        assertEquals(8, back.best(EarKind.DEGREE, 2))
        assertEquals(-1, back.best(EarKind.DEGREE, 1))
        assertEquals(day + 1, back.lastPlayedDay(EarKind.DEGREE))
        assertEquals(2, back.plays(EarKind.DEGREE))
        assertEquals(setOf(day, day + 1), back.workoutDays())
        assertEquals(2, back.workoutsDone())
        assertEquals("15/20", back.lastWorkout())
        assertEquals(2, back.workoutStreak(day + 1))
        assertTrue(back.workoutDoneToday(day + 1))
        assertEquals("ear.p2 only", setOf("ear.p2"), phone.files.keys)
        assertEquals(-1, EarStore(phone, "owner").best(EarKind.DEGREE, 2))
    }

    @Test fun practiceToolsComeBackAsTheyWent() {
        val phone = FileContext()
        PracticeStore(phone, "owner").apply {
            putInt("scale.bpm", 84); putString("scale.key", "Eb"); putBool("practice.loop", true)
            recordScale("MAJOR", 3, 80); recordScale("MAJOR", 3, 72); recordArpeggio("MINOR", 9, 0); recordWarmup("hanon-1-0", 60)
        }
        val back = PracticeStore(phone, "owner")
        assertEquals(84, back.int("scale.bpm", 0)); assertEquals("Eb", back.string("scale.key", "C")); assertTrue(back.bool("practice.loop", false))
        assertEquals(PracticeStat(2, 80), back.stat("scale:MAJOR:3"))
        assertEquals(PracticeStat(1, 0), back.stat("arp:MINOR:9"))
        assertEquals(setOf("scale:MAJOR:3", "arp:MINOR:9", "warmup:hanon-1-0"), back.allStats().keys)
        assertEquals(2, back.distinctKeysPractised())
        assertEquals(java.time.DayOfWeek.MONDAY, back.enum("day", java.time.DayOfWeek.MONDAY))
        back.putEnum("day", java.time.DayOfWeek.FRIDAY)
        assertEquals(java.time.DayOfWeek.FRIDAY, PracticeStore(phone, "owner").enum("day", java.time.DayOfWeek.MONDAY))
        assertEquals(setOf("practice"), phone.files.keys)
        back.clear()
        assertNull(PracticeStore(phone, "owner").allStats().keys.firstOrNull())
    }
}
