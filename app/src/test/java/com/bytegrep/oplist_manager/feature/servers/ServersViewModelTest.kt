package com.bytegrep.oplist_manager.feature.servers

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.data.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServersViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val serversFlow = MutableStateFlow(ServerState())
    private val repository = mockk<AuthRepository>(relaxUnitFun = true) { every { servers } returns serversFlow }
    private val nas = ServerProfile(id = "s1", name = "NAS", baseUrl = "http://10.0.0.1:5244/", username = "admin")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = ServersViewModel(repository)

    @Test
    fun `state follows saved servers while subscribed`() = runTest {
        val vm = createViewModel()

        vm.state.test {
            assertEquals(ServerState(), awaitItem())
            serversFlow.value = ServerState(listOf(nas), activeId = "s1")
            assertEquals(ServerState(listOf(nas), activeId = "s1"), awaitItem())
        }
    }

    @Test
    fun `switch with valid token emits switched`() = runTest {
        coEvery { repository.switchTo("s1") } returns true
        val vm = createViewModel()

        vm.events.test {
            vm.switchTo(nas)
            assertEquals(ServersEvent.Switched, awaitItem())
        }
    }

    @Test
    fun `switch without valid token asks for login`() = runTest {
        coEvery { repository.switchTo("s1") } returns false
        val vm = createViewModel()

        vm.events.test {
            vm.switchTo(nas)
            assertEquals(ServersEvent.NeedLogin("s1"), awaitItem())
        }
    }

    @Test
    fun `switch failure is treated as needing login`() = runTest {
        coEvery { repository.switchTo("s1") } throws AppException.Network("offline")
        val vm = createViewModel()

        vm.events.test {
            vm.switchTo(nas)
            assertEquals(ServersEvent.NeedLogin("s1"), awaitItem())
        }
    }

    @Test
    fun `rename delegates to repository`() = runTest {
        val vm = createViewModel()

        vm.rename("s1", "新名字")
        advanceUntilIdle()

        coVerify { repository.rename("s1", "新名字") }
    }

    @Test
    fun `removing active server emits active removed`() = runTest {
        coEvery { repository.remove("s1") } returns true
        val vm = createViewModel()

        vm.events.test {
            vm.remove("s1")
            assertEquals(ServersEvent.ActiveRemoved, awaitItem())
        }
    }

    @Test
    fun `removing other server shows confirmation message`() = runTest {
        coEvery { repository.remove("s2") } returns false
        val vm = createViewModel()

        vm.events.test {
            vm.remove("s2")
            assertEquals(ServersEvent.Message("已移除服务器"), awaitItem())
        }
    }

    @Test
    fun `remove failure shows error message`() = runTest {
        coEvery { repository.remove("s1") } throws AppException.Unknown("写入失败")
        val vm = createViewModel()

        vm.events.test {
            vm.remove("s1")
            assertEquals(ServersEvent.Message("写入失败"), awaitItem())
        }
    }
}
