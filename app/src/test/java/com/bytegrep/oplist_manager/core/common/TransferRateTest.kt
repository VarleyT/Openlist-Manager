package com.bytegrep.oplist_manager.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TransferRateTest {

    @Test
    fun `first sample has no rate`() {
        val sample = null.advance(bytes = 100, nowMillis = 1_000)

        assertEquals(RateSample(100, 1_000), sample)
        assertNull(sample.bytesPerSecond)
    }

    @Test
    fun `second sample computes bytes per second from delta`() {
        val sample = RateSample(0, 0).advance(bytes = 2_048, nowMillis = 2_000)

        assertEquals(1_024.0, sample.bytesPerSecond!!, 0.001)
    }

    @Test
    fun `later samples are smoothed with previous rate`() {
        val sample = RateSample(0, 0, bytesPerSecond = 1_000.0).advance(bytes = 3_000, nowMillis = 1_000)

        // 0.5 × 3000 + 0.5 × 1000
        assertEquals(2_000.0, sample.bytesPerSecond!!, 0.001)
    }

    @Test
    fun `sample within min interval keeps previous state`() {
        val previous = RateSample(0, 0, bytesPerSecond = 10.0)

        assertSame(previous, previous.advance(bytes = 500, nowMillis = 100))
    }

    @Test
    fun `bytes going backwards restarts sampling`() {
        val sample = RateSample(5_000, 0, bytesPerSecond = 100.0).advance(bytes = 10, nowMillis = 1_000)

        assertEquals(RateSample(10, 1_000), sample)
    }

    @Test
    fun `no progress between samples gives zero rate`() {
        val sample = RateSample(1_000, 0).advance(bytes = 1_000, nowMillis = 3_000)

        assertEquals(0.0, sample.bytesPerSecond!!, 0.0)
    }

    @Test
    fun `remaining seconds rounds up`() {
        assertEquals(3L, estimateRemainingSeconds(bytesPerSecond = 100.0, transferred = 750, total = 1_000))
    }

    @Test
    fun `remaining is null for zero rate or unknown total`() {
        assertNull(estimateRemainingSeconds(0.0, 0, 1_000))
        assertNull(estimateRemainingSeconds(null, 0, 1_000))
        assertNull(estimateRemainingSeconds(100.0, 0, 0))
        assertNull(estimateRemainingSeconds(100.0, 0, -1))
    }

    @Test
    fun `remaining is zero when transferred exceeds total`() {
        assertEquals(0L, estimateRemainingSeconds(100.0, 2_000, 1_000))
    }

    @Test
    fun `format remaining picks units by magnitude`() {
        assertEquals("0 秒", formatRemaining(0))
        assertEquals("59 秒", formatRemaining(59))
        assertEquals("1 分 5 秒", formatRemaining(65))
        assertEquals("2 小时 3 分", formatRemaining(2 * 3_600 + 3 * 60 + 9))
        assertEquals("超过 1 天", formatRemaining(86_400))
    }

    @Test
    fun `format rate line joins speed and remaining`() {
        assertEquals("1.5 MB/s · 剩余 12 秒", formatRateLine(1.5 * 1024 * 1024, 12))
        assertEquals("512 B/s", formatRateLine(512.0, null))
        assertNull(formatRateLine(null, 12))
    }
}
