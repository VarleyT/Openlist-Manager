package com.bytegrep.oplist_manager.data.player

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.network.ApiEnvelope
import com.bytegrep.oplist_manager.core.network.OpenListApi
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsGetRequest
import com.bytegrep.oplist_manager.data.model.FsLinkData
import com.bytegrep.oplist_manager.data.model.FsLinkRequest
import com.bytegrep.oplist_manager.data.model.FsListData
import com.bytegrep.oplist_manager.data.model.FsListRequest
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class VideoPlaybackRepositoryTest {

    private val api = mockk<OpenListApi>()
    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val userFlow = MutableStateFlow<UserDto?>(null)
    private val session = mockk<SessionManager>()
    private val repository = VideoPlaybackRepository(session)

    private val profile = ServerProfile(id = "srv-1", name = "家", baseUrl = "http://192.168.1.2:5244", username = "admin")

    @Before
    fun setUp() {
        every { session.session } returns sessionFlow
        every { session.currentUser } returns userFlow
        every { session.requireSession() } answers {
            sessionFlow.value ?: throw AppException.Unauthorized("尚未登录服务器")
        }
        coEvery { session.request<Any?>(any()) } coAnswers {
            firstArg<suspend OpenListApi.() -> Any?>().invoke(api)
        }
    }

    @Test
    fun `get returns unwrapped file info for path`() = runTest {
        // Arrange
        coEvery { api.fsGet(FsGetRequest("/影视/a.mkv")) } returns
            ApiEnvelope(code = 200, data = FsGetData(name = "a.mkv", rawUrl = "https://cdn/a.mkv"))

        // Act
        val data = repository.get("/影视/a.mkv")

        // Assert
        assertEquals("https://cdn/a.mkv", data.rawUrl)
    }

    @Test
    fun `link posts full path and returns driver link`() = runTest {
        coEvery { api.fsLink(FsLinkRequest("/root/影视/a.mkv")) } returns
            ApiEnvelope(code = 200, data = FsLinkData(url = "https://drive/a", header = mapOf("Referer" to listOf("x"))))

        val data = repository.link("/root/影视/a.mkv")

        assertEquals("https://drive/a", data.url)
        assertEquals(listOf("x"), data.header?.get("Referer"))
    }

    @Test
    fun `get propagates server error from envelope`() = runTest {
        coEvery { api.fsGet(any()) } returns ApiEnvelope(code = 500, message = "object not found", data = null)

        try {
            repository.get("/missing.mp4")
            fail("应抛出 AppException.Server")
        } catch (e: AppException.Server) {
            assertEquals("object not found", e.message)
        }
    }

    @Test
    fun `sibling videos keep playable files only sorted naturally with full paths`() = runTest {
        // Arrange
        coEvery { api.fsList(FsListRequest(path = "/剧集")) } returns ApiEnvelope(
            code = 200,
            data = FsListData(
                content = listOf(
                    FsObject(name = "第10集.mp4"),
                    FsObject(name = "第2集.mkv"),
                    FsObject(name = "第1集.mp4"),
                    FsObject(name = "第1集.srt"),
                    FsObject(name = "花絮.mp4", isDir = true),
                ),
            ),
        )

        // Act
        val siblings = repository.siblingVideos("/剧集/第2集.mkv") { it.endsWith(".mp4") || it.endsWith(".mkv") }

        // Assert
        assertEquals(listOf("/剧集/第1集.mp4", "/剧集/第2集.mkv", "/剧集/第10集.mp4"), siblings)
    }

    @Test
    fun `sibling videos in root directory and empty listing`() = runTest {
        coEvery { api.fsList(FsListRequest(path = "/")) } returns ApiEnvelope(code = 200, data = FsListData(content = null))

        assertTrue(repository.siblingVideos("/a.mp4") { true }.isEmpty())

        coEvery { api.fsList(FsListRequest(path = "/")) } returns
            ApiEnvelope(code = 200, data = FsListData(content = listOf(FsObject(name = "a.mp4"))))
        assertEquals(listOf("/a.mp4"), repository.siblingVideos("/a.mp4") { true })
    }

    @Test
    fun `env uses cached user and active server`() = runTest {
        // Arrange
        sessionFlow.value = Session(profile, "tok")
        userFlow.value = UserDto(role = UserRole.ADMIN, basePath = "/root")

        // Act
        val env = repository.env()

        // Assert
        assertEquals(PlaybackEnv("http://192.168.1.2:5244", "srv-1", isAdmin = true, basePath = "/root"), env)
        coVerify(exactly = 0) { session.restore() }
        coVerify(exactly = 0) { session.refreshUser() }
    }

    @Test
    fun `env restores session after process death and refreshes user`() = runTest {
        // Arrange：进程回收后会话为空，restore 成功后才有会话
        coEvery { session.restore() } answers {
            sessionFlow.value = Session(profile, "tok")
            true
        }
        coEvery { session.refreshUser() } returns UserDto(role = UserRole.GENERAL, basePath = "/u")

        // Act
        val env = repository.env()

        // Assert
        assertFalse(env.isAdmin)
        assertEquals("/u", env.basePath)
        assertEquals("srv-1", env.serverId)
    }

    @Test
    fun `env throws unauthorized when session cannot be restored`() = runTest {
        coEvery { session.restore() } returns false

        try {
            repository.env()
            fail("应抛出 AppException.Unauthorized")
        } catch (e: AppException.Unauthorized) {
            assertEquals("尚未登录服务器", e.message)
        }
    }

    @Test
    fun `env treats user refresh failure as non admin with root base path`() = runTest {
        sessionFlow.value = Session(profile, "tok")
        coEvery { session.refreshUser() } throws AppException.Network("网络错误")

        val env = repository.env()

        assertFalse(env.isAdmin)
        assertEquals("/", env.basePath)
    }

    @Test
    fun `env rethrows cancellation from user refresh`() = runTest {
        sessionFlow.value = Session(profile, "tok")
        coEvery { session.refreshUser() } throws CancellationException("cancelled")

        try {
            repository.env()
            fail("CancellationException 必须继续上抛")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
