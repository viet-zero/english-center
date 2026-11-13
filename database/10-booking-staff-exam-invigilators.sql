ALTER TABLE lich_hoc ADD COLUMN booked_by VARCHAR(36) NULL,
    ADD FOREIGN KEY (booked_by) REFERENCES nhan_vien(id);

CREATE TABLE giam_thi_ky_thi (
    exam_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (exam_id, employee_id),
    FOREIGN KEY (exam_id) REFERENCES lich_thi(id),
    FOREIGN KEY (employee_id) REFERENCES nhan_vien(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO giam_thi_ky_thi (exam_id, employee_id)
SELECT id, invigilator_id FROM lich_thi WHERE invigilator_id IS NOT NULL;
