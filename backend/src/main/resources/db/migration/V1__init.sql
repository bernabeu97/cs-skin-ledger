-- CS 饰品买卖统计系统 初始表结构（MySQL 8，utf8mb4）
CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE items (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    market_hash_name VARCHAR(255) NOT NULL,
    category         VARCHAR(64)  NULL,
    exterior         VARCHAR(16)  NULL,
    stat_trak        TINYINT(1)   NULL,
    icon_url         VARCHAR(512) NULL,
    source           VARCHAR(32)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_items_market_hash_name (market_hash_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE platform_item_links (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    item_id          BIGINT       NOT NULL,
    platform         VARCHAR(16)  NOT NULL,
    platform_item_id VARCHAR(128) NOT NULL,
    extra            JSON         NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pil_platform_item (platform, platform_item_id),
    KEY idx_pil_item (item_id),
    CONSTRAINT fk_pil_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE trades (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    user_id           BIGINT        NOT NULL,
    item_id           BIGINT        NOT NULL,
    platform          VARCHAR(16)   NOT NULL,
    direction         VARCHAR(8)    NOT NULL,
    quantity          DECIMAL(18,4) NOT NULL,
    unit_price        DECIMAL(18,4) NOT NULL,
    total_amount      DECIMAL(18,4) NOT NULL,
    fee               DECIMAL(18,4) NOT NULL DEFAULT 0,
    fee_rate          DECIMAL(10,6) NULL,
    currency          VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    traded_at         DATETIME(6)   NOT NULL,
    external_trade_id VARCHAR(128)  NULL,
    status            VARCHAR(16)   NOT NULL DEFAULT 'COMPLETED',
    note              VARCHAR(500)  NULL,
    created_at        DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_trades_external (platform, external_trade_id),
    KEY idx_trades_user_time (user_id, traded_at),
    KEY idx_trades_item (item_id),
    CONSTRAINT fk_trades_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_trades_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE price_snapshots (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    item_id    BIGINT        NOT NULL,
    platform   VARCHAR(16)   NOT NULL,
    price      DECIMAL(18,4) NOT NULL,
    buy_price  DECIMAL(18,4) NULL,
    sell_price DECIMAL(18,4) NULL,
    volume     INT           NULL,
    currency   VARCHAR(8)    NOT NULL DEFAULT 'CNY',
    fetched_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ps_item_platform_time (item_id, platform, fetched_at),
    CONSTRAINT fk_ps_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE alerts (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    user_id      BIGINT        NOT NULL,
    item_id      BIGINT        NOT NULL,
    platform     VARCHAR(16)   NOT NULL,
    `condition`  VARCHAR(8)    NOT NULL,
    threshold    DECIMAL(18,4) NOT NULL,
    enabled      TINYINT(1)    NOT NULL DEFAULT 1,
    triggered_at DATETIME(6)   NULL,
    PRIMARY KEY (id),
    KEY idx_alerts_user (user_id),
    CONSTRAINT fk_alerts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_alerts_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sync_logs (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    platform      VARCHAR(16)   NOT NULL,
    kind          VARCHAR(16)   NOT NULL,
    started_at    DATETIME(6)   NOT NULL,
    finished_at   DATETIME(6)   NULL,
    status        VARCHAR(16)   NOT NULL,
    records_added INT           NOT NULL DEFAULT 0,
    message       VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    KEY idx_sync_logs_user (user_id),
    CONSTRAINT fk_sync_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settings (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    user_id BIGINT      NOT NULL,
    `key`   VARCHAR(64) NOT NULL,
    `value` JSON        NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settings_user_key (user_id, `key`),
    CONSTRAINT fk_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO users (id, username) VALUES (1, 'local');