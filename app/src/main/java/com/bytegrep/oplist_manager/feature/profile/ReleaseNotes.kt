package com.bytegrep.oplist_manager.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bytegrep.oplist_manager.core.designsystem.theme.OpenListTheme
import com.bytegrep.oplist_manager.core.designsystem.theme.Spacing

/** 行内片段 */
data class InlineSpan(val text: String, val bold: Boolean = false, val code: Boolean = false)

/** 更新日志的块级结构 */
sealed interface NoteBlock {
    data class Heading(val spans: List<InlineSpan>) : NoteBlock
    data class Paragraph(val spans: List<InlineSpan>) : NoteBlock
    data class Bullet(val level: Int, val spans: List<InlineSpan>) : NoteBlock
    data object Gap : NoteBlock
}

/**
 * Release 正文（Markdown 子集）解析：只处理更新日志用到的标题、列表、加粗、行内代码、链接与分隔线。
 * 一级标题（版本名）不显示，弹窗标题已包含版本号。
 */
object ReleaseNotesParser {
    private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val DIVIDER = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val INLINE = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\([^)]*\)""")
    private const val INDENT_PER_LEVEL = 2
    private const val ASCII_LIMIT = 128

    fun parse(markdown: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        val paragraph = StringBuilder()
        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += NoteBlock.Paragraph(inline(paragraph.toString().trim()))
            paragraph.clear()
        }
        for (raw in markdown.lines()) {
            val line = raw.trimEnd()
            val heading = HEADING.matchEntire(line.trimStart())
            val bullet = BULLET.matchEntire(line)
            when {
                line.isBlank() -> flushParagraph()
                DIVIDER.matches(line) -> {
                    flushParagraph()
                    blocks += NoteBlock.Gap
                }
                heading != null -> {
                    flushParagraph()
                    if (heading.groupValues[1].length > 1) blocks += NoteBlock.Heading(inline(heading.groupValues[2]))
                }
                bullet != null -> {
                    flushParagraph()
                    val level = bullet.groupValues[1].replace("\t", "  ").length / INDENT_PER_LEVEL
                    blocks += NoteBlock.Bullet(level, inline(bullet.groupValues[2]))
                }
                else -> {
                    val text = line.trim()
                    // 软换行：两侧都是 ASCII 时补空格，中文之间直接相连
                    val needsSpace = paragraph.isNotEmpty() && paragraph.last().code < ASCII_LIMIT && text.first().code < ASCII_LIMIT
                    paragraph.append(if (needsSpace) " " else "").append(text)
                }
            }
        }
        flushParagraph()
        return blocks.dropWhile { it == NoteBlock.Gap }.dropLastWhile { it == NoteBlock.Gap }
    }

    fun inline(text: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        var last = 0
        for (match in INLINE.findAll(text)) {
            if (match.range.first > last) spans += InlineSpan(text.substring(last, match.range.first))
            val (bold, code, link) = match.destructured
            spans += when {
                bold.isNotEmpty() -> InlineSpan(bold, bold = true)
                code.isNotEmpty() -> InlineSpan(code, code = true)
                else -> InlineSpan(link)
            }
            last = match.range.last + 1
        }
        if (last < text.length) spans += InlineSpan(text.substring(last))
        return spans
    }
}

/** 以原生排版显示更新日志，不暴露 Markdown 符号 */
@Composable
fun ReleaseNotesText(markdown: String, modifier: Modifier = Modifier) {
    val blocks = ReleaseNotesParser.parse(markdown)
    val body = MaterialTheme.typography.bodyMedium
    val ink2 = OpenListTheme.colors.ink2
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        blocks.forEach { block ->
            when (block) {
                is NoteBlock.Heading -> Text(
                    annotated(block.spans),
                    style = MaterialTheme.typography.titleSmall,
                    color = OpenListTheme.colors.ink,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
                is NoteBlock.Paragraph -> Text(annotated(block.spans), style = body, color = ink2)
                is NoteBlock.Bullet -> Row(Modifier.padding(start = (BULLET_INDENT_DP * block.level).dp)) {
                    Text("•  ", style = body, color = ink2)
                    Text(annotated(block.spans), style = body, color = ink2)
                }
                NoteBlock.Gap -> Spacer(Modifier.height(Spacing.xs))
            }
        }
    }
}

private const val BULLET_INDENT_DP = 12

private fun annotated(spans: List<InlineSpan>): AnnotatedString = buildAnnotatedString {
    spans.forEach { span ->
        when {
            span.bold -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(span.text) }
            span.code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(span.text) }
            else -> append(span.text)
        }
    }
}
