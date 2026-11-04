package com.bytegrep.oplist_manager.feature.player

/**
 * 交给内置播放器的扩展名。其余视频格式（avi、rmvb、flv 等 ExoPlayer 不支持或支持差的）
 * 仍走预览页「外部应用打开」。
 */
private val ONLINE_PLAYABLE_EXT = setOf("mp4", "m4v", "mkv", "webm", "mov", "ts", "3gp", "m3u8", "mpd")

fun isOnlinePlayableVideo(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in ONLINE_PLAYABLE_EXT
