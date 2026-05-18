package com.weaver.app.auth

import android.content.Context
import android.util.Log
import com.weaver.app.webview.WebViewHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WeaverAuth"

sealed interface AuthState {
    data object Unknown : AuthState
    data object Authenticating : AuthState
    data class Authenticated(val account: Account) : AuthState
    data class Failed(val reason: String) : AuthState
}

/**
 * Coordinates the Credential Manager sign-in with the headless WebView.
 * The WebView is pre-warmed by MainActivity before this runs; on success
 * we inject cookies and reload so Stitch picks up the authenticated session.
 */
class AuthController(
    private val picker: AccountPicker,
    private val resolver: AccountResolver,
    private val webViewHost: WebViewHost,
) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Unknown)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    fun bootstrap() {
        val existing = resolver.current() ?: return
        CookieInjector.apply(existing)
        webViewHost.load()
        _state.value = AuthState.Authenticated(existing)
    }

    suspend fun signIn(activityContext: Context) {
        _state.value = AuthState.Authenticating
        val account = picker.show(activityContext)
        if (account != null) {
            CookieInjector.apply(account)
            // Reload so Stitch picks up the new session cookies. The WebView was
            // pre-warmed against an anonymous page; this navigates the same
            // renderer instead of cold-starting a new one.
            webViewHost.load()
            _state.value = AuthState.Authenticated(account)
        } else {
            _state.value = AuthState.Failed("Sign-in cancelled or failed")
            Log.w(TAG, "credential picker returned null")
        }
    }

    fun signOut() {
        resolver.clear()
        CookieInjector.clear { webViewHost.load() }
        _state.value = AuthState.Unknown
    }
}
