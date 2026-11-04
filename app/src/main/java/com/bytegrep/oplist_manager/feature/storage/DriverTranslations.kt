package com.bytegrep.oplist_manager.feature.storage

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 存储表单的中文翻译，数据来自 OpenList-Frontend 官方 i18n（zh-CN）。
 * 约定：`字段` 为名称，`字段-tips` 为说明，`字段s` 为下拉选项映射；未命中时回退原文。
 */
class DriverTranslations(
    private val common: JsonObject,
    private val drivers: JsonObject,
) {
    /** 字段显示名：驱动专属优先，其次通用 */
    fun label(driver: String, name: String): String = lookup(driver, name) ?: name

    /** 字段说明：无翻译时使用服务端返回的原文 */
    fun help(driver: String, name: String, fallback: String): String = lookup(driver, "$name-tips") ?: fallback

    /** 下拉选项显示名，实际提交值不变 */
    fun option(driver: String, name: String, option: String): String {
        val key = "${name}s"
        val map = sectionOf(driver)?.get(key) as? JsonObject ?: common[key] as? JsonObject
        return (map?.get(option) as? JsonPrimitive)?.content?.trim()?.ifBlank { null } ?: option
    }

    private fun lookup(driver: String, key: String): String? =
        sectionOf(driver).stringOf(key) ?: common.stringOf(key)

    private fun sectionOf(driver: String): JsonObject? = drivers[driver] as? JsonObject

    private fun JsonObject?.stringOf(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.ifBlank { null }

    companion object {
        val EMPTY = DriverTranslations(JsonObject(emptyMap()), JsonObject(emptyMap()))

        fun parse(json: Json, text: String): DriverTranslations {
            val root = json.parseToJsonElement(text).jsonObject
            return DriverTranslations(
                common = root["common"] as? JsonObject ?: JsonObject(emptyMap()),
                drivers = root["drivers"] as? JsonObject ?: JsonObject(emptyMap()),
            )
        }
    }
}

/** 从 assets 懒加载翻译，进程内缓存 */
@Singleton
class DriverTranslationsLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) {
    private val mutex = Mutex()
    private var cached: DriverTranslations? = null

    suspend fun load(): DriverTranslations = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { readAsset() }.also { cached = it }
    }

    private fun readAsset(): DriverTranslations = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { DriverTranslations.parse(json, it.readText()) }
    }.getOrElse { error ->
        // 翻译缺失不影响表单使用，回退显示原文
        Log.w(TAG, "加载存储表单翻译失败", error)
        DriverTranslations.EMPTY
    }

    private companion object {
        const val ASSET_PATH = "i18n/storage_zh_cn.json"
        const val TAG = "DriverTranslations"
    }
}
