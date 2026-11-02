package com.bigpapituna.sharpears.qa

import com.bigpapituna.sharpears.engine.ChordGame
import com.bigpapituna.sharpears.engine.ChordGame.Outcome
import com.bigpapituna.sharpears.engine.ear.*
import org.junit.Test
import java.io.File

// THROWAWAY (QA audit): drives every ear game, level and 25 seeds with perfect and wrong players. Not committed.
class QaEarTest {
    @Test fun walk() {
        val out = StringBuilder()
        fun log(s: String) { out.appendLine(s) }
        var rounds = 0; var findings = 0
        fun bad(s: String) { findings++; log("FINDING $s") }
        for (kind in EarKind.entries) for (level in kind.levels.indices) {
            for (seed in 1L..25L) {
                val tag = "${kind.id} L${level + 1} seed $seed"
                try {
                    // perfect player
                    val g = newTrainer(kind, level, seed)
                    var guard = 0
                    while (!g.finished && guard++ < 50) {
                        rounds++
                        val desc = g.describe()
                        if (desc.isBlank()) bad("$tag blank describe")
                        if (g.prompt().text.isBlank()) bad("$tag blank prompt")
                        if (kind != EarKind.RHYTHM && g.toPlay().isEmpty()) bad("$tag round ${g.round + 1} nothing to play")
                        g.lit(); g.micGroups()
                        val a: ChordGame.Answer = when (g) {
                            is RhythmTapGame -> g.grade(g.pattern.noteTimesMs)
                            is MelodyMemoryGame -> { var last: ChordGame.Answer? = null; for (n in g.melody) last = g.input(n); last!! }
                            is PerfectPitchGame -> { val ids = g.choices().map { it.id }; if (g.answerId() !in ids) bad("$tag answer not among choices"); if (ids.toSet().size != ids.size) bad("$tag duplicate choices"); if (seed % 2 == 0L) g.choose(g.answerId()!!) else g.input(g.micNotes().first()) }
                            is ScaleDegreeGame -> { val ids = g.choices().map { it.id }; if (g.answerId() !in ids) bad("$tag answer not among choices"); if (g.intro().isEmpty()) bad("$tag no intro"); if (seed % 2 == 0L) g.choose(g.answerId()!!) else g.input(g.micNotes().first()) }
                            is ProgressionGame -> { val ids = g.choices().map { it.id }; if (g.answerId() !in ids) bad("$tag answer not among choices"); if (ids.toSet().size != ids.size) bad("$tag duplicate choices"); g.choose(g.answerId()!!) }
                            else -> error("unknown")
                        }
                        if (a.outcome != Outcome.DONE || !a.clean) bad("$tag perfect answer gave ${a.outcome} clean=${a.clean} line=${a.line?.text} :: $desc")
                        if (a.line != null && a.line!!.text.isBlank()) bad("$tag blank answer line")
                        g.next()
                    }
                    if (g.score != 10) bad("$tag perfect play scored ${g.score}/10")
                    // wrong player
                    val w = newTrainer(kind, level, seed)
                    val wa: ChordGame.Answer? = when (w) {
                        is RhythmTapGame -> w.grade(emptyList())
                        is MelodyMemoryGame -> w.input(w.melody[0] + 1)
                        is PerfectPitchGame -> w.choose((w.answerId()!! + 1) % 12)
                        is ScaleDegreeGame -> w.choose(w.choices().map { it.id }.first { it != w.answerId() })
                        is ProgressionGame -> w.choose(w.choices().map { it.id }.first { it != w.answerId() })
                        else -> null
                    }
                    if (wa != null && wa.clean) bad("$tag a wrong answer counted clean: ${wa.line?.text}")
                    if (wa != null && wa.outcome == Outcome.IGNORED) bad("$tag a wrong answer was ignored")
                    if (wa != null && wa.line?.text.isNullOrBlank()) bad("$tag wrong answer gave no explanation")
                    // skip everything
                    val s = newTrainer(kind, level, seed)
                    var gg = 0
                    while (!s.finished && gg++ < 50) s.skip()
                    if (!s.allSkipped) bad("$tag all skipped but allSkipped false")
                } catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
            }
        }
        // rhythm timing grades
        try {
            val g = RhythmTapGame(0, 3)
            val ex = g.pattern.noteTimesMs
            val late = g.grade(ex.map { it + 120 })
            log("rhythm: all notes 120 ms late -> ${late.outcome} clean=${late.clean} :: ${late.line?.text}")
            val g2 = RhythmTapGame(0, 3)
            val early = g2.grade(ex.map { it - 200 })
            log("rhythm: all notes 200 ms early -> ${early.outcome} clean=${early.clean} :: ${early.line?.text}")
            val g3 = RhythmTapGame(0, 3)
            val dbl = g3.grade(ex + ex.map { it + 40 })
            log("rhythm: doubled taps -> ${dbl.outcome} clean=${dbl.clean} :: ${dbl.line?.text}")
        } catch (e: Throwable) { bad("rhythm timing threw $e") }
        log("rounds driven: $rounds, findings: $findings")
        File("build/qa").mkdirs(); File("build/qa/ear-walk.txt").writeText(out.toString())
    }
}
