RENAME TABLE don_payos TO don_thanh_toan, bien_nhan_payos TO bien_nhan_thanh_toan;
ALTER TABLE don_thanh_toan
    ADD provider VARCHAR(10) NOT NULL DEFAULT 'PAYOS',
    ADD receiving_account VARCHAR(50);
CREATE TABLE sepay_su_kien (
    reference VARCHAR(100) PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    receiving_account VARCHAR(50) NOT NULL,
    transfer_type VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    payment_code VARCHAR(50),
    content VARCHAR(2000),
    bank_reference VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX(status, received_at)
);
