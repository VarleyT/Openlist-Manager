package com.bytegrep.oplist_manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.data.update.AppRelease
import com.bytegrep.oplist_manager.data.update.StartupUpdateChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartState {
    data object Loading : StartState
    data class Ready(val loggedIn: Boolean) : StartState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    settingsStore: AppSettingsStore,
    private val updateChecker: StartupUpdateChecker,
) : ViewModel() {

    private val _start = MutableStateFlow<StartState>(StartState.Loading)
    val start: StateFlow<StartState> = _start.asStateFlow()

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val authExpired: SharedFlow<String> = sessionManager.authExpired

    private val _pendingUpdate = MutableStateFlow<AppRelease?>(null)
    /** 启动检查发现的新版本，非空时弹出提醒 */
    val pendingUpdate: StateFlow<AppRelease?> = _pendingUpdate.asStateFlow()

    init {
        viewModelScope.launch {
            val loggedIn = sessionManager.restore()
            _start.value = StartState.Ready(loggedIn)
            if (loggedIn) runCatching { sessionManager.refreshUser() }
        }
        viewModelScope.launch {
            _pendingUpdate.value = updateChecker.check(BuildConfig.VERSION_NAME)
        }
    }

    fun dismissUpdate() {
        _pendingUpdate.value = null
    }

    fun ignoreUpdate() {
        val release = _pendingUpdate.value ?: return
        _pendingUpdate.value = null
        viewModelScope.launch { updateChecker.ignore(release.version) }
    }
}
