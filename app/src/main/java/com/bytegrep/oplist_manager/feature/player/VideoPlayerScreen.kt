package com.bytegrep.oplist_manager.feature.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

private val OnVideo = Color.White
private val OnVideoDim = Color.White.copy(alpha = 0.7f)
private val Scrim = Color.Black.copy(alpha = 0.55f)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    state: VideoPlayerState,
    player: Player,
    actions: VideoPlayerViewModel,
    onBack: () -> Unit,
    isLandscape: () -> Boolean,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { actions.toggleControls() }) },
    ) {
        ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

        if (state.showLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = OnVideo, strokeWidth = 3.dp)
        }

        AnimatedVisibility(state.controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                if (state.isLocked) {
                    LockButton(locked = true, onClick = actions::toggleLock, modifier = Modifier.align(Alignment.CenterStart))
                } else {
                    ControlsOverlay(state, actions, onBack, isLandscape)
                }
            }
        }

        if (state.audioUnsupported && state.error == null) {
            Text(
                "设备不支持该视频的音频编码（如 DTS / TrueHD），当前无声播放，可尝试外部播放器",
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp, start = Spacing.lg, end = Spacing.lg)
                    .background(Scrim, MaterialTheme.shapes.medium)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                style = MaterialTheme.typography.bodySmall,
                color = OnVideo,
            )
        }

        state.error?.let { error ->
            ErrorPanel(error, onRetry = actions::retry, onBack = onBack, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun BoxScope.ControlsOverlay(
    state: VideoPlayerState,
    actions: VideoPlayerViewModel,
    onBack: () -> Unit,
    isLandscape: () -> Boolean,
) {
    // 顶部：返回 + 标题 + 旋转
    Row(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Scrim, Color.Transparent)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OverlayIcon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", onBack)
        Text(
            state.title,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.sm),
            style = MaterialTheme.typography.titleMedium,
            color = OnVideo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OverlayIcon(Icons.Outlined.ScreenRotation, "切换横竖屏") { actions.toggleOrientation(isLandscape()) }
    }

    // 中部：快退 / 播放暂停 / 快进
    if (state.error == null && !state.isResolving) {
        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIcon(Icons.Outlined.Replay10, "后退 10 秒", size = 40.dp, enabled = state.isSeekable) {
                actions.seekBy(-VideoPlayerController.SEEK_INCREMENT_MS)
            }
            val (icon, label) = when {
                state.isEnded -> Icons.Outlined.Replay to "重新播放"
                state.playWhenReady -> Icons.Outlined.Pause to "暂停"
                else -> Icons.Outlined.PlayArrow to "播放"
            }
            IconButton(
                onClick = actions::togglePlayPause,
                modifier = Modifier.size(72.dp).background(Scrim, CircleShape),
            ) { Icon(icon, label, tint = OnVideo, modifier = Modifier.size(44.dp)) }
            OverlayIcon(Icons.Outlined.Forward10, "前进 10 秒", size = 40.dp, enabled = state.isSeekable) {
                actions.seekBy(VideoPlayerController.SEEK_INCREMENT_MS)
            }
        }
    }

    LockButton(locked = false, onClick = actions::toggleLock, modifier = Modifier.align(Alignment.CenterStart))

    // 底部：进度
    ProgressBar(
        state,
        onSeek = actions::seekTo,
        onDrag = actions::showControls,
        modifier = Modifier.align(Alignment.BottomCenter),
    )
}

@Composable
private fun ProgressBar(
    state: VideoPlayerState,
    onSeek: (Long) -> Unit,
    onDrag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val duration = state.durationMs.coerceAtLeast(1)
    val fraction = dragging ?: (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Scrim)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        Slider(
            value = fraction,
            onValueChange = {
                // 拖动期间保持控制层可见
                if (dragging == null) onDrag()
                dragging = it
            },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = state.isSeekable && state.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = OpenListTheme.colors.accent,
                activeTrackColor = OpenListTheme.colors.accent,
                inactiveTrackColor = OnVideoDim.copy(alpha = 0.3f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatDuration((fraction * duration).toLong()), color = OnVideo, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (!state.isSeekable && state.durationMs > 0) {
                Text("服务器不支持拖动进度", color = OnVideoDim, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(Spacing.md))
            }
            Text(formatDuration(state.durationMs), color = OnVideoDim, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LockButton(locked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(start = Spacing.md)) {
        IconButton(onClick = onClick, modifier = Modifier.background(Scrim, CircleShape)) {
            Icon(
                if (locked) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                contentDescription = if (locked) "解锁屏幕" else "锁定屏幕",
                tint = OnVideo,
            )
        }
    }
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) = OverlayIcon(icon, description, size = 24.dp, enabled = true, onClick = onClick)

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    description: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size + 24.dp)) {
        Icon(icon, description, tint = if (enabled) OnVideo else OnVideoDim.copy(alpha = 0.35f), modifier = Modifier.size(size))
    }
}

@Composable
private fun ErrorPanel(error: PlayerError, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(Spacing.xl)
            .background(Scrim, MaterialTheme.shapes.large)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("无法播放", style = MaterialTheme.typography.titleMedium, color = OnVideo)
        Spacer(Modifier.height(Spacing.sm))
        Text(error.message, style = MaterialTheme.typography.bodyMedium, color = OnVideoDim, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            TextButton(onClick = onBack) { Text("返回", color = OnVideoDim) }
            FilledTonalButton(onClick = onRetry) { Text("重试") }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
