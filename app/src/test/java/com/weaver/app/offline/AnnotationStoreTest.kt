package com.weaver.app.offline

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class AnnotationStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context
            .getSharedPreferences("weaver_annotations", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun toggleFavoriteFlipsStateAndReturnsIt() {
        val store = AnnotationStore(context)

        assertTrue("first toggle favorites the node", store.toggleFavorite("n1"))
        assertTrue(
            store.state.value.favorites
                .contains("n1"),
        )

        assertFalse("second toggle un-favorites it", store.toggleFavorite("n1"))
        assertFalse(
            store.state.value.favorites
                .contains("n1"),
        )
    }

    @Test
    fun favoritesAreIndependentPerNode() {
        val store = AnnotationStore(context)
        store.toggleFavorite("n1")
        store.toggleFavorite("n2")
        store.toggleFavorite("n1")

        assertEquals(setOf("n2"), store.state.value.favorites)
    }

    @Test
    fun addNoteStoresItAgainstTheTarget() {
        val store = AnnotationStore(context)
        val note = store.addNote("n1", "tighten the spacing")

        assertEquals("n1", note.targetId)
        assertEquals("tighten the spacing", note.text)
        assertTrue(note.id.isNotBlank())
        assertTrue(note.createdAt > 0)
        assertEquals(listOf(note), store.state.value.notes)
    }

    @Test
    fun multipleNotesPerTargetAreKept() {
        val store = AnnotationStore(context)
        store.addNote("n1", "one")
        store.addNote("n1", "two")
        store.addNote("n2", "other")

        assertEquals(
            listOf("one", "two"),
            store.state.value.notes
                .filter { it.targetId == "n1" }
                .map { it.text },
        )
        assertEquals(3, store.state.value.notes.size)
    }

    @Test
    fun favoritesAndNotesSurviveANewInstance() {
        AnnotationStore(context).apply {
            toggleFavorite("n1")
            addNote("n1", "revisit later")
        }
        val reloaded = AnnotationStore(context)

        assertTrue(
            reloaded.state.value.favorites
                .contains("n1"),
        )
        assertEquals(
            "revisit later",
            reloaded.state.value.notes
                .single()
                .text,
        )
    }

    @Test
    fun unfavoritingIsPersisted() {
        AnnotationStore(context).apply {
            toggleFavorite("n1")
            toggleFavorite("n1")
        }
        assertTrue(
            AnnotationStore(context)
                .state.value.favorites
                .isEmpty(),
        )
    }
}
