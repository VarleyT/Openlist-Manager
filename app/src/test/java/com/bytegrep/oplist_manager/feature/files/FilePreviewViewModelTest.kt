package com.bytegrep.oplist_manager.feature.files

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.bytegrep.oplist_manager.navigation.FilePreviewRoute
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsType
import com.bytegrep.oplist_manager.data.repository.FileRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FilePreviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<FileRepository>()
    private val downloadHelper = mockk<DownloadHelper>()

    private val path = "/docs/note.txt"
    private val textFile = FsGetData(name = "note.txt", size = 12, type = FsType.TEXT, rawUrl = "https://host/d/note.txt")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // JVM 单测中 toRoute 依赖 Bundle（被 isReturnDefaultValues 置空），解码出的 path 为 null，
        // 因此直接 stub 掉路由解析，只测 ViewModel 自身逻辑
        mockkStatic(NAV_SAVED_STATE_KT)
        every { any<SavedStateHandle>().toRoute(FilePreviewRoute::class, any()) } returns FilePreviewRoute(path)
    }

    @After
    fun tearDown() {
        unmockkStatic(NAV_SAVED_STATE_KT)
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): FilePreviewViewModel =
        FilePreviewViewModel(SavedStateHandle(), repository, downloadHelper)
            .also { advanceUntilIdle() }

    private fun TestScope.collectMessages(vm: FilePreviewViewModel): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.toList(messages) }
        return messages
    }

    @Test
    fun `path comes from route and state starts loading`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } returns "hello"

        val vm = FilePreviewViewModel(SavedStateHandle(), repository, downloadHelper)

        assertEquals(path, vm.state.value.path)
        assertTrue(vm.state.value.isLoading)
        assertEquals("note.txt", vm.state.value.fileName)
    }

    @Test
    fun `text file loads metadata and content`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(textFile.rawUrl, TEXT_PREVIEW_LIMIT) } returns "hello world"

        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(textFile, state.file)
        assertEquals(FileKind.TEXT, state.kind)
        assertEquals(TextPreview.Content("hello world"), state.text)
    }

    @Test
    fun `text content is requested with preview size limit for truncation`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } returns "x"

        createViewModel()

        coVerify(exactly = 1) { repository.readText(textFile.rawUrl, 512 * 1024L) }
    }

    @Test
    fun `text shows loading while content is being fetched`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }

        val vm = FilePreviewViewModel(SavedStateHandle(), repository, downloadHelper)
        advanceUntilIdle()

        assertFalse(vm.state.value.isLoading)
        assertEquals(TextPreview.Loading, vm.state.value.text)
    }

    @Test
    fun `text read failure shows text error but keeps file info`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } throws AppException.Server(404, "读取文件失败 (HTTP 404)")

        val vm = createViewModel()

        val state = vm.state.value
        assertNull(state.error)
        assertEquals(textFile, state.file)
        assertEquals(TextPreview.Error("读取文件失败 (HTTP 404)"), state.text)
    }

    @Test
    fun `text file at or above size limit is not previewed as text`() = runTest(dispatcher) {
        val big = textFile.copy(size = TEXT_PREVIEW_LIMIT)
        coEvery { repository.get(path) } returns big

        val vm = createViewModel()

        assertNull(vm.state.value.text)
        coVerify(exactly = 0) { repository.readText(any(), any()) }
    }

    @Test
    fun `text file without raw url is not previewed as text`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile.copy(rawUrl = "")

        val vm = createViewModel()

        assertNull(vm.state.value.text)
        coVerify(exactly = 0) { repository.readText(any(), any()) }
    }

    @Test
    fun `non text file sets kind without text preview`() = runTest(dispatcher) {
        val video = FsGetData(name = "movie.mp4", size = 1_000_000_000, type = FsType.VIDEO, rawUrl = "https://host/m.mp4")
        coEvery { repository.get(path) } returns video

        val vm = createViewModel()

        assertEquals(FileKind.VIDEO, vm.state.value.kind)
        assertNull(vm.state.value.text)
        assertEquals("movie.mp4", vm.state.value.fileName)
    }

    @Test
    fun `metadata load failure shows user message`() = runTest(dispatcher) {
        coEvery { repository.get(path) } throws AppException.Network("网络连接失败")

        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("网络连接失败", state.error)
        assertNull(state.file)
    }

    @Test
    fun `reload after failure clears error and shows content`() = runTest(dispatcher) {
        coEvery { repository.get(path) } throws AppException.Network("网络连接失败") andThen textFile
        coEvery { repository.readText(any(), any()) } returns "ok"
        val vm = createViewModel()

        vm.load()
        assertTrue(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
        advanceUntilIdle()

        assertEquals(TextPreview.Content("ok"), vm.state.value.text)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `download enqueues raw url and reports started`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } returns ""
        every { downloadHelper.enqueue(any(), any()) } returns 7L
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.download()
        advanceUntilIdle()

        verify { downloadHelper.enqueue(textFile.rawUrl, "note.txt") }
        assertEquals(listOf("已开始下载，可在通知栏查看进度"), messages)
    }

    @Test
    fun `download failure shows user message`() = runTest(dispatcher) {
        coEvery { repository.get(path) } returns textFile
        coEvery { repository.readText(any(), any()) } returns ""
        every { downloadHelper.enqueue(any(), any()) } throws AppException.Unknown("系统下载服务不可用")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.download()
        advanceUntilIdle()

        assertEquals(listOf("下载失败：系统下载服务不可用"), messages)
    }

    @Test
    fun `download before file loaded does nothing`() = runTest(dispatcher) {
        coEvery { repository.get(path) } throws AppException.Network("网络连接失败")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.download()
        advanceUntilIdle()

        verify(exactly = 0) { downloadHelper.enqueue(any(), any()) }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `notify forwards non blank messages only`() = runTest(dispatcher) {
        coEvery { repository.get(path) } throws AppException.Network("x")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.notify("  ")
        vm.notify("已复制到剪贴板")
        advanceUntilIdle()

        assertEquals(listOf("已复制到剪贴板"), messages)
    }

    private companion object {
        const val NAV_SAVED_STATE_KT = "androidx.navigation.SavedStateHandleKt"
    }
}
