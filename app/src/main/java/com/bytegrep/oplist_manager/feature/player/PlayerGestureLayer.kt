package com.bytegrep.oplist_manager.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/** 进行中的拖动手势 */
private data class ActiveDrag(
    /** null 表示该手势不可用（如不可拖动的媒体上横向滑动），整个手势期间保持不响应 */
    val type: DragGesture?,
    val startPositionMs: Long = 0,
    val startLevel: Float = 0f,
    val total: Offset = Offset.Zero,
)

/**
 * 全屏手势层：单击显示/隐藏控制层，双击播放/暂停；
 * 横向拖动调进度（松手后才 seek），左侧纵向调亮度，右侧纵向调音量。锁定时只响应单击。
 */
@Composable
fun PlayerGestureLayer(
    state: VideoPlayerState,
    actions: VideoPlayerViewModel,
    systemControls: PlayerSystemControls,
    modifier: Modifier = Modifier,
) {
    val latest by rememberUpdatedState(state)
    var drag by remember { mutableStateOf<ActiveDrag?>(null) }
    var label by remember { mutableStateOf<String?>(null) }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(state.isLocked) {
                detectTapGestures(
                    onTap = { actions.toggleControls() },
                    onDoubleTap = if (state.isLocked) null else { _ -> actions.togglePlayPause() },
                )
            }
            .pointerInput(state.isLocked) {
                if (state.isLocked) return@pointerInput
                var startX = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        drag = null
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (amount == Offset.Zero && drag == null) return@detectDragGestures
                        val current = drag ?: startDrag(amount, startX, size.width.toFloat(), latest, systemControls)
                        val updated = current.copy(total = current.total + amount)
                        drag = updated
                        label = applyDrag(updated, size.width.toFloat(), size.height.toFloat(), latest, systemControls)
                    },
                    onDragEnd = {
                        drag?.takeIf { it.type == DragGesture.SEEK }?.let {
                            actions.seekTo(seekTargetOf(it, size.width.toFloat(), latest))
                        }
                        drag = null
                        label = null
                    },
                    onDragCancel = {
                        drag = null
                        label = null
                    },
                )
            },
    ) {
        label?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
        }
    }
}

private fun startDrag(
    amount: Offset,
    startX: Float,
    width: Float,
    state: VideoPlayerState,
    controls: PlayerSystemControls,
): ActiveDrag =
    when (val type = PlayerGestures.classify(amount.x, amount.y, startX, width)) {
        // 不可拖动或时长未知的媒体不响应横向手势
        DragGesture.SEEK -> if (state.isSeekable && state.durationMs > 0) {
            ActiveDrag(type, startPositionMs = state.positionMs)
        } else {
            ActiveDrag(null)
        }
        DragGesture.BRIGHTNESS -> ActiveDrag(type, startLevel = controls.brightness())
        DragGesture.VOLUME -> ActiveDrag(type, startLevel = controls.volume())
    }

private fun seekTargetOf(drag: ActiveDrag, width: Float, state: VideoPlayerState): Long =
    PlayerGestures.seekTarget(drag.startPositionMs, drag.total.x / width, state.durationMs)

/** 应用手势效果并返回提示文案 */
private fun applyDrag(
    drag: ActiveDrag,
    width: Float,
    height: Float,
    state: VideoPlayerState,
    controls: PlayerSystemControls,
): String? = when (drag.type) {
    null -> null
    DragGesture.SEEK -> {
        val target = seekTargetOf(drag, width, state)
        val delta = (target - drag.startPositionMs) / 1000
        val sign = if (delta >= 0) "+" else "-"
        "$sign${formatDuration(kotlin.math.abs(delta) * 1000)} / ${formatDuration(target)}"
    }
    DragGesture.BRIGHTNESS -> {
        val level = PlayerGestures.adjustLevel(drag.startLevel, drag.total.y / height)
        controls.setBrightness(level)
        "亮度 ${(level * 100).roundToInt()}%"
    }
    DragGesture.VOLUME -> {
        val level = PlayerGestures.adjustLevel(drag.startLevel, drag.total.y / height)
        controls.setVolume(level)
        "音量 ${(level * 100).roundToInt()}%"
    }
}
