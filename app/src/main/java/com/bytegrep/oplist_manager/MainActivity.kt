package com.bytegrep.oplist_manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.feature.profile.UpdateDialog
import com.bytegrep.oplist_manager.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { viewModel.start.value is StartState.Loading }
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val start by viewModel.start.collectAsStateWithLifecycle()
            OpenListTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                Box(Modifier.fillMaxSize().background(OpenListTheme.colors.bg)) {
                    val ready = start as? StartState.Ready
                    if (ready != null) {
                        AppNavHost(loggedIn = ready.loggedIn, authExpired = viewModel.authExpired)
                        StartupUpdatePrompt()
                    }
                }
            }
        }
    }

    @Composable
    private fun StartupUpdatePrompt() {
        val release by viewModel.pendingUpdate.collectAsStateWithLifecycle()
        release?.let { pending ->
            UpdateDialog(
                release = pending,
                onDownload = {
                    viewModel.dismissUpdate()
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, pending.pageUrl.toUri())) }
                },
                onDismiss = viewModel::dismissUpdate,
                onIgnore = viewModel::ignoreUpdate,
            )
        }
    }
}
