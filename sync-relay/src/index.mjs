// Daily SIM Caller: authenticated device -> D1 -> Google Sheets relay.
// Never embed credentials in the APK or commit them to this repository.
const DEVICE_ID = /^C9-[A-F0-9]{8}$/;
const PHONE = /^\+?[0-9]{3,18}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;
const TIME = /^\d{2}:\d{2}:\d{2}$/;
const TIME_ZONE = /^[A-Za-z0-9_+./-]{1,64}$/;
const RESULTS = new Set(["Ended", "Not connected"]);
const HEADER = ["call_id", "device_id", "phone_number", "date", "time",
  "time_zone", "connected_seconds", "result"];
const MAX_BODY_BYTES = 128_000;

export function validateCall(row) {
  if (!row || typeof row !== "object" || Array.isArray(row)) return false;
  return Number.isSafeInteger(row.call_id) && row.call_id > 0 &&
    typeof row.phone_number === "string" && PHONE.test(row.phone_number) &&
    typeof row.date === "string" && DATE.test(row.date) &&
    typeof row.time === "string" && TIME.test(row.time) &&
    typeof row.time_zone === "string" && TIME_ZONE.test(row.time_zone) &&
    !row.time_zone.includes("..") &&
    Number.isSafeInteger(row.connected_seconds) &&
    row.connected_seconds >= 0 && row.connected_seconds <= 86_400 &&
    RESULTS.has(row.result);
}

function respond(value, status = 200) {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

async function parseJson(request) {
  if (!request.headers.get("content-type")?.includes("application/json")) {
    throw new ClientError("Expected application/json", 415);
  }
  const len = Number(request.headers.get("content-length") || "0");
  if (len > MAX_BODY_BYTES) throw new ClientError("Payload too large", 413);
  const body = await request.text();
  if (new TextEncoder().encode(body).length > MAX_BODY_BYTES) {
    throw new ClientError("Payload too large", 413);
  }
  try { return JSON.parse(body); }
  catch { throw new ClientError("Invalid JSON", 400); }
}

class ClientError extends Error {
  constructor(message, status) { super(message); this.status = status; }
}

async function sha256hex(value) {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), x => x.toString(16).padStart(2, "0")).join("");
}

async function secureEquals(a, b) {
  if (!a || !b) return false;
  const [one, two] = await Promise.all([sha256hex(a), sha256hex(b)]);
  let difference = 0;
  for (let i = 0; i < one.length; i++) difference |= one.charCodeAt(i) ^ two.charCodeAt(i);
  return difference === 0;
}

function newToken() {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

function base64url(data) {
  const binary = typeof data === "string" ? data :
    String.fromCharCode(...new Uint8Array(data));
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}

async function googleToken(env) {
  const service = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON);
  if (!service.client_email || !service.private_key) throw new Error("Google account secret incomplete");
  const keyBody = service.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, "");
  const key = await crypto.subtle.importKey(
    "pkcs8", Uint8Array.from(atob(keyBody), c => c.charCodeAt(0)),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]
  );
  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(JSON.stringify({
    iss: service.client_email,
    scope: "https://www.googleapis.com/auth/spreadsheets",
    aud: "https://oauth2.googleapis.com/token",
    iat: now, exp: now + 3600,
  }));
  const unsigned = header + "." + claims;
  const signature = base64url(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)
  ));
  const oauth = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: unsigned + "." + signature,
    }),
  });
  if (!oauth.ok) throw new Error("Google token request failed (" + oauth.status + ")");
  const json = await oauth.json();
  if (!json.access_token) throw new Error("Google token missing");
  return json.access_token;
}

async function sheets(env, token, suffix, method = "GET", body = undefined) {
  const root = "https://sheets.googleapis.com/v4/spreadsheets/" + encodeURIComponent(env.SHEET_ID);
  const response = await fetch(root + suffix, {
    method,
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) throw new Error("Google Sheets request failed (" + response.status + ")");
  return response.json();
}

async function ensureTab(env, token, deviceId) {
  const meta = await sheets(env, token, "?fields=sheets(properties(title))");
  if (!meta.sheets?.some(tab => tab.properties?.title === deviceId)) {
    try {
      await sheets(env, token, ":batchUpdate", "POST", {
        requests: [{ addSheet: { properties: { title: deviceId } } }],
      });
    } catch (error) {
      // Another upload may have created the same tab concurrently.
      const recheck = await sheets(env, token, "?fields=sheets(properties(title))");
      if (!recheck.sheets?.some(tab => tab.properties?.title === deviceId)) throw error;
    }
  }
  await sheets(env, token, "/values/" + encodeURIComponent("'" + deviceId + "'!A1:H1") +
    "?valueInputOption=RAW", "PUT", { values: [HEADER] });
}

async function mirrorPending(env, deviceId) {
  const unmirrored = await env.DB.prepare(
    "SELECT * FROM calls WHERE device_id=? AND mirrored=0 ORDER BY call_id LIMIT 100"
  ).bind(deviceId).all();
  const rows = unmirrored.results || [];
  if (!rows.length) return 0;
  const accessToken = await googleToken(env);
  await ensureTab(env, accessToken, deviceId);
  const items = [];
  for (const row of rows) {
    let rowNumber = row.sheet_row;
    if (rowNumber === null || rowNumber === undefined) {
      const allocated = await env.DB.prepare(
        "UPDATE devices SET next_sheet_row=next_sheet_row+1 WHERE device_id=? RETURNING next_sheet_row"
      ).bind(deviceId).first();
      if (!allocated) throw new Error("Device missing during row allocation");
      await env.DB.prepare(
        "UPDATE calls SET sheet_row=? WHERE device_id=? AND call_id=? AND sheet_row IS NULL"
      ).bind(allocated.next_sheet_row, deviceId, row.call_id).run();
      const stored = await env.DB.prepare(
        "SELECT sheet_row FROM calls WHERE device_id=? AND call_id=?"
      ).bind(deviceId, row.call_id).first();
      rowNumber = stored.sheet_row;
    }
    items.push({
      range: "'" + deviceId + "'!A" + rowNumber + ":H" + rowNumber,
      majorDimension: "ROWS",
      values: [[row.call_id, deviceId, row.phone_number, row.date, row.time,
        row.time_zone, row.connected_seconds, row.result]],
    });
  }
  const result = await sheets(env, accessToken, "/values:batchUpdate", "POST", {
    valueInputOption: "RAW", data: items,
  });
  if (result.totalUpdatedRows < items.length) throw new Error("Sheets did not update every row");
  for (const row of rows) {
    await env.DB.prepare(
      "UPDATE calls SET mirrored=1 WHERE device_id=? AND call_id=? AND sheet_row IS NOT NULL"
    ).bind(deviceId, row.call_id).run();
  }
  return rows.length;
}

export default {
  async fetch(request, env) {
    const route = new URL(request.url).pathname;
    if (route === "/health" && request.method === "GET") {
      return respond({ service: "daily-sim-caller-sync", status: "running" });
    }
    if (route !== "/v1/enroll" && route !== "/v1/calls") {
      return respond({ error: "Not found" }, 404);
    }
    if (request.method !== "POST") return respond({ error: "Method not allowed" }, 405);
    if (!env.DB || !env.SHEET_ID) return respond({ error: "Service is not configured" }, 503);
    try {
      const data = await parseJson(request);
      if (!data || !DEVICE_ID.test(data.device_id || "")) {
        throw new ClientError("Invalid device ID", 400);
      }
      if (route === "/v1/enroll") {
        if (!env.ENROLLMENT_SECRET || env.ENROLLMENT_SECRET.length < 32) {
          return respond({ error: "Enrollment not configured" }, 503);
        }
        if (typeof data.enrollment_code !== "string" ||
            !await secureEquals(data.enrollment_code, env.ENROLLMENT_SECRET)) {
          return respond({ error: "Invalid enrollment code" }, 401);
        }
        const token = newToken();
        await env.DB.prepare(
          "INSERT INTO devices(device_id, token_hash, next_sheet_row) VALUES(?,?,1) " +
          "ON CONFLICT(device_id) DO UPDATE SET token_hash=excluded.token_hash"
        ).bind(data.device_id, await sha256hex(token)).run();
        return respond({ device_id: data.device_id, token });
      }
      const auth = request.headers.get("Authorization") || "";
      if (!auth.startsWith("Bearer ") || auth.length > 512) {
        return respond({ error: "Device authorization required" }, 401);
      }
      const known = await env.DB.prepare(
        "SELECT token_hash FROM devices WHERE device_id=?"
      ).bind(data.device_id).first();
      if (!known || !await secureEquals(await sha256hex(auth.slice(7)), known.token_hash)) {
        return respond({ error: "Device authorization failed" }, 401);
      }
      if (!Array.isArray(data.calls) || data.calls.length > 100 ||
          !data.calls.every(validateCall)) {
        throw new ClientError("Invalid call batch", 400);
      }
      for (const call of data.calls) {
        await env.DB.prepare(
          "INSERT OR IGNORE INTO calls(device_id, call_id, phone_number, date, time, " +
          "time_zone, connected_seconds, result) VALUES(?,?,?,?,?,?,?,?)"
        ).bind(data.device_id, call.call_id, call.phone_number, call.date,
          call.time, call.time_zone, call.connected_seconds, call.result).run();
      }
      const mirroredCount = await mirrorPending(env, data.device_id);
      const ids = [];
      for (const call of data.calls) {
        const stored = await env.DB.prepare(
          "SELECT mirrored FROM calls WHERE device_id=? AND call_id=?"
        ).bind(data.device_id, call.call_id).first();
        if (stored?.mirrored === 1) ids.push(call.call_id);
      }
      return respond({ synced_call_ids: ids, mirrored_count: mirroredCount });
    } catch (error) {
      if (error instanceof ClientError) return respond({ error: error.message }, error.status);
      console.error(JSON.stringify({ event: "call_log_sync_error", type: error?.name || "Error",
        message: String(error?.message || "Unknown").slice(0, 160) }));
      return respond({ error: "Upload unavailable; retry later" }, 503);
    }
  },
};
