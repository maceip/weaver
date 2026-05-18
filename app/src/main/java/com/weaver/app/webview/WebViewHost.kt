package com.weaver.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.JsBridgeInterface

private const val TAG = "WeaverWebView"

// Public Stitch URL. The HTML we get back from this host is an Angular
// "appcompanion" wrapper that loads the real React Flow editor inside
// `<iframe sandbox="allow-scripts" srcdoc="...">`. Sandbox + srcdoc gives
// the iframe a null origin, so the content script we inject into the parent
// frame can't reach the editor's DOM.
//
// Resolution path (next pass): once auth is established, navigate the WebView
// directly to `https://app-companion-430619.appspot.com/projects/{id}` so the
// editor is the top-level frame and the content script runs in the right
// context. We'll also need androidx.webkit WebMessageListener with
// allowedOriginRules covering that host, since direct navigation may need
// to fake out the `?parent=stitch.withgoogle.com` handshake.
const val STITCH_URL = "https://stitch.withgoogle.com/"
const val STITCH_DIRECT_PROJECT_URL_PREFIX = "https://app-companion-430619.appspot.com/projects/"

class WebViewHost(
    private val appContext: Context,
    private val bridge: Bridge,
) {

    var webView: WebView? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun create(): WebView {
        val view = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.userAgentString = settings.userAgentString + " Weaver/0.1"

            if (WebViewFeature.isFeatureSupported(WebViewFeature.OFF_SCREEN_PRERASTER)) {
                WebViewCompat.setWebViewRenderProcessClient(this, null)
            }
            // Headless: keep the renderer running even though the view isn't on screen.
            isFocusable = true
            isFocusableInTouchMode = true
            visibility = View.VISIBLE

            addJavascriptInterface(JsBridgeInterface(bridge), "Android")

            // The fetch interceptor MUST run before any Stitch script that captures
            // a reference to window.fetch. Use the document-start hook when the
            // WebView build supports it; otherwise fall back to onPageStarted
            // injection, which is best-effort.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(
                    this,
                    StitchFetchInterceptor.source,
                    setOf(
                        "https://stitch.withgoogle.com",
                        "https://app-companion-430619.appspot.com",
                    ),
                )
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress == 100) injectContentScript(view)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    Log.d(TAG, "load start: $url")
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        // Fallback: best-effort early injection. Stitch may already
                        // have grabbed window.fetch by the time this runs.
                        view.evaluateJavascript(StitchFetchInterceptor.source, null)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    injectContentScript(view)
                }

                override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                    val host = req.url.host ?: return false
                    // Keep navigation inside Google's auth + stitch domains.
                    val allowed = host == "stitch.withgoogle.com" ||
                        host.endsWith(".google.com") ||
                        host.endsWith(".googleusercontent.com")
                    if (!allowed) {
                        Log.w(TAG, "blocked navigation to $host")
                        return true
                    }
                    return false
                }
            }
        }
        bridge.attach(view)
        webView = view
        return view
    }

    private fun injectContentScript(view: WebView) {
        view.evaluateJavascript(StitchContentScript.source, null)
    }

    fun load(url: String = STITCH_URL) {
        webView?.loadUrl(url)
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            removeJavascriptInterface("Android")
            destroy()
        }
        bridge.detach()
        webView = null
    }
}
