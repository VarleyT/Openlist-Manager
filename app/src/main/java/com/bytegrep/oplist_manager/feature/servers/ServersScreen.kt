package com.bytegrep.oplist_manager.feature.servers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Box
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.PillKind
import com.bytegrep.oplist_manager.core.designsystem.component.StatusPill
import com.bytegrep.oplist_manager.core.designsystem.component.TextInputDialog
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServersEvent {
    data object Switched : ServersEvent
    data class NeedLogin(val id: String) : ServersEvent
    data class Message(val text: String) : ServersEvent
}

@HiltViewModel
class ServersViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val state: StateFlow<ServerState> =
        authRepository.servers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT), ServerState())

    private val _events = Channel<ServersEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun switchTo(profile: ServerProfile) {
        viewModelScope.launch {
            val ok = runCatching { authRepository.switchTo(profile.id) }.getOrDefault(false)
            _events.send(if (ok) ServersEvent.Switched else ServersEvent.NeedLogin(profile.id))
        }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { authRepository.rename(id, name) }
    }

    fun remove(id: String) {
        viewModelScope.launch {
            runCatching { authRepository.remove(id) }
                .onSuccess { _events.send(ServersEvent.Message("已移除服务器")) }
                .onFailure { _events.send(ServersEvent.Message(it.userMessage())) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT = 5_000L
    }
}

@Composable
fun ServersScreen(
    onBack: () -> Unit,
    onAddServer: () -> Unit,
    onSwitched: () -> Unit,
    onNeedLogin: (String) -> Unit,
    viewModel: ServersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<ServerProfile?>(null) }
    var deleting by remember { mutableStateOf<ServerProfile?>(null) }
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ServersEvent.Switched -> onSwitched()
                is ServersEvent.NeedLogin -> onNeedLogin(event.id)
                is ServersEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            LargeTitleBar(title = "服务器连接", onBack = onBack, actions = {
                IconButton(onClick = onAddServer) { Icon(Icons.Filled.Add, contentDescription = "添加服务器", tint = OpenListTheme.colors.ink) }
            })
            if (state.servers.isEmpty()) {
                EmptyState(title = "暂无服务器", message = "添加一个 OpenList 服务器开始使用", actionLabel = "添加服务器", onAction = onAddServer)
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.page)) {
                    items(state.servers, key = { it.id }) { profile ->
                        ServerItem(
                            profile = profile,
                            isActive = profile.id == state.activeId,
                            onClick = { viewModel.switchTo(profile) },
                            onRename = { renaming = profile },
                            onDelete = { deleting = profile },
                        )
                        Spacer(Modifier.height(Spacing.md))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    renaming?.let { profile ->
        TextInputDialog(title = "重命名服务器", label = "名称", initial = profile.name, onConfirm = { viewModel.rename(profile.id, it) }, onDismiss = { renaming = null })
    }
    deleting?.let { profile ->
        ConfirmDialog(
            title = "移除服务器",
            message = "确定移除「${profile.name}」？本机保存的 Token 与密码将被删除，不影响服务器本身。",
            confirmLabel = "移除",
            destructive = true,
            onConfirm = { viewModel.remove(profile.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ServerItem(profile: ServerProfile, isActive: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val colors = OpenListTheme.colors
    var menu by remember { mutableStateOf(false) }
    OlCard(onClick = onClick, borderColor = if (isActive) colors.accent.copy(alpha = 0.4f) else colors.edge) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.lg, top = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconBlock(Icons.Outlined.Dns, tint = if (isActive) colors.accent else colors.ink2, background = if (isActive) colors.accentSoft else colors.fill)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleSmall, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${profile.username} · ${profile.baseUrl.trimEnd('/')}", style = MaterialTheme.typography.bodySmall, color = colors.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.version.isNotBlank()) {
                    Text("OpenList ${profile.version}", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                }
            }
            when {
                isActive -> StatusPill("当前", PillKind.ACCENT)
                profile.tokenEnc == null -> StatusPill("需登录", PillKind.NEUTRAL, showDot = false)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "更多", tint = colors.ink3) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("移除", color = colors.danger) }, leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = colors.danger) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}
