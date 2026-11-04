package com.bytegrep.oplist_manager.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/** scheme + host + port 完全一致才视为同源 */
fun HttpUrl.isSameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

/**
 * 仅当 [target] 与 OpenList 服务器 [baseUrl] 同源时返回 [token]，否则返回 null。
 * 所有"是否附加 OpenList Token"的决策都应经过这里，避免 Token 泄露给第三方存储直链。
 */
fun tokenForOrigin(baseUrl: String, token: String, target: HttpUrl): String? {
    val server = ApiClientFactory.normalizeBaseUrl(baseUrl)?.toHttpUrlOrNull() ?: return null
    return token.takeIf { server.isSameOrigin(target) }
}

/**
 * 按请求 URL 决定是否附加认证头。必须作为 application interceptor 注册：
 * 跨域重定向时 OkHttp 会自动移除 Authorization，不会把 Token 带到重定向目标。
 */
class SameOriginAuthInterceptor(private val tokenFor: (HttpUrl) -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenFor(request.url) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", token).build())
    }
}
