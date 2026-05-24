package com.weaver.app.offline

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OutboxTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Each test starts from a clean prefs file.
        context
            .getSharedPreferences("weaver_outbox", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun enqueueAddsEntryAndExposesIt() {
        val outbox = Outbox(context)
        val entry = outbox.enqueue("make it blue", """{"type":"submit_prompt"}""")

        assertEquals(1, outbox.pendingCount)
        assertEquals(listOf(entry), outbox.entries.value)
        assertEquals("make it blue", entry.label)
        assertEquals("""{"type":"submit_prompt"}""", entry.payload)
        assertTrue(entry.id.isNotBlank())
        assertTrue(entry.queuedAt > 0)
    }

    @Test
    fun entriesAreKeptInFifoOrder() {
        val outbox = Outbox(context)
        outbox.enqueue("first", "a")
        outbox.enqueue("second", "b")
        outbox.enqueue("third", "c")

        assertEquals(listOf("first", "second", "third"), outbox.snapshot().map { it.label })
    }

    @Test
    fun removeDeletesOnlyThatEntry() {
        val outbox = Outbox(context)
        val a = outbox.enqueue("a", "1")
        val b = outbox.enqueue("b", "2")

        outbox.remove(a.id)

        assertEquals(listOf(b), outbox.snapshot())
        assertEquals(1, outbox.pendingCount)
    }

    @Test
    fun removeUnknownIdIsNoop() {
        val outbox = Outbox(context)
        outbox.enqueue("a", "1")
        outbox.remove("does-not-exist")
        assertEquals(1, outbox.pendingCount)
    }

    @Test
    fun eachEntryGetsAUniqueId() {
        val outbox = Outbox(context)
        val a = outbox.enqueue("a", "1")
        val b = outbox.enqueue("a", "1")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun entriesSurviveANewInstance() {
        Outbox(context).apply {
            enqueue("persisted-1", "x")
            enqueue("persisted-2", "y")
        }
        // A fresh instance models a cold process restart.
        val reloaded = Outbox(context)
        assertEquals(listOf("persisted-1", "persisted-2"), reloaded.snapshot().map { it.label })
    }

    @Test
    fun removalIsPersisted() {
        val first = Outbox(context)
        val a = first.enqueue("a", "1")
        first.enqueue("b", "2")
        first.remove(a.id)

        assertEquals(listOf("b"), Outbox(context).snapshot().map { it.label })
    }
}
