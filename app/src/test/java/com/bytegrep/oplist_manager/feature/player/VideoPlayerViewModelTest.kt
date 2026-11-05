package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.FlagSet
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.player.PlaybackEnv
import com.bytegrep.oplist_manager.data.player.PlaybackPositionStore
import com.bytegrep.oplist_manager.data.player.PlaybackSource
import com.bytegrep.oplist_manager.data.player.PlaybackSourceKind
import com.bytegrep.oplist_manager.data.player.PlaybackSourceResolver
import com.bytegrep.oplist_manager.data.player.SavedPosition
import com.bytegrep.oplist_manager.data.player.SubtitleSource
import com.bytegrep.oplist_manager.data.player.VideoPlaybackRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * VideoPlayerController 在 ViewModel 内部直接创建，因此只替换其内部的 ExoPlayer：
 * 拦截 ExoPlayer.Builder.build() 返回可控的假播放器，并捕获注册的 Player.Listener 来模拟播放器事件。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val resolver = mockk<PlaybackSourceResolver>()
    private val repository = mockk<VideoPlaybackRepository>()
    private val positionStore = mockk<PlaybackPositionStore>(relaxed = true)
    private val exoPlayer = mockk<ExoPlayer>(relaxed = true)
    private val listenerSlot = slot<Player.Listener>()
    private val store = ViewModelStore()

    // 使用真实参数对象（MockK 无法代理 TrackSelectionParameters）；须在 mockkStatic(Util) 之前完成类初始化
    private val defaultTrackParams = TrackSelectionParameters.DEFAULT

    private val env = PlaybackEnv(serverBaseUrl = "http://nas:5244", serverId = "srv", isAdmin = false)

    // 假播放器状态
    private var playbackState = Player.STATE_IDLE
    private var isPlaying = false
    private var positionMs = 0L
    private var durationMs = C.TIME_UNSET
    private var videoSize = VideoSize.UNKNOWN

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // ExoPlayer.Builder 的静态初始化读取 Build.DEVICE（JVM 桩中为 null）
        mockkStatic(Util::class)
        every { Util.isRunningOnEmulator() } returns false
        mockkConstructor(ExoPlayer.Builder::class)
        every { anyConstructed<ExoPlayer.Builder>().build() } returns exoPlayer
        every { exoPlayer.addListener(capture(listenerSlot)) } just Runs
        every { exoPlayer.playbackState } answers { playbackState }
        every { exoPlayer.isPlaying } answers { isPlaying }
        every { exoPlayer.playWhenReady } returns true
        every { exoPlayer.currentPosition } answers { positionMs }
        every { exoPlayer.duration } answers { durationMs }
        every { exoPlayer.bufferedPosition } answers { positionMs }
        every { exoPlayer.isCurrentMediaItemSeekable } returns true
        every { exoPlayer.videoSize } answers { videoSize }
        every { exoPlayer.trackSelectionParameters } returns defaultTrackParams

        coEvery { repository.env() } returns env
        coEvery { repository.siblingVideos(any(), any()) } returns listOf(PATH_1, PATH_2, PATH_3)
        coEvery { resolver.resolve(any(), env) } answers { sourceOf(firstArg()) }
        coEvery { positionStore.get(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun sourceOf(path: String, subtitles: List<SubtitleSource> = emptyList()) = PlaybackSource(
        path = path,
        name = path.substringAfterLast('/'),
        url = "http://nas:5244/d$path",
        kind = PlaybackSourceKind.OPENLIST,
        subtitles = subtitles,
    )

    private fun TestScope.createViewModel(path: String = PATH_2): VideoPlayerViewModel {
        val vm = VideoPlayerViewModel(
            SavedStateHandle(mapOf(VideoPlayerActivity.EXTRA_PATH to path)),
            mockk<Context>(relaxed = true),
            OkHttpClient(),
            resolver,
            repository,
            positionStore,
        )
        store.put("vm", vm)
        // 只执行当前时刻的任务，避免把续播提示等定时任务一并跑完
        runCurrent()
        return vm
    }

    /** 模拟播放器状态变化并触发一次快照回调 */
    private fun emit(state: Int = playbackState, playing: Boolean = isPlaying) {
        playbackState = state
        isPlaying = playing
        listenerSlot.captured.onEvents(exoPlayer, Player.Events(FlagSet.Builder().build()))
    }

    private fun emitPlaying() = emit(Player.STATE_READY, playing = true)

    private fun playerError(code: Int) = PlaybackException("boom", null, code)

    private fun verifyLoaded(path: String, startMs: Long, times: Int = 1) {
        verify(exactly = times) { exoPlayer.setMediaItem(match { it.mediaId == path }, startMs) }
    }

    // ---------- 解析与加载 ----------

    @Test
    fun `initial load resolves path and starts from beginning without saved position`() = runTest {
        // Act
        val vm = createViewModel()

        // Assert
        val state = vm.state.value
        assertFalse(state.isResolving)
        assertNull(state.error)
        assertEquals("2.mkv", state.title)
        assertNull(state.resumeHint)
        verifyLoaded(PATH_2, 0)
        verify { exoPlayer.prepare() }
    }

    @Test
    fun `title before resolving comes from path`() = runTest {
        val vm = VideoPlayerViewModel(
            SavedStateHandle(mapOf(VideoPlayerActivity.EXTRA_PATH to PATH_1)),
            mockk(relaxed = true),
            OkHttpClient(),
            resolver,
            repository,
            positionStore,
        ).also { store.put("vm", it) }

        assertEquals("1.mkv", vm.state.value.title)
        assertTrue(vm.state.value.isResolving)
    }

    @Test
    fun `remembered position resumes playback and shows temporary hint`() = runTest {
        // Arrange
        coEvery { positionStore.get("srv", PATH_2) } returns SavedPosition(70_000, 600_000, 1)

        // Act
        val vm = createViewModel()

        // Assert
        verifyLoaded(PATH_2, 70_000)
        assertEquals("已从 01:10 继续播放", vm.state.value.resumeHint)

        advanceTimeBy(5_001)
        assertNull(vm.state.value.resumeHint)
    }

    @Test
    fun `saved position near the end is ignored`() = runTest {
        coEvery { positionStore.get("srv", PATH_2) } returns SavedPosition(595_000, 600_000, 1)

        val vm = createViewModel()

        verifyLoaded(PATH_2, 0)
        assertNull(vm.state.value.resumeHint)
    }

    @Test
    fun `restart from beginning clears resume hint and seeks to zero`() = runTest {
        coEvery { positionStore.get("srv", PATH_2) } returns SavedPosition(70_000, 600_000, 1)
        val vm = createViewModel()

        vm.restartFromBeginning()

        assertNull(vm.state.value.resumeHint)
        verify { exoPlayer.seekTo(0L) }
    }

    @Test
    fun `env failure shows recoverable error and retry loads video and playlist`() = runTest {
        // Arrange
        coEvery { repository.env() } throws AppException.Unauthorized("尚未登录服务器")
        val vm = createViewModel()
        assertEquals(PlayerError("尚未登录服务器", recoverable = true), vm.state.value.error)
        assertFalse(vm.state.value.isResolving)
        coVerify(exactly = 0) { resolver.resolve(any(), any()) }

        // Act
        coEvery { repository.env() } returns env
        vm.retry()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.error)
        verifyLoaded(PATH_2, 0)
        assertTrue(vm.state.value.hasPrevious)
        assertTrue(vm.state.value.hasNext)
    }

    @Test
    fun `resolve failure shows error and retry resolves again from current position`() = runTest {
        // Arrange
        coEvery { resolver.resolve(PATH_2, env) } throws AppException.Server(500, "object not found")
        val vm = createViewModel()
        assertEquals(PlayerError("object not found", recoverable = true), vm.state.value.error)

        // Act
        coEvery { resolver.resolve(PATH_2, env) } returns sourceOf(PATH_2)
        positionMs = 12_000
        vm.retry()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.error)
        verifyLoaded(PATH_2, 12_000)
    }

    @Test
    fun `retry after playlist failure loads playlist again`() = runTest {
        coEvery { repository.siblingVideos(any(), any()) } throws AppException.Network("网络错误")
        val vm = createViewModel()
        // 同目录列表失败不影响播放
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.hasNext)

        coEvery { repository.siblingVideos(any(), any()) } returns listOf(PATH_1, PATH_2, PATH_3)
        vm.retry()
        advanceUntilIdle()

        assertTrue(vm.state.value.hasNext)
        assertTrue(vm.state.value.hasPrevious)
    }

    // ---------- 播放列表 ----------

    @Test
    fun `playlist flags follow position of current video`() = runTest {
        val first = createViewModel(PATH_1).state.value
        assertFalse(first.hasPrevious)
        assertTrue(first.hasNext)
    }

    @Test
    fun `current video missing from sibling list disables playlist`() = runTest {
        coEvery { repository.siblingVideos(any(), any()) } returns listOf(PATH_1, PATH_3)

        val vm = createViewModel()

        assertFalse(vm.state.value.hasPrevious)
        assertFalse(vm.state.value.hasNext)
    }

    @Test
    fun `play next saves old position stops old media and loads next from start`() = runTest {
        // Arrange
        val vm = createViewModel()
        positionMs = 42_000
        durationMs = 600_000
        videoSize = VideoSize(1920, 1080)
        emit(Player.STATE_READY, playing = false)
        assertEquals(1920f / 1080f, vm.state.value.videoAspectRatio)

        // Act
        vm.playNext()
        advanceUntilIdle()

        // Assert
        coVerify { positionStore.save("srv", PATH_2, 42_000, 600_000, any()) }
        verify { exoPlayer.stop() }
        verify { exoPlayer.clearMediaItems() }
        verifyLoaded(PATH_3, 0)
        val state = vm.state.value
        assertEquals("3.mkv", state.title)
        assertNull(state.videoAspectRatio)
        assertEquals(0L, state.durationMs)
        assertTrue(state.hasPrevious)
        assertFalse(state.hasNext)
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `play previous at first video does nothing`() = runTest {
        val vm = createViewModel(PATH_1)

        vm.playPrevious()
        advanceUntilIdle()

        verify(exactly = 0) { exoPlayer.stop() }
        verifyLoaded(PATH_1, 0)
        assertEquals("1.mkv", vm.state.value.title)
    }

    @Test
    fun `play previous switches to previous video`() = runTest {
        val vm = createViewModel()

        vm.playPrevious()
        advanceUntilIdle()

        verifyLoaded(PATH_1, 0)
        assertFalse(vm.state.value.hasPrevious)
    }

    @Test
    fun `ended video auto plays next one`() = runTest {
        // Arrange
        val vm = createViewModel()
        durationMs = 600_000
        positionMs = 600_000

        // Act
        emit(Player.STATE_ENDED, playing = false)
        advanceUntilIdle()

        // Assert
        coVerify { positionStore.save("srv", PATH_2, 600_000, 600_000, any()) }
        verifyLoaded(PATH_3, 0)
        assertEquals("3.mkv", vm.state.value.title)
    }

    @Test
    fun `ended last video keeps controls visible and does not switch`() = runTest {
        // Arrange
        val vm = createViewModel(PATH_3)
        vm.toggleControls()
        assertFalse(vm.state.value.controlsVisible)

        // Act
        emit(Player.STATE_ENDED, playing = false)
        advanceUntilIdle()

        // Assert
        assertTrue(vm.state.value.isEnded)
        assertTrue(vm.state.value.controlsVisible)
        verify(exactly = 0) { exoPlayer.stop() }
        // 结束后控制层不可隐藏
        vm.toggleControls()
        assertTrue(vm.state.value.controlsVisible)
    }

    // ---------- 播放错误 ----------

    @Test
    fun `recoverable error re-resolves from current position up to three times then shows error`() = runTest {
        // Arrange
        val vm = createViewModel()
        positionMs = 30_000

        // Act & Assert：前三次自动重取链接
        repeat(3) {
            listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
            advanceUntilIdle()
            assertNull(vm.state.value.error)
        }
        coVerify(exactly = 4) { resolver.resolve(PATH_2, env) }
        verifyLoaded(PATH_2, 30_000, times = 3)

        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("网络连接失败，请检查网络后重试", state.error?.message)
        assertTrue(state.controlsVisible)
        assertFalse(state.isBuffering)
        coVerify(exactly = 4) { resolver.resolve(PATH_2, env) }
    }

    @Test
    fun `manual retry resets auto recovery budget`() = runTest {
        val vm = createViewModel()
        repeat(4) {
            listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
            advanceUntilIdle()
        }
        vm.retry()
        advanceUntilIdle()
        coVerify(exactly = 5) { resolver.resolve(PATH_2, env) }

        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        coVerify(exactly = 6) { resolver.resolve(PATH_2, env) }
    }

    @Test
    fun `unrecoverable error is shown immediately`() = runTest {
        val vm = createViewModel()

        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))

        assertEquals(false, vm.state.value.error?.recoverable)
        coVerify(exactly = 1) { resolver.resolve(any(), any()) }
    }

    @Test
    fun `error from old media while switching is ignored`() = runTest {
        // Arrange
        val vm = createViewModel()
        vm.playNext()

        // Act：新视频尚未解析完成时旧媒体报错
        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.error)
        verifyLoaded(PATH_3, 0)
    }

    @Test
    fun `subtitle parsing failure before ready reloads without subtitles and notices after ready`() = runTest {
        // Arrange
        val subtitle = SubtitleSource("http://nas:5244/p/2.ass", "2.ass", "text/x-ssa")
        coEvery { resolver.resolve(PATH_2, env) } returns sourceOf(PATH_2, listOf(subtitle))
        val vm = createViewModel()
        positionMs = 0

        // Act
        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))

        // Assert：直接重新加载同一源，不重新解析，也不显示错误
        verifyLoaded(PATH_2, 0, times = 2)
        coVerify(exactly = 1) { resolver.resolve(any(), any()) }
        assertNull(vm.state.value.error)
        assertNull(vm.state.value.notice)

        emit(Player.STATE_READY, playing = false)
        assertEquals("外挂字幕加载失败，已关闭外挂字幕", vm.state.value.notice)
        advanceTimeBy(5_001)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `video failure after subtitles were dropped shows video error`() = runTest {
        val subtitle = SubtitleSource("http://nas:5244/p/2.ass", "2.ass", "text/x-ssa")
        coEvery { resolver.resolve(PATH_2, env) } returns sourceOf(PATH_2, listOf(subtitle))
        val vm = createViewModel()
        val parsing = PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED

        listenerSlot.captured.onPlayerError(playerError(parsing))
        listenerSlot.captured.onPlayerError(playerError(parsing))

        assertEquals(PlaybackErrorMapper.map(parsing, null), vm.state.value.error)
        // 视频本身有问题时不再提示字幕
        emit(Player.STATE_READY, playing = false)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `parsing failure without subtitles shows error directly`() = runTest {
        val vm = createViewModel()

        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))

        assertEquals(false, vm.state.value.error?.recoverable)
        verifyLoaded(PATH_2, 0, times = 1)
    }

    // ---------- 快照、控制层与进度 ----------

    @Test
    fun `snapshot maps player state and keeps last known aspect ratio`() = runTest {
        // Arrange
        val vm = createViewModel()
        positionMs = 5_000
        durationMs = 60_000
        videoSize = VideoSize(1280, 720)

        // Act
        emit(Player.STATE_BUFFERING, playing = false)

        // Assert
        var state = vm.state.value
        assertTrue(state.isBuffering)
        assertEquals(5_000L, state.positionMs)
        assertEquals(60_000L, state.durationMs)
        assertTrue(state.isSeekable)
        assertEquals(1280f / 720f, state.videoAspectRatio)

        videoSize = VideoSize.UNKNOWN
        durationMs = C.TIME_UNSET
        emit(Player.STATE_READY, playing = false)
        state = vm.state.value
        assertFalse(state.isBuffering)
        assertEquals(0L, state.durationMs)
        assertEquals(1280f / 720f, state.videoAspectRatio)
    }

    @Test
    fun `controls auto hide after playback starts`() = runTest {
        // Arrange
        val vm = createViewModel()

        // Act
        emitPlaying()
        runCurrent()
        advanceTimeBy(3_000)
        assertTrue(vm.state.value.controlsVisible)
        advanceTimeBy(600)

        // Assert
        assertFalse(vm.state.value.controlsVisible)
        assertTrue(vm.state.value.isPlaying)
        store.clear() // 停止进度轮询
    }

    @Test
    fun `pausing cancels auto hide and saves position`() = runTest {
        // Arrange
        val vm = createViewModel()
        durationMs = 600_000
        emitPlaying()
        positionMs = 61_000

        // Act
        emit(Player.STATE_READY, playing = false)
        advanceTimeBy(10_000)

        // Assert
        assertTrue(vm.state.value.controlsVisible)
        coVerify(exactly = 1) { positionStore.save("srv", PATH_2, 61_000, 600_000, any()) }
    }

    @Test
    fun `progress ticker refreshes position and saves every fifteen seconds`() = runTest {
        // Arrange
        val vm = createViewModel()
        durationMs = 600_000
        emitPlaying()

        // Act
        positionMs = 20_000
        advanceTimeBy(501)
        assertEquals(20_000L, vm.state.value.positionMs)
        coVerify(exactly = 0) { positionStore.save(any(), any(), any(), any(), any()) }

        positionMs = 35_000
        advanceTimeBy(15_000)

        // Assert
        coVerify(exactly = 1) { positionStore.save("srv", PATH_2, 35_000, 600_000, any()) }
        store.clear() // 停止进度轮询
    }

    @Test
    fun `toggle controls hides and shows overlay but stays visible on error`() = runTest {
        val vm = createViewModel()

        vm.toggleControls()
        assertFalse(vm.state.value.controlsVisible)
        vm.toggleControls()
        assertTrue(vm.state.value.controlsVisible)

        listenerSlot.captured.onPlayerError(playerError(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        vm.toggleControls()
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test
    fun `track dialog suspends auto hide until closed`() = runTest {
        // Arrange
        val vm = createViewModel()
        emitPlaying()

        // Act
        vm.openTrackDialog()
        advanceTimeBy(10_000)

        // Assert
        assertTrue(vm.state.value.showTrackDialog)
        assertTrue(vm.state.value.controlsVisible)

        vm.closeTrackDialog()
        assertFalse(vm.state.value.showTrackDialog)
        advanceTimeBy(3_600)
        assertFalse(vm.state.value.controlsVisible)
        store.clear() // 停止进度轮询
    }

    @Test
    fun `set speed updates state and player`() = runTest {
        val vm = createViewModel()

        vm.setSpeed(1.5f)

        assertEquals(1.5f, vm.state.value.speed)
        verify { exoPlayer.setPlaybackSpeed(1.5f) }
    }

    @Test
    fun `seek to updates position immediately and clamps negative positions in player`() = runTest {
        val vm = createViewModel()

        vm.seekTo(90_000)
        assertEquals(90_000L, vm.state.value.positionMs)
        vm.seekTo(-5)

        verify { exoPlayer.seekTo(90_000L) }
        verify { exoPlayer.seekTo(0L) }
    }

    @Test
    fun `seek by uses seek back and forward`() = runTest {
        val vm = createViewModel()

        vm.seekBy(-10_000)
        vm.seekBy(10_000)

        verify(exactly = 1) { exoPlayer.seekBack() }
        verify(exactly = 1) { exoPlayer.seekForward() }
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test
    fun `toggle play pause replays ended video`() = runTest {
        val vm = createViewModel()
        playbackState = Player.STATE_ENDED

        vm.togglePlayPause()

        verify { exoPlayer.seekTo(0L) }
        verify { exoPlayer.play() }
        assertTrue(vm.state.value.controlsVisible)
    }

    @Test
    fun `toggle lock and orientation update state`() = runTest {
        val vm = createViewModel()

        vm.toggleLock()
        assertTrue(vm.state.value.isLocked)
        vm.toggleLock()
        assertFalse(vm.state.value.isLocked)

        vm.toggleOrientation(currentIsLandscape = false)
        assertEquals(PlayerOrientation.LANDSCAPE, vm.state.value.orientation)
        vm.toggleOrientation(currentIsLandscape = true)
        assertEquals(PlayerOrientation.PORTRAIT, vm.state.value.orientation)
    }

    @Test
    fun `pause saves position of loaded video`() = runTest {
        val vm = createViewModel()
        positionMs = 61_000
        durationMs = 600_000

        vm.pause()
        advanceUntilIdle()

        verify { exoPlayer.pause() }
        coVerify(exactly = 1) { positionStore.save("srv", PATH_2, 61_000, 600_000, any()) }
    }

    @Test
    fun `pause before video loaded does not save position`() = runTest {
        coEvery { resolver.resolve(PATH_2, env) } throws AppException.Server(500, "x")
        val vm = createViewModel()

        vm.pause()
        advanceUntilIdle()

        coVerify(exactly = 0) { positionStore.save(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `clearing view model releases player`() = runTest {
        createViewModel()

        store.clear()

        verify { exoPlayer.release() }
    }

    private companion object {
        const val PATH_1 = "/剧集/1.mkv"
        const val PATH_2 = "/剧集/2.mkv"
        const val PATH_3 = "/剧集/3.mkv"
    }
}
