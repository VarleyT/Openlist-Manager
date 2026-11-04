package com.bytegrep.oplist_manager.feature.storage

import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.model.DriverItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * 存储表单值 <-> 存储 JsonObject 的纯函数转换。
 *
 * 表单值统一用字符串保存，key 带前缀区分来源，避免 common 与 additional 同名字段（如 order_by）冲突：
 * - common 字段：存储 JSON 顶层，key 为 [commonKey]
 * - additional 字段：序列化进 addition 字符串，key 为 [additionKey]
 */
object StorageFormKeys {
    const val FIELD_DRIVER = "driver"
    const val FIELD_ADDITION = "addition"
    private const val COMMON_PREFIX = "common."
    private const val ADDITION_PREFIX = "addition."

    fun commonKey(name: String): String = COMMON_PREFIX + name
    fun additionKey(name: String): String = ADDITION_PREFIX + name
}

object DriverItemType {
    const val STRING = "string"
    const val NUMBER = "number"
    const val BOOL = "bool"
    const val SELECT = "select"
    const val TEXT = "text"
}

private val mapperJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val SECRET_HINTS = listOf("password", "secret", "token", "key", "cookie")

/** 字段名是否疑似敏感信息（默认遮罩显示） */
fun isSecretField(name: String): Boolean {
    val lower = name.lowercase()
    return SECRET_HINTS.any { lower.contains(it) }
}

/** select 类型的选项列表 */
fun DriverItem.optionList(): List<String> =
    options.split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** 新建存储时的表单默认值 */
fun defaultFormValues(info: DriverInfo): Map<String, String> =
    info.common.associate { StorageFormKeys.commonKey(it.name) to defaultOf(it) } +
        info.additional.associate { StorageFormKeys.additionKey(it.name) to defaultOf(it) }

/** 从原始存储 JSON 提取表单值；缺失字段使用驱动定义的默认值 */
fun extractFormValues(raw: JsonObject, info: DriverInfo): Map<String, String> {
    val addition = parseAddition(raw[StorageFormKeys.FIELD_ADDITION])
    val common = info.common.associate {
        StorageFormKeys.commonKey(it.name) to (raw[it.name]?.toFormString() ?: defaultOf(it))
    }
    val additional = info.additional.associate {
        StorageFormKeys.additionKey(it.name) to (addition[it.name]?.toFormString() ?: defaultOf(it))
    }
    return common + additional
}

/**
 * 构造提交给服务端的存储 JSON。
 *
 * [base] 非空（编辑）时以其为基础，仅覆盖与原始值不同的字段，未知字段原样保留；
 * [base] 为空（新建）时写入全部表单字段。addition 始终重新序列化为字符串。
 */
fun buildStorageJson(
    base: JsonObject?,
    driver: String,
    info: DriverInfo,
    values: Map<String, String>,
): JsonObject {
    val original = base?.let { extractFormValues(it, info) }.orEmpty()
    fun changed(key: String): String? {
        val value = values[key] ?: return null
        return if (base != null && original[key] == value) null else value
    }

    val top = (base ?: JsonObject(emptyMap())).toMutableMap()
    top[StorageFormKeys.FIELD_DRIVER] = JsonPrimitive(driver)
    info.common.forEach { item ->
        changed(StorageFormKeys.commonKey(item.name))?.let { top[item.name] = toJsonValue(item, it) }
    }

    val addition = parseAddition(base?.get(StorageFormKeys.FIELD_ADDITION)).toMutableMap()
    info.additional.forEach { item ->
        changed(StorageFormKeys.additionKey(item.name))?.let { addition[item.name] = toJsonValue(item, it) }
    }
    top[StorageFormKeys.FIELD_ADDITION] = JsonPrimitive(JsonObject(addition).toString())
    return JsonObject(top)
}

/** 按驱动字段类型把表单字符串转成 JSON 值 */
fun toJsonValue(item: DriverItem, value: String): JsonElement = when (item.type) {
    DriverItemType.BOOL -> JsonPrimitive(value.trim().equals("true", ignoreCase = true))
    DriverItemType.NUMBER -> numberOf(value)
    else -> JsonPrimitive(value)
}

private fun numberOf(value: String): JsonElement {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) return JsonPrimitive(0)
    trimmed.toLongOrNull()?.let { return JsonPrimitive(it) }
    trimmed.toDoubleOrNull()?.let { return JsonPrimitive(it) }
    return JsonPrimitive(value)
}

private fun defaultOf(item: DriverItem): String =
    if (item.type == DriverItemType.BOOL && item.default.isBlank()) "false" else item.default

/** addition 可能是 JSON 字符串（常见）或直接是对象，解析失败返回空对象 */
private fun parseAddition(element: JsonElement?): JsonObject {
    if (element == null || element is JsonNull) return JsonObject(emptyMap())
    if (element is JsonObject) return element
    val text = (element as? JsonPrimitive)?.content.orEmpty()
    if (text.isBlank()) return JsonObject(emptyMap())
    return runCatching { mapperJson.parseToJsonElement(text).jsonObject }
        .getOrDefault(JsonObject(emptyMap()))
}

private fun JsonElement.toFormString(): String = when (this) {
    is JsonNull -> ""
    is JsonPrimitive -> content
    else -> toString()
}
