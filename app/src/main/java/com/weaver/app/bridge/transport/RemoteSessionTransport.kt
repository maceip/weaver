package com.weaver.app.bridge.transport

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min

private const val TAG = "WeaverRemoteXport"

/**
 * Bridge transport over a WebSocket to the AWS session bridge.
 *
 * Handshake: on connect, send `{kind:hello, idToken, deviceId}`; the server
 * verifies the Google id_token, binds this socket to the matching
 * BrowserContext, and replies `{kind:ready,...}`. From then on `inbound`
 * frames carry our payloads up and `outbound` frames carry Stitch events
 * back. The server already holds an authenticated Stitch session, so this
 * transport — once [TransportStatus.Ready] — never has an auth problem.
 *
 * Reconnects with capped exponential backoff. The router circuit-breaks on
 * [TransportStatus.Failed] while a reconnect is pending.
 */
class RemoteSessionTransport(
    private val endpoint: String,
    private val deviceId: String,
    private val idTokenProvider: suspend () -> String?,
    private val json: Json,
) : BridgeTransport {
    override val id = "remote"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var outboundSink: ((String) -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // streaming socket — no read timeout
        .build()

    private var webSocket: WebSocket? = null
    private var running = false
    private var attempt = 0

    override fun setOutboundSink(sink: (String) -> Unit) {
        outboundSink = sink
    }

    override fun start() {
        if (running) return
        running = true
        connect()
    }

    override fun stop() {
        running = false
        webSocket?.close(1000, "client stop")
        webSocket = null
        scope.cancel()
        _status.value = TransportStatus.Idle
    }

    override fun sendInbound(payloadJson: String) {
        val ws = webSocket
        if (ws == null || _status.value != TransportStatus.Ready) {
            Log.w(TAG, "drop inbound — socket not ready")
            return
        }
        val payload = runCatching { json.parseToJsonElement(payloadJson).jsonObject }
            .getOrNull() ?: return
        val frame = buildJsonObject {
            put("kind", "inbound")
            put("payload", payload)
        }
        ws.send(frame.toString())
    }

    private fun connect() {
        if (!running) return
        _status.value = TransportStatus.Connecting
        scope.launch {
            val token = idTokenProvider()
            if (token == null) {
                Log.w(TAG, "no id_token — cannot open remote session")
                _status.value = TransportStatus.Failed
                scheduleReconnect()
                return@launch
            }
            val request = Request.Builder().url(endpoint).build()
            webSocket = client.newWebSocket(request, Listener(token))
        }
    }

    private fun scheduleReconnect() {
        if (!running) return
        attempt += 1
        val backoffMs = min(30_000L, 1_000L * (1L shl min(attempt, 5)))
        scope.launch {
            delay(backoffMs)
            if (running) connect()
        }
    }

    private inner class Listener(private val idToken: String) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val hello = buildJsonObject {
                put("kind", "hello")
                put("idToken", idToken)
                put("deviceId", deviceId)
            }
            webSocket.send(hello.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = runCatching { json.parseToJsonElement(text).jsonObject }
                .getOrNull() ?: return
            when (frame["kind"]?.toString()?.trim('"')) {
                "ready" -> {
                    attempt = 0
                    _status.value = TransportStatus.Ready
                    Log.i(TAG, "remote session ready: $frame")
                }
                "outbound" -> {
                    val payload = frame["payload"] as? JsonObject ?: return
                    outboundSink?.invoke(payload.toString())
                }
                "ping" -> webSocket.send("""{"kind":"pong"}""")
                "pong" -> Unit
                "error" -> {
                    val fatal = frame["fatal"]?.toString() == "true"
                    Log.w(TAG, "remote error: $frame (fatal=$fatal)")
                    _status.value = TransportStatus.Failed
                    if (fatal) {
                        running = false
                        webSocket.close(1008, "server-fatal")
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            this@RemoteSessionTransport.webSocket = null
            if (running) {
                _status.value = TransportStatus.Failed
                scheduleReconnect()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "websocket failure: ${t.message}")
            this@RemoteSessionTransport.webSocket = null
            _status.value = TransportStatus.Failed
            scheduleReconnect()
        }
    }
}
