package com.bytegrep.oplist_manager.feature.profile

import com.bytegrep.oplist_manager.BuildConfig
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.update.AppRelease
import com.bytegrep.oplist_manager.data.update.UpdateCheckResult
import com.bytegrep.oplist_manager.data.update.UpdateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<UpdateRepository>()
    private val release = AppRelease(version = "9.9.9", title = "v9.9.9", notes = "", pageUrl = "https://example.com")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        assertEquals(UpdateUiState.Idle, AboutViewModel(repository).updateState.value)
    }

    @Test
    fun `check shows checking then up to date`() = runTest {
        coEvery { repository.check(BuildConfig.VERSION_NAME) } returns UpdateCheckResult.UpToDate
        val vm = AboutViewModel(repository)

        vm.checkUpdate()
        assertEquals(UpdateUiState.Checking, vm.updateState.value)
        advanceUntilIdle()

        assertEquals(UpdateUiState.UpToDate, vm.updateState.value)
    }

    @Test
    fun `available release opens dialog`() = runTest {
        coEvery { repository.check(any()) } returns UpdateCheckResult.Available(release)
        val vm = AboutViewModel(repository)

        vm.checkUpdate()
        advanceUntilIdle()

        assertEquals(UpdateUiState.Available(release, dialogVisible = true), vm.updateState.value)
    }

    @Test
    fun `failure shows user message`() = runTest {
        coEvery { repository.check(any()) } throws AppException.Network("网络不可用")
        val vm = AboutViewModel(repository)

        vm.checkUpdate()
        advanceUntilIdle()

        assertEquals(UpdateUiState.Failed("网络不可用"), vm.updateState.value)
    }

    @Test
    fun `check while checking is ignored`() = runTest {
        val gate = CompletableDeferred<UpdateCheckResult>()
        coEvery { repository.check(any()) } coAnswers { gate.await() }
        val vm = AboutViewModel(repository)

        vm.checkUpdate()
        advanceUntilIdle()
        vm.checkUpdate()
        gate.complete(UpdateCheckResult.UpToDate)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.check(any()) }
        assertEquals(UpdateUiState.UpToDate, vm.updateState.value)
    }

    @Test
    fun `dismiss hides dialog and check again reopens without new request`() = runTest {
        coEvery { repository.check(any()) } returns UpdateCheckResult.Available(release)
        val vm = AboutViewModel(repository)
        vm.checkUpdate()
        advanceUntilIdle()

        vm.dismissDialog()
        assertEquals(UpdateUiState.Available(release, dialogVisible = false), vm.updateState.value)
        vm.checkUpdate()
        advanceUntilIdle()

        assertEquals(UpdateUiState.Available(release, dialogVisible = true), vm.updateState.value)
        coVerify(exactly = 1) { repository.check(any()) }
    }

    @Test
    fun `dismiss outside available state does nothing`() = runTest {
        coEvery { repository.check(any()) } returns UpdateCheckResult.UpToDate
        val vm = AboutViewModel(repository)
        vm.checkUpdate()
        advanceUntilIdle()

        vm.dismissDialog()

        assertEquals(UpdateUiState.UpToDate, vm.updateState.value)
    }

    @Test
    fun `failed check can be retried`() = runTest {
        coEvery { repository.check(any()) } throws AppException.Network("网络不可用") andThen UpdateCheckResult.UpToDate
        val vm = AboutViewModel(repository)
        vm.checkUpdate()
        advanceUntilIdle()

        vm.checkUpdate()
        advanceUntilIdle()

        assertEquals(UpdateUiState.UpToDate, vm.updateState.value)
        coVerify(exactly = 2) { repository.check(any()) }
    }
}
