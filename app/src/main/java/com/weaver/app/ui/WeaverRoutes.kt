package com.weaver.app.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level destinations. The supporting-pane scene strategy treats Overview
 * as the main pane and Focused as the supporting pane, so on the unfolded
 * Pixel Fold both panes render side-by-side; on a phone, Focused pushes
 * Overview off-screen.
 */
@Serializable
data object Overview : NavKey

@Serializable
data class Focused(val nodeId: String) : NavKey

@Serializable
data object MultiSelect : NavKey
