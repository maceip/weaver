package com.weaver.app.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Top-level destinations. The supporting-pane scene strategy treats Overview
 * as the main pane and Focused as the supporting pane, so on the unfolded
 * Pixel Fold both render side-by-side; on a phone, Focused pushes Overview
 * off-screen.
 *
 * Boot flow: Login -> Home -> (Overview <-> Focused | MultiSelect).
 *
 * The project-scoped routes all carry their `projectId` so the back stack
 * stays unambiguous when the user hops between projects — predictive back
 * walks the real history (design D -> C -> B -> A -> overview) instead of
 * collapsing it.
 */
@Serializable
data object Login : NavKey

@Serializable
data object Home : NavKey

@Serializable
data class Overview(
    val projectId: String,
) : NavKey

@Serializable
data class Focused(
    val projectId: String,
    val nodeId: String,
) : NavKey

@Serializable
data class MultiSelect(
    val projectId: String,
) : NavKey

/** The project the user is currently inside, read off the back stack top. */
fun List<NavKey>.currentProjectId(): String? =
    when (val top = lastOrNull()) {
        is Overview -> top.projectId
        is Focused -> top.projectId
        is MultiSelect -> top.projectId
        else -> null
    }
