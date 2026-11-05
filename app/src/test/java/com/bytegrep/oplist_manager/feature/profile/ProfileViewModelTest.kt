package com.bytegrep.oplist_manager.feature.profile

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.ThemeMode
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val userFlow = MutableStateFlow<UserDto?>(null)
    private val session = mockk<SessionManager> {
        every { session } returns sessionFlow
        every { currentUser } returns userFlow
    }
    private val authRepository = mockk<AuthRepository>(relaxUnitFun = true)
    private val settingsFlow = MutableStateFlow(AppSettings())
    private val settingsStore = mockk<AppSettingsStore>(relaxUnitFun = true) { every { settings } returns settingsFlow }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ProfileViewModel(session, authRepository, settingsStore)

    @Test
    fun `refreshes user when not loaded yet`() = runTest {
        coEvery { session.refreshUser() } returns UserDto(id = 1, username = "admin")

        val vm = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 1) { session.refreshUser() }
        assertSame(sessionFlow, vm.currentSession)
        assertSame(userFlow, vm.user)
    }

    @Test
    fun `skips refresh when user already loaded`() = runTest {
        userFlow.value = UserDto(id = 1, username = "admin")

        createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { session.refreshUser() }
    }

    @Test
    fun `refresh failure is swallowed`() = runTest {
        coEvery { session.refreshUser() } throws AppException.Network("offline")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(null, vm.user.value)
    }

    @Test
    fun `logout emits event after repository logout`() = runTest {
        userFlow.value = UserDto(id = 1)
        val vm = createViewModel()

        vm.loggedOut.test {
            vm.logout()
            awaitItem()
        }
        coVerify { authRepository.logout() }
    }

    @Test
    fun `settings follow store while subscribed`() = runTest {
        userFlow.value = UserDto(id = 1)
        val vm = createViewModel()

        vm.settings.test {
            assertEquals(AppSettings(), awaitItem())
            settingsFlow.value = AppSettings(themeMode = ThemeMode.DARK, fileGrid = true)
            assertEquals(AppSettings(themeMode = ThemeMode.DARK, fileGrid = true), awaitItem())
        }
    }

    @Test
    fun `setting changes are written to store`() = runTest {
        userFlow.value = UserDto(id = 1)
        val vm = createViewModel()

        vm.setThemeMode(ThemeMode.LIGHT)
        vm.setDynamicColor(true)
        vm.setFileGrid(true)
        vm.setAutoCheckUpdate(false)
        advanceUntilIdle()

        coVerifyOrder {
            settingsStore.setThemeMode(ThemeMode.LIGHT)
            settingsStore.setDynamicColor(true)
            settingsStore.setFileGrid(true)
            settingsStore.setAutoCheckUpdate(false)
        }
    }
}
