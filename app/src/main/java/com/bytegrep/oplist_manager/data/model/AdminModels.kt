package com.bytegrep.oplist_manager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * 存储挂载的展示模型。编辑时保留原始 [JsonObject] 以兼容不同版本的字段。
 */
data class Storage(
    val id: Int,
    val mountPath: String,
    val driver: String,
    val status: String,
    val disabled: Boolean,
    val remark: String,
    val order: Int,
    val modified: String,
    val raw: JsonObject,
) {
    val isWorking: Boolean get() = !disabled && status == STATUS_WORK
    val isError: Boolean get() = !disabled && status != STATUS_WORK

    companion object {
        const val STATUS_WORK = "work"
    }
}

@Serializable
data class CreateStorageResult(val id: Int = 0)

/** 驱动配置项定义，来自 /api/admin/driver/info */
@Serializable
data class DriverItem(
    val name: String,
    val type: String = "string",
    val default: String = "",
    val options: String = "",
    val required: Boolean = false,
    val help: String = "",
)

@Serializable
data class DriverInfo(
    val common: List<DriverItem> = emptyList(),
    val additional: List<DriverItem> = emptyList(),
)

/** 系统设置项 */
@Serializable
data class SettingItem(
    val key: String,
    val value: String = "",
    val help: String = "",
    val type: String = "string",
    val options: String = "",
    val group: Int = 0,
    val flag: Int = 0,
    val index: Int = 0,
) {
    val isReadOnly: Boolean get() = flag == FLAG_READONLY
    val isDeprecated: Boolean get() = flag == FLAG_DEPRECATED

    companion object {
        const val FLAG_READONLY = 2
        const val FLAG_DEPRECATED = 3
    }
}

/** 设置分组，来源：OpenList internal/model/setting.go */
enum class SettingGroup(val id: Int, val label: String) {
    SITE(1, "站点"),
    STYLE(2, "样式"),
    PREVIEW(3, "预览"),
    GLOBAL(4, "全局"),
    OFFLINE_DOWNLOAD(5, "离线下载"),
    INDEX(6, "索引"),
    SSO(7, "单点登录"),
    LDAP(8, "LDAP"),
    S3(9, "S3"),
    FTP(10, "FTP"),
    TRAFFIC(11, "流量控制"),
}

/** 后台任务 */
@Serializable
data class TaskInfo(
    val id: String,
    val name: String = "",
    val creator: String = "",
    @SerialName("creator_role") val creatorRole: Int = 0,
    val state: Int = 0,
    val status: String = "",
    val progress: Double = 0.0,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("total_bytes") val totalBytes: Long = 0,
    val error: String = "",
) {
    val stateLabel: String get() = TaskState.label(state)
    val isRunning: Boolean get() = state == TaskState.RUNNING || state == TaskState.PENDING
    val isFailed: Boolean get() = state == TaskState.FAILED || state == TaskState.ERRORED
}

/** 任务状态，来源：OpenList pkg/tache 状态枚举 */
object TaskState {
    const val PENDING = 0
    const val RUNNING = 1
    const val SUCCEEDED = 2
    const val CANCELING = 3
    const val CANCELED = 4
    const val ERRORED = 5
    const val FAILING = 6
    const val FAILED = 7
    const val WAITING_RETRY = 8
    const val BEFORE_RETRY = 9

    fun label(state: Int): String = when (state) {
        PENDING -> "等待中"
        RUNNING -> "运行中"
        SUCCEEDED -> "已完成"
        CANCELING -> "取消中"
        CANCELED -> "已取消"
        ERRORED -> "出错"
        FAILING -> "失败中"
        FAILED -> "失败"
        WAITING_RETRY -> "等待重试"
        BEFORE_RETRY -> "准备重试"
        else -> "未知"
    }
}

/** 任务类型，对应 /api/(admin/)task/{type} */
enum class TaskType(val path: String, val label: String) {
    UPLOAD("upload", "上传"),
    COPY("copy", "复制"),
    MOVE("move", "移动"),
    OFFLINE_DOWNLOAD("offline_download", "离线下载"),
    OFFLINE_TRANSFER("offline_download_transfer", "离线转存"),
    DECOMPRESS("decompress", "解压"),
    DECOMPRESS_UPLOAD("decompress_upload", "解压上传"),
}
