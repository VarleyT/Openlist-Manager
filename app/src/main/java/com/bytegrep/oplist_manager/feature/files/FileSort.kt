package com.bytegrep.oplist_manager.feature.files

import com.bytegrep.oplist_manager.data.model.FsObject
import java.time.OffsetDateTime

/** 排序字段 */
enum class SortField(val label: String) {
    NAME("名称"),
    SIZE("大小"),
    MODIFIED("修改时间"),
}

/** 本地排序方式；目录始终排在文件之前 */
data class FileSort(
    val field: SortField = SortField.NAME,
    val ascending: Boolean = true,
)

/** 纯函数：目录在前，再按 [sort] 排序，名称作为次级排序键保证稳定 */
fun sortFiles(items: List<FsObject>, sort: FileSort): List<FsObject> {
    val byField: Comparator<FsObject> = when (sort.field) {
        SortField.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortField.SIZE -> compareBy { it.size }
        SortField.MODIFIED -> compareBy { modifiedEpochMillis(it.modified) }
    }
    val directed = if (sort.ascending) byField else byField.reversed()
    val comparator = compareByDescending<FsObject> { it.isDir }
        .then(directed)
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    return items.sortedWith(comparator)
}

/** 解析 OpenList 的 ISO-8601 时间；失败返回 0（排在最旧） */
internal fun modifiedEpochMillis(iso: String): Long =
    runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrDefault(0L)
