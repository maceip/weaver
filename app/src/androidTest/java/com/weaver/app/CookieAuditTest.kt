package com.weaver.app

import android.util.Log
import android.webkit.CookieManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.auth.CookieInjector
import com.weaver.app.auth.SessionSignal
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Writes WebView cookie state to logcat tag `WeaverCookieAudit` (read via adb). */
@RunWith(AndroidJUnit4::class)
class CookieAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun logWebViewCookieState() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val cm = CookieManager.getInstance()
            val google = CookieInjector.probeGoogleSession()
            val stitch = CookieInjector.probeStitchSession()
            val googleJar = cm.getCookie("https://accounts.google.com").orEmpty()
            val stitchJar = cm.getCookie("https://stitch.withgoogle.com").orEmpty()
            val companionJar = cm.getCookie("https://app-companion-430619.appspot.com").orEmpty()
            val app = composeRule.activity.application as WeaverApp
            val account = app.accountResolver.current()
            Log.e(
                TAG,
                "account=${account?.email ?: "none"} googleProbe=$google stitchProbe=$stitch",
            )
            Log.e(TAG, "googleJarLen=${googleJar.length} stitchJarLen=${stitchJar.length} companionJarLen=${companionJar.length}")
            if (googleJar.isNotEmpty()) Log.e(TAG, "googleJar=$googleJar")
            if (stitchJar.isNotEmpty()) Log.e(TAG, "stitchJar=$stitchJar")
            if (companionJar.isNotEmpty()) Log.e(TAG, "companionJar=$companionJar")
        }
        composeRule.waitForIdle()
        assertNotNull(composeRule.activity)
    }

    private companion object {
        const val TAG = "WeaverCookieAudit"
    }
}
