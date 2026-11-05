package com.bytegrep.oplist_manager

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.ThemeMode
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.data.update.AppRelease
import com.bytegrep.oplist_manager.data.update.StartupUpdateChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val authExpired = MutableSharedFlow<String>()
    private val sessionManager = mockk<SessionManager> {
        every { authExpired } returns this@MainViewModelTest.authExpired
    }
    private val settingsFlow = MutableStateFlow(AppSettings())
    private val settingsStore = mockk<AppSettingsStore> { every { settings } returns settingsFlow }
    private val updateChecker = mockk<StartupUpdateChecker>(relaxUnitFun = true)

    private val release = AppRelease(version = "9.9.9", title = "v9.9.9", notes = "", pageUrl = "https://example.com")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { updateChecker.check(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MainViewModel(sessionManager, settingsStore, updateChecker)

    @Test
    fun `start is loading until session restore completes`() = runTest {
        coEvery { sessionManager.restore() } returns false

        val vm = createViewModel()

        assertEquals(StartState.Loading, vm.start.value)
        advanceUntilIdle()
        assertEquals(StartState.Ready(loggedIn = false), vm.start.value)
        coVerify(exactly = 0) { sessionManager.refreshUser() }
    }

    @Test
    fun `restored session is ready and refreshes user`() = runTest {
        coEvery { sessionManager.restore() } returns true
        coEvery { sessionManager.refreshUser() } returns UserDto(id = 1, username = "admin")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(StartState.Ready(loggedIn = true), vm.start.value)
        coVerify { sessionManager.refreshUser() }
    }

    @Test
    fun `refresh user failure does not affect start state`() = runTest {
        coEvery { sessionManager.restore() } returns true
        coEvery { sessionManager.refreshUser() } throws AppException.Network("offline")

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(StartState.Ready(loggedIn = true), vm.start.value)
    }

    @Test
    fun `settings mirror store and auth expired is forwarded`() = runTest {
        coEvery { sessionManager.restore() } returns false
        val vm = createViewModel()

        settingsFlow.value = AppSettings(themeMode = ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, vm.settings.value.themeMode)
        assertSame(authExpired, vm.authExpired)
    }

    @Test
    fun `startup check exposes pending update with current version`() = runTest {
        coEvery { sessionManager.restore() } returns false
        coEvery { updateChecker.check(BuildConfig.VERSION_NAME, any()) } returns release

        val vm = createViewModel()
        advanceUntilIdle()

        assertEquals(release, vm.pendingUpdate.value)
    }

    @Test
    fun `dismiss hides update without ignoring version`() = runTest {
        coEvery { sessionManager.restore() } returns false
        coEvery { updateChecker.check(any(), any()) } returns release
        val vm = createViewModel()
        advanceUntilIdle()

        vm.dismissUpdate()
        advanceUntilIdle()

        assertNull(vm.pendingUpdate.value)
        coVerify(exactly = 0) { updateChecker.ignore(any()) }
    }

    @Test
    fun `ignore hides update and remembers its version`() = runTest {
        coEvery { sessionManager.restore() } returns false
        coEvery { updateChecker.check(any(), any()) } returns release
        val vm = createViewModel()
        advanceUntilIdle()

        vm.ignoreUpdate()
        advanceUntilIdle()

        assertNull(vm.pendingUpdate.value)
        coVerify { updateChecker.ignore("9.9.9") }
    }

    @Test
    fun `ignore without pending update does nothing`() = runTest {
        coEvery { sessionManager.restore() } returns false
        val vm = createViewModel()
        advanceUntilIdle()

        vm.ignoreUpdate()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateChecker.ignore(any()) }
    }
}
