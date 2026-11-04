package com.bytegrep.oplist_manager.feature.storage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.DriverItem

/** 表单字段：key 为带前缀的表单 key（见 [StorageFormKeys]） */
data class FormField(val key: String, val item: DriverItem)

private const val TEXT_MIN_LINES = 3

/** 已翻译的字段文案 */
private data class FieldText(val label: String, val help: String, val optionLabel: (String) -> String)

private fun FormField.text(driver: String, translations: DriverTranslations): FieldText {
    val name = translations.label(driver, item.name)
    return FieldText(
        label = if (item.required) "$name *" else name,
        help = translations.help(driver, item.name, item.help),
        optionLabel = { translations.option(driver, item.name, it) },
    )
}

@Composable
fun DynamicFormSection(
    fields: List<FormField>,
    driver: String,
    translations: DriverTranslations,
    values: Map<String, String>,
    errors: Map<String, String>,
    enabled: Boolean,
    onValueChange: (String, String) -> Unit,
) {
    OlCard {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            fields.forEach { field ->
                DynamicField(
                    field = field,
                    text = field.text(driver, translations),
                    value = values[field.key].orEmpty(),
                    error = errors[field.key],
                    enabled = enabled,
                    onValueChange = { onValueChange(field.key, it) },
                )
            }
        }
    }
}

@Composable
private fun DynamicField(
    field: FormField,
    text: FieldText,
    value: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    when (field.item.type) {
        DriverItemType.BOOL -> SwitchField(text.label, text.help, value, enabled, onValueChange)
        DriverItemType.SELECT -> SelectField(field.item, text, value, error, enabled, onValueChange)
        else -> TextInputField(field.item, text, value, error, enabled, onValueChange)
    }
}

@Composable
private fun SwitchField(label: String, help: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) {
    val checked = value.equals("true", ignoreCase = true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = OpenListTheme.colors.ink)
            if (help.isNotBlank()) {
                Text(help, style = MaterialTheme.typography.bodySmall, color = OpenListTheme.colors.ink3)
            }
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, enabled = enabled, onCheckedChange = { onValueChange(it.toString()) })
    }
}

@Composable
private fun TextInputField(
    item: DriverItem,
    text: FieldText,
    value: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val isSecret = isSecretField(item.name)
    var revealed by rememberSaveable(item.name) { mutableStateOf(false) }
    val isText = item.type == DriverItemType.TEXT
    val support = supportMessage(error, text.help)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(text.label) },
        singleLine = !isText,
        minLines = if (isText) TEXT_MIN_LINES else 1,
        isError = error != null,
        supportingText = support?.let { message -> @Composable { Text(message) } },
        shape = RoundedCornerShape(Radius.button),
        keyboardOptions = KeyboardOptions(
            keyboardType = when {
                item.type == DriverItemType.NUMBER -> KeyboardType.Decimal
                isSecret -> KeyboardType.Password
                else -> KeyboardType.Text
            },
        ),
        visualTransformation = if (isSecret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isSecret) {
            { RevealToggle(revealed) { revealed = !revealed } }
        } else {
            null
        },
    )
}

@Composable
private fun RevealToggle(revealed: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            if (revealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (revealed) "隐藏" else "显示",
        )
    }
}

/** 错误信息优先，其次显示 help */
private fun supportMessage(error: String?, help: String): String? = error ?: help.ifBlank { null }

/** select：只读输入框 + 点击弹出下拉选项 */
@Composable
private fun SelectField(
    item: DriverItem,
    text: FieldText,
    value: String,
    error: String?,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    var expanded by rememberSaveable(item.name) { mutableStateOf(false) }
    val options = item.optionList()
    val support = supportMessage(error, text.help)
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = if (value.isEmpty()) value else text.optionLabel(value),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text.label) },
            isError = error != null,
            supportingText = support?.let { message -> @Composable { Text(message) } },
            shape = RoundedCornerShape(Radius.button),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        // 覆盖层接收点击，避免只读输入框吞掉点击事件
        Box(Modifier.matchParentSize().clickable(enabled = enabled) { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = OpenListTheme.colors.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text.optionLabel(option), fontWeight = if (option == value) FontWeight.SemiBold else FontWeight.Normal) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
