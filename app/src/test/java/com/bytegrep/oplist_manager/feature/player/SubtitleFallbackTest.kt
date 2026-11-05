package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleFallbackTest {

    private val subtitleUrl = "https://example.com/p/movie.vtt"
    private val videoUrl = "https://example.com/p/movie.mp4"

    private fun shouldRetry(
        errorCode: Int,
        failedHttpUri: String? = null,
        subtitleUrls: List<String> = listOf(subtitleUrl),
        itemReady: Boolean = false,
        alreadyDropped: Boolean = false,
    ) = SubtitleFallback.shouldRetryWithoutSubtitles(errorCode, failedHttpUri, subtitleUrls, itemReady, alreadyDropped)

    @Test
    fun `http failure on subtitle url retries without subtitles`() {
        assertTrue(shouldRetry(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, failedHttpUri = subtitleUrl))
    }

    @Test
    fun `http failure on video url is not treated as subtitle failure`() {
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, failedHttpUri = videoUrl))
    }

    @Test
    fun `malformed subtitle content during preparation retries without subtitles`() {
        // 字幕 HTTP 200 但 WebVTT 头不合法：错误链中没有请求地址，只有容器解析错误
        assertTrue(shouldRetry(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertTrue(shouldRetry(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }

    @Test
    fun `parsing error after subtitles dropped falls through to normal error`() {
        // 去掉字幕后仍解析失败，说明视频本身有问题，不再重试
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, alreadyDropped = true))
    }

    @Test
    fun `errors unrelated to subtitles are not retried`() {
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, subtitleUrls = emptyList()))
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, itemReady = true))
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertFalse(shouldRetry(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
    }
}
