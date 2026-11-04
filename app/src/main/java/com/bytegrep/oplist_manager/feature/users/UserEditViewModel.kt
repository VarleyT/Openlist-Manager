package com.bytegrep.oplist_manager.feature.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.UserRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.navigation.UserEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 按位开关权限：返回新的权限位掩码，不修改入参 */
fun togglePermission(mask: Int, bit: Int, enabled: Boolean): Int {
    require(bit in 0 until Int.SIZE_BITS) { "invalid permission bit: $bit" }
    val flag = 1 shl bit
    return if (enabled) mask or flag else mask and flag.inv()
}

data class UserEditUiState(
    val isNew: Boolean,
    val isAdminSession: Boolean = true,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    /** 表单数据；password 字段为新密码（编辑时留空表示不修改） */
    val form: UserDto = UserDto(),
    /** 服务器上的原始用户（编辑模式） */
    val original: UserDto? = null,
    val isBusy: Boolean = false,
) {
    /** 编辑的是管理员账号：角色、禁用、权限只读 */
    val isTargetAdmin: Boolean get() = original?.isAdmin == true
    val canEditRole: Boolean get() = !isTargetAdmin
    val canDelete: Boolean get() = !isNew && original?.let { !it.isAdmin && !it.isGuest } == true
    val canCancel2fa: Boolean get() = !isNew && original?.otp == true
}

sealed interface UserEditEvent {
    data class Message(val text: String) : UserEditEvent
    /** 保存或删除成功，关闭页面 */
    data class Finished(val text: String) : UserEditEvent
}

@HiltViewModel
class UserEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: UserRepository,
    session: SessionManager,
) : ViewModel() {

    private val userId: Int = savedStateHandle.toRoute<UserEditRoute>().id

    private val _state = MutableStateFlow(
        UserEditUiState(
            isNew = userId < 0,
            isAdminSession = session.currentUser.value?.isAdmin != false,
            isLoading = userId >= 0,
        ),
    )
    val state: StateFlow<UserEditUiState> = _state.asStateFlow()

    private val _events = Channel<UserEditEvent>(Channel.BUFFERED)
    val events: Flow<UserEditEvent> = _events.receiveAsFlow()

    init {
        if (userId >= 0 && _state.value.isAdminSession) load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val user = repository.get(userId)
                _state.update { it.copy(isLoading = false, original = user, form = user.copy(password = "")) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.userMessage()) }
            }
        }
    }

    fun onUsernameChange(value: String) = updateForm { it.copy(username = value) }
    fun onPasswordChange(value: String) = updateForm { it.copy(password = value) }
    fun onBasePathChange(value: String) = updateForm { it.copy(basePath = value) }
    fun onRoleChange(role: Int) {
        if (role == UserRole.ADMIN || !_state.value.canEditRole) return
        updateForm { it.copy(role = role) }
    }
    fun onDisabledChange(value: Boolean) {
        if (_state.value.isTargetAdmin) return
        updateForm { it.copy(disabled = value) }
    }
    fun onAllowLdapChange(value: Boolean) = updateForm { it.copy(allowLdap = value) }
    fun onPermissionChange(bit: Int, enabled: Boolean) {
        if (_state.value.isTargetAdmin) return
        updateForm { it.copy(permission = togglePermission(it.permission, bit, enabled)) }
    }

    private fun updateForm(transform: (UserDto) -> UserDto) {
        _state.update { it.copy(form = transform(it.form)) }
    }

    fun save() {
        val current = _state.value
        if (current.isBusy) return
        val error = validate(current)
        if (error != null) {
            _events.trySend(UserEditEvent.Message(error))
            return
        }
        val payload = current.form.copy(
            username = current.form.username.trim(),
            basePath = normalizeBasePath(current.form.basePath),
        )
        runBusy(success = if (current.isNew) "用户已创建" else "用户已保存", finish = true) {
            if (current.isNew) repository.create(payload.copy(id = 0)) else repository.update(payload)
        }
    }

    fun delete() {
        val target = _state.value.original ?: return
        if (!_state.value.canDelete) return
        runBusy(success = "用户 ${target.username} 已删除", finish = true) { repository.delete(target.id) }
    }

    fun cancel2fa() {
        val target = _state.value.original ?: return
        runBusy(success = "已关闭两步验证", finish = false) {
            repository.cancel2fa(target.id)
            _state.update { s ->
                s.copy(original = s.original?.copy(otp = false), form = s.form.copy(otp = false))
            }
        }
    }

    private fun runBusy(success: String, finish: Boolean, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            try {
                block()
                _events.send(if (finish) UserEditEvent.Finished(success) else UserEditEvent.Message(success))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(UserEditEvent.Message(e.userMessage()))
            } finally {
                _state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun validate(state: UserEditUiState): String? = when {
        state.form.username.isBlank() -> "请输入用户名"
        state.isNew && state.form.password.isBlank() -> "请输入密码"
        else -> null
    }

    private fun normalizeBasePath(path: String): String {
        val trimmed = path.trim()
        return when {
            trimmed.isEmpty() -> "/"
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }
    }
}
