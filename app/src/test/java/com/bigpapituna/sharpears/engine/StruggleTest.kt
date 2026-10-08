package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StruggleTest {
    private fun cheers(s: Struggle, n: Int) = (1..n).count { s.right() }

    @Test fun cleanPlayingNeverCheers() = assertEquals(0, cheers(Struggle(), 50))

    @Test fun cheersOnceAfterAStruggle() {
        val s = Struggle()
        listOf(0L, 1000, 2000).forEach(s::mistake)
        assertEquals(0, cheers(s, 7))
        assertEquals(1, cheers(s, 20))          // the 8th right note, and only once
    }

    @Test fun spreadOutMistakesAreNotAStruggle() {
        val s = Struggle()
        listOf(0L, 9000, 18000).forEach(s::mistake)
        assertEquals(0, cheers(s, 20))
    }

    @Test fun aMistakeRestartsTheCleanStretch() {
        val s = Struggle()
        listOf(0L, 1000, 2000).forEach(s::mistake)
        cheers(s, 7); s.mistake(3000)
        assertEquals(0, cheers(s, 7))
        assertEquals(1, cheers(s, 1))
    }
}
