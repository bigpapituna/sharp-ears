package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SharedPreferences in memory (as in WorldStoreTest), so the kid home's store runs on the JVM. */
private class KidMemory : SharedPreferences {
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

private class KidPhone : ContextWrapper(null) {
    val files = LinkedHashMap<String, KidMemory>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = files.getOrPut(name) { KidMemory() }
}

class KidStoreTest {
    private val phone = KidPhone()
    private fun store(id: String) = KidStore(phone, id, log = {})

    @Test fun aStoneKeepsItsBestStarsAcrossReplaysAndReopens() {
        val mia = store("p1")
        assertEquals(0, mia.stars("playground-1"))
        assertEquals(2, mia.recordStars("playground-1", 2))
        assertEquals("a worse replay keeps the best", 2, mia.recordStars("playground-1", 1))
        assertEquals("a better one raises it", 3, mia.recordStars("playground-1", 3))
        assertEquals(1, mia.recordStars("playground-2", 0))
        val again = store("p1")
        assertEquals(3, again.stars("playground-1"))
        assertEquals(mapOf("playground-1" to 3, "playground-2" to 1), again.allStars())
    }

    @Test fun eachProfileHasItsOwnFileInTheBackupList() {
        store("owner").recordStars("playground-1", 3)
        store("p2").recordStars("playground-1", 1)
        assertEquals(setOf("kid", "kid.p2"), phone.files.keys)
        assertEquals(3, store("owner").stars("playground-1"))
        assertEquals(1, store("p2").stars("playground-1"))
        assertTrue("backup, import and deleting a profile cover it", "kid" in ProgressFiles.NAMES)
    }

    @Test fun aFinalePlaysOnceAndTheRoadLineIsSaidOnce() {
        val mia = store("p1")
        assertTrue(mia.finales().isEmpty())
        val v = mia.version.intValue
        mia.finalePlayed("garden")
        mia.finalePlayed("garden")
        assertEquals(setOf("garden"), store("p1").finales())
        assertEquals("one change, one bump", v + 1, mia.version.intValue)
        assertFalse(mia.roadSaid)
        mia.roadSaid = true
        assertTrue(store("p1").roadSaid)
        // The debug seed forgets a page's finale and its stones' stars, or everything.
        mia.recordStars("playground-1", 2)
        mia.debugForget(lessonIds = listOf("playground-1"), pageIds = listOf("garden"))
        assertTrue(mia.finales().isEmpty())
        assertEquals(0, mia.stars("playground-1"))
        assertFalse(mia.roadSaid)
        mia.recordStars("playground-2", 2); mia.finalePlayed("pond")
        mia.debugForget()
        assertTrue(mia.allStars().isEmpty() && mia.finales().isEmpty())
    }
}
