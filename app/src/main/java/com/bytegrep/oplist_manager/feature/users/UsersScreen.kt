package com.bytegrep.oplist_manager.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.PillKind
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole

private val CARD_GAP = 12.dp

@Composable
fun UsersScreen(
    onBack: () -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.events.collect { snackbar.showSnackbar(it) } }
    // 首次进入以及从编辑页返回时刷新
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTitleBar(
                title = "用户管理",
                onBack = onBack,
                subtitle = if (state.users.isNotEmpty()) "共 ${state.users.size} 个用户" else null,
                actions = {
                    if (state.isAdmin) {
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Filled.Add, contentDescription = "添加用户", tint = OpenListTheme.colors.accent)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            UsersContent(state = state, onRefresh = { viewModel.refresh(pull = true) }, onRetry = { viewModel.refresh() }, onEdit = onEdit, onAdd = onAdd)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersContent(
    state: UsersUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
) {
    when {
        !state.isAdmin -> EmptyState(
            title = "需要管理员权限",
            message = "仅管理员可以管理用户",
            icon = Icons.Outlined.Lock,
        )
        state.isLoading && state.users.isEmpty() -> LoadingState()
        state.error != null && state.users.isEmpty() -> ErrorState(message = state.error, onRetry = onRetry)
        else -> PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.users.isEmpty()) {
                EmptyState(title = "暂无用户", actionLabel = "添加用户", onAction = onAdd)
            } else {
                UserList(users = state.users, onEdit = onEdit)
            }
        }
    }
}

@Composable
private fun UserList(users: List<UserDto>, onEdit: (Int) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.page, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        items(users, key = { it.id }) { user ->
            UserCard(user = user, onClick = { onEdit(user.id) })
        }
    }
}

@Composable
private fun UserCard(user: UserDto, onClick: () -> Unit) {
    val colors = OpenListTheme.colors
    OlCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBlock(
                icon = roleIcon(user.role),
                circle = true,
                size = 44.dp,
                tint = if (user.isAdmin) colors.accent else colors.ink2,
                background = if (user.isAdmin) colors.accentSoft else colors.fill,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                UserTitleLine(user)
                Spacer(Modifier.height(2.dp))
                Text(
                    "基础路径：${user.basePath.ifBlank { "/" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            if (user.disabled) {
                StatusPill(text = "已禁用", kind = PillKind.DANGER)
            } else {
                StatusPill(text = "正常", kind = PillKind.SUCCESS)
            }
        }
    }
}

@Composable
private fun UserTitleLine(user: UserDto) {
    val colors = OpenListTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            user.username,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(6.dp))
        TagChip(
            text = UserRole.label(user.role),
            color = if (user.isAdmin) colors.accent else colors.ink2,
            background = if (user.isAdmin) colors.accentSoft else colors.fill,
        )
        if (user.otp) {
            Spacer(Modifier.width(4.dp))
            TagChip(text = "2FA", color = colors.success, background = colors.successSoft)
        }
    }
}

private fun roleIcon(role: Int) = when (role) {
    UserRole.ADMIN -> Icons.Outlined.AdminPanelSettings
    UserRole.GUEST -> Icons.Outlined.PersonOutline
    else -> Icons.Outlined.Person
}
