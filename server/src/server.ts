import Fastify, { type FastifyInstance } from "fastify";
import { WebSocketServer } from "ws";
import type { IncomingMessage } from "node:http";
import { config } from "./config.js";
import { BridgeGateway } from "./bridge/gateway.js";
import { verifyAttestation } from "./attestation/verifier.js";
import type { SessionProvider } from "./bridge/sessionTypes.js";

/**
 * HTTP + WebSocket server assembly, factored out of the entrypoint so it can
 * be exercised end-to-end in tests with a fake [SessionProvider] (no Chromium).
 */

export interface ServerDeps {
  /** Resolves the StitchSession for a verified identity. */
  sessions: SessionProvider;
  /** Live count of BrowserContexts, surfaced on /readyz. */
  activeContexts: () => number;
  /**
   * Authorizes a WebSocket upgrade. Defaults to the Key Attestation gate;
   * tests inject their own to exercise both accept and reject paths.
   */
  authorizeUpgrade?: (
    req: IncomingMessage,
    log: { warn: (m: string) => void },
  ) => Promise<boolean>;
  /** Fastify request logging — left on in prod, off in tests. */
  logger?: boolean;
}

export interface WeaverServer {
  app: FastifyInstance;
  gateway: BridgeGateway;
  /** Stop accepting sockets and close the HTTP server. */
  close(): Promise<void>;
}

/**
 * Build the bridge server. Fastify serves /healthz and /readyz; a raw `ws`
 * server is attached to the same HTTP server for the bridge WebSocket at
 * /bridge. The caller owns `listen()`.
 */
export async function buildServer(deps: ServerDeps): Promise<WeaverServer> {
  const gateway = new BridgeGateway(deps.sessions);
  const app = Fastify({ logger: deps.logger ?? true });
  const authorize = deps.authorizeUpgrade ?? attestationGate;

  app.get("/healthz", async () => ({ ok: true }));
  app.get("/readyz", async () => ({ ok: true, activeContexts: deps.activeContexts() }));

  // Raw WebSocket server in noServer mode — we own the upgrade handshake so
  // only /bridge upgrades, everything else is dropped.
  const wss = new WebSocketServer({ noServer: true });
  wss.on("connection", (socket) => gateway.handle(socket));

  await app.ready();
  app.server.on("upgrade", (req, socket, head) => {
    void (async () => {
      if (req.url !== "/bridge") {
        socket.destroy();
        return;
      }
      if (!(await authorize(req, app.log))) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws));
    })();
  });

  return {
    app,
    gateway,
    close: async () => {
      wss.close();
      await app.close();
    },
  };
}

/**
 * Baseline authorization gate: the WS upgrade must carry a valid Android Key
 * Attestation proving a genuine install of our CI-signed app on a real device.
 * Runs BEFORE the id_token check — it keeps scripted / desktop clients off the
 * API entirely.
 */
export async function attestationGate(
  req: IncomingMessage,
  log: { warn: (m: string) => void },
): Promise<boolean> {
  if (config.devSkipAttestation) return true;

  const raw = req.headers["x-weaver-attestation"];
  const chain = Array.isArray(raw) ? raw[0] : raw;
  if (!chain) {
    log.warn("WS upgrade rejected: no X-Weaver-Attestation header");
    return false;
  }
  const result = await verifyAttestation(chain, {
    expectedPackage: config.appPackage,
    allowedSignatureDigests: config.signingDigests,
  });
  if (!result.ok) {
    log.warn(`WS upgrade rejected: attestation ${result.code} — ${result.message}`);
    return false;
  }
  return true;
}
