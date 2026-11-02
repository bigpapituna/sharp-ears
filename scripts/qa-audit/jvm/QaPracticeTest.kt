package com.bigpapituna.sharpears.qa

import com.bigpapituna.sharpears.engine.*
import com.bigpapituna.sharpears.engine.practice.*
import org.junit.Test
import java.io.File

// THROWAWAY (QA audit): every practice scale, arpeggio and warm-up in every key, checked for playable notes, fingers and spelling.
class QaPracticeTest {
    @Test fun walk() {
        val out = StringBuilder(); var findings = 0; var checked = 0
        fun bad(s: String) { findings++; out.appendLine("FINDING $s") }
        fun check(tag: String, ex: Exercise) {
            checked++
            val song = try { ex.toSong(tag, tag) } catch (e: Throwable) { bad("$tag toSong threw ${e.message}"); return }
            if (song.notes.isEmpty()) bad("$tag no notes")
            val lo = song.notes.minOf { it.midi }; val hi = song.notes.maxOf { it.midi }
            if (lo < 21 || hi > 108) bad("$tag notes outside the piano $lo..$hi")
            val r = song.notes.count { it.hand == Hand.RIGHT }; val l = song.notes.count { it.hand == Hand.LEFT }
            val rf = ex.fingers.split(" ").filter { it.isNotBlank() }; val lf = ex.leftFingers.split(" ").filter { it.isNotBlank() }
            if (rf.isNotEmpty() && rf.size != r) bad("$tag right fingers ${rf.size} for $r notes")
            if (lf.isNotEmpty() && lf.size != l) bad("$tag left fingers ${lf.size} for $l notes")
            for (f in rf + lf) if (f.toIntOrNull() !in 1..5) bad("$tag finger '$f'")
            if (ex.bpm < 20 || ex.bpm > 240) bad("$tag bpm ${ex.bpm}")
        }
        for (type in ScaleType.entries) for (root in 0..11) for (oct in 1..2) for (p in Practice.entries) {
            val tag = "scale ${scaleRootName(root, type)} ${type.label} x$oct ${p.name}"
            try { val s = Scale(root, type, oct); check(tag, s.toExercise(p, 80.0)); check("$tag once", s.toExercise(p, 80.0, upAndDown = false))
                if (s.title(p).isBlank()) bad("$tag blank title")
                // the descent must come back to the root
                val up = s.upAndDown(Hand.RIGHT); if (up.first() != up.last()) bad("$tag up and down does not return to its start")
                // black keys with the thumb or little finger
                val fg = s.fingering(Hand.RIGHT); val asc = s.ascending(Hand.RIGHT)
                if (fg.size != asc.size) bad("$tag fingering ${fg.size} vs ${asc.size} notes")
            } catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
        }
        for (type in ArpeggioType.entries) for (root in 0..11) for (oct in 1..2) for (p in Practice.entries) {
            val tag = "arpeggio ${scaleRootName(root, type.minorFamily)} ${type.label} x$oct ${p.name}"
            try { val a = Arpeggio(root, type, oct); check(tag, a.toExercise(p, 80.0)); if (a.title(p).isBlank()) bad("$tag blank title") }
            catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
        }
        for (root in 0..11) for (w in warmupCatalog(root)) {
            val tag = "warmup ${w.id} root $root"
            check(tag, w.exercise)
            for (bpm in listOf(w.bpmMin, w.defaultBpm, w.bpmMax)) check("$tag @$bpm", w.at(bpm))
            if (w.defaultBpm !in w.bpmMin..w.bpmMax) bad("$tag default bpm ${w.defaultBpm} outside ${w.bpmMin}..${w.bpmMax}")
            if (w.seconds() <= 0) bad("$tag zero length")
            if (w.title.isBlank() || w.description.isBlank()) bad("$tag blank text")
        }
        for (minutes in listOf(5, 10, 15, 20)) for (level in 1..3) for (seed in 0..7) {
            val tag = "routine $minutes min L$level seed $seed"
            try {
                val rt = buildRoutine(minutes, level, seed)
                if (rt.isEmpty()) bad("$tag empty")
                val secs = routineSeconds(rt)
                if (secs > minutes * 60 * 1.5 || secs < minutes * 60 * 0.4) bad("$tag lasts ${secs.toInt()} s for $minutes min")
                rt.forEach { check("$tag ${it.warmup.id}", it.warmup.at(it.bpm)) }
            } catch (e: Throwable) { bad("$tag threw ${e::class.simpleName}: ${e.message}") }
        }
        out.appendLine("checked $checked exercises, findings: $findings")
        File("build/qa").mkdirs(); File("build/qa/practice-walk.txt").writeText(out.toString())
    }
}
