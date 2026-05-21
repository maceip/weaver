import { createRemoteJWKSet, jwtVerify, decodeJwt } from "jose";
import { createHash } from "node:crypto";
import { config } from "../config.js";

/**
 * Verifies the Google id_token the Android client obtains from Credential
 * Manager and derives a stable, opaque identity from its `sub` claim.
 *
 * The `sub` is the shard key for the whole service: same Google account
 * (two phones) -> same identity -> same BrowserContext; different account
 * -> different identity -> isolated context. The server NEVER trusts a
 * client-supplied user id — only what it extracts from a verified token.
 */

const GOOGLE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/oauth2/v3/certs"),
);
const GOOGLE_ISSUERS = ["https://accounts.google.com", "accounts.google.com"];

export interface VerifiedIdentity {
  /** Opaque, stable per Google account. Safe to log. Used as the shard key. */
  identity: string;
  /** Raw Google subject id. Kept server-side only. */
  sub: string;
  email?: string;
}

export class AuthError extends Error {
  constructor(
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

/** Hash the raw Google sub so the identity used in logs/keys isn't the bare PII id. */
function deriveIdentity(sub: string): string {
  return createHash("sha256").update(`weaver:${sub}`).digest("hex").slice(0, 24);
}

export async function verifyIdToken(idToken: string): Promise<VerifiedIdentity> {
  if (!idToken) throw new AuthError("missing_token", "no id_token supplied");

  if (config.devTrustTokens) {
    // DEV ONLY — decode without signature verification.
    const claims = decodeJwt(idToken);
    const sub = typeof claims.sub === "string" ? claims.sub : "";
    if (!sub) throw new AuthError("no_sub", "token has no sub claim");
    return { identity: deriveIdentity(sub), sub, email: claims.email as string | undefined };
  }

  let payload;
  try {
    ({ payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
      issuer: GOOGLE_ISSUERS,
      audience: config.allowedAudiences.length ? config.allowedAudiences : undefined,
    }));
  } catch (err) {
    throw new AuthError("verify_failed", `id_token verification failed: ${String(err)}`);
  }

  const sub = typeof payload.sub === "string" ? payload.sub : "";
  if (!sub) throw new AuthError("no_sub", "verified token has no sub claim");

  return {
    identity: deriveIdentity(sub),
    sub,
    email: typeof payload.email === "string" ? payload.email : undefined,
  };
}
