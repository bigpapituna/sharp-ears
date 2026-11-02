package com.bigpapituna.sharpears.qa

import com.bigpapituna.sharpears.engine.*
import com.bigpapituna.sharpears.engine.theory.spokenTheory
import org.junit.Test
import java.io.File

// THROWAWAY (QA audit): dumps the course to app/build/qa/course.json. Not committed.
class QaDumpTest {
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\n") + "\""
    @Test fun dump() {
        val pg = com.bigpapituna.sharpears.engine.playground.PLAYGROUND_LEVELS.map { it.id }.toSet()
        val sb = StringBuilder("[")
        var firstL = true
        for (lv in COURSE) for (l in lv.lessons) {
            if (!firstL) sb.append(","); firstL = false
            sb.append("{\"level\":${q(lv.id)},\"playground\":${lv.id in pg},\"id\":${q(l.id)},\"title\":${q(l.title)},\"song\":${q(l.songId ?: "")},\"steps\":[")
            l.steps.forEachIndexed { i, s ->
                if (i > 0) sb.append(",")
                sb.append("{\"kind\":${q(s.kind.name)},\"text\":${q(s.text)},\"game\":${q(s.game?.toString() ?: "")},\"scenes\":[")
                sb.append(s.scenes.joinToString(",") { q(spokenTheory(it.say)) })
                sb.append("],\"lines\":[")
                val lines = mutableListOf(SpokenCatalog.normalize(s.text.replace("\n\n", " ")))
                s.scenes.forEach { lines += SpokenCatalog.normalize(spokenTheory(it.say)) }
                sb.append(lines.distinct().joinToString(",") { q(it) + "" })
                sb.append("]")
                val ex = s.exercise
                if (ex != null) {
                    val song = ex.toSong(l.id, l.title)
                    sb.append(",\"bpm\":${ex.bpm},\"pass\":${ex.pass},\"practice\":${q(ex.practice.name)},\"beats\":${ex.beats},\"right\":${q(ex.right)},\"left\":${q(ex.left)},\"endMs\":${song.durationMs}")
                    sb.append(",\"notes\":[" + song.notes.joinToString(",") { "[${it.midi},${it.startMs},${it.durationMs},${if (it.hand == Hand.RIGHT) 1 else 0}]" } + "]")
                    sb.append(",\"groups\":[" + song.groups.joinToString(",") { g -> "{\"t\":${g.startMs},\"any\":${g.anyOf},\"m\":[${g.notes.joinToString(",") { it.midi.toString() }}]}" } + "]")
                }
                sb.append("}")
            }
            sb.append("],\"next\":${q(SpokenCatalog.normalize("Next: the lesson " + com.bigpapituna.sharpears.engine.theory.spokenNumerals(l.title)))}}")
        }
        sb.append("]")
        File("build/qa").mkdirs(); File("build/qa/course.json").writeText(sb.toString())
        // catalog
        val kid = SpokenCatalog.kidLines().map(SpokenCatalog::normalize).toSet()
        val all = SpokenCatalog.all(emptyList())
        val grown = all.filter { it !in kid }
        File("build/qa/catalog.tsv").writeText(grown.joinToString("\n") { SpokenCatalog.key(it) + "\t" + it.replace("\t", " ") })
        File("build/qa/catalog-kid.tsv").writeText(kid.joinToString("\n") { SpokenCatalog.key(it) + "\t" + it.replace("\t", " ") })
    }

    @Test fun dumpTheory() {
        val sb = StringBuilder("[")
        var first = true
        for ((u, l) in com.bigpapituna.sharpears.engine.theory.THEORY_LESSONS) {
            if (!first) sb.append(","); first = false
            sb.append("{\"unit\":${q(u.id)},\"id\":${q(l.id)},\"title\":${q(l.title)},\"scenes\":[" + l.scenes.joinToString(",") { q(it.caption) } + "],\"quiz\":[")
            sb.append(l.quiz.joinToString(",") { qq ->
                when (qq) {
                    is com.bigpapituna.sharpears.engine.theory.Choice -> "{\"t\":\"choice\",\"prompt\":${q(qq.prompt)},\"options\":[" + qq.options.joinToString(",") { q(it) } + "],\"answer\":${qq.answer}}"
                    is com.bigpapituna.sharpears.engine.theory.Listen -> "{\"t\":\"listen\",\"prompt\":${q(qq.prompt)},\"clips\":${qq.clips},\"options\":[" + qq.options.joinToString(",") { q(it) } + "],\"answer\":${qq.answer}}"
                    is com.bigpapituna.sharpears.engine.theory.PlayIt -> "{\"t\":\"play\",\"prompt\":${q(qq.prompt)},\"target\":${qq.target},\"any\":${qq.anyOctave},\"low\":${qq.low},\"high\":${qq.high}}"
                }
            })
            sb.append("]}")
        }
        sb.append("]")
        File("build/qa").mkdirs(); File("build/qa/theory.json").writeText(sb.toString())
    }
}
