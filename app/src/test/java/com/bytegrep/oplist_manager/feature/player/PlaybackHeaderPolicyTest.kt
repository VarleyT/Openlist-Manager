package com.bytegrep.oplist_manager.feature.player

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaybackHeaderPolicyTest {

    private lateinit var storage: MockWebServer
    private lateinit var other: MockWebServer
    private val policy = PlaybackHeaderPolicy()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        storage = MockWebServer().apply { start() }
        other = MockWebServer().apply { start() }
        client = OkHttpClient().forPlayback(policy)
    }

    @After
    fun tearDown() {
        storage.shutdown()
        other.shutdown()
    }

    private fun execute(url: String, headers: Map<String, String>) {
        val builder = Request.Builder().url(url)
        headers.forEach { (name, value) -> builder.header(name, value) }
        client.newCall(builder.build()).execute().close()
    }

    @Test
    fun `driver headers only apply to registered origin`() {
        policy.register(storage.url("/v.mkv").toString(), mapOf("Referer" to "https://www.alipan.com/"))

        assertEquals(mapOf("Referer" to "https://www.alipan.com/"), policy.headersFor(storage.url("/other").toString()))
        assertEquals(emptyMap<String, String>(), policy.headersFor(other.url("/v.mkv").toString()))
    }

    @Test
    fun `authorization is always stripped when not registered`() {
        storage.enqueue(MockResponse().setBody("ok"))

        execute(storage.url("/v.mkv").toString(), mapOf("Authorization" to "openlist-token", "Range" to "bytes=0-"))

        val recorded = storage.takeRequest()
        assertNull(recorded.getHeader("Authorization"))
        assertEquals("bytes=0-", recorded.getHeader("Range"))
    }

    @Test
    fun `cross origin redirect drops driver headers but keeps range`() {
        storage.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/real.mkv").toString()))
        other.enqueue(MockResponse().setResponseCode(206).setBody("x"))
        val url = storage.url("/d/v.mkv").toString()
        val driverHeaders = mapOf("Cookie" to "secret=1", "Referer" to "https://a/")
        policy.register(url, driverHeaders)

        execute(url, driverHeaders + ("Range" to "bytes=100-"))

        assertEquals("secret=1", storage.takeRequest().getHeader("Cookie"))
        val redirected = other.takeRequest()
        assertNull(redirected.getHeader("Cookie"))
        assertNull(redirected.getHeader("Referer"))
        assertNull(redirected.getHeader("Authorization"))
        assertEquals("bytes=100-", redirected.getHeader("Range"))
    }

    @Test
    fun `registered driver authorization is kept for its own origin`() {
        storage.enqueue(MockResponse().setBody("ok"))
        val url = storage.url("/dav/v.mkv").toString()
        policy.register(url, mapOf("Authorization" to "Basic dav"))

        execute(url, mapOf("Authorization" to "Basic dav"))

        assertEquals("Basic dav", storage.takeRequest().getHeader("Authorization"))
    }
}
