package com.bigpapituna.sharpears.engine.praise

import com.bigpapituna.sharpears.engine.CHEER_TITLES
import com.bigpapituna.sharpears.engine.RunWords
import com.bigpapituna.sharpears.engine.lessons.CheerCoach
import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.lessons.CheerLines
import com.bigpapituna.sharpears.engine.lessons.CheerMemory
import com.bigpapituna.sharpears.engine.lessons.CheerMoment
import com.bigpapituna.sharpears.engine.lessons.CheerTrigger
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The QA praise pass's measuring stick: 30 sessions (a lesson each, new coach objects, the app maybe restarted between), seven
 * pops per trigger per session, for the pup's cheer pops, the cheer bars' titles and a kid's recap words. Writes
 * build/praise-sim-main.txt. Run before and after the fix: only the factories below changed.
 */
class PraiseRepeatSimTest {
    private val tag = "main"
    init { synchronized(PraiseRepeatSimTest::class.java) { if (!cleared) { File("build/praise-sim-main.txt").delete(); cleared = true } } }
    private companion object { var cleared = false }

    // ---- factories: the one place the before and the after differ ------------------------------------------------------
    private var store = MapStore()
    /** Each session is a new app run: new bags bound to the history the last one left. */
    private fun bags(session: Int) = PraiseBags(java.util.Random(500L + session), store)
    private fun coach(kid: Boolean, session: Int) = CheerCoach(kid, seed = 1000L + session, memory = CheerMemory(), bags = bags(session))

    @Test fun popsRepeatLessWhenSessionsAreStrungTogether() {
        val rows = ArrayList<String>()
        for (kid in listOf(false, true)) for (t in CheerTrigger.entries) {
            val pool = if (t == CheerTrigger.RUN) CheerLines.family(t, kid).size + CheerLines.RUN_MILESTONES.sumOf { CheerLines.runNumbered(it, kid).size }
                       else CheerLines.family(t, kid).size + (if (t == CheerTrigger.FOCUS) CheerLines.FOCUS_MINUTES.sumOf { CheerLines.focusMinutes(it, kid).size } else 0)
            val s = RepeatStats("cheer pop ${if (kid) "kid" else "grown"} ${t.wire}", pool)
            var clock = 0L
            for (session in 0 until 30) {
                val c = coach(kid, session)
                repeat(7) { clock += 20_000; s.add(c.forced(t, clock).line) }
            }
            rows += s.row()
            assertEquals(s.name, 0, s.immediate)
        }
        RepeatStats.write(tag, "cheer pops: 30 sessions x 7 pops per trigger (the engine's own pick, rate limits bypassed)", rows)
    }

    @Test fun howOftenPraiseFiresOverTwoHundredCorrectNotes() {
        val rows = ArrayList<String>()
        for (freq in listOf(CheerFrequency.OFTEN, CheerFrequency.LESS)) for (kid in listOf(false, true)) {
            var pops = 0; var notes = 0
            val byTrigger = java.util.TreeMap<String, Int>()
            for (session in 0 until 30) {
                val c = coach(kid, session).also { it.frequency = freq }
                var t = 100_000L + session * 1_000_000L
                c.stepBegan("sim", t)
                for (n in 1..200) {
                    t += 450; notes++
                    if (n % 30 == 0) { c.wrong(CheerMoment(t), n / 8); continue }
                    c.right(CheerMoment(t), n / 8, onTime = true)?.let { pops++; byTrigger.merge(it.trigger.wire, 1, Int::plus) }
                }
            }
            rows += "%-6s %-5s pops per 100 correct notes: %.1f  %s".format(freq.wire, if (kid) "kid" else "grown", pops * 100.0 / notes, byTrigger)
        }
        RepeatStats.write(tag, "frequency: 30 x 200 correct notes at 450 ms, a slip every 30th, bars of 8", rows)
    }

    @Test fun cheerBarTitles() {
        val rows = ArrayList<String>()
        // Lesson: LessonScreen starts at lesson.id.hashCode() and adds one per passed step; the games: round + score (two per clean round).
        run {
            val s = RepeatStats("lesson cheer bar title", CHEER_TITLES.size)
            for (l in 0 until 30) { var cheers = "lesson-$l".hashCode(); repeat(7) { s.add(titleForLessonStep(cheers++)) } }
            rows += s.row()
            assertEquals(0, s.immediate)
        }
        run {
            val s = RepeatStats("ear/chord game title (round+score)", CHEER_TITLES.size)
            for (g in 0 until 30) for (round in 0 until 7) s.add(titleForGame(round, round))
            rows += s.row()
        }
        RepeatStats.write(tag, "cheer bar titles (CHEER_TITLES)", rows)
    }

    @Test fun kidRecapWords() {
        val rows = ArrayList<String>()
        for (stars in 1..3) {
            val t = RepeatStats("kid recap title ${stars}*", RunWords.KID_TITLES[stars - 1].size)
            val l = RepeatStats("kid recap line ${stars}*", RunWords.KID_LINES[stars - 1].size)
            val rnd = java.util.Random(7)
            repeat(30) {
                // RunRecap: seed = the event's hashCode (any int); title and line share it.
                val seed = rnd.nextInt()
                t.add(recapTitle(stars, seed)); l.add(recapLine(stars, seed))
            }
            rows += t.row(); rows += l.row()
        }
        RepeatStats.write(tag, "kid recap (RunWords)", rows)
    }

    // ---- the other factories -----------------------------------------------------------------------------------------
    private fun cheerTitle(seed: Int) = CHEER_TITLES[Math.floorMod(seed, CHEER_TITLES.size)]   // ui/Celebrate.kt cheerTitle
    private var titleBags = bags(0)
    private fun titleForLessonStep(seed: Int) = Praise.title(titleBags)
    private fun titleForGame(round: Int, score: Int) = Praise.title(titleBags)
    private fun recapTitle(stars: Int, seed: Int) = RunWords.kidTitle(stars, titleBags.index("recap.title.$stars", 3))
    private fun recapLine(stars: Int, seed: Int) = RunWords.kidLine(stars, null, titleBags.index("recap.line.$stars", 3))
}
