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
 *
 * Caveat (per the project-logged-out.html fixture): an unauthenticated
 * /projects/{id} request returns a 404 page, not a sign-in redirect.
 * Stitch's session cookies are minted by Google's OAuth handshake
 * (`usegapi=1` + `gapi.lb.en.*`), so a raw id_token from Credential
 * Manager is probably insufficient on its own — we may need to either
 * (a) briefly surface the WebView for Stitch's own Continue-with-Google
 * flow (one-time per account, then headless forever) or (b) exchange
 * the id_token server-side for session cookies. Current code path is
 * the optimistic "id_token + cookie hint" version; if CookieManager
 * doesn't carry a Stitch session marker after [signIn], we'll need to
 * fall back to (a) — track via [CookieInjector.hasStitchCookies].
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
