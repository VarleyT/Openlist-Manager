package com.bytegrep.oplist_manager.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.AppLogo
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.PillKind
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.component.StatCell
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenStorage: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenServers: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
        ) {
            AppBrandBar(onOpenServers)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("服务器概览", style = MaterialTheme.typography.headlineMedium, color = OpenListTheme.colors.ink, modifier = Modifier.weight(1f))
                Text(
                    if (state.isRefreshing) "同步中…" else "已同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpenListTheme.colors.ink3,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            ServerCard(state, onOpenServers)
            SectionHeader("快捷入口", large = true)
            QuickEntries(state.isAdmin, onOpenTasks, onOpenUsers, onOpenSystemSettings, onOpenServers)
            SectionHeader("异常与提醒", large = true)
            AlertsCard(state, onOpenStorage, onOpenTasks)
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun AppBrandBar(onSwitchServer: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        AppLogo(size = 28.dp)
        Spacer(Modifier.width(Spacing.sm))
        Text("OpenList Manager", style = MaterialTheme.typography.titleMedium, color = OpenListTheme.colors.ink, modifier = Modifier.weight(1f))
        IconButton(onClick = onSwitchServer) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = "切换服务器", tint = OpenListTheme.colors.ink)
        }
    }
}

@Composable
private fun ServerCard(state: DashboardUiState, onOpenServers: () -> Unit) {
    OlCard(onClick = onOpenServers) {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconBlock(Icons.Outlined.Dns, size = 48.dp)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(state.server?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = OpenListTheme.colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(state.server?.baseUrl?.trimEnd('/').orEmpty(), style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            when (state.status) {
                ServerStatus.CHECKING -> StatusPill("检测中", PillKind.NEUTRAL)
                ServerStatus.ONLINE -> StatusPill("在线", PillKind.SUCCESS)
                ServerStatus.OFFLINE -> StatusPill("离线", PillKind.DANGER)
            }
        }
        state.statusError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.danger, modifier = Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.md))
        }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = OpenListTheme.colors.hairline)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val version = state.server?.version?.ifBlank { "未知" } ?: "未知"
            if (state.isAdmin) {
                StatCell(state.storages.size.toString(), "存储挂载", Modifier.weight(1f))
                VerticalDivider(Modifier.fillMaxHeight().padding(vertical = Spacing.lg), color = OpenListTheme.colors.hairline)
                StatCell(state.workingCount.toString(), "正常", Modifier.weight(1f), OpenListTheme.colors.success)
                VerticalDivider(Modifier.fillMaxHeight().padding(vertical = Spacing.lg), color = OpenListTheme.colors.hairline)
                StatCell(state.errorStorages.size.toString(), "异常", Modifier.weight(1f), OpenListTheme.colors.danger)
            } else {
                StatCell(version, "服务版本", Modifier.weight(1f))
                StatCell(state.runningTasks.toString(), "进行中任务", Modifier.weight(1f), OpenListTheme.colors.accent)
            }
        }
    }
}

/** 只放「不是 Tab、但常用」的功能；文件、存储已是底部 Tab，不在这里重复 */
@Composable
private fun QuickEntries(
    isAdmin: Boolean,
    onOpenTasks: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenServers: () -> Unit,
) {
    val adminOnly = "需要管理员权限"
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            QuickEntry(Icons.AutoMirrored.Outlined.ListAlt, "任务队列", "上传、复制与下载", onOpenTasks, Modifier.weight(1f))
            QuickEntry(Icons.Outlined.Group, "用户管理", if (isAdmin) "用户与权限设置" else adminOnly, onOpenUsers, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            QuickEntry(Icons.Outlined.AdminPanelSettings, "系统设置", if (isAdmin) "站点与全局配置" else adminOnly, onOpenSystemSettings, Modifier.weight(1f))
            // 离线下载实现前暂放「服务器连接」
            QuickEntry(Icons.Outlined.Dns, "服务器连接", "添加、切换服务器", onOpenServers, Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier) {
    OlCard(onClick = onClick, modifier = modifier) {
        Column(Modifier.padding(Spacing.lg)) {
            IconBlock(icon, tint = OpenListTheme.colors.accent, background = OpenListTheme.colors.accentSoft, size = 44.dp)
            Spacer(Modifier.height(Spacing.md))
            Text(title, style = MaterialTheme.typography.titleMedium, color = OpenListTheme.colors.ink)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AlertsCard(state: DashboardUiState, onOpenStorage: () -> Unit, onOpenTasks: () -> Unit) {
    val colors = OpenListTheme.colors
    val rows = buildList<@Composable () -> Unit> {
        state.errorStorages.forEach { storage ->
            add {
                GroupRow(
                    title = "${storage.mountPath} 挂载异常",
                    subtitle = storage.status,
                    icon = Icons.Outlined.ErrorOutline,
                    iconTint = colors.danger,
                    iconBackground = colors.dangerSoft,
                    onClick = onOpenStorage,
                )
            }
        }
        state.storageError?.let { msg ->
            add { GroupRow(title = "存储状态获取失败", subtitle = msg, icon = Icons.Outlined.SyncProblem, iconTint = colors.warning, iconBackground = colors.warningSoft) }
        }
        if (state.failedTasks > 0) {
            add { GroupRow(title = "${state.failedTasks} 个任务出错", subtitle = "前往任务中心查看详情或重试", icon = Icons.Outlined.ErrorOutline, iconTint = colors.danger, iconBackground = colors.dangerSoft, onClick = onOpenTasks) }
        }
        if (state.runningTasks > 0) {
            add { GroupRow(title = "${state.runningTasks} 个任务进行中", subtitle = "上传、复制、离线下载等后台任务", icon = Icons.AutoMirrored.Outlined.ListAlt, onClick = onOpenTasks) }
        }
    }
    if (rows.isEmpty()) {
        OlCard {
            Box(Modifier.fillMaxWidth()) {
                GroupRow(
                    title = if (state.status == ServerStatus.OFFLINE) "无法获取服务器状态" else "一切正常",
                    subtitle = if (state.status == ServerStatus.OFFLINE) "下拉刷新重试" else "所有挂载与任务运行正常",
                    icon = Icons.Outlined.CheckCircle,
                    iconTint = colors.success,
                    iconBackground = colors.successSoft,
                    showChevron = false,
                )
            }
        }
    } else {
        GroupCard(rows = rows)
    }
}
