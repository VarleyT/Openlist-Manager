package com.bytegrep.oplist_manager.data.repository

import com.bytegrep.oplist_manager.core.network.unwrap
import com.bytegrep.oplist_manager.core.network.unwrapNullable
import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.model.SettingItem
import com.bytegrep.oplist_manager.data.model.Storage
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskType
import com.bytegrep.oplist_manager.data.model.UserDto
import com.bytegrep.oplist_manager.data.session.SessionManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepository @Inject constructor(
    private val session: SessionManager,
) {
    suspend fun list(): List<Storage> =
        session.request { storageList().unwrap().content.orEmpty().map { it.toStorage() } }

    suspend fun get(id: Int): JsonObject = session.request { storageGet(id).unwrap() }

    suspend fun create(body: JsonObject): Int = session.request { storageCreate(body).unwrap().id }

    suspend fun update(body: JsonObject) {
        session.request { storageUpdate(body).unwrapNullable() }
    }

    suspend fun delete(id: Int) {
        session.request { storageDelete(id).unwrapNullable() }
    }

    suspend fun setEnabled(id: Int, enabled: Boolean) {
        session.request { (if (enabled) storageEnable(id) else storageDisable(id)).unwrapNullable() }
    }

    suspend fun reloadAll() {
        session.request { storageLoadAll().unwrapNullable() }
    }

    suspend fun driverNames(): List<String> = session.request { driverNames().unwrap().sorted() }

    suspend fun driverInfo(driver: String): DriverInfo = session.request { driverInfo(driver).unwrap() }

    companion object {
        fun JsonObject.toStorage(): Storage = Storage(
            id = int("id"),
            mountPath = str("mount_path"),
            driver = str("driver"),
            status = str("status"),
            disabled = get("disabled")?.jsonPrimitive?.booleanOrNull ?: false,
            remark = str("remark"),
            order = int("order"),
            modified = str("modified"),
            raw = this,
        )

        private fun JsonObject.str(key: String): String =
            runCatching { get(key)?.jsonPrimitive?.content }.getOrNull().orEmpty()

        private fun JsonObject.int(key: String): Int =
            runCatching { get(key)?.jsonPrimitive?.intOrNull }.getOrNull() ?: 0
    }
}

@Singleton
class UserRepository @Inject constructor(
    private val session: SessionManager,
) {
    suspend fun list(): List<UserDto> = session.request { userList().unwrap().content.orEmpty() }

    suspend fun get(id: Int): UserDto = session.request { userGet(id).unwrap() }

    suspend fun create(user: UserDto) {
        session.request { userCreate(user).unwrapNullable() }
    }

    /** password 为空时服务端保持原密码不变 */
    suspend fun update(user: UserDto) {
        session.request { userUpdate(user).unwrapNullable() }
    }

    suspend fun delete(id: Int) {
        session.request { userDelete(id).unwrapNullable() }
    }

    suspend fun cancel2fa(id: Int) {
        session.request { userCancel2fa(id).unwrapNullable() }
    }
}

@Singleton
class SettingRepository @Inject constructor(
    private val session: SessionManager,
) {
    suspend fun list(group: Int): List<SettingItem> =
        session.request { settingList(group).unwrapNullable().orEmpty().sortedBy { it.index } }

    suspend fun save(items: List<SettingItem>) {
        session.request { settingSave(items).unwrapNullable() }
    }
}

@Singleton
class TaskRepository @Inject constructor(
    private val session: SessionManager,
) {
    private fun scope(): String = if (session.currentUser.value?.isAdmin != false) "admin/task" else "task"

    suspend fun undone(type: TaskType): List<TaskInfo> =
        session.request { taskUndone(scope(), type.path).unwrapNullable().orEmpty() }

    suspend fun done(type: TaskType): List<TaskInfo> =
        session.request { taskDone(scope(), type.path).unwrapNullable().orEmpty() }

    /** [action]: cancel / delete / retry */
    suspend fun action(type: TaskType, action: String, tid: String) {
        session.request { taskAction(scope(), type.path, action, tid).unwrapNullable() }
    }

    /** [action]: clear_done / clear_succeeded / retry_failed */
    suspend fun bulk(type: TaskType, action: String) {
        session.request { taskBulkAction(scope(), type.path, action).unwrapNullable() }
    }
}
