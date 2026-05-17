package com.weaver.app.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

private const val PREFS_NAME = "weaver_auth"
private const val KEY_ACCOUNT_ID = "account_id"
private const val KEY_EMAIL = "email"
private const val KEY_DISPLAY = "display"

class AccountResolver(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): Account? {
        val id = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return Account(
            id = id,
            email = email,
            displayName = prefs.getString(KEY_DISPLAY, null),
            idToken = null,
        )
    }

    fun persist(account: Account) {
        prefs.edit {
            putString(KEY_ACCOUNT_ID, account.id)
            putString(KEY_EMAIL, account.email)
            putString(KEY_DISPLAY, account.displayName)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }
}
