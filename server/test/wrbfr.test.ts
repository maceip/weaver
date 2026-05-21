import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import {
  stripXssi,
  splitFrames,
  decodeWrbFrame,
  isTrailerFrame,
  extractStages,
  extractThemeTokens,
} from "../src/bridge/wrbfr.js";

// The recorded StreamCreateSession response lives in the Android test fixtures.
const HAR_PATH = fileURLToPath(
  new URL(
    "../../app/src/test/resources/stitch-fixtures/network-trace.har",
    import.meta.url,
  ),
);

function streamCreateSessionBody(): string {
  const har = JSON.parse(readFileSync(HAR_PATH, "utf8")) as {
    log: { entries: { request: { url: string }; response: { content: { text?: string } } }[] };
  };
  const entry = har.log.entries.find((e) => e.request.url.includes("StreamCreateSession"));
  assert.ok(entry, "fixture must contain a StreamCreateSession call");
  return entry.response.content.text ?? "";
}

test("stripXssi removes the )]}' guard line", () => {
  assert.equal(stripXssi(")]}'\n42\n[1]"), "42\n[1]");
  assert.equal(stripXssi("no-guard"), "no-guard");
});

test("splitFrames reads length-prefixed frames", () => {
  // size counts the separator \n + the content chars, so "ab" is length 3.
  const { frames, rest } = splitFrames("3\nab6\nhello5\nhi")
  assert.deepEqual(frames, ["ab", "hello"]);
  assert.equal(rest, "5\nhi"); // partial trailing frame retained
});

test("real StreamCreateSession HAR splits into many frames + a trailer", () => {
  const body = stripXssi(streamCreateSessionBody());
  const { frames } = splitFrames(body);
  assert.ok(frames.length > 20, `expected >20 frames, got ${frames.length}`);
  assert.ok(frames.some(isTrailerFrame), "stream must end with an [\"e\", ...] trailer");
});

test("first real frame decodes to a session path", () => {
  const body = stripXssi(streamCreateSessionBody());
  const { frames } = splitFrames(body);
  const first = frames.map(decodeWrbFrame).find((f) => f !== null);
  assert.ok(first, "at least one frame must be a wrb.fr row");
  const inner = first.inner as unknown[];
  assert.equal(typeof inner[0], "string");
  assert.match(inner[0] as string, /^projects\/\d+\/sessions\/\d+$/);
});

test("real stream surfaces generation stages", () => {
  const body = stripXssi(streamCreateSessionBody());
  const { frames } = splitFrames(body);
  const stages = frames
    .map(decodeWrbFrame)
    .filter((f): f is NonNullable<typeof f> => f !== null)
    .flatMap((f) => extractStages(f.inner));
  assert.ok(stages.length > 0, "expected at least one generation stage");
  for (const s of stages) {
    assert.equal(typeof s.id, "string");
    assert.equal(typeof s.label, "string");
  }
});

test("extractStages finds a nested [id,key,label,status] tuple", () => {
  const nested = [null, null, [[[["nplekj15", "predict_shared_components", "Mapping out the components", 1]]]]];
  const stages = extractStages(nested);
  assert.equal(stages.length, 1);
  assert.deepEqual(stages[0], {
    id: "nplekj15",
    key: "predict_shared_components",
    label: "Mapping out the components",
    status: 1,
  });
});

test("extractThemeTokens picks up [name,#hex] pairs and ignores noise", () => {
  const tree = [
    "projects/123",
    [["primary", "#d0bcff"], ["surface", "#2a2a2c"], ["count", "25"]],
  ];
  assert.deepEqual(extractThemeTokens(tree), {
    primary: "#d0bcff",
    surface: "#2a2a2c",
  });
});
