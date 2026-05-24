package com.weaver.app.auth

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the cookie-jar session signal — the fast primary readiness probe. */
class SessionClassifierTest {
    @Test
    fun googleSignedInWhenSecure1PSIDPresent() {
        val header = "NID=abc; __Secure-1PSID=xyz; CONSENT=YES"
        assertEquals(SessionSignal.SignedIn, SessionClassifier.classifyGoogle(header))
    }

    @Test
    fun googleSignedInWhenLegacySIDPresent() {
        assertEquals(
            SessionSignal.SignedIn,
            SessionClassifier.classifyGoogle("SID=value; HSID=value"),
        )
    }

    @Test
    fun googleSignedOutWithOnlyConsentCookies() {
        // NID + CONSENT are set even for signed-out users.
        assertEquals(
            SessionSignal.SignedOut,
            SessionClassifier.classifyGoogle("NID=abc; CONSENT=YES+1; SOCS=xyz"),
        )
    }

    @Test
    fun googleSignedOutWhenJarEmpty() {
        assertEquals(SessionSignal.SignedOut, SessionClassifier.classifyGoogle(null))
        assertEquals(SessionSignal.SignedOut, SessionClassifier.classifyGoogle(""))
    }

    @Test
    fun stitchSignedInWhenNonConsentCookiePresent() {
        // Any cookie that isn't a known consent/prefs cookie reads as session-ish.
        assertEquals(
            SessionSignal.SignedIn,
            SessionClassifier.classifyStitch("NID=abc; STITCH_SESSION=opaque"),
        )
    }

    @Test
    fun stitchSignedOutWithOnlyConsentCookies() {
        assertEquals(
            SessionSignal.SignedOut,
            SessionClassifier.classifyStitch("NID=abc; CONSENT=YES; AEC=z; OTZ=1; 1P_JAR=d"),
        )
    }

    @Test
    fun stitchSignedOutWhenJarEmpty() {
        assertEquals(SessionSignal.SignedOut, SessionClassifier.classifyStitch(null))
        assertEquals(SessionSignal.SignedOut, SessionClassifier.classifyStitch(""))
    }

    @Test
    fun accountChooserHintDoesNotImplySession() {
        // CookieInjector.apply writes ACCOUNT_CHOOSER as a hint — not a session.
        assertEquals(
            SessionSignal.SignedOut,
            SessionClassifier.classifyStitch("ACCOUNT_CHOOSER=ceo@example.com"),
        )
    }

    @Test
    fun cookieNamesParsesNameValuePairs() {
        assertEquals(
            listOf("a", "b", "c"),
            SessionClassifier.cookieNames(" a=1; b=2 ;c=3 "),
        )
    }

    @Test
    fun cookieNamesHandlesEmptyAndNull() {
        assertEquals(emptyList<String>(), SessionClassifier.cookieNames(null))
        assertEquals(emptyList<String>(), SessionClassifier.cookieNames(""))
        assertEquals(emptyList<String>(), SessionClassifier.cookieNames(";; ;"))
    }
}
