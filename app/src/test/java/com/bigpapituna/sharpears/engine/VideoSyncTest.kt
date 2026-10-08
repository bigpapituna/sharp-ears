package com.bigpapituna.sharpears.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSyncTest {
    @Test fun unsyncedHasNoVideoTime() = assertNull(VideoSync(500, 60_500, null).videoSecFor(1000.0))

    @Test fun oneAnchorKeepsTheSongsRate() {
        val s = VideoSync(500, 60_500, first = 7.17f)
        assertFalse(s.twoPoints)
        assertEquals(1.0, s.rate, 1e-9)
        assertEquals(7.17f, s.videoSecFor(500.0)!!, 1e-4f)
        assertEquals(17.17f, s.videoSecFor(10_500.0)!!, 1e-4f)
        assertEquals("never before the video starts", 0f, s.videoSecFor(-20_000.0)!!, 0f)
    }

    @Test fun twoAnchorsDeriveTheRate() {
        // The video plays the 60 s of notes in 63 s: rate 1.05.
        val s = VideoSync(500, 60_500, first = 10f, last = 73f)
        assertTrue(s.twoPoints)
        assertEquals(1.05, s.rate, 1e-9)
        assertEquals(10f, s.videoSecFor(500.0)!!, 1e-4f)
        assertEquals(73f, s.videoSecFor(60_500.0)!!, 1e-4f)
        assertEquals(41.5f, s.videoSecFor(30_500.0)!!, 1e-4f)
    }

    @Test fun aLastAnchorBeforeTheFirstIsIgnored() {
        val s = VideoSync(500, 60_500, first = 10f, last = 5f)
        assertFalse(s.twoPoints)
        assertEquals(1.0, s.rate, 1e-9)
    }

    @Test fun anImplausibleSecondAnchorIsIgnored() {
        // Tapped twice while the video was paused: 0.11 s of video for 60 s of song.
        val s = VideoSync(500, 60_500, first = 7.5f, last = 7.61f)
        assertFalse(s.twoPoints)
        assertEquals(1.0, s.rate, 1e-9)
    }
}
