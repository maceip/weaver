package com.weaver.app.webview

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the accept-list classification that decides Photo Picker vs SAF. */
class WebViewFileChooserTest {

    @Test
    fun emptyAcceptList_fallsBackToDocuments() {
        assertEquals(ChooserTarget.Documents, chooserTarget(emptyList()))
        assertEquals(ChooserTarget.Documents, chooserTarget(listOf("", "  ")))
    }

    @Test
    fun allImageTypes_useImageOnlyPicker() {
        assertEquals(ChooserTarget.ImageOnly, chooserTarget(listOf("image/png", "image/jpeg")))
        assertEquals(ChooserTarget.ImageOnly, chooserTarget(listOf("image/*")))
    }

    @Test
    fun allVideoTypes_useVideoOnlyPicker() {
        assertEquals(ChooserTarget.VideoOnly, chooserTarget(listOf("video/mp4", "video/*")))
    }

    @Test
    fun mixedImageAndVideo_useImageAndVideoPicker() {
        assertEquals(ChooserTarget.ImageAndVideo, chooserTarget(listOf("image/png", "video/mp4")))
    }

    @Test
    fun anyNonMediaType_fallsBackToDocuments() {
        // Stitch's real upload input mixes images with code/text/.fig, so the
        // Photo Picker can't serve it — must drop to SAF.
        assertEquals(
            ChooserTarget.Documents,
            chooserTarget(listOf("image/png", "text/plain", "application/x-figma")),
        )
        assertEquals(ChooserTarget.Documents, chooserTarget(listOf(".png")))
    }

    @Test
    fun mimeFilter_keepsMimeTypesDropsBareExtensionsAndBlanks() {
        assertArrayEquals(
            arrayOf("image/png", "text/plain"),
            mimeFilter(listOf("image/png", ".png", "text/plain", "")),
        )
    }

    @Test
    fun mimeFilter_fallsBackToWildcardWhenNoMimeTypes() {
        assertArrayEquals(arrayOf("*/*"), mimeFilter(listOf(".png", ".fig")))
        assertArrayEquals(arrayOf("*/*"), mimeFilter(emptyList()))
    }
}
