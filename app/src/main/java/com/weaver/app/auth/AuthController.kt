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
 *
 * Caveats (per the project-logged-out.html fixture): an unauthenticated
 * /projects/{id} request returns a 404 page, not a sign-in redirect.
 * Stitch's session cookies are minted by Google's OAuth handshake
 * (`usegapi=1` + `gapi.lb.en.*`), so a raw id_token from Credential
 * Manager is probably insufficient on its own. The full fix is a
 * one-time visible WebView pass through Stitch's Continue-with-Google
 * to capture cookies; until then, [devMode]=true short-circuits the
 * Credential Manager call so an app built with the placeholder OAuth
 * client id can still boot past the login gate for UI testing.
 */
class AuthController(
    private val picker: AccountPicker,
    private val resolver: AccountResolver,
    private val webViewHost: WebViewHost,
    private val devMode: Boolean = false,
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
        if (devMode) {
            // No real OAuth available — synthesize an anonymous identity so the
            // app advances past the Login gate. The WebView still loads Stitch
            // anonymously; the user can interact with the landing page or stay
            // in our own Compose surfaces for UI testing.
            val anon = Account(id = "dev-anonymous", email = "dev@local", displayName = "Dev (no auth)", idToken = null)
            resolver.persist(anon)
            webViewHost.load()
            _state.value = AuthState.Authenticated(anon)
            Log.i(TAG, "dev-mode sign-in: skipping Credential Manager (placeholder OAuth client id)")
            return
        }
        _state.value = AuthState.Authenticating
        val account = picker.show(activityContext)
        if (account != null) {
            CookieInjector.apply(account)
            // Reload so Stitch picks up the new session cookies.
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
