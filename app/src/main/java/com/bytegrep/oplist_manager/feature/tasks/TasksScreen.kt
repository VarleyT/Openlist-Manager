package com.bytegrep.oplist_manager.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.bytegrep.oplist_manager.data.model.TaskType

private val CARD_GAP = 12.dp

/** 卡片操作的回调集合，避免参数过长 */
internal data class TaskCallbacks(
    val onCancel: (TypedTask) -> Unit,
    val onRetry: (TypedTask) -> Unit,
    val onDelete: (TypedTask) -> Unit,
    val onCancelLocal: (LocalUpload) -> Unit,
    val onOpen: (TypedTask) -> Unit,
)

@Composable
fun TasksScreen(
    onBack: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingBulk by rememberSaveable { mutableStateOf<TaskBulkAction?>(null) }
    var pendingDelete by remember { mutableStateOf<TypedTask?>(null) }
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    val callbacks = TaskCallbacks(
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onDelete = { pendingDelete = it },
        onCancelLocal = { viewModel.cancelLocalUpload(it.id) },
        onOpen = { detailKey = it.key },
    )
    LifecycleResumeEffect(viewModel) {
        viewModel.startPolling()
        onPauseOrDispose { viewModel.stopPolling() }
    }

    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TasksTopBar(onBack = onBack, onRefresh = { viewModel.refresh() }, onBulk = { pendingBulk = it }, enabled = !state.isBusy)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            TasksContent(
                state = state,
                onRefresh = { viewModel.refresh(pull = true) },
                onRetryLoad = { viewModel.refresh() },
                onSelectTab = viewModel::selectTab,
                onSelectType = viewModel::selectType,
                callbacks = callbacks,
            )
        }
    }

    // 按 key 从最新列表中查找，轮询刷新时详情同步更新；任务被删除或清除后自动关闭
    detailKey?.let { key ->
        val inDone = state.done.firstOrNull { it.key == key }
        val task = inDone ?: state.undone.firstOrNull { it.key == key }
        if (task == null) {
            if (!state.isLoading && !state.isRefreshing) LaunchedEffect(key) { detailKey = null }
        } else {
            TaskDetailSheet(
                task = task,
                isDoneTab = inDone != null,
                busy = state.isBusy,
                callbacks = callbacks,
                onDismiss = { detailKey = null },
            )
        }
    }
    pendingBulk?.let { action ->
        ConfirmDialog(
            title = action.label,
            message = action.confirmMessage + scopeHint(state),
            confirmLabel = "执行",
            destructive = action != TaskBulkAction.RETRY_FAILED,
            onConfirm = { viewModel.bulk(action) },
            onDismiss = { pendingBulk = null },
        )
    }
    pendingDelete?.let { task ->
        ConfirmDialog(
            title = "删除任务",
            message = "确定删除任务记录 “${maskTaskName(task.task.name)}” 吗？",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { viewModel.delete(task) },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun scopeHint(state: TasksUiState): String =
    "\n作用范围：" + (state.selectedType?.label ?: "全部任务类型")

@Composable
private fun TasksTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBulk: (TaskBulkAction) -> Unit,
    enabled: Boolean,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LargeTitleBar(
        title = "任务中心",
        onBack = onBack,
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = OpenListTheme.colors.ink)
            }
            Box {
                IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "批量操作", tint = OpenListTheme.colors.ink)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    TaskBulkAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label) },
                            onClick = {
                                menuOpen = false
                                onBulk(action)
                            },
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksContent(
    state: TasksUiState,
    onRefresh: () -> Unit,
    onRetryLoad: () -> Unit,
    onSelectTab: (TaskTab) -> Unit,
    onSelectType: (TaskType?) -> Unit,
    callbacks: TaskCallbacks,
) {
    when {
        state.isLoading -> LoadingState()
        state.error != null && state.undone.isEmpty() && state.done.isEmpty() ->
            ErrorState(message = state.error, onRetry = onRetryLoad)
        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
            ) {
                item(key = "summary") { SummaryCard(summary = state.summary) }
                item(key = "tabs") {
                    TaskTabSwitcher(
                        selected = state.tab,
                        undoneCount = state.visibleUndone.size + state.localUploads.count { it.isActive },
                        doneCount = state.visibleDone.size,
                        onSelect = onSelectTab,
                    )
                }
                item(key = "types") { TypeFilterRow(selected = state.selectedType, onSelect = onSelectType) }
                state.partialError?.let { item(key = "partial") { PartialErrorBanner(it) } }
                if (state.tab == TaskTab.UNDONE) undoneItems(state, callbacks) else doneItems(state, callbacks)
            }
        }
    }
}

private fun LazyListScope.undoneItems(state: TasksUiState, callbacks: TaskCallbacks) {
    if (state.localUploads.isNotEmpty()) {
        item(key = "local_header") { SectionHeader("本机上传") }
        items(state.localUploads, key = { "local:${it.id}" }) { upload ->
            LocalUploadCard(upload = upload, onCancel = { callbacks.onCancelLocal(upload) })
        }
        item(key = "server_header") { SectionHeader("服务器任务") }
    }
    if (state.visibleUndone.isEmpty()) {
        item(key = "empty_undone") { EmptyState(title = "暂无进行中的任务") }
    } else {
        items(state.visibleUndone, key = { it.key }) { task ->
            TaskCard(task = task, isDoneTab = false, busy = state.isBusy, callbacks = callbacks, onClick = { callbacks.onOpen(task) })
        }
    }
}

private fun LazyListScope.doneItems(state: TasksUiState, callbacks: TaskCallbacks) {
    if (state.visibleDone.isEmpty()) {
        item(key = "empty_done") { EmptyState(title = "暂无已完成的任务") }
    } else {
        items(state.visibleDone, key = { it.key }) { task ->
            TaskCard(task = task, isDoneTab = true, busy = state.isBusy, callbacks = callbacks, onClick = { callbacks.onOpen(task) })
        }
    }
}
