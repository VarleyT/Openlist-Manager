package com.bytegrep.oplist_manager.feature.storage

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriverTranslationsTest {

    private val translations = DriverTranslations.parse(
        Json,
        """
        {
          "common": {
            "order": "序号",
            "order-tips": "用于排序",
            "webdav_policy": "WebDAV 策略",
            "webdav_policys": { "302_redirect": "302 重定向" }
          },
          "drivers": {
            "drivers": { "BaiduNetdisk": "百度网盘", "Local": "本机存储", "Cloudreve": "Cloudreve" },
            "Local": {
              "root_folder_path": "根文件夹路径",
              "show_hidden": "显示隐藏",
              "show_hidden-tips": "显示隐藏目录以及文件",
              "order": "本地序号"
            }
          }
        }
        """.trimIndent(),
    )

    @Test
    fun `driver specific label wins over common label`() {
        assertEquals("本地序号", translations.label("Local", "order"))
        assertEquals("序号", translations.label("115 Cloud", "order"))
    }

    @Test
    fun `driver name uses chinese translation`() {
        assertEquals("百度网盘", translations.driverName("BaiduNetdisk"))
        assertEquals("本机存储", translations.driverLabel("Local"))
    }

    @Test
    fun `driver name is null when untranslated or identical`() {
        assertNull(translations.driverName("Cloudreve"))
        assertNull(translations.driverName("NewDriver"))
        assertEquals("NewDriver", translations.driverLabel("NewDriver"))
    }

    @Test
    fun `driver search matches chinese name and original name`() {
        val state = StorageEditUiState(
            isNew = true,
            step = StorageEditStep.SELECT_DRIVER,
            drivers = listOf("BaiduNetdisk", "Local", "Cloudreve"),
            translations = translations,
        )

        assertEquals(listOf("BaiduNetdisk"), state.copy(driverQuery = "百度").filteredDrivers)
        assertEquals(listOf("BaiduNetdisk"), state.copy(driverQuery = "baidu").filteredDrivers)
        assertEquals(listOf("Local"), state.copy(driverQuery = " 本机 ").filteredDrivers)
    }

    @Test
    fun `bundled asset translates baidu netdisk`() {
        val text = java.io.File("src/main/assets/i18n/storage_zh_cn.json").readText()
        assertEquals("百度网盘", DriverTranslations.parse(Json, text).driverName("BaiduNetdisk"))
    }

    @Test
    fun `label falls back to field name when missing`() {
        assertEquals("unknown_field", translations.label("Local", "unknown_field"))
    }

    @Test
    fun `help uses tips key then server text`() {
        assertEquals("显示隐藏目录以及文件", translations.help("Local", "show_hidden", "Show hidden"))
        assertEquals("用于排序", translations.help("Local", "order", "use to sort"))
        assertEquals("server help", translations.help("Local", "root_folder_path", "server help"))
    }

    @Test
    fun `option translated while unknown option kept`() {
        assertEquals("302 重定向", translations.option("Local", "webdav_policy", "302_redirect"))
        assertEquals("native_proxy", translations.option("Local", "webdav_policy", "native_proxy"))
    }

    @Test
    fun `empty translations fall back to raw text`() {
        val empty = DriverTranslations.EMPTY
        assertEquals("order", empty.label("Local", "order"))
        assertEquals("use to sort", empty.help("Local", "order", "use to sort"))
    }
}
