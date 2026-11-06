package com.bytegrep.oplist_manager.feature.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.bytegrep.oplist_manager.core.common.RateSample
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** 本机 WorkManager 上传任务 */
data class LocalUpload(
    val id: UUID,
    val name: String,
    /** 0..1，未知为 null */
    val progress: Float?,
    val state: WorkInfo.State,
    val error: String?,
    /** 平滑后的上传速率（字节/秒），未知为 null */
    val bytesPerSecond: Double? = null,
    /** 预计剩余秒数，未知为 null */
    val remainingSeconds: Long? = null,
) {
    val stateLabel: String
        get() = when (state) {
            WorkInfo.State.RUNNING -> "上传中"
            WorkInfo.State.FAILED -> "失败"
            WorkInfo.State.SUCCEEDED -> "已完成"
            WorkInfo.State.CANCELLED -> "已取消"
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> "等待"
        }
    val isActive: Boolean get() = !state.isFinished
}

data class TasksUiState(
    val tab: TaskTab = TaskTab.UNDONE,
    val selectedType: TaskType? = null,
    val undone: List<TypedTask> = emptyList(),
    val done: List<TypedTask> = emptyList(),
    val localUploads: List<LocalUpload> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    /** 部分类型加载失败的提示（不影响其他类型） */
    val partialError: String? = null,
    /** 全部类型均加载失败 */
    val error: String? = null,
    val isBusy: Boolean = false,
    /** 多选中的任务 key（[TypedTask.key]），为空表示未处于多选模式 */
    val selectedKeys: Set<String> = emptySet(),
    /** 运行中服务器任务的速率采样，key 为 [TypedTask.key] */
    val rates: Map<String, RateSample> = emptyMap(),
) {
    private fun List<TypedTask>.filtered() = if (selectedType == null) this else filter { it.type == selectedType }
    val visibleUndone: List<TypedTask> get() = undone.filtered()
    val visibleDone: List<TypedTask> get() = done.filtered()
    /** 当前页签可见的服务器任务 */
    val visibleTasks: List<TypedTask> get() = if (tab == TaskTab.UNDONE) visibleUndone else visibleDone
    val selectionMode: Boolean get() = selectedKeys.isNotEmpty()
    val allSelected: Boolean get() = visibleTasks.isNotEmpty() && visibleTasks.all { it.key in selectedKeys }

    /** 列表变化后剔除已不可见的选中项 */
    fun pruneSelection(): TasksUiState {
        if (selectedKeys.isEmpty()) return this
        val keys = visibleTasks.mapTo(HashSet()) { it.key }
        return copy(selectedKeys = selectedKeys.filterTo(HashSet()) { it in keys })
    }
    val summary: TaskSummary get() = summarize((visibleUndone + visibleDone).map { it.task })
}

/** 并发拉取结果 */
private data class FetchResult(val tasks: List<TypedTask>, val errors: List<String>)

@HiltViewModel
class TasksViewModel internal constructor(
    private val repository: TaskRepository,
    context: Context,
    /** 速率采样用的时钟（毫秒），测试中可替换 */
    private val clock: () -> Long,
) : ViewModel() {

    @Inject
    constructor(
        repository: TaskRepository,
        @ApplicationContext context: Context,
    ) : this(repository, context, System::currentTimeMillis)

    private val workManager = WorkManager.getInstance(context)
    private val _state = MutableStateFlow(TasksUiState())

    private val localUploads: Flow<List<LocalUpload>> = workManager
        .getWorkInfosByTagFlow(UPLOAD_TAG)
        .map { infos -> infos.filter { it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED }.map(::toLocalUpload) }
        .catch { emit(emptyList()) }

    val state: StateFlow<TasksUiState> = combine(_state, localUploads) { s, uploads -> s.copy(localUploads = uploads) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TasksUiState())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    private var refreshJob: Job? = null
    private var pollJob: Job? = null

    init {
        refresh()
    }

    fun selectTab(tab: TaskTab) {
        _state.update { it.copy(tab = tab, selectedKeys = emptySet()) }
    }

    fun selectType(type: TaskType?) {
        _state.update { it.copy(selectedType = type, selectedKeys = emptySet()) }
    }

    // ---------- 多选 ----------

    fun enterSelection(task: TypedTask) = _state.update { it.copy(selectedKeys = setOf(task.key)) }

    fun toggleSelection(task: TypedTask) = _state.update {
        val keys = if (task.key in it.selectedKeys) it.selectedKeys - task.key else it.selectedKeys + task.key
        it.copy(selectedKeys = keys)
    }

    fun toggleSelectAll() = _state.update {
        it.copy(selectedKeys = if (it.allSelected) emptySet() else it.visibleTasks.mapTo(HashSet()) { task -> task.key })
    }

    fun exitSelection() = _state.update { it.copy(selectedKeys = emptySet()) }

    /** 对选中任务执行批量操作，完成后退出多选并刷新 */
    fun batch(action: TaskBatchAction) {
        val current = _state.value
        runBatch(action, current.visibleTasks.filter { it.key in current.selectedKeys })
    }

    /** 取消当前列表（按类型筛选）中的全部进行中任务；服务端无对应条件接口，逐条传入 cancel_some */
    fun cancelAll() = runBatch(TaskBatchAction.CANCEL, _state.value.visibleUndone)

    /** 按类型分组调用 *_some 接口 */
    private fun runBatch(action: TaskBatchAction, selected: List<TypedTask>) {
        if (selected.isEmpty()) return
        launchBusy {
            val taskErrors = mutableListOf<String>()
            val requestErrors = mutableListOf<String>()
            var failed = 0
            selected.groupBy { it.type }.forEach { (type, tasks) ->
                try {
                    val errors = repository.batch(type, action.action, tasks.map { it.task.id })
                    failed += errors.size
                    taskErrors += errors.values
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed += tasks.size
                    requestErrors += "${type.label}：${e.userMessage()}"
                }
            }
            _state.update { it.copy(selectedKeys = emptySet()) }
            _events.send(batchMessage(action, selected.size, failed, (taskErrors.distinct() + requestErrors)))
        }
    }

    private fun batchMessage(action: TaskBatchAction, total: Int, failed: Int, reasons: List<String>): String {
        val succeeded = total - failed
        val detail = reasons.joinToString("；")
        return when {
            failed == 0 -> "已${action.label} $total 项"
            succeeded == 0 -> "${action.label}失败 $failed 项：$detail"
            else -> "已${action.label} $succeeded 项，失败 $failed 项：$detail"
        }
    }

    /** 同时刷新进行中与已完成列表 */
    fun refresh(pull: Boolean = false) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isRefreshing = pull, isLoading = it.undone.isEmpty() && it.done.isEmpty() && !pull) }
            val (undone, done) = coroutineScope {
                val u = async { fetchAll(repository::undone) }
                val d = async { fetchAll(repository::done) }
                u.await() to d.await()
            }
            applyResults(undone, done)
        }
    }

    private fun applyResults(undone: FetchResult, done: FetchResult) {
        val errors = (undone.errors + done.errors).distinct()
        val allFailed = undone.errors.size == TaskType.entries.size && done.errors.size == TaskType.entries.size
        _state.update {
            it.copy(
                undone = undone.tasks,
                rates = updateTaskRates(it.rates, undone.tasks, clock()),
                done = done.tasks,
                isLoading = false,
                isRefreshing = false,
                error = if (allFailed) errors.firstOrNull() else null,
                partialError = if (!allFailed && errors.isNotEmpty()) errors.joinToString("；") else null,
            ).pruneSelection()
        }
    }

    /** 页面可见时调用：进行中页签每 3 秒轮询一次 */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (_state.value.tab == TaskTab.UNDONE && refreshJob?.isActive != true) pollUndone()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun pollUndone() {
        val before = _state.value.undone.map { it.key }.toSet()
        val result = fetchAll(repository::undone)
        if (result.errors.size == TaskType.entries.size) return // 轮询失败静默跳过，等待下次
        _state.update { it.copy(undone = result.tasks, rates = updateTaskRates(it.rates, result.tasks, clock())).pruneSelection() }
        // 有任务结束时同步刷新已完成列表，使统计保持准确
        if (!result.tasks.map { it.key }.toSet().containsAll(before)) {
            val done = fetchAll(repository::done)
            if (done.errors.size < TaskType.entries.size) _state.update { it.copy(done = done.tasks).pruneSelection() }
        }
    }

    private suspend fun fetchAll(loader: suspend (TaskType) -> List<TaskInfo>): FetchResult = coroutineScope {
        val results = TaskType.entries.map { type ->
            async {
                try {
                    Result.success(loader(type).map { TypedTask(type, it) })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure<List<TypedTask>>(IllegalStateException("${type.label}：${e.userMessage()}", e))
                }
            }
        }.awaitAll()
        FetchResult(
            tasks = results.flatMap { it.getOrNull().orEmpty() }.sortedByDescending { it.task.startTime.orEmpty() },
            errors = results.mapNotNull { it.exceptionOrNull()?.message },
        )
    }

    fun cancel(task: TypedTask) = runAction(task, "cancel", "已取消任务")
    fun retry(task: TypedTask) = runAction(task, "retry", "已重新执行任务")
    fun delete(task: TypedTask) = runAction(task, "delete", "已删除任务")

    private fun runAction(task: TypedTask, action: String, success: String) {
        launchBusy {
            try {
                repository.action(task.type, action, task.task.id)
                _events.send(success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send("操作失败：${e.userMessage()}")
            }
        }
    }

    /** 对选中类型（未选中则全部类型）逐个执行批量操作 */
    fun bulk(action: TaskBulkAction) {
        val types = _state.value.selectedType?.let { listOf(it) } ?: TaskType.entries
        launchBusy {
            val failures = types.mapNotNull { type ->
                try {
                    repository.bulk(type, action.action)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    "${type.label}：${e.userMessage()}"
                }
            }
            val message = if (failures.isEmpty()) action.doneMessage else "${action.label}部分失败：${failures.joinToString("；")}"
            _events.send(message)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
            refresh()
        }
    }

    fun cancelLocalUpload(id: UUID) {
        workManager.cancelWorkById(id)
        _events.trySend("已取消本机上传")
    }

    private fun toLocalUpload(info: WorkInfo): LocalUpload {
        val progressMap = info.progress.keyValueMap
        val name = progressMap[KEY_NAME] as? String
            ?: info.outputData.getString(KEY_NAME)
            ?: "上传任务"
        return LocalUpload(
            id = info.id,
            name = name,
            progress = parseUploadProgress(progressMap[KEY_PROGRESS]),
            state = info.state,
            error = info.outputData.getString(KEY_ERROR),
            bytesPerSecond = (progressMap[KEY_SPEED] as? Long)?.takeIf { it >= 0 }?.toDouble(),
            remainingSeconds = (progressMap[KEY_REMAINING] as? Long)?.takeIf { it >= 0 },
        )
    }

    private companion object {
        const val UPLOAD_TAG = "upload"
        const val KEY_NAME = "name"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_SPEED = "speed"
        const val KEY_REMAINING = "remaining"
        const val POLL_INTERVAL_MS = 3_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
