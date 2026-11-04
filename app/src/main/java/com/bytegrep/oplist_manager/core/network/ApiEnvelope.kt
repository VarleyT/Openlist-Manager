package com.bytegrep.oplist_manager.core.network

import kotlinx.serialization.Serializable

/** OpenList 统一响应包：HTTP 状态通常为 200，业务结果由 [code] 表示 */
@Serializable
data class ApiEnvelope<T>(
    val code: Int,
    val message: String = "",
    val data: T? = null,
)

/** 分页列表结构 `{content, total}` */
@Serializable
data class PageData<T>(
    val content: List<T>? = null,
    val total: Long = 0,
)
