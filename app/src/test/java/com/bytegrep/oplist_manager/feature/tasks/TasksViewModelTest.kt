package com.bytegrep.oplist_manager.feature.tasks

import android.content.Context
import androidx.work.Data
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.cash.turbine.test
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<TaskRepository>(relaxUnitFun = true)
    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val workInfos = MutableStateFlow<List<WorkInfo>>(emptyList())

    private val copyTask = TaskInfo(id = "c1", name = "复制 a", state = TaskState.RUNNING, startTime = "2026-01-02T00:00:00Z")
    private val offlineTask = TaskInfo(id = "o1", name = "下载 b", state = TaskState.PENDING, startTime = "2026-01-03T00:00:00Z")
    private val doneTask = TaskInfo(id = "d1", name = "复制 c", state = TaskState.SUCCEEDED, startTime = "2026-01-01T00:00:00Z")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.getWorkInfosByTagFlow("upload") } returns workInfos
        coEvery { repository.undone(any()) } returns emptyList()
        coEvery { repository.undone(TaskType.COPY) } returns listOf(copyTask)
        coEvery { repository.undone(TaskType.OFFLINE_DOWNLOAD) } returns listOf(offlineTask)
        coEvery { repository.done(any()) } returns emptyList()
        coEvery { repository.done(TaskType.COPY) } returns listOf(doneTask)
    }

    @After
    fun tearDown() {
        unmockkObject(WorkManager.Companion)
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): TasksViewModel =
        TasksViewModel(repository, context).also { vm ->
            backgroundScope.launch { vm.state.collect {} }
            advanceUntilIdle()
        }

    // region 加载

    @Test
    fun `init loads undone and done tasks of every type sorted by start time`() = runTest(dispatcher) {
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals(
            listOf(TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask), TypedTask(TaskType.COPY, copyTask)),
            state.undone,
        )
        assertEquals(listOf(TypedTask(TaskType.COPY, doneTask)), state.done)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.partialError)
        TaskType.entries.forEach { type ->
            coVerify(exactly = 1) { repository.undone(type) }
            coVerify(exactly = 1) { repository.done(type) }
        }
    }

    @Test
    fun `failure of some types shows partial error and keeps other results`() = runTest(dispatcher) {
        coEvery { repository.undone(TaskType.MOVE) } throws AppException.Server(500, "boom")
        coEvery { repository.done(TaskType.MOVE) } throws AppException.Server(500, "boom")

        val vm = createViewModel()

        val state = vm.state.value
        assertEquals("移动：boom", state.partialError)
        assertNull(state.error)
        assertEquals(2, state.undone.size)
    }

    @Test
    fun `failure of all types shows full error`() = runTest(dispatcher) {
        coEvery { repository.undone(any()) } throws AppException.Network("网络连接失败")
        coEvery { repository.done(any()) } throws AppException.Network("网络连接失败")

        val vm = createViewModel()

        val state = vm.state.value
        assertEquals("上传：网络连接失败", state.error)
        assertNull(state.partialError)
        assertTrue(state.undone.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `pull refresh shows refreshing indicator`() = runTest(dispatcher) {
        val vm = createViewModel()
        val gate = CompletableDeferred<List<TaskInfo>>()
        coEvery { repository.undone(TaskType.COPY) } coAnswers { gate.await() }

        vm.refresh(pull = true)
        runCurrent()
        assertTrue(vm.state.value.isRefreshing)
        assertFalse(vm.state.value.isLoading)

        gate.complete(listOf(copyTask))
        advanceUntilIdle()
        assertFalse(vm.state.value.isRefreshing)
    }

    // endregion

    // region 筛选

    @Test
    fun `selecting a type filters visible lists and summary`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.selectType(TaskType.COPY)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf(TypedTask(TaskType.COPY, copyTask)), state.visibleUndone)
        assertEquals(listOf(TypedTask(TaskType.COPY, doneTask)), state.visibleDone)
        assertEquals(TaskSummary(running = 1, succeeded = 1), state.summary)
        assertEquals(2, state.undone.size)
    }

    @Test
    fun `clearing type filter shows all tasks`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectType(TaskType.COPY)

        vm.selectType(null)
        advanceUntilIdle()

        assertEquals(2, vm.state.value.visibleUndone.size)
        assertEquals(TaskSummary(running = 1, pending = 1, succeeded = 1), vm.state.value.summary)
    }

    @Test
    fun `select tab updates state`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.selectTab(TaskTab.DONE)
        advanceUntilIdle()

        assertEquals(TaskTab.DONE, vm.state.value.tab)
    }

    // endregion

    // region 单任务操作

    @Test
    fun `cancel calls action api emits message and refreshes`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.events.test {
            vm.cancel(TypedTask(TaskType.COPY, copyTask))
            advanceUntilIdle()
            assertEquals("已取消任务", awaitItem())
        }
        coVerify(exactly = 1) { repository.action(TaskType.COPY, "cancel", "c1") }
        coVerify(exactly = 2) { repository.undone(TaskType.COPY) }
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `retry and delete use matching actions`() = runTest(dispatcher) {
        val vm = createViewModel()
        val task = TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask)

        vm.events.test {
            vm.retry(task)
            advanceUntilIdle()
            assertEquals("已重新执行任务", awaitItem())
            vm.delete(task)
            advanceUntilIdle()
            assertEquals("已删除任务", awaitItem())
        }
        coVerify(exactly = 1) { repository.action(TaskType.OFFLINE_DOWNLOAD, "retry", "o1") }
        coVerify(exactly = 1) { repository.action(TaskType.OFFLINE_DOWNLOAD, "delete", "o1") }
    }

    @Test
    fun `action failure emits error message and clears busy`() = runTest(dispatcher) {
        coEvery { repository.action(any(), any(), any()) } throws AppException.Server(500, "任务不存在")
        val vm = createViewModel()

        vm.events.test {
            vm.cancel(TypedTask(TaskType.COPY, copyTask))
            advanceUntilIdle()
            assertEquals("操作失败：任务不存在", awaitItem())
        }
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `second action while busy is ignored`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.action(TaskType.COPY, "cancel", "c1") } coAnswers { gate.await() }
        val vm = createViewModel()

        vm.cancel(TypedTask(TaskType.COPY, copyTask))
        runCurrent()
        assertTrue(vm.state.value.isBusy)
        vm.delete(TypedTask(TaskType.COPY, copyTask))
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.action(any(), "delete", any()) }
    }

    // endregion

    // region 批量操作

    @Test
    fun `bulk without type filter runs for every type`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.events.test {
            vm.bulk(TaskBulkAction.CLEAR_DONE)
            advanceUntilIdle()
            assertEquals("已清除全部已结束任务", awaitItem())
        }
        TaskType.entries.forEach { type ->
            coVerify(exactly = 1) { repository.bulk(type, "clear_done") }
        }
    }

    @Test
    fun `bulk with type filter runs only for selected type`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectType(TaskType.COPY)

        vm.bulk(TaskBulkAction.CLEAR_SUCCEEDED)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.bulk(TaskType.COPY, "clear_succeeded") }
        coVerify(exactly = 1) { repository.bulk(any(), any()) }
    }

    @Test
    fun `bulk partial failure lists failed types and still refreshes`() = runTest(dispatcher) {
        coEvery { repository.bulk(TaskType.UPLOAD, any()) } throws AppException.Server(500, "不支持")
        val vm = createViewModel()

        vm.events.test {
            vm.bulk(TaskBulkAction.RETRY_FAILED)
            advanceUntilIdle()
            assertEquals("重试失败项部分失败：上传：不支持", awaitItem())
        }
        coVerify(exactly = 2) { repository.done(TaskType.COPY) }
        assertFalse(vm.state.value.isBusy)
    }

    // endregion

    // region 多选

    @Test
    fun `long press enters selection and toggling last item exits`() = runTest(dispatcher) {
        val vm = createViewModel()
        val copy = TypedTask(TaskType.COPY, copyTask)

        vm.enterSelection(copy)
        runCurrent()
        assertEquals(setOf(copy.key), vm.state.value.selectedKeys)
        assertTrue(vm.state.value.selectionMode)

        vm.toggleSelection(copy)
        runCurrent()
        assertFalse(vm.state.value.selectionMode)
    }

    @Test
    fun `toggle select all covers visible tasks of current tab`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.enterSelection(TypedTask(TaskType.COPY, copyTask))
        runCurrent()

        vm.toggleSelectAll()
        runCurrent()
        assertEquals(setOf("copy:c1", "offline_download:o1"), vm.state.value.selectedKeys)
        assertTrue(vm.state.value.allSelected)

        vm.toggleSelectAll()
        runCurrent()
        assertTrue(vm.state.value.selectedKeys.isEmpty())
    }

    @Test
    fun `switching tab or type clears selection`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.enterSelection(TypedTask(TaskType.COPY, copyTask))
        runCurrent()
        vm.selectTab(TaskTab.DONE)
        runCurrent()
        assertFalse(vm.state.value.selectionMode)

        vm.enterSelection(TypedTask(TaskType.COPY, doneTask))
        runCurrent()
        vm.selectType(TaskType.UPLOAD)
        runCurrent()
        assertFalse(vm.state.value.selectionMode)
    }

    @Test
    fun `batch groups selected tasks by type and exits selection`() = runTest(dispatcher) {
        coEvery { repository.batch(any(), any(), any()) } returns emptyMap()
        val vm = createViewModel()
        vm.enterSelection(TypedTask(TaskType.COPY, copyTask))
        runCurrent()
        vm.toggleSelection(TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask))
        runCurrent()

        vm.events.test {
            vm.batch(TaskBatchAction.CANCEL)
            advanceUntilIdle()
            assertEquals("已取消 2 项", awaitItem())
        }
        coVerify(exactly = 1) { repository.batch(TaskType.COPY, "cancel_some", listOf("c1")) }
        coVerify(exactly = 1) { repository.batch(TaskType.OFFLINE_DOWNLOAD, "cancel_some", listOf("o1")) }
        assertFalse(vm.state.value.selectionMode)
        coVerify(exactly = 2) { repository.undone(TaskType.COPY) }
    }

    @Test
    fun `batch reports per task failures and request failures`() = runTest(dispatcher) {
        coEvery { repository.batch(TaskType.COPY, any(), any()) } returns mapOf("c1" to "task not found")
        coEvery { repository.batch(TaskType.OFFLINE_DOWNLOAD, any(), any()) } throws AppException.Server(500, "不支持")
        val vm = createViewModel()
        vm.enterSelection(TypedTask(TaskType.COPY, copyTask))
        runCurrent()
        vm.toggleSelection(TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask))
        runCurrent()

        vm.events.test {
            vm.batch(TaskBatchAction.RETRY)
            advanceUntilIdle()
            assertEquals("重试失败 2 项：task not found；离线下载：不支持", awaitItem())
        }
        assertFalse(vm.state.value.isBusy)
    }

    @Test
    fun `cancelAll cancels every visible undone task grouped by type`() = runTest(dispatcher) {
        coEvery { repository.batch(any(), any(), any()) } returns emptyMap()
        val vm = createViewModel()

        vm.events.test {
            vm.cancelAll()
            advanceUntilIdle()
            assertEquals("已取消 2 项", awaitItem())
        }
        coVerify(exactly = 1) { repository.batch(TaskType.COPY, "cancel_some", listOf("c1")) }
        coVerify(exactly = 1) { repository.batch(TaskType.OFFLINE_DOWNLOAD, "cancel_some", listOf("o1")) }
    }

    @Test
    fun `cancelAll respects type filter`() = runTest(dispatcher) {
        coEvery { repository.batch(any(), any(), any()) } returns emptyMap()
        val vm = createViewModel()
        vm.selectType(TaskType.COPY)
        runCurrent()

        vm.cancelAll()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.batch(TaskType.COPY, "cancel_some", listOf("c1")) }
        coVerify(exactly = 1) { repository.batch(any(), any(), any()) }
    }

    @Test
    fun `selection drops tasks that disappear after refresh`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.enterSelection(TypedTask(TaskType.COPY, copyTask))
        runCurrent()
        vm.toggleSelection(TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask))
        runCurrent()
        coEvery { repository.undone(TaskType.COPY) } returns emptyList()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(setOf("offline_download:o1"), vm.state.value.selectedKeys)
    }

    // endregion

    // region 轮询

    @Test
    fun `polling refetches undone list and done list when a task finishes`() = runTest(dispatcher) {
        val vm = createViewModel()
        coEvery { repository.undone(TaskType.COPY) } returns emptyList()

        vm.startPolling()
        advanceTimeBy(3_001)
        runCurrent()
        vm.stopPolling()

        assertEquals(listOf(TypedTask(TaskType.OFFLINE_DOWNLOAD, offlineTask)), vm.state.value.undone)
        coVerify(exactly = 2) { repository.undone(TaskType.COPY) }
        coVerify(exactly = 2) { repository.done(TaskType.COPY) }
    }

    @Test
    fun `polling does not refetch done list when undone set is unchanged`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.startPolling()
        advanceTimeBy(3_001)
        runCurrent()
        vm.stopPolling()

        coVerify(exactly = 2) { repository.undone(TaskType.COPY) }
        coVerify(exactly = 1) { repository.done(TaskType.COPY) }
    }

    @Test
    fun `polling skips when done tab is selected`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.selectTab(TaskTab.DONE)

        vm.startPolling()
        advanceTimeBy(6_001)
        runCurrent()
        vm.stopPolling()

        coVerify(exactly = 1) { repository.undone(TaskType.COPY) }
    }

    @Test
    fun `polling failure of all types keeps previous list`() = runTest(dispatcher) {
        val vm = createViewModel()
        coEvery { repository.undone(any()) } throws AppException.Network("网络连接失败")

        vm.startPolling()
        advanceTimeBy(3_001)
        runCurrent()
        vm.stopPolling()

        assertEquals(2, vm.state.value.undone.size)
        assertNull(vm.state.value.error)
    }

    // endregion

    // region 本机上传

    @Test
    fun `local uploads exclude succeeded and cancelled work`() = runTest(dispatcher) {
        val running = WorkInfo(
            id = UUID.randomUUID(),
            state = WorkInfo.State.RUNNING,
            tags = setOf("upload"),
            progress = Data.Builder().putString("name", "a.mp4").putInt("progress", 50).build(),
        )
        val succeeded = WorkInfo(id = UUID.randomUUID(), state = WorkInfo.State.SUCCEEDED, tags = setOf("upload"))
        val vm = createViewModel()

        workInfos.value = listOf(running, succeeded)
        advanceUntilIdle()

        val uploads = vm.state.value.localUploads
        assertEquals(1, uploads.size)
        assertEquals("a.mp4", uploads.single().name)
        assertEquals(0.5f, uploads.single().progress)
        assertEquals("上传中", uploads.single().stateLabel)
    }

    @Test
    fun `cancel local upload cancels work and emits message`() = runTest(dispatcher) {
        val vm = createViewModel()
        val id = UUID.randomUUID()

        vm.events.test {
            vm.cancelLocalUpload(id)
            assertEquals("已取消本机上传", awaitItem())
        }
        verify(exactly = 1) { workManager.cancelWorkById(id) }
    }

    // endregion
}
