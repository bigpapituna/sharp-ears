package com.bigpapituna.sharpears.engine

import com.bigpapituna.sharpears.engine.ear.PROGRESSION_CATALOG
import com.bigpapituna.sharpears.engine.ear.degreeFeel
import com.bigpapituna.sharpears.engine.ear.keySpoken
import com.bigpapituna.sharpears.engine.ear.pitchSpoken
import com.bigpapituna.sharpears.engine.ear.solfege
import com.bigpapituna.sharpears.engine.ear.solfegeSpoken
import com.bigpapituna.sharpears.engine.theory.THEORY_LESSONS
import com.bigpapituna.sharpears.engine.theory.spokenNumerals
import com.bigpapituna.sharpears.engine.theory.spokenTheory
import java.security.MessageDigest

/**
 * Every line the voice can say that is known ahead of time. scripts/render_voice.py records each one with the
 * natural (Kokoro) voice into assets/voice/<key>.ogg; at run time a line found there plays instantly, and anything
 * else (feedback that names notes, times and scores) is generated on the phone.
 */
/** What the pup says when a lesson step passes after a few tries. */
const val HIGH_FIVE = "High five! You stuck with it."

/** The short cheers (cheer bars, the theory quiz says them aloud); ui/Celebrate.kt cheerTitle picks one. */
val CHEER_TITLES = listOf("Nice work!", "Great job!", "That's it!", "Well played!", "You got it!", "Spot on!")

object SpokenCatalog {
    /** The same text the voice is handed, tidied: single spaces, trimmed. */
    fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()

    /** File key for a line: the first 16 hex digits of the SHA-1 of its normalised text. */
    fun key(text: String): String = MessageDigest.getInstance("SHA-1").digest(normalize(text).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(16)

    /** [library]: the bundled songs' titles (assets/songs/index.json), which Practice today names before a song part. */
    fun all(library: List<String> = emptyList()): List<String> {
        val lines = linkedSetOf<String>()
        fun add(s: String) { lines += normalize(s) }

        // Lessons: each step as LessonScreen speaks it, the end of a lesson, and the fixed feedback lines. A reading step
        // (2.0.1) is said scene by scene, each caption as the explainer reads it; its whole text stays listed too.
        for (lesson in COURSE.flatMap { it.lessons }) {
            lesson.steps.forEach { step -> add(step.text.replace("\n\n", " ")); step.scenes.forEach { add(spokenTheory(it.say)) } }
            add("Next: the lesson ${spokenNumerals(lesson.title)}")
        }
        listOf("Lesson complete.", "Clean.", "Clean. On to the next step.", "Full speed. Lesson done.", "Full speed. On to the next step.",
            "Passed.", "Passed. On to the next step.", "No notes were heard in time.", "Next: chords").forEach(::add)

        // Tempo ramp and scores.
        for (p in 50..100 step 5) add("Faster: $p percent.")
        for (s in 0..10) { add("$s out of 10."); add("$s out of 10. A new best.") }

        // Chord games: the prompt and the answers for every chord the games can draw.
        for (root in 0..11) for (type in ChordType.entries) {
            val c = Chord(root, type)
            add("Build ${chordSpoken(c)}")
            add("${chordSpoken(c)}. Clean.")
            add("That is ${chordSpoken(c)}.")
            add("Yes, ${chordSpoken(c)}.")
        }
        add(HIGH_FIVE)
        // The sample line Settings plays when a voice is chosen.
        add("Ready when you are. Play the first note.")

        // 2.0: the Theory Academy speaks every scene's line, each quiz prompt, the explanation of a miss, and a cheer.
        for ((_, lesson) in THEORY_LESSONS) {
            lesson.scenes.forEach { add(spokenTheory(it.say)) }
            lesson.quiz.forEach { add(spokenNumerals(it.prompt)); add(spokenNumerals(it.explain)) }
        }
        CHEER_TITLES.forEach(::add)
        // 2.2.1: the player's hints (the octave hint, "Your turn." after the app plays the keys, the noisy-room offer).
        com.bigpapituna.sharpears.engine.hints.OctaveHint.LINES.forEach(::add)
        earLines().forEach(::add)
        // Audit 2026-10-20: the chord and ear games' fixed lines, and Practice today's song part.
        gameLines().forEach(::add)
        library.forEach { add(SessionPart.Song(it, it, 0.0, 0.0).next) }
        // 2.1: the kid skin's words, the Playground's call-outs, the play corner, and Practice today said the kid way.
        kidLines().forEach(::add)
        return lines.toList()
    }

    /**
     * The lines a kid hears, recorded slower (scripts/render_voice.py, speed [KID_SPEED]): the kid skin (stars, steps,
     * goodnight, the keys by colour and animal), Pup's Playground (every step and scene of its three pages, the stages'
     * call-outs), the play corner and Practice today's kid words. Lines that name the kid are made on the phone, at the
     * same speed.
     */
    fun kidLines(): List<String> = buildList {
        addAll(com.bigpapituna.sharpears.engine.skin.SkinLines.fixed())
        for (lesson in com.bigpapituna.sharpears.engine.playground.PLAYGROUND_LEVELS.flatMap { it.lessons }) {
            lesson.steps.forEach { step -> add(step.text.replace("\n\n", " ")); step.scenes.forEach { add(spokenTheory(it.say)) } }
        }
        addAll(com.bigpapituna.sharpears.engine.playground.PlaygroundLines.CALL_OUTS)
        addAll(com.bigpapituna.sharpears.engine.playground.TreeSkyLines.fixed())     // 2.3: the treehouse's and the night sky's stages
        addAll(com.bigpapituna.sharpears.engine.play.PlayLines.fixed())
        addAll(com.bigpapituna.sharpears.engine.play.JamWords.spoken())
        addAll(com.bigpapituna.sharpears.engine.kid.KidLines.fixed())         // 2.2: the kid home and the finale
        addAll(com.bigpapituna.sharpears.engine.arcade.ArcadeLines.spoken())   // 2.7: the kid arcade's games
        // 2.2.1: the treat jar the kid home taps, and the noisy-room offer in a kid's words.
        addAll(com.bigpapituna.sharpears.engine.world.TreatJar.KID_LINES)
        add(com.bigpapituna.sharpears.engine.hints.NoiseWatch.LINE_KID)
        addAll(com.bigpapituna.sharpears.engine.hints.OctaveHint.KID_LINES)       // audit 2026-10-20: the octave hint, a kid's way
        add(RunWords.kidLessonLine(null))
        add(SessionWords.kidNext(SessionPart.Chords(listOf("c"))))
        for (lesson in COURSE.flatMap { it.lessons }) add(SessionWords.kidNext(SessionPart.Lesson(lesson.id, lesson.title)))
    }.map(::normalize).distinct()

    /**
     * The chord and ear games' lines with fixed words (Chords > Play, Ear training): the prompts, the Major or minor
     * verdicts, the wrong keys that name no chord tone, and the scale-degree game's keys outside the key. Build it's
     * "D is not in C major" (504 of them) and the interval answers that name both notes stay generated on the phone.
     */
    private fun gameLines(): List<String> = buildList {
        add(ChordGame(GameKind.HEAR, 0, seed = 0).prompt().spoken)
        for (level in GameKind.MAJOR_MINOR.levels.indices) add(ChordGame(GameKind.MAJOR_MINOR, level, seed = 0).prompt().spoken)
        add(IntervalGame(0, seed = 0).prompt().spoken)
        for (n in listOf(3, 5)) add("Play it back: $n notes.")
        add("Not quite. From the start."); add("Not quite. Here are the notes, from the start.")
        add("Those notes are not a major or minor chord. Try again."); add("Those notes are not a seven or major seven chord. Try again.")
        val why = listOf(
            Triple(ChordType.MAJOR, ChordType.MINOR, "the middle note is a half step higher than in"),
            Triple(ChordType.MINOR, ChordType.MAJOR, "the middle note is a half step lower than in"),
            Triple(ChordType.DOM7, ChordType.MAJ7, "the top note is a half step lower than in"),
            Triple(ChordType.MAJ7, ChordType.DOM7, "the top note is a half step higher than in"))
        for (root in 0..11) for ((type, other, words) in why) for (lead in listOf("Yes. That was", "That was"))
            add("$lead ${chordSpoken(Chord(root, type))}: $words ${chordSpoken(Chord(root, other))}.")
        for (pc in 0..11) { add("${spokenPitch(pc)} is not in the chord."); add("${spokenPitch(pc)} is not in the chord. Here are the notes.") }
        for (p in (-12..12) - 0) for (shown in listOf("", " Here is the note."))
            add("That was ${withArticle(intervalName(p))} ${if (p > 0) "up" else "down"}.$shown")
        for (dir in listOf("up", "down")) for (shown in listOf("", " Here is the note.")) add("That was more than an octave $dir.$shown")
        val keys = (0..2).flatMap { com.bigpapituna.sharpears.engine.ear.ScaleDegreeGame.keysFor(it) }.distinct()
        for ((tonic, minor) in keys) for (pc in 0..11) {
            val scale = if (minor) com.bigpapituna.sharpears.engine.ear.MINOR_SCALE else com.bigpapituna.sharpears.engine.ear.MAJOR_SCALE
            if (Math.floorMod(pc - tonic, 12) in scale) continue
            val line = "${com.bigpapituna.sharpears.engine.ear.spellSpoken(pc, com.bigpapituna.sharpears.engine.ear.flatKey(tonic, minor))} is not in ${keySpoken(tonic, minor)}."
            add(line); add("$line Here is the note.")
        }
    }

    /** How fast the kid lines are recorded (KIDS-PLAN 3.8: the pup speaks slower to kids). */
    const val KID_SPEED = 0.85

    /**
     * Ear training lines whose words are fixed by the game (engine/ear): the prompts, and the answers that name a note,
     * a step of the scale or a progression rather than a timing. Rhythm verdicts with numbers past "spot on" stay
     * generated on the phone.
     */
    private fun earLines(): List<String> = buildList {
        add("Which note is it?"); add("Which step of the scale is this?"); add("Which progression is this?"); add("Listen, then tap the rhythm.")
        add("Yes, first time."); add("Yes.")
        // Melody memory: the keys each level draws from, and the melody's length as it grows over ten rounds.
        val melodyKeys = mapOf(
            (0 to false) to (2..12), (7 to false) to (3..12), (5 to false) to (3..12), (2 to false) to (3..12), (10 to false) to (3..12),
            (9 to true) to (3..12), (4 to true) to (3..12), (2 to true) to (3..12), (0 to true) to (3..12))
        for ((key, lengths) in melodyKeys) for (n in lengths) add("Play it back: $n notes, in ${keySpoken(key.first, key.second)}.")
        // Perfect pitch: right, a wrong key (with the note shown on the second), a wrong button.
        for (pc in 0..11) {
            val p = pitchSpoken(pc)
            add("Yes, $p.")
            add("That was $p, not the note you heard."); add("That was $p, not the note you heard. Here is the note.")
            for (other in 0..11) if (other != pc) add("That was $p, not ${pitchSpoken(other)}.")
        }
        // Scale degrees in major and minor: right, a wrong key, a wrong button (with what the right step feels like).
        val feels = ::degreeFeel
        for (minor in listOf(false, true)) for (d in 0..6) {
            val s = solfegeSpoken(solfege(d, minor))
            add("Yes, $s, step ${d + 1}.")
            add("That was $s, step ${d + 1}."); add("That was $s, step ${d + 1}. Here is the note.")
            for (o in 0..6) if (o != d) {
                val said = solfegeSpoken(solfege(o, minor))
                add("That was $s, step ${d + 1}, not $said. ${feels(d, minor)}")
            }
        }
        // Progressions: right, or the one it was and the one picked (same mode).
        val cat = PROGRESSION_CATALOG
        for (t in cat) {
            add("Yes, ${t.nickname}. ${t.spokenBlurb}")
            cat.filter { it.minor == t.minor && it.id != t.id }.forEach { add("That was ${t.nickname}. ${t.spokenBlurb} You picked ${it.nickname}.") }
        }
        // Rhythm tap: a clean round.
        for (n in 1..16) add("Spot on: all $n notes within 80 milliseconds.")
        // Game scores out of ten are in the lesson block above.
    }
}
