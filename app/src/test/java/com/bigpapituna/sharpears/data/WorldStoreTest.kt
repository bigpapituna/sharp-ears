package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.bigpapituna.sharpears.engine.world.Place
import com.bigpapituna.sharpears.engine.world.PupSaysResult
import com.bigpapituna.sharpears.engine.world.TreatJar
import com.bigpapituna.sharpears.engine.world.WearItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** SharedPreferences in memory (as in ProfilesStoreTest), so the store runs on the JVM. */
private class Memory : SharedPreferences {
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

private class Phone : ContextWrapper(null) {
    val files = LinkedHashMap<String, Memory>()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = files.getOrPut(name) { Memory() }
}

class WorldStoreTest {
    private val phone = Phone()
    private fun store(id: String) = WorldStore(phone, id, log = {})

    @Test fun eachProfileHasItsOwnFileAndItSurvivesAReopen() {
        val owner = store("owner")
        val kid = store("p1")
        owner.lessonComplete("sit")
        owner.lessonComplete("find-c")
        kid.lessonComplete("pg-1", Place.GARDEN)
        assertTrue("world" in phone.files)
        assertTrue("world.p1" in phone.files)
        assertEquals(2, owner.state.history.rolls)
        assertEquals(1, kid.state.history.rolls)
        // Reopened (a profile switch or a restart): the same world, the same seed, so the same next drop.
        val again = store("owner")
        assertEquals(owner.state, again.state)
        assertEquals(owner.preview(), again.preview())
    }

    @Test fun theSeedIsFixedOnFirstOpen() {
        val a = store("p2").state.seed
        assertEquals(a, store("p2").state.seed)
    }

    @Test fun jarNotesAreSavedInBatchesAndOnFlush() {
        val s = store("p3")
        s.addNotes(3)
        assertEquals(3, s.state.jar)
        assertEquals(0, store("p3").state.jar)                     // not saved yet
        s.addNotes(2)
        assertEquals(5, store("p3").state.jar)                     // every five notes
        s.addNotes(1)
        s.flush()
        assertEquals(6, store("p3").state.jar)
        s.addNotes(500)
        assertEquals(TreatJar.CAPACITY, store("p3").state.jar)     // a full jar is saved at once
        assertTrue(s.tip())
        assertFalse(s.tip())
        assertEquals(1, store("p3").state.treats)
    }

    @Test fun latestStickerForTheFamilyWall() {
        assertNull(WorldStore.latestSticker(phone, "p4"))
        val s = store("p4")
        val r = s.lessonComplete("pg-1", Place.POND)
        assertEquals(WorldStore.stickerRes(r.drops.last().sticker), WorldStore.latestSticker(phone, "p4"))
    }

    @Test fun crownOfTheDayAcrossProfiles() {
        val day = LocalDate.of(2026, 10, 18)
        store("owner").pupSays(PupSaysResult("owner", 6, day, atMillis = 200))
        store("p1").pupSays(PupSaysResult("p1", 6, day, atMillis = 100))
        store("p2").pupSays(PupSaysResult("p2", 4, day, atMillis = 50))
        store("p2").pupSays(PupSaysResult("owner", 9, day, atMillis = 60))    // someone else's result: ignored
        assertEquals("p1", WorldStore.crownOfTheDay(phone, listOf("owner", "p1", "p2"), day))
        assertNull(WorldStore.crownOfTheDay(phone, listOf("owner", "p1", "p2"), day.plusDays(1)))
    }

    @Test fun outfitAndRecords() {
        val s = store("p5")
        s.wear(WearItem.BOW_TIE)
        assertTrue(s.state.outfit.isEmpty)                         // locked before any practice
        s.songComplete("twinkle", 82)
        s.wear(WearItem.BOW_TIE)
        assertTrue("a song alone is not enough", s.state.outfit.isEmpty)
        // 2.5: the ledger reads the practice log; a quarter of an hour opens the first item.
        s.ledgerSource = { com.bigpapituna.sharpears.engine.world.LedgerInput(mapOf(LocalDate.now() to 15 * 60), emptySet(), emptySet()) }
        s.sync()
        assertEquals(listOf(WearItem.BOW_TIE), s.state.fresh)
        s.wear(WearItem.BOW_TIE)
        assertEquals(setOf(WearItem.BOW_TIE), store("p5").state.outfit.items)
        s.mergeRecords(mapOf("twinkle" to 70, "ode" to 96))
        assertEquals(mapOf("twinkle" to 82, "ode" to 96), store("p5").state.records)
    }
}
