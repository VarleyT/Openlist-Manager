package com.bytegrep.oplist_manager.data.repository

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.security.SecretCipher
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 仓库层 + 网络层集成测试：真实的 Retrofit / OkHttp / kotlinx.serialization 链路，
 * 服务端由 MockWebServer 模拟 OpenList 的响应约定（HTTP 200 + 业务 code）。
 */
class RepositoryIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var profile: ServerProfile
    private lateinit var sessionManager: SessionManager

    private val serverStore = mockk<ServerStore>(relaxed = true)
    private val cipher = object : SecretCipher {
        override fun encrypt(plain: String) = "enc:$plain"
        override fun decrypt(encoded: String) = encoded.removePrefix("enc:")
    }

    // 与 NetworkModule 中的配置保持一致
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        profile = ServerProfile(
            id = "s1",
            name = "测试服务器",
            baseUrl = server.url("/").toString(),
            username = "admin",
            tokenEnc = "enc:old-token",
            passwordEnc = "enc:secret",
        )
        stubStoredProfile(profile)
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        sessionManager = SessionManager(serverStore, cipher, ApiClientFactory(client, json))
        sessionManager.activate(profile, "old-token", UserDto(id = 1, username = "admin", role = UserRole.ADMIN))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun stubStoredProfile(stored: ServerProfile) {
        coEvery { serverStore.current() } returns ServerState(listOf(stored), activeId = stored.id)
    }

    private fun enqueueJson(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "服务端未收到请求" }

    // ---------- 请求格式与解包 ----------

    @Test
    fun `file list sends raw token header and parses content`() = runTest {
        enqueueJson(
            """{"code":200,"message":"success","data":{"content":[
                {"name":"a.txt","size":12,"is_dir":false,"type":4},
                {"name":"docs","is_dir":true,"type":1}
            ],"total":2,"write":true}}""",
        )

        val data = FileRepository(sessionManager).list("/backup", refresh = true)

        assertEquals(listOf("a.txt", "docs"), data.content.orEmpty().map { it.name })
        assertTrue(data.content!![1].isDir)
        assertTrue(data.write)
        val request = takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/fs/list", request.path)
        // 认证头为原始 token，不带 Bearer 前缀
        assertEquals("old-token", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"path\":\"/backup\""))
        assertTrue(body, body.contains("\"refresh\":true"))
    }

    @Test
    fun `write operation accepts null data`() = runTest {
        enqueueJson("""{"code":200,"message":"success","data":null}""")

        FileRepository(sessionManager).mkdir("/backup", "新建文件夹")

        val request = takeRequest()
        assertEquals("/api/fs/mkdir", request.path)
        assertTrue(request.body.readUtf8().contains("\"path\":\"/backup/新建文件夹\""))
    }

    @Test
    fun `move sends source target and names`() = runTest {
        enqueueJson("""{"code":200,"message":"success"}""")

        FileRepository(sessionManager).move("/a", "/b", listOf("x.mp4", "y.mp4"))

        val body = takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"src_dir\":\"/a\""))
        assertTrue(body, body.contains("\"dst_dir\":\"/b\""))
        assertTrue(body, body.contains("\"names\":[\"x.mp4\",\"y.mp4\"]"))
    }

    @Test
    fun `read operation with null data fails with friendly server error`() = runTest {
        enqueueJson("""{"code":200,"message":"success","data":null}""")

        try {
            FileRepository(sessionManager).get("/a.txt")
            fail("应抛出 Server 异常")
        } catch (e: AppException.Server) {
            assertEquals("服务器返回了空数据", e.message)
        }
    }

    @Test
    fun `business error code maps to Server with server message`() = runTest {
        enqueueJson("""{"code":500,"message":"object not found","data":null}""")

        try {
            FileRepository(sessionManager).list("/missing")
            fail("应抛出 Server 异常")
        } catch (e: AppException.Server) {
            assertEquals(500, e.code)
            assertEquals("object not found", e.message)
        }
    }

    @Test(expected = AppException.Forbidden::class)
    fun `business code 403 maps to Forbidden`() = runTest {
        enqueueJson("""{"code":403,"message":"permission denied"}""")

        UserRepository(sessionManager).list()
    }

    @Test
    fun `http 404 maps to incompatible server message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            StorageRepository(sessionManager).list()
            fail("应抛出 Server 异常")
        } catch (e: AppException.Server) {
            assertEquals(404, e.code)
            assertTrue(e.message!!.contains("不是 OpenList"))
        }
    }

    @Test(expected = AppException.Unknown::class)
    fun `malformed response maps to Unknown`() = runTest {
        enqueueJson("<html>not openlist</html>")

        StorageRepository(sessionManager).list()
    }

    // ---------- 401 自动重登 ----------

    @Test
    fun `business 401 with saved password relogins once and retries with new token`() = runTest {
        enqueueJson("""{"code":401,"message":"token is expired"}""")
        enqueueJson("""{"code":200,"message":"success","data":{"token":"new-token"}}""")
        enqueueJson("""{"code":200,"message":"success","data":{"content":[],"total":0}}""")

        FileRepository(sessionManager).list("/")

        val first = takeRequest()
        val login = takeRequest()
        val retry = takeRequest()
        assertEquals("old-token", first.getHeader("Authorization"))
        assertEquals("/api/auth/login", login.path)
        assertTrue(login.body.readUtf8().contains("\"password\":\"secret\""))
        assertEquals("/api/fs/list", retry.path)
        assertEquals("new-token", retry.getHeader("Authorization"))
        assertEquals("new-token", sessionManager.session.value?.token)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `http 401 is treated the same as business 401`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))
        enqueueJson("""{"code":200,"message":"success","data":{"token":"new-token"}}""")
        enqueueJson("""{"code":200,"message":"success","data":[]}""")

        val items = SettingRepository(sessionManager).list(group = 1)

        assertTrue(items.isEmpty())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `401 without saved password emits authExpired and rethrows`() = runTest {
        stubStoredProfile(profile.copy(passwordEnc = null))
        enqueueJson("""{"code":401,"message":"token is expired"}""")

        sessionManager.authExpired.test {
            try {
                FileRepository(sessionManager).list("/")
                fail("应抛出 Unauthorized")
            } catch (e: AppException.Unauthorized) {
                assertTrue(e.message!!.contains("token is expired"))
            }
            assertTrue(awaitItem().contains("token is expired"))
        }
        // 没有保存密码时不应尝试登录
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `retry still unauthorized after relogin is not retried again`() = runTest {
        enqueueJson("""{"code":401,"message":"expired"}""")
        enqueueJson("""{"code":200,"message":"success","data":{"token":"new-token"}}""")
        enqueueJson("""{"code":401,"message":"still expired"}""")

        try {
            FileRepository(sessionManager).list("/")
            fail("应抛出 Unauthorized")
        } catch (_: AppException.Unauthorized) {
        }

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `failed relogin surfaces original unauthorized error`() = runTest {
        enqueueJson("""{"code":401,"message":"expired"}""")
        enqueueJson("""{"code":400,"message":"password is incorrect"}""")

        try {
            FileRepository(sessionManager).list("/")
            fail("应抛出 Unauthorized")
        } catch (e: AppException.Unauthorized) {
            assertTrue(e.message!!.contains("expired"))
        }
        assertEquals("old-token", sessionManager.session.value?.token)
    }

    // ---------- 取消 ----------

    @Test
    fun `cancelling request propagates CancellationException instead of AppException`() = runTest {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"code":200,"data":{"content":[]}}""")
                .setHeadersDelay(3, TimeUnit.SECONDS),
        )
        val repo = FileRepository(sessionManager)

        val deferred = async(Dispatchers.IO) { repo.list("/") }
        withContext(Dispatchers.IO) { takeRequest() }
        deferred.cancel()
        try {
            deferred.await()
            fail("应抛出 CancellationException")
        } catch (e: CancellationException) {
            assertFalse(e is AppException)
        }
        assertTrue(deferred.isCancelled)
    }

    // ---------- 各仓库的接口约定 ----------

    @Test
    fun `storage list maps raw json fields`() = runTest {
        enqueueJson(
            """{"code":200,"data":{"content":[
                {"id":3,"mount_path":"/aliyun","driver":"AliyundriveOpen","status":"work","disabled":true,"order":2,"remark":"主盘"}
            ],"total":1}}""",
        )

        val storages = StorageRepository(sessionManager).list()

        val s = storages.single()
        assertEquals(3, s.id)
        assertEquals("/aliyun", s.mountPath)
        assertEquals("AliyundriveOpen", s.driver)
        assertEquals("work", s.status)
        assertTrue(s.disabled)
        assertEquals(2, s.order)
        assertEquals("主盘", s.remark)
        assertEquals("/api/admin/storage/list?page=1&per_page=0", takeRequest().path)
    }

    @Test
    fun `storage enable and disable hit separate endpoints`() = runTest {
        enqueueJson("""{"code":200}""")
        enqueueJson("""{"code":200}""")
        val repo = StorageRepository(sessionManager)

        repo.setEnabled(3, enabled = false)
        repo.setEnabled(3, enabled = true)

        assertEquals("/api/admin/storage/disable?id=3", takeRequest().path)
        assertEquals("/api/admin/storage/enable?id=3", takeRequest().path)
    }

    @Test
    fun `driver names are sorted`() = runTest {
        enqueueJson("""{"code":200,"data":["S3","Local","AliyundriveOpen"]}""")

        assertEquals(listOf("AliyundriveOpen", "Local", "S3"), StorageRepository(sessionManager).driverNames())
    }

    @Test
    fun `setting list is sorted by index and tolerates null data`() = runTest {
        enqueueJson("""{"code":200,"data":[{"key":"b","index":2},{"key":"a","index":1}]}""")
        enqueueJson("""{"code":200,"data":null}""")
        val repo = SettingRepository(sessionManager)

        assertEquals(listOf("a", "b"), repo.list(group = 1).map { it.key })
        assertTrue(repo.list(group = 2).isEmpty())
        assertEquals("/api/admin/setting/list?group=1", takeRequest().path)
    }

    @Test
    fun `task repository uses admin scope for admin user`() = runTest {
        enqueueJson("""{"code":200,"data":[{"id":"t1","name":"上传 a.txt","state":1}]}""")

        val tasks = TaskRepository(sessionManager).undone(TaskType.UPLOAD)

        assertEquals("t1", tasks.single().id)
        assertEquals("/api/admin/task/upload/undone", takeRequest().path)
    }

    @Test
    fun `task repository uses user scope for general user`() = runTest {
        sessionManager.updateUser(UserDto(id = 2, username = "guest", role = UserRole.GENERAL))
        enqueueJson("""{"code":200,"data":null}""")
        enqueueJson("""{"code":200}""")
        val repo = TaskRepository(sessionManager)

        assertTrue(repo.done(TaskType.OFFLINE_DOWNLOAD).isEmpty())
        repo.action(TaskType.COPY, "retry", "t9")

        assertEquals("/api/task/offline_download/done", takeRequest().path)
        val action = takeRequest()
        assertEquals("POST", action.method)
        assertEquals("/api/task/copy/retry?tid=t9", action.path)
    }

    // ---------- 非 Retrofit 请求 ----------

    @Test
    fun `upload streams body with encoded file path and reports progress`() = runTest {
        enqueueJson("""{"code":200,"message":"success"}""")
        val content = "hello openlist".toByteArray()
        var lastProgress = 0L

        FileRepository(sessionManager).upload(
            serverId = "s1",
            dir = "/docs",
            name = "a b.txt",
            size = content.size.toLong(),
            mimeType = "text/plain",
            openStream = { content.inputStream() },
            onProgress = { lastProgress = it },
        )

        val request = takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/fs/put", request.path)
        assertEquals("%2Fdocs%2Fa%20b.txt", request.getHeader("File-Path"))
        assertEquals("false", request.getHeader("As-Task"))
        assertEquals("hello openlist", request.body.readUtf8())
        assertEquals(content.size.toLong(), lastProgress)
    }

    @Test
    fun `readText attaches token for same origin and truncates to max bytes`() = runTest {
        server.enqueue(MockResponse().setBody("0123456789"))

        val text = FileRepository(sessionManager).readText(server.url("/d/a.txt").toString(), maxBytes = 4)

        assertEquals("0123", text)
        assertEquals("old-token", takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `readText maps http failure to Server error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        try {
            FileRepository(sessionManager).readText(server.url("/d/a.txt").toString())
            fail("应抛出 Server 异常")
        } catch (e: AppException.Server) {
            assertEquals(500, e.code)
        }
    }
}
