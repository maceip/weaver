/** Environment-driven configuration. All knobs in one place. */

function int(name: string, fallback: number): number {
  const raw = process.env[name];
  if (!raw) return fallback;
  const n = Number.parseInt(raw, 10);
  return Number.isFinite(n) ? n : fallback;
}

function bool(name: string): boolean {
  return process.env[name] === "1" || process.env[name] === "true";
}

export const config = {
  /** HTTP + WebSocket listen port. */
  port: int("PORT", 8080),
  host: process.env.HOST ?? "0.0.0.0",

  /**
   * Accepted OAuth audiences for the Google id_token. The Android client's
   * Credential Manager request must use one of these as its serverClientId.
   * Comma-separated.
   */
  allowedAudiences: (process.env.WEAVER_ALLOWED_AUDIENCES ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),

  /**
   * DEV ONLY. When set, id_tokens are decoded but not cryptographically
   * verified, and an unverified `sub` is trusted. Never enable in prod.
   */
  devTrustTokens: bool("WEAVER_DEV_TRUST_TOKENS"),

  /** Idle BrowserContext eviction — closes a context with no sockets after this. */
  contextIdleMs: int("WEAVER_CONTEXT_IDLE_MS", 15 * 60_000),

  /** Hard cap on concurrent BrowserContexts per server instance. */
  maxContexts: int("WEAVER_MAX_CONTEXTS", 40),

  /** WebSocket heartbeat interval; a socket missing two beats is reaped. */
  heartbeatMs: int("WEAVER_HEARTBEAT_MS", 30_000),

  /** The Stitch entry URL the remote page is parked at until a project opens. */
  stitchUrl: process.env.WEAVER_STITCH_URL ?? "https://stitch.withgoogle.com/",

  /** Run Chromium headed (debugging). Headless in prod. */
  headed: bool("WEAVER_HEADED"),

  // ── Key-attestation gate ────────────────────────────────────────────────
  /** Required APK package name in the attestation. */
  appPackage: process.env.WEAVER_APP_PACKAGE ?? "com.weaver.app",

  /**
   * Allowlisted base64 SHA-256 APK signing-cert digests — the key minted by
   * the GitHub CI build. A WS upgrade whose attestation does not carry one of
   * these is rejected. Empty (the default) fails closed: with no allowlist,
   * nothing passes unless [devSkipAttestation] is set.
   */
  signingDigests: (process.env.WEAVER_SIGNING_DIGESTS ?? "")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean),

  /** DEV ONLY. Skips the attestation gate entirely. Never enable in prod. */
  devSkipAttestation: bool("WEAVER_DEV_SKIP_ATTESTATION"),
} as const;

export type Config = typeof config;
