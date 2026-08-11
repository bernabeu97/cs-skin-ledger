CREATE TABLE watchlist_entries (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    item_id     BIGINT      NOT NULL,
    exterior    VARCHAR(16) NOT NULL DEFAULT '',
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_watchlist_user_item_wear (user_id, item_id, exterior),
    KEY idx_watchlist_user_sort (user_id, sort_order, id),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_item FOREIGN KEY (item_id) REFERENCES items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE alerts
    ADD COLUMN exterior VARCHAR(16) NULL AFTER item_id;

CREATE TABLE market_index_snapshots (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    user_id           BIGINT         NOT NULL,
    kind              VARCHAR(16)    NOT NULL COMMENT 'holdings / watchlist',
    index_value       DECIMAL(18, 6) NOT NULL,
    market_value      DECIMAL(18, 4) NULL,
    composition_hash  VARCHAR(64)    NOT NULL,
    fetched_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_market_index_user_kind_time (user_id, kind, fetched_at),
    CONSTRAINT fk_market_index_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE price_snapshots
    ADD KEY idx_price_snapshots_fetched_at (fetched_at);
