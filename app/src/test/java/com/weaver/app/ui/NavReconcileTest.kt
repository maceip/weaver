package com.weaver.app.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end coverage of the deep nav-history feature: how Stitch's selection
 * reconciles into the back stack. Focused designs must be PUSHED so predictive
 * back walks the real history.
 */
class NavReconcileTest {

    private fun MutableList<NavKey>.apply(selection: List<String>) {
        when (val op = reconcileSelection(this, selection)) {
            is NavReconcile.Push -> add(op.key)
            NavReconcile.PopTop -> if (isNotEmpty()) removeAt(size - 1)
            null -> Unit
        }
    }

    @Test
    fun noReconcileOutsideAProject() {
        assertNull(reconcileSelection(emptyList(), listOf("a")))
        assertNull(reconcileSelection(listOf(Login), listOf("a")))
        assertNull(reconcileSelection(listOf(Login, Home), listOf("a")))
    }

    @Test
    fun growingSelectionFromOverviewPushesMultiSelect() {
        val op = reconcileSelection(listOf(Home, Overview("p")), listOf("a", "b"))
        assertEquals(NavReconcile.Push(MultiSelect("p")), op)
    }

    @Test
    fun clearingSelectionPopsMultiSelect() {
        val op = reconcileSelection(listOf(Home, Overview("p"), MultiSelect("p")), emptyList())
        assertEquals(NavReconcile.PopTop, op)
    }

    @Test
    fun switchingFocusedNodePushesRatherThanReplaces() {
        val op = reconcileSelection(
            listOf(Home, Overview("p"), Focused("p", "a")),
            listOf("b"),
        )
        assertEquals(NavReconcile.Push(Focused("p", "b")), op)
    }

    @Test
    fun reselectingTheSameFocusedNodeIsANoop() {
        assertNull(
            reconcileSelection(listOf(Home, Overview("p"), Focused("p", "a")), listOf("a")),
        )
    }

    @Test
    fun multiSelectIsNotPushedWhileAlreadyFocused() {
        assertNull(
            reconcileSelection(listOf(Home, Overview("p"), Focused("p", "a")), listOf("a", "b")),
        )
    }

    @Test
    fun crossProjectReconcileUsesTheTopProject() {
        val op = reconcileSelection(
            listOf(Home, Overview("p1"), Overview("p2"), Focused("p2", "a")),
            listOf("b"),
        )
        assertEquals(NavReconcile.Push(Focused("p2", "b")), op)
    }

    @Test
    fun designToDesignHistoryAccumulates() {
        // Clicking design a -> b -> c -> d must leave a real four-deep history,
        // not a single collapsed entry — this is the headline behaviour.
        val stack = mutableListOf<NavKey>(Home, Overview("p"), Focused("p", "a"))
        stack.apply(listOf("b"))
        stack.apply(listOf("c"))
        stack.apply(listOf("d"))

        val focusedNodes = stack.filterIsInstance<Focused>().map { it.nodeId }
        assertEquals(listOf("a", "b", "c", "d"), focusedNodes)
        assertTrue("predictive back can walk every design", stack.size == 6)
    }
}
