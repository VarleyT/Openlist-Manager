package com.bytegrep.oplist_manager.feature.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerStateTest {

    @Test
    fun `initial state is resolving with visible controls and auto orientation`() {
        val state = VideoPlayerState()

        assertTrue(state.isResolving)
        assertTrue(state.playWhenReady)
        assertTrue(state.controlsVisible)
        assertEquals(PlayerOrientation.AUTO, state.orientation)
        assertEquals(1f, state.speed)
        assertNull(state.error)
        assertTrue(state.showLoading)
    }

    @Test
    fun `loading shows while resolving or buffering`() {
        val idle = VideoPlayerState(isResolving = false)

        assertFalse(idle.showLoading)
        assertTrue(idle.copy(isBuffering = true).showLoading)
        assertTrue(idle.copy(isResolving = true).showLoading)
    }

    @Test
    fun `error hides loading indicator even while buffering`() {
        val state = VideoPlayerState(
            isResolving = true,
            isBuffering = true,
            error = PlayerError("网络连接失败", recoverable = true),
        )

        assertFalse(state.showLoading)
    }
}
