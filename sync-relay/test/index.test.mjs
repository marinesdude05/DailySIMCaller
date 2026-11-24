import test from "node:test";
import assert from "node:assert/strict";
import relay, { validateCall } from "../src/index.mjs";

const sample = {
  call_id: 4,
  phone_number: "4055550133",
  date: "2026-11-23",
  time: "19:03:55",
  time_zone: "America/Chicago",
  connected_seconds: 57,
  result: "Ended"
};

test("accepts complete and valid outgoing-call rows", () => {
  assert.equal(validateCall(sample), true);
  assert.equal(validateCall({ ...sample, result: "Not connected", connected_seconds: 0 }), true);
});

test("rejects unfinished, malformed, or potentially unsafe records", () => {
  assert.equal(validateCall({ ...sample, result: "Dialing" }), false);
  assert.equal(validateCall({ ...sample, call_id: -1 }), false);
  assert.equal(validateCall({ ...sample, connected_seconds: -5 }), false);
  assert.equal(validateCall({ ...sample, phone_number: "=IMPORTDATA(1)" }), false);
  assert.equal(validateCall({ ...sample, time_zone: "../../../abc" }), false);
});

test("rejects calls missing device authentication", async () => {
  const request = new Request("https://example.workers.dev/v1/calls", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ device_id: "C9-ABC12345", calls: [sample] }),
  });
  const env = { DB: {}, SHEET_ID: "test" };
  const response = await relay.fetch(request, env);
  assert.equal(response.status, 401);
});

test("health check reports process state, not falsely claiming sync", async () => {
  const response = await relay.fetch(new Request("https://example.workers.dev/health"), {});
  assert.equal(response.status, 200);
  const body = await response.json();
  assert.equal(body.status, "running");
  assert.equal(body.synced, undefined);
});

test("requires configured service before handling uploads", async () => {
  const response = await relay.fetch(new Request("https://example.workers.dev/v1/enroll", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: "{}",
  }), {});
  assert.equal(response.status, 503);
});
