package com.bytegrep.oplist_manager.data.update

import com.bytegrep.oplist_manager.core.common.AppException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class UpdateRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: UpdateRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        // 将固定的 GitHub API 地址重定向到本地 MockWebServer
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val url = server.url(chain.request().url.encodedPath)
                chain.proceed(chain.request().newBuilder().url(url).build())
            }
            .build()
        repository = UpdateRepository(client, Json { ignoreUnknownKeys = true; explicitNulls = false })
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns available when remote tag is newer`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.2.0","name":"1.2.0 发布","body":"- 新功能\n","html_url":"https://github.com/VarleyT/Openlist-Manager/releases/tag/v1.2.0","assets":[]}""",
            ),
        )

        val result = repository.check("1.1.0")

        val release = (result as UpdateCheckResult.Available).release
        assertEquals("1.2.0", release.version)
        assertEquals("1.2.0 发布", release.title)
        assertEquals("- 新功能", release.notes)
        assertEquals("https://github.com/VarleyT/Openlist-Manager/releases/tag/v1.2.0", release.pageUrl)
        assertEquals("/repos/VarleyT/Openlist-Manager/releases/latest", server.takeRequest().path)
    }

    @Test
    fun `returns up to date when remote tag is same`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.1.0"}"""))

        assertEquals(UpdateCheckResult.UpToDate, repository.check("1.1.0"))
    }

    @Test
    fun `non github page url falls back to releases page`() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v9.0.0","html_url":"https://evil.example/x"}"""))

        val release = (repository.check("1.0.0") as UpdateCheckResult.Available).release

        assertEquals(AppLinks.RELEASES_URL, release.pageUrl)
    }

    @Test
    fun `404 maps to no release message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertServerError(404, "暂无已发布的版本")
    }

    @Test
    fun `403 maps to rate limit message`() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))

        assertServerError(403, "GitHub 请求过于频繁，请稍后再试")
    }

    private suspend fun assertServerError(code: Int, message: String) {
        try {
            repository.check("1.0.0")
            fail("应抛出异常")
        } catch (e: AppException.Server) {
            assertEquals(code, e.code)
            assertTrue(e.message == message)
        }
    }
}
