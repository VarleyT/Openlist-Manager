package com.bytegrep.oplist_manager.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val fileGrid: Boolean = false,
    val autoCheckUpdate: Boolean = true,
)

/** 启动自动检查更新的节流与忽略记录 */
data class UpdateCheckRecord(
    val lastCheckAt: Long = 0L,
    val ignoredVersion: String? = null,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            fileGrid = prefs[KEY_GRID] ?: false,
            autoCheckUpdate = prefs[KEY_AUTO_UPDATE] ?: true,
        )
    }

    suspend fun updateCheckRecord(): UpdateCheckRecord {
        val prefs = context.settingsDataStore.data.first()
        return UpdateCheckRecord(
            lastCheckAt = prefs[KEY_LAST_UPDATE_CHECK] ?: 0L,
            ignoredVersion = prefs[KEY_IGNORED_VERSION],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_DYNAMIC] = enabled }
    }

    suspend fun setFileGrid(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_GRID] = enabled }
    }

    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_UPDATE] = enabled }
    }

    suspend fun setLastUpdateCheckAt(timestamp: Long) {
        context.settingsDataStore.edit { it[KEY_LAST_UPDATE_CHECK] = timestamp }
    }

    suspend fun setIgnoredVersion(version: String) {
        context.settingsDataStore.edit { it[KEY_IGNORED_VERSION] = version }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_GRID = booleanPreferencesKey("file_grid")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_check_update")
        val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_at")
        val KEY_IGNORED_VERSION = stringPreferencesKey("ignored_update_version")
    }
}
