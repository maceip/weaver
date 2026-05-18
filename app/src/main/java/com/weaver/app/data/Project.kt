package com.weaver.app.data

import kotlinx.serialization.Serializable

/**
 * A weaver project. `stitchProjectId` is whatever Stitch hands us back when we
 * spawn a new design session (captured by the content script). It can be null
 * until Stitch produces an id — that lets us show the project in the Home list
 * immediately after the user types their seed prompt.
 */
@Serializable
data class Project(
    val id: String,
    val stitchProjectId: String? = null,
    val title: String,
    val createdAt: Long,
    val lastModified: Long,
    val thumbUri: String? = null,
    val isDraft: Boolean = true,
)
