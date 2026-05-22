package com.weaver.app.webview

import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import com.weaver.app.bridge.AttachedFile
import com.weaver.app.bridge.Bridge
import com.weaver.app.bridge.Inbound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "WeaverFileChooser"
private const val MAX_UPLOAD_BYTES = 10 * 1024 * 1024

/**
 * Bridges file uploads between Stitch and Android's native pickers.
 *
 * Primary path — [requestUpload]: the user taps Weaver's "Upload Files", we
 * launch the system picker (which carries its own user activation), read the
 * chosen files, and inject their bytes into Stitch's `<input type=file>` over
 * the bridge. This is necessary because the WebView is headless: Chromium
 * gates a *scripted* file-input click on a user activation the headless view
 * can never receive, so the picker cannot be driven from inside the page.
 *
 * Fallback path — [show]: if Stitch's own input ever opens the chooser with a
 * genuine activation, [WebChromeClient.onShowFileChooser] still routes it to
 * the Photo Picker / SAF.
 *
 * Construct from the Activity's `onCreate` — `registerForActivityResult` must
 * run before the Activity is STARTED.
 */
class WebViewFileChooser(
    private val activity: ComponentActivity,
    private val bridge: Bridge,
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var pendingWebViewCallback: ValueCallback<Array<Uri>>? = null
    private var singleSelect = false

    private val pickMedia = activity.registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> deliverToWebView(uris) }

    private val openDocuments = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> deliverToWebView(uris) }

    private val uploadLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris -> onUploadPicked(uris) }

    /** Native-initiated upload — launch the system picker, inject what it returns. */
    fun requestUpload() {
        uploadLauncher.launch(arrayOf("*/*"))
    }

    /** Fallback for a WebView-initiated `onShowFileChooser`. */
    fun show(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams,
    ): Boolean {
        pendingWebViewCallback?.onReceiveValue(null)
        pendingWebViewCallback = callback
        singleSelect = params.mode != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val accepts = params.acceptTypes.orEmpty().toList()
        when (chooserTarget(accepts)) {
            ChooserTarget.ImageOnly ->
                pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
            ChooserTarget.VideoOnly ->
                pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.VideoOnly))
            ChooserTarget.ImageAndVideo ->
                pickMedia.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
            ChooserTarget.Documents ->
                openDocuments.launch(mimeFilter(accepts))
        }
        return true
    }

    private fun deliverToWebView(uris: List<Uri>) {
        val callback = pendingWebViewCallback ?: return
        pendingWebViewCallback = null
        val picked = if (singleSelect) uris.take(1) else uris
        callback.onReceiveValue(picked.toTypedArray().takeIf { it.isNotEmpty() })
    }

    private fun onUploadPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        ioScope.launch {
            val files = uris.mapNotNull { readFile(it) }
            if (files.isEmpty()) {
                Log.w(TAG, "upload produced no readable files (${uris.size} picked)")
                return@launch
            }
            withContext(Dispatchers.Main) { bridge.send(Inbound.AttachFiles(files)) }
        }
    }

    private fun readFile(uri: Uri): AttachedFile? = runCatching {
        val resolver = activity.contentResolver
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        when {
            bytes == null -> null
            bytes.size > MAX_UPLOAD_BYTES -> {
                Log.w(TAG, "skipping upload: ${bytes.size} bytes exceeds cap")
                null
            }
            else -> AttachedFile(
                name = displayName(uri) ?: "upload",
                mime = resolver.getType(uri) ?: "application/octet-stream",
                data = Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        activity.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}

internal enum class ChooserTarget { ImageOnly, VideoOnly, ImageAndVideo, Documents }

/**
 * Classifies a file input's `accept` list into the picker to show. The Photo
 * Picker only handles media, so it's used only when *every* accepted type is
 * image/video; any other type (code, text, `.fig`, …) falls back to SAF.
 */
internal fun chooserTarget(accepts: List<String>): ChooserTarget {
    val types = accepts.filter { it.isNotBlank() }
    if (types.isEmpty()) return ChooserTarget.Documents
    val image = types.all { it.startsWith("image/") }
    val video = types.all { it.startsWith("video/") }
    val mediaOnly = types.all { it.startsWith("image/") || it.startsWith("video/") }
    return when {
        image -> ChooserTarget.ImageOnly
        video -> ChooserTarget.VideoOnly
        mediaOnly -> ChooserTarget.ImageAndVideo
        else -> ChooserTarget.Documents
    }
}

// SAF filters on MIME types only; drop bare extensions and fall back to all types.
internal fun mimeFilter(accepts: List<String>): Array<String> {
    val mimes = accepts.filter { it.isNotBlank() && "/" in it }
    return if (mimes.isEmpty()) arrayOf("*/*") else mimes.toTypedArray()
}
