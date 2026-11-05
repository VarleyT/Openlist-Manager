package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.player.PlaybackEnv
import com.bytegrep.oplist_manager.data.player.PlaybackPositionStore
import com.bytegrep.oplist_manager.data.player.PlaybackSource
import com.bytegrep.oplist_manager.data.player.PlaybackSourceResolver
import com.bytegrep.oplist_manager.data.player.Playlist
import com.bytegrep.oplist_manager.data.player.PositionRules
import com.bytegrep.oplist_manager.data.player.VideoPlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    okHttpClient: OkHttpClient,
    private val resolver: PlaybackSourceResolver,
    private val repository: VideoPlaybackRepository,
    private val positionStore: PlaybackPositionStore,
) : ViewModel() {

    private val initialPath: String = savedStateHandle.get<String>(VideoPlayerActivity.EXTRA_PATH).orEmpty()

    private val _state = MutableStateFlow(VideoPlayerState(title = initialPath.substringAfterLast('/')))
    val state: StateFlow<VideoPlayerState> = _state.asStateFlow()

    private val controller = VideoPlayerController(
        context = context,
        baseClient = okHttpClient,
        onSnapshot = ::onSnapshot,
        onError = ::onPlayerError,
    )

    val player: Player get() = controller.player

    private var env: PlaybackEnv? = null
    private var playlist = Playlist.single(initialPath)
    private val currentPath: String get() = playlist.current ?: initialPath

    /** 播放器中实际已加载的路径；切换中为 null。位置保存与错误处理只针对它 */
    private var loadedPath: String? = null

    /** 当前视频的外挂字幕加载失败后置为 true，重新加载时不再附带外挂字幕 */
    private var externalSubtitlesFailed = false

    /** 已去掉外挂字幕重试，待进入 READY 后再提示用户 */
    private var subtitleNoticePending = false
    private var loadedSource: PlaybackSource? = null

    /** 当前加载的媒体是否已进入过 READY；外挂字幕在准备阶段加载，之后的错误与字幕无关 */
    private var itemReady = false
    private var playlistLoaded = false

    private var resolveJob: Job? = null
    private var progressJob: Job? = null
    private var hideControlsJob: Job? = null
    private var hintJob: Job? = null
    private var noticeJob: Job? = null

    /** 当前视频已自动重取链接的次数；上限 [MAX_AUTO_RECOVERY]，切换视频或手动重试时清零 */
    private var recoveryCount = 0

    /** 上一次快照的播放状态，用于识别「开始播放」「播放结束」的瞬间 */
    private var wasPlaying = false
    private var wasEnded = false
    private var progressTicks = 0

    init {
        viewModelScope.launch {
            loadEnv() ?: return@launch
            resolveAndLoad(startPositionMs = null)
            loadPlaylist()
        }
    }

    private suspend fun loadEnv(): PlaybackEnv? = env ?: try {
        repository.env().also { env = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        _state.update { it.copy(isResolving = false, error = PlayerError(e.userMessage(), recoverable = true)) }
        null
    }

    /** 同目录列表获取失败不影响当前播放，仅不提供上一个/下一个 */
    private suspend fun loadPlaylist() {
        val siblings = try {
            repository.siblingVideos(initialPath, ::isOnlinePlayableVideo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        playlistLoaded = true
        if (initialPath !in siblings) return
        playlist = Playlist(siblings, 0).moveTo(currentPath)
        publishPlaylist()
    }

    private fun publishPlaylist() {
        _state.update { it.copy(hasPrevious = playlist.hasPrevious, hasNext = playlist.hasNext) }
    }

    /**
     * 解析并加载 [currentPath]。[startPositionMs] 为 null 时尝试从记忆位置续播。
     */
    private fun resolveAndLoad(startPositionMs: Long?) {
        resolveJob?.cancel()
        val path = currentPath
        _state.update { it.copy(isResolving = true, error = null, title = path.substringAfterLast('/')) }
        resolveJob = viewModelScope.launch {
            val currentEnv = loadEnv() ?: return@launch
            try {
                val source = resolver.resolve(path, currentEnv)
                val start = startPositionMs ?: rememberedPosition(currentEnv, path)
                _state.update { it.copy(title = source.name, isResolving = false) }
                loadSource(source, start)
                loadedPath = path
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isResolving = false, error = PlayerError(e.userMessage(), recoverable = true))
                }
            }
        }
    }

    private fun loadSource(source: PlaybackSource, startPositionMs: Long) {
        val effective = if (externalSubtitlesFailed) source.copy(subtitles = emptyList()) else source
        loadedSource = effective
        itemReady = false
        controller.load(effective, startPositionMs)
    }

    private suspend fun rememberedPosition(env: PlaybackEnv, path: String): Long {
        val saved = positionStore.get(env.serverId, path) ?: return 0
        if (!PositionRules.shouldRemember(saved.positionMs, saved.durationMs)) return 0
        showHint("已从 ${formatDuration(saved.positionMs)} 继续播放")
        return saved.positionMs
    }

    private fun savePosition() {
        val currentEnv = env ?: return
        val path = loadedPath ?: return
        val position = controller.currentPositionMs()
        val duration = controller.durationMs()
        // 不可取消：退出页面时 viewModelScope 随即取消，也要保证写入完成
        viewModelScope.launch {
            withContext(NonCancellable) { positionStore.save(currentEnv.serverId, path, position, duration) }
        }
    }

    private fun onSnapshot(snapshot: PlaybackSnapshot) {
        if (snapshot.isReady && loadedPath != null) {
            itemReady = true
            if (subtitleNoticePending) {
                subtitleNoticePending = false
                showNotice("外挂字幕加载失败，已关闭外挂字幕")
            }
        }
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
                trackMenu = snapshot.trackMenu,
                videoAspectRatio = snapshot.videoAspectRatio ?: it.videoAspectRatio,
                controlsVisible = it.controlsVisible || (snapshot.isEnded && !playlist.hasNext),
            )
        }
        updateProgressTicker(snapshot.isPlaying)
        // 仅在开始播放的瞬间启动自动隐藏；进度刷新不能重置计时
        if (snapshot.isPlaying && !wasPlaying) scheduleHideControls()
        if (!snapshot.isPlaying) hideControlsJob?.cancel()
        if (wasPlaying && !snapshot.isPlaying) savePosition()
        if (snapshot.isEnded && !wasEnded && loadedPath != null) onEnded()
        wasPlaying = snapshot.isPlaying
        wasEnded = snapshot.isEnded
    }

    private fun onEnded() {
        savePosition()
        if (playlist.hasNext) playNext()
    }

    private fun onPlayerError(error: PlaybackException) {
        // 切换视频期间旧媒体的错误不处理
        if (loadedPath == null) return
        // 外挂字幕（/p 代理 403 等）失败会导致整体报错：先去掉外挂字幕重试，保证视频能播
        val source = loadedSource
        if (source != null && isSubtitleFailure(error, source)) {
            externalSubtitlesFailed = true
            // 等去掉字幕后真正能播再提示；若视频本身有问题，直接展示视频错误
            subtitleNoticePending = true
            loadSource(source, controller.currentPositionMs())
            return
        }
        subtitleNoticePending = false
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
                if (++progressTicks % SAVE_EVERY_TICKS == 0) savePosition()
            }
        }
    }

    private fun isSubtitleFailure(error: PlaybackException, source: PlaybackSource): Boolean {
        val failedUri = generateSequence<Throwable>(error) { it.cause }
            .filterIsInstance<HttpDataSource.HttpDataSourceException>()
            .firstOrNull()?.dataSpec?.uri?.toString()
        return SubtitleFallback.shouldRetryWithoutSubtitles(
            errorCode = error.errorCode,
            failedHttpUri = failedUri,
            subtitleUrls = source.subtitles.map { it.url },
            itemReady = itemReady,
            subtitlesAlreadyDropped = externalSubtitlesFailed,
        )
    }

    private fun showNotice(text: String) {
        noticeJob?.cancel()
        _state.update { it.copy(notice = text) }
        noticeJob = viewModelScope.launch {
            delay(HINT_DURATION_MS)
            _state.update { it.copy(notice = null) }
        }
    }

    private fun showHint(text: String) {
        hintJob?.cancel()
        _state.update { it.copy(resumeHint = text) }
        hintJob = viewModelScope.launch {
            delay(HINT_DURATION_MS)
            _state.update { it.copy(resumeHint = null) }
        }
    }

    // ---------- 用户操作 ----------

    fun retry() {
        recoveryCount = 0
        if (env == null || !playlistLoaded) {
            viewModelScope.launch {
                loadEnv() ?: return@launch
                resolveAndLoad(startPositionMs = if (loadedPath == null) null else controller.currentPositionMs())
                if (!playlistLoaded) loadPlaylist()
            }
            return
        }
        resolveAndLoad(startPositionMs = controller.currentPositionMs())
    }

    fun restartFromBeginning() {
        hintJob?.cancel()
        _state.update { it.copy(resumeHint = null) }
        controller.seekTo(0)
    }

    fun playNext() = switchTo(playlist.next())

    fun playPrevious() = switchTo(playlist.previous())

    private fun switchTo(target: Playlist?) {
        target ?: return
        savePosition()
        controller.stopAndClear()
        controller.clearTrackOverrides()
        loadedPath = null
        loadedSource = null
        externalSubtitlesFailed = false
        subtitleNoticePending = false
        playlist = target
        recoveryCount = 0
        wasEnded = false
        publishPlaylist()
        _state.update { it.copy(videoAspectRatio = null, positionMs = 0, durationMs = 0, audioUnsupported = false) }
        resolveAndLoad(startPositionMs = null)
        showControls()
    }

    fun togglePlayPause() {
        controller.togglePlayPause()
        showControls()
    }

    /** 离开页面时暂停并保存位置 */
    fun pause() {
        controller.pause()
        savePosition()
    }

    fun openTrackDialog() {
        hideControlsJob?.cancel()
        _state.update { it.copy(showTrackDialog = true) }
    }

    fun closeTrackDialog() {
        _state.update { it.copy(showTrackDialog = false) }
        showControls()
    }

    fun selectTrack(option: TrackOption) = controller.selectTrack(option)

    fun disableSubtitles() = controller.disableSubtitles()

    fun setSpeed(speed: Float) {
        controller.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
        showControls()
    }

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
        if (!_state.value.isPlaying || _state.value.showTrackDialog) return
        hideControlsJob = viewModelScope.launch {
            delay(CONTROLS_TIMEOUT_MS)
            hideControls()
        }
    }

    fun toggleLock() {
        _state.update { it.copy(isLocked = !it.isLocked, controlsVisible = true) }
        scheduleHideControls()
    }

    /** 手动切换横竖屏 */
    fun toggleOrientation(currentIsLandscape: Boolean) {
        val next = if (currentIsLandscape) PlayerOrientation.PORTRAIT else PlayerOrientation.LANDSCAPE
        _state.update { it.copy(orientation = next) }
        showControls()
    }

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }

    companion object {
        val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val SAVE_EVERY_TICKS = 30
        private const val CONTROLS_TIMEOUT_MS = 3_500L
        private const val HINT_DURATION_MS = 5_000L
        private const val MAX_AUTO_RECOVERY = 3
    }
}
