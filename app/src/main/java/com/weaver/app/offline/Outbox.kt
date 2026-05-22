package com.weaver.app.offline

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

/** One buffered user action. [payload] is the already-serialized `Inbound` JSON. */
@Serializable
data class OutboxEntry(
    val id: String,
    val label: String,
    val payload: String,
    val queuedAt: Long,
)

/**
 * Disk-backed FIFO queue of actions taken while no transport could reach Stitch
 * (offline, or logged out). Survives process death. [com.weaver.app.bridge.Bridge]
 * drains it the moment a transport reports Ready.
 */
class Outbox(
    context: Context,
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<OutboxEntry>> = _entries.asStateFlow()

    val pendingCount: Int get() = _entries.value.size

    fun enqueue(
        label: String,
        payload: String,
    ): OutboxEntry {
        val entry =
            OutboxEntry(
                id = UUID.randomUUID().toString(),
                label = label,
                payload = payload,
                queuedAt = System.currentTimeMillis(),
            )
        _entries.update { it + entry }
        persist()
        return entry
    }

    /** Value snapshot for draining — caller sends each, then calls [remove]. */
    fun snapshot(): List<OutboxEntry> = _entries.value

    fun remove(id: String) {
        _entries.update { current -> current.filterNot { it.id == id } }
        persist()
    }

    private fun load(): List<OutboxEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<OutboxEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun persist() {
        prefs.edit { putString(KEY, json.encodeToString(_entries.value)) }
    }

    private companion object {
        const val PREFS = "weaver_outbox"
        const val KEY = "entries"
    }
}
