package com.bytegrep.oplist_manager.core.common

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
private const val UNIT_STEP = 1024.0
private val SIZE_UNITS = listOf("B", "KB", "MB", "GB", "TB", "PB")

/** 字节数格式化为可读字符串，如 "2.4 MB" */
fun formatBytes(bytes: Long): String {
    if (bytes < UNIT_STEP) return "$bytes B"
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= UNIT_STEP && unitIndex < SIZE_UNITS.lastIndex) {
        value /= UNIT_STEP
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, SIZE_UNITS[unitIndex])
}

/** ISO-8601 时间（OpenList 返回格式）转本地 "yyyy-MM-dd HH:mm"，解析失败返回原文 */
fun formatDateTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)
    }.getOrElse { iso.take(16).replace('T', ' ') }
}

/** 拼接 OpenList 路径，保证以 "/" 开头且不重复分隔符 */
fun joinPath(parent: String, name: String): String {
    val base = parent.trimEnd('/')
    return if (base.isEmpty()) "/$name" else "$base/$name"
}

/** 取父路径，根目录返回 "/" */
fun parentPath(path: String): String {
    val trimmed = path.trimEnd('/')
    val idx = trimmed.lastIndexOf('/')
    return if (idx <= 0) "/" else trimmed.substring(0, idx)
}
