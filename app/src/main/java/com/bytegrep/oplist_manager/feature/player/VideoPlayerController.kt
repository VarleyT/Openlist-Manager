package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.bytegrep.oplist_manager.data.player.PlaybackSource
import okhttp3.OkHttpClient

/** 播放器快照，由 [VideoPlayerController] 在状态变化时回调 */
data class PlaybackSnapshot(
    val isBuffering: Boolean,
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val isEnded: Boolean,
    val isReady: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val isSeekable: Boolean,
    val videoAspectRatio: Float?,
    /** 媒体含音轨但设备全部无法解码（如 DTS/TrueHD），此时会无声播放 */
    val audioUnsupported: Boolean,
)

/**
 * 封装 ExoPlayer：网络栈、解码策略、MediaItem 构建与状态映射。
 * 生命周期由 ViewModel 管理（跨配置变更存活），对外仅暴露 [Player] 接口，便于后续替换为 MediaController。
 */
@OptIn(UnstableApi::class)
class VideoPlayerController(
    context: Context,
    baseClient: OkHttpClient,
    private val onSnapshot: (PlaybackSnapshot) -> Unit,
    private val onError: (PlaybackException) -> Unit,
) {
    private val headerPolicy = PlaybackHeaderPolicy()

    private val exoPlayer: ExoPlayer = run {
        val httpFactory = OkHttpDataSource.Factory(baseClient.forPlayback(headerPolicy))
        val dataSourceFactory = ResolvingDataSource.Factory(httpFactory) { spec ->
            spec.withAdditionalHeaders(headerPolicy.headersFor(spec.uri.toString()))
        }
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .build()
    }

    val player: Player get() = exoPlayer

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) = onError(error)
    }

    init {
        exoPlayer.addListener(listener)
    }

    /** 加载新的播放源；[startPositionMs] 用于链接过期重取后恢复位置 */
    fun load(source: PlaybackSource, startPositionMs: Long = 0, playWhenReady: Boolean = true) {
        headerPolicy.register(source.url, source.headers)
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(source.path)
            .apply { source.mimeType?.let(::setMimeType) }
            .build()
        exoPlayer.setMediaItem(item, startPositionMs)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    fun togglePlayPause() {
        when {
            exoPlayer.playbackState == Player.STATE_ENDED -> {
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            exoPlayer.playWhenReady -> exoPlayer.pause()
            else -> exoPlayer.play()
        }
    }

    fun pause() = exoPlayer.pause()

    /** 切换视频前停止并清空当前媒体，旧视频不再产生进度、结束或错误事件 */
    fun stopAndClear() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    /** 快进/快退跳到最近关键帧，响应更快 */
    fun seekBy(deltaMs: Long) {
        exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        if (deltaMs < 0) exoPlayer.seekBack() else exoPlayer.seekForward()
    }

    /** 拖动进度条：精确定位 */
    fun seekTo(positionMs: Long) {
        exoPlayer.setSeekParameters(SeekParameters.EXACT)
        exoPlayer.seekTo(positionMs.coerceAtLeast(0))
    }

    fun currentPositionMs(): Long = exoPlayer.currentPosition

    fun durationMs(): Long = exoPlayer.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0

    fun setSpeed(speed: Float) = exoPlayer.setPlaybackSpeed(speed)

    /** 周期性刷新进度（Player.Listener 不会因播放进度变化回调） */
    fun publish() {
        val state = exoPlayer.playbackState
        val size: VideoSize = exoPlayer.videoSize
        onSnapshot(
            PlaybackSnapshot(
                isBuffering = state == Player.STATE_BUFFERING,
                isPlaying = exoPlayer.isPlaying,
                playWhenReady = exoPlayer.playWhenReady,
                isEnded = state == Player.STATE_ENDED,
                isReady = state == Player.STATE_READY,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0),
                isSeekable = exoPlayer.isCurrentMediaItemSeekable,
                audioUnsupported = hasOnlyUnsupportedAudio(exoPlayer.currentTracks),
                videoAspectRatio = size.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { it.width * it.pixelWidthHeightRatio / it.height },
            ),
        )
    }

    private fun hasOnlyUnsupportedAudio(tracks: Tracks): Boolean {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        return audioGroups.isNotEmpty() && audioGroups.none { it.isSupported }
    }

    fun release() {
        exoPlayer.removeListener(listener)
        exoPlayer.release()
    }

    companion object {
        const val SEEK_INCREMENT_MS = 10_000L
    }
}
