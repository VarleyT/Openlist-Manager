package com.bytegrep.oplist_manager.feature.dashboard

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.data.model.Storage
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val session = mockk<SessionManager>()
    private val storageRepository = mockk<StorageRepository>()
    private val taskRepository = mockk<TaskRepository>()

    private val server = ServerProfile(id = "a", name = "A", baseUrl = "http://10.0.0.1:5244/", username = "admin")
    private val sessionFlow = MutableStateFlow<Session?>(Session(server, "token"))
    private val admin = UserDto(id = 1, username = "admin", role = UserRole.ADMIN)
    private val general = UserDto(id = 2, username = "guest", role = UserRole.GENERAL)

    private fun storage(id: Int, status: String = Storage.STATUS_WORK, disabled: Boolean = false) = Storage(
        id = id, mountPath = "/s$id", driver = "Local", status = status, disabled = disabled,
        remark = "", order = 0, modified = "", raw = JsonObject(emptyMap()),
    )

    private fun task(id: String, state: Int) = TaskInfo(id = id, state = state)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { session.session } returns sessionFlow
        coEvery { session.refreshUser() } returns admin
        coEvery { storageRepository.list() } returns emptyList()
        coEvery { taskRepository.undone(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createLoaded(): DashboardViewModel {
        val vm = DashboardViewModel(session, storageRepository, taskRepository)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `initial state shows checking status and current server before loading`() = runTest(dispatcher) {
        // Act
        val vm = DashboardViewModel(session, storageRepository, taskRepository)

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.CHECKING, state.status)
        assertEquals(server, state.server)
        assertTrue(state.isRefreshing)
    }

    @Test
    fun `online admin loads storages and task summary`() = runTest(dispatcher) {
        // Arrange
        coEvery { storageRepository.list() } returns listOf(
            storage(1),
            storage(2),
            storage(3, status = "init failed"),
            storage(4, status = "disabled", disabled = true),
        )
        coEvery { taskRepository.undone(TaskType.UPLOAD) } returns listOf(
            task("u1", TaskState.RUNNING),
            task("u2", TaskState.PENDING),
            task("u3", TaskState.FAILED),
        )
        coEvery { taskRepository.undone(TaskType.COPY) } returns listOf(
            task("c1", TaskState.ERRORED),
            task("c2", TaskState.SUCCEEDED),
        )

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.ONLINE, state.status)
        assertNull(state.statusError)
        assertTrue(state.isAdmin)
        assertEquals(admin, state.user)
        assertEquals(4, state.storages.size)
        assertEquals(2, state.workingCount)
        assertEquals(listOf(3), state.errorStorages.map { it.id })
        assertNull(state.storageError)
        assertEquals(2, state.runningTasks)
        assertEquals(2, state.failedTasks)
        assertFalse(state.isRefreshing)
        assertTrue(state.lastSyncedAt > 0)
        coVerify(exactly = TaskType.entries.size) { taskRepository.undone(any()) }
    }

    @Test
    fun `server unreachable marks offline and skips data loading`() = runTest(dispatcher) {
        // Arrange
        coEvery { session.refreshUser() } throws AppException.Network("无法连接服务器")

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.OFFLINE, state.status)
        assertEquals("无法连接服务器", state.statusError)
        assertFalse(state.isRefreshing)
        coVerify(exactly = 0) { storageRepository.list() }
        coVerify(exactly = 0) { taskRepository.undone(any()) }
    }

    @Test
    fun `non admin user loads tasks but not storages`() = runTest(dispatcher) {
        // Arrange
        coEvery { session.refreshUser() } returns general
        coEvery { taskRepository.undone(TaskType.UPLOAD) } returns listOf(task("u1", TaskState.RUNNING))

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.ONLINE, state.status)
        assertFalse(state.isAdmin)
        assertTrue(state.storages.isEmpty())
        assertEquals(1, state.runningTasks)
        coVerify(exactly = 0) { storageRepository.list() }
    }

    @Test
    fun `storage failure keeps server online and still counts tasks`() = runTest(dispatcher) {
        // Arrange
        coEvery { storageRepository.list() } throws AppException.Forbidden()
        coEvery { taskRepository.undone(TaskType.MOVE) } returns listOf(task("m1", TaskState.RUNNING))

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.ONLINE, state.status)
        assertEquals("当前账号无权限执行此操作", state.storageError)
        assertEquals(1, state.runningTasks)
    }

    @Test
    fun `failed task type requests are ignored and others still counted`() = runTest(dispatcher) {
        // Arrange
        coEvery { taskRepository.undone(TaskType.UPLOAD) } throws AppException.Network("超时")
        coEvery { taskRepository.undone(TaskType.OFFLINE_DOWNLOAD) } returns listOf(
            task("o1", TaskState.RUNNING),
            task("o2", TaskState.FAILED),
        )

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(1, state.runningTasks)
        assertEquals(1, state.failedTasks)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `refresh recovers from offline to online and clears errors`() = runTest(dispatcher) {
        // Arrange
        coEvery { session.refreshUser() } throws AppException.Network("无法连接服务器") andThen admin
        coEvery { storageRepository.list() } returns listOf(storage(1))
        val vm = createLoaded()
        assertEquals(ServerStatus.OFFLINE, vm.state.value.status)

        // Act
        vm.refresh()
        advanceUntilIdle()

        // Assert
        val state = vm.state.value
        assertEquals(ServerStatus.ONLINE, state.status)
        assertNull(state.statusError)
        assertEquals(1, state.storages.size)
    }

    @Test
    fun `refresh after storage error clears storage error on success`() = runTest(dispatcher) {
        // Arrange
        coEvery { storageRepository.list() } throws AppException.Server(500, "服务器错误") andThen listOf(storage(1))
        val vm = createLoaded()
        assertEquals("服务器错误", vm.state.value.storageError)

        // Act
        vm.refresh()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.storageError)
        assertEquals(1, vm.state.value.workingCount)
    }

    @Test
    fun `refresh picks up server switched in session`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()
        val other = server.copy(id = "b", name = "B")

        // Act
        sessionFlow.value = Session(other, "t2")
        vm.refresh()
        advanceUntilIdle()

        // Assert
        assertEquals(other, vm.state.value.server)
    }
}
