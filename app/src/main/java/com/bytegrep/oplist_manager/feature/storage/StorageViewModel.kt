package com.bytegrep.oplist_manager.feature.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.Storage
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageUiState(
    val isAdmin: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val storages: List<Storage> = emptyList(),
    /** 正在执行操作的存储 id，避免重复点击 */
    val busyIds: Set<Int> = emptySet(),
    val sheetTarget: Storage? = null,
    val pendingDelete: Storage? = null,
    val confirmReloadAll: Boolean = false,
) {
    val total: Int get() = storages.size
    val workingCount: Int get() = storages.count { it.isWorking }
    val errorCount: Int get() = storages.count { it.isError }
    val disabledCount: Int get() = storages.count { it.disabled }
}

@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repository: StorageRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var initialLoadStarted = false

    init {
        viewModelScope.launch {
            session.currentUser.collect { user ->
                val isAdmin = user?.isAdmin ?: true
                _state.update { it.copy(isAdmin = isAdmin) }
                // 用户信息未就绪时也尝试加载一次，非管理员会由服务端返回权限错误
                if (isAdmin && !initialLoadStarted) {
                    initialLoadStarted = true
                    launch { load(showLoading = true) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { load(showLoading = false) }
    }

    private var resumedOnce = false

    /** 从编辑页返回时静默刷新；首次进入由 init 负责加载 */
    fun onResume() {
        if (resumedOnce && _state.value.isAdmin) refresh()
        resumedOnce = true
    }

    fun retry() {
        viewModelScope.launch { load(showLoading = true) }
    }

    private suspend fun load(showLoading: Boolean) {
        _state.update { it.copy(isLoading = showLoading && it.storages.isEmpty(), isRefreshing = !showLoading, error = null) }
        try {
            val list = repository.list().sortedWith(compareBy({ it.order }, { it.id }))
            _state.update { it.copy(storages = list, isLoading = false, isRefreshing = false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(isLoading = false, isRefreshing = false, error = e.userMessage()) }
        }
    }

    // ---------- 弹层状态 ----------
    fun openSheet(storage: Storage) = _state.update { it.copy(sheetTarget = storage) }
    fun dismissSheet() = _state.update { it.copy(sheetTarget = null) }
    fun requestDelete(storage: Storage) = _state.update { it.copy(sheetTarget = null, pendingDelete = storage) }
    fun dismissDelete() = _state.update { it.copy(pendingDelete = null) }
    fun requestReloadAll() = _state.update { it.copy(confirmReloadAll = true) }
    fun dismissReloadAll() = _state.update { it.copy(confirmReloadAll = false) }

    // ---------- 操作 ----------
    fun toggleEnabled(storage: Storage) {
        dismissSheet()
        val enable = storage.disabled
        runAction(storage.id, if (enable) "已启用 ${storage.mountPath}" else "已禁用 ${storage.mountPath}") {
            repository.setEnabled(storage.id, enable)
        }
    }

    fun confirmDelete() {
        val target = _state.value.pendingDelete ?: return
        dismissDelete()
        runAction(target.id, "已删除 ${target.mountPath}") { repository.delete(target.id) }
    }

    /** 单个存储重新加载：先禁用再启用，服务端会重新初始化该驱动 */
    fun reloadStorage(storage: Storage) {
        runAction(storage.id, "已重新加载 ${storage.mountPath}") {
            repository.setEnabled(storage.id, false)
            repository.setEnabled(storage.id, true)
        }
    }

    fun confirmReloadAll() {
        dismissReloadAll()
        runAction(null, "已触发重新加载全部存储") { repository.reloadAll() }
    }

    private fun runAction(id: Int?, successMessage: String, block: suspend () -> Unit) {
        if (id != null && id in _state.value.busyIds) return
        viewModelScope.launch {
            id?.let { busy -> _state.update { it.copy(busyIds = it.busyIds + busy) } }
            try {
                block()
                _messages.send(successMessage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("操作失败：${e.userMessage()}")
            } finally {
                id?.let { busy -> _state.update { it.copy(busyIds = it.busyIds - busy) } }
            }
            load(showLoading = false)
        }
    }
}
