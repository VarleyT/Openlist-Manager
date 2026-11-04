package com.bytegrep.oplist_manager.feature.storage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.PillKind
import com.bytegrep.oplist_manager.core.designsystem.component.StatCell
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.Storage

private val CLOUD_HINTS = listOf(
    "s3", "cloud", "drive", "yun", "pan", "oss", "cos", "obs", "onedrive", "dropbox",
    "quark", "baidu", "aliyun", "115", "123", "189", "lanzou", "teambition", "mega", "pikpak",
)

/** 按驱动类型选择图标 */
fun driverIcon(driver: String): ImageVector {
    val lower = driver.lowercase()
    return when {
        lower == "local" -> Icons.Outlined.Dns
        lower.contains("webdav") || lower.contains("ftp") || lower.contains("smb") -> Icons.Outlined.Layers
        CLOUD_HINTS.any { lower.contains(it) } -> Icons.Outlined.Cloud
        else -> Icons.Outlined.FolderZip
    }
}

/** 卡片标题：挂载路径最后一段 */
fun Storage.displayName(): String =
    mountPath.trimEnd('/').substringAfterLast('/').ifBlank { mountPath.ifBlank { "/" } }

@Composable
fun StorageOverviewCard(total: Int, working: Int, error: Int, disabled: Int, modifier: Modifier = Modifier) {
    val colors = OpenListTheme.colors
    OlCard(modifier = modifier) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconBlock(icon = Icons.Outlined.Storage, tint = colors.accent, background = colors.accentSoft, size = 48.dp, iconSize = 26.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("存储概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.ink)
                val desc = if (disabled > 0) "管理 OpenList 的存储挂载 · 已禁用 $disabled 项" else "管理 OpenList 的存储挂载"
                Text(desc, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.hairline)
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            StatCell(total.toString(), "存储总数", Modifier.weight(1f))
            StatCell(working.toString(), "正常", Modifier.weight(1f), valueColor = colors.success)
            StatCell(error.toString(), "异常", Modifier.weight(1f), valueColor = colors.danger)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageCard(
    storage: Storage,
    isBusy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenListTheme.colors
    OlCard(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        borderColor = if (storage.isError) colors.danger.copy(alpha = 0.3f) else colors.edge,
    ) {
        StorageCardHeader(storage)
        if (storage.isError) StorageErrorBar(storage.status, isBusy, onReload)
    }
}

@Composable
private fun StorageCardHeader(storage: Storage) {
    val colors = OpenListTheme.colors
    Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        IconBlock(
            icon = driverIcon(storage.driver),
            tint = if (storage.isError) colors.accent else colors.ink2,
            background = if (storage.isError) colors.accentSoft else colors.fill,
            size = 48.dp,
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    storage.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(Spacing.sm))
                TagChip(storage.driver)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                storage.mountPath,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        StorageStatusPill(storage)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.ink3)
    }
}

@Composable
fun StorageStatusPill(storage: Storage) {
    when {
        storage.disabled -> StatusPill("已禁用", PillKind.NEUTRAL)
        storage.isWorking -> StatusPill("正常", PillKind.SUCCESS)
        else -> StatusPill("异常", PillKind.DANGER)
    }
}

@Composable
private fun StorageErrorBar(message: String, isBusy: Boolean, onReload: () -> Unit) {
    val colors = OpenListTheme.colors
    HorizontalDivider(color = colors.danger.copy(alpha = 0.15f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.dangerSoft)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Spacing.sm))
        Text(
            message.ifBlank { "挂载异常" },
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Button(
            onClick = onReload,
            enabled = !isBusy,
            shape = RoundedCornerShape(Radius.pill),
            colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp),
        ) {
            if (isBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp), color = colors.surface)
            } else {
                Text("重新加载", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageActionSheet(
    storage: Storage,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = OpenListTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                storage.mountPath,
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
            GroupRow(title = "编辑", icon = Icons.Outlined.Edit, showChevron = false, onClick = onEdit)
            GroupRow(
                title = if (storage.disabled) "启用" else "禁用",
                icon = if (storage.disabled) Icons.Outlined.CheckCircle else Icons.Outlined.Block,
                iconTint = if (storage.disabled) colors.success else colors.warning,
                iconBackground = if (storage.disabled) colors.successSoft else colors.warningSoft,
                showChevron = false,
                onClick = onToggle,
            )
            GroupRow(
                title = "删除",
                icon = Icons.Outlined.Delete,
                iconTint = colors.danger,
                iconBackground = colors.dangerSoft,
                titleColor = colors.danger,
                showChevron = false,
                onClick = onDelete,
            )
        }
    }
}
