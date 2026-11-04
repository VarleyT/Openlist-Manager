package com.bytegrep.oplist_manager.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.feature.dashboard.DashboardScreen
import com.bytegrep.oplist_manager.feature.files.FilePreviewScreen
import com.bytegrep.oplist_manager.feature.files.FilesScreen
import com.bytegrep.oplist_manager.feature.login.LoginScreen
import com.bytegrep.oplist_manager.feature.player.VideoPlayerActivity
import com.bytegrep.oplist_manager.feature.player.isOnlinePlayableVideo
import com.bytegrep.oplist_manager.feature.profile.AboutScreen
import com.bytegrep.oplist_manager.feature.profile.AppSettingsScreen
import com.bytegrep.oplist_manager.feature.profile.ProfileScreen
import com.bytegrep.oplist_manager.feature.servers.ServersScreen
import com.bytegrep.oplist_manager.feature.settings.SystemSettingsScreen
import com.bytegrep.oplist_manager.feature.storage.StorageEditScreen
import com.bytegrep.oplist_manager.feature.storage.StorageScreen
import com.bytegrep.oplist_manager.feature.tasks.TasksScreen
import com.bytegrep.oplist_manager.feature.users.UserEditScreen
import com.bytegrep.oplist_manager.feature.users.UsersScreen
import kotlinx.coroutines.flow.SharedFlow

private const val FADE_MS = 220

@Composable
fun AppNavHost(
    loggedIn: Boolean,
    authExpired: SharedFlow<String>,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = TopLevelTab.entries.any { tab -> destination?.hasRoute(tab.route::class) == true }

    LaunchedEffect(authExpired) {
        authExpired.collect { navController.goToLogin() }
    }

    Scaffold(
        containerColor = OpenListTheme.colors.bg,
        bottomBar = {
            if (showBottomBar) {
                BottomBar(
                    isSelected = { tab -> destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true },
                    onSelect = { tab -> navController.switchTab(tab) },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (loggedIn) DashboardRoute else LoginRoute(),
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(FADE_MS)) },
            exitTransition = { fadeOut(tween(FADE_MS)) },
        ) {
            composable<LoginRoute> { entry ->
                val route = entry.toRoute<LoginRoute>()
                LoginScreen(
                    onLoggedIn = { navController.goHome() },
                    onBack = if (route.allowBack) ({ navController.popBackStack() }) else null,
                )
            }
            composable<DashboardRoute> {
                DashboardScreen(
                    onOpenFiles = { navController.switchTab(TopLevelTab.FILES) },
                    onOpenStorage = { navController.switchTab(TopLevelTab.STORAGE) },
                    onOpenTasks = { navController.navigate(TasksRoute) },
                    onOpenUsers = { navController.navigate(UsersRoute) },
                    onOpenServers = { navController.navigate(ServersRoute) },
                    onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                )
            }
            composable<FilesRoute> {
                FilesScreen(
                    onOpenPreview = { path ->
                        if (isOnlinePlayableVideo(path)) {
                            context.startActivity(VideoPlayerActivity.intent(context, path))
                        } else {
                            navController.navigate(FilePreviewRoute(path))
                        }
                    },
                    onOpenTasks = { navController.navigate(TasksRoute) },
                )
            }
            composable<FilePreviewRoute> { FilePreviewScreen(onBack = { navController.popBackStack() }) }
            composable<StorageRoute> {
                StorageScreen(
                    onAdd = { navController.navigate(StorageEditRoute()) },
                    onEdit = { id -> navController.navigate(StorageEditRoute(id)) },
                )
            }
            composable<StorageEditRoute> { StorageEditScreen(onBack = { navController.popBackStack() }) }
            composable<ProfileRoute> {
                ProfileScreen(
                    onOpenServers = { navController.navigate(ServersRoute) },
                    onOpenUsers = { navController.navigate(UsersRoute) },
                    onOpenTasks = { navController.navigate(TasksRoute) },
                    onOpenSystemSettings = { navController.navigate(SystemSettingsRoute) },
                    onOpenAppSettings = { navController.navigate(AppSettingsRoute) },
                    onOpenAbout = { navController.navigate(AboutRoute) },
                    onLoggedOut = { navController.goToLogin() },
                )
            }
            composable<ServersRoute> {
                ServersScreen(
                    onBack = { navController.popBackStack() },
                    onAddServer = { navController.navigate(LoginRoute(allowBack = true)) },
                    onSwitched = { navController.goHome() },
                    onNeedLogin = { id -> navController.navigate(LoginRoute(serverId = id, allowBack = true)) },
                    onActiveRemoved = { navController.goToLogin() },
                )
            }
            composable<UsersRoute> {
                UsersScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(UserEditRoute(id)) },
                    onAdd = { navController.navigate(UserEditRoute()) },
                )
            }
            composable<UserEditRoute> { UserEditScreen(onBack = { navController.popBackStack() }) }
            composable<TasksRoute> { TasksScreen(onBack = { navController.popBackStack() }) }
            composable<SystemSettingsRoute> { SystemSettingsScreen(onBack = { navController.popBackStack() }) }
            composable<AppSettingsRoute> { AppSettingsScreen(onBack = { navController.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { navController.popBackStack() }) }
        }
    }
}

@Composable
private fun BottomBar(isSelected: (TopLevelTab) -> Boolean, onSelect: (TopLevelTab) -> Unit) {
    val colors = OpenListTheme.colors
    HorizontalDivider(color = colors.hairline)
    NavigationBar(containerColor = colors.surface, tonalElevation = 0.dp) {
        TopLevelTab.entries.forEach { tab ->
            val selected = isSelected(tab)
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.accent,
                    selectedTextColor = colors.accent,
                    unselectedIconColor = colors.ink3,
                    unselectedTextColor = colors.ink3,
                    indicatorColor = colors.surface,
                ),
            )
        }
    }
}

private fun NavHostController.switchTab(tab: TopLevelTab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 登录/切换服务器后：清空返回栈并回到概览，重建各页 ViewModel */
private fun NavHostController.goHome() {
    navigate(DashboardRoute) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.goToLogin() {
    navigate(LoginRoute()) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}
