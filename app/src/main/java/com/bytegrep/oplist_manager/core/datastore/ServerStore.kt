package com.bytegrep.oplist_manager.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已保存的服务器配置。[tokenEnc]、[passwordEnc] 为 Keystore 加密后的密文，绝不保存明文。
 */
@Serializable
data class ServerProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val tokenEnc: String? = null,
    val passwordEnc: String? = null,
    val version: String = "",
    val lastConnectedAt: Long = 0,
)

data class ServerState(
    val servers: List<ServerProfile> = emptyList(),
    val activeId: String? = null,
) {
    val active: ServerProfile? get() = servers.firstOrNull { it.id == activeId }
}

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(name = "servers")

/** 服务器列表持久化（DataStore），已在备份规则中排除 */
@Singleton
class ServerStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val state: Flow<ServerState> = context.serverDataStore.data.map { prefs ->
        ServerState(
            servers = prefs[KEY_SERVERS]?.let { decode(it) }.orEmpty(),
            activeId = prefs[KEY_ACTIVE],
        )
    }

    suspend fun current(): ServerState = state.first()

    /** 新增或按 id 覆盖服务器配置 */
    suspend fun upsert(profile: ServerProfile, makeActive: Boolean) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[KEY_SERVERS]?.let { decode(it) }.orEmpty()
            val updated = if (list.any { it.id == profile.id }) {
                list.map { if (it.id == profile.id) profile else it }
            } else {
                list + profile
            }
            prefs[KEY_SERVERS] = json.encodeToString(updated)
            if (makeActive) prefs[KEY_ACTIVE] = profile.id
        }
    }

    suspend fun update(id: String, transform: (ServerProfile) -> ServerProfile) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[KEY_SERVERS]?.let { decode(it) }.orEmpty()
            prefs[KEY_SERVERS] = json.encodeToString(list.map { if (it.id == id) transform(it) else it })
        }
    }

    suspend fun setActive(id: String?) {
        context.serverDataStore.edit { prefs ->
            if (id == null) prefs.remove(KEY_ACTIVE) else prefs[KEY_ACTIVE] = id
        }
    }

    suspend fun remove(id: String) {
        context.serverDataStore.edit { prefs ->
            val list = prefs[KEY_SERVERS]?.let { decode(it) }.orEmpty().filterNot { it.id == id }
            prefs[KEY_SERVERS] = json.encodeToString(list)
            if (prefs[KEY_ACTIVE] == id) prefs.remove(KEY_ACTIVE)
        }
    }

    private fun decode(raw: String): List<ServerProfile> =
        runCatching { json.decodeFromString<List<ServerProfile>>(raw) }.getOrDefault(emptyList())

    private companion object {
        val KEY_SERVERS = stringPreferencesKey("servers")
        val KEY_ACTIVE = stringPreferencesKey("active_server_id")
    }
}
