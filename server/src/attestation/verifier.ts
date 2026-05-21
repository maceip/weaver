import * as x509 from "@peculiar/x509";
import * as asn1js from "asn1js";
import { webcrypto } from "node:crypto";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const GOOGLE_ROOTS = require("./google-attestation-roots.json") as string[];

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
 * client cannot produce it. We check three things:
 *   1. the chain validates and roots in a Google attestation CA,
 *   2. the package name is ours,
 *   3. a signing-cert digest is in our allowlist (the key minted at CI).
 *
 * Ported from the focused checks of github.com/android/keyattestation (a
 * JVM library). Deferred vs. that library: revocation list, the full
 * authorization-list constraint checks, and a server-issued challenge for
 * replay resistance (see [VerifyOptions.expectedChallenge]).
 */

/** Android Key Description extension. */
const KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17";
/** KeyMint tag for attestationApplicationId within an AuthorizationList. */
const ATTESTATION_APPLICATION_ID_TAG = 709;

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

let cachedRoots: x509.X509Certificate[] | null = null;
function googleRoots(): x509.X509Certificate[] {
  if (!cachedRoots) cachedRoots = GOOGLE_ROOTS.map((pem) => new x509.X509Certificate(pem));
  return cachedRoots;
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

/**
 * Full verification: chain signatures, Google-root anchoring, extension parse,
 * and the package + signing-digest + challenge checks.
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

  // Each cert must be signed by the next one up.
  for (let i = 0; i < chain.length - 1; i++) {
    const signedByNext = await chain[i]!.verify({ publicKey: await chain[i + 1]!.publicKey, signatureOnly: true });
    if (!signedByNext) {
      return { ok: false, code: "chain_broken", message: `cert ${i} not signed by cert ${i + 1}` };
    }
  }

  // The top of the chain must be (or be signed by) a Google attestation root.
  const top = chain[chain.length - 1]!;
  const roots = googleRoots();
  const anchored = await isGoogleAnchored(top, roots);
  if (!anchored) {
    return { ok: false, code: "untrusted_root", message: "chain does not anchor to a Google root" };
  }

  // Validity window — every cert must be currently in date.
  const now = opts.now ?? new Date();
  for (const cert of chain) {
    if (now < cert.notBefore || now > cert.notAfter) {
      return { ok: false, code: "cert_expired", message: "a cert in the chain is out of its validity window" };
    }
  }

  let identity: AttestationIdentity;
  try {
    identity = extractIdentity(chain[0]!);
  } catch (e) {
    return { ok: false, code: "extension_parse_failed", message: String(e) };
  }

  if (identity.packageName !== opts.expectedPackage) {
    return { ok: false, code: "wrong_package", message: `package ${identity.packageName} != ${opts.expectedPackage}` };
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

async function isGoogleAnchored(
  top: x509.X509Certificate,
  roots: x509.X509Certificate[],
): Promise<boolean> {
  const topDer = Buffer.from(top.rawData);
  for (const root of roots) {
    if (topDer.equals(Buffer.from(root.rawData))) return true; // chain ends at the root
    if (top.issuer === root.subject) {
      try {
        if (await top.verify({ publicKey: await root.publicKey, signatureOnly: true })) return true;
      } catch {
        /* try next root */
      }
    }
  }
  return false;
}

/** Parse the leaf's Key Description extension into the attestation identity. */
function extractIdentity(leaf: x509.X509Certificate): AttestationIdentity {
  const ext = leaf.extensions.find((e) => e.type === KEY_DESCRIPTION_OID);
  if (!ext) throw new Error("leaf has no key-attestation extension");

  const keyDesc = derSequence(ext.value);
  // KeyDescription: [...,4]=challenge, [6]=softwareEnforced, [7]=teeEnforced.
  const challenge = octetStringB64(keyDesc[4]);
  const appId =
    findAttestationApplicationId(keyDesc[6]) ?? findAttestationApplicationId(keyDesc[7]);
  if (!appId) throw new Error("attestationApplicationId not present");

  return { packageName: appId.packageName, signatureDigests: appId.signatures, challenge };
}

interface AppId {
  packageName: string;
  signatures: string[];
}

/** Find the [709] attestationApplicationId inside an AuthorizationList SEQUENCE. */
function findAttestationApplicationId(authList: asn1js.AsnType | undefined): AppId | null {
  if (!authList || !(authList instanceof asn1js.Sequence)) return null;
  for (const field of authList.valueBlock.value) {
    const tagged = field as asn1js.BaseBlock;
    if (
      tagged.idBlock.tagClass === 3 && // context-specific
      tagged.idBlock.tagNumber === ATTESTATION_APPLICATION_ID_TAG
    ) {
      // [709] EXPLICIT OCTET STRING; the octets are a DER AttestationApplicationId.
      const inner = (tagged as asn1js.Constructed).valueBlock.value[0] as asn1js.OctetString;
      const appIdSeq = derSequenceFromBytes(inner.valueBlock.valueHexView);
      return parseAppId(appIdSeq);
    }
  }
  return null;
}

function parseAppId(seq: asn1js.AsnType[]): AppId {
  // AttestationApplicationId ::= SEQUENCE { packages SET, signatureDigests SET }.
  const packagesSet = seq[0] as asn1js.Set;
  const signaturesSet = seq[1] as asn1js.Set;

  const firstPkg = packagesSet.valueBlock.value[0] as asn1js.Sequence;
  const nameOctet = firstPkg.valueBlock.value[0] as asn1js.OctetString;
  const packageName = Buffer.from(nameOctet.valueBlock.valueHexView).toString("utf8");

  const signatures = signaturesSet.valueBlock.value.map((s) =>
    Buffer.from((s as asn1js.OctetString).valueBlock.valueHexView).toString("base64"),
  );
  return { packageName, signatures };
}

// ── ASN.1 helpers ───────────────────────────────────────────────────────────

function derSequenceFromBytes(bytes: Uint8Array): asn1js.AsnType[] {
  // Copy into a fresh, non-shared ArrayBuffer for asn1js.
  const ab = Uint8Array.from(bytes).buffer;
  const parsed = asn1js.fromBER(ab);
  if (parsed.offset === -1) throw new Error("ASN.1 parse failed");
  return (parsed.result as asn1js.Sequence).valueBlock.value;
}

function derSequence(value: ArrayBuffer): asn1js.AsnType[] {
  const parsed = asn1js.fromBER(value);
  if (parsed.offset === -1) throw new Error("ASN.1 parse failed");
  return (parsed.result as asn1js.Sequence).valueBlock.value;
}

function octetStringB64(node: asn1js.AsnType | undefined): string {
  if (!(node instanceof asn1js.OctetString)) return "";
  return Buffer.from(node.valueBlock.valueHexView).toString("base64");
}
