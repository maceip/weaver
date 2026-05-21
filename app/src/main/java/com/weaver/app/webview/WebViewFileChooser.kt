package com.weaver.app.webview

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia

/**
 * Routes a WebView `<input type=file>` into Android's modern pickers — the
 * Photo Picker for image/video inputs, Storage Access Framework otherwise.
 * Neither needs a storage permission.
 *
 * Construct from the Activity's `onCreate` (it registers result launchers,
 * which must happen before the Activity is STARTED), then hand [show] to
 * [WebChromeClient.onShowFileChooser].
 */
class WebViewFileChooser(activity: ComponentActivity) {

    private var pending: ValueCallback<Array<Uri>>? = null
    private var singleSelect = false

    private val pickMedia = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> deliver(uris) }

    private val openDocuments = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> deliver(uris) }

    /** Launches the picker; the WebView [callback] is invoked later, on result. */
    fun show(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        // A previous chooser is still open — cancel it so the WebView isn't stuck.
        pending?.onReceiveValue(null)
        pending = callback
        singleSelect = params.mode != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

        val accepts = params.acceptTypes.orEmpty().filter { it.isNotBlank() }
        val mediaType = visualMediaType(accepts)
        if (mediaType != null) {
            pickMedia.launch(PickVisualMediaRequest(mediaType))
        } else {
            openDocuments.launch(mimeFilter(accepts))
        }
        return true
    }

    private fun deliver(uris: List<Uri>) {
        val picked = if (singleSelect) uris.take(1) else uris
        pending?.onReceiveValue(picked.toTypedArray().takeIf { it.isNotEmpty() })
        pending = null
    }
}

/** Photo Picker type when every accepted type is image/video, else null (use SAF). */
private fun visualMediaType(accepts: List<String>): PickVisualMedia.VisualMediaType? {
    if (accepts.isEmpty()) return null
    val image = accepts.all { it.startsWith("image/") }
    val video = accepts.all { it.startsWith("video/") }
    val mediaOnly = accepts.all { it.startsWith("image/") || it.startsWith("video/") }
    return when {
        image -> PickVisualMedia.ImageOnly
        video -> PickVisualMedia.VideoOnly
        mediaOnly -> PickVisualMedia.ImageAndVideo
        else -> null
    }
}

/** SAF filters on MIME types only; drop bare extensions and fall back to `*/*`. */
private fun mimeFilter(accepts: List<String>): Array<String> {
    val mimes = accepts.filter { "/" in it }
    return if (mimes.isEmpty()) arrayOf("*/*") else mimes.toTypedArray()
}
