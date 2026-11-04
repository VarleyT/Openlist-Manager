package com.bytegrep.oplist_manager.feature.users

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

/** 编辑页可触发的二次确认 */
private enum class UserConfirm { DELETE, CANCEL_2FA }

@Composable
fun UserEditScreen(
    onBack: () -> Unit,
    viewModel: UserEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirm by rememberSaveable { mutableStateOf<UserConfirm?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is UserEditEvent.Message -> snackbar.showSnackbar(event.text)
                is UserEditEvent.Finished -> onBack()
            }
        }
    }

    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { UserEditTopBar(state = state, onBack = onBack, onSave = viewModel::save) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.isAdminSession -> EmptyState(
                    title = "需要管理员权限",
                    message = "仅管理员可以管理用户",
                    icon = Icons.Outlined.Lock,
                )
                state.isLoading -> LoadingState()
                state.loadError != null -> ErrorState(message = state.loadError.orEmpty(), onRetry = viewModel::load)
                else -> UserEditForm(
                    state = state,
                    viewModel = viewModel,
                    onRequestDelete = { confirm = UserConfirm.DELETE },
                    onRequestCancel2fa = { confirm = UserConfirm.CANCEL_2FA },
                )
            }
        }
    }

    UserConfirmDialog(
        confirm = confirm,
        username = state.original?.username.orEmpty(),
        onConfirm = { type ->
            when (type) {
                UserConfirm.DELETE -> viewModel.delete()
                UserConfirm.CANCEL_2FA -> viewModel.cancel2fa()
            }
        },
        onDismiss = { confirm = null },
    )
}

@Composable
private fun UserEditTopBar(state: UserEditUiState, onBack: () -> Unit, onSave: () -> Unit) {
    LargeTitleBar(
        title = if (state.isNew) "添加用户" else "编辑用户",
        onBack = onBack,
        subtitle = state.original?.username,
        actions = {
            val canSave = state.isAdminSession && !state.isLoading && state.loadError == null
            if (state.isBusy) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(horizontal = Spacing.md).size(20.dp))
            } else if (canSave) {
                TextButton(onClick = onSave) { Text("保存") }
            }
        },
    )
}

@Composable
private fun UserConfirmDialog(
    confirm: UserConfirm?,
    username: String,
    onConfirm: (UserConfirm) -> Unit,
    onDismiss: () -> Unit,
) {
    when (confirm) {
        UserConfirm.DELETE -> ConfirmDialog(
            title = "删除用户",
            message = "确定删除用户 “$username” 吗？此操作不可撤销。",
            confirmLabel = "删除",
            destructive = true,
            onConfirm = { onConfirm(UserConfirm.DELETE) },
            onDismiss = onDismiss,
        )
        UserConfirm.CANCEL_2FA -> ConfirmDialog(
            title = "关闭两步验证",
            message = "关闭后用户 “$username” 登录时将不再需要验证码，确定继续吗？",
            confirmLabel = "关闭",
            destructive = true,
            onConfirm = { onConfirm(UserConfirm.CANCEL_2FA) },
            onDismiss = onDismiss,
        )
        null -> Unit
    }
}

@Composable
private fun UserEditForm(
    state: UserEditUiState,
    viewModel: UserEditViewModel,
    onRequestDelete: () -> Unit,
    onRequestCancel2fa: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.page),
    ) {
        BasicInfoSection(
            state = state,
            onUsernameChange = viewModel::onUsernameChange,
            onPasswordChange = viewModel::onPasswordChange,
            onBasePathChange = viewModel::onBasePathChange,
        )
        RoleStatusSection(
            state = state,
            onRoleChange = viewModel::onRoleChange,
            onDisabledChange = viewModel::onDisabledChange,
            onAllowLdapChange = viewModel::onAllowLdapChange,
        )
        PermissionSection(state = state, onPermissionChange = viewModel::onPermissionChange)
        if (!state.isNew) {
            DangerSection(state = state, onDelete = onRequestDelete, onCancel2fa = onRequestCancel2fa)
        }
        Spacer(Modifier.height(Spacing.xxl))
    }
}
