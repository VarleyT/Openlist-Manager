package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.player.PlaybackSourceResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    private val resolver: PlaybackSourceResolver,
) : ViewModel() {

    private val path: String = savedStateHandle.get<String>(VideoPlayerActivity.EXTRA_PATH).orEmpty()

    private val _state = MutableStateFlow(VideoPlayerState(title = path.substringAfterLast('/')))
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val controller = VideoPlayerController(
        context = context,
        baseClient = okHttpClient,
        onSnapshot = ::onSnapshot,
        onError = ::onPlayerError,
    )

    val player: Player get() = controller.player

    private var resolveJob: Job? = null
    private var progressJob: Job? = null
    private var hideControlsJob: Job? = null

    /** 本次播放已自动重取链接的次数；上限 [MAX_AUTO_RECOVERY]，用户手动重试时清零，避免无限循环 */
    private var recoveryCount = 0

    /** 上一次快照的播放状态，用于识别「开始播放」的瞬间 */
    private var wasPlaying = false

    init {
        resolveAndLoad(startPositionMs = 0)
    }

    private fun resolveAndLoad(startPositionMs: Long) {
        resolveJob?.cancel()
        _state.update { it.copy(isResolving = true, error = null) }
        resolveJob = viewModelScope.launch {
            try {
                val source = resolver.resolve(path)
                _state.update { it.copy(title = source.name, isResolving = false) }
                controller.load(source, startPositionMs)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isResolving = false, error = PlayerError(e.userMessage(), recoverable = true))
                }
            }
        }
    }

    private fun onSnapshot(snapshot: PlaybackSnapshot) {
        _state.update {
            it.copy(
                isBuffering = snapshot.isBuffering,
                isPlaying = snapshot.isPlaying,
                playWhenReady = snapshot.playWhenReady,
                isEnded = snapshot.isEnded,
                positionMs = snapshot.positionMs,
                durationMs = snapshot.durationMs,
                bufferedMs = snapshot.bufferedMs,
                isSeekable = snapshot.isSeekable,
                audioUnsupported = snapshot.audioUnsupported,
                videoAspectRatio = snapshot.videoAspectRatio ?: it.videoAspectRatio,
                controlsVisible = it.controlsVisible || snapshot.isEnded,
            )
        }
        updateProgressTicker(snapshot.isPlaying)
        // 仅在开始播放的瞬间启动自动隐藏；进度刷新不能重置计时
        if (snapshot.isPlaying && !wasPlaying) scheduleHideControls()
        if (!snapshot.isPlaying) hideControlsJob?.cancel()
        wasPlaying = snapshot.isPlaying
    }

    private fun onPlayerError(error: PlaybackException) {
        val mapped = PlaybackErrorMapper.map(error)
        if (mapped.recoverable && recoveryCount < MAX_AUTO_RECOVERY) {
            // 直链过期 / IP 变化：重新获取地址并从原位置继续
            recoveryCount++
            resolveAndLoad(startPositionMs = controller.currentPositionMs())
            return
        }
        _state.update { it.copy(error = mapped, controlsVisible = true, isBuffering = false) }
    }

    private fun updateProgressTicker(isPlaying: Boolean) {
        if (!isPlaying) {
            progressJob?.cancel()
            progressJob = null
            return
        }
        if (progressJob?.isActive == true) return
        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                controller.publish()
            }
        }
    }

    // ---------- 用户操作 ----------

    fun retry() {
        recoveryCount = 0
        resolveAndLoad(startPositionMs = controller.currentPositionMs())
    }

    fun togglePlayPause() {
        controller.togglePlayPause()
        showControls()
    }

    fun pause() = controller.pause()

    fun seekBy(deltaMs: Long) {
        controller.seekBy(deltaMs)
        showControls()
    }

    fun seekTo(positionMs: Long) {
        controller.seekTo(positionMs)
        _state.update { it.copy(positionMs = positionMs) }
        showControls()
    }

    fun toggleControls() {
        if (_state.value.controlsVisible) hideControls() else showControls()
    }

    fun showControls() {
        _state.update { it.copy(controlsVisible = true) }
        scheduleHideControls()
    }

    private fun hideControls() {
        hideControlsJob?.cancel()
        // 出错或播放结束时保持控制层可见
        _state.update { if (it.error != null || it.isEnded) it else it.copy(controlsVisible = false) }
    }

    private fun scheduleHideControls() {
        hideControlsJob?.cancel()
        if (!_state.value.isPlaying) return
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_TIMEOUT_MS)
            hideControls()
        }
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked, controlsVisible = true) }
        scheduleHideControls()
    }

    /** 手动切换横竖屏；当前为 AUTO 时按视频宽高比推断当前方向再取反 */
    fun toggleOrientation(currentIsLandscape: Boolean) {
        val next = if (currentIsLandscape) PlayerOrientation.PORTRAIT else PlayerOrientation.LANDSCAPE
        _state.update { it.copy(orientation = next) }
        showControls()
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
        const val CONTROLS_TIMEOUT_MS = 3_500L
        const val MAX_AUTO_RECOVERY = 3
    }
}
