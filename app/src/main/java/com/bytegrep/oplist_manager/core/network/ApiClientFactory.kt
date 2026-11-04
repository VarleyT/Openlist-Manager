package com.bytegrep.oplist_manager.core.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 按服务器 baseUrl + token 构建并缓存 [OpenListApi]。
 * 共享同一个 OkHttp 连接池，不同服务器的认证头互相隔离。
 */
@Singleton
class ApiClientFactory @Inject constructor(
    private val baseClient: OkHttpClient,
    private val json: Json,
) {
    private val cache = object : LinkedHashMap<String, OpenListApi>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenListApi>?): Boolean =
            size > MAX_CACHE
    }

    @Synchronized
    fun create(baseUrl: String, token: String?): OpenListApi {
        val key = "$baseUrl|${token?.hashCode() ?: 0}"
        return cache.getOrPut(key) { build(baseUrl, token) }
    }

    /**
     * 按请求 URL 动态决定认证头的 OkHttpClient，用于文本预览、图片加载等非 Retrofit 请求。
     * [tokenFor] 对非 OpenList 服务器同源的 URL 必须返回 null。
     */
    fun sameOriginClient(tokenFor: (HttpUrl) -> String?): OkHttpClient =
        baseClient.newBuilder().addInterceptor(SameOriginAuthInterceptor(tokenFor)).build()

    private fun build(baseUrl: String, token: String?): OpenListApi {
        val url = requireNotNull(normalizeBaseUrl(baseUrl)?.toHttpUrlOrNull()) { "服务器地址无效" }
        val client = if (token == null) baseClient else sameOriginClient { target -> token.takeIf { url.isSameOrigin(target) } }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenListApi::class.java)
    }

    companion object {
        private const val MAX_CACHE = 8

        /**
         * 规范化用户输入的服务器地址：补全协议、去掉多余路径分隔，确保以 "/" 结尾。
         * 未写协议时，局域网/IP 地址默认 http，其余默认 https。
         */
        fun normalizeBaseUrl(input: String): String? {
            val raw = input.trim()
            // 先于 trimEnd 判断协议，避免 "http://" 被截成 "http:" 后误当作主机名
            val hasScheme = raw.startsWith("http://", true) || raw.startsWith("https://", true)
            val trimmed = raw.trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (hasScheme) {
                trimmed
            } else {
                val host = trimmed.substringBefore('/').substringBefore(':')
                val scheme = if (isLocalHost(host)) "http" else "https"
                "$scheme://$trimmed"
            }
            val parsed: HttpUrl = "$withScheme/".toHttpUrlOrNull() ?: return null
            return parsed.toString()
        }

        private fun isLocalHost(host: String): Boolean {
            if (host.equals("localhost", true) || host.endsWith(".local", true)) return true
            val parts = host.split('.')
            if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return false
            val a = parts[0].toInt()
            val b = parts[1].toInt()
            return a == 10 || a == 127 || (a == 192 && b == 168) || (a == 172 && b in 16..31)
        }
    }
}
