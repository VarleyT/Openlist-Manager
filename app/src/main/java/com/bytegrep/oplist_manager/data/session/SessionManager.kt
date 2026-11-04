package com.bytegrep.oplist_manager.data.session

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.datastore.ServerProfile
import com.bytegrep.oplist_manager.core.datastore.ServerStore
import com.bytegrep.oplist_manager.core.network.ApiClientFactory
import com.bytegrep.oplist_manager.core.network.OpenListApi
import com.bytegrep.oplist_manager.core.network.safeApiCall
import com.bytegrep.oplist_manager.core.network.tokenForOrigin
import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.core.security.SecretCipher
import com.bytegrep.oplist_manager.data.model.LoginRequest
import com.bytegrep.oplist_manager.data.model.UserDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/** 当前活动会话 */
data class Session(
    val server: ServerProfile,
    val token: String,
)

/**
 * 管理当前服务器会话：恢复、请求代理、Token 失效自动重登（仅在用户选择记住密码时）。
 */
@Singleton
class SessionManager @Inject constructor(
    private val serverStore: ServerStore,
    private val cipher: SecretCipher,
    private val factory: ApiClientFactory,
) {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _currentUser = MutableStateFlow<UserDto?>(null)
    val currentUser: StateFlow<UserDto?> = _currentUser.asStateFlow()

    private val _authExpired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Token 失效且无法自动重登时发出，UI 应跳转登录页 */
    val authExpired: SharedFlow<String> = _authExpired.asSharedFlow()

    private val reloginMutex = Mutex()

    /** 从持久化恢复当前活动服务器会话；返回是否存在有效会话 */
    suspend fun restore(): Boolean {
        val profile = serverStore.current().active
        val token = profile?.tokenEnc?.let { cipher.decrypt(it) }
        _session.value = if (profile != null && token != null) Session(profile, token) else null
        _currentUser.value = null
        return _session.value != null
    }

    fun activate(profile: ServerProfile, token: String, user: UserDto?) {
        _session.value = Session(profile, token)
        _currentUser.value = user
    }

    fun clear() {
        _session.value = null
        _currentUser.value = null
    }

    fun updateUser(user: UserDto) {
        _currentUser.value = user
    }

    fun requireSession(): Session = _session.value ?: throw AppException.Unauthorized("尚未登录服务器")

    /** 当前会话的 API 实例，供不需要自动重登的场景使用 */
    fun api(): OpenListApi = requireSession().let { factory.create(it.server.baseUrl, it.token) }

    /** 当前会话下 [url] 应携带的 Token；仅与当前服务器同源时返回非空 */
    fun tokenFor(url: HttpUrl): String? =
        _session.value?.let { tokenForOrigin(it.server.baseUrl, it.token, url) }

    /** 仅对当前服务器同源请求附加认证头的 OkHttpClient（文本预览、图片加载） */
    val authorizedClient: OkHttpClient by lazy { factory.sameOriginClient(::tokenFor) }

    /**
     * 执行 API 请求。遇到 401 时若保存了密码则自动重登并重试一次，否则通知登录失效。
     */
    suspend fun <T> request(block: suspend OpenListApi.() -> T): T = requestOn(requireSession(), block)

    /**
     * 固定在指定服务器上执行请求，与界面当前活动服务器无关（供后台任务使用）。
     * 进程冷启动时直接从持久化配置恢复凭据；服务器已移除或无可用凭据时抛出 [AppException.Unauthorized]。
     */
    suspend fun <T> requestFor(serverId: String, block: suspend OpenListApi.() -> T): T =
        requestOn(sessionFor(serverId), block)

    private suspend fun sessionFor(serverId: String): Session {
        _session.value?.takeIf { it.server.id == serverId }?.let { return it }
        val profile = serverStore.current().servers.firstOrNull { it.id == serverId }
            ?: throw AppException.Unauthorized("目标服务器已被移除")
        val token = profile.tokenEnc?.let { cipher.decrypt(it) }
            ?: throw AppException.Unauthorized("目标服务器登录已失效，请重新登录")
        return Session(profile, token)
    }

    private suspend fun <T> requestOn(current: Session, block: suspend OpenListApi.() -> T): T {
        return try {
            safeApiCall { factory.create(current.server.baseUrl, current.token).block() }
        } catch (e: AppException.Unauthorized) {
            val renewed = relogin(current)
            if (renewed == null) {
                // 期间已切换到其他服务器时，旧服务器失效不应把用户踢回登录页
                if (_session.value?.server?.id == current.server.id) _authExpired.tryEmit(e.message ?: "登录已失效")
                throw e
            }
            safeApiCall { factory.create(renewed.server.baseUrl, renewed.token).block() }
        }
    }

    /** 加载并缓存当前用户信息 */
    suspend fun refreshUser(): UserDto {
        val user = request { me().unwrap() }
        _currentUser.value = user
        return user
    }

    private suspend fun relogin(expired: Session): Session? = reloginMutex.withLock {
        // 其他协程可能已完成重登
        _session.value?.takeIf { it.token != expired.token && it.server.id == expired.server.id }?.let { return it }
        // 以持久化的最新配置为准：期间可能已被重命名、退出登录（清除密码）或移除
        val latest = serverStore.current().servers.firstOrNull { it.id == expired.server.id } ?: return null
        val password = latest.passwordEnc?.let { cipher.decrypt(it) } ?: return null
        val token = runCatching {
            safeApiCall {
                factory.create(latest.baseUrl, null)
                    .login(LoginRequest(latest.username, password)).unwrap().token
            }
        }.getOrNull() ?: return null
        val tokenEnc = cipher.encrypt(token)
        val now = System.currentTimeMillis()
        serverStore.update(latest.id) { it.copy(tokenEnc = tokenEnc, lastConnectedAt = now) }
        val renewed = Session(latest.copy(tokenEnc = tokenEnc, lastConnectedAt = now), token)
        // 仅当活动会话仍是该服务器时才替换，避免覆盖用户期间切换到的其他服务器
        _session.update { active -> if (active?.server?.id == latest.id) renewed else active }
        renewed
    }
}
