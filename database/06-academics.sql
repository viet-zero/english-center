ALTER TABLE hoa_don ADD COLUMN invoice_number BIGINT NOT NULL AUTO_INCREMENT UNIQUE;

CREATE TABLE lich_thi (
    id VARCHAR(36) PRIMARY KEY,
    class_id VARCHAR(36) NOT NULL,
    title VARCHAR(150) NOT NULL,
    starts_at DATETIME NOT NULL,
    ends_at DATETIME NOT NULL,
    room VARCHAR(100) NOT NULL,
    UNIQUE (class_id, starts_at),
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id),
    CHECK (ends_at > starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diem_danh (
    lesson_id VARCHAR(36) NOT NULL,
    enrollment_id VARCHAR(36) NOT NULL,
    attendance_status VARCHAR(10) NOT NULL,
    PRIMARY KEY (lesson_id, enrollment_id),
    FOREIGN KEY (lesson_id) REFERENCES lich_hoc(id),
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id),
    CHECK (attendance_status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE diem_thi (
    exam_id VARCHAR(36) NOT NULL,
    enrollment_id VARCHAR(36) NOT NULL,
    score DECIMAL(4,2) NOT NULL,
    PRIMARY KEY (exam_id, enrollment_id),
    FOREIGN KEY (exam_id) REFERENCES lich_thi(id),
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id),
    CHECK (score BETWEEN 0 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
