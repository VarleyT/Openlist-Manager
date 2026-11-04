package com.bytegrep.oplist_manager.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// ---------- 顶级 Tab ----------
@Serializable data object DashboardRoute
@Serializable data object FilesRoute
@Serializable data object StorageRoute
@Serializable data object ProfileRoute

// ---------- 二级页面 ----------
/** [serverId] 非空时为已保存服务器重新登录 */
@Serializable data class LoginRoute(val serverId: String? = null, val allowBack: Boolean = false)
@Serializable data class FilePreviewRoute(val path: String)
/** [id] 为 -1 时表示新建存储 */
@Serializable data class StorageEditRoute(val id: Int = -1)
@Serializable data object ServersRoute
@Serializable data object UsersRoute
/** [id] 为 -1 时表示新建用户 */
@Serializable data class UserEditRoute(val id: Int = -1)
@Serializable data object TasksRoute
@Serializable data object SystemSettingsRoute
@Serializable data object AppSettingsRoute
@Serializable data object AboutRoute

enum class TopLevelTab(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    DASHBOARD(DashboardRoute, "概览", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    FILES(FilesRoute, "文件", Icons.Filled.Folder, Icons.Outlined.Folder),
    STORAGE(StorageRoute, "存储", Icons.Filled.Storage, Icons.Outlined.Storage),
    PROFILE(ProfileRoute, "我的", Icons.Filled.Person, Icons.Outlined.Person),
}
