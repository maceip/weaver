import { config } from "./config.js";
import { ContextManager } from "./browser/contextManager.js";
import { buildServer } from "./server.js";

/**
 * Entry point. Launches Chromium, builds the bridge server, listens, and
 * wires graceful shutdown. The server assembly itself lives in server.ts so
 * it can be tested without a real browser.
 */
async function main(): Promise<void> {
  const contexts = new ContextManager();
  await contexts.start();

  const server = await buildServer({
    sessions: contexts,
    activeContexts: () => contexts.activeContexts,
  });

  await server.app.listen({ port: config.port, host: config.host });
  server.app.log.info(
    `weaver session bridge up — ws://${config.host}:${config.port}/bridge`,
  );

  const shutdown = async (signal: string): Promise<void> => {
    server.app.log.info(`${signal} — draining`);
    await server.close();
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
