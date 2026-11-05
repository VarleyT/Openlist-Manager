package com.bytegrep.oplist_manager.data.model

import com.bytegrep.oplist_manager.di.NetworkModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminModelsTest {

    private val json: Json = NetworkModule.provideJson()

    private fun storage(status: String, disabled: Boolean) = Storage(
        id = 1, mountPath = "/m", driver = "Local", status = status, disabled = disabled,
        remark = "", order = 0, modified = "", raw = JsonObject(emptyMap()),
    )

    @Test
    fun `enabled storage with work status is working and not error`() {
        // Act
        val s = storage(Storage.STATUS_WORK, disabled = false)

        // Assert
        assertTrue(s.isWorking)
        assertFalse(s.isError)
    }

    @Test
    fun `enabled storage with non work status is error`() {
        // Act
        val s = storage("failed init: token expired", disabled = false)

        // Assert
        assertFalse(s.isWorking)
        assertTrue(s.isError)
    }

    @Test
    fun `disabled storage is neither working nor error regardless of status`() {
        // Act
        val working = storage(Storage.STATUS_WORK, disabled = true)
        val broken = storage("disabled", disabled = true)

        // Assert
        assertFalse(working.isWorking)
        assertFalse(working.isError)
        assertFalse(broken.isWorking)
        assertFalse(broken.isError)
    }

    @Test
    fun `SettingItem flags map to read only and deprecated`() {
        // Act
        val normal = SettingItem(key = "a", flag = 0)
        val readOnly = SettingItem(key = "b", flag = SettingItem.FLAG_READONLY)
        val deprecated = SettingItem(key = "c", flag = SettingItem.FLAG_DEPRECATED)

        // Assert
        assertFalse(normal.isReadOnly)
        assertFalse(normal.isDeprecated)
        assertTrue(readOnly.isReadOnly)
        assertFalse(readOnly.isDeprecated)
        assertTrue(deprecated.isDeprecated)
        assertFalse(deprecated.isReadOnly)
    }

    @Test
    fun `TaskState label covers every known state and falls back to unknown`() {
        // Arrange
        val expected = mapOf(
            TaskState.PENDING to "等待中",
            TaskState.RUNNING to "运行中",
            TaskState.SUCCEEDED to "已完成",
            TaskState.CANCELING to "取消中",
            TaskState.CANCELED to "已取消",
            TaskState.ERRORED to "出错",
            TaskState.FAILING to "失败中",
            TaskState.FAILED to "失败",
            TaskState.WAITING_RETRY to "等待重试",
            TaskState.BEFORE_RETRY to "准备重试",
        )

        // Act & Assert
        expected.forEach { (state, label) -> assertEquals("state=$state", label, TaskState.label(state)) }
        assertEquals("未知", TaskState.label(99))
        assertEquals("未知", TaskState.label(-1))
    }

    @Test
    fun `TaskInfo isRunning is true only for pending and running`() {
        // Act
        val running = (0..9).filter { TaskInfo(id = "t", state = it).isRunning }

        // Assert
        assertEquals(listOf(TaskState.PENDING, TaskState.RUNNING), running)
    }

    @Test
    fun `TaskInfo isFailed is true only for errored and failed`() {
        // Act
        val failed = (0..9).filter { TaskInfo(id = "t", state = it).isFailed }

        // Assert
        assertEquals(listOf(TaskState.ERRORED, TaskState.FAILED), failed)
    }

    @Test
    fun `TaskInfo stateLabel delegates to TaskState label`() {
        // Act
        val info = TaskInfo(id = "t", state = TaskState.WAITING_RETRY)

        // Assert
        assertEquals("等待重试", info.stateLabel)
    }

    @Test
    fun `TaskInfo decodes snake case fields and null times`() {
        // Arrange
        val raw = """
            {"id":"abc","name":"upload a","creator":"admin","creator_role":2,"state":1,
             "status":"uploading","progress":42.5,"start_time":"2025-01-01T00:00:00Z",
             "end_time":null,"total_bytes":2048,"error":""}
        """.trimIndent()

        // Act
        val info = json.decodeFromString<TaskInfo>(raw)

        // Assert
        assertEquals(2, info.creatorRole)
        assertEquals(42.5, info.progress, 0.0)
        assertEquals("2025-01-01T00:00:00Z", info.startTime)
        assertNull(info.endTime)
        assertEquals(2048L, info.totalBytes)
        assertTrue(info.isRunning)
    }

    @Test
    fun `DriverInfo decodes common and additional items with defaults`() {
        // Arrange
        val raw = """
            {"common":[{"name":"mount_path","type":"string","required":true}],
             "additional":[{"name":"root","default":"/"},{"name":"mode","type":"select","options":"a,b"}]}
        """.trimIndent()

        // Act
        val info = json.decodeFromString<DriverInfo>(raw)

        // Assert
        assertTrue(info.common.single().required)
        assertEquals("/", info.additional[0].default)
        assertEquals("string", info.additional[0].type)
        assertEquals("a,b", info.additional[1].options)
    }

    @Test
    fun `DriverInfo and CreateStorageResult tolerate empty objects`() {
        // Act
        val info = json.decodeFromString<DriverInfo>("{}")
        val created = json.decodeFromString<CreateStorageResult>("{}")

        // Assert
        assertTrue(info.common.isEmpty())
        assertTrue(info.additional.isEmpty())
        assertEquals(0, created.id)
    }

    @Test
    fun `SettingItem decodes with defaults for missing fields`() {
        // Act
        val item = json.decodeFromString<SettingItem>("""{"key":"site_title","value":"OpenList","group":1,"flag":2}""")

        // Assert
        assertEquals("string", item.type)
        assertEquals(SettingGroup.SITE.id, item.group)
        assertTrue(item.isReadOnly)
    }

    @Test
    fun `SettingGroup ids are unique and contiguous from 1`() {
        // Act
        val ids = SettingGroup.entries.map { it.id }

        // Assert
        assertEquals((1..SettingGroup.entries.size).toList(), ids)
    }

    @Test
    fun `TaskType paths match OpenList task api segments`() {
        // Act
        val paths = TaskType.entries.associate { it to it.path }

        // Assert
        assertEquals("offline_download_transfer", paths[TaskType.OFFLINE_TRANSFER])
        assertEquals("decompress_upload", paths[TaskType.DECOMPRESS_UPLOAD])
        assertEquals(TaskType.entries.size, paths.values.toSet().size)
    }
}
