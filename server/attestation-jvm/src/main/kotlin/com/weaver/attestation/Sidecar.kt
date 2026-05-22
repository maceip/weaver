/*
 * Copyright 2026 Weaver. Licensed under the Apache License, Version 2.0.
 */

package com.weaver.attestation

import com.android.keyattestation.verifier.GoogleTrustAnchors
import com.android.keyattestation.verifier.KeyDescription
import com.android.keyattestation.verifier.VerificationResult
import com.android.keyattestation.verifier.Verifier
import com.android.keyattestation.verifier.asX509Certificate
import com.google.gson.GsonBuilder
import java.io.File
import java.time.Instant
import java.util.Base64
import kotlin.system.exitProcess

/**
 * CLI sidecar around Google's `com.android.keyattestation` verifier. The
 * weaver server shells out to this jar once per device connection.
 *
 *   java -jar weaver-attestation-verifier.jar <pem-file> [--now=<iso|millis>]
 *
 * It runs the real PKIX path validation against Google's hardware-attestation
 * trust anchors and extracts the leaf's app identity. Output is a single line
 * of JSON on stdout — success carries `packageName`, `signatureDigests` and
 * `challenge`; the server applies the package / digest / challenge policy.
 */

private val PEM_BLOCK =
  """-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----""".toRegex()

private data class Ok(
  val packageName: String,
  val signatureDigests: List<String>,
  val challenge: String,
) {
  val ok = true
}

private data class Fail(val code: String, val message: String) {
  val ok = false
}

private val gson = GsonBuilder().disableHtmlEscaping().create()

private fun emit(result: Any): Nothing {
  println(gson.toJson(result))
  exitProcess(0)
}

private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

private fun parseInstant(raw: String): Instant =
  if (raw.all { it.isDigit() }) Instant.ofEpochMilli(raw.toLong()) else Instant.parse(raw)

fun main(args: Array<String>) {
  var pemPath: String? = null
  var now: Instant = Instant.now()
  for (arg in args) {
    when {
      arg.startsWith("--now=") -> now = parseInstant(arg.removePrefix("--now="))
      else -> pemPath = arg
    }
  }
  if (pemPath == null) {
    System.err.println("usage: verifier <pem-file> [--now=<iso|millis>]")
    exitProcess(2)
  }

  val text =
    try {
      File(pemPath).readText()
    } catch (e: Exception) {
      emit(Fail("read_failed", e.toString()))
    }

  val certs = PEM_BLOCK.findAll(text).map { it.value.asX509Certificate() }.toList()
  if (certs.isEmpty()) emit(Fail("chain_parse_failed", "no PEM certificates found"))

  // KeyAttestationCertPath expects the full chain including the self-issued
  // root as the last entry — it strips the anchor itself.
  val result =
    try {
      Verifier(GoogleTrustAnchors, { emptySet() }, { now }).verify(certs)
    } catch (e: Exception) {
      emit(Fail("verify_threw", e.toString()))
    }

  when (result) {
    is VerificationResult.Success -> {
      val keyDescription =
        try {
          KeyDescription.parseFrom(certs[0])
        } catch (e: Exception) {
          emit(Fail("extension_parse_failed", e.toString()))
        }
          ?: emit(Fail("extension_parse_failed", "no key-attestation extension on leaf"))
      val appId =
        keyDescription.softwareEnforced.attestationApplicationId
          ?: keyDescription.hardwareEnforced.attestationApplicationId
          ?: emit(Fail("extension_parse_failed", "attestationApplicationId not present"))
      emit(
        Ok(
          packageName = appId.packages.first().name,
          signatureDigests = appId.signatures.map { b64(it.toByteArray()) },
          challenge = b64(keyDescription.attestationChallenge.toByteArray()),
        )
      )
    }
    is VerificationResult.PathValidationFailure ->
      emit(Fail("path_validation_failed", result.cause.message ?: "PKIX path validation failed"))
    is VerificationResult.ChainParsingFailure ->
      emit(Fail("chain_parse_failed", result.cause.message ?: "chain parsing failed"))
    is VerificationResult.ExtensionParsingFailure ->
      emit(Fail("extension_parse_failed", result.cause.message ?: "extension parsing failed"))
    is VerificationResult.ConstraintViolation ->
      emit(Fail("constraint_violation", "${result.constraintLabel}: ${result.cause}"))
    VerificationResult.ChallengeMismatch -> emit(Fail("challenge_mismatch", "challenge mismatch"))
    VerificationResult.SoftwareAttestationUnsupported ->
      emit(Fail("software_attestation", "software attestation is not supported"))
  }
}
