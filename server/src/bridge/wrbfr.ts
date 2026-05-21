/**
 * Parsing for Google's `wrb.fr` chunked-RPC response format — the wire format
 * of Stitch's StreamCreateSession and batchexecute calls.
 *
 * This is the testable twin of the inline parser in `browser/injected.ts`.
 * injected.ts must be a self-contained browser string so it cannot import
 * this module; keep the two behaviourally in sync. Tests exercise THIS copy
 * against the recorded HAR fixtures, which catches algorithm regressions.
 *
 * Format: an XSSI guard line `)]}'` followed by repeated
 *   <decimal-byte-length>\n<json>\n
 * Each json is `[["wrb.fr", null|rpcid, "<json-string>"], ...]` or a trailer
 * like `[["e", frameCount, null, null, totalBytes]]`.
 */

/** Strip the leading `)]}'` XSSI guard line, if present. */
export function stripXssi(body: string): string {
  if (body.startsWith(")]}'")) {
    const nl = body.indexOf("\n");
    return nl >= 0 ? body.slice(nl + 1) : "";
  }
  return body;
}

/**
 * Split a (XSSI-stripped) body into raw frame strings. Returns the frames it
 * could fully read plus any trailing partial buffer (for streaming callers).
 *
 * Two things the format gets subtly wrong-looking:
 *  - The length prefix counts JS-string characters (UTF-16 code units), not
 *    UTF-8 bytes. ASCII-only frames hide this; a frame with multi-byte
 *    content desyncs a byte-based parser. Work on the string directly.
 *  - The count is measured from (and includes) the `\n` that terminates the
 *    length line. So a declared `941` covers that `\n` plus 940 chars of
 *    JSON, and the next length begins at `nl + size`, not `nl + 1 + size`.
 */
export function splitFrames(buf: string): { frames: string[]; rest: string } {
  const frames: string[] = [];
  let offset = 0;
  for (;;) {
    const nl = buf.indexOf("\n", offset);
    if (nl < 0) break;
    const head = buf.slice(offset, nl).trim();
    // Skip the blank line Google emits after the XSSI guard.
    if (head === "") {
      offset = nl + 1;
      continue;
    }
    const size = Number.parseInt(head, 10);
    if (Number.isNaN(size)) break;
    // size counts the `\n` at nl plus (size-1) chars of JSON after it.
    if (buf.length < nl + size) break;
    frames.push(buf.slice(nl + 1, nl + size));
    offset = nl + size;
  }
  return { frames, rest: buf.slice(offset) };
}

export interface SessionStage {
  id: string;
  key: string;
  label: string;
  status: number;
}

/**
 * Depth-first scan for `[id, key, label, status?]` stage tuples — Stitch nests
 * them several arrays deep. Cheap because frames are < 25KB.
 */
export function extractStages(node: unknown): SessionStage[] {
  const out: SessionStage[] = [];
  const walk = (n: unknown): void => {
    if (!Array.isArray(n)) return;
    if (
      n.length >= 3 &&
      typeof n[0] === "string" && n[0].length < 32 &&
      typeof n[1] === "string" && n[1].length < 64 &&
      typeof n[2] === "string" && n[2].length > 0 && n[2].length < 200 &&
      (n.length === 3 || typeof n[3] === "number")
    ) {
      out.push({
        id: n[0],
        key: n[1],
        label: n[2],
        status: typeof n[3] === "number" ? n[3] : 0,
      });
      return;
    }
    for (const child of n) walk(child);
  };
  walk(node);
  return out;
}

/** Depth-first scan for `[name, "#hex"]` design-token pairs. */
export function extractThemeTokens(node: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  const walk = (n: unknown): void => {
    if (!Array.isArray(n)) return;
    if (
      n.length === 2 &&
      typeof n[0] === "string" &&
      typeof n[1] === "string" &&
      /^#[0-9a-fA-F]{3,8}$/.test(n[1])
    ) {
      out[n[0]] = n[1];
      return;
    }
    for (const child of n) walk(child);
  };
  walk(node);
  return out;
}

export interface StreamFrame {
  rpcId: string | null;
  /** The decoded inner payload (the JSON-in-a-string of a wrb.fr row). */
  inner: unknown;
}

/** Decode one frame string into its `wrb.fr` row, or null if it isn't one. */
export function decodeWrbFrame(raw: string): StreamFrame | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    return null;
  }
  if (!Array.isArray(parsed) || !Array.isArray(parsed[0])) return null;
  const row = parsed[0] as unknown[];
  if (row[0] !== "wrb.fr" || typeof row[2] !== "string") return null;
  try {
    return { rpcId: typeof row[1] === "string" ? row[1] : null, inner: JSON.parse(row[2]) };
  } catch {
    return null;
  }
}

/** True when [raw] is the stream trailer `["e", frameCount, ...]`. */
export function isTrailerFrame(raw: string): boolean {
  try {
    const parsed = JSON.parse(raw) as unknown;
    return (
      Array.isArray(parsed) &&
      Array.isArray(parsed[0]) &&
      (parsed[0] as unknown[])[0] === "e"
    );
  } catch {
    return false;
  }
}
