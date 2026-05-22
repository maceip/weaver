package com.weaver.app.offline

import android.content.Context
import com.weaver.app.bridge.StitchNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NodeCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("weaver_node_cache", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun nodes(vararg ids: String) = ids.map { StitchNode(id = it) }

    @Test
    fun loadReturnsWhatWasSaved() {
        val cache = NodeCache(context)
        cache.save("p1", nodes("a", "b", "c"))

        assertEquals(listOf("a", "b", "c"), cache.load("p1").map { it.id })
    }

    @Test
    fun loadUnknownProjectIsEmpty() {
        assertTrue(NodeCache(context).load("never-saved").isEmpty())
    }

    @Test
    fun savingAProjectAgainOverwritesIt() {
        val cache = NodeCache(context)
        cache.save("p1", nodes("old"))
        cache.save("p1", nodes("new1", "new2"))

        assertEquals(listOf("new1", "new2"), cache.load("p1").map { it.id })
    }

    @Test
    fun snapshotsSurviveANewInstance() {
        NodeCache(context).save("p1", nodes("a", "b"))
        assertEquals(listOf("a", "b"), NodeCache(context).load("p1").map { it.id })
    }

    @Test
    fun onlyTheSixMostRecentProjectsAreRetained() {
        val cache = NodeCache(context)
        for (i in 1..7) cache.save("p$i", nodes("n$i"))

        // p1 was the oldest of seven — evicted by the six-project cap.
        assertTrue(cache.load("p1").isEmpty())
        assertEquals(listOf("n2"), cache.load("p2").map { it.id })
        assertEquals(listOf("n7"), cache.load("p7").map { it.id })
    }

    @Test
    fun reSavingAProjectKeepsItFromBeingEvicted() {
        val cache = NodeCache(context)
        for (i in 1..6) cache.save("p$i", nodes("n$i"))
        cache.save("p1", nodes("fresh")) // touch p1 — now most recent
        cache.save("p7", nodes("n7")) // pushes the now-oldest (p2) out

        assertEquals(listOf("fresh"), cache.load("p1").map { it.id })
        assertTrue(cache.load("p2").isEmpty())
    }
}
