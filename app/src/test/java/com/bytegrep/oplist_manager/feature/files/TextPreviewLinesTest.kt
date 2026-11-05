package com.bytegrep.oplist_manager.feature.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPreviewLinesTest {

    @Test
    fun `splits on lf and crlf line endings`() {
        assertEquals(listOf("a", "b", "c"), splitPreviewLines("a\r\nb\nc"))
    }

    @Test
    fun `keeps empty lines in place`() {
        assertEquals(listOf("a", "", "b"), splitPreviewLines("a\n\nb"))
    }

    @Test
    fun `strips leading byte order mark`() {
        assertEquals(listOf("[Script Info]"), splitPreviewLines(Char(0xFEFF) + "[Script Info]"))
    }

    @Test
    fun `chunks lines longer than the limit`() {
        val lines = splitPreviewLines("abcdefg", maxLineLength = 3)
        assertEquals(listOf("abc", "def", "g"), lines)
    }

    @Test
    fun `huge single line never yields a chunk over the limit`() {
        val lines = splitPreviewLines("x".repeat(10_001), maxLineLength = 2_000)
        assertEquals(6, lines.size)
        assertTrue(lines.all { it.length <= 2_000 })
        assertEquals(10_001, lines.sumOf { it.length })
    }

    @Test
    fun `empty text yields a single empty line`() {
        assertEquals(listOf(""), splitPreviewLines(""))
    }
}
