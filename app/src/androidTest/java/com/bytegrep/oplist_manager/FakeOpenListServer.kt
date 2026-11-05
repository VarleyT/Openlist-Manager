package com.bytegrep.oplist_manager

import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * 在测试进程内模拟一台 OpenList v4 服务器，只实现界面流程用到的接口。
 * 用户名 [USERNAME] / 密码 [PASSWORD] 可以登录，其他密码返回业务错误。
 */
class FakeOpenListServer {

    private val server = MockWebServer()

    /** 填入登录页的地址，例如 127.0.0.1:12345（不写协议，局域网地址会补全为 http） */
    val address: String get() = "${server.hostName}:${server.port}"

    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = route(request)
        }
        server.start()
    }

    fun shutdown() {
        server.shutdown()
    }

    private fun route(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty().substringBefore('?')
        val body = when {
            path == "/api/public/settings" -> ok("""{"site_title":"$SITE_TITLE","version":"v4.1.10 (Commit: test)"}""")
            path == "/api/auth/login" -> login(request.body.readUtf8())
            path == "/api/me" -> ok("""{"id":1,"username":"$USERNAME","role":2,"base_path":"/","permission":0}""")
            path == "/api/fs/list" -> ok(
                """{"content":[
                    {"name":"$FILE_NAME","size":2048,"is_dir":false,"type":4,"modified":"2026-11-01T10:00:00Z"},
                    {"name":"$FOLDER_NAME","size":0,"is_dir":true,"type":1,"modified":"2026-11-01T10:00:00Z"}
                ],"total":2,"write":true,"provider":"Local"}""",
            )
            path == "/api/admin/storage/list" -> ok(
                """{"content":[{"id":1,"mount_path":"$MOUNT_PATH","driver":"Local","status":"work","disabled":false,"order":0}],"total":1}""",
            )
            path.endsWith("/undone") || path.endsWith("/done") -> ok("[]")
            else -> ok("null")
        }
        return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }

    private fun login(requestBody: String): String =
        if (requestBody.contains("\"password\":\"$PASSWORD\"")) {
            ok("""{"token":"test-token"}""")
        } else {
            """{"code":400,"message":"$WRONG_PASSWORD_MESSAGE","data":null}"""
        }

    private fun ok(data: String) = """{"code":200,"message":"success","data":$data}"""

    companion object {
        const val SITE_TITLE = "测试站点"
        const val USERNAME = "admin"
        const val PASSWORD = "secret"
        const val WRONG_PASSWORD_MESSAGE = "password is incorrect"
        const val FILE_NAME = "年度报告.txt"
        const val FOLDER_NAME = "照片"
        const val MOUNT_PATH = "/本地存储"
    }
}
