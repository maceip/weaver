package com.weaver.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

private const val PREFS_NAME = "weaver_projects"
private const val KEY_PROJECTS = "projects"

class ProjectRepository(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val _projects = MutableStateFlow(load())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    fun newProject(title: String = "Untitled"): Project {
        val now = System.currentTimeMillis()
        val project =
            Project(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = now,
                lastModified = now,
            )
        upsert(project)
        return project
    }

    fun upsert(project: Project) {
        _projects.update { current ->
            val without = current.filterNot { it.id == project.id }
            (listOf(project) + without).sortedByDescending { it.lastModified }
        }
        persist()
    }

    fun touch(id: String) {
        _projects.update { current ->
            current
                .map { if (it.id == id) it.copy(lastModified = System.currentTimeMillis()) else it }
                .sortedByDescending { it.lastModified }
        }
        persist()
    }

    fun bindStitchId(
        id: String,
        stitchProjectId: String,
        thumbUri: String? = null,
    ) {
        _projects.update { current ->
            current.map {
                if (it.id == id) {
                    it.copy(
                        stitchProjectId = stitchProjectId,
                        thumbUri = thumbUri ?: it.thumbUri,
                        isDraft = false,
                        lastModified = System.currentTimeMillis(),
                    )
                } else {
                    it
                }
            }
        }
        persist()
    }

    fun remove(id: String) {
        _projects.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    private fun load(): List<Project> {
        val raw = prefs.getString(KEY_PROJECTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Project>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        val raw = json.encodeToString(_projects.value)
        prefs.edit { putString(KEY_PROJECTS, raw) }
    }
}
