package com.weaver.app.assets

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Exposes bridge-emitted bitmaps to Compose (and any other Android consumer)
 * via a content:// URI so AsyncImage / ImageDecoder work without keeping
 * everything in process memory.
 */
class AssetContentProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        instance = this
        return true
    }

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor? {
        val id = uri.lastPathSegment ?: return null
        val file = files[id] ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/png"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private val files = ConcurrentHashMap<String, File>()
        private var instance: AssetContentProvider? = null

        fun put(
            context: Context,
            key: String,
            bytes: ByteArray,
        ): Uri {
            val dir = File(context.cacheDir, "weaver-assets").apply { mkdirs() }
            val file = File(dir, "$key.png")
            FileOutputStream(file).use { it.write(bytes) }
            files[key] = file
            val authority = "${context.packageName}.assets"
            return Uri.parse("content://$authority/$key")
        }
    }
}
