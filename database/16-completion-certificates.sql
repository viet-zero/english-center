ALTER TABLE lop_hoc
    ADD completion_pass_score DECIMAL(4,2) NOT NULL DEFAULT 5.00,
    ADD completion_attendance DECIMAL(5,2) NOT NULL DEFAULT 80.00,
    ADD finalized_at DATETIME,
    ADD finalized_by VARCHAR(36),
    ADD FOREIGN KEY (finalized_by) REFERENCES nhan_vien(id),
    ADD CHECK (completion_pass_score BETWEEN 0 AND 10),
    ADD CHECK (completion_attendance BETWEEN 0 AND 100);
ALTER TABLE lich_thi ADD grade_weight DECIMAL(6,2) NOT NULL DEFAULT 1.00 CHECK (grade_weight > 0);
ALTER TABLE dang_ky_hoc_phan DROP CHECK chk_enrollment_status,
    ADD CONSTRAINT chk_enrollment_status CHECK (status IN ('CONFIRMED','CANCELLED','COMPLETED','ON_HOLD','FAILED'));

CREATE TABLE ket_qua_cuoi_khoa (
    enrollment_id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    module_id VARCHAR(36) NOT NULL,
    duration_hours INT NOT NULL,
    final_score DECIMAL(4,2) NOT NULL,
    attended_lessons INT NOT NULL,
    total_lessons INT NOT NULL,
    attendance_percent DECIMAL(5,2) NOT NULL,
    pass_score DECIMAL(4,2) NOT NULL,
    minimum_attendance DECIMAL(5,2) NOT NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('PASS','FAIL','FINANCIAL_HOLD')),
    finalized_by VARCHAR(36) NOT NULL,
    finalized_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id),
    FOREIGN KEY (course_id) REFERENCES khoa_hoc(id),
    FOREIGN KEY (module_id) REFERENCES hoc_phan(id),
    FOREIGN KEY (finalized_by) REFERENCES nhan_vien(id)
);

CREATE TABLE chung_chi (
    id VARCHAR(36) PRIMARY KEY,
    certificate_number BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    verification_token VARCHAR(36) NOT NULL UNIQUE,
    student_id VARCHAR(36) NOT NULL,
    course_id VARCHAR(36) NOT NULL,
    student_name VARCHAR(150) NOT NULL,
    course_name VARCHAR(150) NOT NULL,
    final_score DECIMAL(4,2) NOT NULL,
    attendance_percent DECIMAL(5,2) NOT NULL,
    issued_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    issued_by VARCHAR(36) NOT NULL,
    pdf MEDIUMBLOB,
    UNIQUE(student_id,course_id),
    FOREIGN KEY(student_id) REFERENCES hoc_vien(id),
    FOREIGN KEY(course_id) REFERENCES khoa_hoc(id),
    FOREIGN KEY(issued_by) REFERENCES nhan_vien(id)
);
