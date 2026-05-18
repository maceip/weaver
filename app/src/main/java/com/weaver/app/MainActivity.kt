package com.weaver.app

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor
import com.weaver.app.auth.AccountPicker
import com.weaver.app.auth.AuthController
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.Preset
import com.weaver.app.fold.FoldObserver
import com.weaver.app.ui.WeaverNavRoot
import com.weaver.app.ui.theme.WeaverTheme
import com.weaver.app.webview.WebViewHost

class MainActivity : ComponentActivity() {

    private val interceptor: DariInterceptor? = Dari.createInterceptor(tag = "Stitch")
    private lateinit var bridge: Bridge
    private lateinit var webViewHost: WebViewHost
    private lateinit var foldObserver: FoldObserver
    private lateinit var authController: AuthController

    private val builtinPresets: List<Preset> = listOf(
        Preset("alexandria", "Alexandria", listOf("#1E3A5F", "#F4D35E", "#2E2E2E"), isBuiltin = true),
        Preset("bauhaus", "Bauhaus", listOf("#D72631", "#2E294E", "#E8C547"), isBuiltin = true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WeaverApp

        bridge = Bridge(interceptor)
        webViewHost = WebViewHost(applicationContext, bridge)
        webViewHost.onStitchProjectIdResolved = { stitchId ->
            // Bind the freshly-minted Stitch project id to whichever local draft
            // is on top. The repository ignores the call if no draft is current.
            val draft = app.projectRepository.projects.value.firstOrNull { it.isDraft }
            if (draft != null) app.projectRepository.bindStitchId(draft.id, stitchId)
        }
        foldObserver = FoldObserver(this, bridge)
        val picker = AccountPicker(this, ServerClientIds.WEB_OAUTH, app.accountResolver)
        authController = AuthController(picker, app.accountResolver, webViewHost)

        // ──────────────────────────────────────────────────────────────────────
        // PRE-WARM: create the WebView and start loading Stitch immediately, before
        // any Compose surface is drawn. This warms the renderer process, DNS, TLS,
        // JS engine, and content-script injection while the user is interacting
        // with the login gate. If we already have a persisted account, we apply
        // cookies first so Stitch loads straight into the authenticated session.
        // ──────────────────────────────────────────────────────────────────────
        val hiddenHost = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }
        val webView = webViewHost.create().apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
            visibility = View.VISIBLE
        }
        hiddenHost.addView(webView)

        // Bootstrap auth from persisted account (if any) before the first load,
        // otherwise load anonymously so we have a warm renderer ready to reload
        // after Credential Manager succeeds.
        if (app.accountResolver.current() != null) {
            authController.bootstrap()
        } else {
            webViewHost.load()
        }

        setContent {
            WeaverTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(factory = { hiddenHost })
                    WeaverNavRoot(
                        bridge = bridge,
                        presets = builtinPresets,
                        authController = authController,
                        projectRepository = app.projectRepository,
                        bitmapCache = app.bitmapCache,
                        foldObserver = foldObserver,
                    )
                }
            }
        }

        foldObserver.observe(this)
    }

    override fun onDestroy() {
        webViewHost.destroy()
        super.onDestroy()
    }
}

object ServerClientIds {
    // Wire the real OAuth Web client id via gradle/BuildConfig before shipping.
    const val WEB_OAUTH: String = "REPLACE_WITH_OAUTH_WEB_CLIENT_ID"
}
