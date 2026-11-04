package com.bytegrep.oplist_manager.feature.files

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
)

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
