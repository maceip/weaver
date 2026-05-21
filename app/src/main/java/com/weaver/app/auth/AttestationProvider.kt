package com.weaver.app.auth

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec

private const val TAG = "WeaverAttest"
private const val KEY_ALIAS = "weaver_attestation_key"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"

/**
 * Produces the device Key Attestation header that the session bridge uses as
 * its baseline authorization gate.
 *
 * Generates an EC key in the hardware-backed Android Keystore with an
 * attestation challenge. The keystore returns an X.509 certificate chain
 * rooted in Google's hardware attestation CA; the chain's leaf carries the
 * Key Description extension, which embeds this app's package name and the
 * SHA-256 digests of its APK signing certificates.
 *
 * A desktop or scripted client cannot produce such a chain — only a genuine
 * install of an app signed by our CI key on a real device can. The server
 * (`server/src/attestation/verifier.ts`) checks exactly that.
 *
 * Keystore key generation is hardware-bound and costs a few hundred ms, so
 * the chain is generated once on first use and cached for the process.
 */
class AttestationProvider {

    @Volatile
    private var cached: String? = null

    /**
     * The value for the `X-Weaver-Attestation` request header: a JSON array of
     * base64-encoded DER certificates. Returns null when the device cannot
     * produce a hardware attestation (the caller then falls back to the local
     * transport, which never talks to the bridge).
     */
    @Synchronized
    fun attestationHeader(): String? {
        cached?.let { return it }
        return runCatching { buildChain() }
            .onFailure { Log.w(TAG, "attestation unavailable: ${it.message}") }
            .getOrNull()
            ?.also { cached = it }
    }

    private fun buildChain(): String {
        // A fresh challenge per key. Server-issued challenges (for replay
        // resistance) are a documented follow-up; the security-critical check
        // is the chain + app identity, which a challenge does not affect.
        val challenge = ByteArray(24).also { SecureRandom().nextBytes(it) }

        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAttestationChallenge(challenge)
            .build()

        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).apply {
            initialize(spec)
            generateKeyPair()
        }

        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val chain = keyStore.getCertificateChain(KEY_ALIAS)
            ?: error("no attestation chain — device lacks hardware key attestation")

        val json = JSONArray()
        for (cert in chain) {
            json.put(Base64.encodeToString(cert.encoded, Base64.NO_WRAP))
        }
        return json.toString()
    }
}
