package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/** 面向用户的播放错误；[recoverable] 表示重新获取播放地址后可能恢复（链接过期、IP 变化等） */
data class PlayerError(
    val message: String,
    val recoverable: Boolean,
)

object PlaybackErrorMapper {

    private const val HTTP_UNAUTHORIZED = 401
    private const val HTTP_FORBIDDEN = 403
    private const val HTTP_NOT_FOUND = 404
    private const val HTTP_GONE = 410

    fun map(error: PlaybackException): PlayerError {
        val status = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        return map(error.errorCode, status)
    }

    fun map(errorCode: Int, httpStatus: Int?): PlayerError = when (errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> httpError(httpStatus)
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlayerError("网络连接失败，请检查网络后重试", recoverable = true)
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ->
            PlayerError("系统不允许以明文 HTTP 访问该地址", recoverable = false)
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> PlayerError("文件不存在或已被移动", recoverable = false)
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
            PlayerError("服务器不支持从该位置读取，请从头播放", recoverable = false)
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        -> PlayerError("不支持的视频格式或文件已损坏，可尝试外部播放器", recoverable = false)
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> PlayerError("设备不支持该视频或音频编码，可尝试外部播放器", recoverable = false)
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        -> PlayerError("音频输出失败，设备可能不支持该音频编码", recoverable = false)
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> PlayerError("直播进度已过期，请重试", recoverable = true)
        else -> if (errorCode in IO_RANGE) {
            PlayerError("读取视频数据失败，请重试", recoverable = true)
        } else {
            PlayerError("播放失败（错误码 $errorCode）", recoverable = false)
        }
    }

    private fun httpError(status: Int?): PlayerError = when (status) {
        HTTP_UNAUTHORIZED, HTTP_GONE -> PlayerError("播放链接已失效", recoverable = true)
        HTTP_FORBIDDEN -> PlayerError(
            "存储拒绝访问（403）。该存储可能需要在 OpenList 中开启「Web 代理」后才能在线播放",
            recoverable = true,
        )
        HTTP_NOT_FOUND -> PlayerError("文件不存在（404）", recoverable = false)
        null -> PlayerError("服务器返回错误", recoverable = true)
        else -> PlayerError("服务器返回错误（HTTP $status）", recoverable = status >= 500)
    }

    /** IO 类错误码区间 2000–2999 */
    private val IO_RANGE = 2000..2999
}
