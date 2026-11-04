package com.bytegrep.oplist_manager.feature.storage

import com.bytegrep.oplist_manager.data.model.DriverInfo
import com.bytegrep.oplist_manager.data.model.DriverItem
import com.bytegrep.oplist_manager.feature.storage.StorageFormKeys.additionKey
import com.bytegrep.oplist_manager.feature.storage.StorageFormKeys.commonKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageFormMapperTest {

    private val info = DriverInfo(
        common = listOf(
            DriverItem(name = "mount_path", type = "string", required = true),
            DriverItem(name = "order", type = "number"),
            DriverItem(name = "remark", type = "text"),
            DriverItem(name = "disable_index", type = "bool"),
            DriverItem(name = "webdav_policy", type = "select", options = "use_proxy_url,native_proxy", default = "native_proxy"),
            DriverItem(name = "order_by", type = "string"),
        ),
        additional = listOf(
            DriverItem(name = "root_folder_path", type = "string", required = true),
            DriverItem(name = "thumbnail", type = "bool"),
            DriverItem(name = "thumb_concurrency", type = "number", default = "16"),
            DriverItem(name = "order_by", type = "select", options = "name,size"),
        ),
    )

    private val raw: JsonObject = Json.parseToJsonElement(
        """
        {"id":3,"mount_path":"/local","order":2,"driver":"Local","status":"work",
         "addition":"{\"root_folder_path\":\"/disk/A\",\"thumbnail\":true,\"unknown_add\":42,\"order_by\":\"size\"}",
         "remark":"hello","disabled":false,"disable_index":false,"webdav_policy":"use_proxy_url",
         "order_by":"name","future_field":{"x":1}}
        """.trimIndent(),
    ).jsonObject

    private fun JsonObject.addition(): JsonObject =
        Json.parseToJsonElement(getValue("addition").jsonPrimitive.content).jsonObject

    @Test
    fun `extract reads top level common and parsed addition values`() {
        val values = extractFormValues(raw, info)

        assertEquals("/local", values[commonKey("mount_path")])
        assertEquals("2", values[commonKey("order")])
        assertEquals("false", values[commonKey("disable_index")])
        assertEquals("name", values[commonKey("order_by")])
        assertEquals("/disk/A", values[additionKey("root_folder_path")])
        assertEquals("true", values[additionKey("thumbnail")])
        assertEquals("size", values[additionKey("order_by")])
    }

    @Test
    fun `extract falls back to driver defaults for missing fields`() {
        val values = extractFormValues(raw, info)

        assertEquals("16", values[additionKey("thumb_concurrency")])
    }

    @Test
    fun `build converts bool number and string types`() {
        val values = extractFormValues(raw, info) + mapOf(
            commonKey("order") to "10",
            commonKey("disable_index") to "true",
            commonKey("remark") to "new remark",
            additionKey("thumbnail") to "false",
            additionKey("thumb_concurrency") to "2.5",
        )

        val result = buildStorageJson(raw, "Local", info, values)

        assertEquals(10L, result.getValue("order").jsonPrimitive.long)
        assertFalse(result.getValue("order").jsonPrimitive.isString)
        assertTrue(result.getValue("disable_index").jsonPrimitive.boolean)
        assertEquals("new remark", result.getValue("remark").jsonPrimitive.content)
        assertTrue(result.getValue("remark").jsonPrimitive.isString)
        val addition = result.addition()
        assertFalse(addition.getValue("thumbnail").jsonPrimitive.boolean)
        assertEquals(2.5, addition.getValue("thumb_concurrency").jsonPrimitive.double, 0.0)
    }

    @Test
    fun `addition string round trips and stays a string`() {
        val values = extractFormValues(raw, info) + (additionKey("root_folder_path") to "/disk/B")

        val result = buildStorageJson(raw, "Local", info, values)

        assertTrue(result.getValue("addition").jsonPrimitive.isString)
        val reExtracted = extractFormValues(result, info)
        assertEquals("/disk/B", reExtracted[additionKey("root_folder_path")])
        assertEquals("true", reExtracted[additionKey("thumbnail")])
    }

    @Test
    fun `build keeps unknown fields at top level and inside addition`() {
        val values = extractFormValues(raw, info) + (commonKey("mount_path") to "/renamed")

        val result = buildStorageJson(raw, "Local", info, values)

        assertEquals(3, result.getValue("id").jsonPrimitive.content.toInt())
        assertEquals("work", result.getValue("status").jsonPrimitive.content)
        assertEquals(JsonObject(mapOf("x" to JsonPrimitive(1))), result.getValue("future_field"))
        assertEquals(42L, result.addition().getValue("unknown_add").jsonPrimitive.long)
        assertEquals("/renamed", result.getValue("mount_path").jsonPrimitive.content)
    }

    @Test
    fun `common and additional fields with same name do not collide`() {
        val values = extractFormValues(raw, info) + (additionKey("order_by") to "name")

        val result = buildStorageJson(raw, "Local", info, values)

        assertEquals("name", result.getValue("order_by").jsonPrimitive.content)
        assertEquals("name", result.addition().getValue("order_by").jsonPrimitive.content)
    }

    @Test
    fun `unchanged fields keep original json values`() {
        val result = buildStorageJson(raw, "Local", info, extractFormValues(raw, info))

        assertEquals(raw.getValue("order"), result.getValue("order"))
        assertEquals(raw.getValue("disable_index"), result.getValue("disable_index"))
    }

    @Test
    fun `new storage contains driver common fields and serialized addition`() {
        val values = defaultFormValues(info) + mapOf(
            commonKey("mount_path") to "/new",
            additionKey("root_folder_path") to "/data",
        )

        val result = buildStorageJson(null, "Local", info, values)

        assertEquals("Local", result.getValue("driver").jsonPrimitive.content)
        assertEquals("/new", result.getValue("mount_path").jsonPrimitive.content)
        assertEquals(0L, result.getValue("order").jsonPrimitive.long)
        assertFalse(result.getValue("disable_index").jsonPrimitive.boolean)
        assertEquals("native_proxy", result.getValue("webdav_policy").jsonPrimitive.content)
        assertFalse(result.containsKey("id"))
        val addition = result.addition()
        assertEquals("/data", addition.getValue("root_folder_path").jsonPrimitive.content)
        assertEquals(16L, addition.getValue("thumb_concurrency").jsonPrimitive.long)
        assertFalse(addition.getValue("thumbnail").jsonPrimitive.boolean)
    }

    @Test
    fun `addition given as object or invalid string is tolerated`() {
        val objectAddition = JsonObject(
            mapOf("addition" to JsonObject(mapOf("root_folder_path" to JsonPrimitive("/obj")))),
        )
        val invalidAddition = JsonObject(mapOf("addition" to JsonPrimitive("not json")))

        assertEquals("/obj", extractFormValues(objectAddition, info)[additionKey("root_folder_path")])
        assertEquals("", extractFormValues(invalidAddition, info)[additionKey("root_folder_path")])
    }

    @Test
    fun `secret field detection and option parsing`() {
        assertTrue(isSecretField("refresh_token"))
        assertTrue(isSecretField("Password"))
        assertTrue(isSecretField("access_key_id"))
        assertFalse(isSecretField("root_folder_path"))
        assertEquals(listOf("a", "b"), DriverItem(name = "x", options = "a, b,").optionList())
    }
}
