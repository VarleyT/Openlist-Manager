package com.bytegrep.oplist_manager.feature.player

import android.util.TypedValue
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

private const val SUBTITLE_TEXT_SIZE_SP = 18f
private const val LIST_HEIGHT_RATIO = 0.5f

/**
 * 字幕渲染：稳定版 media3-ui-compose 尚无字幕组件（CuesState 在 1.12-alpha），
 * 这里嵌入 media3-ui 的 SubtitleView，支持 ASS/SSA 样式与定位。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSubtitles(player: Player, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember {
        SubtitleView(context).apply {
            setUserDefaultStyle()
            setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, SUBTITLE_TEXT_SIZE_SP)
            setApplyEmbeddedStyles(true)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
        }
        player.addListener(listener)
        view.setCues(player.currentCues.cues)
        onDispose {
            player.removeListener(listener)
            view.setCues(null)
        }
    }
    AndroidView(factory = { view }, modifier = modifier.fillMaxSize())
}

/** 音轨 / 字幕选择对话框 */
@Composable
fun TrackDialog(
    menu: TrackMenu,
    onSelect: (TrackOption) -> Unit,
    onDisableSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音轨与字幕") },
        text = {
            // 横屏时屏幕较矮，列表最多占屏幕一半高度，其余可滚动
            val maxHeight = (LocalConfiguration.current.screenHeightDp * LIST_HEIGHT_RATIO).dp
            Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
                if (menu.audio.isNotEmpty()) {
                    SectionTitle("音轨")
                    menu.audio.forEach { option -> OptionRow(option.display, option.isSelected) { onSelect(option) } }
                }
                SectionTitle("字幕")
                OptionRow("关闭字幕", menu.textOff, onClick = onDisableSubtitles)
                menu.text.forEach { option -> OptionRow(option.display, option.isSelected) { onSelect(option) } }
                if (menu.text.isEmpty()) {
                    Text(
                        "未找到字幕。外挂字幕需与视频同目录且文件名以视频名开头（srt / ass / vtt）",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpenListTheme.colors.ink3,
                        modifier = Modifier.padding(vertical = Spacing.sm),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

private val TrackOption.display: String get() = if (isSupported) label else "$label（设备不支持）"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = OpenListTheme.colors.ink2,
        modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.xs),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = OpenListTheme.colors.ink)
    }
}
