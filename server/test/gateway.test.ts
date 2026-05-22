import { test } from "node:test";
import assert from "node:assert/strict";

// The gateway reads config at import time; dev-trust so id_tokens decode
// without a JWKS round-trip. Set before importing.
process.env.WEAVER_DEV_TRUST_TOKENS = "1";
const { BridgeGateway } = await import("../src/bridge/gateway.js");
import type { BridgeSession, SessionProvider } from "../src/bridge/sessionTypes.js";
import type { BridgePayload } from "../src/bridge/protocol.js";

/** Minimal unsigned JWT for the dev-trust path. */
function devToken(sub: string): string {
  const b64 = (o: unknown): string => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "none" })}.${b64({ sub })}.sig`;
}

/** Flush the gateway's async bind() (verifyIdToken + sessionFor). */
const flush = (): Promise<void> => new Promise((r) => setTimeout(r, 5));

let sessionSeq = 0;

/** Fake of the per-identity Stitch session — records what the gateway does. */
class FakeSession implements BridgeSession {
  readonly sessionId = `sess-${++sessionSeq}`;
  readonly devices = new Set<string>();
  readonly inbound: BridgePayload[] = [];
  private readonly listeners = new Set<(p: BridgePayload) => void>();

  get deviceCount(): number {
    return this.devices.size;
  }
  attachDevice(id: string): void {
    this.devices.add(id);
  }
  detachDevice(id: string): void {
    this.devices.delete(id);
  }
  onOutbound(fn: (p: BridgePayload) => void): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }
  async sendInbound(p: BridgePayload): Promise<void> {
    this.inbound.push(p);
  }
  /** Test helper: simulate the remote page emitting an event. */
  emitOutbound(p: BridgePayload): void {
    for (const fn of this.listeners) fn(p);
  }
}

/** Fake SessionProvider — one session per identity, like the real shard key. */
class FakeProvider implements SessionProvider {
  readonly sessions = new Map<string, FakeSession>();
  calls = 0;
  async sessionFor(identity: string): Promise<BridgeSession> {
    this.calls += 1;
    let s = this.sessions.get(identity);
    if (!s) {
      s = new FakeSession();
      this.sessions.set(identity, s);
    }
    return s;
  }
}

/** Fake `ws` WebSocket — captures sent frames, drives inbound events. */
class FakeSocket {
  readonly OPEN = 1;
  readyState = 1;
  readonly sent: Record<string, unknown>[] = [];
  private readonly handlers = new Map<string, ((arg?: unknown) => void)[]>();

  on(event: string, cb: (arg?: unknown) => void): this {
    const list = this.handlers.get(event) ?? [];
    list.push(cb);
    this.handlers.set(event, list);
    return this;
  }
  send(data: string): void {
    this.sent.push(JSON.parse(data) as Record<string, unknown>);
  }
  close(): void {
    this.readyState = 3;
    this.fire("close");
  }
  terminate(): void {
    this.readyState = 3;
  }
  private fire(event: string, arg?: unknown): void {
    for (const h of this.handlers.get(event) ?? []) h(arg);
  }
  /** Test helper: deliver a client frame as the gateway would receive it. */
  receive(frame: Record<string, unknown>): void {
    this.fire("message", Buffer.from(JSON.stringify(frame)));
  }
  frames(kind: string): Record<string, unknown>[] {
    return this.sent.filter((f) => f.kind === kind);
  }
}

function gatewayWith(provider: SessionProvider): InstanceType<typeof BridgeGateway> {
  return new BridgeGateway(provider);
}

// ── Scenario 5: two phones, same Google account, one shared session ─────────
test("two devices on one identity share a single StitchSession", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const a = new FakeSocket();
  const b = new FakeSocket();
  gw.handle(a as never);
  gw.handle(b as never);

  a.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-A" });
  b.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-B" });
  await flush();

  // Same Google sub -> one session, both phones attached.
  assert.equal(provider.sessions.size, 1, "one identity -> one session");
  const session = [...provider.sessions.values()][0]!;
  assert.deepEqual([...session.devices].sort(), ["phone-A", "phone-B"]);
  // Each socket got a `ready` frame naming that session.
  assert.equal(a.frames("ready").length, 1);
  assert.equal(b.frames("ready").length, 1);
  assert.equal(a.frames("ready")[0]!.sessionId, session.sessionId);

  a.close();
  b.close();
});

test("outbound from the shared session broadcasts to every attached device", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const a = new FakeSocket();
  const b = new FakeSocket();
  gw.handle(a as never);
  gw.handle(b as never);
  a.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "A" });
  b.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "B" });
  await flush();

  const session = [...provider.sessions.values()][0]!;
  session.emitOutbound({ type: "nodes_updated", nodes: [] });

  // Both phones see the same canvas event.
  assert.equal(a.frames("outbound").length, 1);
  assert.equal(b.frames("outbound").length, 1);
  assert.equal((a.frames("outbound")[0]!.payload as BridgePayload).type, "nodes_updated");

  a.close();
  b.close();
});

test("inbound from either device is applied to the shared session", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const a = new FakeSocket();
  const b = new FakeSocket();
  gw.handle(a as never);
  gw.handle(b as never);
  a.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "A" });
  b.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "B" });
  await flush();

  a.receive({ kind: "inbound", payload: { type: "submit_prompt", text: "from A" } });
  b.receive({ kind: "inbound", payload: { type: "select_node", id: "n1" } });
  await flush();

  const session = [...provider.sessions.values()][0]!;
  assert.deepEqual(
    session.inbound.map((p) => p.type).sort(),
    ["select_node", "submit_prompt"],
  );

  a.close();
  b.close();
});

// ── Scenario 6: two users -> isolated sessions ──────────────────────────────
test("different identities get isolated sessions (no cross-talk)", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const u1 = new FakeSocket();
  const u2 = new FakeSocket();
  gw.handle(u1 as never);
  gw.handle(u2 as never);

  u1.receive({ kind: "hello", idToken: devToken("dev-99"), deviceId: "d1" });
  u2.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "d2" });
  await flush();

  assert.equal(provider.sessions.size, 2, "two identities -> two sessions");

  // An event in user 1's session must never reach user 2's socket.
  const sessions = [...provider.sessions.values()];
  sessions[0]!.emitOutbound({ type: "nodes_updated", nodes: [] });
  const total = u1.frames("outbound").length + u2.frames("outbound").length;
  assert.equal(total, 1, "outbound reached exactly one user's socket");

  u1.close();
  u2.close();
});

test("closing a socket detaches just that device from the session", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const a = new FakeSocket();
  const b = new FakeSocket();
  gw.handle(a as never);
  gw.handle(b as never);
  a.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "A" });
  b.receive({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "B" });
  await flush();

  const session = [...provider.sessions.values()][0]!;
  assert.equal(session.deviceCount, 2);

  a.close();
  assert.equal(session.deviceCount, 1, "phone A detached");
  assert.deepEqual([...session.devices], ["B"]);

  b.close();
});

test("a bad token is rejected with a fatal error frame", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const s = new FakeSocket();
  gw.handle(s as never);

  s.receive({ kind: "hello", idToken: "not-a-jwt", deviceId: "d" });
  await flush();

  assert.equal(provider.sessions.size, 0, "no session for an unauthenticated socket");
  assert.equal(s.frames("error").length, 1);
  assert.equal(s.frames("error")[0]!.fatal, true);
});

test("inbound before hello is refused", async () => {
  const provider = new FakeProvider();
  const gw = gatewayWith(provider);
  const s = new FakeSocket();
  gw.handle(s as never);

  s.receive({ kind: "inbound", payload: { type: "submit_prompt", text: "x" } });
  await flush();

  assert.equal(s.frames("error")[0]!.code, "not_bound");
  s.close();
});
