CREATE TABLE thong_bao (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    message VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at DATETIME NULL,
    INDEX idx_student_notification (student_id, id),
    INDEX idx_student_unread (student_id, read_at),
    FOREIGN KEY (student_id) REFERENCES hoc_vien(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
