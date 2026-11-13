CREATE TABLE don_payos (
    order_code BIGINT PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    amount INT NOT NULL CHECK (amount > 0),
    link_id VARCHAR(64),
    checkout_url VARCHAR(512),
    invoice_id VARCHAR(36),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES hoc_vien(id),
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id),
    FOREIGN KEY (invoice_id) REFERENCES hoa_don(id),
    INDEX (student_id, created_at)
);

CREATE TABLE bien_nhan_payos (
    reference VARCHAR(100) PRIMARY KEY,
    order_code BIGINT NOT NULL,
    amount BIGINT NOT NULL,
    reason VARCHAR(255),
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_code) REFERENCES don_payos(order_code)
);

ALTER TABLE giao_dich MODIFY collected_by VARCHAR(36) NULL;
DROP TABLE IF EXISTS giu_cho;
