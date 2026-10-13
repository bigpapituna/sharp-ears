package com.bigpapituna.sharpears.progress

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.bigpapituna.sharpears.data.Prefs
import com.bigpapituna.sharpears.data.ProgressStore
import com.bigpapituna.sharpears.engine.COURSE
import com.bigpapituna.sharpears.engine.progress.Heat
import com.bigpapituna.sharpears.engine.progress.LevelUp
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** SharedPreferences in memory, so the store's bookkeeping runs on the JVM. */
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

private class FakeContext : ContextWrapper(null) {
    private val files = HashMap<String, MemoryPrefs>()
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = files.getOrPut(name) { MemoryPrefs() }
}

class ProgressStoreTest {
    private val context: Context = FakeContext()
    private val prefs = Prefs(context)
    private val store = ProgressStore(context, prefs)

    private fun at(day: LocalDate, hour: Int = 12) = LocalDateTime.of(day, LocalTime.of(hour, 0))
    private val d0 = LocalDate.of(2026, 9, 1)

    @Test fun practiceMinutesPayTenXpEach() {
        val r = store.record(ProgressEvent.Practice(at(d0), 60))
        assertEquals(10, r.xpGained)
        assertEquals(10, store.xp)
        // Another minute crosses into the second.
        assertEquals(10, store.record(ProgressEvent.Practice(at(d0), 60)).xpGained)
        assertEquals(20, store.xp)
    }

    @Test fun dailyGoalPaysOncePerDay() {
        store.goalMinutes = 5
        val first = store.record(ProgressEvent.Practice(at(d0), 300))      // five minutes
        assertEquals(50 + 75, first.xpGained)
        val more = store.record(ProgressEvent.Practice(at(d0), 120))
        assertEquals(20, more.xpGained)                                     // no second bonus
        val nextDay = store.record(ProgressEvent.Practice(at(d0.plusDays(1)), 300))
        assertEquals(50 + 75, nextDay.xpGained)
        assertEquals(2, store.snapshot(d0.plusDays(1)).daysGoalMet)
    }

    @Test fun goalBonusIsNotDoubledWhenPrefsAlreadyHoldTheSeconds() {
        store.goalMinutes = 5
        prefs.addPractice(d0, 300)                                          // the app writes Prefs first
        val r = store.record(ProgressEvent.Practice(at(d0), 300))
        assertEquals(50 + 75, r.xpGained)
        assertEquals(1, store.snapshot(d0).daysGoalMet)
        assertEquals(300, store.practiceLog()[d0])                          // the larger of the two, not 600
    }

    @Test fun lessonXpIsPaidOnce() {
        assertEquals(100, store.record(ProgressEvent.LessonComplete("sit")).xpGained)
        assertEquals(0, store.record(ProgressEvent.LessonComplete("sit")).xpGained)
        assertEquals(15, store.record(ProgressEvent.LessonStepPassed).xpGained)
    }

    @Test fun theoryXpIsPaidOnce() {
        assertEquals(60, store.record(ProgressEvent.TheoryLesson("t1")).xpGained)
        assertEquals(0, store.record(ProgressEvent.TheoryLesson("t1")).xpGained)
        assertEquals(200, store.record(ProgressEvent.TheoryUnit("u1")).xpGained)
        assertEquals(0, store.record(ProgressEvent.TheoryUnit("u1")).xpGained)
    }

    @Test fun songRunsAndNewBests() {
        val plain = store.record(ProgressEvent.SongRun("twinkle", null, newBest = false, firstCompletion = true))
        assertEquals(50, plain.xpGained)
        val best = store.record(ProgressEvent.SongRun("twinkle", 96, newBest = true, firstCompletion = false))
        assertEquals(100, best.xpGained)
        val s = store.snapshot(d0)
        assertEquals(1, s.songsCompleted)
        assertEquals(1, s.performRuns)
        assertEquals(96, s.bestPerform)
        assertEquals(1, s.newBests)
        assertTrue("perform-95" in best.unlocked.map { it.id })
    }

    @Test fun levelUpIsReportedAndQueued() {
        val r = store.record(ProgressEvent.LessonComplete("sit"))           // 100 XP is level 2
        assertEquals(LevelUp(1, 2), r.levelUp)
        assertEquals(LevelUp(1, 2), store.levelUp.value)
        assertEquals(2, store.level)
        store.consumeLevelUp()
        assertNull(store.levelUp.value)
        // Two level-ups before the first is seen merge into one moment.
        store.record(ProgressEvent.TheoryUnit("u1"))
        store.record(ProgressEvent.TheoryUnit("u2"))
        store.record(ProgressEvent.TheoryUnit("u3"))
        val pending = store.levelUp.value
        assertNotNull(pending)
        assertEquals(LevelUp(2, 4), pending!!)
        assertEquals(4, store.level)
    }

    @Test fun achievementsUnlockOnceWithADateAndAToast() {
        val first = store.record(ProgressEvent.Practice(at(d0), 30))
        assertEquals(listOf("first-note"), first.unlocked.map { it.id })
        assertEquals(1, store.toasts.size)
        assertTrue("first-note" in store.unlocked)
        val again = store.record(ProgressEvent.Practice(at(d0), 30))
        assertTrue(again.unlocked.isEmpty())
        store.popToast()
        assertTrue(store.toasts.isEmpty())
    }

    @Test fun timeOfDayAndComebackFlags() {
        store.record(ProgressEvent.Practice(at(d0, 23), 10))
        store.record(ProgressEvent.Practice(at(d0.plusDays(1), 6), 10))
        var s = store.snapshot(d0.plusDays(1))
        assertTrue(s.nightOwl); assertTrue(s.earlyBird); assertFalse(s.comeback)
        store.record(ProgressEvent.Practice(at(d0.plusDays(9), 12), 10))   // seven full days off
        s = store.snapshot(d0.plusDays(9))
        assertTrue(s.comeback)
    }

    @Test fun chordRootsCollectAndEveryKeyUnlocks() {
        for (root in 0..10) store.record(ProgressEvent.ChordRound(root))
        assertFalse("every-key" in store.unlocked)
        val r = store.record(ProgressEvent.ChordRound(11))
        assertTrue("every-key" in r.unlocked.map { it.id })
        assertEquals(12, store.snapshot(d0).chordRounds)
    }

    @Test fun gamesCountAndPerfectOnes() {
        store.record(ProgressEvent.GameComplete("build", 0, 7))
        store.record(ProgressEvent.GameComplete("build", 0, 10))
        val s = store.snapshot(d0)
        assertEquals(2, s.gamesCompleted)
        assertEquals(1, s.perfectGames)
    }

    @Test fun featuresCollectFromEventsAndExplicitReports() {
        store.record(ProgressEvent.FeatureUsed("mic"))
        store.record(ProgressEvent.EarRound(true))
        store.record(ProgressEvent.EarRound(false))
        store.record(ProgressEvent.SightRead(1))
        val s = store.snapshot(d0)
        assertEquals(setOf("mic", "ear", "sight"), s.featuresUsed)
        assertEquals(2, s.earRounds); assertEquals(1, s.earCorrect); assertEquals(1, s.sightReadRuns)
    }

    @Test fun lessonsAndUnitsReadFromPrefsToo() {
        val basics = COURSE.first { it.id == "basics" }
        basics.lessons.forEach { prefs.markLessonDone(it.id) }
        val s = store.snapshot(d0)
        assertEquals(basics.lessons.size, s.lessonsDone)
        assertTrue("basics" in s.unitsComplete)
        assertTrue(s.lessonsTotal > s.lessonsDone)
    }

    @Test fun streakAndFreezesComeFromTheLog() {
        for (i in 0..6) store.record(ProgressEvent.Practice(at(d0.plusDays(i.toLong())), 60))
        val st = store.streak(d0.plusDays(6))
        assertEquals(7, st.streak); assertEquals(1, st.freezes)
        assertTrue(store.snapshot(d0.plusDays(6)).longestStreak >= 7)
    }

    @Test fun answersPersistAcrossInstances() {
        store.goalMinutes = 20
        store.weeklyDays = 5
        store.onboardingDone = true
        store.useMic = true
        store.experience = com.bigpapituna.sharpears.engine.progress.Experience.A_LITTLE
        store.record(ProgressEvent.LessonComplete("sit"))
        val again = ProgressStore(context, prefs)
        assertEquals(20, again.goalMinutes); assertEquals(5, again.weeklyDays)
        assertTrue(again.onboardingDone); assertTrue(again.useMic)
        assertEquals(com.bigpapituna.sharpears.engine.progress.Experience.A_LITTLE, again.experience)
        assertEquals(100, again.xp)
        assertTrue(again.unlocked.containsKey("lesson-1"))
    }

    @Test fun catchUpCreditsEarlierWorkQuietlyAndOnlyOnce() {
        prefs.addPractice(LocalDate.now(), 600)                              // ten minutes in Prefs
        prefs.markLessonDone("sit")
        store.catchUp()
        assertEquals(100 + 100, store.xp)
        assertNull(store.levelUp.value)
        assertTrue(store.toasts.isEmpty())
        assertTrue("first-note" in store.unlocked)
        store.catchUp()
        assertEquals(200, store.xp)
        // A replayed lesson does not pay again.
        assertEquals(0, store.record(ProgressEvent.LessonComplete("sit")).xpGained)
    }

    @Test fun heatSteps() {
        assertEquals(0, Heat.level(0, 10))
        assertEquals(1, Heat.level(60, 10))
        assertEquals(2, Heat.level(300, 10))
        assertEquals(3, Heat.level(600, 10))
        assertEquals(4, Heat.level(1200, 10))
    }
}
