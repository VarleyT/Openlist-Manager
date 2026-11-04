package com.bytegrep.oplist_manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.ThemeMode
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.data.model.UserDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionManager,
    private val authRepository: AuthRepository,
    private val settingsStore: AppSettingsStore,
) : ViewModel() {

    val currentSession: StateFlow<Session?> = session.session
    val user: StateFlow<UserDto?> = session.currentUser
    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), AppSettings())

    private val _loggedOut = Channel<Unit>(Channel.BUFFERED)
    val loggedOut = _loggedOut.receiveAsFlow()

    init {
        if (session.currentUser.value == null) {
            viewModelScope.launch { runCatching { session.refreshUser() } }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _loggedOut.send(Unit)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    fun setFileGrid(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setFileGrid(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}
