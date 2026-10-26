package com.bigpapituna.sharpears.data

import com.bigpapituna.sharpears.engine.PracticeStats
import com.bigpapituna.sharpears.engine.brief.BriefEvent
import com.bigpapituna.sharpears.engine.brief.Insights
import com.bigpapituna.sharpears.engine.brief.MinutesLedger
import com.bigpapituna.sharpears.engine.brief.Period
import com.bigpapituna.sharpears.engine.family.FamilyToday
import com.bigpapituna.sharpears.engine.family.Profile
import com.bigpapituna.sharpears.engine.progress.ProgressEvent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 2.14, the one minutes ledger on the real stores (Priya's P11, the 2.4 open item "a process killed mid-session loses that
 * session's practice"): a session force-stopped mid-step keeps its checkpointed minutes once the next start closes it, and the
 * brief's Session, Today and Week, the paw strip, the family card, the email's numbers and PracticeStats over the stores'
 * practice log all say the same minutes.
 */
class MinutesLedgerStoresTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zone = ZoneId.of("America/Toronto")
    private val day = LocalDate.of(2026, 10, 21)
    private var t = day.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
    private var fg = 1_000_000L
    private lateinit var phone: FolderContext
    private lateinit var prefs: Prefs
    private lateinit var progress: ProgressStore
    private val mia = Profile.kid("Mia", 1, reader = false, id = "p1")
    /** Seconds a player holds and has not written yet (it writes every 15 s, and when asked). */
    private var held = 0

    @Before fun start() {
        Briefs.reset()
        phone = FolderContext(tmp.root)
        boot()
    }

    @After fun stop() = Briefs.reset()

    /** A process starting: the stores, the clocks, the player's flush, then init (which closes what a killed one left). */
    private fun boot() {
        prefs = Prefs(phone, mia.id)
        progress = ProgressStore(phone, prefs)
        Briefs.clock = { t }; Briefs.foreground = { fg }; Briefs.zone = zone; Briefs.logcat = {}
        Briefs.practiceSeconds = { progress.practiceSecondsTotal }
        Briefs.practiceTotalOf = { id -> if (id == mia.id) ProgressStore(phone, Prefs(phone, id)).practiceSecondsTotal else 0L }
        Briefs.addFlusher(::flushPlayer)
        Briefs.init(phone)
        Briefs.currentId = mia.id
    }

    /** What PlayerScreen's flushPractice does: Prefs first, then ProgressStore. */
    private fun flushPlayer() {
        if (held <= 0) return
        write(held); held = 0
    }

    private fun write(secs: Int) {
        val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(t), zone)
        prefs.addPractice(at.toLocalDate(), secs)
        progress.record(ProgressEvent.Practice(at, secs))
    }

    private fun play(seconds: Int, onScreen: Long = seconds * 1000L) { t += onScreen; fg += onScreen; held += seconds }

    /** The process dies: nothing ends the session; whatever the player held is lost; files and preferences stay. */
    private fun kill() {
        held = 0
        Briefs.reset()
    }

    @Test fun aSessionKilledMidStepKeepsItsMinutesAndEveryViewAgrees() {
        // A short session yesterday evening, ended normally.
        t -= 86_400_000L
        Briefs.sessionStart(mia.id); play(240); Briefs.sessionEnd()
        t += 86_400_000L - 240_000
        // Today: the kid plays, the app checkpoints every 30 s, the player writes every 15 s; then a force-stop.
        Briefs.sessionStart(mia.id)
        repeat(9) { play(30); Briefs.checkpoint() }                  // 4 min 30 s, all checkpointed
        play(15); flushPlayer()                                      // the player wrote 15 s more after the last checkpoint
        play(10)                                                     // 10 s held and never written: lost with the process
        kill()
        // The next start (the app, or the email job's alarm) closes it.
        t += 3_600_000L; fg += 0
        boot()
        val events = Briefs.read(mia.id)
        val end = events.filterIsInstance<BriefEvent.SessionEnded>().last()
        assertEquals("on screen up to the last checkpoint", 270_000L, end.durationMs)
        assertEquals("every second the stores counted", 285, end.practiceSec)
        // Today's views.
        val now = t
        val ledger = MinutesLedger.day(events, day, now, zone)
        val today = Insights.compute(events, mia, Period.DAY, now, zone = zone)
        val week = Insights.compute(events, mia, Period.WEEK, now, zone = zone)
        val note = Insights.compute(events, mia, Period.SESSION, now, zone = zone)
        val card = FamilyToday.of(mia.id, events, day, now, zone)
        val stores = progress.practiceLog()
        assertEquals(285, stores[day])
        val minutes = MinutesLedger.minutes(285)                     // 4
        assertEquals(4, minutes)
        assertEquals(minutes, ledger.practiceMinutes)
        assertEquals(minutes, today.stats.totals.practiceMinutes)
        assertEquals(minutes, note.stats.totals.practiceMinutes)
        assertEquals(minutes, week.stats.days.last().practiceMinutes)
        assertEquals(minutes, card.practiceMinutes)
        assertEquals(minutes, MinutesLedger.minutes(stores.getValue(day).toLong()))
        // The week: the brief's (and so the email's) total and PracticeStats over the stores' log.
        assertEquals(PracticeStats.weekMinutes(stores, day), week.stats.totals.practiceMinutes)
        assertEquals(MinutesLedger.minutes(240L + 285), week.stats.totals.practiceMinutes)
        assertEquals(PracticeStats.weekMinutes(MinutesLedger.practiceByDay(events, now, zone), day), PracticeStats.weekMinutes(stores, day))
        // The strip's days are the stores' days.
        assertEquals(stores.keys.filter { (stores[it] ?: 0) > 0 }.toSet(), MinutesLedger.practiceByDay(events, now, zone).keys)
    }

    /** onStop ends the session after the player writes what it holds, so the last seconds are in it, not after it. */
    @Test fun anEndCountsWhatThePlayerStillHeld() {
        Briefs.sessionStart(mia.id)
        play(44)
        Briefs.sessionEnd()
        assertEquals(44, Briefs.read(mia.id).filterIsInstance<BriefEvent.SessionEnded>().single().practiceSec)
        assertEquals(44, progress.practiceLog()[day])
        // Nothing is left to recover after a normal end.
        kill(); boot()
        assertEquals(1, Briefs.read(mia.id).filterIsInstance<BriefEvent.SessionEnded>().size)
    }

    /** A second init in the same process (the email job) leaves the open session alone. */
    @Test fun theEmailJobsInitDoesNotCloseTheOpenSession() {
        Briefs.sessionStart(mia.id)
        play(30); Briefs.checkpoint()
        Briefs.init(phone)
        assertTrue(Briefs.read(mia.id).none { it is BriefEvent.SessionEnded })
        assertEquals(30, Briefs.sessionSoFar(mia.id)!!.practiceSec)
    }

    /** At midnight a checkpoint ends the session and starts the next, so each day keeps its own minutes. */
    @Test fun midnightSplitsTheSession() {
        t = day.atTime(23, 58).atZone(zone).toInstant().toEpochMilli()
        Briefs.sessionStart(mia.id)
        play(90); Briefs.checkpoint()                                // 23:59:30
        play(60); Briefs.checkpoint()                                // 00:00:30: a new day
        play(30); Briefs.sessionEnd()
        val events = Briefs.read(mia.id)
        assertEquals(2, events.count { it is BriefEvent.SessionStarted })
        val days = MinutesLedger.byDay(events, t, zone)
        // The 60 s written at the midnight checkpoint are dated the new day by the stores, and so by the ledger.
        assertEquals(90, days.getValue(day).practiceSec)
        assertEquals(90, days.getValue(day.plusDays(1)).practiceSec)
        assertEquals(progress.practiceLog(), MinutesLedger.practiceByDay(events, t, zone))
    }

    /** A kid's daily time: the sessions ended today and the open one, from the same ledger. */
    @Test fun todaysTimeOnScreen() {
        Briefs.sessionStart(mia.id); play(120); Briefs.sessionEnd()
        t += 60_000
        Briefs.sessionStart(mia.id); play(90)
        assertEquals(210_000L, Briefs.screenMsToday(mia.id))
        play(30)
        assertEquals(240_000L, Briefs.screenMsToday(mia.id))
        Briefs.sessionEnd()
        assertEquals(240_000L, Briefs.screenMsToday(mia.id))
        Briefs.forgetToday(mia.id)
        assertEquals("read again from the log", 240_000L, Briefs.screenMsToday(mia.id))
    }
}
