package com.weaver.app.bridge

import android.webkit.JavascriptInterface

class JsBridgeInterface(private val bridge: Bridge) {
    @JavascriptInterface
    fun post(json: String) {
        bridge.handleOutbound(json)
    }
}
