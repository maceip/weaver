import { test } from "node:test";
import assert from "node:assert/strict";

// The verifier reads config at import time; set dev-trust before importing so
// the JWKS network path is bypassed and tokens are decoded, not verified.
process.env.WEAVER_DEV_TRUST_TOKENS = "1";
const { verifyIdToken, AuthError } = await import("../src/auth/verifier.js");

/** Minimal unsigned JWT (header.payload.sig) for the dev-trust path. */
function devToken(claims: Record<string, unknown>): string {
  const b64 = (o: unknown): string =>
    Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "none", typ: "JWT" })}.${b64(claims)}.sig`;
}

test("derives a stable hashed identity from the sub claim", async () => {
  const a = await verifyIdToken(devToken({ sub: "google-user-1", email: "a@x.com" }));
  const b = await verifyIdToken(devToken({ sub: "google-user-1", email: "a@x.com" }));
  assert.equal(a.identity, b.identity, "same sub must map to the same identity");
  assert.equal(a.sub, "google-user-1");
  assert.notEqual(a.identity, "google-user-1", "identity is hashed, not the raw sub");
});

test("different accounts get different identities (isolation)", async () => {
  const dev = await verifyIdToken(devToken({ sub: "developer-99" }));
  const ceo = await verifyIdToken(devToken({ sub: "ceo-1" }));
  assert.notEqual(dev.identity, ceo.identity);
});

test("rejects an empty token", async () => {
  await assert.rejects(verifyIdToken(""), (e: unknown) => {
    assert.ok(e instanceof AuthError);
    assert.equal((e as InstanceType<typeof AuthError>).code, "missing_token");
    return true;
  });
});

test("rejects a token with no sub claim", async () => {
  await assert.rejects(verifyIdToken(devToken({ email: "x@y.com" })), (e: unknown) => {
    assert.ok(e instanceof AuthError);
    assert.equal((e as InstanceType<typeof AuthError>).code, "no_sub");
    return true;
  });
});
