package com.bytegrep.oplist_manager.feature.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

private const val BADGE_MAX = 99

/** IconButton 触控区 48dp、图标 24dp，角标向内收 10dp 贴近图标右上角 */
private val BadgeInset = 10.dp

/** 角标文字：超过上限显示 “99+” */
internal fun taskBadgeText(count: Int): String = if (count > BADGE_MAX) "$BADGE_MAX+" else count.toString()

/** 后台任务入口：进行中任务数大于 0 时显示角标 */
@Composable
fun TaskEntryButton(runningTasks: Int, onClick: () -> Unit) {
    val description = if (runningTasks > 0) "后台任务，$runningTasks 个进行中" else "后台任务"
    // 角标放在按钮外层，避免被 IconButton 的圆形裁切截断；再向图标右上角偏移
    BadgedBox(
        badge = {
            if (runningTasks > 0) {
                Badge(
                    modifier = Modifier.offset(x = -BadgeInset, y = BadgeInset),
                    containerColor = OpenListTheme.colors.accent,
                    contentColor = Color.White,
                ) {
                    Text(taskBadgeText(runningTasks))
                }
            }
        },
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Outlined.SwapVert, contentDescription = description, tint = OpenListTheme.colors.ink)
        }
    }
}

/** 更多菜单：排序方式、显示方式（点击展开列表/网格） */
@Composable
fun FilesMoreMenu(isGrid: Boolean, onOpenSort: () -> Unit, onSetGrid: (Boolean) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var layoutExpanded by remember { mutableStateOf(false) }
    val close = { expanded = false; layoutExpanded = false }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = OpenListTheme.colors.ink)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = close) {
            DropdownMenuItem(
                text = { Text("排序方式") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null) },
                onClick = { close(); onOpenSort() },
            )
            HorizontalDivider(color = OpenListTheme.colors.hairline)
            DropdownMenuItem(
                text = { Text("显示方式") },
                leadingIcon = { Icon(layoutIcon(isGrid), contentDescription = null) },
                trailingIcon = {
                    Icon(
                        if (layoutExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (layoutExpanded) "收起" else "展开",
                    )
                },
                onClick = { layoutExpanded = !layoutExpanded },
            )
            if (layoutExpanded) {
                LayoutItem("列表", layoutIcon(grid = false), selected = !isGrid) { close(); onSetGrid(false) }
                LayoutItem("网格", layoutIcon(grid = true), selected = isGrid) { close(); onSetGrid(true) }
            }
        }
    }
}

private fun layoutIcon(grid: Boolean): ImageVector =
    if (grid) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList

/** 显示方式子项：缩进显示，当前项标蓝并带勾 */
@Composable
private fun LayoutItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) OpenListTheme.colors.accent else OpenListTheme.colors.ink
    DropdownMenuItem(
        text = { Text(label, color = color) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        trailingIcon = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = "当前", tint = color) }
        } else {
            null
        },
        onClick = onClick,
        modifier = Modifier.padding(start = Spacing.xl),
    )
}
