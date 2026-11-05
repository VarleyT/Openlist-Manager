package com.bytegrep.oplist_manager.feature.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.Storage

private val CARD_GAP = 12.dp

@Composable
fun StorageScreen(onAdd: () -> Unit, onEdit: (Int) -> Unit) {
    val viewModel: StorageViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTitleBar(title = "挂载管理") {
                if (state.isAdmin) {
                    IconButton(onClick = viewModel::requestReloadAll) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重新加载全部", tint = OpenListTheme.colors.ink)
                    }
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "添加存储", tint = OpenListTheme.colors.ink)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg).padding(padding)) {
            StorageContent(state, viewModel, onEdit)
        }
    }
    StorageDialogs(state, viewModel, onEdit)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageContent(state: StorageUiState, viewModel: StorageViewModel, onEdit: (Int) -> Unit) {
    when {
        !state.isAdmin -> CenteredScroll {
            EmptyState(title = "需要管理员权限", message = "当前账号无法管理存储挂载", icon = Icons.Outlined.AdminPanelSettings)
        }
        state.isLoading -> LoadingState()
        state.error != null && state.storages.isEmpty() -> CenteredScroll {
            ErrorState(message = state.error, onRetry = viewModel::retry)
        }
        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            StorageList(state, onEdit, viewModel::openSheet, viewModel::reloadStorage)
        }
    }
}

@Composable
private fun CenteredScroll(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        content()
    }
}

@Composable
private fun StorageList(
    state: StorageUiState,
    onEdit: (Int) -> Unit,
    onMore: (Storage) -> Unit,
    onReload: (Storage) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        item(key = "overview") {
            StorageOverviewCard(state.total, state.workingCount, state.errorCount, state.disabledCount)
        }
        item(key = "header") {
            SectionHeader(title = "存储列表", large = true) {
                Text("已同步 ${state.total} 项", style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3)
            }
        }
        storageItems(state, onEdit, onMore, onReload)
    }
}

private fun LazyListScope.storageItems(
    state: StorageUiState,
    onEdit: (Int) -> Unit,
    onMore: (Storage) -> Unit,
    onReload: (Storage) -> Unit,
) {
    if (state.storages.isEmpty()) {
        item(key = "empty") { EmptyState(title = "暂无存储", message = "点击右上角 + 添加第一个存储挂载") }
        return
    }
    items(state.storages, key = { it.id }) { storage ->
        StorageCard(
            storage = storage,
            driverLabel = state.translations.driverLabel(storage.driver),
            isBusy = storage.id in state.busyIds,
            onClick = { onEdit(storage.id) },
            onMore = { onMore(storage) },
            onReload = { onReload(storage) },
        )
    }
}

@Composable
private fun StorageDialogs(state: StorageUiState, viewModel: StorageViewModel, onEdit: (Int) -> Unit) {
    state.sheetTarget?.let { target ->
        StorageActionSheet(
            storage = target,
            onDismiss = viewModel::dismissSheet,
            onEdit = {
                viewModel.dismissSheet()
                onEdit(target.id)
            },
            onToggle = { viewModel.toggleEnabled(target) },
            onDelete = { viewModel.requestDelete(target) },
        )
    }
    state.pendingDelete?.let { target ->
        ConfirmDialog(
            title = "删除存储",
            message = "确定删除挂载 ${target.mountPath} 吗？此操作不可撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
    }
    if (state.confirmReloadAll) {
        ConfirmDialog(
            title = "重新加载全部",
            message = "将重新加载所有存储，期间文件访问可能短暂不可用。是否继续？",
            confirmLabel = "重新加载",
            onConfirm = viewModel::confirmReloadAll,
            onDismiss = viewModel::dismissReloadAll,
        )
    }
}
