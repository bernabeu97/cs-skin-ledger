ALTER TABLE lots
    ADD COLUMN deleted_at DATETIME(6) NULL AFTER created_at,
    ADD KEY idx_lots_user_deleted (user_id, deleted_at);
