import type { BridgePayload } from "./protocol.js";

/**
 * The slice of a per-identity Stitch session the [BridgeGateway] depends on.
 * Extracting it as an interface lets the gateway be tested with a fake — the
 * concrete StitchSession (which owns a Chromium BrowserContext) structurally
 * satisfies this.
 */
export interface BridgeSession {
  readonly sessionId: string;
  /** Devices currently attached to this session (a user's phones). */
  readonly deviceCount: number;
  attachDevice(deviceId: string): void;
  detachDevice(deviceId: string): void;
  /** Subscribe to outbound bridge events; returns an unsubscribe fn. */
  onOutbound(fn: (payload: BridgePayload) => void): () => void;
  /** Apply an inbound bridge payload to the session. */
  sendInbound(payload: BridgePayload): Promise<void>;
}

/**
 * Resolves (creating on first use) the [BridgeSession] for a verified
 * identity. ContextManager is the production implementation; the shard key
 * is the Google `sub`, so two devices on one account resolve to one session.
 */
export interface SessionProvider {
  sessionFor(identity: string): Promise<BridgeSession>;
}
