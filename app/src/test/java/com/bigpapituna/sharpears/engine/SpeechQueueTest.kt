package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechQueueTest {
    @Test fun speaksAtOnceWhenTheMicIsOff() {
        val q = SpeechQueue()
        assertEquals(listOf("Play C"), q.offer("Play C", 0, canSpeak = true))
        assertEquals(0, q.size)
    }

    @Test fun holdsLinesWhileTheMicListensAndReleasesThemInOrder() {
        val q = SpeechQueue()
        assertEquals(emptyList<String>(), q.offer("Clean.", 0, canSpeak = false))
        assertEquals(emptyList<String>(), q.offer("Next step.", 100, canSpeak = false))
        assertEquals(listOf("Clean.", "Next step."), q.release(500))
        assertEquals(emptyList<String>(), q.release(600))
    }

    @Test fun heldLinesComeOutBeforeANewOneOnceSpeakingIsAllowed() {
        val q = SpeechQueue()
        q.offer("Clean.", 0, canSpeak = false)
        assertEquals(listOf("Clean.", "Step two."), q.offer("Step two.", 10, canSpeak = true))
    }

    @Test fun staleLinesAreDroppedAndTheQueueIsBounded() {
        val q = SpeechQueue(maxAgeMs = 1000, max = 2)
        q.offer("old", 0, canSpeak = false)
        q.offer("a", 5000, canSpeak = false)
        q.offer("b", 5100, canSpeak = false)
        q.offer("c", 5200, canSpeak = false)
        assertEquals(listOf("b", "c"), q.release(5500))
        q.offer("stale", 0, canSpeak = false)
        assertEquals(emptyList<String>(), q.release(2000))
    }

    @Test fun interruptReplacesAndNonDeferrableLinesAreDropped() {
        val q = SpeechQueue()
        q.offer("a", 0, canSpeak = false)
        q.offer("b", 0, canSpeak = false, interrupt = true)
        q.offer("F sharp is not in D major", 0, canSpeak = false, deferrable = false)
        assertEquals(listOf("b"), q.release(10))
    }
}
