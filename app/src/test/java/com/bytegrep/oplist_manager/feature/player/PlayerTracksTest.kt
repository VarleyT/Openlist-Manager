package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTracksTest {

    private fun format(mime: String, language: String? = null, label: String? = null, channels: Int = Format.NO_VALUE) =
        Format.Builder()
            .setSampleMimeType(mime)
            .setLanguage(language)
            .setLabel(label)
            .setChannelCount(channels)
            .build()

    private fun group(id: String, formats: List<Format>, supported: List<Boolean>, selected: List<Boolean>) =
        Tracks.Group(
            TrackGroup(id, *formats.toTypedArray()),
            false,
            supported.map { if (it) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_SUBTYPE }.toIntArray(),
            selected.toBooleanArray(),
        )

    @Test
    fun `menu collects audio and text tracks and skips video groups`() {
        // Arrange
        val tracks = Tracks(
            listOf(
                group("v", listOf(format(MimeTypes.VIDEO_H264)), listOf(true), listOf(true)),
                group(
                    "a",
                    listOf(format(MimeTypes.AUDIO_AAC, "ja", channels = 2), format(MimeTypes.AUDIO_DTS, "ja", channels = 6)),
                    supported = listOf(true, false),
                    selected = listOf(true, false),
                ),
                group("t", listOf(format(MimeTypes.APPLICATION_SUBRIP, "zh")), listOf(true), listOf(false)),
            ),
        )

        // Act
        val menu = PlayerTracks.menuOf(tracks)

        // Assert
        assertEquals(listOf("日语 · AAC · 立体声", "日语 · DTS · 5.1"), menu.audio.map { it.label })
        assertEquals(listOf("a", "a"), menu.audio.map { it.groupId })
        assertEquals(listOf(1, 1), menu.audio.map { it.groupIndex })
        assertEquals(listOf(0, 1), menu.audio.map { it.trackIndex })
        assertEquals(listOf(true, false), menu.audio.map { it.isSelected })
        assertEquals(listOf(true, false), menu.audio.map { it.isSupported })
        val text = menu.text.single()
        assertEquals("中文 · SRT", text.label)
        assertEquals(C.TRACK_TYPE_TEXT, text.type)
        assertEquals(2, text.groupIndex)
        assertTrue(menu.textOff)
    }

    @Test
    fun `menu numbers duplicate unnamed subtitles across groups`() {
        val tracks = Tracks(
            listOf(
                group("t0", listOf(format(MimeTypes.TEXT_SSA, "zh")), listOf(true), listOf(true)),
                group("t1", listOf(format(MimeTypes.TEXT_SSA, "zh")), listOf(true), listOf(false)),
            ),
        )

        val menu = PlayerTracks.menuOf(tracks)

        assertEquals(listOf("中文 · ASS 1", "中文 · ASS 2"), menu.text.map { it.label })
        assertFalse(menu.textOff)
    }

    @Test
    fun `empty tracks give empty menu`() {
        val menu = PlayerTracks.menuOf(Tracks.EMPTY)

        assertTrue(menu.isEmpty)
        assertTrue(menu.audio.isEmpty() && menu.text.isEmpty())
    }

    @Test
    fun `audio codec names map from mime types`() {
        val expected = mapOf(
            MimeTypes.AUDIO_E_AC3 to "E-AC3",
            MimeTypes.AUDIO_E_AC3_JOC to "E-AC3",
            MimeTypes.AUDIO_DTS_HD to "DTS",
            MimeTypes.AUDIO_DTS_EXPRESS to "DTS",
            MimeTypes.AUDIO_TRUEHD to "TrueHD",
            MimeTypes.AUDIO_FLAC to "FLAC",
            MimeTypes.AUDIO_OPUS to "Opus",
            MimeTypes.AUDIO_MPEG to "MP3",
        )

        expected.forEach { (mime, codec) ->
            assertEquals(mime, codec, PlayerTracks.labelOf(format(mime), C.TRACK_TYPE_AUDIO, 1))
        }
    }

    @Test
    fun `subtitle codec names map from mime types and unknown codec falls back`() {
        assertEquals("WebVTT", PlayerTracks.labelOf(format(MimeTypes.TEXT_VTT), C.TRACK_TYPE_TEXT, 1))
        assertEquals("PGS", PlayerTracks.labelOf(format(MimeTypes.APPLICATION_PGS), C.TRACK_TYPE_TEXT, 1))
        assertEquals("字幕 4", PlayerTracks.labelOf(format(MimeTypes.APPLICATION_TTML), C.TRACK_TYPE_TEXT, 4))
    }

    @Test
    fun `channel count descriptions only apply to audio`() {
        val cases = mapOf(1 to "单声道", 2 to "立体声", 6 to "5.1", 8 to "7.1", 4 to "4声道")
        cases.forEach { (channels, text) ->
            assertEquals(text, PlayerTracks.labelOf(format("audio/unknown", channels = channels), C.TRACK_TYPE_AUDIO, 1))
        }
        assertEquals("音轨 2", PlayerTracks.labelOf(format("audio/unknown", channels = 0), C.TRACK_TYPE_AUDIO, 2))
        // 字幕轨不显示声道
        assertEquals("字幕 1", PlayerTracks.labelOf(format("text/unknown", channels = 2), C.TRACK_TYPE_TEXT, 1))
    }

    @Test
    fun `undetermined language and blank label are omitted`() {
        val label = PlayerTracks.labelOf(
            format(MimeTypes.AUDIO_AAC, language = C.LANGUAGE_UNDETERMINED, label = " "),
            C.TRACK_TYPE_AUDIO,
            1,
        )

        assertEquals("AAC", label)
    }

    @Test
    fun `language already contained in label is not repeated`() {
        val label = PlayerTracks.labelOf(
            format(MimeTypes.APPLICATION_SUBRIP, language = "zh", label = "中文简体"),
            C.TRACK_TYPE_TEXT,
            1,
        )

        assertEquals("中文简体 · SRT", label)
    }
}
