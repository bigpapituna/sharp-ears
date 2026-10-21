package com.bigpapituna.sharpears.engine.brief

import com.bigpapituna.sharpears.engine.Hand
import com.bigpapituna.sharpears.engine.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunTallyTest {
    @Test fun wrongKeysAreNearOrFarByTheNoteWanted() {
        val t = RunTally()
        t.wrong(62, 60)      // two away: a slip
        t.wrong(61, 60)
        t.wrong(65, 60)      // a fourth: far
        t.wrong(70, null)    // nothing wanted: far, no pair
        assertEquals(2, t.near); assertEquals(2, t.far); assertEquals(4, t.wrongKeys)
        assertEquals(listOf(BriefEvent.WrongKey(5, 60, 61, true, 1, "pond-1"), BriefEvent.WrongKey(5, 60, 62, true, 1, "pond-1"),
            BriefEvent.WrongKey(5, 60, 65, false, 1, "pond-1")), t.wrongKeyEvents(5, lessonId = "pond-1"))
    }

    @Test fun everyPairIsWrittenUpToTheCapMostFrequentFirst() {
        val t = RunTally()
        repeat(4) { t.wrong(64, 62) }
        for (k in 66..72) t.wrong(k, 67)
        assertEquals("all eight pairs of an ordinary run", 8, t.wrongKeyEvents(9, songId = "ode").size)
        for (k in 30..59) t.wrong(k, 60)                        // thirty more pairs: a very rough run
        val events = t.wrongKeyEvents(9, songId = "ode")
        assertEquals(24, RunTally.TOP_PAIRS)
        assertEquals(RunTally.TOP_PAIRS, events.size)
        assertEquals(BriefEvent.WrongKey(9, 62, 64, true, 4, songId = "ode"), events.first())
    }

    @Test fun eachStuckWaitCountsItsDeepestHint() {
        val t = RunTally()
        t.hint(HintDepth.GLOW); t.hint(HintDepth.POINT); t.waitEnded()     // pointed, then played
        t.waitEnded()                                                     // a wait with no hint
        t.hint(HintDepth.GLOW); t.hint(HintDepth.POINT); t.hint(HintDepth.PLAY); t.waitEnded()
        t.hint(HintDepth.GLOW); t.waitEnded()
        assertEquals(listOf(2, 3, 1), t.hintsUsed)
        repeat(100) { t.hint(1); t.waitEnded() }
        assertEquals(RunTally.MAX_HINTS, t.hintsUsed.size)
    }

    @Test fun triesAddUpAndAClearStartsOver() {
        val a = RunTally().apply { wrong(62, 60); hint(3); waitEnded(); octave(); letGo() }
        val b = RunTally().apply { wrong(62, 60); wrong(50, 60); octave() }
        val sum = a.plus(b)
        assertEquals(2, sum.near); assertEquals(1, sum.far); assertEquals(2, sum.octaves); assertEquals(1, sum.letGos)
        assertEquals(listOf(3), sum.hintsUsed)
        assertEquals(2, sum.wrongKeyEvents(0).first { it.playedMidi == 62 }.count)
        assertEquals(BriefEvent.OctaveHint(0, "x", null, 2), sum.octaveEvent(0, "x"))
        val copy = a.copy(); a.clear()
        assertEquals(0, a.wrongKeys); assertNull(a.octaveEvent(0)); assertEquals(1, copy.wrongKeys)
    }

    private fun n(midi: Int, hand: Hand = Hand.RIGHT) = Note(midi, 0, 500, hand, 80)

    @Test fun eachNoteIsTimedFromDueToHeardAndCountedCleanForItsHand() {
        val t = RunTally()
        t.due(listOf(n(60), n(64), n(48, Hand.LEFT)), anyOf = false, atMs = 1_000.0)
        t.heard(60, 1_400.0)                    // 400 ms
        t.wrong(65, 64)                         // a slip charged to E4: not clean
        t.heard(64, 2_200.0)                    // 1200 ms
        t.heard(48, 3_000.0)                    // 2000 ms, left hand
        t.heard(48, 9_000.0)                    // a key heard twice counts once
        t.groupDone()
        t.due(listOf(n(62)), anyOf = false, atMs = 5_000.0)
        t.heard(62, 4_900.0)                    // struck just before it was due: no wait
        t.groupDone()
        val d = t.detail()
        assertEquals(4, t.notesDue); assertEquals(0, d.skippedNotes)
        assertEquals((400 + 1200 + 2000 + 0) / 4, d.hesitationMs); assertEquals(4, d.hesitationN)
        assertEquals(HandNotes(due = 3, clean = 2, hesMs = 533, hesN = 3), d.right)
        assertEquals(HandNotes(due = 1, clean = 1, hesMs = 2000, hesN = 1), d.left)
        assertEquals(listOf(KeyMs(48, 2000), KeyMs(64, 1200), KeyMs(60, 400), KeyMs(62, 0)), d.slowKeys)
        assertNull("no Perform notes, no spread", d.spreadMs)
    }

    @Test fun aGroupLetGoOrShortOfAChordNoteCountsItsNotesAsNeverPlayed() {
        val t = RunTally()
        t.due(listOf(n(60), n(64)), anyOf = false, atMs = 0.0)
        t.heard(60, 500.0)
        t.letGo()                               // the Playground lets E4 go...
        t.heard(64, 600.0)                      // ...and feeds it to the transport: not a hesitation
        t.groupDone()
        assertEquals(1, t.skipped); assertEquals(1, t.letGos); assertEquals(1, t.detail().hesitationN)
        assertEquals(HandNotes(due = 2, clean = 1, hesMs = 500, hesN = 1), t.detail().right)
        t.due(listOf(n(60), n(64), n(67), n(71)), anyOf = false, atMs = 1_000.0)
        listOf(60, 64, 67).forEach { t.heard(it, 1_100.0) }
        t.groupDone()                           // the mic's slack passed the chord without B4
        assertEquals(2, t.skipped)
        assertEquals(6, t.notesDue)
    }

    @Test fun anAnyOfGroupIsOneNoteAndAWaitIsCappedAtTwentySeconds() {
        val t = RunTally()
        t.due((60..72).map { n(it) }, anyOf = true, atMs = 0.0)
        t.wrong(40, 60)                         // far below the range: charged to the group
        t.heard(67, 90_000.0)
        t.groupDone()
        val d = t.detail()
        assertEquals(HandNotes(due = 1, clean = 0, hesMs = RunTally.MAX_HESITATION_MS, hesN = 1), d.right)
        assertEquals("an any-of key is no key's own wait", emptyList<KeyMs>(), d.slowKeys)
    }

    @Test fun performGroupsCountForTheirHandsAndTheirOffsetsGiveTheSpread() {
        val t = RunTally()
        for (off in listOf(-40.0, 40.0, -40.0, 40.0)) t.performHit(listOf(n(60), n(48, Hand.LEFT)), anyOf = false, offsetMs = off + 20)
        t.performMiss(listOf(n(62)), anyOf = false)
        val d = t.detail()
        assertEquals(40, d.spreadMs)            // the lean (20 ms late) is timing, not steadiness
        assertEquals(1, d.skippedNotes)
        assertEquals(HandNotes(due = 5, clean = 4), d.right); assertEquals(HandNotes(due = 4, clean = 4), d.left)
        assertNull("three graded notes are too few", RunTally().apply { repeat(3) { performHit(listOf(n(60)), false, 10.0) } }.spreadMs)
    }

    @Test fun hintCountsAreWrittenOnlyWhenTheListWasCutShort() {
        val t = RunTally()
        t.hint(HintDepth.PLAY); t.waitEnded(); t.hint(HintDepth.GLOW); t.waitEnded()
        assertEquals(emptyList<Int>(), t.detail().hintSteps)
        repeat(40) { t.hint(HintDepth.POINT); t.waitEnded() }
        assertEquals(listOf(1, 40, 1), t.detail().hintSteps)
        assertEquals(RunTally.MAX_HINTS, t.hintsUsed.size)
    }

    @Test fun triesAddTheirNotesUpAndAPendingGroupIsNotCarried() {
        val a = RunTally().apply { due(listOf(n(60)), false, 0.0); heard(60, 300.0); groupDone(); due(listOf(n(62)), false, 400.0) }
        val b = RunTally().apply { due(listOf(n(60)), false, 0.0); heard(60, 900.0); groupDone(); performHit(listOf(n(64)), false, 5.0) }
        val sum = a.plus(b).detail()
        assertEquals(600, sum.hesitationMs); assertEquals(2, sum.hesitationN)
        assertEquals(listOf(KeyMs(60, 900)), sum.slowKeys)
        assertEquals(HandNotes(due = 3, clean = 3, hesMs = 600, hesN = 2), sum.right)
        a.clear()
        assertEquals(RunDetail(), a.detail())
    }
}
