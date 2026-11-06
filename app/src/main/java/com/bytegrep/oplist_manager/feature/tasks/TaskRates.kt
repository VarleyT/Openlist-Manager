package com.bytegrep.oplist_manager.feature.tasks

import com.bytegrep.oplist_manager.core.common.RateSample
import com.bytegrep.oplist_manager.core.common.advance
import com.bytegrep.oplist_manager.core.common.estimateRemainingSeconds
import com.bytegrep.oplist_manager.core.common.formatRateLine
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import java.time.Duration
import java.time.OffsetDateTime

private const val PERCENT = 100.0
private const val MILLIS_PER_SECOND = 1000.0

/** 由进度与总大小换算的已传字节数；总大小未知返回 null */
fun TaskInfo.transferredBytes(): Long? =
    if (totalBytes > 0) (totalBytes * progress.coerceIn(0.0, PERCENT) / PERCENT).toLong() else null

/**
 * 用本次轮询结果更新各任务的速率采样（不修改原 Map）。
 * 只跟踪运行中且大小已知的任务，已结束或不再运行的任务会被移除。
 */
fun updateTaskRates(previous: Map<String, RateSample>, tasks: List<TypedTask>, nowMillis: Long): Map<String, RateSample> =
    tasks.mapNotNull { typed ->
        if (typed.task.state != TaskState.RUNNING) return@mapNotNull null
        val bytes = typed.task.transferredBytes() ?: return@mapNotNull null
        typed.key to previous[typed.key].advance(bytes, nowMillis)
    }.toMap()

/** 运行中任务的「速率 · 剩余时间」文案；尚无足够采样时返回 null */
fun runningRateLine(info: TaskInfo, sample: RateSample?): String? {
    val speed = sample?.bytesPerSecond ?: return null
    val remaining = estimateRemainingSeconds(speed, sample.bytes, info.totalBytes)
    return formatRateLine(speed, remaining)
}

/** 已结束任务的平均速率（字节/秒）；时间、大小缺失或耗时为 0 时返回 null */
fun averageSpeed(info: TaskInfo): Double? {
    val bytes = info.transferredBytes()?.takeIf { it > 0 } ?: return null
    val start = info.startTime?.takeIf { it.isNotBlank() } ?: return null
    val end = info.endTime?.takeIf { it.isNotBlank() } ?: return null
    val millis = runCatching { Duration.between(OffsetDateTime.parse(start), OffsetDateTime.parse(end)).toMillis() }
        .getOrNull()
        ?.takeIf { it > 0 } ?: return null
    return bytes * MILLIS_PER_SECOND / millis
}
