package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.practice.ArpeggioType
import com.bigpapituna.sharpears.engine.practice.Arpeggio
import com.bigpapituna.sharpears.engine.practice.Scale
import com.bigpapituna.sharpears.engine.practice.ScaleType
import com.bigpapituna.sharpears.engine.practice.warmupCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Audit (docs/AUDIT-ENGINE.md): everything the player can be handed plays from start to end in every mode and with
 * every hands setting: the library, every lesson step (and a kid's bite of it), the Practice tab's scales, arpeggios
 * and warm-ups in all twelve keys, sight-reading pieces and the chord progressions. A player who plays every note asked
 * for finishes every wait-mode group the song has for that hand, and scores 100% in Perform (every pass mark is
 * reachable). Every key that sounds is released.
 */
class PlaythroughTest {
    private val assets = File("src/main/assets")

    @Suppress("UNCHECKED_CAST")
    private fun library(): List<Song> {
        val songs = (MiniJson.read(File(assets, "songs/index.json").readText()) as Map<String, Any?>)["songs"] as List<Map<String, Any?>>
        return songs.filter { !(it["file"] as String).startsWith("user/") }.map { m ->
            val hands = m["hands"]
            val rule = when {
                hands is Map<*, *> -> HandRule.Tracks((hands["right"] as Number).toInt(), (hands["left"] as Number).toInt())
                hands == "middleC" -> HandRule.MiddleCSplit
                else -> HandRule.Auto
            }
            SmfParser.parse(File(assets, "songs/" + m["file"]).readBytes(), m["id"] as String, m["title"] as String, rule)
        }
    }

    private fun content(): List<Song> = buildList {
        addAll(library())
        for (lesson in COURSE.flatMap { it.lessons }) lesson.steps.forEachIndexed { i, s ->
            val ex = s.exercise ?: return@forEachIndexed
            add(ex.toSong("${lesson.id}#$i", lesson.title))
            if (ex.phraseEnd != null) add(ex.bite().toSong("${lesson.id}#$i bite", lesson.title))
        }
        for (root in 0..11) {
            for (t in ScaleType.entries) add(Scale(root, t, 2).toExercise(Practice.BOTH, 80.0).toSong("scale $root $t", ""))
            for (t in ArpeggioType.entries) add(Arpeggio(root, t, 2).toExercise(Practice.BOTH, 80.0).toSong("arpeggio $root $t", ""))
            warmupCatalog(root).forEach { add(it.exercise.toSong(it.id, it.title)) }
        }
        for (level in 1..2) for (seed in 0L until 20L) add(sightPiece(level, seed).exercise.toSong("sight $level $seed", ""))
        PROGRESSIONS.forEach { p -> PROGRESSION_KEYS.forEach { k -> add(progressionSong(p, k)) } }
    }

    private class Run(val finished: Boolean, val groupsDone: Int, val required: Int, val recap: Recap?, val unreleased: Set<Int>)

    /** The song played by someone who plays exactly what is asked: in wait mode each group's keys, in Perform on time. */
    private fun play(song: Song, mode: Mode, practice: Practice): Run {
        val t = Transport(song, fitRange(song.notes)).apply { this.mode = mode; this.practice = practice; play() }
        val required = song.groups.count { g -> g.notes.any(t::required) }
        val down = HashSet<Int>()
        var done = 0
        var frames = 0
        val limit = song.durationMs / 16 + required * 4 + 1000
        while (t.playing && frames++ < limit) {
            for (e in t.tick(16.0)) if (e is NoteEvent.On) down += e.midi else down -= e.midi
            if (mode == Mode.WAIT && t.waiting) {
                val keys = t.expected.map { it.midi }.distinct()
                for (k in if (t.nextGroup?.anyOf == true) keys.take(1) else keys) if (t.noteInput(k) == InputResult.GROUP_DONE) done++
            }
            if (mode == Mode.PERFORM) for (k in t.listenFor) t.noteInput(k)
        }
        return Run(!t.playing, done, required, t.score?.recap(), down)
    }

    @Test fun everythingPlaysThroughInEveryModeAndHand() {
        val all = content()
        assertTrue("content: ${all.size}", all.size > 900)
        for (song in all) for (mode in Mode.entries) for (practice in Practice.entries) {
            val what = "${song.id} $mode $practice"
            val r = play(song, mode, practice)
            assertTrue("$what reaches the end", r.finished)
            assertEquals("$what releases every key", emptySet<Int>(), r.unreleased)
            if (mode == Mode.WAIT) assertEquals("$what: every group played", r.required, r.groupsDone)
            if (mode == Mode.PERFORM && r.required > 0) assertEquals("$what: a perfect run scores 100", 100, r.recap!!.accuracy)
        }
    }

    /** Every Perform step of the course can be passed, by the hand it asks for, at its own pass mark. */
    @Test fun everyPerformStepHasNotesToPlayAndASanePassMark() {
        for (lesson in COURSE.flatMap { it.lessons }) for (step in lesson.steps) {
            val ex = step.exercise ?: continue
            assertTrue("${lesson.id}: bpm ${ex.bpm}", ex.bpm in 30.0..200.0)
            assertTrue("${lesson.id}: pass ${ex.pass}", ex.pass in 1..100)
            if (step.kind != StepKind.PERFORM && step.kind != StepKind.RAMP) continue
            val r = play(ex.toSong(lesson.id, lesson.title), Mode.PERFORM, ex.practice)
            assertTrue("${lesson.id}: nothing to play", r.required > 0)
            assertTrue("${lesson.id}: ${r.recap}", step.passed(r.recap))
        }
    }
}
