-- TRUY VAN MAU ENGLISH CENTER
-- MySQL 8.0.16+. Chi la tai lieu SQL, chua thuc thi.
-- Chon database va thay cac gia tri mau truoc khi chay tung cau lenh.
-- Cac lenh SET chi khai bao bien trong phien MySQL.
-- Ung dung Java phai dung tham so truy van, khong noi chuoi SQL tu input.

-- KIEU DU LIEU
-- VARCHAR: chuoi, ma dinh danh, so dien thoai (giu so 0 dau).
-- TEXT: noi dung dai nhu mo ta.
-- INT: so nguyen nhu si so, so gio.
-- DECIMAL(15,2): tien te chinh xac; Java dung BigDecimal.
-- DATE: ngay; Java dung LocalDate.
-- TIME: gio; Java dung LocalTime.
-- DATETIME: ngay gio; Java dung LocalDateTime.
-- BOOLEAN: dung/sai; Java dung boolean.
-- Gia tri chua biet dung NULL; kiem tra bang IS NULL, khong dung = NULL.

SET @student_id = 'THAY_MA_HOC_VIEN';
SET @module_id = 'THAY_MA_HOC_PHAN';
SET @class_id = 'THAY_MA_LOP';
SET @invoice_id = 'THAY_MA_HOA_DON';
SET @username = 'THAY_TEN_DANG_NHAP';
SET @keyword = 'THAY_TU_KHOA';
SET @from_date = '2026-11-01';
SET @to_date = '2026-12-01';

-- 1. Tim hoc vien theo ten, ma hoac so dien thoai.
SELECT hv.id, hv.student_code, nd.full_name, nd.phone, nd.email, hv.status
FROM hoc_vien hv
JOIN nguoi_dung nd ON nd.id = hv.user_id
WHERE nd.full_name LIKE CONCAT('%', @keyword, '%')
   OR hv.student_code = @keyword
   OR nd.phone = @keyword
ORDER BY nd.full_name, hv.id
LIMIT 20 OFFSET 0;

-- 2. Ho so cua mot hoc vien.
SELECT hv.id, hv.student_code, nd.full_name, nd.birth_date,
       nd.gender, nd.phone, nd.email, hv.enrollment_date, hv.status
FROM hoc_vien hv
JOIN nguoi_dung nd ON nd.id = hv.user_id
WHERE hv.id = @student_id;

-- 3. Tai khoan va vai tro. Khong tra password_hash ve giao dien.
-- So sanh mat khau bang thu vien bam mat khau trong backend.
SELECT tk.id, tk.username, tk.status, vt.id AS role_id, vt.name AS role_name
FROM tai_khoan tk
LEFT JOIN tai_khoan_vai_tro tkvt ON tkvt.account_id = tk.id
LEFT JOIN vai_tro vt ON vt.id = tkvt.role_id
WHERE tk.username = @username;

-- 4. Khoa hoc va hoc phan dang hoat dong.
SELECT kh.id AS course_id, kh.name AS course_name,
       hp.id AS module_id, hp.name AS module_name,
       hp.sequence_number, hp.tuition, hp.duration_hours
FROM khoa_hoc kh
JOIN hoc_phan hp ON hp.course_id = kh.id
WHERE kh.status = 'ACTIVE' AND hp.status = 'ACTIVE'
ORDER BY kh.name, hp.sequence_number;

-- 5. Lop dang mo va so cho con lai cua hoc phan.
-- So cho la anh chup hien tai; phai kiem tra lai trong transaction khi dang ky.
SELECT lh.id, lh.class_code, nd.full_name AS teacher_name,
       lh.start_date, lh.end_date, hp.tuition, lh.max_students,
       COUNT(dk.id) AS enrolled_students,
       lh.max_students - COUNT(dk.id) AS remaining_seats
FROM lop_hoc lh
JOIN hoc_phan hp ON hp.id = lh.module_id
JOIN khoa_hoc kh ON kh.id = hp.course_id
JOIN nhan_vien nv ON nv.id = lh.teacher_id
JOIN nguoi_dung nd ON nd.id = nv.user_id
LEFT JOIN dang_ky_hoc_phan dk ON dk.class_id = lh.id
    AND dk.status IN ('CONFIRMED', 'COMPLETED')
WHERE lh.module_id = @module_id AND lh.status = 'OPEN'
  AND hp.status = 'ACTIVE' AND kh.status = 'ACTIVE'
  AND nv.status = 'ACTIVE' AND nv.deleted = FALSE
GROUP BY lh.id, lh.class_code, nd.full_name, lh.start_date, lh.end_date,
         hp.tuition, lh.max_students
HAVING remaining_seats > 0
ORDER BY lh.start_date, lh.class_code;

-- 6. Danh sach dang ky cua hoc vien.
SELECT dk.id, hp.name AS module_name, lh.class_code,
       dk.registered_at, dk.applied_tuition, dk.status
FROM dang_ky_hoc_phan dk
JOIN lop_hoc lh ON lh.id = dk.class_id
JOIN hoc_phan hp ON hp.id = lh.module_id
WHERE dk.student_id = @student_id
ORDER BY dk.registered_at DESC, dk.id;

-- 7. Danh sach hoc vien trong lop.
SELECT dk.id AS enrollment_id, hv.student_code, nd.full_name, dk.status
FROM dang_ky_hoc_phan dk
JOIN hoc_vien hv ON hv.id = dk.student_id
JOIN nguoi_dung nd ON nd.id = hv.user_id
WHERE dk.class_id = @class_id
  AND dk.status IN ('CONFIRMED', 'COMPLETED')
ORDER BY nd.full_name, hv.id;

-- 8. Lich hoc cua hoc vien, khoang ngay [from_date, to_date).
SELECT lich.lesson_date, lich.start_time, lich.end_time,
       lh.class_code, ph.name AS room_name, lich.status
FROM dang_ky_hoc_phan dk
JOIN lop_hoc lh ON lh.id = dk.class_id
JOIN lich_hoc lich ON lich.class_id = lh.id
JOIN phong_hoc ph ON ph.id = lich.room_id
WHERE dk.student_id = @student_id
  AND dk.status IN ('CONFIRMED', 'COMPLETED')
  AND lich.status <> 'CANCELLED'
  AND lich.lesson_date >= @from_date AND lich.lesson_date < @to_date
ORDER BY lich.lesson_date, lich.start_time;

-- 9. Hoa don va cong no cua hoc vien.
SELECT id, created_at, total_due, total_paid,
       total_due - total_paid AS remaining_amount, status
FROM hoa_don
WHERE student_id = @student_id
ORDER BY created_at DESC, id;

-- 10. Chi tiet hoa don, gioi han theo chu so huu.
SELECT hd.id AS invoice_id, ct.enrollment_id, hp.name AS module_name,
       lh.class_code, ct.amount, ct.discount, ct.total
FROM hoa_don hd
JOIN chi_tiet_hoa_don ct ON ct.invoice_id = hd.id
JOIN dang_ky_hoc_phan dk ON dk.id = ct.enrollment_id
JOIN lop_hoc lh ON lh.id = dk.class_id
JOIN hoc_phan hp ON hp.id = lh.module_id
WHERE hd.id = @invoice_id AND hd.student_id = @student_id;

-- 11. Lich su thanh toan cua hoa don.
SELECT gd.id, gd.amount, gd.paid_at, gd.method, gd.reference_code, gd.status
FROM giao_dich gd
JOIN hoa_don hd ON hd.id = gd.invoice_id
WHERE hd.id = @invoice_id AND hd.student_id = @student_id
ORDER BY gd.paid_at DESC, gd.id;

-- 12. Tong tien thuc thu theo ngay, chi tinh giao dich thanh cong.
SELECT DATE(paid_at) AS payment_date, SUM(amount) AS collected_amount
FROM giao_dich
WHERE status = 'SUCCESS'
  AND paid_at >= @from_date AND paid_at < @to_date
GROUP BY DATE(paid_at)
ORDER BY payment_date;

-- 13. Danh sach cong no danh cho ke toan.
SELECT hv.student_code, nd.full_name, nd.phone,
       SUM(hd.total_due - hd.total_paid) AS debt
FROM hoa_don hd
JOIN hoc_vien hv ON hv.id = hd.student_id
JOIN nguoi_dung nd ON nd.id = hv.user_id
WHERE hd.total_due > hd.total_paid
GROUP BY hv.id, hv.student_code, nd.full_name, nd.phone
ORDER BY debt DESC, hv.student_code;

-- 14. Doi soat tong da thu voi giao dich thanh cong.
SELECT hd.id, hd.total_paid,
       COALESCE(SUM(CASE WHEN gd.status = 'SUCCESS' THEN gd.amount ELSE 0 END), 0)
           AS actual_paid
FROM hoa_don hd
LEFT JOIN giao_dich gd ON gd.invoice_id = hd.id
GROUP BY hd.id, hd.total_paid
HAVING hd.total_paid <> actual_paid;

-- 15. Mau ghi du lieu. Bo dau -- tung lenh khi da hieu va thay gia tri.
-- Khong xoa hoc vien/hoa don da co lich su giao dich.
-- Them nguoi dung va ho so hoc vien phai thanh cong cung nhau.
-- START TRANSACTION;
-- INSERT INTO nguoi_dung (id, full_name, phone, email)
-- VALUES ('USER_DEMO', 'Nguyen Van A', '0900000000', 'demo@example.com');
-- INSERT INTO hoc_vien (id, user_id, student_code, enrollment_date, status)
-- VALUES ('STUDENT_DEMO', 'USER_DEMO', 'HV-DEMO', CURRENT_DATE, 'ACTIVE');
-- COMMIT;
-- Neu bat ky lenh nao loi, dung ROLLBACK thay COMMIT.
--
-- UPDATE nguoi_dung SET phone = '0900000001' WHERE id = 'USER_DEMO';
-- UPDATE tai_khoan SET status = 'LOCKED' WHERE id = 'ACCOUNT_DEMO';
-- UPDATE khoa_hoc SET status = 'INACTIVE' WHERE id = 'COURSE_DEMO';
--
-- Dang ky, lap hoa don va ghi nhan thanh toan can Service transaction.
-- SQL doc du lieu o tren khong thay the kiem tra phan quyen tai backend.
