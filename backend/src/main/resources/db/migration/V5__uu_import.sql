-- UU 导入：批次增加外部来源引用，避免重复导入
ALTER TABLE lots
    ADD COLUMN source_ref VARCHAR(128) NULL COMMENT '外部来源标识（如 uu:inv:xxx / uu:sale:xxx），用于幂等去重',
    ADD UNIQUE KEY uk_lots_source_ref (source_ref);