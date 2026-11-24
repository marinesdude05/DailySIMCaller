import { bindings, defineConfig } from "cf/config";

// Cloudflare CLI project configuration for the v45 call-log upload service.
// No tokens or Google service-account credentials belong in this file.
export default defineConfig({
  worker: {
    name: "daily-sim-caller-sync",
    entrypoint: "./src/index.mjs",
    compatibilityDate: "2026-11-24",
    workersDev: true,
    observability: {
      enabled: true,
      logs: { enabled: true },
      traces: { enabled: true },
      redactQueryString: true,
    },
    env: {
      DB: bindings.d1({ name: "daily-sim-caller-logs" }),
      SHEET_ID: bindings.text("1m93ppcPSgreho3RNRDI1QKUm5fecUazWcdDt8PRglNg"),
      ENROLLMENT_SECRET: bindings.secret(),
      GOOGLE_SERVICE_ACCOUNT_JSON: bindings.secret(),
    },
  },
});
