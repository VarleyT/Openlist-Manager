package com.bytegrep.oplist_manager.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.SettingGroup
import com.bytegrep.oplist_manager.data.model.SettingItem
import com.bytegrep.oplist_manager.data.repository.SettingRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemSettingsUiState(
    val isAdmin: Boolean = true,
    val group: SettingGroup = SettingGroup.SITE,
    /** 服务器上的设置项（已排除废弃项） */
    val items: List<SettingItem> = emptyList(),
    /** 已修改的 key -> 新值 */
    val edits: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val hasChanges: Boolean get() = edits.isNotEmpty()
    fun valueOf(item: SettingItem): String = edits[item.key] ?: item.value
}

/** 生成只包含修改项的提交列表 */
fun changedItems(items: List<SettingItem>, edits: Map<String, String>): List<SettingItem> =
    items.mapNotNull { item -> edits[item.key]?.takeIf { it != item.value }?.let { item.copy(value = it) } }

@HiltViewModel
class SystemSettingsViewModel @Inject constructor(
    private val repository: SettingRepository,
    session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SystemSettingsUiState())
    val state: StateFlow<SystemSettingsUiState> = combine(_state, session.currentUser) { s, user ->
        s.copy(isAdmin = user?.isAdmin != false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SystemSettingsUiState())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        if (session.currentUser.value?.isAdmin != false) load()
    }

    /** 切换分组会丢弃未保存修改，调用方需先确认 */
    fun selectGroup(group: SettingGroup) {
        if (group == _state.value.group && _state.value.items.isNotEmpty()) return
        _state.update { it.copy(group = group, items = emptyList(), edits = emptyMap()) }
        load()
    }

    fun load() {
        val group = _state.value.group
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val items = repository.list(group.id).filterNot { it.isDeprecated }
                _state.update { it.copy(items = items, edits = emptyMap(), isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        }
    }

    fun onValueChange(item: SettingItem, value: String) {
        if (item.isReadOnly) return
        _state.update { s ->
            val edits = if (value == item.value) s.edits - item.key else s.edits + (item.key to value)
            s.copy(edits = edits)
        }
    }

    fun discardChanges() {
        _state.update { it.copy(edits = emptyMap()) }
    }

    fun save() {
        val current = _state.value
        val changed = changedItems(current.items, current.edits)
        if (current.isSaving || changed.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            try {
                repository.save(changed)
                _events.send("已保存 ${changed.size} 项设置")
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send("保存失败：${e.userMessage()}")
            } finally {
                _state.update { it.copy(isSaving = false) }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
