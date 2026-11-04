package com.bytegrep.oplist_manager.data.player

/** 播放地址来源 */
enum class PlaybackSourceKind {
    /** 由 OpenList 服务器（/d、/p）提供，可能 302 到存储 */
    OPENLIST,

    /** fs/get 返回的第三方存储直链（或下载代理） */
    DIRECT,

    /** 管理员通过 fs/link 获取的驱动直链，附带驱动要求的请求头 */
    DRIVER_LINK,
}

/**
 * 播放器所需的全部播放信息，由 [PlaybackSourceResolver] 生成。
 * [headers] 只能发送给 [url] 的同源地址，绝不包含 OpenList Token。
 */
data class PlaybackSource(
    val path: String,
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    /** 显式 MIME（HLS/DASH 必须指定），null 表示交给播放器自动识别 */
    val mimeType: String? = null,
    val kind: PlaybackSourceKind,
    val size: Long = 0,
    /** 外挂字幕（同目录同名前缀文件，经 OpenList /p 代理加载） */
    val subtitles: List<SubtitleSource> = emptyList(),
)

/** 外挂字幕文件 */
data class SubtitleSource(
    val url: String,
    val name: String,
    val mimeType: String,
)

/** 解析播放源所需的会话环境 */
data class PlaybackEnv(
    val serverBaseUrl: String,
    /** 服务器配置 ID，用于区分不同服务器的播放位置 */
    val serverId: String = "",
    val isAdmin: Boolean,
    val basePath: String = "/",
)
