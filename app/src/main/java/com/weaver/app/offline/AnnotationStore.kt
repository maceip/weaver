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

@Serializable
data class Note(
    val id: String,
    val targetId: String,
    val text: String,
    val createdAt: Long,
)

@Serializable
data class Annotations(
    val favorites: Set<String> = emptySet(),
    val notes: List<Note> = emptyList(),
)

/**
 * On-device favorites and notes, keyed by Stitch node id. This is the local
 * source of truth: starring and note-taking always work, offline or logged out.
 * Mutations are mirrored to Stitch separately via the bridge / [Outbox].
 */
class AnnotationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<Annotations> = _state.asStateFlow()

    /** Flips the favorite flag for [nodeId]; returns the new favorite state. */
    fun toggleFavorite(nodeId: String): Boolean {
        val nowFavorite = nodeId !in _state.value.favorites
        _state.update {
            val favorites = if (nowFavorite) it.favorites + nodeId else it.favorites - nodeId
            it.copy(favorites = favorites)
        }
        persist()
        return nowFavorite
    }

    fun addNote(targetId: String, text: String): Note {
        val note = Note(UUID.randomUUID().toString(), targetId, text, System.currentTimeMillis())
        _state.update { it.copy(notes = it.notes + note) }
        persist()
        return note
    }

    private fun load(): Annotations {
        val raw = prefs.getString(KEY, null) ?: return Annotations()
        return runCatching { json.decodeFromString<Annotations>(raw) }.getOrDefault(Annotations())
    }

    private fun persist() {
        prefs.edit { putString(KEY, json.encodeToString(_state.value)) }
    }

    private companion object {
        const val PREFS = "weaver_annotations"
        const val KEY = "annotations"
    }
}
