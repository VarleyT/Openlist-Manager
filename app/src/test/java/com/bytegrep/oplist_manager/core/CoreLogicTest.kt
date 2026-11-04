package com.bytegrep.oplist_manager.core

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.common.formatBytes
import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.core.common.parentPath
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.ApiEnvelope
import com.bytegrep.oplist_manager.core.network.toAppException
import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

class CoreLogicTest {

    @Test
    fun `normalizeBaseUrl adds http for LAN address and trailing slash`() {
        assertEquals("http://192.168.1.200:5244/", ApiClientFactory.normalizeBaseUrl("192.168.1.200:5244"))
        assertEquals("http://localhost:5244/", ApiClientFactory.normalizeBaseUrl(" localhost:5244/ "))
    }

    @Test
    fun `normalizeBaseUrl defaults to https for public domain and keeps sub path`() {
        assertEquals("https://nas.example.com/", ApiClientFactory.normalizeBaseUrl("nas.example.com"))
        assertEquals("https://example.com/openlist/", ApiClientFactory.normalizeBaseUrl("https://example.com/openlist"))
        assertEquals("http://example.com/", ApiClientFactory.normalizeBaseUrl("HTTP://example.com"))
    }

    @Test
    fun `normalizeBaseUrl rejects scheme without host`() {
        assertNull(ApiClientFactory.normalizeBaseUrl("http://"))
        assertNull(ApiClientFactory.normalizeBaseUrl("https:///"))
    }

    @Test
    fun `normalizeBaseUrl rejects blank input`() {
        assertNull(ApiClientFactory.normalizeBaseUrl("   "))
    }

    @Test
    fun `unwrap returns data on code 200`() {
        assertEquals("ok", ApiEnvelope(code = 200, data = "ok").unwrap())
    }

    @Test(expected = AppException.Unauthorized::class)
    fun `unwrap maps code 401 to Unauthorized`() {
        ApiEnvelope<String>(code = 401, message = "token is invalidated").unwrap()
    }

    @Test
    fun `unwrap maps other codes to Server with message`() {
        val e = runCatching { ApiEnvelope<String>(code = 500, message = "storage not found").unwrap() }.exceptionOrNull()
        assertTrue(e is AppException.Server)
        assertEquals("storage not found", e?.message)
    }

    @Test
    fun `network exceptions map to friendly Network errors`() {
        assertTrue(UnknownHostException().toAppException() is AppException.Network)
        assertTrue(SocketTimeoutException().toAppException() is AppException.Network)
        assertTrue(ConnectException().toAppException() is AppException.Network)
        assertTrue(SSLHandshakeException("bad cert").toAppException().message!!.contains("TLS"))
    }

    @Test
    fun `parseVersion extracts semantic version`() {
        assertEquals("v4.1.10", AuthRepository.parseVersion("v4.1.10 (Commit: e3c664f) - Frontend: v4.1.10"))
        assertEquals("", AuthRepository.parseVersion(""))
    }

    @Test
    fun `formatBytes uses binary units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("2.4 MB", formatBytes((2.4 * 1024 * 1024).toLong()))
    }

    @Test
    fun `path helpers join and split correctly`() {
        assertEquals("/a", joinPath("/", "a"))
        assertEquals("/a/b", joinPath("/a/", "b"))
        assertEquals("/", parentPath("/a"))
        assertEquals("/a", parentPath("/a/b/"))
        assertEquals("/", parentPath("/"))
    }

    @Test
    fun `hasPermission checks bit or admin role`() {
        val user = UserDto(role = UserRole.GENERAL, permission = 0b1000)
        assertTrue(user.hasPermission(3))
        assertFalse(user.hasPermission(4))
        assertTrue(UserDto(role = UserRole.ADMIN, permission = 0).hasPermission(7))
    }
}
