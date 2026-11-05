package com.bytegrep.oplist_manager.data.repository

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.ApiEnvelope
import com.bytegrep.oplist_manager.core.network.OpenListApi
import com.bytegrep.oplist_manager.core.security.SecretCipher
import com.bytegrep.oplist_manager.data.model.LoginRequest
import com.bytegrep.oplist_manager.data.model.TokenData
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.feature.files.UploadScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.net.ConnectException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AuthRepositoryTest {

    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:")
    }
    private val serverStore = mockk<ServerStore>(relaxed = true)
    private val factory = mockk<ApiClientFactory>()
    private val anonApi = mockk<OpenListApi>()
    private val authedApi = mockk<OpenListApi>()
    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val sessionManager = mockk<SessionManager>(relaxed = true) {
        every { session } returns sessionFlow
    }
    private val uploadScheduler = mockk<UploadScheduler>(relaxed = true)

    private val user = UserDto(id = 1, username = "admin", role = UserRole.ADMIN)

    private val repository = AuthRepository(factory, serverStore, cipher, sessionManager, uploadScheduler)

    private fun settings(title: String = "我的网盘", version: String = "v4.1.10 (Commit: e3c664f) - Frontend: v4.1.10"): JsonObject =
        buildJsonObject {
            put("site_title", title)
            put("version", version)
        }

    private fun stubServer(
        stored: List<ServerProfile> = emptyList(),
        siteSettings: JsonObject = settings(),
        token: String = "new-token",
    ) {
        every { factory.create(BASE, null) } returns anonApi
        every { factory.create(BASE, token) } returns authedApi
        coEvery { anonApi.publicSettings() } returns ApiEnvelope(code = 200, data = siteSettings)
        coEvery { anonApi.login(any()) } returns ApiEnvelope(code = 200, data = TokenData(token))
        coEvery { authedApi.me() } returns ApiEnvelope(code = 200, data = user)
        coEvery { serverStore.current() } returns ServerState(stored)
    }

    private suspend fun login(
        username: String = "admin",
        password: String = "pw",
        otp: String = "",
        remember: Boolean = true,
        existingId: String? = null,
    ): ServerProfile = repository.login(RAW_URL, username, password, otp, remember, existingId)

    // ---------- probe ----------

    @Test
    fun `probe normalizes url and parses title and short version`() = runTest {
        stubServer()

        val probe = repository.probe(RAW_URL)

        assertEquals(BASE, probe.baseUrl)
        assertEquals("我的网盘", probe.siteTitle)
        assertEquals("v4.1.10", probe.version)
    }

    @Test
    fun `probe falls back to host when site title is blank or missing`() = runTest {
        stubServer(siteSettings = buildJsonObject { put("site_title", "  ") })

        val probe = repository.probe(RAW_URL)

        assertEquals("192.168.1.2", probe.siteTitle)
        assertEquals("", probe.version)
    }

    @Test
    fun `probe rejects invalid url without network call`() = runTest {
        try {
            repository.probe("   ")
            fail("应抛出 AppException.Unknown")
        } catch (e: AppException.Unknown) {
            assertEquals("服务器地址格式无效", e.message)
        }
        verify(exactly = 0) { factory.create(any(), any()) }
    }

    @Test
    fun `probe maps connection failure to network exception`() = runTest {
        every { factory.create(BASE, null) } returns anonApi
        coEvery { anonApi.publicSettings() } throws ConnectException("connection refused")

        try {
            repository.probe(RAW_URL)
            fail("应抛出 AppException")
        } catch (e: AppException) {
            assertTrue(e.javaClass.simpleName, e is AppException.Network)
        }
    }

    @Test
    fun `parseVersion keeps only leading version token`() {
        assertEquals("v4.1.10", AuthRepository.parseVersion("v4.1.10 (Commit: e3c664f) - Frontend: x"))
        assertEquals("v4.0.0", AuthRepository.parseVersion("v4.0.0"))
        assertEquals("", AuthRepository.parseVersion(""))
    }

    // ---------- login ----------

    @Test
    fun `login trims username and otp but not password and saves new active profile`() = runTest {
        stubServer()
        val request = slot<LoginRequest>()
        coEvery { anonApi.login(capture(request)) } returns ApiEnvelope(code = 200, data = TokenData("new-token"))

        val profile = login(username = "  admin ", password = " pw ", otp = " 123456 ")

        assertEquals(LoginRequest("admin", " pw ", "123456"), request.captured)
        assertTrue(profile.id.isNotBlank())
        assertEquals("我的网盘", profile.name)
        assertEquals(BASE, profile.baseUrl)
        assertEquals("admin", profile.username)
        assertEquals("enc:new-token", profile.tokenEnc)
        assertEquals("enc: pw ", profile.passwordEnc)
        assertEquals("v4.1.10", profile.version)
        assertTrue(profile.lastConnectedAt > 0)
        coVerifyOrder {
            serverStore.upsert(profile, makeActive = true)
            sessionManager.activate(profile, "new-token", user)
        }
    }

    @Test
    fun `login without remember password does not store password`() = runTest {
        stubServer()

        val profile = login(remember = false)

        assertNull(profile.passwordEnc)
        assertEquals("enc:new-token", profile.tokenEnc)
    }

    @Test
    fun `login reuses id and name of saved server with same url and username`() = runTest {
        val saved = ServerProfile(id = "s1", name = "家里 NAS", baseUrl = BASE, username = "admin")
        val other = ServerProfile(id = "s2", name = "其他", baseUrl = BASE, username = "guest")
        stubServer(stored = listOf(other, saved))

        val profile = login(username = " admin ")

        assertEquals("s1", profile.id)
        assertEquals("家里 NAS", profile.name)
    }

    @Test
    fun `login reuses explicitly edited server id even if username changed`() = runTest {
        val saved = ServerProfile(id = "s1", name = "家里 NAS", baseUrl = BASE, username = "old")
        stubServer(stored = listOf(saved))

        val profile = login(username = "admin", existingId = "s1")

        assertEquals("s1", profile.id)
        assertEquals("家里 NAS", profile.name)
        assertEquals("admin", profile.username)
    }

    @Test
    fun `login with different username creates new server`() = runTest {
        val saved = ServerProfile(id = "s1", name = "家里 NAS", baseUrl = BASE, username = "guest")
        stubServer(stored = listOf(saved))

        val profile = login(username = "admin")

        assertNotEquals("s1", profile.id)
        assertEquals("我的网盘", profile.name)
    }

    @Test
    fun `login failure from server is surfaced and nothing is saved`() = runTest {
        stubServer()
        coEvery { anonApi.login(any()) } returns ApiEnvelope(code = 400, message = "password is incorrect")

        try {
            login()
            fail("应抛出 AppException.Server")
        } catch (e: AppException.Server) {
            assertEquals("password is incorrect", e.message)
        }
        coVerify(exactly = 0) { serverStore.upsert(any(), any()) }
        verify(exactly = 0) { sessionManager.activate(any(), any(), any()) }
    }

    // ---------- switchTo ----------

    @Test
    fun `switchTo activates server and returns restore result`() = runTest {
        coEvery { sessionManager.restore() } returns false

        val ok = repository.switchTo("s2")

        assertFalse(ok)
        coVerifyOrder {
            serverStore.setActive("s2")
            sessionManager.restore()
        }
    }

    // ---------- logout ----------

    private val active = ServerProfile(
        id = "s1", name = "NAS", baseUrl = BASE, username = "admin", tokenEnc = "enc:tok", passwordEnc = "enc:pw",
    )

    private fun activeSession() {
        sessionFlow.value = Session(active, "tok")
        every { factory.create(BASE, "tok") } returns authedApi
    }

    private fun capturedUpdate(): ServerProfile {
        val transform = slot<(ServerProfile) -> ServerProfile>()
        coVerify { serverStore.update("s1", capture(transform)) }
        return transform.captured(active)
    }

    @Test
    fun `logout without session does nothing`() = runTest {
        repository.logout()

        verify(exactly = 0) { uploadScheduler.cancelFor(any()) }
        coVerify(exactly = 0) { serverStore.setActive(any()) }
        verify(exactly = 0) { sessionManager.clear() }
    }

    @Test
    fun `logout cancels uploads notifies server and clears credentials`() = runTest {
        activeSession()
        coEvery { authedApi.logout() } returns ApiEnvelope(code = 200)

        repository.logout()

        verify { uploadScheduler.cancelFor("s1") }
        coVerify { authedApi.logout() }
        val cleared = capturedUpdate()
        assertNull(cleared.tokenEnc)
        assertNull(cleared.passwordEnc)
        assertEquals("NAS", cleared.name)
        coVerify { serverStore.setActive(null) }
        verify { sessionManager.clear() }
    }

    @Test
    fun `logout still clears local state when server logout fails`() = runTest {
        activeSession()
        coEvery { authedApi.logout() } throws IOException("offline")

        repository.logout()

        val cleared = capturedUpdate()
        assertNull(cleared.tokenEnc)
        assertNull(cleared.passwordEnc)
        coVerify { serverStore.setActive(null) }
        verify { sessionManager.clear() }
    }

    // ---------- rename ----------

    @Test
    fun `rename trims name and refreshes active session`() = runTest {
        activeSession()

        repository.rename("s1", "  新名字 ")

        assertEquals("新名字", capturedUpdate().name)
        coVerify { sessionManager.restore() }
    }

    @Test
    fun `rename with blank name keeps old name and skips restore for inactive server`() = runTest {
        repository.rename("s1", "   ")

        assertEquals("NAS", capturedUpdate().name)
        coVerify(exactly = 0) { sessionManager.restore() }
    }

    // ---------- remove ----------

    @Test
    fun `removing active server clears session and returns true`() = runTest {
        activeSession()

        val wasActive = repository.remove("s1")

        assertTrue(wasActive)
        verify { uploadScheduler.cancelFor("s1") }
        coVerify { serverStore.remove("s1") }
        verify { sessionManager.clear() }
    }

    @Test
    fun `removing inactive server keeps session and returns false`() = runTest {
        activeSession()

        val wasActive = repository.remove("s2")

        assertFalse(wasActive)
        verify { uploadScheduler.cancelFor("s2") }
        coVerify { serverStore.remove("s2") }
        verify(exactly = 0) { sessionManager.clear() }
    }

    private companion object {
        const val RAW_URL = "192.168.1.2:5244/"
        const val BASE = "http://192.168.1.2:5244/"
    }
}
