package com.bytegrep.oplist_manager.feature.tasks

import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import java.time.Duration
import java.time.OffsetDateTime

/**
 * 从服务端任务名称中解析出的路径信息。
 *
 * OpenList 的任务名称格式（来源：OpenList internal/fs、internal/offline_download）：
 * - 复制 / 移动 / 合并 / 离线转存：`copy [挂载路径](实际路径) to [挂载路径](实际路径)`
 * - 上传：`upload 文件名 to [挂载路径](实际路径)`
 * - 离线转存上传：`upload [临时路径](链接) to [挂载路径](实际路径)`
 * - 离线下载：`download 链接 to (目标路径)`
 * - 解压：`decompress [挂载路径](实际路径)[包内路径] to [挂载路径](实际路径) with password <密码>`
 */
data class TaskPaths(
    val source: String,
    val target: String,
    /** 解压任务的压缩包内路径 */
    val innerPath: String? = null,
)

private val DECOMPRESS = Regex("""^decompress \[(.*?)]\((.*)\)\[(.*)] to \[(.*?)]\((.*)\) with password <.*>$""")
private val SOURCE_TO_TARGET = Regex("""^\w+ \[(.*?)]\((.*)\) to \[(.*?)]\((.*)\)$""")
private val UPLOAD = Regex("""^upload (.+) to \[(.*?)]\((.*)\)$""")
private val DOWNLOAD = Regex("""^download (.+) to \((.*)\)$""")
private val PASSWORD = Regex(""" with password <.+>$""")

private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L

/** 解析任务名称，无法识别的格式返回 null（界面回退显示原始名称） */
fun parseTaskName(name: String): TaskPaths? {
    DECOMPRESS.matchEntire(name)?.destructured?.let { (srcMount, srcPath, inner, dstMount, dstPath) ->
        return TaskPaths(joinMount(srcMount, srcPath), joinMount(dstMount, dstPath), inner)
    }
    SOURCE_TO_TARGET.matchEntire(name)?.destructured?.let { (srcMount, srcPath, dstMount, dstPath) ->
        // 离线转存上传的第二段是下载链接而非存储路径
        val source = if (srcPath.contains("://")) srcPath else joinMount(srcMount, srcPath)
        return TaskPaths(source, joinMount(dstMount, dstPath))
    }
    UPLOAD.matchEntire(name)?.destructured?.let { (fileName, dstMount, dstPath) ->
        return TaskPaths(fileName, joinMount(dstMount, dstPath))
    }
    DOWNLOAD.matchEntire(name)?.destructured?.let { (url, dstPath) ->
        return TaskPaths(url, dstPath)
    }
    return null
}

/** 仍在执行或等待重试的状态：此时服务端保留的 error 来自上一轮失败的尝试 */
private val RETRYING_STATES = setOf(TaskState.PENDING, TaskState.RUNNING, TaskState.WAITING_RETRY, TaskState.BEFORE_RETRY)

/** 错误信息是否为上一轮尝试遗留（任务已自动重试，当前尝试未必出错） */
val TaskInfo.hasPreviousAttemptError: Boolean get() = error.isNotBlank() && state in RETRYING_STATES

/** 遮蔽解压任务名称中的压缩包密码 */
fun maskTaskName(name: String): String = PASSWORD.replace(name, " with password <***>")

/** 计算任务耗时，时间缺失、无法解析或结束早于开始时返回 null */
fun formatTaskDuration(start: String?, end: String?): String? {
    if (start.isNullOrBlank() || end.isNullOrBlank()) return null
    val duration = runCatching {
        Duration.between(OffsetDateTime.parse(start), OffsetDateTime.parse(end))
    }.getOrNull() ?: return null
    if (duration.isNegative) return null
    val total = duration.seconds
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return when {
        hours > 0 -> "$hours 小时 $minutes 分 $seconds 秒"
        minutes > 0 -> "$minutes 分 $seconds 秒"
        seconds > 0 -> "$seconds 秒"
        else -> "不到 1 秒"
    }
}

/** 拼接挂载路径与存储内实际路径，例如 `/本地` + `/电影` -> `/本地/电影` */
private fun joinMount(mount: String, actualPath: String): String {
    val base = mount.trimEnd('/')
    val rest = actualPath.trimStart('/')
    return when {
        rest.isEmpty() -> base.ifEmpty { "/" }
        else -> "$base/$rest"
    }
}
