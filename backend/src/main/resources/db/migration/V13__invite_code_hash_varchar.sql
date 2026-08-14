-- 修复实体 schema 校验:code_hash 从 CHAR(64) 调整为 VARCHAR(64)
-- (Hibernate 对 String 字段按 VARCHAR 校验,CHAR 与 VARCHAR 不匹配)
ALTER TABLE invite_codes
    MODIFY COLUMN code_hash VARCHAR(64) NOT NULL;
