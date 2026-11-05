package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.PlaybackException

/**
 * 外挂字幕失败时的降级判断（纯函数，便于测试）：外挂字幕是可选内容，不能因为它导致视频无法播放。
 */
object SubtitleFallback {

    /** 字幕内容损坏（如 WebVTT 头不合法）时 Media3 报的是容器解析错误，错误链中没有请求地址 */
    private val PARSING_CODES = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    )

    /**
     * 是否应去掉外挂字幕后重试一次。
     *
     * - 已经进入过 READY、已去掉过字幕或没有外挂字幕：与字幕无关。
     * - 错误链中带有失败的 HTTP 请求地址：仅当该地址属于外挂字幕时重试。
     * - 准备阶段的解析错误：无法区分是视频还是字幕损坏，先去掉字幕重试；若视频本身损坏，重试后会再次报错并正常展示。
     */
    fun shouldRetryWithoutSubtitles(
        errorCode: Int,
        failedHttpUri: String?,
        subtitleUrls: List<String>,
        itemReady: Boolean,
        subtitlesAlreadyDropped: Boolean,
    ): Boolean {
        if (subtitlesAlreadyDropped || itemReady || subtitleUrls.isEmpty()) return false
        if (failedHttpUri != null) return failedHttpUri in subtitleUrls
        return errorCode in PARSING_CODES
    }
}
