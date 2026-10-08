package com.bigpapituna.sharpears.engine

import java.security.MessageDigest

/**
 * Every line the voice can say that is known ahead of time. scripts/render_voice.py records each one with the
 * natural (Kokoro) voice into assets/voice/<key>.ogg; at run time a line found there plays instantly, and anything
 * else (feedback that names notes, times and scores) is generated on the phone.
 */
object SpokenCatalog {
    /** The same text the voice is handed, tidied: single spaces, trimmed. */
    fun normalize(text: String) = text.replace(Regex("\\s+"), " ").trim()

    /** File key for a line: the first 16 hex digits of the SHA-1 of its normalised text. */
    fun key(text: String): String = MessageDigest.getInstance("SHA-1").digest(normalize(text).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }.take(16)

    fun all(): List<String> {
        val lines = linkedSetOf<String>()
        fun add(s: String) { lines += normalize(s) }

        // Lessons: each step as LessonScreen speaks it, the end of a lesson, and the fixed feedback lines.
        for (lesson in COURSE.flatMap { it.lessons }) {
            lesson.steps.forEach { add(it.text.replace("\n\n", " ")) }
            add("Next: the lesson ${lesson.title}")
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
        // The sample line Settings plays when a voice is chosen.
        add("Ready when you are. Play the first note.")
        return lines.toList()
    }
}
