package com.bytegrep.oplist_manager.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.common.formatBytes
import com.bytegrep.oplist_manager.core.common.formatDateTime
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskType
import java.util.Locale

/** 任务详情底部弹窗：完整路径、错误信息、耗时等，文字可长按复制 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailSheet(
    task: TypedTask,
    isDoneTab: Boolean,
    busy: Boolean,
    callbacks: TaskCallbacks,
    onDismiss: () -> Unit,
) {
    val colors = OpenListTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl)
                .padding(bottom = Spacing.lg),
        ) {
            DetailHeader(type = task.type, info = task.task)
            Spacer(Modifier.height(Spacing.md))
            SelectionContainer { DetailRows(type = task.type, info = task.task) }
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = colors.hairline)
            TaskActions(
                task = task,
                isDoneTab = isDoneTab,
                busy = busy,
                callbacks = callbacks.copy(
                    onDelete = {
                        onDismiss()
                        callbacks.onDelete(it)
                    },
                ),
            )
        }
    }
}

@Composable
private fun DetailHeader(type: TaskType, info: TaskInfo) {
    val colors = OpenListTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBlock(icon = typeIcon(type), tint = colors.accent, background = colors.accentSoft)
        Spacer(Modifier.width(Spacing.md))
        Text(
            "${type.label}任务",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        StatusPill(text = info.stateLabel, kind = pillKind(info.state))
    }
}

@Composable
private fun DetailRows(type: TaskType, info: TaskInfo) {
    val danger = OpenListTheme.colors.danger
    val paths = remember(info.name) { parseTaskName(info.name) }
    val duration = remember(info.startTime, info.endTime) { formatTaskDuration(info.startTime, info.endTime) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        paths?.let {
            DetailRow(sourceLabel(type), it.source)
            it.innerPath?.let { inner -> DetailRow("压缩包内路径", inner) }
            DetailRow("目标路径", it.target)
        }
        if (info.isRunning) {
            DetailRow("进度", String.format(Locale.US, "%.1f%%", info.progress))
        }
        if (info.isRunning && info.status.isNotBlank()) DetailRow("当前状态", info.status)
        if (info.error.isNotBlank()) DetailRow("错误信息", info.error, valueColor = danger)
        if (info.totalBytes > 0) DetailRow("大小", formatBytes(info.totalBytes))
        if (info.creator.isNotBlank()) DetailRow("创建者", info.creator)
        formatDateTime(info.startTime).takeIf { it.isNotBlank() }?.let { DetailRow("开始时间", it) }
        formatDateTime(info.endTime).takeIf { it.isNotBlank() }?.let { DetailRow("结束时间", it) }
        duration?.let { DetailRow("耗时", it) }
        DetailRow("任务名称", maskTaskName(info.name).ifBlank { "—" })
        DetailRow("任务 ID", info.id)
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color = OpenListTheme.colors.ink) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = OpenListTheme.colors.ink3)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

private fun sourceLabel(type: TaskType): String = when (type) {
    TaskType.UPLOAD, TaskType.DECOMPRESS_UPLOAD -> "文件"
    TaskType.OFFLINE_DOWNLOAD -> "下载链接"
    TaskType.COPY, TaskType.MOVE, TaskType.OFFLINE_TRANSFER, TaskType.DECOMPRESS -> "源路径"
}
