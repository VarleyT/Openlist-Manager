package com.bytegrep.oplist_manager.feature.storage

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.navigation.StorageEditRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

enum class StorageEditStep { SELECT_DRIVER, FORM }

data class StorageEditUiState(
    val isNew: Boolean,
    val step: StorageEditStep,
    val isLoading: Boolean = false,
    val loadError: String? = null,
    val drivers: List<String> = emptyList(),
    val driverQuery: String = "",
    val driver: String = "",
    val info: DriverInfo? = null,
    val values: Map<String, String> = emptyMap(),
    val initialValues: Map<String, String> = emptyMap(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val translations: DriverTranslations = DriverTranslations.EMPTY,
    val showDiscardDialog: Boolean = false,
) {
    val isDirty: Boolean get() = step == StorageEditStep.FORM && values != initialValues
    val filteredDrivers: List<String>
        get() = if (driverQuery.isBlank()) drivers else drivers.filter { it.contains(driverQuery.trim(), ignoreCase = true) }
}

sealed interface StorageEditEvent {
    data class Message(val text: String) : StorageEditEvent
    data class Saved(val text: String) : StorageEditEvent
    data object Exit : StorageEditEvent
}

@HiltViewModel
class StorageEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: StorageRepository,
    private val translationsLoader: DriverTranslationsLoader,
) : ViewModel() {

    private val storageId: Int = savedStateHandle.toRoute<StorageEditRoute>().id
    private val isNew = storageId < 0
    private var rawStorage: JsonObject? = null

    private val _state = MutableStateFlow(
        StorageEditUiState(isNew = isNew, step = if (isNew) StorageEditStep.SELECT_DRIVER else StorageEditStep.FORM),
    )
    val state: StateFlow<StorageEditUiState> = _state.asStateFlow()

    private val _events = Channel<StorageEditEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        retry()
        viewModelScope.launch {
            val translations = translationsLoader.load()
            _state.update { it.copy(translations = translations) }
        }
    }

    fun retry() {
        val current = _state.value
        when {
            !isNew -> loadExisting()
            current.step == StorageEditStep.SELECT_DRIVER -> loadDrivers()
            else -> selectDriver(current.driver)
        }
    }

    private fun loadDrivers() = launchLoading {
        val drivers = repository.driverNames()
        _state.update { it.copy(drivers = drivers) }
    }

    private fun loadExisting() = launchLoading {
        val raw = repository.get(storageId)
        val driver = raw["driver"]?.jsonPrimitive?.content.orEmpty()
        val info = repository.driverInfo(driver)
        rawStorage = raw
        val values = extractFormValues(raw, info)
        _state.update { it.copy(driver = driver, info = info, values = values, initialValues = values) }
    }

    fun onDriverQueryChange(query: String) = _state.update { it.copy(driverQuery = query) }

    fun selectDriver(driver: String) {
        _state.update { it.copy(driver = driver, step = StorageEditStep.FORM, info = null, fieldErrors = emptyMap()) }
        launchLoading {
            val info = repository.driverInfo(driver)
            val values = defaultFormValues(info)
            _state.update {
                // 加载期间用户可能已返回驱动列表或切换驱动，此时丢弃结果
                if (it.step != StorageEditStep.FORM || it.driver != driver) it
                else it.copy(info = info, values = values, initialValues = values)
            }
        }
    }

    fun onValueChange(key: String, value: String) = _state.update {
        it.copy(values = it.values + (key to value), fieldErrors = it.fieldErrors - key)
    }

    // ---------- 返回处理 ----------
    fun onBackRequested() {
        val current = _state.value
        if (current.isSaving) return
        if (current.isDirty) _state.update { it.copy(showDiscardDialog = true) } else exit()
    }

    fun dismissDiscard() = _state.update { it.copy(showDiscardDialog = false) }

    fun confirmDiscard() {
        dismissDiscard()
        exit()
    }

    /** 新建流程在表单页返回时回到驱动选择，其余情况退出页面 */
    private fun exit() {
        val current = _state.value
        if (current.isNew && current.step == StorageEditStep.FORM) {
            _state.update {
                it.copy(step = StorageEditStep.SELECT_DRIVER, info = null, values = emptyMap(), initialValues = emptyMap(), loadError = null)
            }
            if (current.drivers.isEmpty()) loadDrivers()
        } else {
            viewModelScope.launch { _events.send(StorageEditEvent.Exit) }
        }
    }

    // ---------- 保存 ----------
    fun save() {
        val current = _state.value
        val info = current.info ?: return
        if (current.isSaving) return
        val errors = validateForm(info, current.values)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors) }
            viewModelScope.launch { _events.send(StorageEditEvent.Message("请检查标红的字段")) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val body = buildStorageJson(rawStorage, current.driver, info, current.values)
                if (isNew) repository.create(body) else repository.update(body)
                _state.update { it.copy(isSaving = false, initialValues = current.values) }
                _events.send(StorageEditEvent.Saved(if (isNew) "存储已添加" else "存储已保存"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false) }
                _events.send(StorageEditEvent.Message("保存失败：${e.userMessage()}"))
            }
        }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.userMessage()) }
            }
        }
    }
}

/** 必填与数字格式校验，返回 key -> 错误信息 */
internal fun validateForm(info: DriverInfo, values: Map<String, String>): Map<String, String> {
    val fields = info.common.map { StorageFormKeys.commonKey(it.name) to it } +
        info.additional.map { StorageFormKeys.additionKey(it.name) to it }
    return fields.mapNotNull { (key, item) ->
        val value = values[key].orEmpty().trim()
        val error = when {
            item.type == DriverItemType.BOOL -> null
            item.required && value.isEmpty() -> "此项为必填"
            item.type == DriverItemType.NUMBER && value.isNotEmpty() && value.toDoubleOrNull() == null -> "请输入数字"
            else -> null
        }
        error?.let { key to it }
    }.toMap()
}
