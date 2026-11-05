package com.bytegrep.oplist_manager.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.bytegrep.oplist_manager.core.common.formatBytes
import com.bytegrep.oplist_manager.core.common.formatDateTime
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.PillKind
import com.bytegrep.oplist_manager.core.designsystem.component.StatCell
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType
import java.util.Locale

@Composable
internal fun SummaryCard(summary: TaskSummary) {
    val colors = OpenListTheme.colors
    OlCard {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm)) {
            StatCell(summary.running.toString(), "运行中", Modifier.weight(1f), valueColor = colors.accent)
            StatCell(summary.pending.toString(), "等待中", Modifier.weight(1f), valueColor = colors.ink2)
            StatCell(summary.succeeded.toString(), "已完成", Modifier.weight(1f), valueColor = colors.success)
            StatCell(summary.failed.toString(), "失败", Modifier.weight(1f), valueColor = colors.danger)
        }
    }
}

/** 胶囊分段切换 */
@Composable
internal fun TaskTabSwitcher(selected: TaskTab, undoneCount: Int, doneCount: Int, onSelect: (TaskTab) -> Unit) {
    val colors = OpenListTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.surface)
            .padding(4.dp),
    ) {
        TaskTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val count = if (tab == TaskTab.UNDONE) undoneCount else doneCount
            Text(
                text = "${tab.label} ($count)",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) colors.surface else colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (isSelected) colors.accent else colors.surface)
                    .clickable { onSelect(tab) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun TypeFilterRow(selected: TaskType?, onSelect: (TaskType?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("全部") })
        TaskType.entries.forEach { type ->
            FilterChip(selected = selected == type, onClick = { onSelect(type) }, label = { Text(type.label) })
        }
    }
}

@Composable
internal fun PartialErrorBanner(message: String) {
    val colors = OpenListTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.button))
            .background(colors.dangerSoft)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.danger)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            "部分类型加载失败：$message",
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TaskCard(
    task: TypedTask,
    isDoneTab: Boolean,
    busy: Boolean,
    callbacks: TaskCallbacks,
    onClick: () -> Unit,
) {
    val info = task.task
    OlCard(onClick = onClick) {
        Column(Modifier.padding(Spacing.lg)) {
            TaskHeader(type = task.type, info = info)
            if (!isDoneTab && info.isRunning) {
                Spacer(Modifier.height(Spacing.md))
                ProgressLine(fraction = (info.progress / PERCENT_SCALE).toFloat(), totalBytes = info.totalBytes)
            } else if (info.totalBytes > 0) {
                Spacer(Modifier.height(Spacing.xs))
                MetaText("大小：${formatBytes(info.totalBytes)}")
            }
            if (info.error.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    info.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenListTheme.colors.danger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            TaskTimeLine(info)
            HorizontalDivider(Modifier.padding(vertical = Spacing.sm), color = OpenListTheme.colors.hairline)
            TaskActions(task = task, isDoneTab = isDoneTab, busy = busy, callbacks = callbacks, onDetail = onClick)
        }
    }
}

@Composable
private fun TaskHeader(type: TaskType, info: TaskInfo) {
    val colors = OpenListTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBlock(icon = typeIcon(type), tint = colors.accent, background = colors.accentSoft)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(text = type.label, color = colors.accent, background = colors.accentSoft)
                if (info.creator.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    MetaText(info.creator)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                maskTaskName(info.name).ifBlank { info.id },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        StatusPill(text = info.stateLabel, kind = pillKind(info.state))
    }
}

@Composable
private fun ProgressLine(fraction: Float, totalBytes: Long, color: Color = OpenListTheme.colors.accent) {
    val safe = fraction.coerceIn(0f, 1f)
    LinearProgressIndicator(
        progress = { safe },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(Radius.pill)),
        color = color,
        trackColor = OpenListTheme.colors.fill,
    )
    Spacer(Modifier.height(Spacing.xs))
    Row {
        Text(
            String.format(Locale.US, "%.0f%%", safe * PERCENT_SCALE),
            style = MaterialTheme.typography.labelMedium,
            color = color,
        )
        if (totalBytes > 0) {
            Spacer(Modifier.width(Spacing.sm))
            MetaText(formatBytes(totalBytes))
        }
    }
}

@Composable
private fun TaskTimeLine(info: TaskInfo) {
    val start = formatDateTime(info.startTime)
    val end = formatDateTime(info.endTime)
    val text = buildList {
        if (start.isNotBlank()) add("开始 $start")
        if (end.isNotBlank()) add("结束 $end")
    }.joinToString("  ·  ")
    if (text.isNotBlank()) MetaText(text)
}

@Composable
internal fun TaskActions(
    task: TypedTask,
    isDoneTab: Boolean,
    busy: Boolean,
    callbacks: TaskCallbacks,
    /** 卡片上显示「详细」按钮；详情弹窗内传 null 不显示 */
    onDetail: (() -> Unit)? = null,
) {
    val danger = OpenListTheme.colors.danger
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        onDetail?.let { TextButton(onClick = it) { Text("详细") } }
        if (!isDoneTab) {
            TextButton(onClick = { callbacks.onCancel(task) }, enabled = !busy) { Text("取消", color = danger) }
        } else {
            if (task.task.isFailed) {
                TextButton(onClick = { callbacks.onRetry(task) }, enabled = !busy) { Text("重试") }
            }
            TextButton(onClick = { callbacks.onDelete(task) }, enabled = !busy) { Text("删除", color = danger) }
        }
    }
}

@Composable
internal fun LocalUploadCard(upload: LocalUpload, onCancel: () -> Unit) {
    val colors = OpenListTheme.colors
    val failed = upload.state == WorkInfo.State.FAILED
    OlCard {
        Column(Modifier.padding(Spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBlock(icon = Icons.Outlined.Upload, tint = colors.success, background = colors.successSoft)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    upload.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                StatusPill(text = upload.stateLabel, kind = localPillKind(upload.state))
            }
            if (upload.state == WorkInfo.State.RUNNING) {
                Spacer(Modifier.height(Spacing.md))
                ProgressLine(fraction = upload.progress ?: 0f, totalBytes = 0, color = colors.success)
            }
            if (failed && !upload.error.isNullOrBlank()) {
                Spacer(Modifier.height(Spacing.sm))
                Text(upload.error, style = MaterialTheme.typography.bodySmall, color = colors.danger, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (upload.isActive) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onCancel) { Text("取消", color = colors.danger) }
                }
            }
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

internal fun typeIcon(type: TaskType): ImageVector = when (type) {
    TaskType.UPLOAD -> Icons.Outlined.CloudUpload
    TaskType.COPY -> Icons.Outlined.ContentCopy
    TaskType.MOVE -> Icons.AutoMirrored.Outlined.DriveFileMove
    TaskType.OFFLINE_DOWNLOAD -> Icons.Outlined.CloudDownload
    TaskType.OFFLINE_TRANSFER -> Icons.Outlined.CloudSync
    TaskType.DECOMPRESS -> Icons.Outlined.Unarchive
    TaskType.DECOMPRESS_UPLOAD -> Icons.Outlined.FolderZip
}

internal fun pillKind(state: Int): PillKind = when (state) {
    TaskState.RUNNING, TaskState.BEFORE_RETRY -> PillKind.ACCENT
    TaskState.SUCCEEDED -> PillKind.SUCCESS
    TaskState.ERRORED, TaskState.FAILED, TaskState.FAILING -> PillKind.DANGER
    TaskState.CANCELING, TaskState.WAITING_RETRY -> PillKind.WARNING
    else -> PillKind.NEUTRAL
}

private fun localPillKind(state: WorkInfo.State): PillKind = when (state) {
    WorkInfo.State.RUNNING -> PillKind.ACCENT
    WorkInfo.State.FAILED -> PillKind.DANGER
    WorkInfo.State.SUCCEEDED -> PillKind.SUCCESS
    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.CANCELLED -> PillKind.NEUTRAL
}

private const val PERCENT_SCALE = 100f
