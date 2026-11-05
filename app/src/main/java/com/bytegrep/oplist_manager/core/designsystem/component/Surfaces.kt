package com.bytegrep.oplist_manager.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

/** 行尾说明文字最大宽度，避免挤压标题 */
private val TRAILING_TEXT_MAX_WIDTH = 160.dp

/** 白色圆角卡片 + 极淡描边 */
@Composable
fun OlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = OpenListTheme.colors.edge,
    containerColor: Color = OpenListTheme.colors.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) { Column(content = content) }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.dp, borderColor),
        ) { Column(content = content) }
    }
}

/** 图标块：圆角方形浅底 + 图标 */
@Composable
fun IconBlock(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = OpenListTheme.colors.ink2,
    background: Color = OpenListTheme.colors.fill,
    size: Dp = Spacing.iconBlock,
    iconSize: Dp = 22.dp,
    circle: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(if (circle) CircleShape else RoundedCornerShape(Radius.iconBlock))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** 分组列表行：图标 + 标题/说明 + 尾部文字/箭头 */
@Composable
fun GroupRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = OpenListTheme.colors.accent,
    iconBackground: Color = OpenListTheme.colors.accentSoft,
    trailingText: String? = null,
    showChevron: Boolean = true,
    titleColor: Color = OpenListTheme.colors.ink,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Spacing.rowMinHeight)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBlock(icon = icon, tint = iconTint, background = iconBackground, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenListTheme.colors.ink2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!trailingText.isNullOrBlank()) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = OpenListTheme.colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = TRAILING_TEXT_MAX_WIDTH),
            )
        }
        trailing?.invoke(this)
        if (showChevron && onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = OpenListTheme.colors.ink3,
            )
        }
    }
}

/** 分组卡片：内部行之间使用细分割线 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    OlCard(modifier = modifier) {
        rows.forEachIndexed { index, row ->
            row()
            if (index < rows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = Spacing.lg),
                    color = OpenListTheme.colors.hairline,
                )
            }
        }
    }
}

/** 分组标题，例如 “基础设置” */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xl, bottom = Spacing.sm, start = if (large) 0.dp else Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelMedium,
            color = if (large) OpenListTheme.colors.ink else OpenListTheme.colors.ink2,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** 大标题顶栏：可选返回按钮 + 28sp 标题 + 右侧操作 */
@Composable
fun LargeTitleBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) Spacing.xs else Spacing.page, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = OpenListTheme.colors.ink)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                color = OpenListTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, content = actions)
    }
}

/** 统计单元：数值 + 标签（概览卡片三栏） */
@Composable
fun StatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = OpenListTheme.colors.ink,
) {
    Column(modifier = modifier.padding(vertical = Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = valueColor)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink2)
    }
}
