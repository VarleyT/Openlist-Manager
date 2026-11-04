package com.bytegrep.oplist_manager.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parse strips v prefix and build metadata`() {
        val version = AppVersion.parse("v1.2.3-beta.1+abc")
        assertEquals(AppVersion(listOf(1, 2, 3), "beta.1"), version)
    }

    @Test
    fun `parse returns null for invalid input`() {
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse("1..2"))
    }

    @Test
    fun `numeric segments compare by value not text`() {
        assertTrue(isNewerVersion("1.10.0", "1.9.0"))
        assertFalse(isNewerVersion("1.9.0", "1.10.0"))
    }

    @Test
    fun `missing segments are treated as zero`() {
        assertFalse(isNewerVersion("1.2", "1.2.0"))
        assertTrue(isNewerVersion("1.2.1", "1.2"))
    }

    @Test
    fun `release is newer than prerelease of same number`() {
        assertTrue(isNewerVersion("1.0.0", "1.0.0-rc.1"))
        assertFalse(isNewerVersion("1.0.0-rc.1", "1.0.0"))
    }

    @Test
    fun `prerelease identifiers follow semver precedence`() {
        assertTrue(isNewerVersion("1.0.0-beta.11", "1.0.0-beta.2"))
        assertTrue(isNewerVersion("1.0.0-beta", "1.0.0-alpha.9"))
        assertTrue(isNewerVersion("1.0.0-alpha.1", "1.0.0-alpha"))
    }

    @Test
    fun `same version or unparseable is not an update`() {
        assertFalse(isNewerVersion("1.0.0", "v1.0.0"))
        assertFalse(isNewerVersion("garbage", "1.0.0"))
        assertFalse(isNewerVersion("2.0.0", "dev"))
    }
}
