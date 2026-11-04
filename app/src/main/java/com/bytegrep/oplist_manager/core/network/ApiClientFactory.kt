package com.bytegrep.oplist_manager.core.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
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

    /** 带认证头的 OkHttpClient，用于上传、文本预览等非 Retrofit 请求 */
    fun authorizedClient(token: String?): OkHttpClient =
        baseClient.newBuilder().apply { if (token != null) addInterceptor(authInterceptor(token)) }.build()

    private fun build(baseUrl: String, token: String?): OpenListApi {
        val url = requireNotNull(normalizeBaseUrl(baseUrl)?.toHttpUrlOrNull()) { "服务器地址无效" }
        return Retrofit.Builder()
            .baseUrl(url)
            .client(authorizedClient(token))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenListApi::class.java)
    }

    companion object {
        private const val MAX_CACHE = 8

        fun authInterceptor(token: String): Interceptor = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Authorization", token).build())
        }

        /**
         * 规范化用户输入的服务器地址：补全协议、去掉多余路径分隔，确保以 "/" 结尾。
         * 未写协议时，局域网/IP 地址默认 http，其余默认 https。
         */
        fun normalizeBaseUrl(input: String): String? {
            val trimmed = input.trim().trimEnd('/')
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
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
