package com.bytegrep.oplist_manager.feature.storage

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.Storage
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
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
class StorageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<StorageRepository>(relaxed = true)
    private val session = mockk<SessionManager>()
    private val translationsLoader = mockk<DriverTranslationsLoader>()
    private val currentUser = MutableStateFlow<UserDto?>(UserDto(role = UserRole.ADMIN))

    private fun storage(
        id: Int,
        mountPath: String = "/s$id",
        status: String = Storage.STATUS_WORK,
        disabled: Boolean = false,
        order: Int = 0,
    ) = Storage(
        id = id, mountPath = mountPath, driver = "Local", status = status, disabled = disabled,
        remark = "", order = order, modified = "", raw = JsonObject(emptyMap()),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { session.currentUser } returns currentUser
        coEvery { translationsLoader.load() } returns DriverTranslations.EMPTY
        coEvery { repository.list() } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createLoaded(): StorageViewModel {
        val vm = StorageViewModel(repository, session, translationsLoader)
        advanceUntilIdle()
        return vm
    }

    // ---------- 列表加载 ----------

    @Test
    fun `initial load sorts storages by order then id and clears loading`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } returns listOf(storage(3, order = 1), storage(2, order = 0), storage(1, order = 1))

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals(listOf(2, 1, 3), state.storages.map { it.id })
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
        assertTrue(state.isAdmin)
    }

    @Test
    fun `initial load failure exposes user message and stops loading`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } throws AppException.Network("网络连接失败")

        // Act
        val vm = createLoaded()

        // Assert
        val state = vm.state.value
        assertEquals("网络连接失败", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.storages.isEmpty())
    }

    @Test
    fun `retry after failure loads storages and clears error`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } throws AppException.Network("网络连接失败") andThen listOf(storage(1))
        val vm = createLoaded()

        // Act
        vm.retry()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.error)
        assertEquals(listOf(1), vm.state.value.storages.map { it.id })
    }

    @Test
    fun `refresh failure keeps previously loaded storages`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } returns listOf(storage(1)) andThenThrows AppException.Server(500, "服务器错误")
        val vm = createLoaded()

        // Act
        vm.refresh()
        advanceUntilIdle()

        // Assert
        assertEquals("服务器错误", vm.state.value.error)
        assertEquals(listOf(1), vm.state.value.storages.map { it.id })
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `non admin user does not trigger storage loading`() = runTest(dispatcher) {
        // Arrange
        currentUser.value = UserDto(role = UserRole.GENERAL)

        // Act
        val vm = createLoaded()

        // Assert
        assertFalse(vm.state.value.isAdmin)
        coVerify(exactly = 0) { repository.list() }
    }

    @Test
    fun `unknown user still attempts initial load only once`() = runTest(dispatcher) {
        // Arrange
        currentUser.value = null
        val vm = createLoaded()

        // Act：用户信息随后就绪，不应重复首次加载
        currentUser.value = UserDto(role = UserRole.ADMIN)
        advanceUntilIdle()

        // Assert
        assertTrue(vm.state.value.isAdmin)
        coVerify(exactly = 1) { repository.list() }
    }

    @Test
    fun `onResume skips first call and refreshes on later calls`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()

        // Act
        vm.onResume()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.list() }
        vm.onResume()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 2) { repository.list() }
    }

    // ---------- 统计 ----------

    @Test
    fun `state counts total working error and disabled storages`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } returns listOf(
            storage(1),
            storage(2),
            storage(3, status = "init failed"),
            storage(4, status = "disabled", disabled = true),
        )

        // Act
        val state = createLoaded().state.value

        // Assert
        assertEquals(4, state.total)
        assertEquals(2, state.workingCount)
        assertEquals(1, state.errorCount)
        assertEquals(1, state.disabledCount)
    }

    // ---------- 启用 / 禁用 ----------

    @Test
    fun `toggleEnabled on disabled storage enables it and refreshes list`() = runTest(dispatcher) {
        // Arrange
        val target = storage(7, mountPath = "/a", disabled = true)
        val vm = createLoaded()
        vm.openSheet(target)

        vm.messages.test {
            // Act
            vm.toggleEnabled(target)
            advanceUntilIdle()

            // Assert
            assertEquals("已启用 /a", awaitItem())
        }
        coVerify { repository.setEnabled(7, true) }
        coVerify(exactly = 2) { repository.list() }
        assertNull(vm.state.value.sheetTarget)
        assertTrue(vm.state.value.busyIds.isEmpty())
    }

    @Test
    fun `toggleEnabled on enabled storage disables it`() = runTest(dispatcher) {
        // Arrange
        val target = storage(7, mountPath = "/a", disabled = false)
        val vm = createLoaded()

        vm.messages.test {
            // Act
            vm.toggleEnabled(target)
            advanceUntilIdle()

            // Assert
            assertEquals("已禁用 /a", awaitItem())
        }
        coVerify { repository.setEnabled(7, false) }
    }

    @Test
    fun `failed action reports error message and releases busy flag`() = runTest(dispatcher) {
        // Arrange
        val target = storage(7, mountPath = "/a")
        coEvery { repository.setEnabled(7, any()) } throws AppException.Forbidden()
        val vm = createLoaded()

        vm.messages.test {
            // Act
            vm.toggleEnabled(target)
            advanceUntilIdle()

            // Assert
            assertEquals("操作失败：当前账号无权限执行此操作", awaitItem())
        }
        assertTrue(vm.state.value.busyIds.isEmpty())
        coVerify(exactly = 2) { repository.list() }
    }

    @Test
    fun `action on busy storage is ignored until previous one finishes`() = runTest(dispatcher) {
        // Arrange
        val target = storage(7)
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.setEnabled(7, any()) } coAnswers { gate.await() }
        val vm = createLoaded()

        // Act
        vm.toggleEnabled(target)
        advanceUntilIdle()
        val busyDuring = vm.state.value.busyIds
        vm.toggleEnabled(target)
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        // Assert
        assertEquals(setOf(7), busyDuring)
        coVerify(exactly = 1) { repository.setEnabled(7, any()) }
        assertTrue(vm.state.value.busyIds.isEmpty())
    }

    @Test
    fun `reloadStorage disables then enables the storage`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()

        vm.messages.test {
            // Act
            vm.reloadStorage(storage(5, mountPath = "/r"))
            advanceUntilIdle()

            // Assert
            assertEquals("已重新加载 /r", awaitItem())
        }
        coVerifyOrder {
            repository.setEnabled(5, false)
            repository.setEnabled(5, true)
        }
    }

    // ---------- 重新加载全部 ----------

    @Test
    fun `confirmReloadAll closes dialog triggers reload and refreshes list`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()
        vm.requestReloadAll()
        assertTrue(vm.state.value.confirmReloadAll)

        vm.messages.test {
            // Act
            vm.confirmReloadAll()
            advanceUntilIdle()

            // Assert
            assertEquals("已触发重新加载全部存储", awaitItem())
        }
        assertFalse(vm.state.value.confirmReloadAll)
        coVerify(exactly = 1) { repository.reloadAll() }
        coVerify(exactly = 2) { repository.list() }
    }

    // ---------- 删除 ----------

    @Test
    fun `confirmDelete deletes pending storage and refreshes list`() = runTest(dispatcher) {
        // Arrange
        val target = storage(9, mountPath = "/d")
        coEvery { repository.list() } returns listOf(storage(1), target) andThen listOf(storage(1))
        val vm = createLoaded()
        vm.openSheet(target)
        vm.requestDelete(target)
        assertNull(vm.state.value.sheetTarget)
        assertEquals(target, vm.state.value.pendingDelete)

        vm.messages.test {
            // Act
            vm.confirmDelete()
            advanceUntilIdle()

            // Assert
            assertEquals("已删除 /d", awaitItem())
        }
        coVerify(exactly = 1) { repository.delete(9) }
        assertNull(vm.state.value.pendingDelete)
        assertEquals(listOf(1), vm.state.value.storages.map { it.id })
    }

    @Test
    fun `confirmDelete without pending target does nothing`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()

        // Act
        vm.confirmDelete()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.delete(any()) }
        coVerify(exactly = 1) { repository.list() }
    }

    @Test
    fun `dismissDelete clears pending target without deleting`() = runTest(dispatcher) {
        // Arrange
        val vm = createLoaded()
        vm.requestDelete(storage(9))

        // Act
        vm.dismissDelete()
        vm.confirmDelete()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.pendingDelete)
        coVerify(exactly = 0) { repository.delete(any()) }
    }
}
