package com.weaver.app.bridge.transport

import android.webkit.WebView
import com.weaver.app.auth.CookieInjector
import com.weaver.app.auth.SessionSignal
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
 * Health is proven from two signals, fast-then-authoritative:
 *
 *  1. Cookie jar ([probeSession]) — synchronous, deterministic, available the
 *     moment the page finishes loading. Google session cookies present ->
 *     a provisional [TransportStatus.Ready]; absent -> [TransportStatus.Degraded].
 *  2. Content-script traffic (OutboundClassifier) — definitive. `nodes_updated`
 *     can only come from a mounted React Flow editor (cookies present AND
 *     still valid) -> [TransportStatus.Ready]; `error{selector_breakage}`
 *     means no editor mounted (cookies stale or absent) -> [TransportStatus.Degraded].
 *
 * The cookie probe gives an instant verdict; the content script corrects it
 * within seconds if the cookies turned out to be stale.
 */
class LocalWebViewTransport : BridgeTransport {
    override val id = "local"

    private val _status = MutableStateFlow(TransportStatus.Idle)
    override val status: StateFlow<TransportStatus> = _status.asStateFlow()

    private var outboundSink: ((String) -> Unit)? = null
    private var webView: WebView? = null

    /** The `window.Android` object — hand this to `addJavascriptInterface`. */
    val jsInterface = JsBridgeInterface { json ->
        OutboundClassifier.classify(json)?.let { _status.value = it }
        outboundSink?.invoke(json)
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
     * Degraded are NOT set here — those are proven by [probeSession] and
     * OutboundClassifier. This only moves the status backward (toward Connecting)
     * so a navigation resets the proof.
     */
    fun reportLifecycle(status: TransportStatus) {
        if (status == TransportStatus.Connecting || status == TransportStatus.Failed) {
            _status.value = status
        }
    }

    /**
     * Fast cookie-jar probe — call once the page finishes loading (cookies are
     * settled by then). Sets a provisional status that OutboundClassifier later
     * confirms or corrects from real editor traffic. Does not downgrade an
     * already-confirmed [TransportStatus.Ready].
     */
    fun probeSession() {
        if (_status.value == TransportStatus.Ready) return // already confirmed by traffic
        val google = CookieInjector.probeGoogleSession()
        val stitch = CookieInjector.probeStitchSession()
        _status.value = when {
            stitch == SessionSignal.SignedIn || google == SessionSignal.SignedIn ->
                TransportStatus.Ready
            else -> TransportStatus.Degraded
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
