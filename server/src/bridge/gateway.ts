import type { WebSocket } from "ws";
import { config } from "../config.js";
import { AuthError, verifyIdToken } from "../auth/verifier.js";
import type { BridgeSession, SessionProvider } from "./sessionTypes.js";
import {
  parseClientFrame,
  type BridgePayload,
  type ServerFrame,
} from "./protocol.js";

/**
 * WebSocket gateway. One WebSocket = one device. The first frame must be a
 * `hello` carrying a Google id_token; the gateway verifies it, resolves the
 * identity, binds the socket to that identity's StitchSession, and from then
 * on shuttles bridge payloads both ways.
 *
 * Multiplexing: several sockets can share one StitchSession. Inbound from any
 * device is applied to the shared page; outbound is broadcast to every socket
 * bound to that session.
 */
export class BridgeGateway {
  /** identity -> sockets currently bound to that session. */
  private readonly byIdentity = new Map<string, Set<WebSocket>>();
  /** socket -> teardown for its outbound subscription. */
  private readonly unsubscribe = new WeakMap<WebSocket, () => void>();

  constructor(private readonly contexts: SessionProvider) {}

  /** Wire up a freshly-accepted WebSocket. */
  handle(socket: WebSocket): void {
    let bound: { identity: string; session: BridgeSession; deviceId: string } | null = null;
    let alive = true;

    const send = (frame: ServerFrame): void => {
      if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(frame));
    };
    const fail = (code: string, message: string, fatal: boolean): void => {
      send({ kind: "error", code, message, fatal });
      if (fatal) socket.close(1008, code);
    };

    // Heartbeat — a socket that misses two beats is reaped.
    let missedBeats = 0;
    const heartbeat = setInterval(() => {
      if (missedBeats >= 2) {
        socket.terminate();
        return;
      }
      missedBeats += 1;
      send({ kind: "ping" });
    }, config.heartbeatMs);

    socket.on("message", (raw) => {
      const frame = parseClientFrame(raw.toString());
      if (!frame) {
        fail("bad_frame", "unparseable frame", false);
        return;
      }
      missedBeats = 0;

      switch (frame.kind) {
        case "pong":
        case "ping":
          if (frame.kind === "ping") send({ kind: "pong" });
          return;

        case "hello": {
          if (bound) {
            fail("already_bound", "hello received twice", false);
            return;
          }
          void this.bind(frame.idToken, frame.deviceId, socket, send, fail).then((b) => {
            if (b && alive) bound = b;
            else if (b && !alive) this.unbindSocket(b.identity, socket); // raced a close
          });
          return;
        }

        case "inbound": {
          if (!bound) {
            fail("not_bound", "inbound before hello", false);
            return;
          }
          void bound.session
            .sendInbound(frame.payload)
            .catch((e) => fail("inbound_failed", String(e), false));
          return;
        }
      }
    });

    socket.on("close", () => {
      alive = false;
      clearInterval(heartbeat);
      this.unsubscribe.get(socket)?.();
      if (bound) {
        bound.session.detachDevice(bound.deviceId);
        this.unbindSocket(bound.identity, socket);
      }
    });

    socket.on("error", () => socket.close());
  }

  /** Verify the token, resolve the session, subscribe the socket to outbound. */
  private async bind(
    idToken: string,
    deviceId: string,
    socket: WebSocket,
    send: (f: ServerFrame) => void,
    fail: (code: string, message: string, fatal: boolean) => void,
  ): Promise<{ identity: string; session: BridgeSession; deviceId: string } | null> {
    let identity: string;
    try {
      ({ identity } = await verifyIdToken(idToken));
    } catch (e) {
      const code = e instanceof AuthError ? e.code : "auth_failed";
      fail(code, e instanceof Error ? e.message : "auth failed", true);
      return null;
    }

    let session: BridgeSession;
    try {
      session = await this.contexts.sessionFor(identity);
    } catch (e) {
      fail("session_unavailable", String(e), false);
      return null;
    }

    session.attachDevice(deviceId);
    let set = this.byIdentity.get(identity);
    if (!set) {
      set = new Set();
      this.byIdentity.set(identity, set);
    }
    set.add(socket);

    // Fan this session's outbound events to this socket.
    const off = session.onOutbound((payload: BridgePayload) => {
      if (socket.readyState === socket.OPEN) {
        socket.send(JSON.stringify({ kind: "outbound", payload }));
      }
    });
    this.unsubscribe.set(socket, off);

    send({
      kind: "ready",
      identity,
      sessionId: session.sessionId,
      attachedDevices: session.deviceCount,
    });
    return { identity, session, deviceId };
  }

  private unbindSocket(identity: string, socket: WebSocket): void {
    const set = this.byIdentity.get(identity);
    if (!set) return;
    set.delete(socket);
    if (set.size === 0) this.byIdentity.delete(identity);
  }
}
