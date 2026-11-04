package com.bytegrep.oplist_manager.feature.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
) {
    private fun List<TypedTask>.filtered() = if (selectedType == null) this else filter { it.type == selectedType }
    val visibleUndone: List<TypedTask> get() = undone.filtered()
    val visibleDone: List<TypedTask> get() = done.filtered()
    val summary: TaskSummary get() = summarize((visibleUndone + visibleDone).map { it.task })
}

/** 并发拉取结果 */
private data class FetchResult(val tasks: List<TypedTask>, val errors: List<String>)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

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
        _state.update { it.copy(tab = tab) }
    }

    fun selectType(type: TaskType?) {
        _state.update { it.copy(selectedType = type) }
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
                done = done.tasks,
                isLoading = false,
                isRefreshing = false,
                error = if (allFailed) errors.firstOrNull() else null,
                partialError = if (!allFailed && errors.isNotEmpty()) errors.joinToString("；") else null,
            )
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
        _state.update { it.copy(undone = result.tasks) }
        // 有任务结束时同步刷新已完成列表，使统计保持准确
        if (!result.tasks.map { it.key }.toSet().containsAll(before)) {
            val done = fetchAll(repository::done)
            if (done.errors.size < TaskType.entries.size) _state.update { it.copy(done = done.tasks) }
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
            val message = if (failures.isEmpty()) "${action.label}完成" else "${action.label}部分失败：${failures.joinToString("；")}"
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
        )
    }

    private companion object {
        const val UPLOAD_TAG = "upload"
        const val KEY_NAME = "name"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val POLL_INTERVAL_MS = 3_000L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
