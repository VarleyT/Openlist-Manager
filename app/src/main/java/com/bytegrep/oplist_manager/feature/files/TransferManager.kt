package com.bytegrep.oplist_manager.feature.files

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.common.userMessage
import com.bytegrep.oplist_manager.data.repository.FileRepository
import com.bytegrep.oplist_manager.data.session.SessionManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** 根据扩展名推断 MIME 类型 */
fun guessMimeType(name: String): String? =
    extensionOf(name).takeIf { it.isNotEmpty() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }

/**
 * 使用系统 DownloadManager 下载到 Download/OpenList/，通知栏展示进度与完成状态。
 */
@Singleton
class DownloadHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionManager,
) {
    /** 入队下载，返回 DownloadManager 任务 id；失败抛出 [AppException] */
    fun enqueue(rawUrl: String, fileName: String): Long {
        val url = rawUrl.toHttpUrlOrNull() ?: throw AppException.Unknown("该文件没有可用的下载链接")
        val localName = sanitizeLocalFileName(fileName)
        val request = DownloadManager.Request(Uri.parse(url.toString()))
            .setTitle(localName)
            .setDescription("OpenList 文件下载")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$SUB_DIR/$localName")
        guessMimeType(localName)?.let { request.setMimeType(it) }
        session.tokenFor(url)?.let { request.addRequestHeader("Authorization", it) }
        val manager = context.getSystemService(DownloadManager::class.java)
            ?: throw AppException.Unknown("系统下载服务不可用")
        return try {
            manager.enqueue(request)
        } catch (e: IllegalArgumentException) {
            throw AppException.Unknown("无法创建下载任务：${e.message.orEmpty()}", e)
        } catch (e: IllegalStateException) {
            throw AppException.Unknown("无法写入下载目录：${e.message.orEmpty()}", e)
        } catch (e: SecurityException) {
            throw AppException.Unknown("没有权限创建下载任务", e)
        }
    }

    private companion object {
        const val SUB_DIR = "OpenList"
    }
}

/** 上传任务调度：每个文件一个 WorkManager 任务 */
@Singleton
class UploadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /** 入队上传，返回成功入队的文件数。需在 IO 线程调用（会查询 ContentResolver） */
    fun enqueue(uris: List<Uri>, targetDir: String, serverId: String): Int = uris.count { uri ->
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = queryDisplayName(uri) ?: return@count false
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .addTag(TAG)
            .addTag(serverTag(serverId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setInputData(
                workDataOf(
                    UploadWorker.KEY_SERVER_ID to serverId,
                    UploadWorker.KEY_URI to uri.toString(),
                    UploadWorker.KEY_TARGET_DIR to targetDir,
                    UploadWorker.KEY_NAME to name,
                ),
            )
            .build()
        workManager.enqueue(request)
        true
    }

    fun observe(): Flow<List<WorkInfo>> = workManager.getWorkInfosByTagFlow(TAG)

    /** 取消指定服务器的全部排队及进行中的上传（移除服务器、退出登录时调用） */
    fun cancelFor(serverId: String) {
        workManager.cancelAllWorkByTag(serverTag(serverId))
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        const val TAG = "upload"

        private fun serverTag(serverId: String) = "upload-server:$serverId"
    }
}

/** 后台上传 Worker：前台通知 + 进度节流上报，网络错误自动重试 */
@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val fileRepository: FileRepository,
) : CoroutineWorker(appContext, params) {

    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val notificationId = id.hashCode()

    override suspend fun doWork(): Result {
        // 旧版本入队的任务没有服务器信息，不能猜测目标服务器，直接失败
        val serverId = inputData.getString(KEY_SERVER_ID) ?: return failure("上传任务缺少服务器信息，请重新上传")
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse) ?: return failure("缺少文件参数")
        val targetDir = inputData.getString(KEY_TARGET_DIR) ?: "/"
        val name = inputData.getString(KEY_NAME) ?: return failure("缺少文件名")
        ensureChannel()
        runCatching { setForeground(foregroundInfo(name, 0)) }
        return try {
            upload(serverId, uri, targetDir, name)
            notificationManager?.cancel(notificationId)
            Result.success(workDataOf(KEY_NAME to name, KEY_TARGET_DIR to targetDir))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException.Network) {
            if (runAttemptCount < MAX_RETRY) Result.retry() else failure(e.userMessage(), name, targetDir)
        } catch (e: Exception) {
            failure(e.userMessage(), name, targetDir)
        }
    }

    private suspend fun upload(serverId: String, uri: Uri, targetDir: String, name: String) {
        val resolver = applicationContext.contentResolver
        val size = querySize(uri)
        val lastReport = AtomicLong(0L)
        fileRepository.upload(
            serverId = serverId,
            dir = targetDir,
            name = name,
            size = size,
            mimeType = resolver.getType(uri) ?: guessMimeType(name),
            openStream = { resolver.openInputStream(uri) ?: throw IOException("无法读取文件：$name") },
            onProgress = { written -> reportProgress(name, written, size, lastReport) },
        )
    }

    private fun reportProgress(name: String, written: Long, size: Long, lastReport: AtomicLong) {
        val now = System.currentTimeMillis()
        val last = lastReport.get()
        if (now - last < PROGRESS_INTERVAL_MS || !lastReport.compareAndSet(last, now)) return
        val percent = if (size > 0) ((written * 100) / size).toInt().coerceIn(0, 100) else 0
        setProgressAsync(workDataOf(KEY_PROGRESS to percent, KEY_PROGRESS_NAME to name))
        notificationManager?.notify(notificationId, buildNotification(name, percent))
    }

    private fun querySize(uri: Uri): Long = runCatching {
        applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.SIZE)
            if (idx >= 0 && c.moveToFirst() && !c.isNull(idx)) c.getLong(idx) else -1L
        }
    }.getOrNull() ?: -1L

    private fun failure(message: String, name: String = "", targetDir: String = "") =
        Result.failure(workDataOf(KEY_ERROR to message, KEY_NAME to name, KEY_TARGET_DIR to targetDir))

    private fun foregroundInfo(name: String, percent: Int) =
        ForegroundInfo(notificationId, buildNotification(name, percent), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    private fun buildNotification(name: String, percent: Int): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("正在上传 $name")
            .setContentText("$percent%")
            .setProgress(100, percent, percent == 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "文件传输", NotificationManager.IMPORTANCE_LOW)
        notificationManager?.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_SERVER_ID = "serverId"
        const val KEY_URI = "uri"
        const val KEY_TARGET_DIR = "targetDir"
        const val KEY_NAME = "fileName"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_PROGRESS_NAME = "name"
        const val CHANNEL_ID = "transfer"
        private const val MAX_RETRY = 3
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
