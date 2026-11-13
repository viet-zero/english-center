ALTER TABLE lich_thi
    ADD COLUMN exam_format VARCHAR(10) NOT NULL DEFAULT 'ONSITE',
    ADD COLUMN invigilator_id VARCHAR(36) NULL,
    ADD FOREIGN KEY (invigilator_id) REFERENCES nhan_vien(id),
    ADD CHECK (exam_format IN ('ONSITE', 'ONLINE'));
