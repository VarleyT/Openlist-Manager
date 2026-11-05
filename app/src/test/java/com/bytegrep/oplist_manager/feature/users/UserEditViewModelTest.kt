package com.bytegrep.oplist_manager.feature.users

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.model.UserRole
import com.bytegrep.oplist_manager.data.repository.UserRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.bytegrep.oplist_manager.navigation.UserEditRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class UserEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<UserRepository>(relaxUnitFun = true)
    private val currentUser = MutableStateFlow<UserDto?>(UserDto(id = 1, username = "admin", role = UserRole.ADMIN))
    private val session = mockk<SessionManager> { every { currentUser } returns this@UserEditViewModelTest.currentUser }

    private val bob = UserDto(
        id = 3,
        username = "bob",
        password = "server-hash",
        basePath = "/bob",
        role = UserRole.GENERAL,
        permission = 0b0001,
        otp = true,
    )
    private val admin = UserDto(id = 1, username = "admin", role = UserRole.ADMIN)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(SAVED_STATE_HANDLE_KT)
    }

    @After
    fun tearDown() {
        unmockkStatic(SAVED_STATE_HANDLE_KT)
        Dispatchers.resetMain()
    }

    /**
     * JVM 单测下 toRoute 依赖 android.os.Bundle（isReturnDefaultValues 使其恒返回 0），
     * 因此 mock 非 inline 的 toRoute(KClass, typeMap) 直接返回目标路由。
     */
    private fun createViewModel(id: Int): UserEditViewModel {
        val handle = SavedStateHandle()
        every { handle.toRoute(UserEditRoute::class, any()) } returns UserEditRoute(id)
        return UserEditViewModel(handle, repository, session)
    }

    private fun loadedViewModel(user: UserDto, scope: TestScope): UserEditViewModel {
        coEvery { repository.get(user.id) } returns user
        return createViewModel(user.id).also { scope.advanceUntilIdle() }
    }

    // region 加载

    @Test
    fun `new user route does not load and starts with empty form`() = runTest(dispatcher) {
        val vm = createViewModel(-1)
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertEquals(UserDto(allowLdap = true), state.form)
        assertNull(state.original)
        assertFalse(state.canDelete)
        assertFalse(state.canCancel2fa)
        coVerify(exactly = 0) { repository.get(any()) }
    }

    @Test
    fun `new user form defaults allow ldap on to match server create behavior`() = runTest(dispatcher) {
        // OpenList 创建用户时会把 allow_ldap=false 强制写成 true，新建表单默认值需与之一致
        val vm = createViewModel(-1)
        advanceUntilIdle()

        assertTrue(vm.state.value.form.allowLdap)
    }

    @Test
    fun `existing user route loads user and clears password in form`() = runTest(dispatcher) {
        coEvery { repository.get(3) } returns bob

        val vm = createViewModel(3)
        assertTrue(vm.state.value.isLoading)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isNew)
        assertFalse(state.isLoading)
        assertEquals(bob, state.original)
        assertEquals(bob.copy(password = ""), state.form)
        assertTrue(state.canDelete)
        assertTrue(state.canCancel2fa)
    }

    @Test
    fun `load failure exposes error and retry succeeds`() = runTest(dispatcher) {
        coEvery { repository.get(3) } throws AppException.Network("网络连接失败")
        val vm = createViewModel(3)
        advanceUntilIdle()
        assertEquals("网络连接失败", vm.state.value.loadError)
        assertFalse(vm.state.value.isLoading)

        coEvery { repository.get(3) } returns bob
        vm.load()
        advanceUntilIdle()

        assertNull(vm.state.value.loadError)
        assertEquals(bob, vm.state.value.original)
    }

    @Test
    fun `non admin session does not load user`() = runTest(dispatcher) {
        currentUser.value = UserDto(id = 9, username = "normal", role = UserRole.GENERAL)

        val vm = createViewModel(3)
        advanceUntilIdle()

        assertFalse(vm.state.value.isAdminSession)
        coVerify(exactly = 0) { repository.get(any()) }
    }

    // endregion

    // region 表单编辑

    @Test
    fun `permission toggle updates form mask`() = runTest(dispatcher) {
        val vm = loadedViewModel(bob, this)

        vm.onPermissionChange(UserPermission.WRITE.bit, true)
        vm.onPermissionChange(UserPermission.SEE_HIDES.bit, false)

        assertEquals(0b1000, vm.state.value.form.permission)
        assertEquals(0b0001, vm.state.value.original?.permission)
    }

    @Test
    fun `admin target ignores permission disabled and role changes`() = runTest(dispatcher) {
        val vm = loadedViewModel(admin, this)

        vm.onPermissionChange(UserPermission.WRITE.bit, true)
        vm.onDisabledChange(true)
        vm.onRoleChange(UserRole.GUEST)

        val form = vm.state.value.form
        assertEquals(0, form.permission)
        assertFalse(form.disabled)
        assertEquals(UserRole.ADMIN, form.role)
        assertFalse(vm.state.value.canEditRole)
        assertFalse(vm.state.value.canDelete)
    }

    @Test
    fun `role cannot be promoted to admin`() = runTest(dispatcher) {
        val vm = createViewModel(-1)

        vm.onRoleChange(UserRole.ADMIN)
        assertEquals(UserRole.GENERAL, vm.state.value.form.role)

        vm.onRoleChange(UserRole.GUEST)
        assertEquals(UserRole.GUEST, vm.state.value.form.role)
    }

    @Test
    fun `text and switch fields update form`() = runTest(dispatcher) {
        val vm = createViewModel(-1)

        vm.onUsernameChange("carol")
        vm.onPasswordChange("secret")
        vm.onBasePathChange("/carol")
        vm.onDisabledChange(true)
        vm.onAllowLdapChange(true)

        val form = vm.state.value.form
        assertEquals("carol", form.username)
        assertEquals("secret", form.password)
        assertEquals("/carol", form.basePath)
        assertTrue(form.disabled)
        assertTrue(form.allowLdap)
    }

    // endregion

    // region 保存

    @Test
    fun `save with blank username reports validation error`() = runTest(dispatcher) {
        val vm = createViewModel(-1)
        vm.onUsernameChange("   ")
        vm.onPasswordChange("pw")

        vm.events.test {
            vm.save()
            assertEquals(UserEditEvent.Message("请输入用户名"), awaitItem())
        }
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `save new user without password reports validation error`() = runTest(dispatcher) {
        val vm = createViewModel(-1)
        vm.onUsernameChange("carol")

        vm.events.test {
            vm.save()
            assertEquals(UserEditEvent.Message("请输入密码"), awaitItem())
        }
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `save new user trims username normalizes base path and finishes`() = runTest(dispatcher) {
        val payload = slot<UserDto>()
        coEvery { repository.create(capture(payload)) } returns Unit
        val vm = createViewModel(-1)
        vm.onUsernameChange("  carol  ")
        vm.onPasswordChange("pw")
        vm.onBasePathChange(" data ")

        vm.events.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Finished("用户已创建"), awaitItem())
        }
        assertEquals("carol", payload.captured.username)
        assertEquals("/data", payload.captured.basePath)
        assertEquals("pw", payload.captured.password)
        assertEquals(0, payload.captured.id)
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `blank base path is normalized to root`() = runTest(dispatcher) {
        val payload = slot<UserDto>()
        coEvery { repository.create(capture(payload)) } returns Unit
        val vm = createViewModel(-1)
        vm.onUsernameChange("carol")
        vm.onPasswordChange("pw")
        vm.onBasePathChange("  ")

        vm.save()
        advanceUntilIdle()

        assertEquals("/", payload.captured.basePath)
    }

    @Test
    fun `editing user with empty password submits update without new password`() = runTest(dispatcher) {
        val payload = slot<UserDto>()
        coEvery { repository.update(capture(payload)) } returns Unit
        val vm = loadedViewModel(bob, this)
        vm.onPermissionChange(UserPermission.WRITE.bit, true)

        vm.events.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Finished("用户已保存"), awaitItem())
        }
        assertEquals(3, payload.captured.id)
        assertEquals("", payload.captured.password)
        assertEquals(0b1001, payload.captured.permission)
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `save failure emits message and clears busy`() = runTest(dispatcher) {
        coEvery { repository.update(any()) } throws AppException.Server(400, "用户名已存在")
        val vm = loadedViewModel(bob, this)

        vm.events.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Message("用户名已存在"), awaitItem())
        }
        assertFalse(vm.state.value.isBusy)
    }

    // endregion

    // region 删除与两步验证

    @Test
    fun `delete general user finishes with message`() = runTest(dispatcher) {
        val vm = loadedViewModel(bob, this)

        vm.events.test {
            vm.delete()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Finished("用户 bob 已删除"), awaitItem())
        }
        coVerify(exactly = 1) { repository.delete(3) }
    }

    @Test
    fun `delete is ignored for admin and guest targets`() = runTest(dispatcher) {
        val guest = UserDto(id = 2, username = "guest", role = UserRole.GUEST)
        val adminVm = loadedViewModel(admin, this)
        val guestVm = loadedViewModel(guest, this)

        adminVm.delete()
        guestVm.delete()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `delete failure emits message without finishing`() = runTest(dispatcher) {
        coEvery { repository.delete(3) } throws AppException.Forbidden()
        val vm = loadedViewModel(bob, this)

        vm.events.test {
            vm.delete()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Message("当前账号无权限执行此操作"), awaitItem())
        }
    }

    @Test
    fun `cancel 2fa clears otp flag and stays on page`() = runTest(dispatcher) {
        val vm = loadedViewModel(bob, this)

        vm.events.test {
            vm.cancel2fa()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Message("已关闭两步验证"), awaitItem())
        }
        coVerify(exactly = 1) { repository.cancel2fa(3) }
        assertEquals(false, vm.state.value.original?.otp)
        assertFalse(vm.state.value.form.otp)
        assertFalse(vm.state.value.canCancel2fa)
    }

    @Test
    fun `cancel 2fa failure keeps otp flag`() = runTest(dispatcher) {
        coEvery { repository.cancel2fa(3) } throws AppException.Network("网络连接失败")
        val vm = loadedViewModel(bob, this)

        vm.events.test {
            vm.cancel2fa()
            advanceUntilIdle()
            assertEquals(UserEditEvent.Message("网络连接失败"), awaitItem())
        }
        assertTrue(vm.state.value.canCancel2fa)
        assertFalse(vm.state.value.isBusy)
    }

    // endregion

    private companion object {
        const val SAVED_STATE_HANDLE_KT = "androidx.navigation.SavedStateHandleKt"
    }
}
