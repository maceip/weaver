package com.weaver.app.fold

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.Inbound
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FoldState(
    val isFolded: Boolean = false,
    val isHalfOpen: Boolean = false,
    /** Half-open with a horizontal hinge — the propped-up "tabletop" posture. */
    val isTabletop: Boolean = false,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
)

/** Pure mapping of a [FoldingFeature] (if any) into a [FoldState]. */
fun foldStateOf(
    fold: FoldingFeature?,
    widthPx: Int,
    heightPx: Int,
): FoldState =
    FoldState(
        isFolded =
            fold?.state == FoldingFeature.State.FLAT &&
                fold.orientation == FoldingFeature.Orientation.VERTICAL,
        isHalfOpen = fold?.state == FoldingFeature.State.HALF_OPENED,
        isTabletop =
            fold?.state == FoldingFeature.State.HALF_OPENED &&
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL,
        widthPx = widthPx,
        heightPx = heightPx,
    )

class FoldObserver(
    private val activity: Activity,
    private val bridge: Bridge,
) {
    private val _state = MutableStateFlow(FoldState())
    val state: StateFlow<FoldState> = _state.asStateFlow()

    fun observe(owner: LifecycleOwner) {
        val tracker = WindowInfoTracker.getOrCreate(activity)
        val metricsCalculator = WindowMetricsCalculator.getOrCreate()
        owner.lifecycleScope.launch {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tracker.windowLayoutInfo(activity).collect { info ->
                    val bounds = metricsCalculator.computeCurrentWindowMetrics(activity).bounds
                    val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                    val next = foldStateOf(fold, bounds.width(), bounds.height())
                    if (next != _state.value) {
                        _state.value = next
                        bridge.send(Inbound.ViewportChanged(next.widthPx, next.heightPx))
                    }
                }
            }
        }
    }
}
