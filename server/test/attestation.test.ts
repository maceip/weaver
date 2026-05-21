import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { parseChain, verifyAttestation } from "../src/attestation/verifier.js";

const fixture = (name: string): string =>
  readFileSync(fileURLToPath(new URL(`./fixtures/${name}`, import.meta.url)), "utf8");

// A real Android key-attestation chain + its decoded expectations, lifted
// from github.com/android/keyattestation testdata (caiman / sdk36).
const REAL_CHAIN = fixture("real-attestation-chain.pem");
const EXPECTED = JSON.parse(fixture("real-attestation-expected.json")) as {
  attestationChallenge: string;
  softwareEnforced: {
    attestationApplicationId: { packages: { name: string }[]; signatures: string[] };
  };
};
const REAL_PACKAGE = EXPECTED.softwareEnforced.attestationApplicationId.packages[0]!.name;
const REAL_DIGEST = EXPECTED.softwareEnforced.attestationApplicationId.signatures[0]!;
// The RKP intermediate in this fixture is valid Sep 24 – Oct 3 2025; pin the
// verifier's clock inside that window so the time-frozen fixture verifies.
const FIXTURE_DATE = new Date("2025-09-28T00:00:00Z");

test("parses the 5-cert real attestation chain", () => {
  assert.equal(parseChain(REAL_CHAIN).length, 5);
});

test("verifies a real chain and extracts the app identity", async () => {
  const result = await verifyAttestation(REAL_CHAIN, {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: [REAL_DIGEST],
    now: FIXTURE_DATE,
  });
  assert.equal(result.ok, true, result.ok ? "" : `${result.code}: ${result.message}`);
  if (result.ok) {
    assert.equal(result.identity.packageName, REAL_PACKAGE);
    assert.ok(result.identity.signatureDigests.includes(REAL_DIGEST));
  }
});

test("rejects a chain whose package is not ours", async () => {
  const result = await verifyAttestation(REAL_CHAIN, {
    expectedPackage: "com.someone.else",
    allowedSignatureDigests: [REAL_DIGEST],
    now: FIXTURE_DATE,
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "wrong_package");
});

test("rejects a chain signed by a key not in the allowlist", async () => {
  const result = await verifyAttestation(REAL_CHAIN, {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: ["AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="],
    now: FIXTURE_DATE,
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "wrong_signing_key");
});

test("rejects a challenge that does not match", async () => {
  const result = await verifyAttestation(REAL_CHAIN, {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: [REAL_DIGEST],
    now: FIXTURE_DATE,
    expectedChallenge: Buffer.from("not-the-challenge").toString("base64"),
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "challenge_mismatch");
});

test("rejects an expired chain (fixture verified at today's date)", async () => {
  // No `now` override -> real clock -> the Sep 2025 RKP intermediate is expired.
  const result = await verifyAttestation(REAL_CHAIN, {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: [REAL_DIGEST],
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "cert_expired");
});

test("rejects a single-cert chain (no CA path)", async () => {
  const oneCert = REAL_CHAIN.match(
    /-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/,
  )![0];
  const result = await verifyAttestation(oneCert, {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: [REAL_DIGEST],
    now: FIXTURE_DATE,
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "chain_too_short");
});

test("rejects garbage input", async () => {
  const result = await verifyAttestation("not a cert", {
    expectedPackage: REAL_PACKAGE,
    allowedSignatureDigests: [REAL_DIGEST],
    now: FIXTURE_DATE,
  });
  assert.equal(result.ok, false);
  if (!result.ok) assert.equal(result.code, "chain_parse_failed");
});
