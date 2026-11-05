package com.bytegrep.oplist_manager.core.datastore

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 只覆盖纯逻辑部分。ServerStore 的读写依赖 `preferencesDataStore` 委托（进程内单例、绑定 Context），
 * 且 Android 版 DataStore 用 File.renameTo 覆盖旧文件，在 Windows JVM 上第二次写入必然失败，
 * 因此读写流程无法在本地 JVM 单测中稳定验证。
 */
class ServerStoreTest {

    /** 与 ServerStore 内部的 Json 配置一致 */
    private val json = Json { ignoreUnknownKeys = true }

    private val a = ServerProfile(id = "a", name = "A", baseUrl = "http://10.0.0.1:5244/", username = "u", tokenEnc = "enc-a")
    private val b = ServerProfile(id = "b", name = "B", baseUrl = "https://b.example.com/", username = "v")

    @Test
    fun `ServerState active returns profile matching activeId`() {
        // Act
        val state = ServerState(servers = listOf(a, b), activeId = "b")

        // Assert
        assertEquals(b, state.active)
    }

    @Test
    fun `ServerState active is null when activeId is null or refers to removed server`() {
        // Assert
        assertNull(ServerState(servers = listOf(a), activeId = null).active)
        assertNull(ServerState(servers = listOf(a), activeId = "gone").active)
        assertNull(ServerState().active)
    }

    @Test
    fun `ServerProfile list round trips through stored json format`() {
        // Arrange
        val list = listOf(a.copy(passwordEnc = "enc-pw", version = "v4.1.0", lastConnectedAt = 99L), b)

        // Act
        val decoded = json.decodeFromString<List<ServerProfile>>(json.encodeToString(list))

        // Assert
        assertEquals(list, decoded)
    }

    @Test
    fun `ServerProfile saved by older version decodes with defaults and ignores unknown keys`() {
        // Arrange
        val raw = """[{"id":"a","name":"A","baseUrl":"http://h/","username":"u","legacyField":true}]"""

        // Act
        val profile = json.decodeFromString<List<ServerProfile>>(raw).single()

        // Assert
        assertNull(profile.tokenEnc)
        assertNull(profile.passwordEnc)
        assertEquals("", profile.version)
        assertEquals(0L, profile.lastConnectedAt)
    }
}
