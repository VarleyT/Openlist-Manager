package com.bytegrep.oplist_manager.feature.player

import kotlin.math.abs

/** 拖动手势类型：首次移动方向决定，之后整个手势不再切换 */
enum class DragGesture { SEEK, BRIGHTNESS, VOLUME }

/** 手势换算（纯函数，便于测试） */
object PlayerGestures {
    /** 横向滑过整个屏幕宽度对应的进度跨度 */
    const val FULL_WIDTH_SEEK_MS = 120_000L

    /** 左半屏纵向滑动调亮度，右半屏调音量，横向滑动调进度 */
    fun classify(dx: Float, dy: Float, startX: Float, width: Float): DragGesture = when {
        abs(dx) > abs(dy) -> DragGesture.SEEK
        startX < width / 2 -> DragGesture.BRIGHTNESS
        else -> DragGesture.VOLUME
    }

    /** [dxFraction] 为横向位移占屏幕宽度的比例（右为正） */
    fun seekTarget(startMs: Long, dxFraction: Float, durationMs: Long): Long {
        val target = startMs + (dxFraction * FULL_WIDTH_SEEK_MS).toLong()
        val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return target.coerceIn(0, max)
    }

    /** [dyFraction] 为纵向位移占屏幕高度的比例（下为正）；上滑增大，滑满一屏覆盖全部范围 */
    fun adjustLevel(start: Float, dyFraction: Float): Float = (start - dyFraction).coerceIn(0f, 1f)
}

/** 由 Activity 提供的系统级控制（窗口亮度、媒体音量），取值均为 0..1 */
interface PlayerSystemControls {
    fun brightness(): Float
    fun setBrightness(level: Float)
    fun volume(): Float
    fun setVolume(level: Float)
}
