ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER password_hash,
    ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0 AFTER role,
    ADD COLUMN totp_secret VARCHAR(512) NULL AFTER disabled,
    ADD COLUMN totp_enabled TINYINT(1) NOT NULL DEFAULT 0 AFTER totp_secret,
    ADD COLUMN recovery_code_hashes TEXT NULL AFTER totp_enabled,
    ADD COLUMN must_change_password TINYINT(1) NOT NULL DEFAULT 0 AFTER recovery_code_hashes,
    ADD COLUMN session_version INT NOT NULL DEFAULT 0 AFTER must_change_password,
    ADD COLUMN deletion_requested_at DATETIME(6) NULL AFTER session_version;

CREATE TABLE invite_codes (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code_hash   CHAR(64)     NOT NULL,
    created_by  BIGINT       NOT NULL,
    used_by     BIGINT       NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_invite_code_hash (code_hash),
    KEY idx_invite_expires (expires_at),
    CONSTRAINT fk_invite_creator FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT fk_invite_user FOREIGN KEY (used_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NULL,
    username    VARCHAR(64)   NULL,
    event_type  VARCHAR(64)   NOT NULL,
    status      VARCHAR(16)   NOT NULL,
    ip_address  VARCHAR(64)   NULL,
    user_agent  VARCHAR(512)  NULL,
    target_type VARCHAR(64)   NULL,
    target_id   VARCHAR(128)  NULL,
    details     VARCHAR(1000) NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at),
    KEY idx_audit_user_created (user_id, created_at),
    KEY idx_audit_event_created (event_type, created_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
