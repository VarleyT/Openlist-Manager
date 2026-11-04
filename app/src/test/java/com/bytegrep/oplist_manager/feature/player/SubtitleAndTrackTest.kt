package com.bytegrep.oplist_manager.feature.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.bytegrep.oplist_manager.data.model.FsObject
import com.bytegrep.oplist_manager.data.player.PlaybackSourceResolver
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAndTrackTest {

    private val server = "http://192.168.1.2:5244".toHttpUrl()

    @Test
    fun `related subtitles become signed proxy urls sorted naturally`() {
        val related = listOf(
            FsObject(name = "电影.mkv", sign = "v"),
            FsObject(name = "电影.chs.ass", sign = "s1"),
            FsObject(name = "电影.en.srt", sign = "s2"),
            FsObject(name = "电影.nfo", sign = "x"),
            FsObject(name = "电影.vtt", isDir = true),
        )

        val subtitles = PlaybackSourceResolver.subtitlesOf(related, "/影视/电影.mkv", server, "/")

        assertEquals(listOf("电影.chs.ass", "电影.en.srt"), subtitles.map { it.name })
        assertEquals("text/x-ssa", subtitles[0].mimeType)
        assertEquals("application/x-subrip", subtitles[1].mimeType)
        val url = subtitles[0].url.toHttpUrl()
        assertEquals(listOf("p", "影视", "电影.chs.ass"), url.pathSegments)
        assertEquals("s1", url.queryParameter("sign"))
    }

    @Test
    fun `subtitle url includes user base path and server sub path and encodes special chars`() {
        val related = listOf(FsObject(name = "a #1?.srt"))

        val url = PlaybackSourceResolver.subtitlesOf(
            related,
            "/dir/a.mp4",
            "https://example.com/openlist/".toHttpUrl(),
            "/root",
        ).single().url.toHttpUrl()

        assertEquals(listOf("openlist", "p", "root", "dir", "a #1?.srt"), url.pathSegments)
        assertEquals(null, url.queryParameter("sign"))
        assertEquals(null, url.fragment)
    }

    @Test
    fun `audio label combines name language codec and channels`() {
        val format = Format.Builder()
            .setLabel("导演评论")
            .setLanguage("zh")
            .setSampleMimeType(MimeTypes.AUDIO_AC3)
            .setChannelCount(6)
            .build()

        assertEquals("导演评论 · 中文 · AC3 · 5.1", PlayerTracks.labelOf(format, C.TRACK_TYPE_AUDIO, 1))
    }

    @Test
    fun `extracted subtitle uses original codec and fallback label`() {
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES)
            .setCodecs(MimeTypes.TEXT_SSA)
            .build()
        assertEquals("ASS", PlayerTracks.labelOf(format, C.TRACK_TYPE_TEXT, 2))

        assertEquals("音轨 3", PlayerTracks.labelOf(Format.Builder().build(), C.TRACK_TYPE_AUDIO, 3))
    }

    @Test
    fun `duplicate labels get ordinals`() {
        val base = TrackOption(C.TRACK_TYPE_TEXT, "g0", 0, 0, "中文 · ASS", isSelected = false, isSupported = true)
        val result = PlayerTracks.distinctLabels(listOf(base, base.copy(trackIndex = 1), base.copy(label = "英语")))
        assertEquals(listOf("中文 · ASS 1", "中文 · ASS 2", "英语"), result.map { it.label })
    }

    @Test
    fun `track menu flags`() {
        val audio = TrackOption(C.TRACK_TYPE_AUDIO, "g0", 0, 0, "a", isSelected = true, isSupported = true)
        val text = TrackOption(C.TRACK_TYPE_TEXT, "g1", 1, 0, "t", isSelected = false, isSupported = true)

        assertTrue(TrackMenu(listOf(audio)).isEmpty)
        assertFalse(TrackMenu(listOf(audio), listOf(text)).isEmpty)
        assertTrue(TrackMenu(listOf(audio), listOf(text)).textOff)
        assertFalse(TrackMenu(listOf(audio), listOf(text.copy(isSelected = true))).textOff)
    }
}
