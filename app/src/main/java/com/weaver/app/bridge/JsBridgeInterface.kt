package com.weaver.app.bridge

import android.webkit.JavascriptInterface

/**
 * The `window.Android` object the content script posts to. Sink-based rather
 * than Bridge-coupled so the local WebView path can be wrapped by a transport
 * (see LocalWebViewTransport) and routed alongside the remote path.
 */
class JsBridgeInterface(
    private val onPost: (String) -> Unit,
) {
    @JavascriptInterface
    fun post(json: String) {
        onPost(json)
    }
}
