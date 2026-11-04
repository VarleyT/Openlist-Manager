package com.bytegrep.oplist_manager.feature.files

import com.bytegrep.oplist_manager.data.model.FsType

/** 文件展示分类，决定图标与颜色 */
enum class FileKind { FOLDER, IMAGE, VIDEO, AUDIO, TEXT, PDF, ARCHIVE, OTHER }

/** 文本预览大小上限：512KB */
const val TEXT_PREVIEW_LIMIT: Long = 512 * 1024L

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "ico", "avif")
private val VIDEO_EXT = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts", "rmvb", "3gp")
private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "ogg", "m4a", "wma", "ape", "opus")
private val ARCHIVE_EXT = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "iso")
private val TEXT_PREVIEW_EXT = setOf(
    "txt", "md", "json", "log", "xml", "yml", "yaml", "conf", "ini", "sh",
    "py", "kt", "java", "js", "ts", "css", "html", "csv",
)

/** 小写扩展名，无扩展名返回空串 */
fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

fun fileKindOf(name: String, isDir: Boolean, type: Int): FileKind {
    if (isDir || type == FsType.FOLDER) return FileKind.FOLDER
    val ext = extensionOf(name)
    return when {
        ext == "pdf" -> FileKind.PDF
        ext in ARCHIVE_EXT -> FileKind.ARCHIVE
        type == FsType.IMAGE || ext in IMAGE_EXT -> FileKind.IMAGE
        // "ts" 在 OpenList 中通常识别为视频，优先服务器 type
        type == FsType.VIDEO || (ext in VIDEO_EXT && type != FsType.TEXT) -> FileKind.VIDEO
        type == FsType.AUDIO || ext in AUDIO_EXT -> FileKind.AUDIO
        type == FsType.TEXT || ext in TEXT_PREVIEW_EXT -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

/** 是否可以在应用内以文本方式预览 */
fun canPreviewText(name: String, type: Int, size: Long): Boolean =
    (type == FsType.TEXT || extensionOf(name) in TEXT_PREVIEW_EXT) && size < TEXT_PREVIEW_LIMIT

/** 校验新建/重命名的名称，合法返回 null，否则返回错误提示 */
fun validateFileName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "名称不能为空"
        trimmed == "." || trimmed == ".." -> "名称不合法"
        trimmed.contains('/') || trimmed.contains('\\') -> "名称不能包含 / 或 \\"
        else -> null
    }
}

/** 搜索错误友好化：服务器未开启索引时 OpenList 返回 "search not available" 等信息 */
fun searchErrorMessage(raw: String): String {
    val lower = raw.lowercase()
    val indexDisabled = "search not available" in lower ||
        "not available" in lower ||
        "index" in lower ||
        "searcher" in lower
    return if (indexDisabled) "服务器未启用搜索索引，请在 OpenList 管理后台「索引」中开启并构建索引" else raw
}

/** 用于保存到本机的文件名：替换路径分隔符等非法字符 */
fun sanitizeLocalFileName(name: String): String {
    val cleaned = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trim('.')
    return cleaned.ifEmpty { "download" }
}
