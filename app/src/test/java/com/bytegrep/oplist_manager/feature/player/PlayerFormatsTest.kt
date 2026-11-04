package com.bytegrep.oplist_manager.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFormatsTest {

    @Test
    fun `common containers open in built in player`() {
        listOf("a.mp4", "/x/B.MKV", "c.webm", "d.m3u8", "e.mpd", "f.mov").forEach {
            assertTrue(it, isOnlinePlayableVideo(it))
        }
    }

    @Test
    fun `unsupported or non video stays in preview`() {
        listOf("a.avi", "b.rmvb", "c.flv", "d.txt", "noext").forEach {
            assertFalse(it, isOnlinePlayableVideo(it))
        }
    }

    @Test
    fun `duration formatting`() {
        assertEquals("00:00", formatDuration(-5))
        assertEquals("01:05", formatDuration(65_000))
        assertEquals("1:00:01", formatDuration(3_601_000))
    }
}
