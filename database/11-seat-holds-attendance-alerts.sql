CREATE TABLE giu_cho (
    student_id VARCHAR(36) PRIMARY KEY,
    class_id VARCHAR(36) NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_hold_class (class_id, expires_at),
    FOREIGN KEY (student_id) REFERENCES hoc_vien(id),
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE canh_bao_chuyen_can (
    enrollment_id VARCHAR(36) PRIMARY KEY,
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
