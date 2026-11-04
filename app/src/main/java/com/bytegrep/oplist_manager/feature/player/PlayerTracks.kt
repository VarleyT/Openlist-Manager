package com.bytegrep.oplist_manager.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import java.util.Locale

/** 可选择的一条音轨或字幕轨 */
data class TrackOption(
    val type: Int,
    /** 轨道组 ID（TrackGroup.id）；选择时按 ID 匹配，避免菜单过期后按下标选错 */
    val groupId: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val isSelected: Boolean,
    val isSupported: Boolean,
)

/** 轨道菜单数据 */
data class TrackMenu(
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
) {
    val textOff: Boolean get() = text.none { it.isSelected }
    val isEmpty: Boolean get() = audio.size <= 1 && text.isEmpty()
}

object PlayerTracks {

    /** 将播放器当前轨道转换为菜单；groupIndex 指向 [Tracks.groups] 的下标 */
    @OptIn(UnstableApi::class)
    fun menuOf(tracks: Tracks): TrackMenu {
        val audio = mutableListOf<TrackOption>()
        val text = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            val target = when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> text
                else -> return@forEachIndexed
            }
            for (trackIndex in 0 until group.length) {
                target += TrackOption(
                    type = group.type,
                    groupId = group.mediaTrackGroup.id,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = labelOf(group.getTrackFormat(trackIndex), group.type, target.size + 1),
                    isSelected = group.isTrackSelected(trackIndex),
                    isSupported = group.isTrackSupported(trackIndex),
                )
            }
        }
        return TrackMenu(distinctLabels(audio), distinctLabels(text))
    }

    /** 同名轨道（如未命名的简/繁中文字幕）追加序号区分：「中文 · ASS 1」「中文 · ASS 2」 */
    fun distinctLabels(options: List<TrackOption>): List<TrackOption> {
        val duplicates = options.groupingBy { it.label }.eachCount().filterValues { it > 1 }.keys
        val counters = mutableMapOf<String, Int>()
        return options.map { option ->
            if (option.label !in duplicates) return@map option
            val n = counters.merge(option.label, 1, Int::plus) ?: 1
            option.copy(label = "${option.label} $n")
        }
    }

    /** 「名称 · 语言 · 编码 声道」，缺失部分省略 */
    fun labelOf(format: Format, type: Int, ordinal: Int): String {
        val language = format.language?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
            ?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.SIMPLIFIED_CHINESE).ifBlank { it } }
        val parts = listOfNotNull(
            format.label?.takeIf { it.isNotBlank() },
            language?.takeIf { lang -> format.label?.contains(lang) != true },
            codecOf(format),
            channelsOf(format).takeIf { type == C.TRACK_TYPE_AUDIO },
        )
        val fallback = if (type == C.TRACK_TYPE_AUDIO) "音轨 $ordinal" else "字幕 $ordinal"
        return parts.joinToString(" · ").ifBlank { fallback }
    }

    /** 1.4 起字幕在提取阶段转为 cues，原始格式记录在 codecs 字段 */
    @OptIn(UnstableApi::class)
    private fun codecOf(format: Format): String? = when (
        if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) format.codecs else format.sampleMimeType
    ) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "ASS"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.APPLICATION_PGS -> "PGS"
        else -> null
    }

    private fun channelsOf(format: Format): String? = when (val count = format.channelCount) {
        Format.NO_VALUE, 0 -> null
        1 -> "单声道"
        2 -> "立体声"
        6 -> "5.1"
        8 -> "7.1"
        else -> "${count}声道"
    }
}
