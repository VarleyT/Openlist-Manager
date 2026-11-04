package com.bytegrep.oplist_manager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 文件类型，对应 OpenList `type` 字段 */
object FsType {
    const val UNKNOWN = 0
    const val FOLDER = 1
    const val VIDEO = 2
    const val AUDIO = 3
    const val TEXT = 4
    const val IMAGE = 5
}

@Serializable
data class FsObject(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
    val created: String = "",
    val sign: String = "",
    val thumb: String = "",
    val type: Int = FsType.UNKNOWN,
)

@Serializable
data class FsListRequest(
    val path: String,
    val password: String = "",
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 0,
    val refresh: Boolean = false,
)

@Serializable
data class FsListData(
    val content: List<FsObject>? = null,
    val total: Long = 0,
    val readme: String = "",
    val write: Boolean = false,
    val provider: String = "",
)

@Serializable
data class FsGetRequest(
    val path: String,
    val password: String = "",
)

@Serializable
data class FsGetData(
    val name: String,
    val size: Long = 0,
    @SerialName("is_dir") val isDir: Boolean = false,
    val modified: String = "",
    val created: String = "",
    val sign: String = "",
    val thumb: String = "",
    val type: Int = FsType.UNKNOWN,
    @SerialName("raw_url") val rawUrl: String = "",
    val provider: String = "",
    /** 同目录下文件名前缀相同的文件（仅视频返回，可用于匹配字幕） */
    val related: List<FsObject>? = null,
)

/** `/api/fs/link` 请求（仅管理员）；path 为含用户 base_path 的完整路径 */
@Serializable
data class FsLinkRequest(
    val path: String,
    val password: String = "",
)

/** `/api/fs/link` 响应：驱动直链及其要求的请求头（Go http.Header：name -> values） */
@Serializable
data class FsLinkData(
    val url: String = "",
    val header: Map<String, List<String>>? = null,
)

@Serializable
data class FsDirsRequest(
    val path: String,
    val password: String = "",
    @SerialName("force_root") val forceRoot: Boolean = false,
)

@Serializable
data class FsDirItem(
    val name: String,
    val modified: String = "",
)

@Serializable
data class FsSearchRequest(
    val parent: String,
    val keywords: String,
    /** 0 全部，1 仅目录，2 仅文件 */
    val scope: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 100,
    val password: String = "",
)

@Serializable
data class FsSearchItem(
    val parent: String,
    val name: String,
    @SerialName("is_dir") val isDir: Boolean = false,
    val size: Long = 0,
    val type: Int = FsType.UNKNOWN,
)

@Serializable
data class MkdirRequest(val path: String)

@Serializable
data class RenameRequest(val path: String, val name: String)

@Serializable
data class MoveCopyRequest(
    @SerialName("src_dir") val srcDir: String,
    @SerialName("dst_dir") val dstDir: String,
    val names: List<String>,
)

@Serializable
data class RemoveRequest(val dir: String, val names: List<String>)
