package com.bytegrep.oplist_manager.data.player

import com.bytegrep.oplist_manager.core.common.AppException
import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsGetRequest
import com.bytegrep.oplist_manager.data.model.FsLinkData
import com.bytegrep.oplist_manager.data.model.FsLinkRequest
import com.bytegrep.oplist_manager.data.session.SessionManager
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/** 播放相关的 OpenList API 调用（均走 session.request，Token 仅发往 OpenList 服务器） */
@Singleton
class VideoPlaybackRepository @Inject constructor(
    private val session: SessionManager,
) {

    suspend fun get(path: String): FsGetData =
        session.request { fsGet(FsGetRequest(path)).unwrap() }

    /** [fullPath] 需包含用户 base_path；仅管理员可调用 */
    suspend fun link(fullPath: String): FsLinkData =
        session.request { fsLink(FsLinkRequest(fullPath)).unwrap() }

    /** 当前会话环境；进程被回收后重新进入播放页时先恢复会话 */
    suspend fun env(): PlaybackEnv {
        if (session.session.value == null && !session.restore()) {
            throw AppException.Unauthorized("尚未登录服务器")
        }
        val current = session.requireSession()
        // 用户信息获取失败时按非管理员处理（只用 raw_url），不阻塞播放
        val user = session.currentUser.value ?: try {
            session.refreshUser()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return PlaybackEnv(
            serverBaseUrl = current.server.baseUrl,
            isAdmin = user?.isAdmin == true,
            basePath = user?.basePath ?: "/",
        )
    }
}
