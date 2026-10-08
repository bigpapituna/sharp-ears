package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunRecordingTest {
    @Test fun aRunSurvivesTheRoundTripThroughJson() {
        val run = RunRecording("fur-elise", listOf(
            PlayedNote(76, 0, 280, 96), PlayedNote(75, 310, 250, 88), PlayedNote(60, 1500, 350, 80), PlayedNote(64, -120, 90, 1),
        ), speed = 0.75)
        val json = run.toJson()
        assertTrue(json.startsWith("{\"songId\":\"fur-elise\""))
        assertEquals(run, RunRecording.fromJson(json))
        assertEquals(RunRecording("x", emptyList()), RunRecording.fromJson(RunRecording("x", emptyList()).toJson()))
    }

    @Test fun theRecorderTimesEachKeyFromStrikeToRelease() {
        val r = RunRecorder()
        r.on(60, 1000.4, 100)
        r.on(64, 1010.0, 90)
        r.off(60, 1400.0)
        r.on(67, 2000.0, 80)                                       // never released (a mic strike)
        r.off(62, 2100.0)                                          // a release with no strike: ignored
        val run = r.take("t")
        assertEquals(listOf(PlayedNote(60, 1000, 400, 100), PlayedNote(64, 1010, RunRecording.DEFAULT_MS, 90),
            PlayedNote(67, 2000, RunRecording.DEFAULT_MS, 80)), run.notes)
        assertEquals(listOf(1000L, 1010L, 2000L), run.asNotes().map { it.startMs })
    }

    @Test fun miniJsonReadsWhatItWrites() {
        val m = linkedMapOf<String, Any?>("s" to "a \"quoted\" \\ line\nnext", "i" to 42L, "neg" to -7L, "f" to 1.5, "b" to true, "n" to null,
            "list" to listOf(1L, "two", false), "nested" to mapOf("k" to "v"))
        assertEquals(m, MiniJson.read(MiniJson.write(m)))
        assertEquals(mapOf("a" to listOf(1L, 2L)), MiniJson.read(" { \"a\" : [ 1 , 2 ] } "))
    }
}
