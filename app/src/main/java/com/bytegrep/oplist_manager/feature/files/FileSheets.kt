package com.bytegrep.oplist_manager.feature.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.SelectionActionBar
import com.bytegrep.oplist_manager.core.designsystem.component.SelectionBarAction
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.FsObject

/** 底部菜单项 */
@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = OpenListTheme.colors.ink,
) {
    val color = if (enabled) tint else OpenListTheme.colors.ink3
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .heightIn(min = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(Modifier.width(Spacing.lg))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OlSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = OpenListTheme.colors.surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.md)) { content() }
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = OpenListTheme.colors.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
    )
}

/** FAB “+” 菜单 */
@Composable
fun AddSheet(canWrite: Boolean, onUpload: () -> Unit, onNewFolder: () -> Unit, onDismiss: () -> Unit) {
    OlSheet(onDismiss) {
        SheetTitle("新建")
        SheetAction(Icons.Outlined.UploadFile, "上传文件", onUpload, enabled = canWrite)
        SheetAction(Icons.Outlined.CreateNewFolder, "新建文件夹", onNewFolder, enabled = canWrite)
        if (!canWrite) {
            Text(
                "当前目录无写入权限",
                style = MaterialTheme.typography.bodySmall,
                color = OpenListTheme.colors.ink3,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.sm),
            )
        }
    }
}

/** 排序方式选择 */
@Composable
fun SortSheet(current: FileSort, onSelect: (FileSort) -> Unit, onDismiss: () -> Unit) {
    OlSheet(onDismiss) {
        SheetTitle("排序方式")
        SortField.entries.forEach { field ->
            listOf(true, false).forEach { asc ->
                val option = FileSort(field, asc)
                val selected = option == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = Spacing.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${field.label}（${sortDirectionLabel(field, asc)}）",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) OpenListTheme.colors.accent else OpenListTheme.colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = OpenListTheme.colors.accent)
                }
            }
        }
    }
}

private fun sortDirectionLabel(field: SortField, asc: Boolean): String = when (field) {
    SortField.NAME -> if (asc) "A→Z" else "Z→A"
    SortField.SIZE -> if (asc) "从小到大" else "从大到小"
    SortField.MODIFIED -> if (asc) "从旧到新" else "从新到旧"
}

/** 单个文件的上下文菜单 */
@Composable
fun ItemMenuSheet(
    item: FsObject,
    permissions: FilePermissions,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    OlSheet(onDismiss) {
        ItemMenuHeader(item)
        HorizontalDivider(color = OpenListTheme.colors.hairline)
        SheetAction(Icons.AutoMirrored.Outlined.OpenInNew, if (item.isDir) "打开" else "预览", onOpen)
        if (!item.isDir) SheetAction(Icons.Outlined.Download, "下载", onDownload)
        SheetAction(Icons.Outlined.DriveFileRenameOutline, "重命名", onRename, enabled = permissions.canRename)
        SheetAction(Icons.AutoMirrored.Outlined.DriveFileMove, "移动", onMove, enabled = permissions.canMove)
        SheetAction(Icons.Outlined.ContentCopy, "复制", onCopy, enabled = permissions.canCopy)
        SheetAction(Icons.Outlined.Delete, "删除", onDelete, enabled = permissions.canRemove, tint = OpenListTheme.colors.danger)
    }
}

@Composable
private fun ItemMenuHeader(item: FsObject) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(kind = item.kind(), thumb = item.thumb, size = 40.dp, iconSize = 22.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, color = OpenListTheme.colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle(), style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, maxLines = 1)
        }
    }
}

/** 多选模式底部操作栏 */
@Composable
fun SelectionActionBar(
    count: Int,
    permissions: FilePermissions,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val enabled = count > 0
    SelectionActionBar {
        SelectionBarAction(Icons.AutoMirrored.Outlined.DriveFileMove, "移动", enabled && permissions.canMove, OpenListTheme.colors.ink, onMove)
        SelectionBarAction(Icons.Outlined.ContentCopy, "复制", enabled && permissions.canCopy, OpenListTheme.colors.ink, onCopy)
        SelectionBarAction(Icons.Outlined.Delete, "删除", enabled && permissions.canRemove, OpenListTheme.colors.danger, onDelete)
    }
}

/** 移动/复制目标目录选择器：通过 fs/dirs 逐级浏览 */
@Composable
fun DirPickerSheet(
    picker: DirPickerState,
    onOpen: (String) -> Unit,
    onUp: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OlSheet(onDismiss) {
        SheetTitle("${picker.mode.title}（${picker.names.size} 项）")
        Text(
            picker.path,
            style = MaterialTheme.typography.bodySmall,
            color = OpenListTheme.colors.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Spacer(Modifier.height(Spacing.sm))
        HorizontalDivider(color = OpenListTheme.colors.hairline)
        DirPickerList(picker, onOpen, onUp)
        HorizontalDivider(color = OpenListTheme.colors.hairline)
        DirPickerButtons(picker, onConfirm, onDismiss)
    }
}

@Composable
private fun DirPickerList(picker: DirPickerState, onOpen: (String) -> Unit, onUp: () -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp)) {
        if (picker.path != "/") {
            item { SheetAction(Icons.Outlined.ArrowUpward, "返回上一级", onUp) }
        }
        when {
            picker.isLoading -> item {
                Row(Modifier.fillMaxWidth().padding(Spacing.xl), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            picker.error != null -> item { PickerHint(picker.error, OpenListTheme.colors.danger) }
            picker.dirs.isEmpty() -> item { PickerHint("没有子文件夹", OpenListTheme.colors.ink3) }
            else -> items(picker.dirs, key = { it }) { dir -> PickerDirRow(dir) { onOpen(dir) } }
        }
    }
}

@Composable
private fun PickerHint(text: String, color: Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.padding(Spacing.xl))
}

@Composable
private fun PickerDirRow(name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBlock(Icons.Outlined.Folder, tint = OpenListTheme.colors.folder, background = OpenListTheme.colors.folderSoft, size = 36.dp, iconSize = 20.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = OpenListTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = OpenListTheme.colors.ink3)
    }
}

@Composable
private fun DirPickerButtons(picker: DirPickerState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
        if (picker.path == picker.srcDir) {
            Text("目标目录与当前目录相同", style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3)
            Spacer(Modifier.height(Spacing.sm))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)) { Text("取消") }
            Button(onClick = onConfirm, enabled = picker.canConfirm, modifier = Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)) {
                Text(picker.mode.confirmLabel)
            }
        }
    }
}
