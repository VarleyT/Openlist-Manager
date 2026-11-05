package com.bytegrep.oplist_manager.data.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMemoryTest {

    @Test
    fun `position near start or end is not remembered`() {
        assertFalse(PositionRules.shouldRemember(5_000, 600_000))
        assertTrue(PositionRules.shouldRemember(60_000, 600_000))
        assertFalse(PositionRules.shouldRemember(590_000, 600_000))
        assertFalse(PositionRules.shouldRemember(7_000_000, 7_200_000))
    }

    @Test
    fun `unknown duration such as live stream is not remembered`() {
        assertFalse(PositionRules.shouldRemember(30_000, 0))
    }

    @Test
    fun `unknown duration keeps existing record instead of clearing it`() {
        // 媒体准备中退出：时长尚未知，不能清掉之前的进度
        assertEquals(PositionAction.KEEP, PositionRules.decide(0, 0))
        assertEquals(PositionAction.KEEP, PositionRules.decide(120_000, 0))
    }

    @Test
    fun `known duration saves or clears by remember rules`() {
        assertEquals(PositionAction.SAVE, PositionRules.decide(60_000, 600_000))
        assertEquals(PositionAction.CLEAR, PositionRules.decide(0, 600_000))
        assertEquals(PositionAction.CLEAR, PositionRules.decide(595_000, 600_000))
    }

    @Test
    fun `saved position round trips and rejects garbage`() {
        val saved = SavedPosition(61_000, 600_000, 1_800_000_000_000)
        assertEquals(saved, SavedPosition.decode(saved.encode()))
        assertNull(SavedPosition.decode("abc"))
        assertNull(SavedPosition.decode("1|2"))
    }

    @Test
    fun `natural order sorts episode numbers numerically`() {
        assertEquals(listOf("s01e02.mkv", "S01E03.mkv"), listOf("S01E03.mkv", "s01e02.mkv").sortedWith(NaturalOrder))
        assertEquals(listOf("第1集.mp4", "第2集.mp4", "第10集.mp4"), listOf("第10集.mp4", "第2集.mp4", "第1集.mp4").sortedWith(NaturalOrder))
    }

    @Test
    fun `natural order handles long numbers and leading zeros`() {
        assertTrue(NaturalOrder.compare("ep002", "ep10") < 0)
        assertTrue(NaturalOrder.compare("a99999999999999999999", "a100000000000000000000") < 0)
    }

    @Test
    fun `playlist navigation`() {
        val list = Playlist(listOf("/a/1.mp4", "/a/2.mp4", "/a/3.mp4"), 0).moveTo("/a/2.mp4")
        assertEquals("/a/2.mp4", list.current)
        assertTrue(list.hasPrevious && list.hasNext)
        assertEquals("/a/3.mp4", list.next()?.current)
        assertNull(list.next()?.next())
        assertEquals("/a/1.mp4", list.previous()?.current)
        assertFalse(Playlist.single("/x.mp4").hasNext)
    }
}
