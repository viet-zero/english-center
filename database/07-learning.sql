CREATE TABLE noi_dung_lop (
    id VARCHAR(36) PRIMARY KEY,
    class_id VARCHAR(36) NOT NULL,
    kind VARCHAR(12) NOT NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id),
    CHECK (kind IN ('MATERIAL', 'ASSIGNMENT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE bai_nop (
    content_id VARCHAR(36) NOT NULL,
    enrollment_id VARCHAR(36) NOT NULL,
    answer TEXT NOT NULL,
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (content_id, enrollment_id),
    FOREIGN KEY (content_id) REFERENCES noi_dung_lop(id),
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
