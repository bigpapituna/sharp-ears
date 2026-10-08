package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerKeyTest {
    @Test fun micHeardWhatWasPlayed() {
        val a = AnswerKey()
        a.key(1_000, 48, listened = true); a.mic(1_110, 48)       // the mic is ~100 ms behind the keyboard
        a.key(1_000, 84, listened = true); a.mic(1_050, 84)
        assertEquals(emptyList<AnswerKey.Mismatch>(), a.settle(5_000))
    }

    @Test fun aKeyTheMicNeverReportsIsAMiss() {
        val a = AnswerKey()
        a.key(1_000, 48, listened = true); a.mic(1_100, 48)
        a.key(1_000, 84, listened = true)                           // Roy's C6 over C3
        assertEquals(listOf(AnswerKey.Mismatch(AnswerKey.Kind.MISSED, 84, 1_000)), a.settle(5_000))
    }

    @Test fun aNoteTheMicReportsWithNoKeyIsFalse() {
        val a = AnswerKey()
        a.mic(2_000, 60)
        a.key(2_000, 61, listened = false)                          // a wrong key: not a mic miss, and not C4
        assertEquals(listOf(AnswerKey.Mismatch(AnswerKey.Kind.FALSE, 60, 2_000)), a.settle(5_000))
    }

    @Test fun nothingIsDecidedBeforeItsWindowCloses() {
        val a = AnswerKey()
        a.key(1_000, 48, listened = true)
        assertEquals(emptyList<AnswerKey.Mismatch>(), a.settle(1_500))
        a.mic(1_550, 48)
        assertEquals(emptyList<AnswerKey.Mismatch>(), a.settle(5_000))
    }

    @Test fun aMicStrikeTooLateIsBothAMissAndFalse() {
        val a = AnswerKey()
        a.key(1_000, 50, listened = true); a.mic(2_500, 50)
        assertEquals(listOf(AnswerKey.Kind.MISSED, AnswerKey.Kind.FALSE), a.settle(9_000).map { it.kind })
    }
}
