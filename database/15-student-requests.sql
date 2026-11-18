ALTER TABLE dang_ky_hoc_phan
    DROP CHECK dang_ky_hoc_phan_chk_2,
    ADD CONSTRAINT chk_enrollment_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED', 'ON_HOLD'));

CREATE TABLE yeu_cau_hoc_vien (
    id VARCHAR(36) PRIMARY KEY,
    enrollment_id VARCHAR(36) NOT NULL,
    kind VARCHAR(10) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by VARCHAR(36),
    reviewed_at DATETIME,
    review_note VARCHAR(1000),
    paid_amount DECIMAL(15,2),
    remaining_value DECIMAL(15,2),
    total_lessons INT,
    elapsed_lessons INT,
    pending_enrollment VARCHAR(36) GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN enrollment_id ELSE NULL END) STORED,
    UNIQUE (pending_enrollment),
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id),
    FOREIGN KEY (reviewed_by) REFERENCES nhan_vien(id),
    CHECK (kind IN ('CANCEL', 'HOLD')),
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'REFUNDED')),
    INDEX (status, created_at)
);

CREATE TABLE hoan_tien_demo (
    id VARCHAR(36) PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL UNIQUE,
    invoice_id VARCHAR(36) NOT NULL,
    amount DECIMAL(15,2) NOT NULL CHECK (amount >= 0),
    employee_id VARCHAR(36) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (request_id) REFERENCES yeu_cau_hoc_vien(id),
    FOREIGN KEY (invoice_id) REFERENCES hoa_don(id),
    FOREIGN KEY (employee_id) REFERENCES nhan_vien(id)
);
