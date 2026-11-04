package com.bytegrep.oplist_manager.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.positionDataStore: DataStore<Preferences> by preferencesDataStore(name = "playback_positions")

/** 一条播放位置记录 */
data class SavedPosition(val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
    fun encode(): String = "$positionMs|$durationMs|$updatedAt"

    companion object {
        fun decode(raw: String): SavedPosition? {
            val parts = raw.split('|').mapNotNull { it.toLongOrNull() }
            return if (parts.size == 3) SavedPosition(parts[0], parts[1], parts[2]) else null
        }
    }
}

/**
 * 播放位置记忆：按「服务器 + 路径」的哈希存储（不落明文路径），最多 [MAX_ENTRIES] 条，超出淘汰最旧记录。
 */
@Singleton
class PlaybackPositionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** 读取失败（文件损坏等）视为无记录：位置记忆是可选能力，不能影响播放 */
    suspend fun get(serverId: String, path: String): SavedPosition? = try {
        context.positionDataStore.data.first()[keyOf(serverId, path)]?.let(SavedPosition::decode)
    } catch (e: IOException) {
        null
    }

    /** 按 [PositionRules] 决定保存或清除（已看完/刚开始不记忆） */
    suspend fun save(serverId: String, path: String, positionMs: Long, durationMs: Long, now: Long = System.currentTimeMillis()) {
        val key = keyOf(serverId, path)
        try {
            write(key, positionMs, durationMs, now)
        } catch (e: IOException) {
            // 磁盘已满或文件损坏：放弃本次记忆，不影响播放
        }
    }

    private suspend fun write(key: Preferences.Key<String>, positionMs: Long, durationMs: Long, now: Long) {
        context.positionDataStore.edit { prefs ->
            if (!PositionRules.shouldRemember(positionMs, durationMs)) {
                prefs.remove(key)
                return@edit
            }
            prefs[key] = SavedPosition(positionMs, durationMs, now).encode()
            evictOldest(prefs)
        }
    }

    private fun evictOldest(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        val entries = prefs.asMap().entries
        val overflow = entries.size - MAX_ENTRIES
        if (overflow <= 0) return
        entries
            .mapNotNull { (k, v) -> (v as? String)?.let(SavedPosition::decode)?.let { k to it.updatedAt } }
            .sortedBy { it.second }
            .take(overflow)
            .forEach { (k, _) -> prefs.remove(k) }
    }

    private fun keyOf(serverId: String, path: String): Preferences.Key<String> {
        val digest = MessageDigest.getInstance("SHA-256").digest("$serverId\n$path".toByteArray())
        return stringPreferencesKey(digest.joinToString("") { "%02x".format(it) }.take(KEY_LENGTH))
    }

    private companion object {
        const val MAX_ENTRIES = 300
        const val KEY_LENGTH = 32
    }
}

/** 播放位置记忆规则（纯函数，便于测试） */
object PositionRules {
    /** 少于该时长不记忆 */
    const val MIN_POSITION_MS = 10_000L

    /** 距结尾少于该时长视为看完 */
    const val END_MARGIN_MS = 15_000L

    /** 播放进度超过该比例视为看完 */
    const val FINISHED_RATIO = 0.97

    fun shouldRemember(positionMs: Long, durationMs: Long): Boolean {
        // 时长未知（直播等）不记忆，避免续播到结尾直接触发「播放结束」
        if (positionMs < MIN_POSITION_MS || durationMs <= 0) return false
        return positionMs < durationMs - END_MARGIN_MS && positionMs < durationMs * FINISHED_RATIO
    }
}
