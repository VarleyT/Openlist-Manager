package com.bytegrep.oplist_manager.feature.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.repository.UserRepository
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

data class UsersUiState(
    val isAdmin: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val users: List<UserDto> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val repository: UserRepository,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = combine(_state, session.currentUser) { s, user ->
        // currentUser 尚未加载时按管理员处理，由服务端兜底鉴权
        s.copy(isAdmin = user?.isAdmin != false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UsersUiState())

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    private var loadJob: Job? = null

    /** [pull] 为 true 表示下拉刷新，保留列表并显示刷新指示器 */
    fun refresh(pull: Boolean = false) {
        if (session.currentUser.value?.isAdmin == false) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update {
                it.copy(isLoading = it.users.isEmpty() && !pull, isRefreshing = pull, error = null)
            }
            try {
                val users = repository.list().sortedBy { it.id }
                _state.update { it.copy(users = users, isLoading = false, isRefreshing = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onLoadFailed(e.userMessage())
            }
        }
    }

    private fun onLoadFailed(message: String) {
        val hadData = _state.value.users.isNotEmpty()
        _state.update {
            it.copy(isLoading = false, isRefreshing = false, error = if (hadData) null else message)
        }
        if (hadData) _events.trySend("刷新失败：$message")
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
