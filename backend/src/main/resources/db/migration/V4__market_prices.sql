-- 行情模块（M2/M3）：平台商品 ID 映射表
-- 价格快照表 price_snapshots 已在 V1 中建立，此处只新增 marketplace_ids

CREATE TABLE IF NOT EXISTS marketplace_ids (
    market_hash_name    VARCHAR(255) NOT NULL COMMENT '完整市场名（含磨损后缀，如 AK-47 | Redline (Field-Tested)）',
    youpin_id           BIGINT       NULL COMMENT '悠悠有品(UU) 商品模板ID',
    buff_goods_id       BIGINT       NULL COMMENT 'BUFF 商品ID',
    buffmarket_goods_id BIGINT       NULL COMMENT 'Buff Market 商品ID',
    csmoney_nameid      BIGINT       NULL COMMENT 'CS.Money 商品ID',
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (market_hash_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;