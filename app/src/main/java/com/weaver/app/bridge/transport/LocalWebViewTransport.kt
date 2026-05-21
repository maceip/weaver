package com.weaver.app.bridge.transport

import android.webkit.WebView
import com.weaver.app.bridge.JsBridgeInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridge transport over the bundled on-device WebView.
 *
 * Inbound is delivered by `evaluateJavascript(window.__weaverBridge.receive)`.
 * Outbound arrives through the [JsBridgeInterface] that the content script
 * posts to.
 *
 * The transport proves its own health from that outbound stream — it does not
 * need to be told. The content script only finds `.react-flow` and emits
 * `nodes_updated` on a real, authenticated Stitch editor; on the 404 / landing
 * page there is no canvas, so it emits `error{selector_breakage}` after its
 * 10s probe. So: first healthy editor event -> [TransportStatus.Ready];
 * selector breakage -> [TransportStatus.Degraded].
 */
class LocalWebViewTransport : BridgeTransport {
    override val id = "local"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var outboundSink: ((String) -> Unit)? = null
    private var webView: WebView? = null

    private val typeRegex = Regex("\"type\"\\s*:\\s*\"([^\"]+)\"")

    /** The `window.Android` object — hand this to `addJavascriptInterface`. */
    val jsInterface = JsBridgeInterface { json ->
        assessHealth(json)
        outboundSink?.invoke(json)
    }

    /**
     * Self-assessment from the content script's own traffic. `nodes_updated`
     * can only come from a mounted React Flow editor, which only renders for
     * an authenticated session — that is the proof the local WebView has a
     * usable Stitch session. `selector_breakage` is the opposite proof.
     */
    private fun assessHealth(outboundJson: String) {
        when (typeRegex.find(outboundJson)?.groupValues?.get(1)) {
            "nodes_updated", "selection_changed", "agent_log_updated",
            "session_started", "session_progress",
            -> _status.value = TransportStatus.Ready
            "error" -> {
                if (outboundJson.contains("selector_breakage") ||
                    outboundJson.contains("canvas_missing")
                ) {
                    _status.value = TransportStatus.Degraded
                }
            }
        }
    }

    override fun setOutboundSink(sink: (String) -> Unit) {
        outboundSink = sink
    }

    /** Called by WebViewHost once the WebView is constructed. */
    fun bindWebView(webView: WebView) {
        this.webView = webView
        _status.value = TransportStatus.Connecting
    }

    /**
     * Lifecycle nudge from WebViewHost (Connecting on page start). Ready and
     * Degraded are NOT set here — those are proven from the outbound stream
     * in [assessHealth]. This only moves the status backward (toward
     * Connecting) so a navigation resets the proof.
     */
    fun reportLifecycle(status: TransportStatus) {
        if (status == TransportStatus.Connecting || status == TransportStatus.Failed) {
            _status.value = status
        }
    }

    override fun start() {
        if (_status.value == TransportStatus.Idle) {
            _status.value = TransportStatus.Connecting
        }
    }

    override fun stop() {
        webView = null
        _status.value = TransportStatus.Idle
    }

    override fun sendInbound(payloadJson: String) {
        val view = webView ?: return
        val escaped = payloadJson.replace("\\", "\\\\").replace("'", "\\'")
        view.post {
            view.evaluateJavascript("window.__weaverBridge.receive('$escaped')", null)
        }
    }
}
