package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.family.ProfileKind
import java.util.Locale

/*
 * "Ask the brief" (docs/PARENT-BRIEF-PLAN.md): a grown-up types or dictates a question and the writer answers it from the same
 * findings. Pure state for the screen: the short history of question and answer pairs for the open brief, the cleaning of a
 * question, the dictation text rules and the starter questions. The question leaves the phone only through
 * BriefWriter.answer; dictation is Android's own speech recognition, which the screen asks to stay on the phone.
 */

/** One question and what became of it. [answer] is null while the writer is thinking and when it could not answer ([failed]). */
data class Exchange(val id: Int, val question: String, val answer: String? = null, val failed: Boolean = false) {
    val pending: Boolean get() = answer == null && !failed
}

/**
 * The exchanges of one open brief, newest last, at most [MAX]: an older one drops off when a new one comes. Immutable, so a
 * Compose state can hold it.
 */
data class AskHistory(val exchanges: List<Exchange> = emptyList(), private val nextId: Int = 1) {
    /** The question [text] cleaned and added as pending; null (and no change) when it is blank. Returns the new history and the id. */
    fun ask(text: String): Pair<AskHistory, Int>? {
        val q = AskRules.clean(text) ?: return null
        val e = Exchange(nextId, q)
        return AskHistory((exchanges + e).takeLast(MAX), nextId + 1) to e.id
    }

    fun answered(id: Int, text: String): AskHistory = replace(id) { it.copy(answer = text.trim().ifEmpty { null }, failed = text.isBlank()) }

    fun failed(id: Int): AskHistory = replace(id) { it.copy(answer = null, failed = true) }

    /** True while the writer is working on one: the screen does not take a second question yet. */
    val busy: Boolean get() = exchanges.any { it.pending }

    private fun replace(id: Int, f: (Exchange) -> Exchange) = copy(exchanges = exchanges.map { if (it.id == id) f(it) else it })

    companion object { const val MAX = 8 }
}

object AskRules {
    const val MAX_QUESTION = 300

    /** The question as it will be sent: trimmed, single spaces, at most [MAX_QUESTION] characters; null when there is nothing in it. */
    fun clean(text: String): String? =
        text.trim().replace(Regex("\\s+"), " ").take(MAX_QUESTION).trim().takeIf { it.isNotEmpty() }

    /**
     * What the writer is handed: the person's first name swapped for "my child" (a grown-up) or "the learner" so a typed name
     * never reaches a model, as the plan promises. Whole words only, any case.
     */
    fun forWriter(question: String, name: String, kind: ProfileKind): String {
        val alias = if (kind == ProfileKind.KID) "my child" else "the learner"
        val names = name.trim().split(Regex("\\s+")).filter { it.length >= 2 }
        if (names.isEmpty()) return question
        val pattern = Regex("(?<![\\p{L}\\p{N}])(" + names.joinToString("|") { Regex.escape(it) } + ")(?:'s|’s)?(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return pattern.replace(question) { m -> if (m.value.endsWith("s") && m.value.length > m.groupValues[1].length) "$alias's" else alias }
    }

    /** What a screen says when the writer could not answer. */
    const val COULD_NOT = "I could not answer that one just now. Try asking it another way."

    /** Starter questions under the box, from what the findings hold (never a name, never a lesson title). At most three. */
    fun starters(f: Findings): List<String> = buildList {
        val kinds = f.flags.map { it.kind }
        if (FlagKind.STUCK_LESSON in kinds || FlagKind.STUCK_SONG in kinds) add("Why is one thing taking so long?")
        if (FlagKind.NOISY_ROOM in kinds) add("Is the microphone hearing the room?")
        if (FlagKind.PRACTICE_DIPPED in kinds) add("Is this a normal amount of practice?")
        if (FlagKind.HINT_DEPENDENCE in kinds) add("Is it bad that the app plays the key?")
        add("What went well?")
        // "My week" is a grown-up asking about themselves: I, not we or they.
        if (f.profileKind == ProfileKind.ADULT) { add("What should I do next?"); add("How much should I practise?") }
        else { add("What should we do next?"); add("How much should they practise?") }
    }.distinct().take(3)
}

/** Dictation: what the speech recogniser hears becomes text in the question field. */
object DictationText {
    /** [heard] added to what is already typed: one space between them, the first word capitalised at the start of a sentence. */
    fun append(existing: String, heard: String): String {
        val h = heard.trim()
        if (h.isEmpty()) return existing
        val e = existing.trimEnd()
        if (e.isEmpty()) return h.replaceFirstChar { it.titlecase(Locale.getDefault()) }
        val sentenceEnd = e.last() in ".?!"
        return e + " " + if (sentenceEnd) h.replaceFirstChar { it.titlecase(Locale.getDefault()) } else h
    }

    /** What to tell the grown-up for a SpeechRecognizer error code (android.speech.SpeechRecognizer.ERROR_*). Null: nothing to say (they stopped it). */
    fun errorMessage(code: Int): String? = when (code) {
        5 -> null                                                     // ERROR_CLIENT: stopped by us
        7, 6 -> "I did not catch that. Tap the microphone and try again."       // NO_MATCH, SPEECH_TIMEOUT
        9 -> "Allow the microphone to talk instead of typing."        // INSUFFICIENT_PERMISSIONS
        8 -> "The microphone is busy. Try again in a moment."         // RECOGNIZER_BUSY
        1, 2, 3, 4 -> "Speech is not working on this phone just now. You can type instead."   // NETWORK_TIMEOUT, NETWORK, AUDIO, SERVER
        10, 11, 12, 13 -> "Speech is not available on this phone. You can type instead."     // TOO_MANY, DISCONNECTED, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE
        else -> "Speech is not working just now. You can type instead."
    }
}
