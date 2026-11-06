package com.bytegrep.oplist_manager.feature.files

import android.net.Uri
import androidx.work.Data
import androidx.work.WorkInfo
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.AppSettings
import com.bytegrep.oplist_manager.core.datastore.AppSettingsStore
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.network.PageData
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsListData
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.FsSearchItem
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.model.UserPermission
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.repository.FileRepository
import com.bytegrep.oplist_manager.data.repository.TaskRepository
import com.bytegrep.oplist_manager.data.session.Session
import com.bytegrep.oplist_manager.data.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
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
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class FilesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val repository = mockk<FileRepository>()
    private val session = mockk<SessionManager>()
    private val settingsStore = mockk<AppSettingsStore>(relaxed = true)
    private val uploadScheduler = mockk<UploadScheduler>()
    private val downloadHelper = mockk<DownloadHelper>()
    private val taskRepository = mockk<TaskRepository>()

    private val currentUser = MutableStateFlow<UserDto?>(UserDto(id = 1, permission = 0))
    private val sessionFlow = MutableStateFlow<Session?>(null)
    private val uploads = MutableStateFlow<List<WorkInfo>>(emptyList())

    private val rootItems = listOf(
        FsObject(name = "b.txt", size = 10),
        FsObject(name = "Docs", isDir = true),
        FsObject(name = "a.txt", size = 5),
    )
    private val docsItems = listOf(FsObject(name = "readme.md"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { settingsStore.settings } returns flowOf(AppSettings())
        every { session.currentUser } returns currentUser
        every { session.session } returns sessionFlow
        every { uploadScheduler.observe() } returns uploads
        coEvery { repository.list("/", any()) } returns FsListData(content = rootItems)
        coEvery { repository.list("/Docs", any()) } returns FsListData(content = docsItems)
        coEvery { taskRepository.undone(any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): FilesViewModel =
        FilesViewModel(repository, session, settingsStore, uploadScheduler, downloadHelper, taskRepository).also { advanceUntilIdle() }

    /** 在后台收集一次性消息，返回实时更新的列表 */
    private fun TestScope.collectMessages(vm: FilesViewModel): List<String> {
        val messages = mutableListOf<String>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.messages.toList(messages) }
        return messages
    }

    // ---------------- 加载 ----------------

    @Test
    fun `initial load success shows sorted root items with folders first`() = runTest(dispatcher) {
        val vm = createViewModel()

        val state = vm.state.value
        assertEquals("/", state.currentPath)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(listOf("Docs", "a.txt", "b.txt"), state.items.map { it.name })
    }

    @Test
    fun `initial state is loading before repository returns`() = runTest(dispatcher) {
        val vm = FilesViewModel(repository, session, settingsStore, uploadScheduler, downloadHelper, taskRepository)

        // 尚未推进调度器，列表请求未返回
        assertTrue(vm.state.value.isLoading)
        assertTrue(vm.state.value.items.isEmpty())
    }

    @Test
    fun `initial load failure shows user message as error`() = runTest(dispatcher) {
        coEvery { repository.list("/", any()) } throws AppException.Network("网络连接失败")

        val vm = createViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals("网络连接失败", state.error)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `retry after failure reloads and clears error`() = runTest(dispatcher) {
        coEvery { repository.list("/", any()) } throws AppException.Network("网络连接失败") andThen
            FsListData(content = rootItems)
        val vm = createViewModel()

        vm.retry()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun `list write flag grants write permission even without user bit`() = runTest(dispatcher) {
        coEvery { repository.list("/", any()) } returns FsListData(content = rootItems, write = true)

        val vm = createViewModel()

        assertTrue(vm.state.value.permissions.canWrite)
    }

    @Test
    fun `permissions follow user permission bits`() = runTest(dispatcher) {
        currentUser.value = UserDto(id = 1, permission = 1 shl UserPermission.RENAME.bit)

        val vm = createViewModel()

        val p = vm.state.value.permissions
        assertFalse(p.canWrite)
        assertTrue(p.canRename)
        assertFalse(p.canMove)
        assertFalse(p.canCopy)
        assertFalse(p.canRemove)
    }

    @Test
    fun `unknown user triggers refreshUser and failure does not break browsing`() = runTest(dispatcher) {
        currentUser.value = null
        coEvery { session.refreshUser() } throws AppException.Network("超时")

        val vm = createViewModel()

        coVerify(exactly = 1) { session.refreshUser() }
        assertNull(vm.state.value.error)
        // 用户未知时除写入外按宽松处理
        assertTrue(vm.state.value.permissions.canRemove)
    }

    // ---------------- 导航 ----------------

    @Test
    fun `openDir navigates into child directory`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.openDir("Docs")
        advanceUntilIdle()

        assertEquals("/Docs", vm.state.value.currentPath)
        assertFalse(vm.state.value.isRoot)
        assertEquals(listOf("readme.md"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `navigating clears previous items and selection immediately`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.enterSelection("a.txt")

        vm.openDir("Docs")

        val state = vm.state.value
        assertTrue(state.isLoading)
        assertTrue(state.items.isEmpty())
        assertFalse(state.selectionMode)
        assertTrue(state.selectedNames.isEmpty())
    }

    @Test
    fun `goUp returns to parent directory`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.openDir("Docs")
        advanceUntilIdle()

        vm.goUp()
        advanceUntilIdle()

        assertEquals("/", vm.state.value.currentPath)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun `goUp at root does nothing`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.goUp()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.list(any(), any()) }
        assertEquals("/", vm.state.value.currentPath)
    }

    @Test
    fun `openPath exits search and loads target path`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns PageData(listOf(FsSearchItem("/Docs", "readme.md")), 1)
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.openPath("/Docs")
        advanceUntilIdle()

        assertNull(vm.state.value.search)
        assertEquals("", vm.state.value.searchQuery)
        assertEquals("/Docs", vm.state.value.currentPath)
    }

    // ---------------- 刷新 ----------------

    @Test
    fun `refresh requests server refresh and resets refreshing flag`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.refresh()
        assertTrue(vm.state.value.isRefreshing)
        assertFalse(vm.state.value.isLoading)
        advanceUntilIdle()

        coVerify { repository.list("/", true) }
        assertFalse(vm.state.value.isRefreshing)
    }

    @Test
    fun `refresh failure keeps items and sends message instead of error`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)
        coEvery { repository.list("/", true) } throws AppException.Network("网络连接失败")

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.state.value.error)
        assertEquals(3, vm.state.value.items.size)
        assertFalse(vm.state.value.isRefreshing)
        assertEquals(listOf("刷新失败：网络连接失败"), messages)
    }

    // ---------------- 文件操作 ----------------

    @Test
    fun `createFolder success sends message and reloads list`() = runTest(dispatcher) {
        coEvery { repository.mkdir("/", "New") } returns Unit
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.createFolder("  New ")
        assertTrue(vm.state.value.isOperating)
        advanceUntilIdle()

        coVerify { repository.mkdir("/", "New") }
        coVerify(exactly = 2) { repository.list("/", false) }
        assertEquals(listOf("已创建文件夹 New"), messages)
        assertFalse(vm.state.value.isOperating)
    }

    @Test
    fun `createFolder with invalid name shows validation message without request`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.createFolder("a/b")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.mkdir(any(), any()) }
        assertEquals(listOf("名称不能包含 / 或 \\"), messages)
    }

    @Test
    fun `createFolder failure shows user message and does not reload`() = runTest(dispatcher) {
        coEvery { repository.mkdir(any(), any()) } throws AppException.Server(500, "目录已存在")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.createFolder("Docs")
        advanceUntilIdle()

        assertEquals(listOf("目录已存在"), messages)
        coVerify(exactly = 1) { repository.list(any(), any()) }
        assertFalse(vm.state.value.isOperating)
    }

    @Test
    fun `rename success uses full path and reloads`() = runTest(dispatcher) {
        coEvery { repository.rename(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.openDir("Docs")
        advanceUntilIdle()
        val messages = collectMessages(vm)

        vm.rename(FsObject(name = "readme.md"), "README.md")
        advanceUntilIdle()

        coVerify { repository.rename("/Docs/readme.md", "README.md") }
        coVerify(exactly = 2) { repository.list("/Docs", false) }
        assertEquals(listOf("已重命名为 README.md"), messages)
    }

    @Test
    fun `rename to same name is a no-op`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.rename(FsObject(name = "a.txt"), " a.txt ")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.rename(any(), any()) }
        assertTrue(messages.isEmpty())
        assertFalse(vm.state.value.isOperating)
    }

    @Test
    fun `rename failure shows user message`() = runTest(dispatcher) {
        coEvery { repository.rename(any(), any()) } throws AppException.Forbidden()
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.rename(FsObject(name = "a.txt"), "c.txt")
        advanceUntilIdle()

        assertEquals(listOf("当前账号无权限执行此操作"), messages)
    }

    @Test
    fun `remove success clears selection and reloads`() = runTest(dispatcher) {
        coEvery { repository.remove(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.enterSelection("a.txt")
        vm.toggleSelection("b.txt")
        val messages = collectMessages(vm)

        vm.remove(listOf("a.txt", "b.txt"))
        advanceUntilIdle()

        coVerify { repository.remove("/", listOf("a.txt", "b.txt")) }
        assertEquals(listOf("已删除 2 项"), messages)
        assertFalse(vm.state.value.selectionMode)
        assertTrue(vm.state.value.selectedNames.isEmpty())
        coVerify(exactly = 2) { repository.list("/", false) }
    }

    @Test
    fun `remove failure keeps selection and shows message`() = runTest(dispatcher) {
        coEvery { repository.remove(any(), any()) } throws AppException.Server(500, "删除失败")
        val vm = createViewModel()
        vm.enterSelection("a.txt")
        val messages = collectMessages(vm)

        vm.remove(listOf("a.txt"))
        advanceUntilIdle()

        assertEquals(listOf("删除失败"), messages)
        assertTrue(vm.state.value.selectionMode)
        assertEquals(setOf("a.txt"), vm.state.value.selectedNames)
    }

    @Test
    fun `remove with empty list does nothing`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.remove(emptyList())
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.remove(any(), any()) }
        assertFalse(vm.state.value.isOperating)
    }

    @Test
    fun `operation closes overlay`() = runTest(dispatcher) {
        coEvery { repository.mkdir(any(), any()) } returns Unit
        val vm = createViewModel()
        vm.showOverlay(FilesOverlay.NewFolder)

        vm.createFolder("X")

        assertNull(vm.state.value.overlay)
    }

    // ---------------- 移动 / 复制 ----------------

    @Test
    fun `move via picker submits to chosen directory and reloads`() = runTest(dispatcher) {
        coEvery { repository.dirs("/") } returns listOf("docs", "Archive")
        coEvery { repository.dirs("/Archive") } returns emptyList()
        coEvery { repository.move(any(), any(), any()) } returns Unit
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.openPicker(TransferMode.MOVE, listOf("a.txt"))
        advanceUntilIdle()
        assertEquals(listOf("Archive", "docs"), vm.state.value.picker?.dirs)
        // 仍在源目录时不可确认
        assertFalse(vm.state.value.picker!!.canConfirm)

        vm.pickerOpen("Archive")
        advanceUntilIdle()
        vm.confirmPicker()
        advanceUntilIdle()

        coVerify { repository.move("/", "/Archive", listOf("a.txt")) }
        assertNull(vm.state.value.picker)
        assertEquals(listOf("已移动 1 项 到 /Archive"), messages)
        coVerify(exactly = 2) { repository.list("/", false) }
    }

    @Test
    fun `copy failure is prefixed with operation label`() = runTest(dispatcher) {
        coEvery { repository.dirs(any()) } returns emptyList()
        coEvery { repository.copy(any(), any(), any()) } throws AppException.Server(500, "目标已存在")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.openPicker(TransferMode.COPY, listOf("a.txt", "b.txt"))
        advanceUntilIdle()
        vm.pickerOpen("Docs")
        advanceUntilIdle()
        vm.confirmPicker()
        advanceUntilIdle()

        coVerify { repository.copy("/", "/Docs", listOf("a.txt", "b.txt")) }
        assertEquals(listOf("复制失败：目标已存在"), messages)
        coVerify(exactly = 1) { repository.list(any(), any()) }
    }

    @Test
    fun `copy success reports submitted message`() = runTest(dispatcher) {
        coEvery { repository.dirs(any()) } returns emptyList()
        coEvery { repository.copy(any(), any(), any()) } returns Unit
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.openPicker(TransferMode.COPY, listOf("a.txt", "b.txt"))
        advanceUntilIdle()
        vm.pickerOpen("Docs")
        advanceUntilIdle()
        vm.confirmPicker()
        advanceUntilIdle()

        assertEquals(listOf("已提交复制 2 项 到 /Docs"), messages)
    }

    @Test
    fun `picker dir load failure shows error and pickerUp navigates to parent`() = runTest(dispatcher) {
        coEvery { repository.dirs("/") } returns listOf("Docs")
        coEvery { repository.dirs("/Docs") } throws AppException.Network("网络连接失败")
        val vm = createViewModel()

        vm.openPicker(TransferMode.MOVE, listOf("a.txt"))
        advanceUntilIdle()
        vm.pickerOpen("Docs")
        advanceUntilIdle()
        assertEquals("网络连接失败", vm.state.value.picker?.error)
        assertFalse(vm.state.value.picker!!.isLoading)

        vm.pickerUp()
        advanceUntilIdle()
        assertEquals("/", vm.state.value.picker?.path)
        assertNull(vm.state.value.picker?.error)
    }

    @Test
    fun `confirmPicker while dirs loading is ignored`() = runTest(dispatcher) {
        coEvery { repository.dirs(any()) } returns emptyList()
        val vm = createViewModel()
        vm.openPicker(TransferMode.MOVE, listOf("a.txt"))
        advanceUntilIdle()
        vm.pickerOpen("Docs")
        // 未推进调度器，目录仍在加载

        vm.confirmPicker()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.move(any(), any(), any()) }
    }

    // ---------------- 搜索 ----------------

    @Test
    fun `search loads first page in current directory`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns
            PageData((1..100).map { FsSearchItem("/", "f$it") }, total = 150)
        val vm = createViewModel()
        vm.enterSelection("a.txt")

        vm.onSearchQueryChange("  kw ")
        vm.search()
        assertTrue(vm.state.value.search!!.isLoading)
        assertFalse(vm.state.value.selectionMode)
        advanceUntilIdle()

        val search = vm.state.value.search!!
        assertEquals("kw", search.keywords)
        assertFalse(search.isLoading)
        assertEquals(100, search.results.size)
        assertTrue(search.hasMore)
    }

    @Test
    fun `blank query exits search without request`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.onSearchQueryChange("   ")
        vm.search()
        advanceUntilIdle()

        assertNull(vm.state.value.search)
        coVerify(exactly = 0) { repository.search(any(), any(), any()) }
    }

    @Test
    fun `search failure for disabled index shows friendly message`() = runTest(dispatcher) {
        coEvery { repository.search(any(), any(), any()) } throws AppException.Server(500, "search not available")
        val vm = createViewModel()

        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        val search = vm.state.value.search!!
        assertFalse(search.isLoading)
        assertEquals(searchErrorMessage("search not available"), search.error)
    }

    @Test
    fun `loadMoreSearch requests next page and appends results`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns
            PageData((1..100).map { FsSearchItem("/", "f$it") }, total = 150)
        coEvery { repository.search("/", "kw", 2) } returns
            PageData((101..150).map { FsSearchItem("/", "f$it") }, total = 150)
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.loadMoreSearch()
        assertTrue(vm.state.value.search!!.isLoadingMore)
        advanceUntilIdle()

        val search = vm.state.value.search!!
        assertEquals(2, search.page)
        assertEquals(150, search.results.size)
        assertFalse(search.hasMore)
        assertFalse(search.isLoadingMore)
    }

    @Test
    fun `loadMoreSearch while a page is loading sends only one request`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns
            PageData((1..100).map { FsSearchItem("/", "f$it") }, total = 300)
        coEvery { repository.search("/", "kw", 2) } returns
            PageData((101..200).map { FsSearchItem("/", "f$it") }, total = 300)
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.loadMoreSearch()
        vm.loadMoreSearch()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.search("/", "kw", 2) }
        coVerify(exactly = 0) { repository.search("/", "kw", 3) }
    }

    @Test
    fun `loadMoreSearch without more results does nothing`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns PageData(listOf(FsSearchItem("/", "f1")), total = 1)
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.loadMoreSearch()
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.search(any(), any(), 2) }
    }

    @Test
    fun `loadMoreSearch failure keeps results and exposes loadMoreError`() = runTest(dispatcher) {
        coEvery { repository.search("/", "kw", 1) } returns
            PageData((1..100).map { FsSearchItem("/", "f$it") }, total = 150)
        coEvery { repository.search("/", "kw", 2) } throws AppException.Network("网络连接失败")
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.loadMoreSearch()
        advanceUntilIdle()

        val search = vm.state.value.search!!
        assertEquals(100, search.results.size)
        assertEquals("网络连接失败", search.loadMoreError)
        assertFalse(search.isLoadingMore)
        assertNull(search.error)
    }

    @Test
    fun `new search while loading more discards stale page`() = runTest(dispatcher) {
        coEvery { repository.search("/", "old", 1) } returns
            PageData((1..100).map { FsSearchItem("/", "old$it") }, total = 150)
        val stalePage = CompletableDeferred<PageData<FsSearchItem>>()
        coEvery { repository.search("/", "old", 2) } coAnswers { stalePage.await() }
        coEvery { repository.search("/", "new", 1) } returns PageData(listOf(FsSearchItem("/", "new1")), total = 1)
        val vm = createViewModel()
        vm.onSearchQueryChange("old")
        vm.search()
        advanceUntilIdle()

        vm.loadMoreSearch()
        advanceUntilIdle()
        vm.onSearchQueryChange("new")
        vm.search()
        stalePage.complete(PageData((101..150).map { FsSearchItem("/", "old$it") }, total = 150))
        advanceUntilIdle()

        val search = vm.state.value.search!!
        assertEquals("new", search.keywords)
        assertEquals(listOf("new1"), search.results.map { it.name })
    }

    @Test
    fun `exitSearch clears search state and query`() = runTest(dispatcher) {
        coEvery { repository.search(any(), any(), any()) } returns PageData(emptyList(), 0)
        val vm = createViewModel()
        vm.onSearchQueryChange("kw")
        vm.search()
        advanceUntilIdle()

        vm.exitSearch()

        assertNull(vm.state.value.search)
        assertEquals("", vm.state.value.searchQuery)
    }

    // ---------------- 下载 ----------------

    @Test
    fun `download folder shows not supported message`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.download(FsObject(name = "Docs", isDir = true))
        advanceUntilIdle()

        assertEquals(listOf("暂不支持下载文件夹"), messages)
        coVerify(exactly = 0) { repository.get(any()) }
        verify(exactly = 0) { downloadHelper.enqueue(any(), any()) }
    }

    @Test
    fun `download file enqueues raw url and reports started`() = runTest(dispatcher) {
        coEvery { repository.get("/Docs/readme.md") } returns
            FsGetData(name = "readme.md", rawUrl = "https://host/d/readme.md")
        every { downloadHelper.enqueue(any(), any()) } returns 1L
        val vm = createViewModel()
        vm.openDir("Docs")
        advanceUntilIdle()
        val messages = collectMessages(vm)

        vm.download(FsObject(name = "readme.md"))
        advanceUntilIdle()

        verify { downloadHelper.enqueue("https://host/d/readme.md", "readme.md") }
        assertEquals(listOf("已开始下载 readme.md，可在通知栏查看进度"), messages)
    }

    @Test
    fun `download falls back to item name when server name blank`() = runTest(dispatcher) {
        coEvery { repository.get("/a.txt") } returns FsGetData(name = "", rawUrl = "https://host/d/a.txt")
        every { downloadHelper.enqueue(any(), any()) } returns 1L
        val vm = createViewModel()

        vm.download(FsObject(name = "a.txt"))
        advanceUntilIdle()

        verify { downloadHelper.enqueue("https://host/d/a.txt", "a.txt") }
    }

    @Test
    fun `download failure shows user message`() = runTest(dispatcher) {
        coEvery { repository.get(any()) } returns FsGetData(name = "a.txt", rawUrl = "")
        every { downloadHelper.enqueue(any(), any()) } throws AppException.Unknown("该文件没有可用的下载链接")
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.download(FsObject(name = "a.txt"))
        advanceUntilIdle()

        assertEquals(listOf("下载失败：该文件没有可用的下载链接"), messages)
    }

    // ---------------- 上传 ----------------

    @Test
    fun `upload without session shows not logged in message`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)

        vm.upload(listOf(mockk<Uri>()))
        advanceUntilIdle()

        assertEquals(listOf("尚未登录服务器"), messages)
        verify(exactly = 0) { uploadScheduler.enqueue(any(), any(), any()) }
    }

    @Test
    fun `finished upload in current dir notifies and reloads`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)
        // 首次订阅已发生（空列表）；新完成的任务应提示
        val output = Data.Builder()
            .putString(UploadWorker.KEY_NAME, "new.txt")
            .putString(UploadWorker.KEY_TARGET_DIR, "/")
            .build()
        uploads.value = listOf(WorkInfo(UUID.randomUUID(), WorkInfo.State.SUCCEEDED, emptySet(), output))
        advanceUntilIdle()

        assertEquals(listOf("已上传 new.txt"), messages)
        coVerify(exactly = 2) { repository.list("/", false) }
        assertNull(vm.state.value.upload)
    }

    @Test
    fun `failed upload reports error message`() = runTest(dispatcher) {
        val vm = createViewModel()
        val messages = collectMessages(vm)
        val output = Data.Builder()
            .putString(UploadWorker.KEY_NAME, "big.iso")
            .putString(UploadWorker.KEY_ERROR, "空间不足")
            .build()
        uploads.value = listOf(WorkInfo(UUID.randomUUID(), WorkInfo.State.FAILED, emptySet(), output))
        advanceUntilIdle()

        assertEquals(listOf("上传失败 big.iso：空间不足"), messages)
        coVerify(exactly = 1) { repository.list(any(), any()) }
    }

    // ---------------- 视图 / 多选 ----------------

    @Test
    fun `setGrid updates state and persists setting`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.setGrid(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.isGrid)
        coVerify { settingsStore.setFileGrid(true) }
    }

    @Test
    fun `setGrid with current value does not persist`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.setGrid(false)
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsStore.setFileGrid(any()) }
    }

    // ---------------- 后台任务角标 ----------------

    @Test
    fun `refreshTaskCount counts running and pending tasks across types`() = runTest(dispatcher) {
        val types = TaskType.entries
        coEvery { taskRepository.undone(types[0]) } returns listOf(
            TaskInfo(id = "1", state = TaskState.RUNNING),
            TaskInfo(id = "2", state = TaskState.FAILED),
        )
        coEvery { taskRepository.undone(types[1]) } returns listOf(TaskInfo(id = "3", state = TaskState.PENDING))
        val vm = createViewModel()

        vm.refreshTaskCount()
        advanceUntilIdle()

        assertEquals(2, vm.state.value.runningTasks)
    }

    @Test
    fun `refreshTaskCount keeps previous count when every query fails`() = runTest(dispatcher) {
        coEvery { taskRepository.undone(any()) } returns listOf(TaskInfo(id = "1", state = TaskState.RUNNING))
        val vm = createViewModel()
        vm.refreshTaskCount()
        advanceUntilIdle()
        coEvery { taskRepository.undone(any()) } throws AppException.Network("网络错误")

        vm.refreshTaskCount()
        advanceUntilIdle()

        assertEquals(TaskType.entries.size, vm.state.value.runningTasks)
    }

    @Test
    fun `task polling refreshes count while tasks are running and stops at zero`() = runTest(dispatcher) {
        val first = TaskType.entries.first()
        coEvery { taskRepository.undone(first) } returns listOf(TaskInfo(id = "1", state = TaskState.RUNNING))
        val vm = createViewModel()

        vm.startTaskPolling()
        runCurrent()
        assertEquals(1, vm.state.value.runningTasks)

        // 服务端任务完成：下一轮轮询应把角标清零
        coEvery { taskRepository.undone(first) } returns emptyList()
        advanceTimeBy(TASK_POLL_INTERVAL_MS + 1)
        assertEquals(0, vm.state.value.runningTasks)

        // 数量为 0 后停止轮询，不再请求
        val callsAtZero = TaskType.entries.size * 2
        advanceTimeBy(TASK_POLL_INTERVAL_MS * 3)
        coVerify(exactly = callsAtZero) { taskRepository.undone(any()) }
    }

    @Test
    fun `stopTaskPolling stops further requests`() = runTest(dispatcher) {
        coEvery { taskRepository.undone(any()) } returns listOf(TaskInfo(id = "1", state = TaskState.RUNNING))
        val vm = createViewModel()

        vm.startTaskPolling()
        runCurrent()
        vm.stopTaskPolling()
        advanceTimeBy(TASK_POLL_INTERVAL_MS * 3)

        coVerify(exactly = TaskType.entries.size) { taskRepository.undone(any()) }
    }

    @Test
    fun `refreshTaskCount outside polling fetches only once`() = runTest(dispatcher) {
        coEvery { taskRepository.undone(any()) } returns listOf(TaskInfo(id = "1", state = TaskState.RUNNING))
        val vm = createViewModel()

        vm.refreshTaskCount()
        advanceTimeBy(TASK_POLL_INTERVAL_MS * 3)

        coVerify(exactly = TaskType.entries.size) { taskRepository.undone(any()) }
    }

    @Test
    fun `task badge text caps at 99 plus`() {
        assertEquals("5", taskBadgeText(5))
        assertEquals("99", taskBadgeText(99))
        assertEquals("99+", taskBadgeText(100))
    }

    @Test
    fun `selection toggles and exits when last item deselected`() = runTest(dispatcher) {
        val vm = createViewModel()

        vm.enterSelection("a.txt")
        vm.toggleSelectAll()
        assertTrue(vm.state.value.allSelected)
        vm.toggleSelectAll()
        assertTrue(vm.state.value.selectedNames.isEmpty())
        vm.toggleSelection("a.txt")
        vm.toggleSelection("a.txt")

        assertFalse(vm.state.value.selectionMode)
    }

    @Test
    fun `setSort resorts current items and closes overlay`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.showOverlay(FilesOverlay.SortSheet)

        vm.setSort(FileSort(SortField.SIZE, ascending = false))

        assertEquals(listOf("Docs", "b.txt", "a.txt"), vm.state.value.items.map { it.name })
        assertNull(vm.state.value.overlay)
    }
}
