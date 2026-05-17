package com.weaver.app.assets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache

private const val DATA_URL_PREFIX = "data:image/"

class BitmapCache(maxBytes: Int = 24 * 1024 * 1024) {

    private data class Key(val id: String, val revision: Long)

    private val cache = object : LruCache<Key, Bitmap>(maxBytes) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.byteCount
    }

    fun put(id: String, revision: Long, bitmap: Bitmap) {
        cache.put(Key(id, revision), bitmap)
    }

    fun get(id: String, revision: Long): Bitmap? = cache.get(Key(id, revision))

    fun decode(payload: String): Bitmap? {
        val bytes = decodeBytes(payload) ?: return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun decodeBytes(payload: String): ByteArray? {
        val base64 = if (payload.startsWith(DATA_URL_PREFIX)) {
            val comma = payload.indexOf(',')
            if (comma < 0) return null
            payload.substring(comma + 1)
        } else {
            payload
        }
        return runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull()
    }

    fun clear() = cache.evictAll()
}
