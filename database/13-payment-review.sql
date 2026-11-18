ALTER TABLE bien_nhan_payos
    ADD resolution VARCHAR(20),
    ADD resolved_by VARCHAR(36),
    ADD resolved_at DATETIME,
    ADD target_class_id VARCHAR(36),
    ADD resolved_invoice_id VARCHAR(36),
    ADD resolution_note VARCHAR(255),
    ADD FOREIGN KEY (resolved_by) REFERENCES nhan_vien(id),
    ADD FOREIGN KEY (target_class_id) REFERENCES lop_hoc(id),
    ADD FOREIGN KEY (resolved_invoice_id) REFERENCES hoa_don(id);
