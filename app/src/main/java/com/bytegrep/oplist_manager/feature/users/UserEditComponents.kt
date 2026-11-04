package com.bytegrep.oplist_manager.feature.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnKeyOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.bytegrep.oplist_manager.core.designsystem.component.GroupCard
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.model.UserRole

@Composable
internal fun BasicInfoSection(
    state: UserEditUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBasePathChange: (String) -> Unit,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    SectionHeader("基本信息")
    OlCard {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            FormField(label = "用户名", value = state.form.username, onValueChange = onUsernameChange)
            FormField(
                label = if (state.isNew) "密码" else "新密码",
                value = state.form.password,
                onValueChange = onPasswordChange,
                supporting = if (state.isNew) null else "留空表示不修改密码",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
            )
            FormField(
                label = "基础路径",
                value = state.form.basePath,
                onValueChange = onBasePathChange,
                supporting = "用户可访问的根目录，例如 /",
            )
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        shape = RoundedCornerShape(Radius.button),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun RoleStatusSection(
    state: UserEditUiState,
    onRoleChange: (Int) -> Unit,
    onDisabledChange: (Boolean) -> Unit,
    onAllowLdapChange: (Boolean) -> Unit,
) {
    val form = state.form
    SectionHeader("角色与状态")
    GroupCard(
        rows = listOf<@Composable () -> Unit>(
            { RoleRow(role = form.role, editable = state.canEditRole, onRoleChange = onRoleChange) },
            {
                SwitchRow(
                    title = "禁用账号",
                    subtitle = if (state.isTargetAdmin) "管理员账号不能被禁用" else "禁用后该用户无法登录",
                    checked = form.disabled,
                    enabled = !state.isTargetAdmin,
                    onCheckedChange = onDisabledChange,
                )
            },
            {
                SwitchRow(
                    title = "允许 LDAP 登录",
                    checked = form.allowLdap,
                    onCheckedChange = onAllowLdapChange,
                )
            },
        ),
    )
}

@Composable
private fun RoleRow(role: Int, editable: Boolean, onRoleChange: (Int) -> Unit) {
    GroupRow(
        title = "角色",
        subtitle = if (editable) null else "管理员角色不可修改",
        showChevron = false,
        trailing = {
            if (editable) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    listOf(UserRole.GENERAL, UserRole.GUEST).forEach { option ->
                        FilterChip(
                            selected = role == option,
                            onClick = { onRoleChange(option) },
                            label = { Text(UserRole.label(option)) },
                        )
                    }
                }
            } else {
                Text(UserRole.label(role), style = MaterialTheme.typography.bodyMedium, color = OpenListTheme.colors.ink2)
            }
        },
    )
}

@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    GroupRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        titleColor = if (enabled) OpenListTheme.colors.ink else OpenListTheme.colors.ink3,
        onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

@Composable
internal fun PermissionSection(state: UserEditUiState, onPermissionChange: (Int, Boolean) -> Unit) {
    val isAdmin = state.isTargetAdmin
    SectionHeader(if (isAdmin) "权限（管理员拥有全部权限）" else "权限")
    GroupCard(
        rows = UserPermission.entries.map<UserPermission, @Composable () -> Unit> { permission ->
            {
                SwitchRow(
                    title = permission.label,
                    checked = state.form.hasPermission(permission.bit),
                    enabled = !isAdmin,
                    onCheckedChange = { enabled -> onPermissionChange(permission.bit, enabled) },
                )
            }
        },
    )
}

@Composable
internal fun DangerSection(state: UserEditUiState, onDelete: () -> Unit, onCancel2fa: () -> Unit) {
    val colors = OpenListTheme.colors
    val rows = buildList<@Composable () -> Unit> {
        add {
            GroupRow(
                title = "关闭两步验证",
                subtitle = if (state.canCancel2fa) "该用户已启用两步验证" else "该用户未启用两步验证",
                icon = Icons.Outlined.VpnKeyOff,
                iconTint = if (state.canCancel2fa) colors.warning else colors.ink3,
                iconBackground = if (state.canCancel2fa) colors.warningSoft else colors.fill,
                titleColor = if (state.canCancel2fa) colors.ink else colors.ink3,
                showChevron = false,
                onClick = if (state.canCancel2fa && !state.isBusy) onCancel2fa else null,
            )
        }
        if (state.canDelete) {
            add {
                GroupRow(
                    title = "删除用户",
                    icon = Icons.Outlined.DeleteOutline,
                    iconTint = colors.danger,
                    iconBackground = colors.dangerSoft,
                    titleColor = colors.danger,
                    showChevron = false,
                    onClick = if (state.isBusy) null else onDelete,
                )
            }
        }
    }
    SectionHeader("安全与危险操作")
    GroupCard(rows = rows)
}
