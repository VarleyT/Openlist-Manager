package com.bytegrep.oplist_manager.feature.login

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import com.bytegrep.oplist_manager.navigation.LoginRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class LoginViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<AuthRepository>()

    private val nas = ServerProfile(id = "s1", name = "NAS", baseUrl = "http://192.168.1.2:5244/", username = "admin")
    private val cloud = ServerProfile(id = "s2", name = "云", baseUrl = "https://pan.example.com/", username = "guest")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(SAVED_STATE_HANDLE_KT)
        every { repository.servers } returns flowOf(ServerState(listOf(nas, cloud), activeId = "s2"))
    }

    @After
    fun tearDown() {
        unmockkStatic(SAVED_STATE_HANDLE_KT)
        Dispatchers.resetMain()
    }

    /** JVM 下 toRoute 依赖 Bundle，mock 非 inline 的 toRoute(KClass, typeMap) 直接返回路由 */
    private fun createViewModel(serverId: String? = null): LoginViewModel {
        val handle = SavedStateHandle()
        every { handle.toRoute(LoginRoute::class, any()) } returns LoginRoute(serverId)
        return LoginViewModel(handle, repository)
    }

    private fun LoginViewModel.fillForm() {
        onUrlChange("192.168.1.2:5244")
        onUsernameChange("admin")
        onPasswordChange("pw")
    }

    @Test
    fun `init loads history and active server`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(nas, cloud), state.history)
        assertEquals("s2", state.activeId)
        assertNull(state.editingId)
        assertEquals("", state.url)
    }

    @Test
    fun `route server id prefills url and username from history`() = runTest {
        val vm = createViewModel(serverId = "s1")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("http://192.168.1.2:5244", state.url)
        assertEquals("admin", state.username)
        assertEquals("s1", state.editingId)
    }

    @Test
    fun `unknown route server id keeps editing id but leaves form empty`() = runTest {
        val vm = createViewModel(serverId = "gone")
        advanceUntilIdle()

        assertEquals("gone", vm.state.value.editingId)
        assertEquals("", vm.state.value.url)
    }

    @Test
    fun `otp input keeps only first six digits`() = runTest {
        val vm = createViewModel()

        vm.onOtpChange("12a 34-5678")

        assertEquals("123456", vm.state.value.otpCode)
    }

    @Test
    fun `toggles update remember and password visibility`() = runTest {
        val vm = createViewModel()

        vm.onRememberChange(false)
        vm.togglePasswordVisible()

        assertFalse(vm.state.value.rememberPassword)
        assertTrue(vm.state.value.passwordVisible)
    }

    @Test
    fun `submit is ignored when form is incomplete`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.onUrlChange("192.168.1.2")

        vm.submit()
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        coVerify(exactly = 0) { repository.login(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `successful submit passes form values clears password and emits logged in`() = runTest {
        coEvery { repository.login(any(), any(), any(), any(), any(), any()) } returns nas
        val vm = createViewModel(serverId = "s1")
        advanceUntilIdle()
        vm.onPasswordChange("pw")
        vm.onOtpChange("654321")
        vm.onRememberChange(false)

        vm.loggedIn.test {
            vm.submit()
            assertTrue(vm.state.value.isLoading)
            awaitItem()
        }

        coVerify {
            repository.login(
                rawUrl = "http://192.168.1.2:5244",
                username = "admin",
                password = "pw",
                otpCode = "654321",
                rememberPassword = false,
                existingId = "s1",
            )
        }
        assertFalse(vm.state.value.isLoading)
        assertEquals("", vm.state.value.password)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `failed submit shows error and keeps password`() = runTest {
        coEvery { repository.login(any(), any(), any(), any(), any(), any()) } throws
            AppException.Server(400, "password is incorrect")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.fillForm()

        vm.submit()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("password is incorrect", state.error)
        assertEquals("pw", state.password)
    }

    @Test
    fun `editing a field clears previous error`() = runTest {
        coEvery { repository.login(any(), any(), any(), any(), any(), any()) } throws AppException.Unknown("失败")
        val vm = createViewModel()
        advanceUntilIdle()
        vm.fillForm()
        vm.submit()
        advanceUntilIdle()

        vm.onPasswordChange("pw2")

        assertNull(vm.state.value.error)
    }

    @Test
    fun `submit while loading is ignored`() = runTest {
        val gate = CompletableDeferred<ServerProfile>()
        coEvery { repository.login(any(), any(), any(), any(), any(), any()) } coAnswers { gate.await() }
        val vm = createViewModel()
        advanceUntilIdle()
        vm.fillForm()

        vm.submit()
        advanceUntilIdle()
        vm.submit()
        advanceUntilIdle()
        gate.complete(nas)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.login(any(), any(), any(), any(), any(), any()) }
    }

    private companion object {
        const val SAVED_STATE_HANDLE_KT = "androidx.navigation.SavedStateHandleKt"
    }
}
