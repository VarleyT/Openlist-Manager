package com.bytegrep.oplist_manager.feature.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskDetailsTest {

    @Test
    fun `copy task splits source and target with mount paths`() {
        val paths = parseTaskName("copy [/本地-B](/【D】电影/让子弹飞.mkv) to [/阿里云](/备份/电影)")

        assertEquals("/本地-B/【D】电影/让子弹飞.mkv", paths?.source)
        assertEquals("/阿里云/备份/电影", paths?.target)
    }

    @Test
    fun `root actual path collapses to mount path`() {
        val paths = parseTaskName("move [/local](/) to [/](/target)")

        assertEquals("/local", paths?.source)
        assertEquals("/target", paths?.target)
    }

    @Test
    fun `paths containing parentheses are kept intact`() {
        val paths = parseTaskName("merge [/a](/电影 (2010)/x.mkv) to [/b](/存档 (旧))")

        assertEquals("/a/电影 (2010)/x.mkv", paths?.source)
        assertEquals("/b/存档 (旧)", paths?.target)
    }

    @Test
    fun `upload task uses file name as source`() {
        val paths = parseTaskName("upload 照片.jpg to [/本地](/相册)")

        assertEquals("照片.jpg", paths?.source)
        assertEquals("/本地/相册", paths?.target)
    }

    @Test
    fun `offline download keeps url as source`() {
        val paths = parseTaskName("download magnet:?xt=urn:btih:abc to (/下载)")

        assertEquals("magnet:?xt=urn:btih:abc", paths?.source)
        assertEquals("/下载", paths?.target)
    }

    @Test
    fun `offline transfer upload uses url as source`() {
        val paths = parseTaskName("upload [/tmp/a.bin](https://x.com/a.bin) to [/115](/下载)")

        assertEquals("https://x.com/a.bin", paths?.source)
        assertEquals("/115/下载", paths?.target)
    }

    @Test
    fun `decompress task exposes inner path and hides password`() {
        val paths = parseTaskName("decompress [/本地](/a.zip)[/docs] to [/本地](/解压) with password <secret>")

        assertEquals("/本地/a.zip", paths?.source)
        assertEquals("/docs", paths?.innerPath)
        assertEquals("/本地/解压", paths?.target)
    }

    @Test
    fun `unknown format returns null`() {
        assertNull(parseTaskName("something else"))
        assertNull(parseTaskName(""))
    }

    @Test
    fun `maskTaskName hides decompress password`() {
        assertEquals(
            "decompress [/a](/b.zip)[/] to [/a](/c) with password <***>",
            maskTaskName("decompress [/a](/b.zip)[/] to [/a](/c) with password <p@ss>"),
        )
    }

    @Test
    fun `maskTaskName keeps empty password and other names unchanged`() {
        val noPassword = "decompress [/a](/b.zip)[/] to [/a](/c) with password <>"
        assertEquals(noPassword, maskTaskName(noPassword))
        assertEquals("copy [/a](/b) to [/c](/d)", maskTaskName("copy [/a](/b) to [/c](/d)"))
    }

    @Test
    fun `duration formats hours minutes seconds`() {
        assertEquals("1 小时 2 分 3 秒", formatTaskDuration("2026-11-05T17:00:00+08:00", "2026-11-05T18:02:03+08:00"))
        assertEquals("5 分 0 秒", formatTaskDuration("2026-11-05T17:00:00Z", "2026-11-05T17:05:00Z"))
        assertEquals("不到 1 秒", formatTaskDuration("2026-11-05T17:00:00Z", "2026-11-05T17:00:00.4Z"))
    }

    @Test
    fun `duration is null for missing invalid or negative ranges`() {
        assertNull(formatTaskDuration(null, "2026-11-05T17:00:00Z"))
        assertNull(formatTaskDuration("2026-11-05T17:00:00Z", ""))
        assertNull(formatTaskDuration("bad", "2026-11-05T17:00:00Z"))
        assertNull(formatTaskDuration("2026-11-05T18:00:00Z", "2026-11-05T17:00:00Z"))
    }
}
