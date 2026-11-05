package com.bytegrep.oplist_manager.feature.storage

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.model.DriverItem
import com.bytegrep.oplist_manager.data.repository.StorageRepository
import com.bytegrep.oplist_manager.navigation.StorageEditRoute
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `SavedStateHandle.toRoute` 在 JVM 单测中依赖被 stub 的 Bundle（读出的 Int 恒为 0），
 * 因此 mock 其非 inline 的实现 `toRoute(KClass, Map)` 直接返回目标路由。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StorageEditViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<StorageRepository>(relaxed = true)
    private val translationsLoader = mockk<DriverTranslationsLoader>()

    private val localInfo = DriverInfo(
        common = listOf(
            DriverItem(name = "mount_path", required = true),
            DriverItem(name = "order", type = DriverItemType.NUMBER, default = "0"),
            DriverItem(name = "remark"),
        ),
        additional = listOf(
            DriverItem(name = "root_folder_path", required = true),
            DriverItem(name = "show_hidden", type = DriverItemType.BOOL, required = true),
        ),
    )

    private val mountKey = StorageFormKeys.commonKey("mount_path")
    private val orderKey = StorageFormKeys.commonKey("order")
    private val rootKey = StorageFormKeys.additionKey("root_folder_path")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic("androidx.navigation.SavedStateHandleKt")
        coEvery { translationsLoader.load() } returns DriverTranslations.EMPTY
        coEvery { repository.driverNames() } returns listOf("Local", "S3")
        coEvery { repository.driverInfo("Local") } returns localInfo
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.navigation.SavedStateHandleKt")
        Dispatchers.resetMain()
    }

    private fun TestScope.createVm(id: Int): StorageEditViewModel {
        val handle = SavedStateHandle()
        every { handle.toRoute(StorageEditRoute::class, any()) } returns StorageEditRoute(id)
        val vm = StorageEditViewModel(handle, repository, translationsLoader)
        advanceUntilIdle()
        return vm
    }

    private fun TestScope.createNewWithLocalForm(): StorageEditViewModel {
        val vm = createVm(-1)
        vm.selectDriver("Local")
        advanceUntilIdle()
        return vm
    }

    private fun fillRequired(vm: StorageEditViewModel) {
        vm.onValueChange(mountKey, "/local")
        vm.onValueChange(rootKey, "/data")
    }

    // ---------- 新建：驱动选择 ----------

    @Test
    fun `new storage starts at driver selection and loads driver names`() = runTest(dispatcher) {
        // Act
        val vm = createVm(-1)

        // Assert
        val state = vm.state.value
        assertTrue(state.isNew)
        assertEquals(StorageEditStep.SELECT_DRIVER, state.step)
        assertEquals(listOf("Local", "S3"), state.drivers)
        assertFalse(state.isLoading)
        coVerify(exactly = 0) { repository.get(any()) }
    }

    @Test
    fun `driver list failure shows load error and retry reloads drivers`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.driverNames() } throws AppException.Network("网络连接失败") andThen listOf("Local")
        val vm = createVm(-1)
        assertEquals("网络连接失败", vm.state.value.loadError)

        // Act
        vm.retry()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.loadError)
        assertEquals(listOf("Local"), vm.state.value.drivers)
    }

    @Test
    fun `driver query filters driver names ignoring case`() = runTest(dispatcher) {
        // Arrange
        val vm = createVm(-1)

        // Act
        vm.onDriverQueryChange(" s3 ")

        // Assert
        assertEquals(listOf("S3"), vm.state.value.filteredDrivers)
    }

    @Test
    fun `selecting driver loads driver info and fills default form values`() = runTest(dispatcher) {
        // Act
        val vm = createNewWithLocalForm()

        // Assert
        val state = vm.state.value
        assertEquals(StorageEditStep.FORM, state.step)
        assertEquals("Local", state.driver)
        assertEquals(localInfo, state.info)
        assertEquals("0", state.values[orderKey])
        assertEquals("false", state.values[StorageFormKeys.additionKey("show_hidden")])
        assertEquals(state.values, state.initialValues)
        assertFalse(state.isDirty)
    }

    @Test
    fun `stale driver info is discarded after switching to another driver`() = runTest(dispatcher) {
        // Arrange
        val slowLocal = CompletableDeferred<DriverInfo>()
        val s3Info = DriverInfo(common = listOf(DriverItem(name = "mount_path")))
        coEvery { repository.driverInfo("Local") } coAnswers { slowLocal.await() }
        coEvery { repository.driverInfo("S3") } returns s3Info
        val vm = createVm(-1)

        // Act
        vm.selectDriver("Local")
        advanceUntilIdle()
        vm.selectDriver("S3")
        advanceUntilIdle()
        slowLocal.complete(localInfo)
        advanceUntilIdle()

        // Assert
        assertEquals("S3", vm.state.value.driver)
        assertEquals(s3Info, vm.state.value.info)
    }

    @Test
    fun `driver info failure in form step can be retried`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.driverInfo("Local") } throws AppException.Server(500, "驱动不存在") andThen localInfo
        val vm = createNewWithLocalForm()
        assertEquals("驱动不存在", vm.state.value.loadError)

        // Act
        vm.retry()
        advanceUntilIdle()

        // Assert
        assertNull(vm.state.value.loadError)
        assertEquals(localInfo, vm.state.value.info)
    }

    // ---------- 编辑：加载已有存储 ----------

    @Test
    fun `editing existing storage loads raw json and extracts form values`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.get(5) } returns existingRaw()

        // Act
        val vm = createVm(5)

        // Assert
        val state = vm.state.value
        assertFalse(state.isNew)
        assertEquals(StorageEditStep.FORM, state.step)
        assertEquals("Local", state.driver)
        assertEquals("/old", state.values[mountKey])
        assertEquals("/srv", state.values[rootKey])
        assertFalse(state.isDirty)
        coVerify(exactly = 0) { repository.driverNames() }
    }

    @Test
    fun `editing load failure exposes load error`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.get(5) } throws AppException.Unknown("存储不存在")

        // Act
        val vm = createVm(5)

        // Assert
        assertEquals("存储不存在", vm.state.value.loadError)
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.info)
    }

    // ---------- 校验 ----------

    @Test
    fun `save with missing required fields marks errors and does not submit`() = runTest(dispatcher) {
        // Arrange
        val vm = createNewWithLocalForm()

        vm.events.test {
            // Act
            vm.save()
            advanceUntilIdle()

            // Assert
            assertEquals(StorageEditEvent.Message("请检查标红的字段"), awaitItem())
        }
        val errors = vm.state.value.fieldErrors
        assertEquals("此项为必填", errors[mountKey])
        assertEquals("此项为必填", errors[rootKey])
        // 布尔字段不参与必填校验
        assertFalse(errors.containsKey(StorageFormKeys.additionKey("show_hidden")))
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `save with non numeric number field reports number error`() = runTest(dispatcher) {
        // Arrange
        val vm = createNewWithLocalForm()
        fillRequired(vm)
        vm.onValueChange(orderKey, "abc")

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        assertEquals(mapOf(orderKey to "请输入数字"), vm.state.value.fieldErrors)
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `editing a field clears its validation error`() = runTest(dispatcher) {
        // Arrange
        val vm = createNewWithLocalForm()
        vm.save()
        advanceUntilIdle()

        // Act
        vm.onValueChange(mountKey, "/x")

        // Assert
        assertFalse(vm.state.value.fieldErrors.containsKey(mountKey))
        assertTrue(vm.state.value.fieldErrors.containsKey(rootKey))
    }

    @Test
    fun `save does nothing before driver info is loaded`() = runTest(dispatcher) {
        // Arrange
        val vm = createVm(-1)

        // Act
        vm.save()
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { repository.create(any()) }
        assertFalse(vm.state.value.isSaving)
    }

    // ---------- 保存 ----------

    @Test
    fun `saving new storage creates it and emits saved event`() = runTest(dispatcher) {
        // Arrange
        val body = slot<JsonObject>()
        coEvery { repository.create(capture(body)) } returns 11
        val vm = createNewWithLocalForm()
        fillRequired(vm)

        vm.events.test {
            // Act
            vm.save()
            advanceUntilIdle()

            // Assert
            assertEquals(StorageEditEvent.Saved("存储已添加"), awaitItem())
        }
        assertEquals("Local", body.captured["driver"]?.jsonPrimitive?.content)
        assertEquals("/local", body.captured["mount_path"]?.jsonPrimitive?.content)
        assertEquals(0, body.captured["order"]?.jsonPrimitive?.int)
        val state = vm.state.value
        assertFalse(state.isSaving)
        assertEquals(state.values, state.initialValues)
        assertFalse(state.isDirty)
    }

    @Test
    fun `saving existing storage updates it keeping unknown fields`() = runTest(dispatcher) {
        // Arrange
        val body = slot<JsonObject>()
        coEvery { repository.get(5) } returns existingRaw()
        coEvery { repository.update(capture(body)) } returns Unit
        val vm = createVm(5)
        vm.onValueChange(mountKey, "/new")

        vm.events.test {
            // Act
            vm.save()
            advanceUntilIdle()

            // Assert
            assertEquals(StorageEditEvent.Saved("存储已保存"), awaitItem())
        }
        assertEquals(5, body.captured["id"]?.jsonPrimitive?.int)
        assertEquals("/new", body.captured["mount_path"]?.jsonPrimitive?.content)
        assertEquals("keep", body.captured["unknown_field"]?.jsonPrimitive?.content)
        coVerify(exactly = 0) { repository.create(any()) }
    }

    @Test
    fun `save failure resets saving flag and emits error message`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.create(any()) } throws AppException.Server(500, "挂载路径已存在")
        val vm = createNewWithLocalForm()
        fillRequired(vm)

        vm.events.test {
            // Act
            vm.save()
            advanceUntilIdle()

            // Assert
            assertEquals(StorageEditEvent.Message("保存失败：挂载路径已存在"), awaitItem())
        }
        assertFalse(vm.state.value.isSaving)
        assertTrue(vm.state.value.isDirty)
    }

    // ---------- 返回 ----------

    @Test
    fun `back with unsaved changes asks for discard confirmation`() = runTest(dispatcher) {
        // Arrange
        val vm = createNewWithLocalForm()
        vm.onValueChange(mountKey, "/x")

        // Act
        vm.onBackRequested()

        // Assert
        assertTrue(vm.state.value.showDiscardDialog)
        assertEquals(StorageEditStep.FORM, vm.state.value.step)
    }

    @Test
    fun `confirming discard in new flow returns to driver selection`() = runTest(dispatcher) {
        // Arrange
        val vm = createNewWithLocalForm()
        vm.onValueChange(mountKey, "/x")
        vm.onBackRequested()

        // Act
        vm.confirmDiscard()
        advanceUntilIdle()

        // Assert
        val state = vm.state.value
        assertFalse(state.showDiscardDialog)
        assertEquals(StorageEditStep.SELECT_DRIVER, state.step)
        assertNull(state.info)
        assertTrue(state.values.isEmpty())
    }

    @Test
    fun `back without changes on edit page emits exit event`() = runTest(dispatcher) {
        // Arrange
        coEvery { repository.get(5) } returns existingRaw()
        val vm = createVm(5)

        vm.events.test {
            // Act
            vm.onBackRequested()
            advanceUntilIdle()

            // Assert
            assertEquals(StorageEditEvent.Exit, awaitItem())
        }
    }

    private fun existingRaw() = JsonObject(
        mapOf(
            "id" to JsonPrimitive(5),
            "driver" to JsonPrimitive("Local"),
            "mount_path" to JsonPrimitive("/old"),
            "order" to JsonPrimitive(2),
            "unknown_field" to JsonPrimitive("keep"),
            "addition" to JsonPrimitive("""{"root_folder_path":"/srv","show_hidden":true}"""),
        ),
    )
}
