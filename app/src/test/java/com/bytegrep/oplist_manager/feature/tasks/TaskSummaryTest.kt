package com.bytegrep.oplist_manager.feature.tasks

import com.bytegrep.oplist_manager.data.model.TaskInfo
import com.bytegrep.oplist_manager.data.model.TaskState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskSummaryTest {

    private fun task(id: String, state: Int) = TaskInfo(id = id, state = state)

    @Test
    fun `empty list yields zero summary`() {
        assertEquals(TaskSummary(), summarize(emptyList()))
    }

    @Test
    fun `counts each state bucket`() {
        val tasks = listOf(
            task("1", TaskState.RUNNING),
            task("2", TaskState.RUNNING),
            task("3", TaskState.PENDING),
            task("4", TaskState.SUCCEEDED),
            task("5", TaskState.SUCCEEDED),
            task("6", TaskState.SUCCEEDED),
            task("7", TaskState.FAILED),
        )
        assertEquals(TaskSummary(running = 2, pending = 1, succeeded = 3, failed = 1), summarize(tasks))
    }

    @Test
    fun `errored counts as failed and waiting retry counts as pending`() {
        val tasks = listOf(task("1", TaskState.ERRORED), task("2", TaskState.WAITING_RETRY))
        assertEquals(TaskSummary(pending = 1, failed = 1), summarize(tasks))
    }

    @Test
    fun `transitional states count as running`() {
        val tasks = listOf(
            task("1", TaskState.CANCELING),
            task("2", TaskState.FAILING),
            task("3", TaskState.BEFORE_RETRY),
        )
        assertEquals(TaskSummary(running = 3), summarize(tasks))
    }

    @Test
    fun `canceled and unknown states are not counted`() {
        val tasks = listOf(task("1", TaskState.CANCELED), task("2", 99))
        assertEquals(TaskSummary(), summarize(tasks))
    }

    @Test
    fun `parseUploadProgress supports fraction and percent`() {
        assertEquals(0.5f, parseUploadProgress(0.5f) ?: -1f, 0.0001f)
        assertEquals(0.25f, parseUploadProgress(0.25) ?: -1f, 0.0001f)
        assertEquals(0.4f, parseUploadProgress(40) ?: -1f, 0.0001f)
        assertEquals(1f, parseUploadProgress(150L) ?: -1f, 0.0001f)
        assertNull(parseUploadProgress("50"))
        assertNull(parseUploadProgress(null))
    }
}
