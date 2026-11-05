package com.bytegrep.oplist_manager.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.TagChip
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.SettingItem

private const val TYPE_BOOL = "bool"
private const val TYPE_SELECT = "select"
private const val TYPE_NUMBER = "number"
private const val TYPE_TEXT = "text"
private const val MASK = "••••••••"
private const val TEXT_PREVIEW_LINES = 3

@Composable
internal fun SettingsCard(
    state: SystemSettingsUiState,
    onValueChange: (SettingItem, String) -> Unit,
    onEditText: (SettingItem) -> Unit,
) {
    OlCard {
        state.items.forEachIndexed { index, item ->
            SettingRow(
                item = item,
                value = state.valueOf(item),
                modified = item.key in state.edits,
                onValueChange = { onValueChange(item, it) },
                onEditText = { onEditText(item) },
            )
            if (index < state.items.lastIndex) {
                HorizontalDivider(Modifier.padding(start = Spacing.lg), color = OpenListTheme.colors.hairline)
            }
        }
    }
}

@Composable
private fun SettingRow(
    item: SettingItem,
    value: String,
    modified: Boolean,
    onValueChange: (String) -> Unit,
    onEditText: () -> Unit,
) {
    val enabled = !item.isReadOnly
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        if (item.type == TYPE_BOOL) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingTitle(item = item, modified = modified, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                Switch(
                    checked = value.equals("true", ignoreCase = true),
                    onCheckedChange = { onValueChange(it.toString()) },
                    enabled = enabled,
                )
            }
        } else {
            SettingTitle(item = item, modified = modified)
            Spacer(Modifier.height(Spacing.sm))
            SettingEditor(item = item, value = value, enabled = enabled, onValueChange = onValueChange, onEditText = onEditText)
        }
    }
}

@Composable
private fun SettingEditor(
    item: SettingItem,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onEditText: () -> Unit,
) {
    val sensitive = isSensitiveSetting(item.key)
    when (item.type) {
        TYPE_SELECT -> SelectEditor(
            value = value,
            options = parseOptions(item.options),
            label = { settingOptionLabel(item.key, it) },
            enabled = enabled,
            onSelect = onValueChange,
        )
        TYPE_TEXT -> TextPreview(value = value, sensitive = sensitive, enabled = enabled, onClick = onEditText)
        else -> InlineEditor(
            value = value,
            numeric = item.type == TYPE_NUMBER,
            sensitive = sensitive,
            enabled = enabled,
            onValueChange = onValueChange,
        )
    }
}

@Composable
private fun SettingTitle(item: SettingItem, modified: Boolean, modifier: Modifier = Modifier) {
    val colors = OpenListTheme.colors
    val label = settingLabel(item.key)
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item.isReadOnly) {
                Spacer(Modifier.width(6.dp))
                TagChip("只读")
            }
            if (modified) {
                Spacer(Modifier.width(6.dp))
                TagChip("已修改", color = colors.accent, background = colors.accentSoft)
            }
        }
        if (label != item.key) {
            Text(item.key, style = MaterialTheme.typography.labelSmall, color = colors.ink3, maxLines = 1)
        }
        if (item.help.isNotBlank()) {
            Text(item.help, style = MaterialTheme.typography.bodySmall, color = colors.ink2, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

internal fun parseOptions(options: String): List<String> =
    options.split(',').map { it.trim() }.filter { it.isNotEmpty() }

@Composable
private fun SelectEditor(
    value: String,
    options: List<String>,
    label: (String) -> String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled && options.isNotEmpty(),
            shape = RoundedCornerShape(Radius.button),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (value.isBlank()) "未设置" else label(value), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "选择")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun InlineEditor(
    value: String,
    numeric: Boolean,
    sensitive: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val masked = sensitive && !revealed
    OutlinedTextField(
        value = value,
        onValueChange = { input -> if (!numeric || input.isNumericInput()) onValueChange(input) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                numeric -> KeyboardType.Number
                sensitive -> KeyboardType.Password
                else -> KeyboardType.Text
            },
        ),
        visualTransformation = if (masked) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (sensitive) {
            { RevealButton(revealed = revealed, onToggle = { revealed = !revealed }) }
        } else {
            null
        },
        shape = RoundedCornerShape(Radius.button),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** 允许空串、负号与小数点，便于逐字输入 */
private val NUMERIC_INPUT = Regex("^-?\\d*(\\.\\d*)?$")

private fun String.isNumericInput(): Boolean = NUMERIC_INPUT.matches(this)

@Composable
private fun RevealButton(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (revealed) "隐藏" else "显示",
        )
    }
}

@Composable
private fun TextPreview(value: String, sensitive: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = OpenListTheme.colors
    val display = when {
        value.isBlank() -> "未设置，点击编辑"
        sensitive -> MASK
        else -> value
    }
    OlCard(containerColor = colors.fill, borderColor = colors.hairline) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .heightIn(min = 48.dp)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                display,
                style = MaterialTheme.typography.bodySmall,
                color = if (value.isBlank()) colors.ink3 else colors.ink2,
                maxLines = TEXT_PREVIEW_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                Spacer(Modifier.width(Spacing.sm))
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = colors.ink3)
            }
        }
    }
}

/** 多行文本编辑对话框 */
@Composable
internal fun MultilineEditDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 6,
                maxLines = 12,
                shape = RoundedCornerShape(Radius.button),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text)
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        containerColor = OpenListTheme.colors.surface,
    )
}
