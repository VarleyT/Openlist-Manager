package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
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
import com.bytegrep.oplist_manager.data.player.SubtitleSource
import java.util.Locale
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
    val trackMenu: TrackMenu,
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
            .apply {
                // 默认优先系统语言的音轨与字幕
                val language = Locale.getDefault().language
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setPreferredAudioLanguage(language)
                    .setPreferredTextLanguage(language)
                    .build()
            }
    }

    val player: Player get() = exoPlayer

    /** 仅在轨道变化时重建，避免每次进度刷新都生成新菜单导致整页重组 */
    private var trackMenu = TrackMenu()
    private var audioUnsupported = false

    /** 本次加载带外挂字幕且用户未手动选择时，轨道就绪后优先选中第一个外挂字幕 */
    private var preferExternalSubtitle = false

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            if (preferExternalSubtitle) preferExternalSubtitle = !selectExternalSubtitle(tracks)
            trackMenu = PlayerTracks.menuOf(tracks)
            audioUnsupported = hasOnlyUnsupportedAudio(tracks)
        }

        override fun onEvents(player: Player, events: Player.Events) = publish()

        override fun onPlayerError(error: PlaybackException) = onError(error)
    }

    init {
        exoPlayer.addListener(listener)
    }

    /** 加载新的播放源；[startPositionMs] 用于链接过期重取后恢复位置 */
    fun load(source: PlaybackSource, startPositionMs: Long = 0, playWhenReady: Boolean = true) {
        headerPolicy.register(source.url, source.headers)
        preferExternalSubtitle = source.subtitles.isNotEmpty()
        val item = MediaItem.Builder()
            .setUri(source.url)
            .setMediaId(source.path)
            .apply { source.mimeType?.let(::setMimeType) }
            .setSubtitleConfigurations(source.subtitles.mapIndexed(::subtitleConfigOf))
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
                audioUnsupported = audioUnsupported,
                trackMenu = trackMenu,
                videoAspectRatio = size.takeIf { it.width > 0 && it.height > 0 }
                    ?.let { it.width * it.pixelWidthHeightRatio / it.height },
            ),
        )
    }

    /** 选择指定音轨/字幕轨；选择字幕时同时取消「关闭字幕」 */
    /**
     * 内封字幕常带 default 标记而优先被选中（可能为空轨），同名外挂字幕通常才是用户需要的。
     * 返回是否已处理（找到外挂轨或用户已关闭字幕）。
     */
    private fun selectExternalSubtitle(tracks: Tracks): Boolean {
        val params = exoPlayer.trackSelectionParameters
        if (C.TRACK_TYPE_TEXT in params.disabledTrackTypes) return true
        val external = tracks.groups.firstOrNull {
            // 外挂字幕的 ID 在轨道格式上（如 "1:external-0"），轨道组 ID 只是 "1:0"
            it.type == C.TRACK_TYPE_TEXT && it.length > 0 &&
                it.getTrackFormat(0).id?.endsWith(EXTERNAL_SUBTITLE_ID_PREFIX + "0") == true
        } ?: return false
        if (!external.isSelected) {
            exoPlayer.trackSelectionParameters = params.buildUpon()
                .setOverrideForType(TrackSelectionOverride(external.mediaTrackGroup, 0))
                .build()
        }
        return true
    }

    fun selectTrack(option: TrackOption) {
        preferExternalSubtitle = false
        val group = exoPlayer.currentTracks.groups.firstOrNull {
            it.type == option.type && it.mediaTrackGroup.id == option.groupId
        } ?: return
        if (option.trackIndex >= group.length) return
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .setTrackTypeDisabled(option.type, false)
            .build()
    }

    fun disableSubtitles() {
        preferExternalSubtitle = false
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /** 切换视频时清除上一个视频的手动选择（保留「关闭字幕」偏好） */
    fun clearTrackOverrides() {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .clearOverrides()
            .build()
    }

    private fun subtitleConfigOf(index: Int, subtitle: SubtitleSource): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setId(EXTERNAL_SUBTITLE_ID_PREFIX + index)
            .setMimeType(subtitle.mimeType)
            .setLabel(subtitle.name)
            // 第一个外挂字幕默认显示
            .setSelectionFlags(if (index == 0) C.SELECTION_FLAG_DEFAULT else 0)
            .build()

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
        private const val EXTERNAL_SUBTITLE_ID_PREFIX = "external-"
    }
}
