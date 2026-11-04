package com.bytegrep.oplist_manager.core.network

import com.bytegrep.oplist_manager.data.model.CreateStorageResult
import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.model.FsDirItem
import com.bytegrep.oplist_manager.data.model.FsDirsRequest
import com.bytegrep.oplist_manager.data.model.FsGetData
import com.bytegrep.oplist_manager.data.model.FsGetRequest
import com.bytegrep.oplist_manager.data.model.FsLinkData
import com.bytegrep.oplist_manager.data.model.FsLinkRequest
import com.bytegrep.oplist_manager.data.model.FsListData
import com.bytegrep.oplist_manager.data.model.FsListRequest
import com.bytegrep.oplist_manager.data.model.FsSearchItem
import com.bytegrep.oplist_manager.data.model.FsSearchRequest
import com.bytegrep.oplist_manager.data.model.LoginRequest
import com.bytegrep.oplist_manager.data.model.MkdirRequest
import com.bytegrep.oplist_manager.data.model.MoveCopyRequest
import com.bytegrep.oplist_manager.data.model.RemoveRequest
import com.bytegrep.oplist_manager.data.model.RenameRequest
import com.bytegrep.oplist_manager.data.model.SettingItem
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TokenData
import com.bytegrep.oplist_manager.data.model.UserDto
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * OpenList REST API（已针对 v4.1.x 实测）。
 * 认证方式：请求头 `Authorization: <token>`，不带 Bearer 前缀。
 */
interface OpenListApi {

    // ---------- 公共 / 认证 ----------
    @GET("api/public/settings")
    suspend fun publicSettings(): ApiEnvelope<JsonObject>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): ApiEnvelope<TokenData>

    @GET("api/me")
    suspend fun me(): ApiEnvelope<UserDto>

    @GET("api/auth/logout")
    suspend fun logout(): ApiEnvelope<JsonElement?>

    // ---------- 文件 ----------
    @POST("api/fs/list")
    suspend fun fsList(@Body body: FsListRequest): ApiEnvelope<FsListData>

    @POST("api/fs/get")
    suspend fun fsGet(@Body body: FsGetRequest): ApiEnvelope<FsGetData>

    /** 获取驱动直链与所需请求头，仅管理员可用 */
    @POST("api/fs/link")
    suspend fun fsLink(@Body body: FsLinkRequest): ApiEnvelope<FsLinkData>

    @POST("api/fs/dirs")
    suspend fun fsDirs(@Body body: FsDirsRequest): ApiEnvelope<List<FsDirItem>>

    @POST("api/fs/search")
    suspend fun fsSearch(@Body body: FsSearchRequest): ApiEnvelope<PageData<FsSearchItem>>

    @POST("api/fs/mkdir")
    suspend fun fsMkdir(@Body body: MkdirRequest): ApiEnvelope<JsonElement?>

    @POST("api/fs/rename")
    suspend fun fsRename(@Body body: RenameRequest): ApiEnvelope<JsonElement?>

    @POST("api/fs/move")
    suspend fun fsMove(@Body body: MoveCopyRequest): ApiEnvelope<JsonElement?>

    @POST("api/fs/copy")
    suspend fun fsCopy(@Body body: MoveCopyRequest): ApiEnvelope<JsonElement?>

    @POST("api/fs/remove")
    suspend fun fsRemove(@Body body: RemoveRequest): ApiEnvelope<JsonElement?>

    /** 流式上传。[filePath] 需 URL 编码，[asTask] 为 "true" 时由服务端异步上传 */
    @PUT("api/fs/put")
    suspend fun fsPut(
        @Header("File-Path") filePath: String,
        @Header("As-Task") asTask: String,
        @Body body: RequestBody,
    ): ApiEnvelope<JsonElement?>

    // ---------- 存储（管理员） ----------
    @GET("api/admin/storage/list")
    suspend fun storageList(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 0,
    ): ApiEnvelope<PageData<JsonObject>>

    @GET("api/admin/storage/get")
    suspend fun storageGet(@Query("id") id: Int): ApiEnvelope<JsonObject>

    @POST("api/admin/storage/create")
    suspend fun storageCreate(@Body body: JsonObject): ApiEnvelope<CreateStorageResult>

    @POST("api/admin/storage/update")
    suspend fun storageUpdate(@Body body: JsonObject): ApiEnvelope<JsonElement?>

    @POST("api/admin/storage/delete")
    suspend fun storageDelete(@Query("id") id: Int): ApiEnvelope<JsonElement?>

    @POST("api/admin/storage/enable")
    suspend fun storageEnable(@Query("id") id: Int): ApiEnvelope<JsonElement?>

    @POST("api/admin/storage/disable")
    suspend fun storageDisable(@Query("id") id: Int): ApiEnvelope<JsonElement?>

    @POST("api/admin/storage/load_all")
    suspend fun storageLoadAll(): ApiEnvelope<JsonElement?>

    @GET("api/admin/driver/names")
    suspend fun driverNames(): ApiEnvelope<List<String>>

    @GET("api/admin/driver/info")
    suspend fun driverInfo(@Query("driver") driver: String): ApiEnvelope<DriverInfo>

    // ---------- 用户（管理员） ----------
    @GET("api/admin/user/list")
    suspend fun userList(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 0,
    ): ApiEnvelope<PageData<UserDto>>

    @GET("api/admin/user/get")
    suspend fun userGet(@Query("id") id: Int): ApiEnvelope<UserDto>

    @POST("api/admin/user/create")
    suspend fun userCreate(@Body body: UserDto): ApiEnvelope<JsonElement?>

    @POST("api/admin/user/update")
    suspend fun userUpdate(@Body body: UserDto): ApiEnvelope<JsonElement?>

    @POST("api/admin/user/delete")
    suspend fun userDelete(@Query("id") id: Int): ApiEnvelope<JsonElement?>

    @POST("api/admin/user/cancel_2fa")
    suspend fun userCancel2fa(@Query("id") id: Int): ApiEnvelope<JsonElement?>

    // ---------- 设置（管理员） ----------
    @GET("api/admin/setting/list")
    suspend fun settingList(@Query("group") group: Int): ApiEnvelope<List<SettingItem>>

    @POST("api/admin/setting/save")
    suspend fun settingSave(@Body body: List<SettingItem>): ApiEnvelope<JsonElement?>

    // ---------- 任务 ----------
    /** [scope] 为 "admin/task"（管理员）或 "task"（普通用户） */
    @GET("api/{scope}/{type}/undone")
    suspend fun taskUndone(
        @Path("scope", encoded = true) scope: String,
        @Path("type") type: String,
    ): ApiEnvelope<List<TaskInfo>>

    @GET("api/{scope}/{type}/done")
    suspend fun taskDone(
        @Path("scope", encoded = true) scope: String,
        @Path("type") type: String,
    ): ApiEnvelope<List<TaskInfo>>

    /** [action] 为 cancel / delete / retry */
    @POST("api/{scope}/{type}/{action}")
    suspend fun taskAction(
        @Path("scope", encoded = true) scope: String,
        @Path("type") type: String,
        @Path("action") action: String,
        @Query("tid") tid: String,
    ): ApiEnvelope<JsonElement?>

    /** [action] 为 clear_done / clear_succeeded / retry_failed */
    @POST("api/{scope}/{type}/{action}")
    suspend fun taskBulkAction(
        @Path("scope", encoded = true) scope: String,
        @Path("type") type: String,
        @Path("action") action: String,
    ): ApiEnvelope<JsonElement?>
}
