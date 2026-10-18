package com.bigpapituna.sharpears.engine.play

import com.bigpapituna.sharpears.engine.MicEvent
import com.bigpapituna.sharpears.engine.NoteMatcher

/**
 * Runs audio through the note matcher the way MicInput does (512-sample chunks), with a hook per chunk so a test can
 * also feed the chord ear, change what is expected, or answer strikes as they come.
 */
internal object MicRig {
    fun run(
        audio: FloatArray, matcher: NoteMatcher,
        onChunk: (chunk: FloatArray, n: Int, at: Long) -> Unit = { _, _, _ -> },
        onEvent: (MicEvent) -> Unit,
    ) {
        var i = 0
        val buf = FloatArray(512)
        while (i < audio.size) {
            val n = minOf(512, audio.size - i)
            System.arraycopy(audio, i, buf, 0, n)
            onChunk(buf, n, matcher.samplesProcessed)
            matcher.process(buf, n).forEach(onEvent)
            i += n
        }
    }

    /** The strikes (not inferred octaves) heard in [audio] with [expected] keys, each in its own group. */
    fun strikes(audio: FloatArray, expected: Set<Int>, groups: List<Set<Int>> = expected.map { setOf(it) }): List<MicEvent.Strike> {
        val m = NoteMatcher()
        m.expected = expected; m.groups = groups
        val out = ArrayList<MicEvent.Strike>()
        run(audio, m) { if (it is MicEvent.Strike && it.inferredFrom == null) out += it }
        return out
    }
}
