-- 同一基础饰品的不同磨损价格不同，快照必须带磨损维度。
ALTER TABLE price_snapshots
    ADD COLUMN exterior VARCHAR(16) NULL AFTER item_id,
    DROP INDEX idx_ps_item_platform_time,
    ADD KEY idx_ps_item_exterior_platform_time (item_id, exterior, platform, fetched_at);
