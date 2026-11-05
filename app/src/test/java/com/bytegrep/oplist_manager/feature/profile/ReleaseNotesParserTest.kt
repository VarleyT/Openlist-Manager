package com.bytegrep.oplist_manager.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesParserTest {

    @Test
    fun `release notes structure is parsed without markdown symbols`() {
        val markdown = """
            # OpenList Manager v1.3.1

            本版本让播放器可以播放 DTS 音频。

            ---

            ## 新增

            ### 支持 DTS

            - 能播放的格式包括 DTS。
              - 子项
            - **安装包变大**，原因说明。
        """.trimIndent()

        val blocks = ReleaseNotesParser.parse(markdown)

        assertEquals(
            listOf(
                NoteBlock.Paragraph(listOf(InlineSpan("本版本让播放器可以播放 DTS 音频。"))),
                NoteBlock.Gap,
                NoteBlock.Heading(listOf(InlineSpan("新增"))),
                NoteBlock.Heading(listOf(InlineSpan("支持 DTS"))),
                NoteBlock.Bullet(0, listOf(InlineSpan("能播放的格式包括 DTS。"))),
                NoteBlock.Bullet(1, listOf(InlineSpan("子项"))),
                NoteBlock.Bullet(0, listOf(InlineSpan("安装包变大", bold = true), InlineSpan("，原因说明。"))),
            ),
            blocks,
        )
    }

    @Test
    fun `inline code and links keep only text`() {
        assertEquals(
            listOf(
                InlineSpan("见 "),
                InlineSpan("v4.0 发布说明"),
                InlineSpan("，文件 "),
                InlineSpan("电影.mkv", code = true),
            ),
            ReleaseNotesParser.inline("见 [v4.0 发布说明](https://example.com)，文件 `电影.mkv`"),
        )
    }

    @Test
    fun `soft wrapped lines join without space between cjk and with space between ascii`() {
        val blocks = ReleaseNotesParser.parse("第一行中文\n第二行中文\nfoo\nbar")

        assertEquals(listOf(NoteBlock.Paragraph(listOf(InlineSpan("第一行中文第二行中文foo bar")))), blocks)
    }

    @Test
    fun `plain text without markdown is one paragraph and empty input has no blocks`() {
        assertEquals(
            listOf(NoteBlock.Paragraph(listOf(InlineSpan("修复若干问题")))),
            ReleaseNotesParser.parse("修复若干问题"),
        )
        assertEquals(emptyList<NoteBlock>(), ReleaseNotesParser.parse("   \n---\n"))
    }
}
