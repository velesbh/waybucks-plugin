-- Waybucks — SQLite Schema
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

-- Shares bws_players table with blockway-social via same DB file (optional)
-- Waybucks creates its own tables prefixed wb_

CREATE TABLE IF NOT EXISTS wb_balances (
    uuid        TEXT    NOT NULL PRIMARY KEY,
    username    TEXT    NOT NULL,
    balance     INTEGER NOT NULL DEFAULT 0,
    updated_at  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wb_transactions (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    type        TEXT    NOT NULL,
    player_uuid TEXT    NOT NULL,
    target_uuid TEXT,
    amount      INTEGER NOT NULL DEFAULT 0,
    note        TEXT,
    created_at  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_wb_tx_player ON wb_transactions (player_uuid, created_at);

CREATE TABLE IF NOT EXISTS wb_shops (
    id              TEXT    NOT NULL PRIMARY KEY,
    name            TEXT    NOT NULL,
    description     TEXT    NOT NULL DEFAULT '',
    price           INTEGER NOT NULL DEFAULT 0,
    commands        TEXT    NOT NULL DEFAULT '[]',
    enabled         INTEGER NOT NULL DEFAULT 1,
    stock           INTEGER NOT NULL DEFAULT -1,
    max_per_player  INTEGER NOT NULL DEFAULT -1,
    icon_material   TEXT    NOT NULL DEFAULT 'GOLD_INGOT',
    created_by      TEXT    NOT NULL,
    created_at      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wb_shop_purchases (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    shop_id     TEXT    NOT NULL,
    player_uuid TEXT    NOT NULL,
    bought_at   INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (shop_id) REFERENCES wb_shops(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_wb_purchases ON wb_shop_purchases (shop_id, player_uuid);

CREATE TABLE IF NOT EXISTS wb_daily_rewards (
    player_uuid     TEXT    NOT NULL PRIMARY KEY,
    last_claimed_at INTEGER NOT NULL DEFAULT 0,
    streak          INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS wb_api_keys (
    key_hash    TEXT    NOT NULL PRIMARY KEY,
    label       TEXT    NOT NULL UNIQUE,
    created_at  INTEGER NOT NULL DEFAULT 0,
    last_used   INTEGER
);
