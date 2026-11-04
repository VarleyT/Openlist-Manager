package com.bytegrep.oplist_manager.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.repository.FileRepository
import com.bytegrep.oplist_manager.navigation.FilePreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 文本预览加载状态 */
sealed interface TextPreview {
    data object Loading : TextPreview
    data class Content(val text: String) : TextPreview
    data class Error(val message: String) : TextPreview
}

data class FilePreviewUiState(
    val path: String,
    val isLoading: Boolean = true,
    val error: String? = null,
    val file: FsGetData? = null,
    val kind: FileKind = FileKind.OTHER,
    /** 非空表示以文本方式预览 */
    val text: TextPreview? = null,
) {
    val fileName: String get() = file?.name ?: path.substringAfterLast('/').ifEmpty { path }
    val mimeType: String? get() = guessMimeType(fileName)
}

@HiltViewModel
class FilePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FileRepository,
    private val downloadHelper: DownloadHelper,
) : ViewModel() {

    private val path: String = savedStateHandle.toRoute<FilePreviewRoute>().path

    private val _state = MutableStateFlow(FilePreviewUiState(path = path))
    val state: StateFlow<FilePreviewUiState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun load() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val file = repository.get(path)
                val kind = fileKindOf(file.name, file.isDir, file.type)
                val asText = canPreviewText(file.name, file.type, file.size) && file.rawUrl.isNotBlank()
                _state.update {
                    it.copy(isLoading = false, file = file, kind = kind, text = if (asText) TextPreview.Loading else null)
                }
                if (asText) loadText(file.rawUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.userMessage()) }
            }
        }
    }

    private suspend fun loadText(url: String) {
        val result = try {
            TextPreview.Content(repository.readText(url, TEXT_PREVIEW_LIMIT))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            TextPreview.Error(e.userMessage())
        }
        _state.update { it.copy(text = result) }
    }

    fun download() {
        val file = _state.value.file ?: return
        viewModelScope.launch {
            val message = try {
                downloadHelper.enqueue(file.rawUrl, file.name)
                "已开始下载，可在通知栏查看进度"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "下载失败：${e.userMessage()}"
            }
            _messages.send(message)
        }
    }

    /** 供 UI 层（剪贴板、外部应用）完成后反馈 */
    fun notify(message: String) {
        if (message.isBlank()) return
        viewModelScope.launch { _messages.send(message) }
    }
}
