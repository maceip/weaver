import { test, type TestContext } from "node:test";
import assert from "node:assert/strict";

// Exercise the real HTTP + WebSocket server end-to-end: a real Fastify
// instance, the real `ws` upgrade handshake, the real BridgeGateway — only
// the Chromium-backed StitchSession is faked. Env is set before importing so
// the gateway accepts dev id_tokens and the heartbeat is fast.
process.env.WEAVER_DEV_TRUST_TOKENS = "1";
process.env.WEAVER_DEV_SKIP_ATTESTATION = "1";
process.env.WEAVER_HEARTBEAT_MS = "120";

const { buildServer } = await import("../src/server.js");
import { WebSocket } from "ws";
import type { BridgeSession, SessionProvider } from "../src/bridge/sessionTypes.js";
import type { BridgePayload } from "../src/bridge/protocol.js";

/** Minimal unsigned JWT for the dev-trust path. */
function devToken(sub: string): string {
  const b64 = (o: unknown): string => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${b64({ alg: "none" })}.${b64({ sub })}.sig`;
}

let sessionSeq = 0;

/** Stand-in for the Chromium-backed StitchSession. */
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
  emitOutbound(p: BridgePayload): void {
    for (const fn of this.listeners) fn(p);
  }
}

class FakeProvider implements SessionProvider {
  readonly sessions = new Map<string, FakeSession>();
  async sessionFor(identity: string): Promise<BridgeSession> {
    let s = this.sessions.get(identity);
    if (!s) {
      s = new FakeSession();
      this.sessions.set(identity, s);
    }
    return s;
  }
  /** The single fake session (most tests use one identity). */
  only(): FakeSession {
    assert.equal(this.sessions.size, 1, "expected exactly one session");
    return [...this.sessions.values()][0]!;
  }
}

type ServerFrame = { kind: string; [k: string]: unknown };

/** A real `ws` client with frame collection + predicate waiting. */
class Client {
  readonly ws: WebSocket;
  readonly frames: ServerFrame[] = [];
  private readonly waiters: { pred: (f: ServerFrame) => boolean; resolve: (f: ServerFrame) => void }[] =
    [];

  constructor(url: string, autoPong = true) {
    this.ws = new WebSocket(url);
    this.ws.on("message", (raw) => {
      const frame = JSON.parse(raw.toString()) as ServerFrame;
      if (frame.kind === "ping" && autoPong && this.ws.readyState === WebSocket.OPEN) {
        this.send({ kind: "pong" });
      }
      this.frames.push(frame);
      for (let i = this.waiters.length - 1; i >= 0; i--) {
        if (this.waiters[i]!.pred(frame)) {
          this.waiters.splice(i, 1)[0]!.resolve(frame);
        }
      }
    });
  }

  opened(): Promise<void> {
    if (this.ws.readyState === WebSocket.OPEN) return Promise.resolve();
    return new Promise((resolve, reject) => {
      this.ws.once("open", () => resolve());
      this.ws.once("error", reject);
    });
  }

  send(frame: Record<string, unknown>): void {
    this.ws.send(JSON.stringify(frame));
  }

  /** Resolve with the first frame (past or future) matching `pred`. */
  waitFor(pred: (f: ServerFrame) => boolean, label = "frame"): Promise<ServerFrame> {
    const seen = this.frames.find(pred);
    if (seen) return Promise.resolve(seen);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`timed out waiting for ${label}`)), 2000);
      this.waiters.push({
        pred,
        resolve: (f) => {
          clearTimeout(timer);
          resolve(f);
        },
      });
    });
  }

  closed(): Promise<number> {
    if (this.ws.readyState === WebSocket.CLOSED) return Promise.resolve(this.ws.CLOSED);
    return new Promise((resolve) => this.ws.once("close", (code) => resolve(code)));
  }

  close(): void {
    if (this.ws.readyState <= WebSocket.OPEN) this.ws.close();
  }
}

interface Harness {
  baseUrl: string;
  wsUrl: string;
  provider: FakeProvider;
  connect(autoPong?: boolean): Client;
}

/** Start a server on an ephemeral port; cleanup is registered on `t`. */
async function start(
  t: TestContext,
  opts: {
    activeContexts?: () => number;
    authorizeUpgrade?: () => Promise<boolean>;
  } = {},
): Promise<Harness> {
  const provider = new FakeProvider();
  const server = await buildServer({
    sessions: provider,
    activeContexts: opts.activeContexts ?? (() => 0),
    authorizeUpgrade: opts.authorizeUpgrade,
    logger: false,
  });
  await server.app.listen({ port: 0, host: "127.0.0.1" });
  const addr = server.app.server.address();
  if (addr === null || typeof addr === "string") throw new Error("no port");
  const { port } = addr;
  const clients: Client[] = [];
  t.after(async () => {
    for (const c of clients) c.close();
    await server.close();
  });
  return {
    baseUrl: `http://127.0.0.1:${port}`,
    wsUrl: `ws://127.0.0.1:${port}/bridge`,
    provider,
    connect: (autoPong = true) => {
      const c = new Client(`ws://127.0.0.1:${port}/bridge`, autoPong);
      clients.push(c);
      return c;
    },
  };
}

// ── HTTP endpoints ──────────────────────────────────────────────────────────
test("/healthz reports liveness", async (t) => {
  const h = await start(t);
  const res = await fetch(`${h.baseUrl}/healthz`);
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { ok: true });
});

test("/readyz reports the live BrowserContext count", async (t) => {
  const h = await start(t, { activeContexts: () => 7 });
  const res = await fetch(`${h.baseUrl}/readyz`);
  assert.deepEqual(await res.json(), { ok: true, activeContexts: 7 });
});

// ── WebSocket upgrade ───────────────────────────────────────────────────────
test("upgrade on a path other than /bridge is dropped", async (t) => {
  const h = await start(t);
  const ws = new WebSocket(`${h.baseUrl.replace("http", "ws")}/nope`);
  await assert.rejects(
    new Promise((resolve, reject) => {
      ws.once("open", resolve);
      ws.once("error", reject);
    }),
  );
});

test("a rejected authorization gate drops the upgrade", async (t) => {
  const h = await start(t, { authorizeUpgrade: async () => false });
  const ws = new WebSocket(h.wsUrl);
  await assert.rejects(
    new Promise((resolve, reject) => {
      ws.once("open", resolve);
      ws.once("error", reject);
    }),
    "upgrade should fail when the gate rejects",
  );
});

// ── Handshake ───────────────────────────────────────────────────────────────
test("hello handshake binds the socket and replies with ready", async (t) => {
  const h = await start(t);
  const c = h.connect();
  await c.opened();
  c.send({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-A" });

  const ready = await c.waitFor((f) => f.kind === "ready", "ready");
  assert.ok(typeof ready.identity === "string" && ready.identity.length > 0);
  assert.equal(ready.sessionId, h.provider.only().sessionId);
  assert.equal(ready.attachedDevices, 1);
});

test("a bad id_token yields a fatal error frame and closes the socket", async (t) => {
  const h = await start(t);
  const c = h.connect();
  await c.opened();
  c.send({ kind: "hello", idToken: "not-a-jwt", deviceId: "d" });

  const err = await c.waitFor((f) => f.kind === "error", "error");
  assert.equal(err.fatal, true);
  await c.closed();
  assert.equal(h.provider.sessions.size, 0);
});

test("an inbound frame before hello is refused", async (t) => {
  const h = await start(t);
  const c = h.connect();
  await c.opened();
  c.send({ kind: "inbound", payload: { type: "submit_prompt", text: "x" } });

  const err = await c.waitFor((f) => f.kind === "error", "error");
  assert.equal(err.code, "not_bound");
});

// ── Bridge round-trip ───────────────────────────────────────────────────────
test("inbound frames reach the session; outbound is delivered to the client", async (t) => {
  const h = await start(t);
  const c = h.connect();
  await c.opened();
  c.send({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-A" });
  await c.waitFor((f) => f.kind === "ready");

  c.send({ kind: "inbound", payload: { type: "submit_prompt", text: "make a login screen" } });
  await c.waitFor(() => h.provider.only().inbound.length > 0, "inbound applied").catch(() => {});
  // The fake records inbound synchronously on sendInbound; give the WS a tick.
  await new Promise((r) => setTimeout(r, 50));
  assert.equal(h.provider.only().inbound[0]?.text, "make a login screen");

  h.provider.only().emitOutbound({ type: "nodes_updated", nodes: [] });
  const outbound = await c.waitFor((f) => f.kind === "outbound", "outbound");
  assert.equal((outbound.payload as BridgePayload).type, "nodes_updated");
});

// ── Session multiplexing over real sockets ──────────────────────────────────
test("two devices on one identity share a session; outbound broadcasts to both", async (t) => {
  const h = await start(t);
  const a = h.connect();
  const b = h.connect();
  await Promise.all([a.opened(), b.opened()]);

  a.send({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-A" });
  await a.waitFor((f) => f.kind === "ready");
  b.send({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "phone-B" });
  const bReady = await b.waitFor((f) => f.kind === "ready");

  assert.equal(h.provider.sessions.size, 1, "one identity -> one session");
  assert.equal(bReady.attachedDevices, 2);

  h.provider.only().emitOutbound({ type: "nodes_updated", nodes: [] });
  const [oa, ob] = await Promise.all([
    a.waitFor((f) => f.kind === "outbound"),
    b.waitFor((f) => f.kind === "outbound"),
  ]);
  assert.equal((oa.payload as BridgePayload).type, "nodes_updated");
  assert.equal((ob.payload as BridgePayload).type, "nodes_updated");
});

test("different identities get isolated sessions", async (t) => {
  const h = await start(t);
  const u1 = h.connect();
  const u2 = h.connect();
  await Promise.all([u1.opened(), u2.opened()]);

  u1.send({ kind: "hello", idToken: devToken("dev-99"), deviceId: "d1" });
  u2.send({ kind: "hello", idToken: devToken("ceo-1"), deviceId: "d2" });
  await Promise.all([
    u1.waitFor((f) => f.kind === "ready"),
    u2.waitFor((f) => f.kind === "ready"),
  ]);

  assert.equal(h.provider.sessions.size, 2);
  const sessions = [...h.provider.sessions.values()];
  sessions[0]!.emitOutbound({ type: "nodes_updated", nodes: [] });
  await new Promise((r) => setTimeout(r, 80));
  const total =
    u1.frames.filter((f) => f.kind === "outbound").length +
    u2.frames.filter((f) => f.kind === "outbound").length;
  assert.equal(total, 1, "outbound reached exactly one user");
});

// ── Heartbeat ───────────────────────────────────────────────────────────────
test("the server sends heartbeat pings", async (t) => {
  const h = await start(t);
  const c = h.connect();
  await c.opened();
  await c.waitFor((f) => f.kind === "ping", "ping");
});

test("a socket that never answers heartbeats is reaped", async (t) => {
  const h = await start(t);
  const c = h.connect(/* autoPong */ false);
  await c.opened();
  // heartbeatMs=120 -> two missed beats -> terminate at ~360ms.
  const code = await Promise.race([
    c.closed(),
    new Promise<string>((r) => setTimeout(() => r("still-open"), 1500)),
  ]);
  assert.notEqual(code, "still-open", "silent socket should have been terminated");
});

// ── Lifecycle ───────────────────────────────────────────────────────────────
test("close() stops the server accepting connections", async (t) => {
  const h = await start(t);
  await fetch(`${h.baseUrl}/healthz`); // up
  // start()'s t.after closes the server; assert the close itself is graceful
  // by closing early here and confirming the port is dead.
  const server = await buildServer({
    sessions: new FakeProvider(),
    activeContexts: () => 0,
    authorizeUpgrade: async () => true,
    logger: false,
  });
  await server.app.listen({ port: 0, host: "127.0.0.1" });
  const addr = server.app.server.address();
  if (addr === null || typeof addr === "string") throw new Error("no port");
  const url = `http://127.0.0.1:${addr.port}/healthz`;
  assert.equal((await fetch(url)).status, 200);
  await server.close();
  await assert.rejects(fetch(url), "server should refuse connections after close()");
});
