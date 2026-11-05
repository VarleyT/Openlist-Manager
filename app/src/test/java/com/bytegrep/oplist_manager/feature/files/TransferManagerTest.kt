package com.bytegrep.oplist_manager.feature.files

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.Operation
import androidx.work.ProgressUpdater
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.data.repository.FileRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import com.google.common.util.concurrent.ListenableFuture
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class TransferManagerTest {

    private val mimeMap = mockk<MimeTypeMap>()

    @Before
    fun setUp() {
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } returns mockk(relaxed = true)
        mockkStatic(MimeTypeMap::class)
        every { MimeTypeMap.getSingleton() } returns mimeMap
        every { mimeMap.getMimeTypeFromExtension(any()) } answers {
            when (firstArg<String>()) {
                "mp4" -> "video/mp4"
                "txt" -> "text/plain"
                else -> null
            }
        }
    }

    @After
    fun tearDown() = unmockkAll()

    private fun uriOf(value: String, lastSegment: String? = null): Uri = mockk<Uri>().also {
        every { it.toString() } returns value
        every { it.lastPathSegment } returns lastSegment
    }

    // ---------------- guessMimeType ----------------

    @Test
    fun `guessMimeType lowercases extension before lookup`() {
        // Act
        val mime = guessMimeType("Movie.MP4")

        // Assert
        assertEquals("video/mp4", mime)
        verify { mimeMap.getMimeTypeFromExtension("mp4") }
    }

    @Test
    fun `guessMimeType returns null without lookup when name has no extension`() {
        // Act
        val mime = guessMimeType("Makefile")

        // Assert
        assertNull(mime)
        verify(exactly = 0) { mimeMap.getMimeTypeFromExtension(any()) }
    }

    // ---------------- DownloadHelper ----------------

    private val server = ServerProfile(id = "s1", name = "S", baseUrl = "http://10.0.0.1:5244/", username = "u")
    private val downloadManager = mockk<DownloadManager>()

    private fun downloadHelper(
        loggedIn: Boolean = true,
        manager: DownloadManager? = downloadManager,
    ): DownloadHelper {
        mockkConstructor(DownloadManager.Request::class)
        every { anyConstructed<DownloadManager.Request>().setTitle(any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().setDescription(any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().setNotificationVisibility(any()) } answers {
            self as DownloadManager.Request
        }
        every { anyConstructed<DownloadManager.Request>().setDestinationInExternalPublicDir(any(), any()) } answers {
            self as DownloadManager.Request
        }
        every { anyConstructed<DownloadManager.Request>().setMimeType(any()) } answers { self as DownloadManager.Request }
        every { anyConstructed<DownloadManager.Request>().addRequestHeader(any(), any()) } answers {
            self as DownloadManager.Request
        }
        every { downloadManager.enqueue(any()) } returns 42L

        val context = mockk<Context>()
        every { context.getSystemService(DownloadManager::class.java) } returns manager
        val session = SessionManager(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        if (loggedIn) session.activate(server, "tok", null)
        return DownloadHelper(context, session)
    }

    @Test
    fun `download to current server attaches raw token authorization header`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        val id = helper.enqueue("http://10.0.0.1:5244/d/movie.mp4?sign=x", "movie.mp4")

        // Assert
        assertEquals(42L, id)
        verify { anyConstructed<DownloadManager.Request>().addRequestHeader("Authorization", "tok") }
        verify { anyConstructed<DownloadManager.Request>().setMimeType("video/mp4") }
    }

    @Test
    fun `download from other origin does not leak token`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        helper.enqueue("https://cdn.example.com/movie.mp4", "movie.mp4")

        // Assert
        verify(exactly = 0) { anyConstructed<DownloadManager.Request>().addRequestHeader(any(), any()) }
        verify { downloadManager.enqueue(any()) }
    }

    @Test
    fun `download from same host but different port does not attach token`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        helper.enqueue("http://10.0.0.1:8080/movie.mp4", "movie.mp4")

        // Assert
        verify(exactly = 0) { anyConstructed<DownloadManager.Request>().addRequestHeader(any(), any()) }
    }

    @Test
    fun `download without session sends no authorization header`() {
        // Arrange
        val helper = downloadHelper(loggedIn = false)

        // Act
        helper.enqueue("http://10.0.0.1:5244/d/a.txt", "a.txt")

        // Assert
        verify(exactly = 0) { anyConstructed<DownloadManager.Request>().addRequestHeader(any(), any()) }
    }

    @Test
    fun `download file name is sanitized for title and destination`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        helper.enqueue("http://10.0.0.1:5244/d/x", "a/b:c?.txt")

        // Assert
        verify { anyConstructed<DownloadManager.Request>().setTitle("a_b_c_.txt") }
        verify { anyConstructed<DownloadManager.Request>().setDestinationInExternalPublicDir(any(), "OpenList/a_b_c_.txt") }
        verify { anyConstructed<DownloadManager.Request>().setMimeType("text/plain") }
    }

    @Test
    fun `download without recognizable extension skips mime type`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        helper.enqueue("http://10.0.0.1:5244/d/README", "README")

        // Assert
        verify(exactly = 0) { anyConstructed<DownloadManager.Request>().setMimeType(any()) }
    }

    @Test
    fun `download url is normalized by HttpUrl before handing to DownloadManager`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        helper.enqueue("http://10.0.0.1:5244/d/a b.mp4", "a b.mp4")

        // Assert
        verify { Uri.parse("http://10.0.0.1:5244/d/a%20b.mp4") }
    }

    @Test
    fun `download with invalid url fails before touching DownloadManager`() {
        // Arrange
        val helper = downloadHelper()

        // Act
        val error = runCatching { helper.enqueue("", "a.txt") }.exceptionOrNull()

        // Assert
        assertTrue(error is AppException.Unknown)
        assertEquals("该文件没有可用的下载链接", error?.message)
        verify(exactly = 0) { downloadManager.enqueue(any()) }
    }

    @Test
    fun `download fails with friendly message when system download service is missing`() {
        // Arrange
        val helper = downloadHelper(manager = null)

        // Act
        val error = runCatching { helper.enqueue("http://10.0.0.1:5244/d/a.txt", "a.txt") }.exceptionOrNull()

        // Assert
        assertTrue(error is AppException.Unknown)
        assertEquals("系统下载服务不可用", error?.message)
    }

    @Test
    fun `download manager errors are mapped to AppException with cause`() {
        // Arrange
        val helper = downloadHelper()
        val cases = listOf(
            IllegalArgumentException("bad uri") to "无法创建下载任务：bad uri",
            IllegalStateException("no dir") to "无法写入下载目录：no dir",
            SecurityException("denied") to "没有权限创建下载任务",
        )

        cases.forEach { (thrown, expected) ->
            every { downloadManager.enqueue(any()) } throws thrown

            // Act
            val error = runCatching { helper.enqueue("http://10.0.0.1:5244/d/a.txt", "a.txt") }.exceptionOrNull()

            // Assert
            assertTrue("$thrown", error is AppException.Unknown)
            assertEquals(expected, error?.message)
            assertSame(thrown, error?.cause)
        }
    }

    // ---------------- UploadScheduler ----------------

    private val workManager = mockk<WorkManager>(relaxed = true)
    private val resolver = mockk<ContentResolver>(relaxed = true)
    private val enqueued = mutableListOf<WorkRequest>()

    private fun scheduler(): UploadScheduler {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.enqueue(capture(enqueued)) } returns mockk<Operation>(relaxed = true)
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        return UploadScheduler(context)
    }

    private fun nameCursor(name: String?): Cursor = mockk<Cursor>(relaxed = true).also {
        every { it.getColumnIndex(any()) } returns if (name == null) -1 else 0
        every { it.moveToFirst() } returns true
        every { it.getString(0) } returns name
    }

    @Test
    fun `enqueue builds one tagged work request per file with server and target input`() {
        // Arrange
        val scheduler = scheduler()
        val uri = uriOf("content://docs/1")
        every { resolver.query(uri, any(), any(), any(), any()) } returns nameCursor("a.txt")

        // Act
        val count = scheduler.enqueue(listOf(uri), targetDir = "/dst", serverId = "s1")

        // Assert
        assertEquals(1, count)
        val request = enqueued.single()
        assertTrue(request.tags.containsAll(listOf(UploadScheduler.TAG, "upload-server:s1")))
        val input = request.workSpec.input
        assertEquals("s1", input.getString(UploadWorker.KEY_SERVER_ID))
        assertEquals("content://docs/1", input.getString(UploadWorker.KEY_URI))
        assertEquals("/dst", input.getString(UploadWorker.KEY_TARGET_DIR))
        assertEquals("a.txt", input.getString(UploadWorker.KEY_NAME))
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertEquals(UploadWorker::class.java.name, request.workSpec.workerClassName)
    }

    @Test
    fun `enqueue requests persistable read permission and continues when it is denied`() {
        // Arrange
        val scheduler = scheduler()
        val uri = uriOf("content://docs/1")
        every { resolver.takePersistableUriPermission(uri, any()) } throws SecurityException("not persistable")
        every { resolver.query(uri, any(), any(), any(), any()) } returns nameCursor("a.txt")

        // Act
        val count = scheduler.enqueue(listOf(uri), "/", "s1")

        // Assert
        assertEquals(1, count)
        verify { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    @Test
    fun `enqueue falls back to last path segment when display name is unavailable`() {
        // Arrange
        val scheduler = scheduler()
        val noCursor = uriOf("content://docs/primary%3ADownload%2Fb.mp4", lastSegment = "primary:Download/b.mp4")
        val noColumn = uriOf("content://docs/2", lastSegment = "c.txt")
        val queryFails = uriOf("content://docs/3", lastSegment = "d.txt")
        every { resolver.query(noCursor, any(), any(), any(), any()) } returns null
        every { resolver.query(noColumn, any(), any(), any(), any()) } returns nameCursor(null)
        every { resolver.query(queryFails, any(), any(), any(), any()) } throws SecurityException("revoked")

        // Act
        val count = scheduler.enqueue(listOf(noCursor, noColumn, queryFails), "/", "s1")

        // Assert
        assertEquals(3, count)
        assertEquals(
            listOf("b.mp4", "c.txt", "d.txt"),
            enqueued.map { it.workSpec.input.getString(UploadWorker.KEY_NAME) },
        )
    }

    @Test
    fun `enqueue skips files whose name cannot be determined and counts only queued ones`() {
        // Arrange
        val scheduler = scheduler()
        val good = uriOf("content://docs/1")
        val nameless = uriOf("content://docs/2", lastSegment = null)
        every { resolver.query(good, any(), any(), any(), any()) } returns nameCursor("a.txt")
        every { resolver.query(nameless, any(), any(), any(), any()) } returns null

        // Act
        val count = scheduler.enqueue(listOf(good, nameless), "/", "s1")

        // Assert
        assertEquals(1, count)
        assertEquals(1, enqueued.size)
    }

    @Test
    fun `cancelFor cancels only uploads tagged with that server`() {
        // Arrange
        val scheduler = scheduler()

        // Act
        scheduler.cancelFor("s2")

        // Assert
        verify { workManager.cancelAllWorkByTag("upload-server:s2") }
        verify(exactly = 0) { workManager.cancelAllWorkByTag(UploadScheduler.TAG) }
    }

    @Test
    fun `observe watches all uploads by common tag`() {
        // Arrange
        val scheduler = scheduler()
        val flow = emptyFlow<List<androidx.work.WorkInfo>>()
        every { workManager.getWorkInfosByTagFlow(UploadScheduler.TAG) } returns flow

        // Act
        val observed = scheduler.observe()

        // Assert
        assertSame(flow, observed)
    }

    // ---------------- UploadWorker ----------------

    private val fileRepository = mockk<FileRepository>()
    private val progressData = mutableListOf<Data>()

    private fun worker(input: Data, attempt: Int = 0, size: Long? = 100L, mime: String? = null): UploadWorker {
        val progressUpdater = mockk<ProgressUpdater>()
        every { progressUpdater.updateProgress(any(), any(), capture(progressData)) } returns
            mockk<ListenableFuture<Void>>(relaxed = true)
        val params = mockk<WorkerParameters>()
        every { params.id } returns UUID.randomUUID()
        every { params.inputData } returns input
        every { params.runAttemptCount } returns attempt
        every { params.progressUpdater } returns progressUpdater
        every { params.foregroundUpdater } throws IllegalStateException("no foreground in unit test")

        every { resolver.getType(any()) } returns mime
        every { resolver.query(any(), any(), any(), any(), any()) } returns size?.let { s ->
            mockk<Cursor>(relaxed = true).also {
                every { it.getColumnIndex(any()) } returns 0
                every { it.moveToFirst() } returns true
                every { it.isNull(0) } returns false
                every { it.getLong(0) } returns s
            }
        }
        val context = mockk<Context>()
        every { context.contentResolver } returns resolver
        // 返回 null 让通知相关代码短路（NotificationCompat 依赖真实 Android 实现）
        every { context.getSystemService(NotificationManager::class.java) } returns null
        return UploadWorker(context, params, fileRepository)
    }

    private fun input(
        serverId: String? = "s1",
        uri: String? = "content://docs/1",
        targetDir: String? = "/dst",
        name: String? = "a.txt",
    ): Data = Data.Builder().apply {
        serverId?.let { putString(UploadWorker.KEY_SERVER_ID, it) }
        uri?.let { putString(UploadWorker.KEY_URI, it) }
        targetDir?.let { putString(UploadWorker.KEY_TARGET_DIR, it) }
        name?.let { putString(UploadWorker.KEY_NAME, it) }
    }.build()

    private fun stubUpload(block: (onProgress: (Long) -> Unit) -> Unit = {}) {
        coEvery { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) } answers {
            block(arg(6))
        }
    }

    @Test
    fun `successful upload targets pinned server and returns name and dir`() = runTest {
        // Arrange
        stubUpload()
        val worker = worker(input(), size = 2048L)

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(workDataOf(UploadWorker.KEY_NAME to "a.txt", UploadWorker.KEY_TARGET_DIR to "/dst")), result)
        coVerify {
            fileRepository.upload(
                serverId = "s1", dir = "/dst", name = "a.txt", size = 2048L,
                mimeType = "text/plain", openStream = any(), onProgress = any(),
            )
        }
    }

    @Test
    fun `upload prefers content resolver mime type over extension guess`() = runTest {
        // Arrange
        stubUpload()
        val worker = worker(input(name = "a.txt"), mime = "application/x-custom")

        // Act
        worker.doWork()

        // Assert
        coVerify { fileRepository.upload(any(), any(), any(), any(), "application/x-custom", any(), any()) }
    }

    @Test
    fun `upload uses unknown size when provider does not report it`() = runTest {
        // Arrange
        stubUpload()
        val worker = worker(input(), size = null)

        // Act
        worker.doWork()

        // Assert
        coVerify { fileRepository.upload(any(), any(), any(), size = -1L, any(), any(), any()) }
    }

    @Test
    fun `missing target dir defaults to root`() = runTest {
        // Arrange
        stubUpload()
        val worker = worker(input(targetDir = null))

        // Act
        val result = worker.doWork()

        // Assert
        coVerify { fileRepository.upload(any(), dir = "/", any(), any(), any(), any(), any()) }
        assertEquals("/", (result as ListenableWorker.Result.Success).outputData.getString(UploadWorker.KEY_TARGET_DIR))
    }

    @Test
    fun `legacy work without server id fails without uploading`() = runTest {
        // Arrange
        stubUpload()
        val worker = worker(input(serverId = null))

        // Act
        val result = worker.doWork()

        // Assert
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("上传任务缺少服务器信息，请重新上传", (result as ListenableWorker.Result.Failure).outputData.getString(UploadWorker.KEY_ERROR))
        coVerify(exactly = 0) { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `missing uri or name fails with specific message`() = runTest {
        // Arrange
        stubUpload()

        // Act
        val noUri = worker(input(uri = null)).doWork() as ListenableWorker.Result.Failure
        val noName = worker(input(name = null)).doWork() as ListenableWorker.Result.Failure

        // Assert
        assertEquals("缺少文件参数", noUri.outputData.getString(UploadWorker.KEY_ERROR))
        assertEquals("缺少文件名", noName.outputData.getString(UploadWorker.KEY_ERROR))
        coVerify(exactly = 0) { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `network error is retried while attempts remain`() = runTest {
        // Arrange
        coEvery { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) } throws
            AppException.Network("连接超时")

        // Act
        val results = (0..2).map { attempt -> worker(input(), attempt = attempt).doWork() }

        // Assert
        results.forEach { assertEquals(ListenableWorker.Result.retry(), it) }
    }

    @Test
    fun `network error fails after max retries with message name and dir`() = runTest {
        // Arrange
        coEvery { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) } throws
            AppException.Network("连接超时")

        // Act
        val result = worker(input(), attempt = 3).doWork()

        // Assert
        val data = (result as ListenableWorker.Result.Failure).outputData
        assertEquals("连接超时", data.getString(UploadWorker.KEY_ERROR))
        assertEquals("a.txt", data.getString(UploadWorker.KEY_NAME))
        assertEquals("/dst", data.getString(UploadWorker.KEY_TARGET_DIR))
    }

    @Test
    fun `non network errors fail immediately without retry`() = runTest {
        // Arrange
        val errors = listOf(
            AppException.Server(403, "无权限写入"),
            AppException.Unauthorized(),
            IllegalStateException("boom"),
        )

        errors.forEach { error ->
            coEvery { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) } throws error

            // Act
            val result = worker(input(), attempt = 0).doWork()

            // Assert
            assertTrue("$error", result is ListenableWorker.Result.Failure)
            assertEquals(error.message, (result as ListenableWorker.Result.Failure).outputData.getString(UploadWorker.KEY_ERROR))
        }
    }

    @Test
    fun `cancellation is propagated instead of reported as failure`() = runTest {
        // Arrange
        coEvery { fileRepository.upload(any(), any(), any(), any(), any(), any(), any()) } throws
            CancellationException("stopped")
        val worker = worker(input())

        // Act
        try {
            worker.doWork()
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            // Assert
            assertEquals("stopped", e.message)
        }
    }

    @Test
    fun `open stream throws IOException when content resolver cannot open file`() = runTest {
        // Arrange
        val openStream = slot<() -> InputStream>()
        coEvery { fileRepository.upload(any(), any(), any(), any(), any(), capture(openStream), any()) } returns Unit
        every { resolver.openInputStream(any()) } returns null
        worker(input(name = "a.txt")).doWork()

        // Act
        val error = runCatching { openStream.captured.invoke() }.exceptionOrNull()

        // Assert
        assertTrue(error is IOException)
        assertEquals("无法读取文件：a.txt", error?.message)
    }

    @Test
    fun `progress reports are throttled to one per interval`() = runTest {
        // Arrange
        stubUpload { onProgress ->
            onProgress(10)
            onProgress(50)
            onProgress(90)
            Thread.sleep(PROGRESS_INTERVAL_MS + 100)
            onProgress(100)
        }
        val worker = worker(input(name = "a.txt"), size = 100L)

        // Act
        worker.doWork()

        // Assert
        assertEquals(listOf(10, 100), progressData.map { it.getInt(UploadWorker.KEY_PROGRESS, -1) })
        assertTrue(progressData.all { it.getString(UploadWorker.KEY_PROGRESS_NAME) == "a.txt" })
    }

    @Test
    fun `progress percent is zero for unknown size and clamped to 100`() = runTest {
        // Arrange
        stubUpload { onProgress -> onProgress(500) }

        // Act
        worker(input(), size = null).doWork()
        worker(input(), size = 100L).doWork()

        // Assert
        assertEquals(listOf(0, 100), progressData.map { it.getInt(UploadWorker.KEY_PROGRESS, -1) })
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
