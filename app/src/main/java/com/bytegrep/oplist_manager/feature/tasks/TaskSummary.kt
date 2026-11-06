package com.bytegrep.oplist_manager.feature.tasks

import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType

/** 任务统计：运行中 / 等待中 / 已完成 / 失败 */
data class TaskSummary(
    val running: Int = 0,
    val pending: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

private val RUNNING_STATES = setOf(TaskState.RUNNING, TaskState.CANCELING, TaskState.FAILING, TaskState.BEFORE_RETRY)
private val PENDING_STATES = setOf(TaskState.PENDING, TaskState.WAITING_RETRY)
private val FAILED_STATES = setOf(TaskState.ERRORED, TaskState.FAILED)

/** 统计任务状态；已取消及未知状态不计入任何一栏 */
fun summarize(tasks: List<TaskInfo>): TaskSummary {
    var running = 0
    var pending = 0
    var succeeded = 0
    var failed = 0
    tasks.forEach { task ->
        when (task.state) {
            in RUNNING_STATES -> running++
            in PENDING_STATES -> pending++
            TaskState.SUCCEEDED -> succeeded++
            in FAILED_STATES -> failed++
        }
    }
    return TaskSummary(running = running, pending = pending, succeeded = succeeded, failed = failed)
}

/** 带类型的任务（同一 id 在不同类型间可能重复，因此 key 需包含类型） */
data class TypedTask(val type: TaskType, val task: TaskInfo) {
    val key: String get() = "${type.path}:${task.id}"
}

enum class TaskTab(val label: String) { UNDONE("进行中"), DONE("已完成") }

/** 按条件批量处理已结束任务，[action] 对应 OpenList 接口参数 */
enum class TaskBulkAction(val action: String, val label: String, val doneMessage: String, val confirmMessage: String) {
    RETRY_FAILED("retry_failed", "重试失败项", "已重试失败任务", "将重新执行所有失败的任务。"),
    CLEAR_SUCCEEDED("clear_succeeded", "清除成功项", "已清除成功任务", "只清除成功完成的任务记录，失败和已取消的会保留，方便之后重试。"),
    CLEAR_DONE("clear_done", "清除全部", "已清除全部已结束任务", "将清除所有已结束的任务记录，包括成功、失败和已取消的。"),
}

/** 对多选任务的批量操作，[action] 对应 OpenList 的 *_some 接口 */
enum class TaskBatchAction(val action: String, val label: String) {
    CANCEL("cancel_some", "取消"),
    RETRY("retry_some", "重试"),
    DELETE("delete_some", "删除"),
}

/**
 * 解析 WorkManager progress 中的进度值，兼容 Float/Double(0..1) 与 Int/Long(0..100)。
 * 返回 0..1，无法解析时返回 null。
 */
fun parseUploadProgress(value: Any?): Float? = when (value) {
    is Float -> value.coerceIn(0f, 1f)
    is Double -> value.toFloat().coerceIn(0f, 1f)
    is Int -> (value / PERCENT).coerceIn(0f, 1f)
    is Long -> (value / PERCENT).coerceIn(0f, 1f)
    else -> null
}

private const val PERCENT = 100f
