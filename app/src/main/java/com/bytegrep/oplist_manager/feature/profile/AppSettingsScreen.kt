package com.bytegrep.oplist_manager.feature.profile

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.datastore.ThemeMode
import com.bytegrep.oplist_manager.core.designsystem.component.AppLogo
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.update.AppLinks
import com.bytegrep.oplist_manager.data.update.AppRelease

@Composable
fun AppSettingsScreen(onBack: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        LargeTitleBar(title = "应用设置", onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.page)) {
            SectionHeader("外观")
            OlCard {
                Column(Modifier.padding(Spacing.lg)) {
                    Text("主题模式", style = MaterialTheme.typography.titleSmall, color = OpenListTheme.colors.ink)
                    Spacer(Modifier.height(Spacing.md))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            ) { Text(mode.label) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
            val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            GroupCard(
                rows = listOf(
                    {
                        GroupRow(
                            "动态配色",
                            subtitle = if (dynamicSupported) "跟随系统壁纸取色（Material You）" else "需要 Android 12 及以上",
                            icon = Icons.Outlined.ColorLens,
                            showChevron = false,
                            trailing = {
                                Switch(checked = settings.dynamicColor, onCheckedChange = viewModel::setDynamicColor, enabled = dynamicSupported)
                            },
                        )
                    },
                ),
            )
            SectionHeader("文件")
            GroupCard(
                rows = listOf(
                    {
                        GroupRow(
                            "网格布局",
                            subtitle = "文件列表默认以网格方式显示",
                            icon = Icons.Outlined.GridView,
                            showChevron = false,
                            trailing = { Switch(checked = settings.fileGrid, onCheckedChange = viewModel::setFileGrid) },
                        )
                    },
                ),
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit, viewModel: AboutViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val open: (String) -> Unit = { url ->
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    }
    (updateState as? UpdateUiState.Available)?.takeIf { it.dialogVisible }?.let { state ->
        UpdateDialog(
            release = state.release,
            onDownload = {
                viewModel.dismissDialog()
                open(state.release.pageUrl)
            },
            onDismiss = viewModel::dismissDialog,
        )
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        LargeTitleBar(title = "关于", onBack = onBack)
        Column(Modifier.padding(horizontal = Spacing.page), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(Spacing.xl))
            AppLogo()
            Spacer(Modifier.height(Spacing.md))
            Text("OpenList Manager", style = MaterialTheme.typography.headlineSmall, color = OpenListTheme.colors.ink)
            Text("版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink2)
            Spacer(Modifier.height(Spacing.sm))
            Text(
                "OpenList 的 Android 远程管理客户端，通过官方 REST API 管理文件、存储、用户与任务。",
                style = MaterialTheme.typography.bodyMedium,
                color = OpenListTheme.colors.ink2,
            )
            SectionHeader("更新")
            GroupCard(
                rows = listOf(
                    { UpdateRow(updateState, onClick = viewModel::checkUpdate) },
                    { GroupRow("项目仓库", subtitle = AppLinks.REPO_DISPLAY, icon = Icons.Outlined.Code, onClick = { open(AppLinks.REPO_URL) }) },
                ),
            )
            SectionHeader("相关链接")
            GroupCard(
                rows = listOf(
                    { GroupRow("OpenList 项目", subtitle = "github.com/OpenListTeam/OpenList", icon = Icons.Outlined.Code, onClick = { open("https://github.com/OpenListTeam/OpenList") }) },
                    { GroupRow("API 文档", subtitle = "fox.oplist.org", icon = Icons.Outlined.Api, onClick = { open("https://fox.oplist.org/") }) },
                ),
            )
            SectionHeader("安全说明")
            OlCard {
                Row(Modifier.padding(Spacing.lg)) {
                    IconBlock(Icons.Outlined.Security, size = 36.dp, iconSize = 20.dp, tint = OpenListTheme.colors.success, background = OpenListTheme.colors.successSoft)
                    Spacer(Modifier.padding(start = Spacing.md))
                    Text(
                        "Token 与密码使用 Android Keystore（AES-256-GCM）加密保存，不参与云备份；不同服务器之间的认证信息相互隔离；TLS 证书错误不会被忽略。",
                        style = MaterialTheme.typography.bodySmall,
                        color = OpenListTheme.colors.ink2,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun UpdateRow(state: UpdateUiState, onClick: () -> Unit) {
    val subtitle = when (state) {
        UpdateUiState.Idle -> "当前版本 v${BuildConfig.VERSION_NAME}"
        UpdateUiState.Checking -> "正在检查…"
        UpdateUiState.UpToDate -> "已是最新版本"
        is UpdateUiState.Available -> "发现新版本 v${state.release.version}"
        is UpdateUiState.Failed -> state.message
    }
    GroupRow(
        "检查更新",
        subtitle = subtitle,
        icon = Icons.Outlined.SystemUpdate,
        showChevron = state !is UpdateUiState.Checking,
        onClick = onClick,
        trailing = if (state is UpdateUiState.Checking) {
            { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OpenListTheme.colors.ink3) }
        } else {
            null
        },
    )
}

@Composable
private fun UpdateDialog(release: AppRelease, onDownload: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${release.version}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenListTheme.colors.ink3,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    release.notes.ifEmpty { "暂无更新说明" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = OpenListTheme.colors.ink2,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDownload) { Text("前往下载") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("稍后") } },
    )
}
