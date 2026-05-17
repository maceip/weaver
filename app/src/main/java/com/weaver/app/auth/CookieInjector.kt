package com.weaver.app.auth

import android.util.Log
import android.webkit.CookieManager

private const val TAG = "WeaverCookies"
private const val STITCH_DOMAIN = "https://stitch.withgoogle.com"
private const val GOOGLE_DOMAIN = "https://accounts.google.com"

/**
 * Cookie injection for the headless Stitch WebView.
 *
 * Browser-based Google sign-in writes its own cookies into the WebView's
 * CookieManager when the user completes a flow inside that WebView, so the
 * "easy" path is: navigate the WebView to accounts.google.com, let the
 * existing Android account proxy handle SSO, and CookieManager already holds
 * what Stitch needs. This class exists for the cases where we have an
 * id_token from Credential Manager and want to seed the session without
 * a visible browser hop.
 */
object CookieInjector {

    fun apply(account: Account) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        // No long-lived auth cookie can be safely synthesized from an id_token alone — Google's
        // session establishment must happen against accounts.google.com. We mark the account
        // hint so the OAuth chooser auto-selects the right identity on first load.
        val hint = "ACCOUNT_CHOOSER=${account.email}; Domain=.google.com; Path=/; Secure"
        cm.setCookie(GOOGLE_DOMAIN, hint) { ok ->
            Log.d(TAG, "account hint set ok=$ok for ${account.email}")
        }
        cm.flush()
    }

    fun clear(onDone: () -> Unit = {}) {
        val cm = CookieManager.getInstance()
        cm.removeAllCookies { cm.flush(); onDone() }
    }

    fun hasStitchCookies(): Boolean {
        val cookies = CookieManager.getInstance().getCookie(STITCH_DOMAIN)
        return !cookies.isNullOrBlank()
    }
}
