import * as x509 from "@peculiar/x509";
import { webcrypto } from "node:crypto";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { mkdtemp, writeFile, rm, access } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const execFileAsync = promisify(execFile);

// Hand @peculiar/x509 the Node platform WebCrypto. Typed loosely because the
// lib's param type is the DOM `Crypto` which the Node tsconfig doesn't pull in.
(x509.cryptoProvider.set as (c: unknown) => void)(webcrypto);

/**
 * Android Key Attestation verifier — the baseline authorization gate.
 *
 * An attested key generated in the device's hardware keystore comes with an
 * X.509 chain rooted in Google's hardware attestation CA. The chain's leaf
 * carries the Key Description extension (OID 1.3.6.1.4.1.11129.2.1.17), which
 * includes the `attestationApplicationId` — the calling app's package name
 * and the SHA-256 digests of its APK signing certificates.
 *
 * Verifying that chain proves the request comes from a genuine install of an
 * app signed by OUR key, running on a real device — a desktop / scripted
 * client cannot produce it.
 *
 * The cryptographic verification (PKIX path validation against Google's
 * trust anchors, revocation, the authorization-list constraints) is delegated
 * to Google's own `com.android.keyattestation` library, run as a JVM sidecar
 * (see ../../attestation-jvm). This module does the cheap input checks, drives
 * the sidecar, then applies the package / signing-digest / challenge policy.
 */

export interface AttestationIdentity {
  packageName: string;
  /** Base64 SHA-256 digests of the APK signing certificates. */
  signatureDigests: string[];
  /** The attestation challenge embedded in the leaf, base64. */
  challenge: string;
}

export type AttestationResult =
  | { ok: true; identity: AttestationIdentity }
  | { ok: false; code: string; message: string };

export interface VerifyOptions {
  /** Required APK package name. */
  expectedPackage: string;
  /** Allowlisted base64 SHA-256 signing-cert digests (the CI-minted key). */
  allowedSignatureDigests: string[];
  /** When set, the leaf's challenge must equal this base64 value. */
  expectedChallenge?: string;
  /** Clock for the validity-window check. Defaults to now; injectable for tests. */
  now?: Date;
}

/** Parse a chain delivered as concatenated PEM or a JSON array of base64 DER. */
export function parseChain(raw: string): x509.X509Certificate[] {
  const trimmed = raw.trim();
  if (trimmed.startsWith("[")) {
    return (JSON.parse(trimmed) as string[]).map((b64) => new x509.X509Certificate(b64));
  }
  const blocks = trimmed.match(/-----BEGIN CERTIFICATE-----[\s\S]*?-----END CERTIFICATE-----/g);
  if (!blocks) throw new Error("no PEM certificates found");
  return blocks.map((pem) => new x509.X509Certificate(pem));
}

/** Location of the built attestation-verifier fat jar. */
function sidecarJarPath(): string {
  const env = process.env.WEAVER_ATTESTATION_JAR;
  if (env) return env;
  return fileURLToPath(
    new URL(
      "../../attestation-jvm/build/libs/weaver-attestation-verifier.jar",
      import.meta.url,
    ),
  );
}

interface SidecarOk {
  ok: true;
  packageName: string;
  signatureDigests: string[];
  challenge: string;
}
interface SidecarFail {
  ok: false;
  code: string;
  message: string;
}

/** Run the JVM sidecar over a chain; it does the real PKIX verification. */
async function runSidecar(
  chain: x509.X509Certificate[],
  now: Date,
): Promise<SidecarOk | SidecarFail> {
  const jar = sidecarJarPath();
  try {
    await access(jar);
  } catch {
    return { ok: false, code: "sidecar_unavailable", message: `verifier jar not found at ${jar}` };
  }

  const dir = await mkdtemp(join(tmpdir(), "weaver-attest-"));
  const pemFile = join(dir, "chain.pem");
  try {
    await writeFile(pemFile, chain.map((c) => c.toString("pem")).join("\n"));
    let stdout: string;
    try {
      ({ stdout } = await execFileAsync("java", [
        "-jar",
        jar,
        pemFile,
        `--now=${now.getTime()}`,
      ]));
    } catch (e) {
      return { ok: false, code: "sidecar_failed", message: String(e) };
    }
    try {
      return JSON.parse(stdout.trim()) as SidecarOk | SidecarFail;
    } catch {
      return { ok: false, code: "sidecar_bad_output", message: stdout.slice(0, 200) };
    }
  } finally {
    await rm(dir, { recursive: true, force: true });
  }
}

/**
 * Full verification: cheap input checks, then Google's library (via the
 * sidecar) for the cryptographic chain validation + identity extraction,
 * then the package + signing-digest + challenge policy.
 */
export async function verifyAttestation(
  chainPem: string,
  opts: VerifyOptions,
): Promise<AttestationResult> {
  let chain: x509.X509Certificate[];
  try {
    chain = parseChain(chainPem);
  } catch (e) {
    return { ok: false, code: "chain_parse_failed", message: String(e) };
  }
  if (chain.length < 2) {
    return { ok: false, code: "chain_too_short", message: "need leaf + >=1 CA cert" };
  }

  // Validity window — every cert must be currently in date. Cheap and
  // deterministic; the sidecar's PKIX repeats it, but doing it here yields a
  // precise `cert_expired` code and lets a test pin `now`.
  const now = opts.now ?? new Date();
  for (const cert of chain) {
    if (now < cert.notBefore || now > cert.notAfter) {
      return {
        ok: false,
        code: "cert_expired",
        message: "a cert in the chain is out of its validity window",
      };
    }
  }

  const sidecar = await runSidecar(chain, now);
  if (!sidecar.ok) {
    return { ok: false, code: sidecar.code, message: sidecar.message };
  }
  const identity: AttestationIdentity = {
    packageName: sidecar.packageName,
    signatureDigests: sidecar.signatureDigests,
    challenge: sidecar.challenge,
  };

  if (identity.packageName !== opts.expectedPackage) {
    return {
      ok: false,
      code: "wrong_package",
      message: `package ${identity.packageName} != ${opts.expectedPackage}`,
    };
  }
  const digestMatch = identity.signatureDigests.some((d) =>
    opts.allowedSignatureDigests.includes(d),
  );
  if (!digestMatch) {
    return { ok: false, code: "wrong_signing_key", message: "no signing-cert digest in the allowlist" };
  }
  if (opts.expectedChallenge && identity.challenge !== opts.expectedChallenge) {
    return { ok: false, code: "challenge_mismatch", message: "attestation challenge did not match" };
  }

  return { ok: true, identity };
}
