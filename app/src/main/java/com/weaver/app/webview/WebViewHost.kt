package com.weaver.app.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.ExportKind
import com.weaver.app.bridge.JsBridgeInterface
import com.weaver.app.bridge.Outbound
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

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

    /**
     * Fires whenever the WebView lands on a Stitch project URL, with the
     * numeric project id parsed out. Used by NavRoot to call
     * ProjectRepository.bindStitchId so a brand-new project that just got
     * its id from Stitch can be promoted from "draft" to a real persisted
     * project.
     */
    var onStitchProjectIdResolved: ((stitchProjectId: String) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun create(): WebView {
        // Remote DevTools: with this enabled, chrome://inspect on a desktop
        // browser (over `adb forward`) gives full DevTools — Network panel,
        // console, Performance, source-stepping into Stitch. Debug-only.
        WebView.setWebContentsDebuggingEnabled(true)

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

            // Stitch exports go through a Content-Disposition: attachment GET to
            // contribution.usercontent.google.com/download?c=<base64-protobuf>.
            // The protobuf token is generated client-side by Stitch's JS, so we
            // can't synthesise it ourselves — but we don't need to. When the
            // user taps Export -> {Zip,Figma,...} the WebView starts a download
            // and this listener picks it up. Emit ExportComplete with the URL
            // so the Compose layer can hand it to Android's DownloadManager (or
            // an InputStream copy) and surface the saved file.
            setDownloadListener { url, _, contentDisposition, mimeType, contentLength ->
                val kind = when {
                    url.contains(".zip") || mimeType == "application/zip" -> ExportKind.Zip
                    url.contains(".fig") || mimeType.contains("figma") -> ExportKind.Figma
                    mimeType.startsWith("text/html") -> ExportKind.RawCode
                    else -> ExportKind.RawCode
                }
                val payload = buildJsonObject {
                    put("url", JsonPrimitive(url))
                    put("mimeType", JsonPrimitive(mimeType))
                    put("contentDisposition", JsonPrimitive(contentDisposition ?: ""))
                    put("contentLength", JsonPrimitive(contentLength))
                }
                Log.d(TAG, "download intercepted: kind=$kind size=$contentLength url=${url.take(120)}")
                bridge.handleOutbound(
                    bridge.json.encodeToString(
                        Outbound.serializer(),
                        Outbound.ExportComplete(kind = kind, payload = payload),
                    ),
                )
            }

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

                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val tag = "WeaverJS"
                    val line = "[${message.messageLevel().name}] " +
                        "${message.message()} (${message.sourceId()}:${message.lineNumber()})"
                    when (message.messageLevel()) {
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(tag, line)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(tag, line)
                        else -> Log.d(tag, line)
                    }
                    return true
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
                    if (url != null) extractStitchProjectId(url)?.let {
                        onStitchProjectIdResolved?.invoke(it)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    injectContentScript(view)
                }

                override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                    // HAR-equivalent surface: every request flows through here.
                    // We never modify the response, just log the method+URL so the
                    // Dari notification + logcat give us a network timeline without
                    // needing to attach a desktop devtools. Stitch traffic is
                    // distinguishable by host filter.
                    val url = req.url.toString()
                    val host = req.url.host ?: ""
                    if (host.endsWith("stitch.withgoogle.com") ||
                        host.endsWith("appspot.com") ||
                        host.endsWith("googleusercontent.com")
                    ) {
                        Log.d("WeaverNet", "${req.method} ${SystemClock.elapsedRealtime()}ms $url")
                    }
                    return null
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

    /**
     * Stitch project URLs look like
     *   https://stitch.withgoogle.com/projects/<20-digit-numeric>
     *   https://app-companion-430619.appspot.com/projects/<20-digit-numeric>[?...]
     * The iframe srcdoc URL also embeds the same id. Returns null when [url]
     * is anything else (landing page, /assets, internal pings).
     */
    internal fun extractStitchProjectId(url: String): String? {
        val regex = Regex("""/projects/(\d{8,})""")
        return regex.find(url)?.groupValues?.get(1)
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
