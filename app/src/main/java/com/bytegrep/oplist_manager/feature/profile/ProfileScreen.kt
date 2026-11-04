package com.bytegrep.oplist_manager.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.session.Session

@Composable
fun ProfileScreen(
    onOpenServers: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by viewModel.currentSession.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.loggedOut.collect { onLoggedOut() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        LargeTitleBar(title = "我的")
        Column(Modifier.padding(horizontal = Spacing.page)) {
            AccountCard(user, session, onOpenServers)
            if (user?.isAdmin == true) {
                SectionHeader("管理")
                GroupCard(
                    rows = listOf(
                        { GroupRow("用户管理", subtitle = "账号、角色与权限", icon = Icons.Outlined.Group, onClick = onOpenUsers) },
                        { GroupRow("任务中心", subtitle = "上传、复制、离线下载任务", icon = Icons.AutoMirrored.Outlined.ListAlt, onClick = onOpenTasks) },
                        { GroupRow("系统设置", subtitle = "OpenList 站点与全局配置", icon = Icons.Outlined.AdminPanelSettings, onClick = onOpenSystemSettings) },
                    ),
                )
            } else {
                SectionHeader("任务")
                GroupCard(rows = listOf({ GroupRow("任务中心", subtitle = "我的上传、复制任务", icon = Icons.AutoMirrored.Outlined.ListAlt, onClick = onOpenTasks) }))
            }
            SectionHeader("基础设置")
            GroupCard(
                rows = listOf(
                    { GroupRow("服务器连接", subtitle = "添加、切换或移除服务器", icon = Icons.Outlined.Dns, onClick = onOpenServers) },
                    { GroupRow("外观与主题", icon = Icons.Outlined.Palette, iconTint = OpenListTheme.colors.ink2, iconBackground = OpenListTheme.colors.fill, trailingText = settings.themeMode.label, onClick = onOpenAppSettings) },
                    { GroupRow("应用设置", subtitle = "文件布局、动态配色", icon = Icons.Outlined.Tune, onClick = onOpenAppSettings) },
                ),
            )
            SectionHeader("系统与信息")
            GroupCard(
                rows = listOf(
                    { GroupRow("服务器版本", icon = Icons.Outlined.Terminal, iconTint = OpenListTheme.colors.ink2, iconBackground = OpenListTheme.colors.fill, trailingText = "OpenList ${session?.server?.version.orEmpty()}", showChevron = false) },
                    { GroupRow("关于 OpenList Manager", icon = Icons.Outlined.Info, iconTint = OpenListTheme.colors.ink2, iconBackground = OpenListTheme.colors.fill, trailingText = "v${BuildConfig.VERSION_NAME}", onClick = onOpenAbout) },
                ),
            )
            Spacer(Modifier.height(Spacing.xl))
            OlCard(onClick = { confirmLogout = true }) {
                Row(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = OpenListTheme.colors.danger)
                    Spacer(Modifier.width(Spacing.sm))
                    Text("断开连接并退出登录", style = MaterialTheme.typography.titleSmall, color = OpenListTheme.colors.danger)
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            message = "将注销当前服务器会话并清除本机保存的 Token 与密码。服务器配置会保留在历史连接中。",
            confirmLabel = "退出",
            destructive = true,
            onConfirm = viewModel::logout,
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun AccountCard(user: UserDto?, session: Session?, onOpenServers: () -> Unit) {
    val colors = OpenListTheme.colors
    OlCard {
        Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
            IconBlock(Icons.Outlined.AccountCircle, tint = colors.surface, background = colors.accent, size = 60.dp, iconSize = 36.dp, circle = true)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(user?.username ?: session?.server?.username.orEmpty(), style = MaterialTheme.typography.titleLarge, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(Spacing.sm))
                    TagChip(UserRole.label(user?.role ?: UserRole.GENERAL), color = colors.accent, background = colors.accentSoft)
                }
                Text(permissionSummary(user), style = MaterialTheme.typography.bodySmall, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = Spacing.lg), color = colors.hairline)
        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            IconBlock(Icons.Outlined.Dns, size = 36.dp, iconSize = 20.dp, tint = colors.accent, background = colors.accentSoft)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(session?.server?.name.orEmpty(), style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(session?.server?.baseUrl?.trimEnd('/').orEmpty(), style = MaterialTheme.typography.bodySmall, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onOpenServers) { Text("切换") }
        }
    }
}

private fun permissionSummary(user: UserDto?): String {
    if (user == null) return "正在加载账号信息…"
    if (user.isAdmin) return "根路径 ${user.basePath} · 权限全开"
    val granted = UserPermission.entries.count { user.hasPermission(it.bit) }
    return "根路径 ${user.basePath} · 已授予 $granted 项权限"
}
