package com.bytegrep.oplist_manager.feature.users

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.UserRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<UserRepository>()
    private val currentUser = MutableStateFlow<UserDto?>(UserDto(id = 1, username = "admin", role = UserRole.ADMIN))
    private val session = mockk<SessionManager> { every { currentUser } returns this@UsersViewModelTest.currentUser }

    private val alice = UserDto(id = 2, username = "alice")
    private val bob = UserDto(id = 3, username = "bob")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** state 为 WhileSubscribed，需保持订阅才能读到最新值 */
    private fun TestScope.createViewModel(): UsersViewModel =
        UsersViewModel(repository, session).also { vm -> backgroundScope.launch { vm.state.collect {} } }

    @Test
    fun `refresh loads users sorted by id`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.list() } returns listOf(bob, alice)
        val vm = createViewModel()

        // Act
        vm.refresh()
        advanceUntilIdle()

        // Assert
        val state = vm.state.value
        assertEquals(listOf(alice, bob), state.users)
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertNull(state.error)
    }

    @Test
    fun `initial load failure shows full page error without event`() = runTest(dispatcher) {
        coEvery { repository.list() } throws AppException.Network("网络连接失败")
        val vm = createViewModel()

        vm.events.test {
            vm.refresh()
            advanceUntilIdle()

            assertEquals("网络连接失败", vm.state.value.error)
            assertFalse(vm.state.value.isLoading)
            expectNoEvents()
        }
    }

    @Test
    fun `refresh failure with existing data keeps list and emits event`() = runTest(dispatcher) {
        coEvery { repository.list() } returns listOf(alice)
        val vm = createViewModel()
        vm.refresh()
        advanceUntilIdle()
        coEvery { repository.list() } throws AppException.Server(500, "服务器错误")

        vm.events.test {
            vm.refresh(pull = true)
            advanceUntilIdle()

            assertEquals("刷新失败：服务器错误", awaitItem())
            assertEquals(listOf(alice), vm.state.value.users)
            assertNull(vm.state.value.error)
            assertFalse(vm.state.value.isRefreshing)
        }
    }

    @Test
    fun `pull refresh shows refreshing indicator instead of loading`() = runTest(dispatcher) {
        val gate = CompletableDeferred<List<UserDto>>()
        coEvery { repository.list() } coAnswers { gate.await() }
        val vm = createViewModel()

        vm.refresh(pull = true)
        runCurrent()

        assertTrue(vm.state.value.isRefreshing)
        assertFalse(vm.state.value.isLoading)

        gate.complete(listOf(alice))
        advanceUntilIdle()
        assertFalse(vm.state.value.isRefreshing)
        assertEquals(listOf(alice), vm.state.value.users)
    }

    @Test
    fun `refresh after error clears previous error`() = runTest(dispatcher) {
        coEvery { repository.list() } throws AppException.Network("网络连接失败")
        val vm = createViewModel()
        vm.refresh()
        advanceUntilIdle()
        coEvery { repository.list() } returns listOf(alice)

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(listOf(alice), vm.state.value.users)
    }

    @Test
    fun `newer refresh cancels in-flight one so stale result is not applied`() = runTest(dispatcher) {
        val slow = CompletableDeferred<List<UserDto>>()
        var calls = 0
        coEvery { repository.list() } coAnswers { if (calls++ == 0) slow.await() else listOf(bob) }
        val vm = createViewModel()

        vm.refresh()
        runCurrent()
        vm.refresh()
        advanceUntilIdle()
        slow.complete(listOf(alice))
        advanceUntilIdle()

        assertEquals(listOf(bob), vm.state.value.users)
    }

    @Test
    fun `non admin user does not request user list`() = runTest(dispatcher) {
        currentUser.value = UserDto(id = 5, username = "guest", role = UserRole.GENERAL)
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.state.value.isAdmin)
        coVerify(exactly = 0) { repository.list() }
    }

    @Test
    fun `unknown current user is treated as admin`() = runTest(dispatcher) {
        currentUser.value = null
        coEvery { repository.list() } returns emptyList()
        val vm = createViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.state.value.isAdmin)
        coVerify(exactly = 1) { repository.list() }
    }
}
