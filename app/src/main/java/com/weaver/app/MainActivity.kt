package com.weaver.app

import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.easyhooon.dari.Dari
import com.easyhooon.dari.interceptor.DariInterceptor
import com.weaver.app.auth.AccountPicker
import com.weaver.app.auth.CookieInjector
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.Preset
import com.weaver.app.fold.FoldObserver
import com.weaver.app.ui.WeaverNavRoot
import com.weaver.app.ui.theme.WeaverTheme
import com.weaver.app.webview.STITCH_URL
import com.weaver.app.webview.WebViewHost
import kotlinx.coroutines.launch

private const val TAG = "WeaverMain"

class MainActivity : ComponentActivity() {

    private val interceptor: DariInterceptor? = Dari.createInterceptor(tag = "Stitch")
    private lateinit var bridge: Bridge
    private lateinit var webViewHost: WebViewHost
    private lateinit var foldObserver: FoldObserver
    private lateinit var accountPicker: AccountPicker

    private val builtinPresets: List<Preset> = listOf(
        Preset("alexandria", "Alexandria", listOf("#1E3A5F", "#F4D35E", "#2E2E2E"), isBuiltin = true),
        Preset("bauhaus", "Bauhaus", listOf("#D72631", "#2E294E", "#E8C547"), isBuiltin = true),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bridge = Bridge(interceptor)
        webViewHost = WebViewHost(applicationContext, bridge)
        foldObserver = FoldObserver(this, bridge)
        val app = application as WeaverApp
        accountPicker = AccountPicker(this, ServerClientIds.WEB_OAUTH, app.accountResolver)

        // 1x1 transparent host so the WebView renderer keeps executing while Compose owns the
        // visible surface. WebView throttles aggressively when fully detached.
        val hiddenHost = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }
        val webView = webViewHost.create().apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
            visibility = View.VISIBLE
        }
        hiddenHost.addView(webView)

        setContent {
            WeaverTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(factory = { hiddenHost })
                    WeaverNavRoot(
                        bridge = bridge,
                        presets = builtinPresets,
                        bitmapCache = app.bitmapCache,
                        foldObserver = foldObserver,
                    )
                }
            }
        }

        foldObserver.observe(this)

        lifecycleScope.launch {
            val existing = app.accountResolver.current()
            if (existing != null) {
                CookieInjector.apply(existing)
                webViewHost.load(STITCH_URL)
            } else {
                val picked = accountPicker.show(this@MainActivity)
                if (picked != null) CookieInjector.apply(picked)
                else Log.w(TAG, "no account selected; loading Stitch unauthenticated")
                webViewHost.load(STITCH_URL)
            }
        }
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
