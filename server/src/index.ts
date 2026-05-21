import Fastify from "fastify";
import { WebSocketServer } from "ws";
import { config } from "./config.js";
import { ContextManager } from "./browser/contextManager.js";
import { BridgeGateway } from "./bridge/gateway.js";

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
    if (req.url !== "/bridge") {
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit("connection", ws));
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

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
