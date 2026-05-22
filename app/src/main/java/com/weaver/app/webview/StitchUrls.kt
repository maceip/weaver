package com.weaver.app.webview

/** Pure URL helpers for Stitch, factored out of WebViewHost for testability. */
internal object StitchUrls {
    private val projectIdRegex = Regex("""/projects/(\d{8,})""")

    /**
     * Extracts the numeric Stitch project id from a URL. Matches both
     *   https://stitch.withgoogle.com/projects/<20-digit-numeric>
     *   https://app-companion-430619.appspot.com/projects/<id>?usegapi=1...
     * Returns null for anything else (landing page, /assets, pings).
     */
    fun extractProjectId(url: String): String? = projectIdRegex.find(url)?.groupValues?.get(1)
}
