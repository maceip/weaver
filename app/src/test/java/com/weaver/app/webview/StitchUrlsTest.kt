package com.weaver.app.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StitchUrlsTest {

    @Test
    fun extractsIdFromPublicProjectUrl() {
        assertEquals(
            "15407001095177925694",
            StitchUrls.extractProjectId("https://stitch.withgoogle.com/projects/15407001095177925694"),
        )
    }

    @Test
    fun extractsIdFromAppCompanionUrlWithQuery() {
        assertEquals(
            "17066426755718106944",
            StitchUrls.extractProjectId(
                "https://app-companion-430619.appspot.com/projects/17066426755718106944?usegapi=1&jsh=x",
            ),
        )
    }

    @Test
    fun returnsNullForLandingPage() {
        assertNull(StitchUrls.extractProjectId("https://stitch.withgoogle.com/"))
        assertNull(StitchUrls.extractProjectId("https://app-companion-430619.appspot.com/?usegapi=1"))
    }

    @Test
    fun returnsNullForAssetAndPingUrls() {
        assertNull(StitchUrls.extractProjectId("https://stitch.withgoogle.com/assets/index-CM1lE9by.css"))
        assertNull(StitchUrls.extractProjectId("https://stitch.withgoogle.com/_/Nemo/cspreport"))
    }

    @Test
    fun ignoresShortNonNumericProjectSegments() {
        // /projects/new or a short id is not a real 20-digit project id.
        assertNull(StitchUrls.extractProjectId("https://stitch.withgoogle.com/projects/new"))
        assertNull(StitchUrls.extractProjectId("https://stitch.withgoogle.com/projects/123"))
    }
}
