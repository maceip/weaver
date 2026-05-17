package com.weaver.app.bridge

import android.util.Log
import android.webkit.WebView
import com.easyhooon.dari.interceptor.DariInterceptor
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val TAG = "WeaverBridge"

class Bridge(
    private val interceptor: DariInterceptor? = null,
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val _nodes = MutableStateFlow<List<StitchNode>>(emptyList())
    val nodes: StateFlow<List<StitchNode>> = _nodes.asStateFlow()

    private val _selection = MutableStateFlow<List<String>>(emptyList())
    val selection: StateFlow<List<String>> = _selection.asStateFlow()

    private val _generation = MutableStateFlow<Map<String, GenerationState>>(emptyMap())
    val generation: StateFlow<Map<String, GenerationState>> = _generation.asStateFlow()

    private val _events = MutableSharedFlow<Outbound>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Outbound> = _events.asSharedFlow()

    private var webView: WebView? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach() {
        webView = null
    }

    fun handleOutbound(raw: String) {
        val message = runCatching { json.decodeFromString<Outbound>(raw) }
            .onFailure { Log.w(TAG, "decode outbound failed: ${it.message}") }
            .getOrNull() ?: return
        interceptor?.onWebToAppRequest(message::class.simpleName ?: "outbound", null, raw)
        when (message) {
            is Outbound.NodesUpdated -> _nodes.value = message.nodes
            is Outbound.SelectionChanged -> _selection.value = message.ids
            is Outbound.GenerationProgress -> _generation.update { it + (message.id to message.state) }
            is Outbound.AssetReady -> _events.tryEmit(message)
            is Outbound.ExportComplete -> _events.tryEmit(message)
            is Outbound.Error -> {
                Log.e(TAG, "stitch error ${message.code}: ${message.message}")
                _events.tryEmit(message)
            }
        }
    }

    fun send(message: Inbound) {
        val view = webView ?: run {
            Log.w(TAG, "send before attach: $message")
            return
        }
        val payload = json.encodeToString(message)
        interceptor?.onAppToWebRequest(message::class.simpleName ?: "inbound", null, payload)
        val escaped = payload.replace("\\", "\\\\").replace("'", "\\'")
        view.post {
            view.evaluateJavascript("window.__weaverBridge.receive('$escaped')", null)
        }
    }

    fun rawEventDebug(raw: String): JsonObject? = runCatching {
        json.decodeFromString<JsonObject>(raw)
    }.getOrNull()
}
