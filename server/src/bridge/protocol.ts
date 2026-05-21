/**
 * Wire protocol shared with the Android client.
 *
 * Two layers:
 *  - the WS *envelope* ({kind: ...}) handled by the gateway, and
 *  - the bridge *payload* — the exact `Outbound`/`Inbound` JSON the on-device
 *    Bridge already speaks. We do not re-model the payload here; it is passed
 *    through opaquely. `type` is the discriminator (matches the Kotlin
 *    `@SerialName` + classDiscriminator = "type" setup).
 */

/** A bridge payload — opaque pass-through; `type` is the only field we read. */
export interface BridgePayload {
  type: string;
  [k: string]: unknown;
}

// ── Client -> server envelopes ──────────────────────────────────────────────

export interface HelloFrame {
  kind: "hello";
  /** Google id_token from Credential Manager. */
  idToken: string;
  /** Stable per-device id so the gateway can dedupe reconnects. */
  deviceId: string;
  clientInfo?: Record<string, unknown>;
}

export interface InboundFrame {
  kind: "inbound";
  /** An `Inbound` bridge message (submit_prompt, select_node, ...). */
  payload: BridgePayload;
}

export interface PingFrame {
  kind: "ping";
}

export type ClientFrame = HelloFrame | InboundFrame | PingFrame | { kind: "pong" };

// ── Server -> client envelopes ──────────────────────────────────────────────

export interface ReadyFrame {
  kind: "ready";
  /** Opaque identity (hashed Google sub) this socket was bound to. */
  identity: string;
  /** Server-side session id (one per BrowserContext). */
  sessionId: string;
  /** How many devices are currently attached to this session (incl. this one). */
  attachedDevices: number;
}

export interface OutboundFrame {
  kind: "outbound";
  /** An `Outbound` bridge message (nodes_updated, session_progress, ...). */
  payload: BridgePayload;
}

export interface ErrorFrame {
  kind: "error";
  code: string;
  message: string;
  /** When true the client should not retry with the same token. */
  fatal: boolean;
}

export type ServerFrame =
  | ReadyFrame
  | OutboundFrame
  | ErrorFrame
  | { kind: "ping" }
  | { kind: "pong" };

export function parseClientFrame(raw: string): ClientFrame | null {
  try {
    const obj = JSON.parse(raw) as { kind?: unknown };
    if (typeof obj.kind !== "string") return null;
    return obj as ClientFrame;
  } catch {
    return null;
  }
}
