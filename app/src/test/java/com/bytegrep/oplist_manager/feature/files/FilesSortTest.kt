package com.bytegrep.oplist_manager.feature.files

import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.model.FsType
import com.bytegrep.oplist_manager.data.repository.FileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesSortTest {

    private val items = listOf(
        FsObject(name = "b.txt", size = 300, modified = "2025-01-10T10:00:00+08:00"),
        FsObject(name = "Docs", isDir = true, modified = "2025-01-01T00:00:00+08:00"),
        FsObject(name = "a.mp4", size = 1000, modified = "2025-01-20T10:00:00+08:00"),
        FsObject(name = "archive", isDir = true, modified = "2025-01-15T00:00:00+08:00"),
        FsObject(name = "C.pdf", size = 50, modified = "2025-01-05T10:00:00+08:00"),
    )

    private fun names(sort: FileSort) = sortFiles(items, sort).map { it.name }

    @Test
    fun `directories always come first`() {
        SortField.entries.forEach { field ->
            listOf(true, false).forEach { asc ->
                val sorted = sortFiles(items, FileSort(field, asc))
                assertTrue(sorted.take(2).all { it.isDir })
                assertTrue(sorted.drop(2).none { it.isDir })
            }
        }
    }

    @Test
    fun `name ascending is case insensitive`() {
        assertEquals(listOf("archive", "Docs", "a.mp4", "b.txt", "C.pdf"), names(FileSort(SortField.NAME, true)))
    }

    @Test
    fun `name descending keeps directories first`() {
        assertEquals(listOf("Docs", "archive", "C.pdf", "b.txt", "a.mp4"), names(FileSort(SortField.NAME, false)))
    }

    @Test
    fun `size ascending and descending`() {
        assertEquals(listOf("C.pdf", "b.txt", "a.mp4"), names(FileSort(SortField.SIZE, true)).drop(2))
        assertEquals(listOf("a.mp4", "b.txt", "C.pdf"), names(FileSort(SortField.SIZE, false)).drop(2))
    }

    @Test
    fun `modified time sorting`() {
        assertEquals(listOf("Docs", "archive", "C.pdf", "b.txt", "a.mp4"), names(FileSort(SortField.MODIFIED, true)))
        assertEquals(listOf("archive", "Docs", "a.mp4", "b.txt", "C.pdf"), names(FileSort(SortField.MODIFIED, false)))
    }

    @Test
    fun `unparseable modified time sorts as oldest`() {
        val list = listOf(FsObject(name = "x", modified = "bad"), FsObject(name = "y", modified = "2024-01-01T00:00:00Z"))
        assertEquals(listOf("x", "y"), sortFiles(list, FileSort(SortField.MODIFIED, true)).map { it.name })
    }

    @Test
    fun `empty list stays empty`() {
        assertTrue(sortFiles(emptyList(), FileSort()).isEmpty())
    }

    @Test
    fun `encodeFilePath encodes space as percent20`() {
        assertEquals("%2Fmy%20docs%2Fa%20b.txt", FileRepository.encodeFilePath("/my docs/a b.txt"))
    }

    @Test
    fun `encodeFilePath encodes chinese and slash`() {
        val encoded = FileRepository.encodeFilePath("/文档/报告.pdf")
        assertEquals("%2F%E6%96%87%E6%A1%A3%2F%E6%8A%A5%E5%91%8A.pdf", encoded)
        assertFalse(encoded.contains('/'))
        assertFalse(encoded.contains('+'))
    }

    @Test
    fun `file kind classification`() {
        assertEquals(FileKind.FOLDER, fileKindOf("x", true, FsType.UNKNOWN))
        assertEquals(FileKind.PDF, fileKindOf("a.PDF", false, FsType.UNKNOWN))
        assertEquals(FileKind.ARCHIVE, fileKindOf("r.zip", false, FsType.UNKNOWN))
        assertEquals(FileKind.IMAGE, fileKindOf("p.png", false, FsType.UNKNOWN))
        assertEquals(FileKind.VIDEO, fileKindOf("v.bin", false, FsType.VIDEO))
        assertEquals(FileKind.TEXT, fileKindOf("main.kt", false, FsType.UNKNOWN))
        assertEquals(FileKind.OTHER, fileKindOf("data.bin", false, FsType.UNKNOWN))
    }

    @Test
    fun `text preview requires known type and size limit`() {
        assertTrue(canPreviewText("readme.md", FsType.UNKNOWN, 10))
        assertTrue(canPreviewText("noext", FsType.TEXT, 10))
        assertFalse(canPreviewText("big.log", FsType.TEXT, TEXT_PREVIEW_LIMIT))
        assertFalse(canPreviewText("a.bin", FsType.UNKNOWN, 10))
    }

    @Test
    fun `file name validation`() {
        assertNull(validateFileName("新建文件夹"))
        assertEquals("名称不能为空", validateFileName("  "))
        assertTrue(validateFileName("a/b") != null)
        assertTrue(validateFileName("..") != null)
    }

    @Test
    fun `search index disabled error is friendly`() {
        assertTrue(searchErrorMessage("search not available").contains("搜索索引"))
        assertEquals("连接超时", searchErrorMessage("连接超时"))
    }

    @Test
    fun `local file name is sanitized`() {
        assertEquals("a_b_c.txt", sanitizeLocalFileName("a/b:c.txt"))
        assertEquals("download", sanitizeLocalFileName("..."))
    }
}
