package com.bytegrep.oplist_manager.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.designsystem.component.AppLogo
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.IconBlock
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onBack: (() -> Unit)?,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.loggedIn.collect { onLoggedIn() } }

    Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg).statusBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.page),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (onBack != null) 48.dp else 40.dp))
            LoginHeader()
            Spacer(Modifier.height(Spacing.xxl))
            LoginForm(state = state, viewModel = viewModel)
            if (state.history.isNotEmpty()) {
                HistorySection(state.history, state.activeId, onFill = viewModel::fill)
            }
            Spacer(Modifier.height(Spacing.xxl))
            Text(
                "OpenList 客户端 · 已适配 OpenList v4 API",
                style = MaterialTheme.typography.labelSmall,
                color = OpenListTheme.colors.ink3,
            )
            Spacer(Modifier.height(Spacing.xxl))
        }
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(Spacing.xs)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = OpenListTheme.colors.ink)
            }
        }
    }
}

@Composable
private fun LoginHeader() {
    AppLogo()
    Spacer(Modifier.height(Spacing.lg))
    Text("OpenList Manager", style = MaterialTheme.typography.headlineMedium, color = OpenListTheme.colors.ink)
    Spacer(Modifier.height(Spacing.sm))
    Text(
        "连接并管理您的私有 OpenList 远程服务器",
        style = MaterialTheme.typography.bodyMedium,
        color = OpenListTheme.colors.ink2,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun LoginForm(state: LoginUiState, viewModel: LoginViewModel) {
    OlCard {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            LabeledField(
                label = "服务器地址",
                value = state.url,
                onValueChange = viewModel::onUrlChange,
                placeholder = "https://nas.example.com",
                leading = Icons.Outlined.Dns,
                keyboardType = KeyboardType.Uri,
                trailing = {
                    // 地址无法解析时不显示锁图标，避免误导
                    if (state.isSecure || state.isInsecure) {
                        Icon(
                            if (state.isInsecure) Icons.Outlined.LockOpen else Icons.Outlined.Lock,
                            contentDescription = if (state.isInsecure) "未加密连接" else "加密连接",
                            tint = if (state.isInsecure) OpenListTheme.colors.warning else OpenListTheme.colors.success,
                        )
                    }
                },
            )
            if (state.isInsecure) {
                Text(
                    "HTTP 为明文传输，密码与 Token 可能被同一网络中的他人截获，建议仅在可信局域网使用。",
                    style = MaterialTheme.typography.labelSmall,
                    color = OpenListTheme.colors.warning,
                )
            }
            LabeledField("用户名", state.username, viewModel::onUsernameChange, "admin", Icons.Outlined.Person)
            LabeledField(
                label = "密码",
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                placeholder = "请输入密码",
                leading = Icons.Outlined.Key,
                keyboardType = KeyboardType.Password,
                visual = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = viewModel::togglePasswordVisible) {
                        Icon(
                            if (state.passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (state.passwordVisible) "隐藏密码" else "显示密码",
                            tint = OpenListTheme.colors.ink3,
                        )
                    }
                },
            )
            LabeledField(
                label = "双重身份验证（2FA 验证码，可选）",
                value = state.otpCode,
                onValueChange = viewModel::onOtpChange,
                placeholder = "6 位验证码",
                leading = Icons.Outlined.Shield,
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                onDone = viewModel::submit,
            )
            RememberRow(state.rememberPassword, viewModel::onRememberChange)
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.danger)
            }
            SubmitButton(state.isLoading, state.canSubmit, viewModel::submit)
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leading: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = OpenListTheme.colors.ink2)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = OpenListTheme.colors.ink3) },
            leadingIcon = { Icon(leading, contentDescription = null, tint = OpenListTheme.colors.ink3) },
            trailingIcon = trailing,
            singleLine = true,
            visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
            keyboardActions = if (onDone != null) KeyboardActions(onDone = { onDone() }) else KeyboardActions.Default,
            shape = RoundedCornerShape(Radius.button),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = OpenListTheme.colors.fill,
                focusedContainerColor = OpenListTheme.colors.surface,
                unfocusedBorderColor = OpenListTheme.colors.edge,
            ),
        )
    }
}

@Composable
private fun RememberRow(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("记住密码", style = MaterialTheme.typography.titleSmall, color = OpenListTheme.colors.ink)
            Text(
                "加密保存于本机，登录过期时自动重新登录",
                style = MaterialTheme.typography.labelSmall,
                color = OpenListTheme.colors.ink2,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SubmitButton(isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(Radius.button),
    ) {
        if (isLoading) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = OpenListTheme.colors.surface)
            Spacer(Modifier.width(Spacing.sm))
            Text("正在连接…")
        } else {
            Text("连接服务器", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(Spacing.sm))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HistorySection(history: List<ServerProfile>, activeId: String?, onFill: (ServerProfile) -> Unit) {
    SectionHeader(title = "历史连接", action = {
        Text("${history.size} 台服务器", style = MaterialTheme.typography.labelSmall, color = OpenListTheme.colors.ink3)
    })
    GroupCard(
        rows = history.map { profile ->
            {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBlock(Icons.Outlined.Dns, size = 40.dp)
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                profile.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = OpenListTheme.colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (profile.id == activeId) {
                                Spacer(Modifier.width(6.dp))
                                TagChip("上次连接", color = OpenListTheme.colors.accent, background = OpenListTheme.colors.accentSoft)
                            }
                        }
                        Text(
                            "${profile.username} · ${profile.baseUrl.trimEnd('/')}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OpenListTheme.colors.ink2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(
                        onClick = { onFill(profile) },
                        modifier = Modifier.clip(RoundedCornerShape(Radius.pill)),
                    ) { Text("填入") }
                }
            }
        },
    )
}
