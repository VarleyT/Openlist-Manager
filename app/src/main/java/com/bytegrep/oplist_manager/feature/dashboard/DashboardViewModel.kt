package com.bytegrep.oplist_manager.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.data.model.Storage
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ServerStatus { CHECKING, ONLINE, OFFLINE }

data class DashboardUiState(
    val server: ServerProfile? = null,
    val user: UserDto? = null,
    val status: ServerStatus = ServerStatus.CHECKING,
    val statusError: String? = null,
    val storages: List<Storage> = emptyList(),
    val storageError: String? = null,
    val runningTasks: Int = 0,
    val failedTasks: Int = 0,
    val isRefreshing: Boolean = false,
    val lastSyncedAt: Long = 0,
) {
    val isAdmin: Boolean get() = user?.isAdmin == true
    val workingCount: Int get() = storages.count { it.isWorking }
    val errorStorages: List<Storage> get() = storages.filter { it.isError }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val session: SessionManager,
    private val storageRepository: StorageRepository,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardUiState(server = session.session.value?.server))
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isRefreshing = true, server = session.session.value?.server) }
        viewModelScope.launch {
            val user = checkServer()
            if (user?.isAdmin == true) loadAdminData()
            if (user != null) loadTaskSummary()
            _state.update { it.copy(isRefreshing = false, lastSyncedAt = System.currentTimeMillis()) }
        }
    }

    /** 通过 /api/me 同时检测在线状态与 Token 有效性 */
    private suspend fun checkServer(): UserDto? = try {
        val user = session.refreshUser()
        _state.update { it.copy(user = user, status = ServerStatus.ONLINE, statusError = null) }
        user
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _state.update { it.copy(status = ServerStatus.OFFLINE, statusError = e.userMessage()) }
        null
    }

    private suspend fun loadAdminData() {
        try {
            val storages = storageRepository.list()
            _state.update { it.copy(storages = storages, storageError = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(storageError = e.userMessage()) }
        }
    }

    private suspend fun loadTaskSummary() = coroutineScope {
        val results = TaskType.entries.map { type ->
            async { runCatching { taskRepository.undone(type) }.getOrDefault(emptyList()) }
        }.awaitAll().flatten()
        _state.update {
            it.copy(runningTasks = results.count { task -> task.isRunning }, failedTasks = results.count { task -> task.isFailed })
        }
    }
}
