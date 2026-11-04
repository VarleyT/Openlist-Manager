package com.bytegrep.oplist_manager.data.update

import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 启动时静默检查更新：受开关控制、[CHECK_INTERVAL_MS] 内最多一次、跳过已忽略版本，失败不打扰用户。
 */
@Singleton
class StartupUpdateChecker @Inject constructor(
    private val repository: UpdateRepository,
    private val settingsStore: AppSettingsStore,
) {

    /** 需要提醒时返回新版本，否则返回 null */
    suspend fun check(currentVersion: String, now: Long = System.currentTimeMillis()): AppRelease? {
        if (!settingsStore.settings.first().autoCheckUpdate) return null
        val record = settingsStore.updateCheckRecord()
        val elapsed = now - record.lastCheckAt
        // elapsed < 0 说明系统时间被回拨，允许重新检查
        if (elapsed in 0 until CHECK_INTERVAL_MS) return null
        // 先记录时间：即使请求失败也按间隔节流，避免频繁打到 GitHub 限额
        settingsStore.setLastUpdateCheckAt(now)
        val result = try {
            repository.check(currentVersion)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        return (result as? UpdateCheckResult.Available)?.release
            ?.takeIf { it.version != record.ignoredVersion }
    }

    suspend fun ignore(version: String) {
        settingsStore.setIgnoredVersion(version)
    }

    companion object {
        const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
