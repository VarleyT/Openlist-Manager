package com.bytegrep.oplist_manager.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.update.AppRelease
import com.bytegrep.oplist_manager.data.update.UpdateCheckResult
import com.bytegrep.oplist_manager.data.update.UpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: AppRelease, val dialogVisible: Boolean = true) : UpdateUiState
    data class Failed(val message: String) : UpdateUiState
}

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val updateRepository: UpdateRepository,
) : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    fun checkUpdate() {
        val current = _updateState.value
        if (current is UpdateUiState.Checking) return
        if (current is UpdateUiState.Available) {
            _updateState.value = current.copy(dialogVisible = true)
            return
        }
        _updateState.value = UpdateUiState.Checking
        viewModelScope.launch {
            _updateState.value = try {
                when (val result = updateRepository.check(BuildConfig.VERSION_NAME)) {
                    UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                    is UpdateCheckResult.Available -> UpdateUiState.Available(result.release)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateUiState.Failed(e.userMessage())
            }
        }
    }

    fun dismissDialog() {
        val current = _updateState.value
        if (current is UpdateUiState.Available) _updateState.value = current.copy(dialogVisible = false)
    }
}
