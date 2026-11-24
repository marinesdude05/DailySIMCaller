-- D1 is the durable source of truth; Sheets is a retriable mirror.
CREATE TABLE IF NOT EXISTS devices (
  device_id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL,
  next_sheet_row INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS calls (
  device_id TEXT NOT NULL,
  call_id INTEGER NOT NULL,
  phone_number TEXT NOT NULL,
  date TEXT NOT NULL,
  time TEXT NOT NULL,
  time_zone TEXT NOT NULL,
  connected_seconds INTEGER NOT NULL,
  result TEXT NOT NULL,
  sheet_row INTEGER,
  mirrored INTEGER NOT NULL DEFAULT 0,
  received_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (device_id, call_id),
  FOREIGN KEY (device_id) REFERENCES devices(device_id)
);

CREATE INDEX IF NOT EXISTS idx_calls_pending ON calls(device_id, mirrored, call_id);
