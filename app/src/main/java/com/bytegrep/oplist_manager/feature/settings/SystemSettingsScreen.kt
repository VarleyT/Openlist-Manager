package com.bytegrep.oplist_manager.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.SettingGroup
import com.bytegrep.oplist_manager.data.model.SettingItem

/** 有未保存修改时待执行的离开动作 */
private sealed interface PendingLeave {
    data object Back : PendingLeave
    data class SwitchGroup(val group: SettingGroup) : PendingLeave
}

@Composable
fun SystemSettingsScreen(
    onBack: () -> Unit,
    viewModel: SystemSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<PendingLeave?>(null) }

    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val requestBack: () -> Unit = {
        if (state.hasChanges) {
            pending = PendingLeave.Back
        } else {
            onBack()
        }
    }
    BackHandler(enabled = state.hasChanges) { pending = PendingLeave.Back }

    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { SettingsTopBar(state = state, onBack = requestBack, onSave = viewModel::save) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.isAdmin) {
                EmptyState(title = "需要管理员权限", message = "仅管理员可以修改系统设置", icon = Icons.Outlined.Lock)
            } else {
                GroupSelector(
                    selected = state.group,
                    onSelect = { group ->
                        if (group == state.group) return@GroupSelector
                        if (state.hasChanges) {
                            pending = PendingLeave.SwitchGroup(group)
                        } else {
                            viewModel.selectGroup(group)
                        }
                    },
                )
                Box(Modifier.weight(1f)) {
                    SettingsBody(state = state, onRetry = viewModel::load, onValueChange = viewModel::onValueChange)
                }
            }
        }
    }

    pending?.let { leave ->
        ConfirmDialog(
            title = "放弃未保存的修改？",
            message = "当前分组有 ${state.edits.size} 项修改尚未保存，继续操作将丢弃这些修改。",
            confirmLabel = "放弃修改",
            destructive = true,
            onConfirm = {
                viewModel.discardChanges()
                when (leave) {
                    PendingLeave.Back -> onBack()
                    is PendingLeave.SwitchGroup -> viewModel.selectGroup(leave.group)
                }
            },
            onDismiss = { pending = null },
        )
    }
}

@Composable
private fun SettingsTopBar(state: SystemSettingsUiState, onBack: () -> Unit, onSave: () -> Unit) {
    LargeTitleBar(
        title = "系统设置",
        onBack = onBack,
        subtitle = if (state.hasChanges) "${state.edits.size} 项未保存" else null,
        actions = {
            if (state.isSaving) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(horizontal = Spacing.md).size(20.dp))
            } else if (state.isAdmin) {
                TextButton(onClick = onSave, enabled = state.hasChanges) { Text("保存") }
            }
        },
    )
}

@Composable
private fun GroupSelector(selected: SettingGroup, onSelect: (SettingGroup) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SettingGroup.entries.forEach { group ->
            FilterChip(selected = group == selected, onClick = { onSelect(group) }, label = { Text(group.label) })
        }
    }
}

@Composable
private fun SettingsBody(
    state: SystemSettingsUiState,
    onRetry: () -> Unit,
    onValueChange: (SettingItem, String) -> Unit,
) {
    var editingText by rememberSaveable { mutableStateOf<String?>(null) }
    when {
        state.isLoading && state.items.isEmpty() -> LoadingState()
        state.error != null && state.items.isEmpty() -> ErrorState(message = state.error, onRetry = onRetry)
        state.items.isEmpty() -> EmptyState(title = "该分组暂无设置项")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.md, bottom = Spacing.xxl),
        ) {
            item(key = "settings_${state.group.id}") {
                SettingsCard(state = state, onValueChange = onValueChange, onEditText = { editingText = it.key })
            }
        }
    }
    val editingItem = editingText?.let { key -> state.items.firstOrNull { it.key == key } }
    if (editingItem != null) {
        MultilineEditDialog(
            title = settingLabel(editingItem.key),
            initial = state.valueOf(editingItem),
            onConfirm = { onValueChange(editingItem, it) },
            onDismiss = { editingText = null },
        )
    }
}
