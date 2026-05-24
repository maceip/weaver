package com.weaver.app.agent

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TaskTrackerTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("weaver_tasks", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun submitOnlineIsRunning() {
        val tracker = TaskTracker(context)
        tracker.submit("make it minimal", queued = false)
        assertEquals(
            TaskStatus.Running,
            tracker.tasks.value
                .single()
                .status,
        )
    }

    @Test
    fun submitOfflineIsQueued() {
        val tracker = TaskTracker(context)
        val id = tracker.submit("make it minimal", queued = true)
        val task = tracker.tasks.value.single()
        assertEquals(TaskStatus.Queued, task.status)
        assertEquals(id, task.id)
        assertEquals("make it minimal", task.prompt)
    }

    @Test
    fun promoteQueuedMovesQueuedToRunningOnly() {
        val tracker = TaskTracker(context)
        tracker.submit("a", queued = false) // Running
        tracker.submit("b", queued = true) // Queued
        tracker.settleOldestRunning() // a -> Done

        tracker.promoteQueued()

        val byPrompt = tracker.tasks.value.associate { it.prompt to it.status }
        assertEquals(TaskStatus.Done, byPrompt["a"]) // untouched
        assertEquals(TaskStatus.Running, byPrompt["b"]) // promoted
    }

    @Test
    fun settleOldestRunningCompletesInOrder() {
        val tracker = TaskTracker(context)
        tracker.submit("a", queued = false)
        tracker.submit("b", queued = false)

        tracker.settleOldestRunning()
        assertEquals(
            listOf(TaskStatus.Done, TaskStatus.Running),
            tracker.tasks.value.map { it.status },
        )

        tracker.settleOldestRunning()
        assertEquals(
            listOf(TaskStatus.Done, TaskStatus.Done),
            tracker.tasks.value.map { it.status },
        )
    }

    @Test
    fun settleOldestRunningCanFail() {
        val tracker = TaskTracker(context)
        tracker.submit("a", queued = false)
        tracker.settleOldestRunning(failed = true)
        assertEquals(
            TaskStatus.Failed,
            tracker.tasks.value
                .single()
                .status,
        )
    }

    @Test
    fun settleWithNoRunningTaskIsNoop() {
        val tracker = TaskTracker(context)
        tracker.submit("queued", queued = true)
        tracker.settleOldestRunning()
        assertEquals(
            TaskStatus.Queued,
            tracker.tasks.value
                .single()
                .status,
        )
    }

    @Test
    fun onlyTheTwelveMostRecentTasksAreRetained() {
        val tracker = TaskTracker(context)
        for (i in 1..13) tracker.submit("prompt-$i", queued = false)

        val prompts = tracker.tasks.value.map { it.prompt }
        assertEquals(12, prompts.size)
        assertTrue("oldest task dropped", "prompt-1" !in prompts)
        assertEquals("prompt-13", prompts.last())
    }

    @Test
    fun tasksSurviveANewInstance() {
        TaskTracker(context).submit("queued offline", queued = true)
        // Models a cold restart while still offline — the orb must still
        // show the queued prompt.
        val reloaded = TaskTracker(context)
        assertEquals(
            TaskStatus.Queued,
            reloaded.tasks.value
                .single()
                .status,
        )
        assertEquals(
            "queued offline",
            reloaded.tasks.value
                .single()
                .prompt,
        )
    }
}
