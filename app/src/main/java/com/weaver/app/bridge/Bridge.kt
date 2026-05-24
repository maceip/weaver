package com.weaver.app.bridge

import android.util.Log
import com.easyhooon.dari.interceptor.DariInterceptor
import com.weaver.app.bridge.transport.BridgeTransport
import com.weaver.app.bridge.transport.TransportStatus
import com.weaver.app.offline.Outbox
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val TAG = "WeaverBridge"

class Bridge(
    private val interceptor: DariInterceptor? = null,
    private val outbox: Outbox? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    val json: Json =
        Json {
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

    private val _agentLog = MutableStateFlow<List<AgentLogEntry>>(emptyList())
    val agentLog: StateFlow<List<AgentLogEntry>> = _agentLog.asStateFlow()

    /**
     * Active streaming sessions, keyed by sessionId. Tracks per-session sequence
     * number, latest stages, and finished-bytes when complete. Populated from
     * the fetch interceptor's tee of /AppCompanionAgentService/StreamCreateSession.
     */
    private val _sessions = MutableStateFlow<Map<String, SessionSnapshot>>(emptyMap())
    val sessions: StateFlow<Map<String, SessionSnapshot>> = _sessions.asStateFlow()

    /** Per-project Material design tokens harvested from batchexecute f6CJY. */
    private val _projectThemes = MutableStateFlow<Map<String, Map<String, String>>>(emptyMap())
    val projectThemes: StateFlow<Map<String, Map<String, String>>> = _projectThemes.asStateFlow()

    private val _events =
        MutableSharedFlow<Outbound>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val events: SharedFlow<Outbound> = _events.asSharedFlow()

    private var transport: BridgeTransport? = null

    /** Last inbound JSON payloads from [send], for on-device golden tests only. */
    private val inboundSentForInstrumentation = mutableListOf<String>()

    internal fun inboundSentForInstrumentation(): List<String> =
        synchronized(inboundSentForInstrumentation) { inboundSentForInstrumentation.toList() }

    // Production passes Dispatchers.Main so outbox flushes reach the WebView
    // transport on the right thread; unit tests fall back to the default.
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    /** True while a transport can actually reach Stitch. Drives offline UI. */
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /**
     * Bind the transport that carries bridge traffic. Pass a [BridgeRouter] to
     * get circuit-breaking between the local WebView and the remote session
     * bridge. The transport delivers `Outbound` JSON back through [handleOutbound].
     */
    fun bindTransport(transport: BridgeTransport) {
        this.transport = transport
        transport.setOutboundSink(::handleOutbound)
        scope.launch {
            transport.status.collect { status ->
                val ready = status == TransportStatus.Ready
                _online.value = ready
                if (ready) flushOutbox()
            }
        }
    }

    fun unbindTransport() {
        transport = null
        _online.value = false
    }

    /** Cancel the bridge's internal coroutines. Call from the Activity teardown. */
    fun dispose() {
        scope.cancel()
    }

    /** Seed the canvas from a cached snapshot — only if nothing live arrived yet. */
    fun seedNodes(cached: List<StitchNode>) {
        if (_nodes.value.isEmpty() && cached.isNotEmpty()) _nodes.value = cached
    }

    private fun flushOutbox() {
        val ob = outbox ?: return
        val t = transport ?: return
        for (entry in ob.snapshot()) {
            if (t.status.value != TransportStatus.Ready) break
            t.sendInbound(entry.payload)
            interceptor?.onAppToWebRequest("outbox_flush", null, entry.payload)
            ob.remove(entry.id)
        }
    }

    fun handleOutbound(raw: String) {
        val message =
            runCatching { json.decodeFromString<Outbound>(raw) }
                .onFailure { Log.w(TAG, "decode outbound failed: ${it.message}") }
                .getOrNull() ?: return
        interceptor?.onWebToAppRequest(message::class.simpleName ?: "outbound", null, raw)
        when (message) {
            is Outbound.NodesUpdated -> {
                _nodes.value = message.nodes
            }

            is Outbound.SelectionChanged -> {
                _selection.value = message.ids
            }

            is Outbound.GenerationProgress -> {
                _generation.update { it + (message.id to message.state) }
            }

            is Outbound.AssetReady -> {
                _events.tryEmit(message)
            }

            is Outbound.ExportComplete -> {
                _events.tryEmit(message)
            }

            is Outbound.AgentLogUpdated -> {
                _agentLog.value = message.entries
            }

            is Outbound.SessionStarted -> {
                _sessions.update {
                    it + (
                        message.sessionId to
                            SessionSnapshot(
                                projectId = message.projectId,
                                sessionId = message.sessionId,
                            )
                    )
                }
            }

            is Outbound.SessionProgress -> {
                _sessions.update { current ->
                    val existing =
                        current[message.sessionId] ?: SessionSnapshot(
                            projectId = "",
                            sessionId = message.sessionId,
                        )
                    current + (
                        message.sessionId to
                            existing.copy(
                                seqNo = message.seqNo,
                                stages = message.stages,
                            )
                    )
                }
            }

            is Outbound.SessionFinished -> {
                _sessions.update { current ->
                    val existing = current[message.sessionId] ?: return@update current
                    current + (
                        message.sessionId to
                            existing.copy(
                                finished = true,
                                totalBytes = message.totalBytes,
                            )
                    )
                }
            }

            is Outbound.ProjectThemeUpdated -> {
                _projectThemes.update {
                    it + (message.projectId to message.tokens)
                }
            }

            is Outbound.Error -> {
                Log.e(TAG, "stitch error ${message.code}: ${message.message}")
                _events.tryEmit(message)
            }
        }
    }

    fun send(message: Inbound) {
        val payload = json.encodeToString(message)
        synchronized(inboundSentForInstrumentation) { inboundSentForInstrumentation += payload }
        interceptor?.onAppToWebRequest(message::class.simpleName ?: "inbound", null, payload)
        val pipe = transport
        if (pipe != null && pipe.status.value == TransportStatus.Ready) {
            pipe.sendInbound(payload)
        } else if (message.isBufferable) {
            // Offline or logged out — buffer the action, flush on reconnect.
            outbox?.enqueue(message.outboxLabel, payload)
                ?: Log.w(TAG, "no outbox — dropped $message")
        } else {
            Log.w(TAG, "transient inbound dropped while offline: $message")
        }
    }

    fun rawEventDebug(raw: String): JsonObject? =
        runCatching {
            json.decodeFromString<JsonObject>(raw)
        }.getOrNull()
}
