package com.bytegrep.oplist_manager.feature.storage

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.component.ConfirmDialog
import com.bytegrep.oplist_manager.core.designsystem.component.EmptyState
import com.bytegrep.oplist_manager.core.designsystem.component.ErrorState
import com.bytegrep.oplist_manager.core.designsystem.component.GroupRow
import com.bytegrep.oplist_manager.core.designsystem.component.LargeTitleBar
import com.bytegrep.oplist_manager.core.designsystem.component.LoadingState
import com.bytegrep.oplist_manager.core.designsystem.component.OlCard
import com.bytegrep.oplist_manager.core.designsystem.component.SectionHeader
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Radius
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing
import com.bytegrep.oplist_manager.data.model.DriverInfo

private val CARD_GAP = 12.dp

@Composable
fun StorageEditScreen(onBack: () -> Unit) {
    val viewModel: StorageEditViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StorageEditEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is StorageEditEvent.Saved -> {
                    Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
                    currentOnBack()
                }
                StorageEditEvent.Exit -> currentOnBack()
            }
        }
    }
    BackHandler(onBack = viewModel::onBackRequested)

    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTitleBar(
                title = if (state.isNew) "添加存储" else "编辑存储",
                subtitle = state.driver.takeIf { state.step == StorageEditStep.FORM },
                onBack = viewModel::onBackRequested,
            ) {
                if (state.step == StorageEditStep.FORM && state.info != null) {
                    SaveAction(isSaving = state.isSaving, onSave = viewModel::save)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg).padding(padding).imePadding()) {
            when (state.step) {
                StorageEditStep.SELECT_DRIVER -> DriverPicker(state, viewModel)
                StorageEditStep.FORM -> FormContent(state, viewModel)
            }
        }
    }

    if (state.showDiscardDialog) {
        ConfirmDialog(
            title = "放弃修改？",
            message = "当前有未保存的修改，返回后将丢失。",
            confirmLabel = "放弃",
            destructive = true,
            onConfirm = viewModel::confirmDiscard,
            onDismiss = viewModel::dismissDiscard,
        )
    }
}

@Composable
private fun SaveAction(isSaving: Boolean, onSave: () -> Unit) {
    TextButton(onClick = onSave, enabled = !isSaving) {
        if (isSaving) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("保存")
        }
    }
}

@Composable
private fun DriverPicker(state: StorageEditUiState, viewModel: StorageEditViewModel) {
    when {
        state.isLoading -> LoadingState()
        state.loadError != null -> ErrorState(message = state.loadError, onRetry = viewModel::retry)
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = Spacing.xxl),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = state.driverQuery,
                    onValueChange = viewModel::onDriverQueryChange,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    placeholder = { Text("搜索驱动") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.button),
                )
            }
            item(key = "header") { SectionHeader(title = "选择驱动（${state.filteredDrivers.size}）") }
            driverRows(state.filteredDrivers, viewModel::selectDriver)
        }
    }
}

private fun LazyListScope.driverRows(drivers: List<String>, onSelect: (String) -> Unit) {
    if (drivers.isEmpty()) {
        item(key = "empty") { EmptyState(title = "没有匹配的驱动") }
        return
    }
    items(drivers, key = { it }) { driver ->
        OlCard(modifier = Modifier.padding(bottom = Spacing.sm)) {
            GroupRow(
                title = driver,
                icon = driverIcon(driver),
                onClick = { onSelect(driver) },
            )
        }
    }
}

@Composable
private fun FormContent(state: StorageEditUiState, viewModel: StorageEditViewModel) {
    val info = state.info
    when {
        state.isLoading -> LoadingState()
        state.loadError != null -> ErrorState(message = state.loadError, onRetry = viewModel::retry)
        info == null -> LoadingState()
        else -> DriverForm(state, info, viewModel::onValueChange)
    }
}

@Composable
private fun DriverForm(state: StorageEditUiState, info: DriverInfo, onValueChange: (String, String) -> Unit) {
    val commonFields = remember(info) { info.common.map { FormField(StorageFormKeys.commonKey(it.name), it) } }
    val additionFields = remember(info) { info.additional.map { FormField(StorageFormKeys.additionKey(it.name), it) } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, bottom = Spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP),
    ) {
        item(key = "common_header") { SectionHeader(title = "基本设置") }
        item(key = "common") {
            DynamicFormSection(commonFields, state.driver, state.translations, state.values, state.fieldErrors, !state.isSaving, onValueChange)
        }
        if (additionFields.isNotEmpty()) {
            item(key = "addition_header") { SectionHeader(title = "驱动配置") }
            item(key = "addition") {
                DynamicFormSection(additionFields, state.driver, state.translations, state.values, state.fieldErrors, !state.isSaving, onValueChange)
            }
        }
        item(key = "footer") {
            Column {
                HorizontalDivider(color = OpenListTheme.colors.hairline)
                Text(
                    "带 * 的字段为必填项。未在表单中列出的字段将保持原值。",
                    style = MaterialTheme.typography.bodySmall,
                    color = OpenListTheme.colors.ink3,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        }
    }
}
