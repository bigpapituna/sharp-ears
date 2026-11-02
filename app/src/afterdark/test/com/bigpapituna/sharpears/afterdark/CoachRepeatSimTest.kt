package com.bigpapituna.sharpears.afterdark

import com.bigpapituna.sharpears.engine.lessons.CheerFrequency
import com.bigpapituna.sharpears.engine.praise.MapStore
import com.bigpapituna.sharpears.engine.praise.PraiseBags
import com.bigpapituna.sharpears.engine.praise.RepeatStats
import com.bigpapituna.sharpears.extras.Moment
import org.junit.Test
import java.io.File

/**
 * The QA praise pass's measuring stick for the grown-up coach: for each voice and each bank (the moment's lines for a woman),
 * 30 sessions (the app restarted between, so a new CoachBrain each) of seven lines. Writes build/praise-sim-coach.txt.
 */
class CoachRepeatSimTest {
    private val root = File("src/afterdark/assets/coach")

    // ---- the factory: the one place the before and the after differ --------------------------------------------------------
    private var store = MapStore()
    private fun brain(session: Int) = CoachBrain(2000L + session, PraiseBags(java.util.Random(900L + session), store))

    /** The moment that draws from [bank], and the count that selects it. */
    private fun momentOf(bank: String): Pair<Moment, Int?> = when (bank) {
        "run5" -> Moment.RUN to 5; "run10" -> Moment.RUN to 10; "run20" -> Moment.RUN to 20; "run40" -> Moment.RUN to 40
        "run" -> Moment.RUN to 3
        "hard_passed" -> Moment.HARD_PASSED to null
        else -> if (bank.startsWith("focus")) Moment.FOCUS to bank.removePrefix("focus").toInt() else Moment.valueOf(bank.uppercase()) to null
    }

    @Test fun coachLinesRepeatLessAcrossSessions() {
        File("build/praise-sim-coach.txt").delete()
        for (voice in root.listFiles { f -> f.isDirectory }!!.sortedBy { it.name }) {
            store = MapStore()
            val bank = CoachBank.parse(File(voice, "lines.json").readText())
            val rows = ArrayList<String>()
            for (name in CoachBank.MOMENTS) {
                val (m, count) = momentOf(name)
                val s = RepeatStats("${voice.name} $name", bank.of(name, "w").size)
                var clock = 0L
                for (session in 0 until 30) {
                    val b = brain(session)
                    repeat(7) {
                        clock += 20_000
                        b.stepBegan()
                        // A run of 5 or 10 may roll to the plain bank: draw until this bank's turn (the plain bank counts for "run").
                        val l = b.pick(m, count, clock, CheerFrequency.OFTEN, bank, "w")
                        if (l != null && l.moment == name) s.add(l.id)
                        else if (l != null) { /* rolled to another bank */ }
                    }
                }
                rows += s.row()
                org.junit.Assert.assertEquals(s.name, 0, s.immediate)
            }
            RepeatStats.write("coach", "coach ${voice.name}: 30 sessions x 7 picks per bank, a woman", rows)
        }
    }
}
