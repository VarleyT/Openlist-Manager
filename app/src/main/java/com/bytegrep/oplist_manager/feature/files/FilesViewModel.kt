package com.bytegrep.oplist_manager.feature.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.core.common.parentPath
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.repository.FileRepository
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** 有进行中任务时，后台任务角标的刷新间隔 */
internal const val TASK_POLL_INTERVAL_MS = 5_000L

@HiltViewModel
class FilesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val session: SessionManager,
    private val settingsStore: AppSettingsStore,
    private val uploadScheduler: UploadScheduler,
    private val downloadHelper: DownloadHelper,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** 一次性 Snackbar 消息 */
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var pickerJob: Job? = null
    private var taskCountJob: Job? = null
    private var taskPolling = false
    private var listWrite: Boolean = false
    private var handledUploads: Set<UUID>? = null

    init {
        observeSettings()
        observeUser()
        observeUploads()
        load("/")
    }

    // ---------------- 浏览 ----------------

    fun openDir(name: String) = load(joinPath(_state.value.currentPath, name))

    fun openPath(path: String) {
        exitSearch()
        load(path)
    }

    fun goUp() {
        if (!_state.value.isRoot) load(parentPath(_state.value.currentPath))
    }

    fun refresh() {
        load(_state.value.currentPath, refresh = true, pull = true)
        refreshTaskCount()
    }

    fun retry() = load(_state.value.currentPath)

    private fun load(path: String, refresh: Boolean = false, pull: Boolean = false) {
        loadJob?.cancel()
        val pathChanged = path != _state.value.currentPath
        _state.update {
            it.copy(
                currentPath = path,
                items = if (pathChanged) emptyList() else it.items,
                isLoading = !pull && (pathChanged || it.items.isEmpty()),
                isRefreshing = pull,
                error = null,
                selectionMode = if (pathChanged) false else it.selectionMode,
                selectedNames = if (pathChanged) emptySet() else it.selectedNames,
            )
        }
        loadJob = viewModelScope.launch {
            try {
                val data = repository.list(path, refresh)
                listWrite = data.write
                _state.update {
                    it.copy(
                        items = sortFiles(data.content.orEmpty(), it.sort),
                        isLoading = false,
                        isRefreshing = false,
                        permissions = computePermissions(session.currentUser.value),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLoadFailed(e)
            }
        }
    }

    private suspend fun onLoadFailed(e: Exception) {
        val keepItems = _state.value.items.isNotEmpty()
        _state.update {
            it.copy(isLoading = false, isRefreshing = false, error = if (keepItems) null else e.userMessage())
        }
        if (keepItems) _messages.send("刷新失败：${e.userMessage()}")
    }

    // ---------------- 视图 ----------------

    fun setSort(sort: FileSort) {
        _state.update { it.copy(sort = sort, items = sortFiles(it.items, sort), overlay = null) }
    }

    fun setGrid(grid: Boolean) {
        if (grid == _state.value.isGrid) return
        _state.update { it.copy(isGrid = grid) }
        viewModelScope.launch { settingsStore.setFileGrid(grid) }
    }

    fun showOverlay(overlay: FilesOverlay) = _state.update { it.copy(overlay = overlay) }

    fun dismissOverlay() = _state.update { it.copy(overlay = null) }

    // ---------------- 多选 ----------------

    fun enterSelection(name: String) =
        _state.update { it.copy(selectionMode = true, selectedNames = setOf(name)) }

    fun toggleSelection(name: String) = _state.update {
        val next = if (name in it.selectedNames) it.selectedNames - name else it.selectedNames + name
        it.copy(selectedNames = next, selectionMode = next.isNotEmpty())
    }

    fun toggleSelectAll() = _state.update {
        it.copy(selectedNames = if (it.allSelected) emptySet() else it.items.map { item -> item.name }.toSet())
    }

    fun exitSelection() = _state.update { it.copy(selectionMode = false, selectedNames = emptySet()) }

    // ---------------- 搜索 ----------------

    fun onSearchQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    fun search() {
        val keywords = _state.value.searchQuery.trim()
        if (keywords.isEmpty()) return exitSearch()
        searchJob?.cancel()
        _state.update { it.copy(search = SearchUiState(keywords), selectionMode = false, selectedNames = emptySet()) }
        searchJob = viewModelScope.launch {
            try {
                val page = repository.search(_state.value.currentPath, keywords, page = 1)
                _state.update { it.copy(search = it.search?.appendPage(1, page.content.orEmpty(), page.total)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = searchErrorMessage(e.userMessage())
                _state.update { it.copy(search = it.search?.copy(isLoading = false, error = message)) }
            }
        }
    }

    /** 滚动到底部时加载下一页搜索结果；同一时间只有一个请求 */
    fun loadMoreSearch() {
        val current = _state.value.search ?: return
        if (current.isLoading || current.isLoadingMore || !current.hasMore) return
        val nextPage = current.page + 1
        _state.update { it.copy(search = it.search?.copy(isLoadingMore = true, loadMoreError = null)) }
        searchJob = viewModelScope.launch {
            try {
                val page = repository.search(_state.value.currentPath, current.keywords, page = nextPage)
                _state.update { state ->
                    // 期间关键字已变化则丢弃结果
                    val search = state.search?.takeIf { it.keywords == current.keywords } ?: return@update state
                    state.copy(search = search.appendPage(nextPage, page.content.orEmpty(), page.total))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = searchErrorMessage(e.userMessage())
                _state.update { it.copy(search = it.search?.copy(isLoadingMore = false, loadMoreError = message)) }
            }
        }
    }

    fun exitSearch() {
        searchJob?.cancel()
        _state.update { it.copy(search = null, searchQuery = "") }
    }

    // ---------------- 文件操作 ----------------

    fun createFolder(name: String) {
        val error = validateFileName(name)
        if (error != null) return sendMessage(error)
        val parent = _state.value.currentPath
        runOperation("已创建文件夹 ${name.trim()}") { repository.mkdir(parent, name.trim()) }
    }

    fun rename(item: FsObject, newName: String) {
        val error = validateFileName(newName)
        if (error != null) return sendMessage(error)
        if (newName.trim() == item.name) return
        val path = joinPath(_state.value.currentPath, item.name)
        runOperation("已重命名为 ${newName.trim()}") { repository.rename(path, newName.trim()) }
    }

    fun remove(names: List<String>) {
        if (names.isEmpty()) return
        val dir = _state.value.currentPath
        runOperation("已删除 ${names.size} 项") { repository.remove(dir, names) }
    }

    fun download(item: FsObject) {
        if (item.isDir) return sendMessage("暂不支持下载文件夹")
        val path = joinPath(_state.value.currentPath, item.name)
        viewModelScope.launch {
            try {
                val data = repository.get(path)
                downloadHelper.enqueue(data.rawUrl, data.name.ifBlank { item.name })
                _messages.send("已开始下载 ${item.name}，可在通知栏查看进度")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("下载失败：${e.userMessage()}")
            }
        }
    }

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val targetDir = _state.value.currentPath
        // 入队时锁定目标服务器，之后切换服务器也不会改变上传目的地
        val serverId = session.session.value?.server?.id ?: return sendMessage("尚未登录服务器")
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { uploadScheduler.enqueue(uris, targetDir, serverId) }
            _messages.send(if (count > 0) "已加入上传队列：$count 个文件" else "无法读取所选文件")
        }
    }

    private fun runOperation(success: String, block: suspend () -> Unit) {
        _state.update { it.copy(isOperating = true, overlay = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(selectionMode = false, selectedNames = emptySet()) }
                _messages.send(success)
                load(_state.value.currentPath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send(e.userMessage())
            } finally {
                _state.update { it.copy(isOperating = false) }
            }
        }
    }

    private fun sendMessage(message: String) {
        viewModelScope.launch { _messages.send(message) }
    }

    // ---------------- 移动 / 复制 目标选择 ----------------

    fun openPicker(mode: TransferMode, names: List<String>) {
        if (names.isEmpty()) return
        val src = _state.value.currentPath
        _state.update { it.copy(overlay = null, picker = DirPickerState(mode, src, names, path = src)) }
        loadPickerDirs(src)
    }

    fun pickerOpen(name: String) {
        _state.value.picker?.let { loadPickerDirs(joinPath(it.path, name)) }
    }

    fun pickerUp() {
        _state.value.picker?.takeIf { it.path != "/" }?.let { loadPickerDirs(parentPath(it.path)) }
    }

    fun dismissPicker() {
        pickerJob?.cancel()
        _state.update { it.copy(picker = null) }
    }

    fun confirmPicker() {
        val picker = _state.value.picker?.takeIf { it.canConfirm } ?: return
        dismissPicker()
        val label = if (picker.mode == TransferMode.MOVE) "移动" else "复制"
        val message = if (picker.mode == TransferMode.MOVE) "已移动 ${picker.names.size} 项" else "已提交复制 ${picker.names.size} 项"
        runOperation("$message 到 ${picker.path}") {
            try {
                when (picker.mode) {
                    TransferMode.MOVE -> repository.move(picker.srcDir, picker.path, picker.names)
                    TransferMode.COPY -> repository.copy(picker.srcDir, picker.path, picker.names)
                }
                refreshTaskCount()
            } catch (e: AppException) {
                throw AppException.Unknown("${label}失败：${e.userMessage()}", e)
            }
        }
    }

    private fun loadPickerDirs(path: String) {
        pickerJob?.cancel()
        _state.update { it.copy(picker = it.picker?.copy(path = path, dirs = emptyList(), isLoading = true, error = null)) }
        pickerJob = viewModelScope.launch {
            try {
                val dirs = repository.dirs(path).sortedWith(String.CASE_INSENSITIVE_ORDER)
                _state.update { it.copy(picker = it.picker?.copy(dirs = dirs, isLoading = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(picker = it.picker?.copy(isLoading = false, error = e.userMessage())) }
            }
        }
    }

    // ---------------- 观察：设置 / 用户 / 上传 ----------------

    private fun observeSettings() {
        viewModelScope.launch {
            settingsStore.settings.map { it.fileGrid }.distinctUntilChanged().collect { grid ->
                _state.update { it.copy(isGrid = grid) }
            }
        }
    }

    private fun observeUser() {
        viewModelScope.launch {
            session.currentUser.collect { user ->
                _state.update { it.copy(permissions = computePermissions(user)) }
            }
        }
        if (session.currentUser.value == null) {
            viewModelScope.launch {
                try {
                    session.refreshUser()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // 用户信息获取失败不影响浏览，权限由服务端最终校验
                }
            }
        }
    }

    /** 用户信息未知时按宽松处理，最终由服务端校验 */
    private fun computePermissions(user: UserDto?): FilePermissions {
        fun has(p: UserPermission) = user?.hasPermission(p.bit) ?: true
        return FilePermissions(
            canWrite = listWrite || (user?.hasPermission(UserPermission.WRITE.bit) ?: false),
            canRename = has(UserPermission.RENAME),
            canMove = has(UserPermission.MOVE),
            canCopy = has(UserPermission.COPY),
            canRemove = has(UserPermission.REMOVE),
        )
    }

    private fun observeUploads() {
        viewModelScope.launch {
            uploadScheduler.observe().collect { infos -> onUploadInfos(infos) }
        }
    }

    private suspend fun onUploadInfos(infos: List<WorkInfo>) {
        _state.update { it.copy(upload = uploadBannerOf(infos)) }
        val finished = infos.filter { it.state.isFinished }
        val handled = handledUploads
        if (handled == null) {
            // 首次订阅：历史已完成任务不再提示
            handledUploads = finished.map { it.id }.toSet()
            return
        }
        val fresh = finished.filter { it.id !in handled }
        if (fresh.isEmpty()) return
        handledUploads = handled + fresh.map { it.id }
        var needRefresh = false
        fresh.forEach { info ->
            val name = info.outputData.getString(UploadWorker.KEY_NAME).orEmpty()
            val dir = info.outputData.getString(UploadWorker.KEY_TARGET_DIR)
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    _messages.send("已上传 $name")
                    if (dir == _state.value.currentPath) needRefresh = true
                }
                WorkInfo.State.FAILED -> {
                    val error = info.outputData.getString(UploadWorker.KEY_ERROR) ?: "未知错误"
                    _messages.send("上传失败 $name：$error")
                }
                else -> Unit
            }
        }
        if (needRefresh && _state.value.search == null) load(_state.value.currentPath)
        refreshTaskCount()
    }

    // ---------------- 后台任务角标 ----------------

    /** 页面在前台时开启轮询：有进行中任务就每隔 [TASK_POLL_INTERVAL_MS] 刷新一次角标 */
    fun startTaskPolling() {
        taskPolling = true
        refreshTaskCount()
    }

    /** 页面退到后台时停止轮询 */
    fun stopTaskPolling() {
        taskPolling = false
        taskCountJob?.cancel()
    }

    /**
     * 统计服务端进行中/排队中的任务数；全部类型都查询失败时保留原值。
     * 轮询开启且仍有进行中任务时继续定时刷新，数量归零后停止，等下次操作或回到页面再触发。
     */
    fun refreshTaskCount() {
        taskCountJob?.cancel()
        taskCountJob = viewModelScope.launch {
            while (true) {
                fetchRunningTaskCount()?.let { running -> _state.update { it.copy(runningTasks = running) } }
                if (!taskPolling || _state.value.runningTasks == 0) break
                delay(TASK_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun fetchRunningTaskCount(): Int? = coroutineScope {
        val perType = TaskType.entries.map { type -> async { undoneOrNull(type) } }.awaitAll()
        if (perType.all { it == null }) null else perType.sumOf { tasks -> tasks.orEmpty().count { it.isRunning } }
    }

    private suspend fun undoneOrNull(type: TaskType) = try {
        taskRepository.undone(type)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun uploadBannerOf(infos: List<WorkInfo>): UploadBanner? {
        val active = infos.filter { !it.state.isFinished }
        if (active.isEmpty()) return null
        val running = active.firstOrNull { it.state == WorkInfo.State.RUNNING }
        return UploadBanner(
            activeCount = active.size,
            currentName = running?.progress?.getString(UploadWorker.KEY_PROGRESS_NAME),
            progress = running?.progress?.getInt(UploadWorker.KEY_PROGRESS, 0) ?: 0,
        )
    }
}
