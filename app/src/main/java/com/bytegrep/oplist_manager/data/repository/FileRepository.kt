package com.bytegrep.oplist_manager.data.repository

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.common.joinPath
import com.bytegrep.oplist_manager.core.network.PageData
import com.bytegrep.oplist_manager.core.network.safeApiCall
import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.core.network.unwrapNullable
import com.bytegrep.oplist_manager.data.model.FsDirsRequest
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsGetRequest
import com.bytegrep.oplist_manager.data.model.FsListData
import com.bytegrep.oplist_manager.data.model.FsListRequest
import com.bytegrep.oplist_manager.data.model.FsSearchItem
import com.bytegrep.oplist_manager.data.model.FsSearchRequest
import com.bytegrep.oplist_manager.data.model.MkdirRequest
import com.bytegrep.oplist_manager.data.model.MoveCopyRequest
import com.bytegrep.oplist_manager.data.model.RemoveRequest
import com.bytegrep.oplist_manager.data.model.RenameRequest
import com.bytegrep.oplist_manager.data.session.SessionManager
import java.io.InputStream
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

@Singleton
class FileRepository @Inject constructor(
    private val session: SessionManager,
) {
    suspend fun list(path: String, refresh: Boolean = false): FsListData =
        session.request { fsList(FsListRequest(path = path, refresh = refresh)).unwrap() }

    suspend fun get(path: String): FsGetData =
        session.request { fsGet(FsGetRequest(path)).unwrap() }

    suspend fun dirs(path: String): List<String> =
        session.request { fsDirs(FsDirsRequest(path)).unwrapNullable().orEmpty().map { it.name } }

    /** 搜索第 [page] 页（从 1 开始），返回该页结果与服务端统计的总数 */
    suspend fun search(parent: String, keywords: String, page: Int): PageData<FsSearchItem> =
        session.request {
            fsSearch(FsSearchRequest(parent = parent, keywords = keywords, page = page, perPage = SEARCH_PAGE_SIZE)).unwrap()
        }

    suspend fun mkdir(parent: String, name: String) {
        session.request { fsMkdir(MkdirRequest(joinPath(parent, name))).unwrapNullable() }
    }

    suspend fun rename(path: String, newName: String) {
        session.request { fsRename(RenameRequest(path, newName)).unwrapNullable() }
    }

    suspend fun move(srcDir: String, dstDir: String, names: List<String>) {
        session.request { fsMove(MoveCopyRequest(srcDir, dstDir, names)).unwrapNullable() }
    }

    suspend fun copy(srcDir: String, dstDir: String, names: List<String>) {
        session.request { fsCopy(MoveCopyRequest(srcDir, dstDir, names)).unwrapNullable() }
    }

    suspend fun remove(dir: String, names: List<String>) {
        session.request { fsRemove(RemoveRequest(dir, names)).unwrapNullable() }
    }

    /**
     * 流式上传文件到 [serverId] 服务器的 [dir]/[name]，不受期间切换活动服务器影响。
     * [openStream] 每次调用需返回新的输入流（重登重试时会再次读取）。
     */
    suspend fun upload(
        serverId: String,
        dir: String,
        name: String,
        size: Long,
        mimeType: String?,
        openStream: () -> InputStream,
        onProgress: (Long) -> Unit,
    ) {
        val filePath = encodeFilePath(joinPath(dir, name))
        session.requestFor(serverId) {
            val body = StreamRequestBody(mimeType?.toMediaTypeOrNull(), size, openStream, onProgress)
            fsPut(filePath, asTask = "false", body = body).unwrapNullable()
        }
    }

    /** 读取文本内容用于预览，超过 [maxBytes] 截断 */
    suspend fun readText(url: String, maxBytes: Long = MAX_TEXT_PREVIEW): String = withContext(Dispatchers.IO) {
        safeApiCall {
            val request = Request.Builder().url(url).build()
            session.authorizedClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw AppException.Server(response.code, "读取文件失败 (HTTP ${response.code})")
                val source = response.body.source()
                source.request(maxBytes)
                val bytes = source.buffer.let { buf -> buf.readByteArray(minOf(buf.size, maxBytes)) }
                String(bytes, Charsets.UTF_8)
            }
        }
    }

    private class StreamRequestBody(
        private val type: MediaType?,
        private val length: Long,
        private val openStream: () -> InputStream,
        private val onProgress: (Long) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType? = type
        override fun contentLength(): Long = length

        override fun writeTo(sink: BufferedSink) {
            openStream().source().use { source ->
                var written = 0L
                while (true) {
                    val read = source.read(sink.buffer, CHUNK)
                    if (read == -1L) break
                    sink.emit()
                    written += read
                    onProgress(written)
                }
            }
        }
    }

    companion object {
        private const val CHUNK = 64 * 1024L
        const val MAX_TEXT_PREVIEW = 512 * 1024L
        const val SEARCH_PAGE_SIZE = 100

        /** File-Path 头需 URL 编码；空格必须为 %20（服务端使用 PathUnescape） */
        fun encodeFilePath(path: String): String =
            URLEncoder.encode(path, Charsets.UTF_8.name()).replace("+", "%20")
    }
}
