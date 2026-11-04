package com.bytegrep.oplist_manager.data.session

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.ApiEnvelope
import com.bytegrep.oplist_manager.core.network.OpenListApi
import com.bytegrep.oplist_manager.core.security.SecretCipher
import com.bytegrep.oplist_manager.data.model.TokenData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class SessionManagerTest {

    private val serverA = ServerProfile(id = "a", name = "A", baseUrl = "http://10.0.0.1:5244/", username = "u", passwordEnc = "enc:pw")
    private val serverB = ServerProfile(id = "b", name = "B", baseUrl = "http://10.0.0.2:5244/", username = "u")

    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:")
    }
    private val serverStore = mockk<ServerStore>(relaxed = true)
    private val factory = mockk<ApiClientFactory>()
    private val apiA = mockk<OpenListApi>()
    private val apiB = mockk<OpenListApi>()

    private fun manager(servers: List<ServerProfile> = listOf(serverA, serverB)): SessionManager {
        coEvery { serverStore.current() } returns ServerState(servers, activeId = "a")
        every { factory.create(serverA.baseUrl, any()) } returns apiA
        every { factory.create(serverB.baseUrl, any()) } returns apiB
        coEvery { apiA.login(any()) } returns ApiEnvelope(code = 200, data = TokenData("a-new"))
        return SessionManager(serverStore, cipher, factory)
    }

    @Test
    fun `relogin after switching server does not overwrite active session`() = runTest {
        val sm = manager()
        sm.activate(serverA, "a-old", null)
        var calls = 0

        val result = sm.request {
            calls++
            if (calls == 1) {
                // 请求进行中用户切到 B，随后 A 返回 401
                sm.activate(serverB, "b-token", null)
                throw AppException.Unauthorized("expired")
            }
            if (this === apiA) "retried-on-a" else "wrong-server"
        }

        assertEquals("retried-on-a", result)
        assertEquals("b", sm.session.value?.server?.id)
        assertEquals("b-token", sm.session.value?.token)
    }

    @Test
    fun `relogin on same server replaces session and persists new token`() = runTest {
        val sm = manager()
        sm.activate(serverA, "a-old", null)
        var calls = 0
        val transform = slot<(ServerProfile) -> ServerProfile>()
        coEvery { serverStore.update("a", capture(transform)) } returns Unit

        sm.request { if (calls++ == 0) throw AppException.Unauthorized("expired") else Unit }

        assertEquals("a-new", sm.session.value?.token)
        assertEquals("enc:a-new", transform.captured(serverA).tokenEnc)
    }

    @Test
    fun `relogin uses latest stored profile so logout clears auto relogin`() = runTest {
        // 内存会话仍带密码，但持久化配置已在退出登录时清除密码
        val sm = manager(servers = listOf(serverA.copy(passwordEnc = null), serverB))
        sm.activate(serverA, "a-old", null)

        try {
            sm.request<Unit> { throw AppException.Unauthorized("expired") }
            fail("应抛出 Unauthorized")
        } catch (_: AppException.Unauthorized) {
        }

        coVerify(exactly = 0) { apiA.login(any()) }
        coVerify(exactly = 0) { serverStore.update(any(), any()) }
    }

    @Test
    fun `requestFor stays on bound server after user switches active server`() = runTest {
        val sm = manager(servers = listOf(serverA.copy(tokenEnc = "enc:a-stored"), serverB))
        sm.activate(serverB, "b-token", null)

        val target = sm.requestFor("a") { this }

        assertSame(apiA, target)
        verify { factory.create(serverA.baseUrl, "a-stored") }
        assertEquals("b", sm.session.value?.server?.id)
    }

    @Test
    fun `requestFor restores credentials from store on cold start`() = runTest {
        // 模拟 WorkManager 冷启动拉起进程：内存中没有任何会话
        val sm = manager(servers = listOf(serverA.copy(tokenEnc = "enc:a-stored"), serverB))

        val target = sm.requestFor("a") { this }

        assertSame(apiA, target)
        verify { factory.create(serverA.baseUrl, "a-stored") }
        assertNull(sm.session.value)
    }

    @Test
    fun `requestFor prefers in-memory token of active server`() = runTest {
        val sm = manager(servers = listOf(serverA.copy(tokenEnc = "enc:a-stored"), serverB))
        sm.activate(serverA, "a-fresh", null)

        sm.requestFor("a") { this }

        verify { factory.create(serverA.baseUrl, "a-fresh") }
    }

    @Test(expected = AppException.Unauthorized::class)
    fun `requestFor fails when bound server was removed`() = runTest {
        val sm = manager(servers = listOf(serverB))

        sm.requestFor("a") { this }
    }

    @Test(expected = AppException.Unauthorized::class)
    fun `requestFor fails when bound server has no stored token`() = runTest {
        val sm = manager(servers = listOf(serverA.copy(tokenEnc = null), serverB))

        sm.requestFor("a") { this }
    }

    @Test
    fun `tokenFor only returns token for current server origin`() {
        val sm = manager()
        sm.activate(serverA, "a-token", null)

        assertEquals("a-token", sm.tokenFor("http://10.0.0.1:5244/p/x".toHttpUrl()))
        assertNull(sm.tokenFor("https://cdn.example.com/x".toHttpUrl()))
    }
}
