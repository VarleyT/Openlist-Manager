package com.bytegrep.oplist_manager.core.common

import kotlin.math.ceil

/**
 * 传输速率采样状态：最近一次采样的已传字节数、时间，以及平滑后的速率。
 * OpenList 不返回实时速率，由客户端按两次采样的差值估算。
 */
data class RateSample(
    val bytes: Long,
    val atMillis: Long,
    /** 指数平滑后的速率（字节/秒），只有一次采样时为 null */
    val bytesPerSecond: Double? = null,
)

/** 平滑系数：越大越贴近最新一次采样 */
private const val EMA_ALPHA = 0.5
/** 两次采样间隔过短时忽略新采样，避免短间隔放大误差 */
private const val MIN_SAMPLE_INTERVAL_MS = 500L
private const val MILLIS_PER_SECOND = 1000.0
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_DAY = 86_400L

/**
 * 加入一次新采样并返回新状态（不修改原状态）。
 * 已传字节数回退（例如任务重试）时重新开始计算。
 */
fun RateSample?.advance(bytes: Long, nowMillis: Long): RateSample {
    if (this == null || bytes < this.bytes || nowMillis < atMillis) return RateSample(bytes, nowMillis)
    val elapsed = nowMillis - atMillis
    if (elapsed < MIN_SAMPLE_INTERVAL_MS) return this
    val instant = (bytes - this.bytes) * MILLIS_PER_SECOND / elapsed
    val smoothed = bytesPerSecond?.let { EMA_ALPHA * instant + (1 - EMA_ALPHA) * it } ?: instant
    return RateSample(bytes, nowMillis, smoothed)
}

/** 按当前速率估算剩余秒数；速率为 0、总大小未知时返回 null */
fun estimateRemainingSeconds(bytesPerSecond: Double?, transferred: Long, total: Long): Long? {
    if (bytesPerSecond == null || bytesPerSecond <= 0.0 || total <= 0) return null
    val left = (total - transferred).coerceAtLeast(0)
    return ceil(left / bytesPerSecond).toLong()
}

/** 速率格式化，如 "3.2 MB/s" */
fun formatSpeed(bytesPerSecond: Double): String = "${formatBytes(bytesPerSecond.toLong().coerceAtLeast(0))}/s"

/** 剩余时间格式化，如 "1 小时 5 分"、"12 秒" */
fun formatRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / SECONDS_PER_HOUR
    val minutes = safe % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = safe % SECONDS_PER_MINUTE
    return when {
        safe >= SECONDS_PER_DAY -> "超过 1 天"
        hours > 0 -> "$hours 小时 $minutes 分"
        minutes > 0 -> "$minutes 分 $secs 秒"
        else -> "$secs 秒"
    }
}

/** 拼接速率与剩余时间，如 "3.2 MB/s · 剩余 12 秒"；速率未知返回 null */
fun formatRateLine(bytesPerSecond: Double?, remainingSeconds: Long?): String? {
    if (bytesPerSecond == null) return null
    val speed = formatSpeed(bytesPerSecond)
    return remainingSeconds?.let { "$speed · 剩余 ${formatRemaining(it)}" } ?: speed
}
