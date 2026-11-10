import { test } from "node:test";
import assert from "node:assert/strict";
import { writeFileSync } from "node:fs";
import { join } from "node:path";
import { harness, httpFixture, stream } from "./fixtures.js";

test("32K model compacts a single tool-heavy turn after a clamped reply", { timeout: 20000 }, async () => {
  let requests = 0;
  const provider = await httpFixture((_body, response) => {
    requests++;
    if (requests <= 4) {
      response.writeHead(200, { "Content-Type": "text/event-stream" });
      const delta = requests <= 3 ? { role: "assistant", tool_calls: [{ index: 0, id: `read-${requests}`, type: "function", function: { name: "read", arguments: JSON.stringify({ path: "evidence.txt" }) } }] } : { role: "assistant", content: "I" };
      response.write(`data: ${JSON.stringify({ choices: [{ index: 0, delta, finish_reason: requests <= 3 ? "tool_calls" : "length" }] })}\n\n`);
      response.write(`data: ${JSON.stringify({ choices: [], usage: { prompt_tokens: requests <= 3 ? requests * 6000 : 30000, completion_tokens: requests <= 3 ? 30 : 1 } })}\n\n`);
      response.end("data: [DONE]\n\n");
    } else {
      assert.ok(requests <= 6, "native recovery must remain bounded");
      stream(response, { text: requests === 5 ? "Evidence read three times; finish without repeating reads." : "Recovered within the original ACP prompt" });
    }
  });
  const host = await harness(provider.url, { configure(config) {
    config.provider.models![0].contextWindow = 32768;
    config.provider.models![0].maxTokens = 4096;
  } });
  writeFileSync(join(host.root, "evidence.txt"), "Owned evidence line for the current task.\n".repeat(600));
  try {
    const session = await host.newSession();
    const result = await host.prompt(session.sessionId, "Read the evidence and finish the task.");
    assert.equal(result.stopReason, "end_turn");
    assert.equal(requests, 6);
    assert.ok(host.updates.some(({ update }) => update.sessionUpdate === "agent_message_chunk" && update.content.type === "text" && update.content.text.includes("Recovered within")));
  } finally { await host.close(); await provider.close(); }
});
