package com.weaver.app

import android.app.Application
import com.weaver.app.assets.BitmapCache
import com.weaver.app.auth.AccountResolver

class WeaverApp : Application() {
    val accountResolver: AccountResolver by lazy { AccountResolver(this) }
    val bitmapCache: BitmapCache by lazy { BitmapCache() }
}
