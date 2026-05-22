package com.weaver.app.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verifies the back stack always resolves the project the user is inside. */
class NavRoutesTest {
    @Test
    fun emptyStackHasNoProject() {
        assertNull(emptyList<NavKey>().currentProjectId())
    }

    @Test
    fun loginAndHomeHaveNoProject() {
        assertNull(listOf<NavKey>(Login).currentProjectId())
        assertNull(listOf<NavKey>(Login, Home).currentProjectId())
    }

    @Test
    fun overviewExposesItsProject() {
        assertEquals("p1", listOf<NavKey>(Home, Overview("p1")).currentProjectId())
    }

    @Test
    fun focusedExposesItsProject() {
        val stack = listOf<NavKey>(Home, Overview("p1"), Focused("p1", "node-9"))
        assertEquals("p1", stack.currentProjectId())
    }

    @Test
    fun multiSelectExposesItsProject() {
        assertEquals("p3", listOf<NavKey>(Home, MultiSelect("p3")).currentProjectId())
    }

    @Test
    fun topOfStackWinsAcrossProjects() {
        // Hopping from project p1 into p2 must report p2, not the older p1.
        val stack = listOf<NavKey>(Home, Overview("p1"), Overview("p2"))
        assertEquals("p2", stack.currentProjectId())
    }

    @Test
    fun deepFocusedHistoryStillResolvesProject() {
        // A rich design-to-design history is the whole point of pushing
        // Focused entries — currentProjectId must hold through it.
        val stack =
            listOf<NavKey>(
                Home,
                Overview("p1"),
                Focused("p1", "a"),
                Focused("p1", "b"),
                Focused("p1", "c"),
            )
        assertEquals("p1", stack.currentProjectId())
    }
}
