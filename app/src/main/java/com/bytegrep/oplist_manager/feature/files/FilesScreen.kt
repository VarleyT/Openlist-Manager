package com.bytegrep.oplist_manager.feature.files

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.component.TextInputDialog
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.FsSearchItem

private val GridMinCellSize = 150.dp
private val ListBottomPadding = 96.dp

@Composable
fun FilesScreen(onOpenPreview: (String) -> Unit, onOpenTasks: () -> Unit) {
    val vm: FilesViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val startUpload = rememberUploadLauncher(onPicked = vm::upload)
    BackHandler(enabled = state.selectionMode || state.search != null || !state.isRoot) {
        when {
            state.selectionMode -> vm.exitSelection()
            state.search != null -> vm.exitSearch()
            else -> vm.goUp()
        }
    }
    val openItem: (FsObject) -> Unit = { item ->
        if (item.isDir) vm.openDir(item.name) else onOpenPreview(joinPath(state.currentPath, item.name))
    }

    Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg)) {
        FilesContent(state = state, vm = vm, openItem = openItem, onOpenPreview = onOpenPreview)
        if (!state.selectionMode && state.search == null && state.permissions.canWrite) {
            AddFab(onClick = { vm.showOverlay(FilesOverlay.AddSheet) }, modifier = Modifier.align(Alignment.BottomEnd))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.lg))
    }
    FilesOverlays(state = state, vm = vm, openItem = openItem, onUpload = startUpload, onOpenTasks = onOpenTasks)
}

/** 上传：Android 13+ 先请求通知权限（拒绝也继续），再打开系统文件选择器 */
@Composable
private fun rememberUploadLauncher(onPicked: (List<android.net.Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        picker.launch(arrayOf("*/*"))
    }
    return {
        val needAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needAsk) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else picker.launch(arrayOf("*/*"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesContent(
    state: FilesUiState,
    vm: FilesViewModel,
    openItem: (FsObject) -> Unit,
    onOpenPreview: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        FilesHeader(state, vm)
        if (state.isOperating) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = Spacing.page))
        state.upload?.let { UploadBannerView(it) }
        Box(Modifier.weight(1f)) {
            val search = state.search
            if (search != null) {
                SearchResults(search, onOpen = { hit ->
                    if (hit.isDir) vm.openPath(joinPath(hit.parent, hit.name)) else onOpenPreview(joinPath(hit.parent, hit.name))
                })
            } else {
                PullToRefreshBox(isRefreshing = state.isRefreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                    FileListBody(state, vm, openItem)
                }
            }
        }
        if (state.selectionMode) {
            val names = state.selectedNames.toList()
            SelectionActionBar(
                count = names.size,
                permissions = state.permissions,
                onMove = { vm.openPicker(TransferMode.MOVE, names) },
                onCopy = { vm.openPicker(TransferMode.COPY, names) },
                onDelete = { vm.showOverlay(FilesOverlay.ConfirmDelete(names)) },
            )
        }
    }
}

@Composable
private fun FilesHeader(state: FilesUiState, vm: FilesViewModel) {
    if (state.selectionMode) {
        SelectionTopBar(state.selectedNames.size, state.allSelected, vm::toggleSelectAll, vm::exitSelection)
        return
    }
    LargeTitleBar(title = "文件管理") {
        IconButton(onClick = { vm.showOverlay(FilesOverlay.SortSheet) }) {
            Icon(Icons.Outlined.SwapVert, contentDescription = "排序", tint = OpenListTheme.colors.ink)
        }
        IconButton(onClick = vm::toggleGrid) {
            Icon(
                if (state.isGrid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView,
                contentDescription = if (state.isGrid) "列表视图" else "网格视图",
                tint = OpenListTheme.colors.ink,
            )
        }
    }
    FileSearchField(query = state.searchQuery, onQueryChange = vm::onSearchQueryChange, onSearch = vm::search, onClear = vm::exitSearch)
    if (state.search == null) {
        Breadcrumb(path = state.currentPath, onNavigate = vm::openPath)
    } else {
        Text(
            "在 ${state.currentPath} 中搜索",
            style = MaterialTheme.typography.bodySmall,
            color = OpenListTheme.colors.ink3,
            modifier = Modifier.padding(horizontal = Spacing.page, vertical = Spacing.md),
        )
    }
}

@Composable
private fun FileListBody(state: FilesUiState, vm: FilesViewModel, openItem: (FsObject) -> Unit) {
    val onClick: (FsObject) -> Unit = { item -> if (state.selectionMode) vm.toggleSelection(item.name) else openItem(item) }
    val onLongClick: (FsObject) -> Unit = { item -> if (state.selectionMode) vm.toggleSelection(item.name) else vm.enterSelection(item.name) }
    val onMore: (FsObject) -> Unit = { item -> vm.showOverlay(FilesOverlay.ItemMenu(item)) }
    when {
        state.isLoading -> StatusList { LoadingState() }
        state.error != null -> StatusList { ErrorState(message = state.error, onRetry = vm::retry) }
        state.items.isEmpty() -> StatusList { EmptyState(title = "空文件夹", message = "这里还没有任何文件") }
        state.isGrid -> FileGrid(state, onClick, onLongClick, onMore)
        else -> FileList(state, onClick, onLongClick, onMore)
    }
}

/** 状态页也放在可滚动容器中，保证下拉刷新可用 */
@Composable
private fun StatusList(content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { content() } }
    }
}

@Composable
private fun FileList(
    state: FilesUiState,
    onClick: (FsObject) -> Unit,
    onLongClick: (FsObject) -> Unit,
    onMore: (FsObject) -> Unit,
) {
    val count = state.items.size
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.xs, bottom = ListBottomPadding),
    ) {
        itemsIndexed(state.items, key = { _, item -> item.name }) { index, item ->
            Column(Modifier.clip(groupShape(index, count)).background(OpenListTheme.colors.surface)) {
                FileRow(
                    title = item.name,
                    subtitle = item.subtitle(),
                    kind = item.kind(),
                    thumb = item.thumb,
                    selectionMode = state.selectionMode,
                    selected = item.name in state.selectedNames,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) },
                    onMore = { onMore(item) },
                )
                if (index < count - 1) {
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = OpenListTheme.colors.hairline)
                }
            }
        }
    }
}

@Composable
private fun FileGrid(
    state: FilesUiState,
    onClick: (FsObject) -> Unit,
    onLongClick: (FsObject) -> Unit,
    onMore: (FsObject) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(GridMinCellSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.xs, bottom = ListBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(state.items, key = { it.name }) { item ->
            FileGridCard(
                item = item,
                selectionMode = state.selectionMode,
                selected = item.name in state.selectedNames,
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) },
                onMore = { onMore(item) },
            )
        }
    }
}

@Composable
private fun SearchResults(search: SearchUiState, onOpen: (FsSearchItem) -> Unit) {
    when {
        search.isLoading -> LoadingState(text = "搜索中…")
        search.error != null -> EmptyState(title = "搜索失败", message = search.error, icon = Icons.Outlined.SearchOff)
        search.results.isEmpty() -> EmptyState(title = "没有找到结果", message = "换个关键字试试", icon = Icons.Outlined.SearchOff)
        else -> SearchResultList(search.results, onOpen)
    }
}

@Composable
private fun SearchResultList(results: List<FsSearchItem>, onOpen: (FsSearchItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = ListBottomPadding),
    ) {
        itemsIndexed(results, key = { _, hit -> joinPath(hit.parent, hit.name) }) { index, hit ->
            Column(Modifier.clip(groupShape(index, results.size)).background(OpenListTheme.colors.surface)) {
                FileRow(
                    title = hit.name,
                    subtitle = hit.parent,
                    kind = fileKindOf(hit.name, hit.isDir, hit.type),
                    thumb = "",
                    selectionMode = false,
                    selected = false,
                    onClick = { onOpen(hit) },
                    onLongClick = null,
                    onMore = null,
                )
                if (index < results.size - 1) {
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = OpenListTheme.colors.hairline)
                }
            }
        }
    }
}

@Composable
private fun AddFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.padding(Spacing.xl),
        shape = CircleShape,
        containerColor = OpenListTheme.colors.accent,
        contentColor = androidx.compose.ui.graphics.Color.White,
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "新建")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesOverlays(
    state: FilesUiState,
    vm: FilesViewModel,
    openItem: (FsObject) -> Unit,
    onUpload: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    state.picker?.let { picker ->
        DirPickerSheet(picker, vm::pickerOpen, vm::pickerUp, vm::confirmPicker, vm::dismissPicker)
    }
    when (val overlay = state.overlay) {
        null -> Unit
        FilesOverlay.AddSheet -> AddSheet(
            canWrite = state.permissions.canWrite,
            onUpload = { vm.dismissOverlay(); onUpload() },
            onNewFolder = { vm.showOverlay(FilesOverlay.NewFolder) },
            onOpenTasks = { vm.dismissOverlay(); onOpenTasks() },
            onDismiss = vm::dismissOverlay,
        )
        FilesOverlay.SortSheet -> SortSheet(state.sort, vm::setSort, vm::dismissOverlay)
        is FilesOverlay.ItemMenu -> ItemMenu(overlay.item, state.permissions, vm, openItem)
        FilesOverlay.NewFolder -> TextInputDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            confirmLabel = "创建",
            onConfirm = vm::createFolder,
            onDismiss = vm::dismissOverlay,
        )
        is FilesOverlay.Rename -> TextInputDialog(
            title = "重命名",
            label = "新名称",
            initial = overlay.item.name,
            onConfirm = { name -> vm.rename(overlay.item, name) },
            onDismiss = vm::dismissOverlay,
        )
        is FilesOverlay.ConfirmDelete -> ConfirmDialog(
            title = "删除确认",
            message = deleteMessage(overlay.names),
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { vm.remove(overlay.names) },
            onDismiss = vm::dismissOverlay,
        )
    }
}

private fun deleteMessage(names: List<String>): String =
    if (names.size == 1) "确定要删除“${names.first()}”吗？此操作不可撤销。" else "确定要删除选中的 ${names.size} 项吗？此操作不可撤销。"

@Composable
private fun ItemMenu(item: FsObject, permissions: FilePermissions, vm: FilesViewModel, openItem: (FsObject) -> Unit) {
    ItemMenuSheet(
        item = item,
        permissions = permissions,
        onOpen = { vm.dismissOverlay(); openItem(item) },
        onDownload = { vm.dismissOverlay(); vm.download(item) },
        onRename = { vm.showOverlay(FilesOverlay.Rename(item)) },
        onMove = { vm.openPicker(TransferMode.MOVE, listOf(item.name)) },
        onCopy = { vm.openPicker(TransferMode.COPY, listOf(item.name)) },
        onDelete = { vm.showOverlay(FilesOverlay.ConfirmDelete(listOf(item.name))) },
        onDismiss = vm::dismissOverlay,
    )
}
