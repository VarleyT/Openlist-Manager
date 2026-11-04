package com.bytegrep.oplist_manager.data.player

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsLinkData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PlaybackSourceResolverTest {

    private val repository = mockk<VideoPlaybackRepository>()
    private val resolver = PlaybackSourceResolver(repository)
    private val user = PlaybackEnv(serverBaseUrl = "http://192.168.1.2:5244", isAdmin = false)
    private val admin = user.copy(isAdmin = true)

    private fun givenFile(name: String = "movie.mkv", rawUrl: String) {
        coEvery { repository.get(any()) } returns FsGetData(name = name, size = 100, rawUrl = rawUrl)
    }

    @Test
    fun `openlist proxy url is marked openlist without headers`() = runTest {
        givenFile(rawUrl = "http://192.168.1.2:5244/p/media/movie.mkv?sign=abc")

        val source = resolver.resolve("/media/movie.mkv", admin)

        assertEquals(PlaybackSourceKind.OPENLIST, source.kind)
        assertEquals("http://192.168.1.2:5244/p/media/movie.mkv?sign=abc", source.url)
        assertTrue(source.headers.isEmpty())
        coVerify(exactly = 0) { repository.link(any()) }
    }

    @Test
    fun `third party url for non admin uses raw url without calling fs link`() = runTest {
        givenFile(rawUrl = "https://cdn.example.com/x.mkv?t=1")

        val source = resolver.resolve("/media/movie.mkv", user)

        assertEquals(PlaybackSourceKind.DIRECT, source.kind)
        assertEquals("https://cdn.example.com/x.mkv?t=1", source.url)
        coVerify(exactly = 0) { repository.link(any()) }
    }

    @Test
    fun `admin gets driver headers from fs link with base path`() = runTest {
        givenFile(rawUrl = "https://cdn.example.com/x.mkv")
        coEvery { repository.link("/root/media/movie.mkv") } returns FsLinkData(
            url = "https://cdn.example.com/y.mkv",
            header = mapOf(
                "Referer" to listOf("https://www.alipan.com/"),
                "Range" to listOf("bytes=0-"),
                "Host" to listOf("evil"),
            ),
        )

        val source = resolver.resolve("/media/movie.mkv", admin.copy(basePath = "/root/"))

        assertEquals(PlaybackSourceKind.DRIVER_LINK, source.kind)
        assertEquals("https://cdn.example.com/y.mkv", source.url)
        assertEquals(mapOf("Referer" to "https://www.alipan.com/"), source.headers)
    }

    @Test
    fun `admin falls back to raw url when fs link has no headers`() = runTest {
        givenFile(rawUrl = "https://cdn.example.com/x.mkv")
        coEvery { repository.link(any()) } returns FsLinkData(url = "https://cdn.example.com/y.mkv")

        val source = resolver.resolve("/a.mkv", admin)

        assertEquals(PlaybackSourceKind.DIRECT, source.kind)
        assertEquals("https://cdn.example.com/x.mkv", source.url)
    }

    @Test
    fun `admin falls back to raw url when fs link fails`() = runTest {
        givenFile(rawUrl = "https://cdn.example.com/x.mkv")
        coEvery { repository.link(any()) } throws AppException.Forbidden()

        val source = resolver.resolve("/a.mkv", admin)

        assertEquals("https://cdn.example.com/x.mkv", source.url)
    }

    @Test
    fun `hls and dash get explicit mime`() = runTest {
        givenFile(name = "live.m3u8", rawUrl = "https://cdn.example.com/live.m3u8")
        assertEquals(PlaybackSourceResolver.MIME_HLS, resolver.resolve("/live.m3u8", user).mimeType)

        givenFile(name = "a.MPD", rawUrl = "https://cdn.example.com/a.mpd")
        assertEquals(PlaybackSourceResolver.MIME_DASH, resolver.resolve("/a.MPD", user).mimeType)

        givenFile(name = "a.mp4", rawUrl = "https://cdn.example.com/a.mp4")
        assertNull(resolver.resolve("/a.mp4", user).mimeType)
    }

    @Test
    fun `non http raw url is rejected`() = runTest {
        for (raw in listOf("", "file:///sdcard/a.mp4", "content://x/a.mp4", "javascript:alert(1)")) {
            givenFile(rawUrl = raw)
            try {
                resolver.resolve("/a.mp4", user)
                fail("应拒绝 $raw")
            } catch (e: AppException) {
                assertEquals("未获取到可播放的地址", e.message)
            }
        }
    }

    @Test
    fun `full path joins base path`() {
        assertEquals("/a/b.mp4", PlaybackSourceResolver.fullPath("/", "/a/b.mp4"))
        assertEquals("/a/b.mp4", PlaybackSourceResolver.fullPath("", "/a/b.mp4"))
        assertEquals("/root/a/b.mp4", PlaybackSourceResolver.fullPath("/root", "/a/b.mp4"))
        assertEquals("/root/a/b.mp4", PlaybackSourceResolver.fullPath("/root/", "/a/b.mp4"))
    }

    @Test
    fun `sanitize joins multi values and drops blocked headers`() {
        val result = PlaybackSourceResolver.sanitizeHeaders(
            mapOf(
                "Cookie" to listOf("a=1", "b=2"),
                "Content-Length" to listOf("1"),
                "X-Empty" to emptyList(),
            ),
        )
        assertEquals(mapOf("Cookie" to "a=1, b=2"), result)
    }
}
