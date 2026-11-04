package com.bytegrep.oplist_manager.feature.player

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.datastore.ThemeMode
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

/**
 * 独立播放 Activity：便于单独处理方向/全屏，并为后续画中画（Activity 级）预留。
 * 声明了 configChanges，旋转时不重建，SurfaceView 与播放器保持连续。
 */
@AndroidEntryPoint
class VideoPlayerActivity : ComponentActivity() {

    private val viewModel: VideoPlayerViewModel by viewModels()

    private val systemControls by lazy { ActivitySystemControls(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterImmersive()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.isLocked, state.orientation, state.videoAspectRatio) {
                requestedOrientation = orientationFor(state)
            }
            LaunchedEffect(state.isPlaying) { setKeepScreenOn(state.isPlaying) }
            OpenListTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
                VideoPlayerScreen(
                    state = state,
                    player = viewModel.player,
                    actions = viewModel,
                    onBack = ::finish,
                    isLandscape = { resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE },
                    systemControls = systemControls,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 后台播放属于后续阶段（MediaSessionService），当前离开页面即暂停
        if (!isChangingConfigurations) viewModel.pause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
    }

    private fun enterImmersive() {
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun setKeepScreenOn(keepOn: Boolean) {
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun orientationFor(state: VideoPlayerState): Int = when {
        state.isLocked -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        state.orientation == PlayerOrientation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        state.orientation == PlayerOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        // 自动：横向视频转横屏，竖向视频保持竖屏
        (state.videoAspectRatio ?: return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) > 1f ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    companion object {
        const val EXTRA_PATH = "path"

        fun intent(context: Context, path: String): Intent =
            Intent(context, VideoPlayerActivity::class.java).putExtra(EXTRA_PATH, path)
    }
}

/** 亮度只作用于当前窗口（离开播放页自动恢复系统亮度）；音量调节媒体音量 */
private class ActivitySystemControls(private val activity: ComponentActivity) : PlayerSystemControls {
    private val audio = activity.getSystemService(AudioManager::class.java)

    override fun brightness(): Float {
        val window = activity.window.attributes.screenBrightness
        if (window >= 0f) return window
        val system = Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_SYSTEM_BRIGHTNESS)
        return (system / MAX_SYSTEM_BRIGHTNESS).coerceIn(0f, 1f)
    }

    override fun setBrightness(level: Float) {
        activity.window.attributes = activity.window.attributes.apply {
            // 不设为 0，避免屏幕完全变黑
            screenBrightness = level.coerceIn(MIN_BRIGHTNESS, 1f)
        }
    }

    override fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setVolume(level: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * max).roundToInt(), 0)
    }

    private companion object {
        const val DEFAULT_SYSTEM_BRIGHTNESS = 128
        const val MAX_SYSTEM_BRIGHTNESS = 255f
        const val MIN_BRIGHTNESS = 0.01f
    }
}
