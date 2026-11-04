package com.bytegrep.oplist_manager.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGesturesTest {

    @Test
    fun `horizontal drag seeks, vertical drag depends on side`() {
        assertEquals(DragGesture.SEEK, PlayerGestures.classify(dx = 10f, dy = 2f, startX = 100f, width = 1000f))
        assertEquals(DragGesture.BRIGHTNESS, PlayerGestures.classify(dx = 1f, dy = -8f, startX = 100f, width = 1000f))
        assertEquals(DragGesture.VOLUME, PlayerGestures.classify(dx = 1f, dy = 8f, startX = 900f, width = 1000f))
    }

    @Test
    fun `seek target scales with width and clamps`() {
        assertEquals(70_000, PlayerGestures.seekTarget(10_000, 0.5f, 600_000))
        assertEquals(0, PlayerGestures.seekTarget(10_000, -1f, 600_000))
        assertEquals(600_000, PlayerGestures.seekTarget(590_000, 1f, 600_000))
        assertEquals(130_000, PlayerGestures.seekTarget(10_000, 1f, 0))
    }

    @Test
    fun `level increases when swiping up and clamps`() {
        assertEquals(0.75f, PlayerGestures.adjustLevel(0.5f, -0.25f), 0.0001f)
        assertEquals(1f, PlayerGestures.adjustLevel(0.9f, -0.5f), 0.0001f)
        assertEquals(0f, PlayerGestures.adjustLevel(0.1f, 0.5f), 0.0001f)
    }

    @Test
    fun `speed labels`() {
        assertEquals("1.0x", speedLabel(1f))
        assertEquals("2.0x", speedLabel(2f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals("0.5x", speedLabel(0.5f))
    }
}
