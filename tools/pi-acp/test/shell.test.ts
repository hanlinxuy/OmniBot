import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { setTimeout } from "node:timers/promises";
import { phoneBashTool } from "../src/shell.js";

test("an omitted bash timeout terminates the SDK process group", async (t) => {
  const root = mkdtempSync(join(tmpdir(), "pi-shell-deadline-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const tool = phoneBashTool(root, 0.08);
  await assert.rejects(tool.execute("omitted", {
    command: "(sleep 0.4; touch late-write) & wait",
  }, undefined, undefined, undefined as never), /timed out after 0.08 seconds/);
  await setTimeout(500);
  assert.equal(existsSync(join(root, "late-write")), false);
});

test("an explicit longer bash timeout remains supported", async () => {
  const tool = phoneBashTool(tmpdir(), 0.08);
  const result = await tool.execute("longer", {
    command: "sleep 0.2; printf 'finished'", timeout: 2,
  }, undefined, undefined, undefined as never);
  assert.ok(result.content.some((item) => item.type === "text" && item.text === "finished"));
});

test("bash cancellation still uses Pi's abort signal", async () => {
  const tool = phoneBashTool(tmpdir());
  const abort = new AbortController();
  const pending = tool.execute("cancel", { command: "sleep 5" }, abort.signal, undefined, undefined as never);
  abort.abort();
  await assert.rejects(pending, /Command aborted/);
});
