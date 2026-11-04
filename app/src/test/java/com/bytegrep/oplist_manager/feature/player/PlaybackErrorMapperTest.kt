package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackErrorMapperTest {

    private fun map(code: Int, status: Int? = null) = PlaybackErrorMapper.map(code, status)

    @Test
    fun `expired link statuses are recoverable`() {
        for (status in listOf(401, 403, 410)) {
            assertTrue(map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, status).recoverable)
        }
        assertTrue(map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 403).message.contains("Web 代理"))
    }

    @Test
    fun `not found and client errors are not recoverable`() {
        assertFalse(map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404).recoverable)
        assertFalse(map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 400).recoverable)
        assertTrue(map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 502).recoverable)
    }

    @Test
    fun `network errors are recoverable`() {
        assertTrue(map(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).recoverable)
        assertTrue(map(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT).recoverable)
        assertTrue(map(PlaybackException.ERROR_CODE_IO_UNSPECIFIED).recoverable)
    }

    @Test
    fun `decoder and format errors suggest external player`() {
        val decoder = map(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        assertFalse(decoder.recoverable)
        assertTrue(decoder.message.contains("外部播放器"))
        assertFalse(map(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).recoverable)
    }

    @Test
    fun `unknown code shows code`() {
        assertEquals("播放失败（错误码 7001）", map(7001).message)
    }
}
