package com.bytegrep.oplist_manager.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SameOriginAuthTest {

    private lateinit var openList: MockWebServer
    private lateinit var thirdParty: MockWebServer

    @Before
    fun setUp() {
        openList = MockWebServer().apply { start() }
        thirdParty = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        openList.shutdown()
        thirdParty.shutdown()
    }

    private fun client(): OkHttpClient {
        val base = openList.url("/").toString()
        return OkHttpClient.Builder()
            .addInterceptor(SameOriginAuthInterceptor { tokenForOrigin(base, TOKEN, it) })
            .build()
    }

    private fun get(client: OkHttpClient, url: String) =
        client.newCall(Request.Builder().url(url).build()).execute().close()

    @Test
    fun `same origin request carries token`() {
        openList.enqueue(MockResponse().setBody("ok"))

        get(client(), openList.url("/p/a.txt").toString())

        assertEquals(TOKEN, openList.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `third party direct link does not carry token`() {
        thirdParty.enqueue(MockResponse().setBody("ok"))

        get(client(), thirdParty.url("/a.txt?sign=x").toString())

        assertNull(thirdParty.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `cross origin redirect drops token`() {
        thirdParty.enqueue(MockResponse().setBody("ok"))
        openList.enqueue(MockResponse().setResponseCode(302).addHeader("Location", thirdParty.url("/real").toString()))

        get(client(), openList.url("/d/a.txt").toString())

        assertEquals(TOKEN, openList.takeRequest().getHeader("Authorization"))
        assertNull(thirdParty.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `tokenForOrigin requires matching scheme host and port`() {
        val base = "http://192.168.1.2:5244"
        assertEquals(TOKEN, tokenForOrigin(base, TOKEN, "http://192.168.1.2:5244/p/x".toHttpUrl()))
        assertNull(tokenForOrigin(base, TOKEN, "https://192.168.1.2:5244/p/x".toHttpUrl()))
        assertNull(tokenForOrigin(base, TOKEN, "http://192.168.1.2:8080/p/x".toHttpUrl()))
        assertNull(tokenForOrigin(base, TOKEN, "http://cdn.example.com:5244/p/x".toHttpUrl()))
    }

    @Test
    fun `tokenForOrigin normalizes base url without scheme`() {
        assertEquals(TOKEN, tokenForOrigin("192.168.1.2:5244", TOKEN, "http://192.168.1.2:5244/d/x".toHttpUrl()))
    }

    private companion object {
        const val TOKEN = "secret-token"
    }
}
