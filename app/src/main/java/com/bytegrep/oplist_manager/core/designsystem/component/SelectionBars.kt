package com.bytegrep.oplist_manager.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

/** 多选模式顶栏：关闭 + 已选数量 + 全选/全不选 */
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

/** 多选模式底部操作栏，内容为若干 [SelectionBarAction] */
@Composable
fun SelectionActionBar(content: @Composable RowScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(OpenListTheme.colors.surface)) {
        HorizontalDivider(color = OpenListTheme.colors.hairline)
        Row(
            Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
            content = content,
        )
    }
}

/** 底部操作栏按钮：图标 + 文字，禁用时置灰 */
@Composable
fun SelectionBarAction(icon: ImageVector, label: String, enabled: Boolean, tint: Color, onClick: () -> Unit) {
    val color = if (enabled) tint else OpenListTheme.colors.ink3
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.button))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Spacing.xl, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
