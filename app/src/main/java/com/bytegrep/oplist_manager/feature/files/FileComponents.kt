package com.bytegrep.oplist_manager.feature.files

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.bytegrep.oplist_manager.core.common.formatBytes
import com.bytegrep.oplist_manager.core.common.formatDateTime
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.FsObject

private val AudioColor = Color(0xFFE0457B)
private val TextColor = Color(0xFF0E9AA7)
private val ArchiveColor = Color(0xFF8E44EC)
private const val SOFT_ALPHA = 0.12f

/** 文件分类对应的图标与配色 */
data class FileVisual(val icon: ImageVector, val tint: Color, val background: Color)

@Composable
fun fileVisualOf(kind: FileKind): FileVisual {
    val c = OpenListTheme.colors
    return when (kind) {
        FileKind.FOLDER -> FileVisual(Icons.Outlined.Folder, c.folder, c.folderSoft)
        FileKind.IMAGE -> FileVisual(Icons.Outlined.Image, c.success, c.successSoft)
        FileKind.VIDEO -> FileVisual(Icons.Outlined.PlayCircle, c.accent, c.accentSoft)
        FileKind.AUDIO -> FileVisual(Icons.Outlined.MusicNote, AudioColor, AudioColor.copy(alpha = SOFT_ALPHA))
        FileKind.TEXT -> FileVisual(Icons.Outlined.Description, TextColor, TextColor.copy(alpha = SOFT_ALPHA))
        FileKind.PDF -> FileVisual(Icons.Outlined.PictureAsPdf, c.danger, c.dangerSoft)
        FileKind.ARCHIVE -> FileVisual(Icons.Outlined.FolderZip, ArchiveColor, ArchiveColor.copy(alpha = SOFT_ALPHA))
        FileKind.OTHER -> FileVisual(Icons.AutoMirrored.Outlined.InsertDriveFile, c.ink2, c.fill)
    }
}

fun FsObject.kind(): FileKind = fileKindOf(name, isDir, type)

/** 行副标题：“大小 · 时间”，目录仅显示时间 */
fun FsObject.subtitle(): String {
    val time = formatDateTime(modified)
    return if (isDir) time.ifEmpty { "文件夹" } else listOf(formatBytes(size), time).filter { it.isNotEmpty() }.joinToString(" · ")
}

/** 文件图标块；图片有缩略图时显示缩略图 */
@Composable
fun FileIcon(kind: FileKind, thumb: String, size: Dp, iconSize: Dp, modifier: Modifier = Modifier) {
    val visual = fileVisualOf(kind)
    if (thumb.isNotBlank() && kind != FileKind.FOLDER) {
        AsyncImage(
            model = thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(RoundedCornerShape(Radius.iconBlock)).background(visual.background),
        )
    } else {
        IconBlock(icon = visual.icon, tint = visual.tint, background = visual.background, size = size, iconSize = iconSize, modifier = modifier)
    }
}

/** 分组卡片中某一行的圆角形状 */
fun groupShape(index: Int, count: Int): Shape {
    val r = Radius.card
    val top = if (index == 0) r else 0.dp
    val bottom = if (index == count - 1) r else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    title: String,
    subtitle: String,
    kind: FileKind,
    thumb: String,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onMore: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 68.dp)
            .padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(kind = kind, thumb = thumb, size = 44.dp, iconSize = 24.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = OpenListTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RowTrailing(selectionMode = selectionMode, selected = selected, onMore = onMore)
    }
}

@Composable
private fun RowTrailing(selectionMode: Boolean, selected: Boolean, onMore: (() -> Unit)?) {
    when {
        selectionMode -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { SelectionMark(selected) }
        onMore != null -> IconButton(onClick = onMore) {
            Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = OpenListTheme.colors.ink3)
        }
        else -> Spacer(Modifier.width(Spacing.md))
    }
}

@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
        contentDescription = if (selected) "已选中" else "未选中",
        tint = if (selected) OpenListTheme.colors.accent else OpenListTheme.colors.ink3,
        modifier = modifier.size(24.dp),
    )
}

private val FIXED_LINE_HEIGHT = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FsObject,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenListTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(colors.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(Spacing.md),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.size(Spacing.sm))
            FileIcon(kind = item.kind(), thumb = item.thumb, size = 64.dp, iconSize = 34.dp)
            Spacer(Modifier.size(Spacing.sm))
            // 首末行不按字体度量裁剪，中英文名称行高一致，网格卡片等高
            Text(item.name, style = MaterialTheme.typography.bodyMedium.copy(lineHeightStyle = FIXED_LINE_HEIGHT), fontWeight = FontWeight.SemiBold, color = colors.ink, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(item.subtitle(), style = MaterialTheme.typography.labelSmall, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            if (selectionMode) {
                SelectionMark(selected, Modifier.padding(Spacing.xs))
            } else {
                IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = "更多", tint = colors.ink3)
                }
            }
        }
    }
}

/** 胶囊搜索框 */
@Composable
fun FileSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OpenListTheme.colors
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.page),
        placeholder = { Text("搜索文件、文件夹", color = colors.ink3) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.ink3) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "清除", tint = colors.ink3) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(Radius.pill),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.fill,
            unfocusedContainerColor = colors.fill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = colors.accent,
        ),
    )
}

/** 面包屑：首页图标 / 路径段 */
@Composable
fun Breadcrumb(path: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val colors = OpenListTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onNavigate("/") }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.Home, contentDescription = "根目录", tint = if (segments.isEmpty()) colors.accent else colors.ink)
        }
        segments.forEachIndexed { index, segment ->
            Text("/", color = colors.ink3, modifier = Modifier.padding(horizontal = Spacing.xs))
            val target = "/" + segments.take(index + 1).joinToString("/")
            val isLast = index == segments.lastIndex
            Text(
                text = segment,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isLast) colors.ink else colors.ink2,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (isLast) colors.surface else Color.Transparent)
                    .clickable { onNavigate(target) }
                    .padding(horizontal = Spacing.md, vertical = 6.dp),
            )
        }
    }
}

/** 多选模式顶栏 */
@Composable
fun SelectionTopBar(count: Int, allSelected: Boolean, onToggleAll: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "取消", tint = OpenListTheme.colors.ink) }
        Text("已选 $count 项", style = MaterialTheme.typography.headlineSmall, color = OpenListTheme.colors.ink, modifier = Modifier.weight(1f))
        Text(
            if (allSelected) "全不选" else "全选",
            style = MaterialTheme.typography.labelLarge,
            color = OpenListTheme.colors.accent,
            modifier = Modifier.clip(RoundedCornerShape(Radius.pill)).clickable(onClick = onToggleAll).padding(Spacing.md),
        )
    }
}

/** 上传进度横幅 */
@Composable
fun UploadBannerView(banner: UploadBanner, modifier: Modifier = Modifier) {
    val colors = OpenListTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.page, vertical = Spacing.xs)
            .clip(RoundedCornerShape(Radius.button))
            .background(colors.accentSoft)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val name = banner.currentName?.let { "：$it" }.orEmpty()
        Text("正在上传 ${banner.activeCount} 个文件$name", style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (banner.progress > 0) {
            LinearProgressIndicator(progress = { banner.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.surface)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.surface)
        }
    }
}
