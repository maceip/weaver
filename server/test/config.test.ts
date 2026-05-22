import { test } from "node:test";
import assert from "node:assert/strict";

// config.ts reads process.env once at import. node:test runs each test file
// in its own process, so setting env here is isolated from other test files.
process.env.HOST = "127.0.0.1";
process.env.WEAVER_ALLOWED_AUDIENCES = " aud-1 , ,aud-2 ";
process.env.WEAVER_HEARTBEAT_MS = "250";
process.env.WEAVER_MAX_CONTEXTS = "not-a-number";
process.env.WEAVER_DEV_TRUST_TOKENS = "1";
process.env.WEAVER_SIGNING_DIGESTS = "abc=,def=";
delete process.env.PORT;
delete process.env.WEAVER_DEV_SKIP_ATTESTATION;
delete process.env.WEAVER_HEADED;

const { config } = await import("../src/config.js");

test("string env vars override defaults", () => {
  assert.equal(config.host, "127.0.0.1");
});

test("unset numeric env falls back to its default", () => {
  assert.equal(config.port, 8080);
});

test("numeric env vars are parsed", () => {
  assert.equal(config.heartbeatMs, 250);
});

test("an unparseable numeric env falls back to the default", () => {
  assert.equal(config.maxContexts, 40);
});

test("comma-separated lists are split, trimmed, and emptied entries dropped", () => {
  assert.deepEqual(config.allowedAudiences, ["aud-1", "aud-2"]);
  assert.deepEqual(config.signingDigests, ["abc=", "def="]);
});

test("boolean env vars: set is true, unset is false", () => {
  assert.equal(config.devTrustTokens, true);
  assert.equal(config.devSkipAttestation, false);
  assert.equal(config.headed, false);
});
