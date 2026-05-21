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
 * posts to. The WebView may or may not hold a Stitch session — when it does
 * not, callers should mark this transport [TransportStatus.Degraded] so the
 * router prefers the always-authenticated remote bridge.
 */
class LocalWebViewTransport : BridgeTransport {
    override val id = "local"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var outboundSink: ((String) -> Unit)? = null
    private var webView: WebView? = null

    /** The `window.Android` object — hand this to `addJavascriptInterface`. */
    val jsInterface = JsBridgeInterface { json -> outboundSink?.invoke(json) }

    override fun setOutboundSink(sink: (String) -> Unit) {
        outboundSink = sink
    }

    /** Called by WebViewHost once the WebView is constructed. */
    fun bindWebView(webView: WebView) {
        this.webView = webView
        _status.value = TransportStatus.Connecting
    }

    /**
     * Reflects what the bridge knows about the WebView's Stitch session:
     * authenticated + page ready -> Ready; up but unauthenticated -> Degraded;
     * destroyed -> Failed.
     */
    fun reportStatus(status: TransportStatus) {
        _status.value = status
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
