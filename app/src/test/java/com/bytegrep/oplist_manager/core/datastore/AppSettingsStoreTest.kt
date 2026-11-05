package com.bytegrep.oplist_manager.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只覆盖默认值。AppSettingsStore 的读写依赖 `preferencesDataStore` 委托，
 * 原因同 [ServerStoreTest]，无法在本地 JVM 单测中稳定验证。
 */
class AppSettingsStoreTest {

    @Test
    fun `first launch settings follow system theme and enable auto update check`() {
        // Act
        val defaults = AppSettings()

        // Assert
        assertEquals(ThemeMode.SYSTEM, defaults.themeMode)
        assertFalse(defaults.dynamicColor)
        assertFalse(defaults.fileGrid)
        assertTrue(defaults.autoCheckUpdate)
    }

    @Test
    fun `update check record defaults to never checked and nothing ignored`() {
        // Act
        val record = UpdateCheckRecord()

        // Assert
        assertEquals(0L, record.lastCheckAt)
        assertNull(record.ignoredVersion)
    }
}
