package com.bytegrep.oplist_manager.feature.files

import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.FsSearchItem

/** 当前目录下的操作权限 */
data class FilePermissions(
    val canWrite: Boolean = false,
    val canRename: Boolean = false,
    val canMove: Boolean = false,
    val canCopy: Boolean = false,
    val canRemove: Boolean = false,
)

data class SearchUiState(
    val keywords: String,
    val isLoading: Boolean = true,
    val results: List<FsSearchItem> = emptyList(),
    val error: String? = null,
    /** 服务端统计的结果总数 */
    val total: Long = 0,
    /** 已加载的页码，0 表示尚未加载 */
    val page: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
) {
    val hasMore: Boolean get() = page > 0 && results.size < total
}

/**
 * 合并一页搜索结果：按完整路径去重（LazyColumn key 不允许重复）。
 * 服务端返回空页时视为已到末尾，避免 total 不准确导致无限请求。
 */
fun SearchUiState.appendPage(page: Int, items: List<FsSearchItem>, total: Long): SearchUiState {
    val seen = results.mapTo(HashSet()) { searchKey(it) }
    val merged = results + items.filter { seen.add(searchKey(it)) }
    return copy(
        isLoading = false,
        isLoadingMore = false,
        error = null,
        loadMoreError = null,
        results = merged,
        page = page,
        total = if (items.isEmpty()) merged.size.toLong() else maxOf(total, merged.size.toLong()),
    )
}

fun searchKey(item: FsSearchItem): String = joinPath(item.parent, item.name)

enum class TransferMode(val title: String, val confirmLabel: String) {
    MOVE("移动到", "移动到此处"),
    COPY("复制到", "复制到此处"),
}

/** 移动/复制目标目录选择器状态 */
data class DirPickerState(
    val mode: TransferMode,
    val srcDir: String,
    val names: List<String>,
    val path: String,
    val dirs: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val canConfirm: Boolean get() = !isLoading && path != srcDir
}

/** 后台上传进度汇总 */
data class UploadBanner(
    val activeCount: Int,
    val currentName: String?,
    val progress: Int,
)

/** 当前显示的弹窗/底部菜单（同一时刻仅一个） */
sealed interface FilesOverlay {
    data object AddSheet : FilesOverlay
    data object SortSheet : FilesOverlay
    data class ItemMenu(val item: FsObject) : FilesOverlay
    data object NewFolder : FilesOverlay
    data class Rename(val item: FsObject) : FilesOverlay
    data class ConfirmDelete(val names: List<String>) : FilesOverlay
}

data class FilesUiState(
    val currentPath: String = "/",
    val items: List<FsObject> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isOperating: Boolean = false,
    val error: String? = null,
    val permissions: FilePermissions = FilePermissions(),
    val isGrid: Boolean = false,
    val sort: FileSort = FileSort(),
    val selectionMode: Boolean = false,
    val selectedNames: Set<String> = emptySet(),
    val searchQuery: String = "",
    val search: SearchUiState? = null,
    val picker: DirPickerState? = null,
    val overlay: FilesOverlay? = null,
    val upload: UploadBanner? = null,
) {
    val isRoot: Boolean get() = currentPath == "/"
    val allSelected: Boolean get() = items.isNotEmpty() && selectedNames.size == items.size
}
