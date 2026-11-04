package com.bytegrep.oplist_manager.data.repository

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerState
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.safeApiCall
import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.core.security.SecretCipher
import com.bytegrep.oplist_manager.data.model.LoginRequest
import com.bytegrep.oplist_manager.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** 连接测试结果 */
data class ServerProbe(
    val baseUrl: String,
    val siteTitle: String,
    val version: String,
)

@Singleton
class AuthRepository @Inject constructor(
    private val factory: ApiClientFactory,
    private val serverStore: ServerStore,
    private val cipher: SecretCipher,
    private val sessionManager: SessionManager,
) {
    val servers: Flow<ServerState> = serverStore.state

    /** 测试服务器连通性并读取站点标题、版本号 */
    suspend fun probe(rawUrl: String): ServerProbe {
        val baseUrl = ApiClientFactory.normalizeBaseUrl(rawUrl)
            ?: throw AppException.Unknown("服务器地址格式无效")
        val settings = safeApiCall { factory.create(baseUrl, null).publicSettings().unwrap() }
        return ServerProbe(
            baseUrl = baseUrl,
            siteTitle = settings.string("site_title").ifBlank { URI(baseUrl).host.orEmpty() },
            version = parseVersion(settings.string("version")),
        )
    }

    /**
     * 登录并保存服务器配置。[rememberPassword] 为 true 时加密保存密码用于 Token 过期后自动重登。
     */
    suspend fun login(
        rawUrl: String,
        username: String,
        password: String,
        otpCode: String,
        rememberPassword: Boolean,
        existingId: String?,
    ): ServerProfile {
        val probe = probe(rawUrl)
        val token = safeApiCall {
            factory.create(probe.baseUrl, null)
                .login(LoginRequest(username.trim(), password, otpCode.trim()))
                .unwrap().token
        }
        val user = safeApiCall { factory.create(probe.baseUrl, token).me().unwrap() }
        val existing = serverStore.current().servers.firstOrNull {
            it.id == existingId || (it.baseUrl == probe.baseUrl && it.username == username.trim())
        }
        val profile = ServerProfile(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = existing?.name ?: probe.siteTitle,
            baseUrl = probe.baseUrl,
            username = username.trim(),
            tokenEnc = cipher.encrypt(token),
            passwordEnc = if (rememberPassword) cipher.encrypt(password) else null,
            version = probe.version,
            lastConnectedAt = System.currentTimeMillis(),
        )
        serverStore.upsert(profile, makeActive = true)
        sessionManager.activate(profile, token, user)
        return profile
    }

    /** 切换到已保存的服务器；若无有效 Token 返回 false，需要重新登录 */
    suspend fun switchTo(id: String): Boolean {
        serverStore.setActive(id)
        return sessionManager.restore()
    }

    /** 退出当前服务器：通知服务端注销，并清除本地 Token 与密码 */
    suspend fun logout() {
        val session = sessionManager.session.value ?: return
        runCatching { safeApiCall { factory.create(session.server.baseUrl, session.token).logout() } }
        serverStore.update(session.server.id) { it.copy(tokenEnc = null, passwordEnc = null) }
        serverStore.setActive(null)
        sessionManager.clear()
    }

    suspend fun rename(id: String, name: String) {
        serverStore.update(id) { it.copy(name = name.trim().ifEmpty { it.name }) }
        if (sessionManager.session.value?.server?.id == id) sessionManager.restore()
    }

    suspend fun remove(id: String) {
        val isActive = sessionManager.session.value?.server?.id == id
        serverStore.remove(id)
        if (isActive) sessionManager.clear()
    }

    private fun JsonObject.string(key: String): String =
        runCatching { get(key)?.jsonPrimitive?.content }.getOrNull().orEmpty()

    companion object {
        /** "v4.1.10 (Commit: e3c664f) - Frontend: ..." -> "v4.1.10" */
        fun parseVersion(raw: String): String = raw.substringBefore(' ').trim()
    }
}
