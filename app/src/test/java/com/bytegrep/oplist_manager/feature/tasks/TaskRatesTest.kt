package com.bytegrep.oplist_manager.feature.tasks

import com.bytegrep.oplist_manager.core.common.RateSample
import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import com.bytegrep.oplist_manager.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskRatesTest {

    private fun running(id: String, progress: Double, total: Long = 1_000) =
        TypedTask(TaskType.COPY, TaskInfo(id = id, state = TaskState.RUNNING, progress = progress, totalBytes = total))

    @Test
    fun `transferred bytes derive from progress percent`() {
        assertEquals(250L, TaskInfo(id = "a", progress = 25.0, totalBytes = 1_000).transferredBytes())
        assertEquals(1_000L, TaskInfo(id = "a", progress = 120.0, totalBytes = 1_000).transferredBytes())
        assertNull(TaskInfo(id = "a", progress = 25.0, totalBytes = 0).transferredBytes())
    }

    @Test
    fun `update rates computes speed between polls`() {
        val first = updateTaskRates(emptyMap(), listOf(running("a", 10.0)), nowMillis = 0)
        val second = updateTaskRates(first, listOf(running("a", 30.0)), nowMillis = 2_000)

        // (300 - 100) 字节 / 2 秒
        assertEquals(100.0, second.getValue(running("a", 0.0).key).bytesPerSecond!!, 0.001)
    }

    @Test
    fun `update rates skips unknown size and non running tasks and drops ended ones`() {
        val pending = TypedTask(TaskType.COPY, TaskInfo(id = "p", state = TaskState.PENDING, progress = 0.0, totalBytes = 10))
        val previous = mapOf("gone" to RateSample(1, 0))

        val rates = updateTaskRates(previous, listOf(running("a", 10.0, total = 0), pending), nowMillis = 1_000)

        assertEquals(emptyMap<String, RateSample>(), rates)
    }

    @Test
    fun `running rate line includes remaining time`() {
        val info = TaskInfo(id = "a", state = TaskState.RUNNING, progress = 50.0, totalBytes = 2_048)

        assertEquals("1.0 KB/s · 剩余 1 秒", runningRateLine(info, RateSample(1_024, 0, bytesPerSecond = 1_024.0)))
        assertNull(runningRateLine(info, RateSample(1_024, 0)))
        assertNull(runningRateLine(info, null))
    }

    @Test
    fun `average speed uses start and end time`() {
        val info = TaskInfo(
            id = "a",
            progress = 100.0,
            totalBytes = 4_096,
            startTime = "2026-01-01T00:00:00Z",
            endTime = "2026-01-01T00:00:04+00:00",
        )

        assertEquals(1_024.0, averageSpeed(info)!!, 0.001)
    }

    @Test
    fun `average speed is null when time is missing invalid or zero`() {
        val base = TaskInfo(id = "a", progress = 100.0, totalBytes = 4_096, startTime = "2026-01-01T00:00:00Z")

        assertNull(averageSpeed(base))
        assertNull(averageSpeed(base.copy(endTime = "bad")))
        assertNull(averageSpeed(base.copy(endTime = "2026-01-01T00:00:00Z")))
        assertNull(averageSpeed(base.copy(endTime = "2026-01-01T00:00:04Z", totalBytes = 0)))
    }
}
