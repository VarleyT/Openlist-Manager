package com.bytegrep.oplist_manager.feature.player

/** 屏幕方向偏好 */
enum class PlayerOrientation { AUTO, LANDSCAPE, PORTRAIT }

/** 播放页不可变 UI 状态 */
data class VideoPlayerState(
    val title: String = "",
    /** 正在通过 OpenList 解析播放地址 */
    val isResolving: Boolean = true,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = true,
    val isEnded: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val isSeekable: Boolean = false,
    /** 视频宽高比（宽/高），未知时为 null */
    val videoAspectRatio: Float? = null,
    val error: PlayerError? = null,
    /** 所有音轨均无法解码，提示用户当前无声 */
    val audioUnsupported: Boolean = false,
    val controlsVisible: Boolean = true,
    /** 锁定后屏蔽所有触摸操作与方向变化，仅保留解锁按钮 */
    val isLocked: Boolean = false,
    val orientation: PlayerOrientation = PlayerOrientation.AUTO,
    val speed: Float = 1f,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    /** 从记忆位置续播时的提示（如「已从 01:10:25 继续播放」），null 不显示 */
    val resumeHint: String? = null,
) {
    val showLoading: Boolean get() = error == null && (isResolving || isBuffering)
}
