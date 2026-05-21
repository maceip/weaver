package com.weaver.app

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import com.weaver.app.assets.BitmapCache
import com.weaver.app.auth.AccountResolver
import com.weaver.app.data.ProjectRepository
import java.util.UUID

class WeaverApp : Application() {
    val accountResolver: AccountResolver by lazy { AccountResolver(this) }
    val bitmapCache: BitmapCache by lazy { BitmapCache() }
    val projectRepository: ProjectRepository by lazy { ProjectRepository(this) }

    /**
     * Stable per-install id. Lets the session bridge dedupe a device's
     * reconnects and count how many of a user's phones are attached.
     * Generated once, persisted; not tied to hardware identifiers.
     */
    val deviceId: String by lazy {
        val prefs = getSharedPreferences("weaver_device", Context.MODE_PRIVATE)
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("device_id", it) }
        }
    }
}
