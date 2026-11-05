package com.bytegrep.oplist_manager.data.model

import com.bytegrep.oplist_manager.di.NetworkModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FsModelsTest {

    private val json: Json = NetworkModule.provideJson()

    @Test
    fun `FsObject maps snake case is_dir and fills missing fields with defaults`() {
        // Arrange
        val raw = """{"name":"movies","is_dir":true,"unknown_field":1}"""

        // Act
        val obj = json.decodeFromString<FsObject>(raw)

        // Assert
        assertEquals("movies", obj.name)
        assertTrue(obj.isDir)
        assertEquals(0L, obj.size)
        assertEquals("", obj.sign)
        assertEquals(FsType.UNKNOWN, obj.type)
    }

    @Test
    fun `FsListData tolerates null content returned for empty directories`() {
        // Arrange
        val raw = """{"content":null,"total":0,"write":true,"provider":"Local"}"""

        // Act
        val data = json.decodeFromString<FsListData>(raw)

        // Assert
        assertNull(data.content)
        assertTrue(data.write)
        assertEquals("Local", data.provider)
    }

    @Test
    fun `FsGetData decodes raw_url and related subtitle candidates`() {
        // Arrange
        val raw = """
            {"name":"a.mkv","size":1024,"is_dir":false,"type":2,
             "raw_url":"https://cdn/a.mkv","provider":"S3",
             "related":[{"name":"a.srt","size":10,"type":4}]}
        """.trimIndent()

        // Act
        val data = json.decodeFromString<FsGetData>(raw)

        // Assert
        assertEquals("https://cdn/a.mkv", data.rawUrl)
        assertEquals(FsType.VIDEO, data.type)
        assertEquals(listOf("a.srt"), data.related?.map { it.name })
        assertEquals(FsType.TEXT, data.related?.first()?.type)
    }

    @Test
    fun `FsGetData coerces null type to UNKNOWN instead of failing`() {
        // Arrange
        val raw = """{"name":"x","type":null,"related":null}"""

        // Act
        val data = json.decodeFromString<FsGetData>(raw)

        // Assert
        assertEquals(FsType.UNKNOWN, data.type)
        assertNull(data.related)
    }

    @Test
    fun `FsLinkData decodes multi value header map and allows missing header`() {
        // Arrange
        val withHeader = """{"url":"https://x/y","header":{"Referer":["https://a"],"Cookie":["c1","c2"]}}"""
        val withoutHeader = """{"url":"https://x/y"}"""

        // Act
        val a = json.decodeFromString<FsLinkData>(withHeader)
        val b = json.decodeFromString<FsLinkData>(withoutHeader)

        // Assert
        assertEquals(listOf("c1", "c2"), a.header?.get("Cookie"))
        assertEquals(listOf("https://a"), a.header?.get("Referer"))
        assertNull(b.header)
        assertEquals("https://x/y", b.url)
    }

    @Test
    fun `FsListRequest encodes per_page and default fields`() {
        // Act
        val obj = json.encodeToJsonElement(FsListRequest.serializer(), FsListRequest(path = "/a", perPage = 50)).jsonObject

        // Assert
        assertEquals("/a", obj["path"]?.jsonPrimitive?.content)
        assertEquals(50, obj["per_page"]?.jsonPrimitive?.int)
        assertEquals(1, obj["page"]?.jsonPrimitive?.int)
        assertEquals("", obj["password"]?.jsonPrimitive?.content)
        assertEquals(false, obj["refresh"]?.jsonPrimitive?.boolean)
        assertFalse(obj.containsKey("perPage"))
    }

    @Test
    fun `FsDirsRequest encodes force_root with snake case`() {
        // Act
        val obj = json.encodeToJsonElement(FsDirsRequest.serializer(), FsDirsRequest(path = "/", forceRoot = true)).jsonObject

        // Assert
        assertEquals(true, obj["force_root"]?.jsonPrimitive?.boolean)
        assertFalse(obj.containsKey("forceRoot"))
    }

    @Test
    fun `FsSearchRequest defaults to all scope first page and 100 per page`() {
        // Act
        val obj = json.encodeToJsonElement(
            FsSearchRequest.serializer(),
            FsSearchRequest(parent = "/", keywords = "abc"),
        ).jsonObject

        // Assert
        assertEquals(0, obj["scope"]?.jsonPrimitive?.int)
        assertEquals(1, obj["page"]?.jsonPrimitive?.int)
        assertEquals(100, obj["per_page"]?.jsonPrimitive?.int)
        assertEquals("abc", obj["keywords"]?.jsonPrimitive?.content)
    }

    @Test
    fun `FsSearchItem decodes is_dir and parent path`() {
        // Arrange
        val raw = """{"parent":"/docs","name":"sub","is_dir":true,"size":0,"type":1}"""

        // Act
        val item = json.decodeFromString<FsSearchItem>(raw)

        // Assert
        assertEquals("/docs", item.parent)
        assertTrue(item.isDir)
        assertEquals(FsType.FOLDER, item.type)
    }

    @Test
    fun `MoveCopyRequest encodes src_dir dst_dir and names`() {
        // Act
        val obj = json.encodeToJsonElement(
            MoveCopyRequest.serializer(),
            MoveCopyRequest(srcDir = "/a", dstDir = "/b", names = listOf("x", "y")),
        ).jsonObject

        // Assert
        assertEquals("/a", obj["src_dir"]?.jsonPrimitive?.content)
        assertEquals("/b", obj["dst_dir"]?.jsonPrimitive?.content)
        assertEquals(listOf("x", "y"), obj["names"]?.jsonArray?.map { it.jsonPrimitive.content })
    }

    @Test
    fun `write requests round trip through serialization`() {
        // Arrange
        val mkdir = MkdirRequest("/new")
        val rename = RenameRequest("/a.txt", "b.txt")
        val remove = RemoveRequest("/dir", listOf("1", "2"))
        val get = FsGetRequest("/a", password = "pw")
        val link = FsLinkRequest("/base/a")

        // Act & Assert
        assertEquals(mkdir, json.decodeFromString<MkdirRequest>(json.encodeToString(MkdirRequest.serializer(), mkdir)))
        assertEquals(rename, json.decodeFromString<RenameRequest>(json.encodeToString(RenameRequest.serializer(), rename)))
        assertEquals(remove, json.decodeFromString<RemoveRequest>(json.encodeToString(RemoveRequest.serializer(), remove)))
        assertEquals(get, json.decodeFromString<FsGetRequest>(json.encodeToString(FsGetRequest.serializer(), get)))
        assertEquals(link, json.decodeFromString<FsLinkRequest>(json.encodeToString(FsLinkRequest.serializer(), link)))
    }

    @Test
    fun `FsDirItem decodes with optional modified`() {
        // Act
        val item = json.decodeFromString<FsDirItem>("""{"name":"d"}""")

        // Assert
        assertEquals(FsDirItem(name = "d", modified = ""), item)
    }
}
