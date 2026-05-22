import { test } from "node:test";
import assert from "node:assert/strict";
import { parseClientFrame } from "../src/bridge/protocol.js";

test("parses a hello frame and keeps its fields", () => {
  const f = parseClientFrame(
    JSON.stringify({ kind: "hello", idToken: "tok", deviceId: "dev-1" }),
  );
  assert.ok(f);
  assert.equal(f.kind, "hello");
  if (f.kind === "hello") {
    assert.equal(f.idToken, "tok");
    assert.equal(f.deviceId, "dev-1");
  }
});

test("parses an inbound frame carrying a bridge payload", () => {
  const f = parseClientFrame(
    JSON.stringify({ kind: "inbound", payload: { type: "submit_prompt", text: "hi" } }),
  );
  assert.ok(f);
  assert.equal(f.kind, "inbound");
  if (f.kind === "inbound") assert.equal(f.payload.type, "submit_prompt");
});

test("parses ping and pong frames", () => {
  assert.equal(parseClientFrame(JSON.stringify({ kind: "ping" }))?.kind, "ping");
  assert.equal(parseClientFrame(JSON.stringify({ kind: "pong" }))?.kind, "pong");
});

test("rejects a frame with no kind", () => {
  assert.equal(parseClientFrame(JSON.stringify({ idToken: "x" })), null);
});

test("rejects a frame whose kind is not a string", () => {
  assert.equal(parseClientFrame(JSON.stringify({ kind: 7 })), null);
  assert.equal(parseClientFrame(JSON.stringify({ kind: null })), null);
});

test("rejects non-object JSON", () => {
  assert.equal(parseClientFrame("[]"), null);
  assert.equal(parseClientFrame("42"), null);
  assert.equal(parseClientFrame("null"), null);
  assert.equal(parseClientFrame('"a string"'), null);
});

test("rejects non-JSON and empty input", () => {
  assert.equal(parseClientFrame("not json"), null);
  assert.equal(parseClientFrame(""), null);
  assert.equal(parseClientFrame("{"), null);
});
