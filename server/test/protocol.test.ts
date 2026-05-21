import { test } from "node:test";
import assert from "node:assert/strict";
import { parseClientFrame } from "../src/bridge/protocol.js";

test("parses a hello frame", () => {
  const f = parseClientFrame(
    JSON.stringify({ kind: "hello", idToken: "tok", deviceId: "dev-1" }),
  );
  assert.ok(f);
  assert.equal(f.kind, "hello");
});

test("parses an inbound frame carrying a bridge payload", () => {
  const f = parseClientFrame(
    JSON.stringify({ kind: "inbound", payload: { type: "submit_prompt", text: "hi" } }),
  );
  assert.ok(f);
  assert.equal(f.kind, "inbound");
});

test("rejects a frame with no kind", () => {
  assert.equal(parseClientFrame(JSON.stringify({ idToken: "x" })), null);
});

test("rejects non-JSON", () => {
  assert.equal(parseClientFrame("not json"), null);
  assert.equal(parseClientFrame(""), null);
});
