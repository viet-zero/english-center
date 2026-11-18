CREATE TABLE ma_giam_gia (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    course_id VARCHAR(36) NOT NULL,
    amount INT NOT NULL CHECK (amount > 0),
    starts_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    max_uses INT NOT NULL DEFAULT 1 CHECK (max_uses > 0),
    status VARCHAR(10) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')),
    FOREIGN KEY(course_id) REFERENCES khoa_hoc(id),
    CHECK (expires_at > starts_at)
);
ALTER TABLE don_thanh_toan
    DROP CHECK don_thanh_toan_chk_1,
    ADD tuition_amount INT,
    ADD discount_amount INT NOT NULL DEFAULT 0,
    ADD coupon_id VARCHAR(36),
    ADD coupon_code VARCHAR(30),
    ADD FOREIGN KEY(coupon_id) REFERENCES ma_giam_gia(id);
UPDATE don_thanh_toan o LEFT JOIN hoa_don i ON i.id=o.invoice_id
    SET o.discount_amount=COALESCE(i.discount,0),o.tuition_amount=o.amount+COALESCE(i.discount,0);
ALTER TABLE don_thanh_toan MODIFY tuition_amount INT NOT NULL,
    ADD CHECK (amount >= 0 AND discount_amount >= 0 AND tuition_amount >= discount_amount
        AND amount=tuition_amount-discount_amount);
ALTER TABLE hoa_don ADD voucher_id VARCHAR(36),ADD FOREIGN KEY(voucher_id) REFERENCES ma_giam_gia(id);
CREATE TABLE luot_dung_ma (
    coupon_id VARCHAR(36) NOT NULL,
    student_id VARCHAR(36) NOT NULL,
    order_code BIGINT NOT NULL UNIQUE,
    invoice_id VARCHAR(36) NOT NULL UNIQUE,
    used_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(coupon_id,student_id),
    FOREIGN KEY(coupon_id) REFERENCES ma_giam_gia(id),
    FOREIGN KEY(student_id) REFERENCES hoc_vien(id),
    FOREIGN KEY(order_code) REFERENCES don_thanh_toan(order_code),
    FOREIGN KEY(invoice_id) REFERENCES hoa_don(id)
);
