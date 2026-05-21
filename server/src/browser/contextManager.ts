import { chromium, type Browser, type Cookie } from "playwright";
import { config } from "../config.js";
import { StitchSession } from "./stitchSession.js";

/**
 * Owns the single Chromium process and the per-identity BrowserContext pool.
 *
 * One Chromium, N isolated BrowserContexts — one per Google identity. A
 * context has its own cookie jar and storage, so Stitch in context A can
 * never see context B's projects. A user's two phones resolve to the same
 * identity and therefore share one context (and one StitchSession).
 */
export class ContextManager {
  private browser: Browser | undefined;
  private readonly sessions = new Map<string, StitchSession>();
  /** Per-identity Stitch cookies to inject before first navigation. */
  private readonly cookieJar = new Map<string, Cookie[]>();
  private evictionTimer: NodeJS.Timeout | undefined;

  async start(): Promise<void> {
    this.browser = await chromium.launch({
      headless: !config.headed,
      args: [
        "--disable-dev-shm-usage", // /dev/shm is tiny in containers; use /tmp
        "--no-sandbox", // required in most container runtimes
        "--disable-gpu",
      ],
    });
    this.evictionTimer = setInterval(() => void this.evictIdle(), 60_000);
  }

  /**
   * Per-identity Stitch session cookies. Calling this before [sessionFor]
   * means the context boots already authenticated. HOW these cookies are
   * obtained is the deferred auth workstream — this is just the plumbing.
   */
  hydrateContext(identity: string, cookies: Cookie[]): void {
    this.cookieJar.set(identity, cookies);
  }

  /** Get the live session for an identity, creating its context on first use. */
  async sessionFor(identity: string): Promise<StitchSession> {
    const existing = this.sessions.get(identity);
    if (existing) return existing;

    if (!this.browser) throw new Error("ContextManager not started");
    if (this.sessions.size >= config.maxContexts) {
      await this.evictIdle(true);
      if (this.sessions.size >= config.maxContexts) {
        throw new Error("context pool exhausted");
      }
    }

    const context = await this.browser.newContext({
      viewport: { width: 1280, height: 884 },
      userAgent:
        "Mozilla/5.0 (Linux; Android 15; Pixel 10 Pro Fold) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/150.0.0.0 Mobile Safari/537.36 Weaver/0.1",
    });

    const cookies = this.cookieJar.get(identity);
    if (cookies?.length) await context.addCookies(cookies);

    const session = new StitchSession(identity, context);
    this.sessions.set(identity, session);
    await session.start();
    return session;
  }

  /** Close sessions with no attached devices past the idle deadline. */
  private async evictIdle(force = false): Promise<void> {
    for (const [identity, session] of this.sessions) {
      const stale = session.idleMs > config.contextIdleMs;
      if (stale || (force && session.deviceCount === 0)) {
        this.sessions.delete(identity);
        await session.close();
      }
    }
  }

  async stop(): Promise<void> {
    if (this.evictionTimer) clearInterval(this.evictionTimer);
    for (const session of this.sessions.values()) await session.close();
    this.sessions.clear();
    await this.browser?.close().catch(() => {});
    this.browser = undefined;
  }

  get activeContexts(): number {
    return this.sessions.size;
  }
}
