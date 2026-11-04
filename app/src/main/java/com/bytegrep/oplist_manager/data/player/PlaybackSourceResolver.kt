package com.bytegrep.oplist_manager.data.player

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.core.common.parentPath
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.isSameOrigin
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把 OpenList 文件路径解析为可播放地址：
 * 1. fs/get 取 raw_url；
 * 2. raw_url 指向第三方且当前为管理员时，尝试 fs/link 获取驱动要求的请求头（阿里 Referer、百度 UA 等）；
 * 3. 根据扩展名给出 HLS/DASH 的 MIME。
 */
@Singleton
class PlaybackSourceResolver @Inject constructor(
    private val repository: VideoPlaybackRepository,
) {

    suspend fun resolve(path: String): PlaybackSource = resolve(path, repository.env())

    suspend fun resolve(path: String, env: PlaybackEnv): PlaybackSource {
        val file = repository.get(path)
        if (file.isDir) throw AppException.Unknown("这是一个文件夹，无法播放")
        val raw = parsePlayableUrl(file.rawUrl) ?: throw AppException.Unknown("未获取到可播放的地址")
        val server = ApiClientFactory.normalizeBaseUrl(env.serverBaseUrl)?.toHttpUrlOrNull()
        val base = PlaybackSource(
            path = path,
            name = file.name,
            url = raw.toString(),
            mimeType = mimeTypeHint(file.name),
            kind = if (server != null && server.isSameOrigin(raw)) PlaybackSourceKind.OPENLIST else PlaybackSourceKind.DIRECT,
            size = file.size,
            subtitles = server?.let { subtitlesOf(file.related.orEmpty(), path, it, env.basePath) }.orEmpty(),
        )
        if (base.kind != PlaybackSourceKind.DIRECT || !env.isAdmin) return base
        return driverLinkOrNull(base, env) ?: base
    }

    /** fs/link 失败或不需要请求头时返回 null，回退到 raw_url */
    private suspend fun driverLinkOrNull(base: PlaybackSource, env: PlaybackEnv): PlaybackSource? {
        val link = try {
            repository.link(fullPath(env.basePath, base.path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val headers = sanitizeHeaders(link.header.orEmpty())
        if (headers.isEmpty()) return null
        val url = parsePlayableUrl(link.url) ?: return null
        return base.copy(url = url.toString(), headers = headers, kind = PlaybackSourceKind.DRIVER_LINK)
    }

    companion object {
        private val SUBTITLE_MIME = mapOf(
            "srt" to "application/x-subrip",
            "ass" to "text/x-ssa",
            "ssa" to "text/x-ssa",
            "vtt" to "text/vtt",
        )

        /**
         * 从 fs/get 的 related 中挑出字幕，拼成 OpenList 代理地址 `/p{base_path}{dir}/{name}?sign=`
         * （与官方前端一致；/p 不校验 Authorization，只校验各文件自带的 sign）。
         */
        fun subtitlesOf(related: List<FsObject>, videoPath: String, server: HttpUrl, basePath: String): List<SubtitleSource> {
            val dir = parentPath(videoPath)
            return related.asSequence()
                .filter { !it.isDir }
                .mapNotNull { file ->
                    val mime = SUBTITLE_MIME[file.name.substringAfterLast('.', "").lowercase()] ?: return@mapNotNull null
                    val fullPath = fullPath(basePath, joinPath(dir, file.name))
                    // 在服务器地址原有路径之后追加（兼容部署在子路径下的 OpenList）
                    val url = server.newBuilder()
                        .addPathSegment("p")
                        .addPathSegments(fullPath.trimStart('/'))
                        .query(null)
                        .apply { if (file.sign.isNotEmpty()) addQueryParameter("sign", file.sign) }
                        .build()
                    SubtitleSource(url = url.toString(), name = file.name, mimeType = mime)
                }
                .sortedWith(compareBy(NaturalOrder) { it.name })
                .toList()
        }

        /** 由播放器自身管理或可能破坏请求的头，不接受驱动下发 */
        private val BLOCKED_HEADERS = setOf("host", "range", "content-length", "connection", "accept-encoding", "transfer-encoding")

        /** 只接受 http/https 绝对地址 */
        fun parsePlayableUrl(raw: String): HttpUrl? =
            raw.trim().takeIf { it.startsWith("http://", true) || it.startsWith("https://", true) }?.toHttpUrlOrNull()

        fun sanitizeHeaders(header: Map<String, List<String>>): Map<String, String> =
            header.asSequence()
                .filter { (name, values) -> name.isNotBlank() && name.lowercase() !in BLOCKED_HEADERS && values.isNotEmpty() }
                .associate { (name, values) -> name.trim() to values.joinToString(", ") }

        /** fs/link 按完整路径处理，需要拼上用户 base_path */
        fun fullPath(basePath: String, path: String): String {
            val base = basePath.trim().trimEnd('/')
            if (base.isEmpty()) return path
            return joinPath(base, path.trimStart('/'))
        }

        fun mimeTypeHint(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
            "m3u8" -> MIME_HLS
            "mpd" -> MIME_DASH
            else -> null
        }

        // 与 androidx.media3.common.MimeTypes 一致，数据层不依赖 Media3
        const val MIME_HLS = "application/x-mpegURL"
        const val MIME_DASH = "application/dash+xml"
    }
}
