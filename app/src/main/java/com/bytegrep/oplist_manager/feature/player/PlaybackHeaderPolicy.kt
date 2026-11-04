package com.bytegrep.oplist_manager.feature.player

import com.bytegrep.oplist_manager.core.network.isSameOrigin
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 播放请求的请求头策略：
 * - 驱动请求头只发给登记的同源地址（scheme+host+port 完全一致）；
 * - 302 跳到其他源时，作为网络拦截器在每一跳上移除驱动请求头；
 * - 始终移除未登记的 Authorization，OpenList Token 不会出现在播放请求里。
 */
class PlaybackHeaderPolicy {

    private data class Entry(val origin: HttpUrl, val headers: Map<String, String>)

    @Volatile
    private var entry: Entry? = null

    fun register(url: String, headers: Map<String, String>) {
        entry = url.toHttpUrlOrNull()?.takeIf { headers.isNotEmpty() }?.let { Entry(it, headers) }
    }

    fun headersFor(url: String): Map<String, String> {
        val current = entry ?: return emptyMap()
        val target = url.toHttpUrlOrNull() ?: return emptyMap()
        return if (current.origin.isSameOrigin(target)) current.headers else emptyMap()
    }

    /** 必须注册为 network interceptor，才能作用于重定向后的每一跳 */
    val networkInterceptor = Interceptor { chain ->
        val request = chain.request()
        val allowed = headersFor(request.url.toString()).keys.map { it.lowercase() }.toSet()
        val toRemove = buildSet {
            entry?.headers?.keys?.forEach { if (it.lowercase() !in allowed) add(it) }
            if (AUTHORIZATION !in allowed) add("Authorization")
        }
        val builder = request.newBuilder()
        toRemove.forEach { builder.removeHeader(it) }
        chain.proceed(builder.build())
    }

    private companion object {
        const val AUTHORIZATION = "authorization"
    }
}

private const val PLAYER_READ_TIMEOUT_SEC = 30L

/**
 * 播放专用客户端：基于共享客户端（复用连接池、UA），不含任何 OpenList 认证拦截器。
 * OkHttp 默认跟随 http↔https 跨协议重定向（ExoPlayer 自带网络栈默认不跟随）。
 */
fun OkHttpClient.forPlayback(policy: PlaybackHeaderPolicy): OkHttpClient = newBuilder()
    .readTimeout(PLAYER_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
    .followRedirects(true)
    .followSslRedirects(true)
    .addNetworkInterceptor(policy.networkInterceptor)
    .build()
