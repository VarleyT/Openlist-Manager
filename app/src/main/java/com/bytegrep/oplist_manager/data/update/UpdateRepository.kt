package com.bytegrep.oplist_manager.data.update

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.network.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

object AppLinks {
    const val REPO_URL = "https://github.com/VarleyT/Openlist-Manager"
    const val REPO_DISPLAY = "github.com/VarleyT/Openlist-Manager"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LATEST_RELEASE_API = "https://api.github.com/repos/VarleyT/Openlist-Manager/releases/latest"
}

@Serializable
data class GithubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = AppLinks.RELEASES_URL,
)

data class AppRelease(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val release: AppRelease) : UpdateCheckResult
}

/**
 * 通过 GitHub Releases 检查新版本。releases/latest 自动排除 draft 与预发布。
 * 使用不带 OpenList 认证的普通客户端，避免 token 泄露给第三方。
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {

    suspend fun check(currentVersion: String): UpdateCheckResult = safeApiCall {
        val release = fetchLatest()
        val version = release.tagName.trim().removePrefix("v").removePrefix("V")
        if (version.isEmpty()) throw AppException.Unknown("版本信息无法解析")
        if (isNewerVersion(version, currentVersion)) {
            UpdateCheckResult.Available(
                AppRelease(
                    version = version,
                    title = release.name?.takeIf { it.isNotBlank() } ?: "v$version",
                    notes = release.body.orEmpty().trim(),
                    pageUrl = release.htmlUrl.takeIf { it.startsWith("https://github.com/") } ?: AppLinks.RELEASES_URL,
                ),
            )
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    private suspend fun fetchLatest(): GithubReleaseDto = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(AppLinks.LATEST_RELEASE_API)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == HTTP_NOT_FOUND -> throw AppException.Server(HTTP_NOT_FOUND, "暂无已发布的版本")
                response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY ->
                    throw AppException.Server(response.code, "GitHub 请求过于频繁，请稍后再试")
                !response.isSuccessful -> throw AppException.Server(response.code, "检查更新失败（HTTP ${response.code}）")
            }
            json.decodeFromString(GithubReleaseDto.serializer(), response.body.string())
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TOO_MANY = 429
    }
}
