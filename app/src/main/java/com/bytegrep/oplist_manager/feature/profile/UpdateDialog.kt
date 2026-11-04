package com.bytegrep.oplist_manager.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.update.AppRelease

/**
 * 新版本提醒弹窗。[onIgnore] 非空时显示「忽略此版本」（启动自动提醒使用）。
 */
@Composable
fun UpdateDialog(
    release: AppRelease,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onIgnore: (() -> Unit)? = null,
) {
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
        dismissButton = {
            Row {
                if (onIgnore != null) {
                    TextButton(onClick = onIgnore) { Text("忽略此版本", color = OpenListTheme.colors.ink3) }
                }
                TextButton(onClick = onDismiss) { Text("稍后") }
            }
        },
    )
}
