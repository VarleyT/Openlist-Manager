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
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    // 只放 App 本身和当前账号相关的内容，服务器管理入口统一在「概览」
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        LargeTitleBar(title = "我的")
        Column(Modifier.padding(horizontal = Spacing.page)) {
            AccountCard(user, session)
            SectionHeader("设置")
            GroupCard(
                rows = listOf(
                    { GroupRow("应用设置", subtitle = "主题、文件布局、动态配色", icon = Icons.Outlined.Tune, trailingText = settings.themeMode.label, onClick = onOpenAppSettings) },
                    { GroupRow("服务器连接", subtitle = "管理本机保存的服务器", icon = Icons.Outlined.Dns, onClick = onOpenServers) },
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
private fun AccountCard(user: UserDto?, session: Session?) {
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
                serverSummary(session)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** 例如「OpenList · v4.1.10」，无服务器信息时返回 null */
private fun serverSummary(session: Session?): String? {
    val server = session?.server ?: return null
    return listOf(server.name, server.version).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null }
}

private fun permissionSummary(user: UserDto?): String {
    if (user == null) return "正在加载账号信息…"
    if (user.isAdmin) return "根路径 ${user.basePath} · 权限全开"
    val granted = UserPermission.entries.count { user.hasPermission(it.bit) }
    return "根路径 ${user.basePath} · 已授予 $granted 项权限"
}
