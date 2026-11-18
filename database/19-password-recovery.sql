ALTER TABLE tai_khoan
    ADD failed_login_count INT NOT NULL DEFAULT 0 CHECK (failed_login_count BETWEEN 0 AND 5),
    ADD lock_reason VARCHAR(20) CHECK (lock_reason IN ('ADMIN','FAILED_ATTEMPTS'));
UPDATE tai_khoan SET lock_reason='ADMIN' WHERE status='LOCKED';

CREATE TABLE password_reset (
    account_id VARCHAR(36) PRIMARY KEY,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME,
    FOREIGN KEY(account_id) REFERENCES tai_khoan(id)
);
CREATE TABLE demo_email_outbox (
    id VARCHAR(36) PRIMARY KEY,
    account_id VARCHAR(36) NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    subject VARCHAR(150) NOT NULL,
    reset_url VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NOT NULL,
    FOREIGN KEY(account_id) REFERENCES tai_khoan(id),
    INDEX(created_at)
);
