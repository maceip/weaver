import type { BrowserContext, Page } from "playwright";
import { randomUUID } from "node:crypto";
import { config } from "../config.js";
import { BRIDGE_SHIM, CONTENT_SCRIPT, FETCH_INTERCEPTOR } from "./injected.js";
import type { BridgePayload } from "../bridge/protocol.js";

/**
 * One authenticated Stitch page, isolated to a single identity's
 * BrowserContext. Multiple devices (a user's phones) attach to the same
 * StitchSession; outbound events fan out to all of them.
 */
export class StitchSession {
  readonly sessionId = randomUUID();
  private page: Page | undefined;
  private readonly outboundListeners = new Set<(p: BridgePayload) => void>();
  /** Device sockets currently attached. Identity is shared; canvas is shared. */
  private readonly devices = new Set<string>();
  private lastActivity = Date.now();

  constructor(
    readonly identity: string,
    private readonly context: BrowserContext,
  ) {}

  get deviceCount(): number {
    return this.devices.size;
  }

  get idleMs(): number {
    return this.devices.size > 0 ? 0 : Date.now() - this.lastActivity;
  }

  /** Boot the page: expose the native-bridge binding, inject scripts, navigate. */
  async start(): Promise<void> {
    if (this.page) return;
    const page = await this.context.newPage();
    this.page = page;

    // The Playwright twin of Android's @JavascriptInterface. The injected
    // BRIDGE_SHIM points window.Android.post at this binding, so the content
    // script runs unmodified.
    await page.exposeBinding("__weaverEmit", (_source, json: string) => {
      this.lastActivity = Date.now();
      let payload: BridgePayload | null = null;
      try {
        payload = JSON.parse(json) as BridgePayload;
      } catch {
        return;
      }
      if (payload && typeof payload.type === "string") {
        for (const fn of this.outboundListeners) fn(payload);
      }
    });

    // addInitScript runs on every navigation, before page scripts — the
    // server-side equivalent of WebViewCompat.addDocumentStartJavaScript.
    await page.addInitScript(BRIDGE_SHIM);
    await page.addInitScript(FETCH_INTERCEPTOR);
    await page.addInitScript(CONTENT_SCRIPT);

    await page.goto(config.stitchUrl, { waitUntil: "domcontentloaded" });
  }

  /** Apply an `Inbound` bridge message to the remote page. */
  async sendInbound(payload: BridgePayload): Promise<void> {
    this.lastActivity = Date.now();
    const page = this.page;
    if (!page) return;
    const json = JSON.stringify(payload);
    // Runs in the page context. `globalThis` is in the ES2022 lib, so this
    // typechecks without pulling the whole DOM lib into the Node build.
    await page.evaluate((j) => {
      const g = globalThis as { __weaverBridge?: { receive(s: string): void } };
      g.__weaverBridge?.receive(j);
    }, json);
  }

  /** Navigate the remote page (e.g. into a specific Stitch project). */
  async navigate(url: string): Promise<void> {
    this.lastActivity = Date.now();
    await this.page?.goto(url, { waitUntil: "domcontentloaded" });
  }

  onOutbound(fn: (p: BridgePayload) => void): () => void {
    this.outboundListeners.add(fn);
    return () => this.outboundListeners.delete(fn);
  }

  attachDevice(deviceId: string): void {
    this.devices.add(deviceId);
    this.lastActivity = Date.now();
  }

  detachDevice(deviceId: string): void {
    this.devices.delete(deviceId);
    this.lastActivity = Date.now();
  }

  async close(): Promise<void> {
    this.outboundListeners.clear();
    this.devices.clear();
    await this.page?.close().catch(() => {});
    await this.context.close().catch(() => {});
    this.page = undefined;
  }
}
