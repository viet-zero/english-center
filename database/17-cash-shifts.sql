CREATE TABLE ca_thu_tien (
    id VARCHAR(36) PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    opening_cash DECIMAL(15,2) NOT NULL CHECK (opening_cash >= 0),
    opened_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at DATETIME,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    cash_received DECIMAL(15,2),
    cash_refunded DECIMAL(15,2),
    expected_cash DECIMAL(15,2),
    actual_cash DECIMAL(15,2),
    difference DECIMAL(15,2),
    close_note VARCHAR(1000),
    active_employee VARCHAR(36) GENERATED ALWAYS AS
      (CASE WHEN status='OPEN' THEN employee_id ELSE NULL END) STORED,
    UNIQUE(active_employee),
    FOREIGN KEY(employee_id) REFERENCES nhan_vien(id)
);
ALTER TABLE giao_dich ADD shift_id VARCHAR(36), ADD FOREIGN KEY(shift_id) REFERENCES ca_thu_tien(id);
ALTER TABLE hoan_tien_demo
    ADD shift_id VARCHAR(36),
    ADD method VARCHAR(20) NOT NULL DEFAULT 'BANK_TRANSFER' CHECK (method IN ('CASH','BANK_TRANSFER')),
    ADD FOREIGN KEY(shift_id) REFERENCES ca_thu_tien(id);
ALTER TABLE bien_nhan_payos ADD refund_shift_id VARCHAR(36),
    ADD FOREIGN KEY(refund_shift_id) REFERENCES ca_thu_tien(id);
