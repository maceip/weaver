package com.weaver.app.offline

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.weaver.app.bridge.StitchNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class ProjectNodes(
    val projectId: String,
    val nodes: List<StitchNode>,
    val savedAt: Long,
)

/**
 * Persists the last canvas snapshot per project so the overview renders
 * immediately on a cold, offline launch instead of showing a blank canvas.
 * Capped to the most recently saved projects to keep prefs small.
 */
class NodeCache(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private var entries: List<ProjectNodes> = load()

    fun save(projectId: String, nodes: List<StitchNode>) {
        val next = ProjectNodes(projectId, nodes, System.currentTimeMillis())
        entries = (listOf(next) + entries.filterNot { it.projectId == projectId }).take(MAX_PROJECTS)
        persist()
    }

    fun load(projectId: String): List<StitchNode> =
        entries.firstOrNull { it.projectId == projectId }?.nodes ?: emptyList()

    private fun load(): List<ProjectNodes> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProjectNodes>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        prefs.edit { putString(KEY, json.encodeToString(entries)) }
    }

    private companion object {
        const val PREFS = "weaver_node_cache"
        const val KEY = "projects"
        const val MAX_PROJECTS = 6
    }
}
