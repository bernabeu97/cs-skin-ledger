-- 其他收支：会员费、赔偿、退款等非饰品资金项（与批次账本并列）
CREATE TABLE other_cost_entries (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    category    VARCHAR(32)   NOT NULL COMMENT '分类：membership/platform_fee/compensation_expense/compensation_income/refund/other',
    direction   VARCHAR(8)    NOT NULL COMMENT 'expense=支出 / income=收入',
    amount      DECIMAL(18,4) NOT NULL COMMENT '金额（正数）',
    occurred_at DATETIME(6)   NOT NULL,
    platform    VARCHAR(16)   NULL,
    item_id     BIGINT        NULL COMMENT '可选，关联饰品（如赔偿）',
    note        VARCHAR(500)  NULL,
    source_ref  VARCHAR(128)  NULL COMMENT '幂等去重（如 xls:c:行号）',
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ocs_source_ref (source_ref),
    KEY idx_ocs_user_time (user_id, occurred_at),
    KEY idx_ocs_category (category),
    CONSTRAINT fk_ocs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ocs_item FOREIGN KEY (item_id) REFERENCES items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;