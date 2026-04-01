-- Waybucks — MySQL Schema

CREATE TABLE IF NOT EXISTS wb_balances (
    uuid        VARCHAR(36)  NOT NULL PRIMARY KEY,
    username    VARCHAR(16)  NOT NULL,
    balance     BIGINT       NOT NULL DEFAULT 0,
    updated_at  BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wb_transactions (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(32)  NOT NULL,
    player_uuid VARCHAR(36)  NOT NULL,
    target_uuid VARCHAR(36),
    amount      BIGINT       NOT NULL DEFAULT 0,
    note        TEXT,
    created_at  BIGINT       NOT NULL DEFAULT 0,
    INDEX idx_wb_tx_player (player_uuid, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wb_shops (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    name            VARCHAR(64)  NOT NULL,
    description     TEXT         NOT NULL,
    price           BIGINT       NOT NULL DEFAULT 0,
    commands        TEXT         NOT NULL,
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,
    stock           INT          NOT NULL DEFAULT -1,
    max_per_player  INT          NOT NULL DEFAULT -1,
    icon_material   VARCHAR(64)  NOT NULL DEFAULT 'GOLD_INGOT',
    created_by      VARCHAR(36)  NOT NULL,
    created_at      BIGINT       NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wb_shop_purchases (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shop_id     VARCHAR(36)  NOT NULL,
    player_uuid VARCHAR(36)  NOT NULL,
    bought_at   BIGINT       NOT NULL DEFAULT 0,
    INDEX idx_wb_purchases (shop_id, player_uuid),
    FOREIGN KEY (shop_id) REFERENCES wb_shops(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wb_daily_rewards (
    player_uuid     VARCHAR(36) NOT NULL PRIMARY KEY,
    last_claimed_at BIGINT      NOT NULL DEFAULT 0,
    streak          INT         NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS wb_api_keys (
    key_hash    VARCHAR(64)  NOT NULL PRIMARY KEY,
    label       VARCHAR(64)  NOT NULL UNIQUE,
    created_at  BIGINT       NOT NULL DEFAULT 0,
    last_used   BIGINT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
