package com.weaver.app

import android.util.Log
import android.webkit.CookieManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weaver.app.auth.CookieInjector
import com.weaver.app.auth.SessionSignal
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * One-shot restore: adb push JSON to [IMPORT_PATH], then run this test class only.
 *
 *   ./scripts/push-cookies-to-device.sh
 */
@RunWith(AndroidJUnit4::class)
class CookieRestoreTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun importCookiesFromSdcardAndLogProbes() {
        val file = File(IMPORT_PATH)
        assertTrue("Push cookies first: adb push weaver-cookies-import.json $IMPORT_PATH", file.exists())

        val root = JSONObject(file.readText())
        val cookies = root.getJSONArray("cookies")
        composeRule.runOnUiThread {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            var applied = 0
            for (i in 0 until cookies.length()) {
                val row = cookies.getJSONObject(i)
                if (!shouldImport(row.getString("name"))) continue
                val host = row.getString("host").trimStart('.')
                val url = "https://$host"
                val header = buildCookieHeader(row)
                cm.setCookie(url, header) { }
                applied++
            }
            cm.flush()
            val google = CookieInjector.probeGoogleSession()
            val stitch = CookieInjector.probeStitchSession()
            Log.e(TAG, "applied=$applied googleProbe=$google stitchProbe=$stitch")
            Log.e(TAG, "stitchJar=${cm.getCookie("https://stitch.withgoogle.com").orEmpty().take(200)}")
        }
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            val stitch = CookieInjector.probeStitchSession()
            assertTrue(
                "Stitch session not detected after import (got $stitch)",
                stitch == SessionSignal.SignedIn,
            )
        }
        assertNotNull(composeRule.activity)
    }

    private fun shouldImport(name: String): Boolean =
        SESSION_TOKENS.any { name.contains(it) }

    private fun buildCookieHeader(row: JSONObject): String {
        val domain = row.getString("host")
        val domainAttr = if (domain.startsWith(".")) "Domain=$domain" else "Domain=.$domain"
        val path = row.optString("path", "/")
        val secure = if (row.optBoolean("secure")) "; Secure" else ""
        val httpOnly = if (row.optBoolean("httpOnly")) "; HttpOnly" else ""
        return "${row.getString("name")}=${row.getString("value")}; $domainAttr; Path=$path$secure$httpOnly"
    }

    private companion object {
        const val TAG = "WeaverCookieRestore"
        const val IMPORT_PATH = "/sdcard/Download/weaver-cookies-import.json"
        val SESSION_TOKENS = listOf(
            "OSID", "SID", "LSID", "ACCOUNT", "SAPISID", "SSID", "__Secure", "__Host", "APISID", "HSID",
        )
    }
}
