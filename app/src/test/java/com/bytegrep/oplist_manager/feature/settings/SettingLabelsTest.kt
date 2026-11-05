package com.bytegrep.oplist_manager.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingLabelsTest {

    @Test
    fun `known keys map to chinese labels`() {
        assertEquals("站点标题", settingLabel("site_title"))
        assertEquals("Aria2 密钥", settingLabel("aria2_secret"))
        assertEquals("115 临时目录", settingLabel("115_temp_dir"))
        assertEquals("解压上传线程数", settingLabel("task_decompress_upload_thread_num"))
    }

    @Test
    fun `unknown key falls back to raw key`() {
        assertEquals("some_new_setting", settingLabel("some_new_setting"))
        assertEquals("", settingLabel(""))
    }

    @Test
    fun `lookup is exact and case sensitive`() {
        assertEquals("SITE_TITLE", settingLabel("SITE_TITLE"))
        assertEquals(" site_title", settingLabel(" site_title"))
    }

    @Test
    fun `keys containing sensitive markers are masked regardless of case`() {
        listOf("token", "aria2_secret", "ldap_manager_password", "s3_access_key_id", "SSO_CLIENT_SECRET", "ApiToken")
            .forEach { assertTrue(it, isSensitiveSetting(it)) }
    }

    @Test
    fun `ordinary keys are not sensitive`() {
        listOf("site_title", "version", "aria2_uri", "max_index_depth", "")
            .forEach { assertFalse(it, isSensitiveSetting(it)) }
    }

    @Test
    fun `substring match also flags keys that merely contain key`() {
        // 子串匹配的已知副作用：含 "key" 字样的非密钥字段（如公钥、键盘）也会被遮罩
        assertTrue(isSensitiveSetting("sso_jwt_public_key"))
        assertTrue(isSensitiveSetting("monkey_mode"))
    }
}
