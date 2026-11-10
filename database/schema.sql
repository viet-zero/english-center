-- English Center schema draft for MySQL 8.0.16+
-- This file has not been executed. Select the target database manually before use.
-- IDs are assigned by the application. Monetary values are VND.
-- Application transactions must handle capacity, scheduling and payment concurrency.
-- Existing Java entities need relationship and column mappings before using this schema.

CREATE TABLE nguoi_dung (
    id VARCHAR(36) PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    birth_date DATE,
    gender VARCHAR(20),
    identity_number VARCHAR(20) UNIQUE,
    phone VARCHAR(20) UNIQUE,
    email VARCHAR(254) UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tai_khoan (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_login DATETIME,
    FOREIGN KEY (user_id) REFERENCES nguoi_dung(id),
    CHECK (status IN ('ACTIVE', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE vai_tro (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tai_khoan_vai_tro (
    account_id VARCHAR(36) NOT NULL,
    role_id VARCHAR(36) NOT NULL,
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, role_id),
    FOREIGN KEY (account_id) REFERENCES tai_khoan(id),
    FOREIGN KEY (role_id) REFERENCES vai_tro(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hoc_vien (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    student_code VARCHAR(30) NOT NULL UNIQUE,
    entrance_score DECIMAL(5,2),
    enrollment_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (user_id) REFERENCES nguoi_dung(id),
    CHECK (entrance_score IS NULL OR entrance_score >= 0),
    CHECK (status IN ('ACTIVE', 'ON_HOLD', 'GRADUATED', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE nhan_vien (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL UNIQUE,
    employee_code VARCHAR(30) NOT NULL UNIQUE,
    position VARCHAR(100),
    specialization VARCHAR(255),
    biography TEXT,
    hire_date DATE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    FOREIGN KEY (user_id) REFERENCES nguoi_dung(id),
    CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE khoa_hoc (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(150) NOT NULL UNIQUE,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hoc_phan (
    id VARCHAR(36) PRIMARY KEY,
    course_id VARCHAR(36) NOT NULL,
    name VARCHAR(150) NOT NULL,
    sequence_number INT NOT NULL,
    tuition DECIMAL(15,2) NOT NULL,
    duration_hours INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE (course_id, sequence_number),
    FOREIGN KEY (course_id) REFERENCES khoa_hoc(id),
    CHECK (sequence_number > 0),
    CHECK (tuition >= 0),
    CHECK (duration_hours > 0),
    CHECK (status IN ('ACTIVE', 'INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lop_hoc (
    id VARCHAR(36) PRIMARY KEY,
    module_id VARCHAR(36) NOT NULL,
    teacher_id VARCHAR(36) NOT NULL,
    class_code VARCHAR(30) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    max_students INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    FOREIGN KEY (module_id) REFERENCES hoc_phan(id),
    FOREIGN KEY (teacher_id) REFERENCES nhan_vien(id),
    CHECK (end_date >= start_date),
    CHECK (max_students > 0),
    CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE phong_hoc (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    CHECK (capacity > 0),
    CHECK (status IN ('AVAILABLE', 'MAINTENANCE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE lich_hoc (
    id VARCHAR(36) PRIMARY KEY,
    class_id VARCHAR(36) NOT NULL,
    room_id VARCHAR(36) NOT NULL,
    lesson_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id),
    FOREIGN KEY (room_id) REFERENCES phong_hoc(id),
    INDEX idx_room_schedule (room_id, lesson_date, start_time, end_time),
    INDEX idx_class_schedule (class_id, lesson_date),
    CHECK (end_time > start_time),
    CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE dang_ky_hoc_phan (
    id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    class_id VARCHAR(36) NOT NULL,
    registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    applied_tuition DECIMAL(15,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    UNIQUE (student_id, class_id),
    FOREIGN KEY (student_id) REFERENCES hoc_vien(id),
    FOREIGN KEY (class_id) REFERENCES lop_hoc(id),
    INDEX idx_class_status (class_id, status),
    CHECK (applied_tuition >= 0),
    CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hoa_don (
    id VARCHAR(36) PRIMARY KEY,
    student_id VARCHAR(36) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    discount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_due DECIMAL(15,2) NOT NULL,
    total_paid DECIMAL(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    FOREIGN KEY (student_id) REFERENCES hoc_vien(id),
    CHECK (discount >= 0),
    CHECK (total_due >= 0),
    CHECK (total_paid >= 0 AND total_paid <= total_due),
    CHECK (
        (status = 'UNPAID' AND total_paid = 0 AND total_due > 0)
        OR (status = 'PARTIAL' AND total_paid > 0 AND total_paid < total_due)
        OR (status = 'PAID' AND total_paid = total_due)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chi_tiet_hoa_don (
    id VARCHAR(36) PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    enrollment_id VARCHAR(36) NOT NULL UNIQUE,
    amount DECIMAL(15,2) NOT NULL,
    discount DECIMAL(15,2) NOT NULL DEFAULT 0,
    total DECIMAL(15,2) NOT NULL,
    UNIQUE (invoice_id),
    FOREIGN KEY (invoice_id) REFERENCES hoa_don(id),
    FOREIGN KEY (enrollment_id) REFERENCES dang_ky_hoc_phan(id),
    CHECK (amount >= 0),
    CHECK (discount >= 0 AND discount <= amount),
    CHECK (total = amount - discount)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE giao_dich (
    id VARCHAR(36) PRIMARY KEY,
    invoice_id VARCHAR(36) NOT NULL,
    collected_by VARCHAR(36) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    method VARCHAR(20) NOT NULL,
    reference_code VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    FOREIGN KEY (invoice_id) REFERENCES hoa_don(id),
    FOREIGN KEY (collected_by) REFERENCES nhan_vien(id),
    INDEX idx_invoice_status (invoice_id, status),
    CHECK (amount > 0),
    CHECK (method IN ('CASH', 'BANK_TRANSFER')),
    CHECK (status IN ('SUCCESS', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Application rules
-- Lock student and class before checking prerequisite, duplicates, capacity and conflicts.
-- Sequence 1 has no prerequisite; later modules require completing the preceding module.
-- Reactivate the existing enrollment when registering again for the same cancelled class.
-- Create enrollment, invoice and invoice item atomically; one invoice per enrollment in v1.
-- Invoice student must match enrollment student and total_due must match item total.
-- Lock invoice before recording payment; sum SUCCESS payments to update total_paid.
-- reference_code is an idempotency key, including for cash receipts.
-- Lock room, teacher and class consistently before checking overlapping lesson intervals.
-- Confirm teacher role and room capacity in the service layer.
-- Voucher support, learning materials, assignments and online tests are future extensions.
-- Invoice.voucherId in Java is reserved for that extension and is not mapped in this draft.
-- No seed data, database creation, triggers or automatic execution are included.
