package com.bigpapituna.sharpears.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.bigpapituna.sharpears.engine.ProgressBackup
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.family.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

/** The per-profile event log on a real folder (a temporary one): order, background writes, the caps, delete, backup. */
class EventLogTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 86_400_000L
    private val now = 1_792_000_000_000L
    private fun done(at: Long, lesson: String = "pond-1") = BriefEvent.LessonDone(at, lesson, 3)

    /** An executor that runs nothing until asked: what the caller's thread does is all that happens. */
    private class Later : Executor {
        val tasks = ArrayDeque<Runnable>()
        override fun execute(r: Runnable) { tasks += r }
        fun runAll() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private fun file() = EventLog.fileFor(tmp.root, "p1")

    @Test fun eventsComeBackInOrderAndByTime() {
        val log = EventLog(file(), clock = { now })
        for (i in 0 until 200) log.append(done(now - 1000 + i, "pond-${i % 12 + 1}"))
        val all = log.read()
        assertEquals(200, all.size)
        assertEquals((0 until 200).map { now - 1000 + it }, all.map { it.at })
        assertEquals(listOf(now - 900, now - 899), log.read(since = now - 900, until = now - 898).map { it.at })
        assertEquals(File(File(tmp.root, "events"), "p1.log"), log.file)
        assertEquals(200, log.file.readLines().size)
    }

    @Test fun appendingNeverWritesOnTheCallersThread() {
        val io = Later()
        val log = EventLog(file(), clock = { now }, io = io)
        log.append(done(now)); log.append(done(now + 1))
        assertFalse("nothing written until the io thread runs", log.file.exists())
        assertEquals(2, io.tasks.size)          // the open's prune, and one write for both events
        io.runAll()
        assertEquals(2, log.file.readLines().size)
    }

    @Test fun openingKeepsTheLastNinetyDays() {
        file().parentFile!!.mkdirs()
        file().writeText(listOf(done(now - 100 * day), done(now - 91 * day), done(now - 89 * day), done(now - day)).joinToString("\n") { it.encode() } + "\n")
        val log = EventLog(file(), clock = { now })
        assertEquals(listOf(now - 89 * day, now - day), log.read().map { it.at })
        assertEquals(2, file().readLines().size)
    }

    @Test fun openingKeepsAtMostTheCapWhicheverIsSmaller() {
        file().parentFile!!.mkdirs()
        file().writeText((0 until 30).joinToString("\n", postfix = "\n") { done(now - 95 * day + it * 4 * day).encode() })
        val log = EventLog(file(), clock = { now }, maxEvents = 10)
        // 30 events every 4 days from 95 days ago: 28 are within 90 days (two are older), the cap keeps the last 10.
        assertEquals((20 until 30).map { now - 95 * day + it * 4 * day }, log.read().map { it.at })
        // And the age wins when it is the smaller: a cap of 50 still drops the two older than 90 days.
        file().writeText((0 until 30).joinToString("\n", postfix = "\n") { done(now - 95 * day + it * 4 * day).encode() })
        assertEquals(28, EventLog(file(), clock = { now }, maxEvents = 50).read().size)
    }

    @Test fun aLineCutShortIsDroppedAndANewerAppsEventIsKept() {
        file().parentFile!!.mkdirs()
        val unknown = "{\"t\":${now - 5},\"e\":\"hologram\",\"x\":1}"
        file().writeText(done(now - 10).encode() + "\n" + unknown + "\n" + "{\"t\":${now - 1},\"e\":\"less")
        val log = EventLog(file(), clock = { now })
        assertEquals(listOf(done(now - 10).encode(), unknown), log.lines())
        assertTrue(log.read()[1] is BriefEvent.Unknown)
    }

    @Test fun aLogGrowingPastItsCapIsPrunedAsItGoes() {
        val log = EventLog(file(), clock = { now }, maxEvents = 10)
        for (i in 0 until 11) log.append(done(now + i))
        log.flush()
        assertEquals(11, log.lines().size)          // a tenth over is fine
        log.append(done(now + 11))
        assertEquals((2..11).map { now + it }, log.read().map { it.at })
    }

    @Test fun deleteRemovesTheFileAndStopsWrites() {
        val log = EventLog(file(), clock = { now })
        log.append(done(now)); log.flush()
        assertTrue(file().exists())
        log.delete()
        log.append(done(now + 1)); log.flush()
        assertFalse(file().exists())
    }

    @Test fun replacingTheLogKeepsTheCap() {
        val log = EventLog(file(), clock = { now }, maxEvents = 3)
        log.append(done(now))
        log.replace((1..5).map { done(now + it).encode() } + "not an event" + "")
        assertEquals((3..5).map { now + it }, log.read().map { it.at })
    }

    // ---- backup ----

    private fun write(id: String, vararg ats: Long) = EventLog.fileFor(tmp.root, id).apply { parentFile!!.mkdirs(); writeText(ats.joinToString("") { done(it).encode() + "\n" }) }

    @Test fun aBackupCarriesEveryProfilesLinesAndPutsThemBack() {
        write("owner", now, now + 1); write("p1", now + 2); write("p2")
        val logs = EventLog.export(tmp.root, listOf("owner", "p1", "p2"))
        assertEquals(mapOf("owner" to listOf(done(now).encode(), done(now + 1).encode()), "p1" to listOf(done(now + 2).encode())), logs)
        // Through the backup's JSON and back.
        val text = ProgressBackup.encode(mapOf("progress" to mapOf("voice" to true)), logs)
        assertEquals(logs, ProgressBackup.decodeBackup(text).events)
        // The phone moved on meanwhile: p1 has more, p3 is someone the backup does not know.
        write("p1", now + 2, now + 3); write("p3", now + 4)
        EventLog.restore(tmp.root, logs, listOf("owner", "p1"), allProfiles = true)
        assertEquals(listOf("owner", "p1"), EventLog.profilesWithLogs(tmp.root))
        assertEquals(listOf(done(now + 2).encode()), EventLog.fileFor(tmp.root, "p1").readLines())
    }

    @Test fun aBackupFromBeforeTheBriefClearsTheLogsAndATwoPointOhOneLeavesThem() {
        write("owner", now); write("p1", now)
        EventLog.restore(tmp.root, null, listOf("owner", "p1"), allProfiles = false)        // 2.0: the owner's files only
        assertEquals(listOf("owner", "p1"), EventLog.profilesWithLogs(tmp.root))
        EventLog.restore(tmp.root, null, listOf("owner", "p1"), allProfiles = true)         // 2.1 to 2.3.1: no events in it
        assertTrue(EventLog.profilesWithLogs(tmp.root).isEmpty())
    }

    @Test fun aRestoredLogTakesOnlyEventLines() {
        EventLog.restore(tmp.root, mapOf("owner" to listOf(done(now).encode(), "garbage", "{\"t\":1,\"e\":\"x\"}", "")), listOf("owner"), true)
        assertEquals(listOf(done(now).encode(), "{\"t\":1,\"e\":\"x\"}"), EventLog.fileFor(tmp.root, "owner").readLines())
        assertTrue(runCatching { EventLog.fileFor(tmp.root, "../owner") }.isFailure)
    }

    // ---- profiles ----

    @Test fun deletingAProfileDeletesItsLog() {
        val phone = FolderContext(tmp.root)
        val profiles = Profiles(phone, clock = { now }, log = {})
        val mia = profiles.create(Profile.kid("Mia", 1, reader = false))!!
        write(mia.id, now); write("owner", now)
        assertTrue(profiles.delete(mia.id))
        assertEquals(listOf("owner"), EventLog.profilesWithLogs(tmp.root))
    }

    @Test fun briefsDoNothingBeforeTheAppStartsThem() {
        Briefs.log { BriefEvent.SessionStarted(it) }
        assertNull(Briefs.logOf("owner"))
        assertTrue(Briefs.read("owner").isEmpty())
        assertNull(Briefs.sessionSoFar("owner"))
    }
}

/** Preferences in memory and a real files folder, so Profiles runs on the JVM with its event logs. */
private class FolderContext(private val files: File) : ContextWrapper(null) {
    private val prefs = HashMap<String, SharedPreferences>()
    override fun getApplicationContext(): Context = this
    override fun getFilesDir(): File = files
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs.getOrPut(name) { MapPrefs() }
    override fun deleteSharedPreferences(name: String): Boolean = prefs.remove(name) != null
}

private class MapPrefs : SharedPreferences {
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
