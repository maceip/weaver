package com.weaver.app.fold

import android.graphics.Rect
import androidx.window.layout.FoldingFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the foldable posture mapping that drives the tabletop layout. */
class FoldObserverTest {
    /** [foldStateOf] only reads state + orientation; bounds is never touched. */
    private class FakeFold(
        override val state: FoldingFeature.State,
        override val orientation: FoldingFeature.Orientation,
    ) : FoldingFeature {
        override val bounds: Rect get() = throw UnsupportedOperationException()
        override val isSeparating: Boolean get() = false
        override val occlusionType: FoldingFeature.OcclusionType
            get() = FoldingFeature.OcclusionType.NONE
    }

    @Test
    fun noFoldingFeatureIsAFlatSlab() {
        val state = foldStateOf(null, widthPx = 1080, heightPx = 2400)
        assertFalse(state.isFolded)
        assertFalse(state.isHalfOpen)
        assertFalse(state.isTabletop)
        assertEquals(1080, state.widthPx)
        assertEquals(2400, state.heightPx)
    }

    @Test
    fun flatVerticalHingeIsFolded() {
        val state =
            foldStateOf(
                FakeFold(FoldingFeature.State.FLAT, FoldingFeature.Orientation.VERTICAL),
                1080,
                2400,
            )
        assertTrue(state.isFolded)
        assertFalse(state.isTabletop)
        assertFalse(state.isHalfOpen)
    }

    @Test
    fun halfOpenHorizontalHingeIsTabletop() {
        val state =
            foldStateOf(
                FakeFold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL),
                2200,
                1840,
            )
        assertTrue(state.isTabletop)
        assertTrue(state.isHalfOpen)
        assertFalse(state.isFolded)
    }

    @Test
    fun halfOpenVerticalHingeIsBookPostureNotTabletop() {
        val state =
            foldStateOf(
                FakeFold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.VERTICAL),
                2200,
                1840,
            )
        assertTrue(state.isHalfOpen)
        assertFalse("a vertical hinge is the book posture, not tabletop", state.isTabletop)
        assertFalse(state.isFolded)
    }

    @Test
    fun fullyOpenHorizontalHingeIsNeitherFoldedNorTabletop() {
        val state =
            foldStateOf(
                FakeFold(FoldingFeature.State.FLAT, FoldingFeature.Orientation.HORIZONTAL),
                2200,
                1840,
            )
        assertFalse(state.isFolded)
        assertFalse(state.isTabletop)
        assertFalse(state.isHalfOpen)
    }
}
