package com.bytegrep.oplist_manager.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import com.bytegrep.oplist_manager.navigation.LoginRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val otpCode: String = "",
    val rememberPassword: Boolean = true,
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val history: List<ServerProfile> = emptyList(),
    val activeId: String? = null,
    val editingId: String? = null,
) {
    val isInsecure: Boolean get() = url.trim().startsWith("http://", ignoreCase = true)
    val canSubmit: Boolean get() = url.isNotBlank() && username.isNotBlank() && password.isNotEmpty() && !isLoading
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<LoginRoute>()

    private val _state = MutableStateFlow(LoginUiState(editingId = route.serverId))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
    val loggedIn = _loggedIn.receiveAsFlow()

    init {
        viewModelScope.launch {
            val servers = authRepository.servers.first()
            _state.update { it.copy(history = servers.servers, activeId = servers.activeId) }
            servers.servers.firstOrNull { it.id == route.serverId }?.let(::fill)
        }
    }

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, error = null) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onOtpChange(value: String) = _state.update { it.copy(otpCode = value.filter(Char::isDigit).take(OTP_LENGTH)) }
    fun onRememberChange(value: Boolean) = _state.update { it.copy(rememberPassword = value) }
    fun togglePasswordVisible() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    /** 从历史连接填入地址与用户名 */
    fun fill(profile: ServerProfile) = _state.update {
        it.copy(url = profile.baseUrl.trimEnd('/'), username = profile.username, editingId = profile.id, error = null)
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                authRepository.login(
                    rawUrl = current.url,
                    username = current.username,
                    password = current.password,
                    otpCode = current.otpCode,
                    rememberPassword = current.rememberPassword,
                    existingId = current.editingId,
                )
                _state.update { it.copy(isLoading = false, password = "") }
                _loggedIn.send(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        }
    }

    private companion object {
        const val OTP_LENGTH = 6
    }
}
