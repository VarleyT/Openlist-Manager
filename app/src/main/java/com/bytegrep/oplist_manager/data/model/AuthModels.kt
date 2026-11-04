package com.bytegrep.oplist_manager.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("otp_code") val otpCode: String = "",
)

@Serializable
data class TokenData(val token: String)

/** 用户角色，对应 OpenList `role` */
object UserRole {
    const val GENERAL = 0
    const val GUEST = 1
    const val ADMIN = 2

    fun label(role: Int): String = when (role) {
        ADMIN -> "管理员"
        GUEST -> "访客"
        else -> "普通用户"
    }
}

@Serializable
data class UserDto(
    val id: Int = 0,
    val username: String = "",
    val password: String = "",
    @SerialName("base_path") val basePath: String = "/",
    val role: Int = UserRole.GENERAL,
    val disabled: Boolean = false,
    val permission: Int = 0,
    @SerialName("sso_id") val ssoId: String = "",
    @SerialName("allow_ldap") val allowLdap: Boolean = false,
    val otp: Boolean = false,
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
    val isGuest: Boolean get() = role == UserRole.GUEST

    fun hasPermission(bit: Int): Boolean = isAdmin || (permission shr bit) and 1 == 1
}

/** 用户权限位，来源：OpenList internal/model/user.go */
enum class UserPermission(val bit: Int, val label: String) {
    SEE_HIDES(0, "查看隐藏文件"),
    ACCESS_WITHOUT_PASSWORD(1, "无需密码访问"),
    OFFLINE_DOWNLOAD(2, "添加离线下载"),
    WRITE(3, "上传与新建目录"),
    RENAME(4, "重命名"),
    MOVE(5, "移动"),
    COPY(6, "复制"),
    REMOVE(7, "删除"),
    WEBDAV_READ(8, "WebDAV 读取"),
    WEBDAV_MANAGE(9, "WebDAV 管理"),
    FTP_ACCESS(10, "FTP/SFTP 访问"),
    FTP_MANAGE(11, "FTP/SFTP 写入"),
    READ_ARCHIVES(12, "读取压缩包"),
    DECOMPRESS(13, "解压缩"),
    SHARE(14, "分享"),
}
