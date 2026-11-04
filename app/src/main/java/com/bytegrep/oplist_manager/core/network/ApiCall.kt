package com.bytegrep.oplist_manager.core.network

import com.bytegrep.oplist_manager.core.common.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

private const val CODE_OK = 200
private const val CODE_UNAUTHORIZED = 401
private const val CODE_FORBIDDEN = 403

/**
 * 解包 [ApiEnvelope]：code 200 返回 data，其他 code 抛出 [AppException]。
 * data 为 null 时返回 null，由调用方决定是否允许。
 */
fun <T> ApiEnvelope<T>.unwrapNullable(): T? = when (code) {
    CODE_OK -> data
    CODE_UNAUTHORIZED -> throw AppException.Unauthorized(
        message.takeIf { it.isNotBlank() }?.let { "登录已失效：$it" } ?: "登录已失效，请重新登录",
    )
    CODE_FORBIDDEN -> throw AppException.Forbidden(message.ifBlank { "当前账号无权限执行此操作" })
    else -> throw AppException.Server(code, message.ifBlank { "服务器错误 ($code)" })
}

fun <T> ApiEnvelope<T>.unwrap(): T =
    unwrapNullable() ?: throw AppException.Server(code, "服务器返回了空数据")

/**
 * 执行网络请求并将底层异常映射为 [AppException]。
 * CancellationException 始终向上抛出。
 */
suspend fun <T> safeApiCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: AppException) {
    throw e
} catch (e: Throwable) {
    throw e.toAppException()
}

fun Throwable.toAppException(): AppException = when (this) {
    is AppException -> this
    is UnknownHostException -> AppException.Network("无法解析服务器地址，请检查 URL 或网络", this)
    is SocketTimeoutException -> AppException.Network("连接超时，请检查服务器是否在线", this)
    is ConnectException -> AppException.Network("无法连接到服务器，请检查地址、端口或服务状态", this)
    is NoRouteToHostException -> AppException.Network("无法访问服务器所在网络", this)
    is SSLHandshakeException, is SSLPeerUnverifiedException ->
        AppException.Network("TLS 证书校验失败：证书不受信任或域名不匹配", this)
    is SSLException -> AppException.Network("安全连接失败：${message ?: "TLS 错误"}", this)
    is HttpException -> when (code()) {
        CODE_UNAUTHORIZED -> AppException.Unauthorized()
        CODE_FORBIDDEN -> AppException.Forbidden()
        404 -> AppException.Server(404, "接口不存在 (404)，服务器版本可能不兼容或地址不是 OpenList")
        else -> AppException.Server(code(), "HTTP ${code()} ${message()}")
    }
    is SerializationException, is IllegalArgumentException ->
        AppException.Unknown("服务器响应格式无法解析，可能不是 OpenList 服务或版本不兼容", this)
    is IOException -> AppException.Network("网络异常：${message ?: javaClass.simpleName}", this)
    else -> AppException.Unknown(message ?: javaClass.simpleName, this)
}
