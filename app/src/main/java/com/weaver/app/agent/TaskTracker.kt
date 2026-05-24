package com.weaver.app.agent

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class TaskStatus { Queued, Running, Done, Failed }

@Serializable
data class TaskEntry(
    val id: String,
    val prompt: String,
    val status: TaskStatus,
    val startedAt: Long,
)

/**
 * Recent prompts the user fired, with their completion status. Backs the
 * floating agent orb. Persisted so a queued-while-offline prompt still shows
 * after a cold restart.
 */
class TaskTracker(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<TaskEntry>> = _tasks.asStateFlow()

    /** Records a fired prompt. [queued] is true when buffered offline. */
    fun submit(
        prompt: String,
        queued: Boolean,
    ): String {
        val entry =
            TaskEntry(
                id = UUID.randomUUID().toString(),
                prompt = prompt,
                status = if (queued) TaskStatus.Queued else TaskStatus.Running,
                startedAt = System.currentTimeMillis(),
            )
        _tasks.update { (it + entry).takeLast(MAX) }
        persist()
        return entry.id
    }

    /** Buffered prompts have left the outbox — they are now in flight. */
    fun promoteQueued() {
        _tasks.update { list ->
            list.map { if (it.status == TaskStatus.Queued) it.copy(status = TaskStatus.Running) else it }
        }
        persist()
    }

    /** Settles the oldest in-flight task when a session completes (or errors). */
    fun settleOldestRunning(failed: Boolean = false) {
        _tasks.update { list ->
            val idx = list.indexOfFirst { it.status == TaskStatus.Running }
            if (idx < 0) {
                list
            } else {
                list.toMutableList().also {
                    it[idx] = it[idx].copy(status = if (failed) TaskStatus.Failed else TaskStatus.Done)
                }
            }
        }
        persist()
    }

    private fun load(): List<TaskEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<TaskEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        prefs.edit { putString(KEY, json.encodeToString(_tasks.value)) }
    }

    private companion object {
        const val PREFS = "weaver_tasks"
        const val KEY = "tasks"
        const val MAX = 12
    }
}
