package com.weaver.app.auth

/**
 * Pure classification of a raw `Cookie:` header string into a [SessionSignal].
 * Factored out of [CookieInjector] so the signal logic is testable without the
 * Android `CookieManager`. [CookieInjector] supplies the live header; this
 * object holds the cookie-name knowledge and the parsing.
 */
internal object SessionClassifier {
    /** Google's account-session cookies. Presence of any one means signed in. */
    val GOOGLE_SESSION_COOKIES =
        setOf(
            "SID",
            "__Secure-1PSID",
            "__Secure-3PSID",
            "SAPISID",
            "__Secure-3PAPISID",
        )

    /** Consent / preference cookies that do NOT imply an authenticated session. */
    val NON_SESSION_COOKIES =
        setOf(
            "NID",
            "CONSENT",
            "SOCS",
            "AEC",
            "OTZ",
            "1P_JAR",
            "ACCOUNT_CHOOSER",
        )

    /** Cookie names from a `name=value; name2=value2` header. */
    fun cookieNames(header: String?): List<String> =
        header
            ?.split(";")
            ?.mapNotNull { it.substringBefore("=").trim().takeIf(String::isNotEmpty) }
            ?: emptyList()

    /** Signed in iff a recognised Google session cookie is present. */
    fun classifyGoogle(header: String?): SessionSignal {
        val names = cookieNames(header)
        if (names.isEmpty()) return SessionSignal.SignedOut
        return if (names.any { it in GOOGLE_SESSION_COOKIES }) {
            SessionSignal.SignedIn
        } else {
            SessionSignal.SignedOut
        }
    }

    /**
     * Signed in iff any cookie present on the Stitch host is not a known
     * consent/prefs cookie. Heuristic — the exact Stitch session cookie name
     * needs a live capture; tighten to an exact-name match once known.
     */
    fun classifyStitch(header: String?): SessionSignal {
        val names = cookieNames(header)
        if (names.isEmpty()) return SessionSignal.SignedOut
        return if (names.any { it !in NON_SESSION_COOKIES }) {
            SessionSignal.SignedIn
        } else {
            SessionSignal.SignedOut
        }
    }
}
