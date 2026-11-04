package com.bytegrep.oplist_manager.core.common

/**
 * 统一的业务/网络异常，UI 层直接使用 [message] 展示给用户。
 */
sealed class AppException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Token 失效或未登录 */
    class Unauthorized(message: String = "登录已失效，请重新登录") : AppException(message)

    /** 服务器返回业务错误（code != 200） */
    class Server(val code: Int, message: String) : AppException(message)

    /** 网络层错误：DNS、超时、TLS、连接失败等 */
    class Network(message: String, cause: Throwable? = null) : AppException(message, cause)

    /** 权限不足（非管理员访问管理接口等） */
    class Forbidden(message: String = "当前账号无权限执行此操作") : AppException(message)

    /** 其他未知错误 */
    class Unknown(message: String, cause: Throwable? = null) : AppException(message, cause)
}

/** 将任意异常转换为可展示的中文信息 */
fun Throwable.userMessage(): String = when (this) {
    is AppException -> message ?: "未知错误"
    else -> message ?: javaClass.simpleName
}
