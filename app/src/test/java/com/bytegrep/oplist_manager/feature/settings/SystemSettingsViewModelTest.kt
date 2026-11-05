package com.bytegrep.oplist_manager.feature.settings

import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.SettingGroup
import com.bytegrep.oplist_manager.data.model.SettingItem
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.SettingRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class SystemSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<SettingRepository>(relaxUnitFun = true)
    private val currentUser = MutableStateFlow<UserDto?>(UserDto(id = 1, username = "admin", role = UserRole.ADMIN))
    private val session = mockk<SessionManager> { every { currentUser } returns this@SystemSettingsViewModelTest.currentUser }

    private val title = SettingItem(key = "site_title", value = "OpenList", group = SettingGroup.SITE.id)
    private val announcement = SettingItem(key = "announcement", value = "", group = SettingGroup.SITE.id)
    private val version = SettingItem(key = "version", value = "v4", flag = SettingItem.FLAG_READONLY)
    private val deprecated = SettingItem(key = "old_key", value = "x", flag = SettingItem.FLAG_DEPRECATED)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { repository.list(SettingGroup.SITE.id) } returns listOf(title, announcement, version, deprecated)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): SystemSettingsViewModel =
        SystemSettingsViewModel(repository, session).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
        }

    // region 加载

    @Test
    fun `init loads site group and drops deprecated items`() = runTest(dispatcher) {
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals(SettingGroup.SITE, state.group)
        assertEquals(listOf(title, announcement, version), state.items)
        assertFalse(state.isLoading)
        assertFalse(state.hasChanges)
        assertNull(state.error)
    }

    @Test
    fun `non admin session skips loading`() = runTest(dispatcher) {
        currentUser.value = UserDto(id = 9, username = "normal", role = UserRole.GENERAL)

        val vm = createViewModel()

        assertFalse(vm.state.value.isAdmin)
        coVerify(exactly = 0) { repository.list(any()) }
    }

    @Test
    fun `load failure exposes error message`() = runTest(dispatcher) {
        coEvery { repository.list(SettingGroup.SITE.id) } throws AppException.Network("网络连接失败")

        val vm = createViewModel()

        assertEquals("网络连接失败", vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `select group clears edits and loads that group`() = runTest(dispatcher) {
        val logo = SettingItem(key = "logo", value = "a.png", group = SettingGroup.STYLE.id)
        coEvery { repository.list(SettingGroup.STYLE.id) } returns listOf(logo)
        val vm = createViewModel()
        vm.onValueChange(title, "新标题")

        vm.selectGroup(SettingGroup.STYLE)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(SettingGroup.STYLE, state.group)
        assertEquals(listOf(logo), state.items)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `selecting current loaded group does not reload`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.selectGroup(SettingGroup.SITE)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.list(SettingGroup.SITE.id) }
    }

    // endregion

    // region 编辑脏状态

    @Test
    fun `changing a value marks it dirty and valueOf returns edit`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.onValueChange(title, "新标题")
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.hasChanges)
        assertEquals("新标题", state.valueOf(title))
        assertEquals("", state.valueOf(announcement))
    }

    @Test
    fun `restoring original value clears dirty state`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onValueChange(title, "新标题")
        advanceUntilIdle()
        assertTrue(vm.state.value.hasChanges)

        vm.onValueChange(title, "OpenList")
        advanceUntilIdle()

        assertFalse(vm.state.value.hasChanges)
    }

    @Test
    fun `read only item cannot be edited`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.onValueChange(version, "v5")
        advanceUntilIdle()

        assertFalse(vm.state.value.hasChanges)
        assertEquals("v4", vm.state.value.valueOf(version))
    }

    @Test
    fun `discard changes clears all edits`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.onValueChange(title, "新标题")
        vm.onValueChange(announcement, "公告")

        vm.discardChanges()
        advanceUntilIdle()

        assertFalse(vm.state.value.hasChanges)
    }

    // endregion

    // region 保存

    @Test
    fun `save submits only changed items then reloads`() = runTest(dispatcher) {
        val submitted = slot<List<SettingItem>>()
        coEvery { repository.save(capture(submitted)) } returns Unit
        val vm = createViewModel()
        vm.onValueChange(title, "新标题")

        vm.events.test {
            vm.save()
            advanceUntilIdle()
            assertEquals("已保存 1 项设置", awaitItem())
        }
        assertEquals(listOf(title.copy(value = "新标题")), submitted.captured)
        coVerify(exactly = 2) { repository.list(SettingGroup.SITE.id) }
        assertFalse(vm.state.value.isSaving)
        assertFalse(vm.state.value.hasChanges)
    }

    @Test
    fun `save without changes does nothing`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `save failure emits message and keeps edits`() = runTest(dispatcher) {
        coEvery { repository.save(any()) } throws AppException.Server(500, "保存出错")
        val vm = createViewModel()
        vm.onValueChange(title, "新标题")

        vm.events.test {
            vm.save()
            advanceUntilIdle()
            assertEquals("保存失败：保存出错", awaitItem())
        }
        assertTrue(vm.state.value.hasChanges)
        assertFalse(vm.state.value.isSaving)
        coVerify(exactly = 1) { repository.list(SettingGroup.SITE.id) }
    }

    @Test
    fun `changedItems ignores edits equal to original or unknown keys`() {
        val result = changedItems(
            items = listOf(title, announcement),
            edits = mapOf("site_title" to "OpenList", "announcement" to "hi", "missing" to "x"),
        )

        assertEquals(listOf(announcement.copy(value = "hi")), result)
    }

    // endregion
}
