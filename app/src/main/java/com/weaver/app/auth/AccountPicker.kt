package com.weaver.app.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

private const val TAG = "WeaverAccountPicker"

class AccountPicker(
    private val context: Context,
    private val serverClientId: String,
    private val resolver: AccountResolver,
) {

    private val manager = CredentialManager.create(context)

    suspend fun show(activityContext: Context): Account? {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = manager.getCredential(activityContext, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data)
                val account = Account(
                    id = token.id,
                    email = token.id,
                    displayName = token.displayName,
                    idToken = token.idToken,
                )
                resolver.persist(account)
                account
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            Log.w(TAG, "credential request failed: ${e.message}")
            null
        } catch (e: GoogleIdTokenParsingException) {
            Log.w(TAG, "id token parse failed: ${e.message}")
            null
        }
    }
}
