import Fastify from "fastify";
import { WebSocketServer } from "ws";
import type { IncomingMessage } from "node:http";
import { config } from "./config.js";
import { ContextManager } from "./browser/contextManager.js";
import { BridgeGateway } from "./bridge/gateway.js";
import { verifyAttestation } from "./attestation/verifier.js";

/**
 * Entry point. Fastify serves /healthz and /readyz; a raw `ws` server is
 * attached to the same HTTP server for the bridge WebSocket at /bridge.
 * `ws` (not socket.io) keeps the hot path allocation-light.
 */
async function main(): Promise<void> {
  const contexts = new ContextManager();
  await contexts.start();

  const gateway = new BridgeGateway(contexts);
  const app = Fastify({ logger: true });

  app.get("/healthz", async () => ({ ok: true }));
  app.get("/readyz", async () => ({
    ok: true,
    activeContexts: contexts.activeContexts,
  }));

  // Raw WebSocket server in noServer mode — we own the upgrade handshake so
  // only /bridge upgrades, everything else 404s.
  const wss = new WebSocketServer({ noServer: true });
  wss.on("connection", (socket) => gateway.handle(socket));

  await app.ready();
  app.server.on("upgrade", (req, socket, head) => {
    void (async () => {
      if (req.url !== "/bridge") {
        socket.destroy();
        return;
      }
      // Baseline authorization gate: the WS upgrade must carry a valid Android
      // Key Attestation proving a genuine install of our CI-signed app on a
      // real device. This runs BEFORE the id_token check — it keeps scripted
      // / desktop clients off the API entirely.
      if (!(await attestationGate(req, app.log))) {
        socket.destroy();
        return;
      }
      wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws));
    })();
  });

  await app.listen({ port: config.port, host: config.host });
  app.log.info(
    `weaver session bridge up — ws://${config.host}:${config.port}/bridge`,
  );

  const shutdown = async (signal: string): Promise<void> => {
    app.log.info(`${signal} — draining`);
    wss.close();
    await app.close();
    await contexts.stop();
    process.exit(0);
  };
  process.on("SIGTERM", () => void shutdown("SIGTERM"));
  process.on("SIGINT", () => void shutdown("SIGINT"));
}

/** True iff the upgrade request carries an acceptable Key Attestation. */
async function attestationGate(
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

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
