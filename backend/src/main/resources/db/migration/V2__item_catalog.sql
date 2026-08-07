-- 饰品数据字典：来自 CSGO-API（ByMykel/CSGO-API），支持中文名称/磨损/浮点值
ALTER TABLE items
    ADD COLUMN name_zh     VARCHAR(255) NULL COMMENT '中文名称',
    ADD COLUMN weapon      VARCHAR(64)  NULL COMMENT '武器/类型名（中文）',
    ADD COLUMN min_float   DECIMAL(8,4) NULL COMMENT '最小磨损值',
    ADD COLUMN max_float   DECIMAL(8,4) NULL COMMENT '最大磨损值',
    ADD COLUMN wears       JSON         NULL COMMENT '可用磨损等级（中文列表）',
    ADD COLUMN external_id VARCHAR(64)  NULL COMMENT 'CSGO-API 物品 id',
    ADD KEY idx_items_name_zh (name_zh),
    ADD UNIQUE KEY uk_items_external (external_id);

ALTER TABLE trades
    ADD COLUMN exterior    VARCHAR(16)  NULL COMMENT '磨损等级（如 崭新出厂）',
    ADD COLUMN float_value DECIMAL(8,4) NULL COMMENT '具体磨损值 0-1';